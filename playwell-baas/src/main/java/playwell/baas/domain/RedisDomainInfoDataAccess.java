package playwell.baas.domain;

import com.alibaba.fastjson.JSON;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import playwell.common.argument.BaseArgumentRootContext;
import playwell.common.expression.PlaywellExpression;
import playwell.common.expression.PlaywellExpressionContext;
import playwell.common.expression.spel.SpELPlaywellExpressionContext;
import playwell.storage.redis.RedisHelper;
import playwell.util.Regexpr;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

/**
 * 基于Redis的Domain属性访问
 */
public class RedisDomainInfoDataAccess implements DomainInfoDataAccess {

  public static final String TYPE = "redis";

  private static final Logger logger = LogManager.getLogger(RedisDomainInfoDataAccess.class);

  private String redisResource;

  private String namespace;

  public RedisDomainInfoDataAccess() {

  }

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    this.redisResource = configuration.getString(ConfigItems.REDIS_RESOURCE);
    this.namespace = configuration.getString(ConfigItems.NAMESPACE);
  }

  @Override
  public Optional<DomainInfo> query(String type, String domainId, List<String> properties) {
    final String key = key(domainId);
    try (Jedis jedis = RedisHelper.use(redisResource).jedis()) {
      // 查询所有的属性
      if (CollectionUtils.isEmpty(properties)) {
        final Map<String, String> propertyValueMap = jedis.hgetAll(key);
        final DomainInfo domainInfo = buildWithKVMap(domainId, propertyValueMap);
        return Optional.of(domainInfo);
      }
      // 查询指定的属性
      else {
        final List<String> propertyValueList = jedis.hmget(key, properties.toArray(new String[]{}));
        final DomainInfo domainInfo = buildWithKVList(domainId, properties, propertyValueList);
        return Optional.of(domainInfo);
      }
    }
  }

  @Override
  public void upsert(String type, String domainId, Map<String, Object> properties) {
    final DomainInfoUpsertCommand command = new DomainInfoUpsertCommand(type, domainId, properties);
    this.executeUpsertCommands(Collections.singletonList(command));
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public Collection<DomainInfo> executeQueryCommands(
      Collection<DomainInfoQueryCommand> queryCommands) {
    if (CollectionUtils.isEmpty(queryCommands)) {
      return Collections.emptyList();
    }

    final List<Pair<DomainInfoQueryCommand, Response>> allResponse = new LinkedList<>();

    try (Jedis jedis = RedisHelper.use(redisResource).jedis()) {
      try (Pipeline pipeline = jedis.pipelined()) {
        for (DomainInfoQueryCommand cmd : queryCommands) {
          final Response response;
          if (CollectionUtils.isEmpty(cmd.getProperties())) {
            response = pipeline.hgetAll(key(cmd.getDomainId()));
          } else {
            response = pipeline.hmget(
                key(cmd.getDomainId()), cmd.getProperties().toArray(new String[]{}));
          }
          allResponse.add(Pair.of(cmd, response));
        }
      }
    }

    return allResponse.stream().map(p -> {
      final DomainInfoQueryCommand cmd = p.getLeft();
      final List<String> propertyKeys = cmd.getProperties();
      final Object propertyValues = p.getRight().get();
      if (propertyValues instanceof List) {
        List<String> propertyValueList = (List<String>) propertyValues;
        return buildWithKVList(cmd.getDomainId(), propertyKeys, propertyValueList);
      } else {
        Map<String, String> propertyValueMap = (Map<String, String>) propertyValues;
        return buildWithKVMap(cmd.getDomainId(), propertyValueMap);
      }
    }).collect(Collectors.toList());
  }

  private DomainInfo buildWithKVList(
      String domainId, List<String> propertyKeys, List<String> propertyValues) {
    final Map<String, Object> properties = new HashMap<>(propertyKeys.size());
    for (int i = 0; i < propertyKeys.size(); i++) {
      final String key = propertyKeys.get(i);
      final String value = propertyValues.get(i);
      if (value == null) {
        continue;
      }
      properties.put(key, JSON.parse(value));
    }
    return new DomainInfo(domainId, properties);
  }

  private DomainInfo buildWithKVMap(String domainId, Map<String, String> propertyValueMap) {
    final Map<String, Object> properties = new HashMap<>(propertyValueMap.size());
    for (Map.Entry<String, String> entry : propertyValueMap.entrySet()) {
      final String key = entry.getKey();
      final String value = entry.getValue();
      if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
        continue;
      }
      properties.put(key, JSON.parse(value));
    }
    return new DomainInfo(domainId, properties);
  }

  @Override
  public void executeUpsertCommands(
      Collection<DomainInfoUpsertCommand> upsertCommands) {
    if (CollectionUtils.isEmpty(upsertCommands)) {
      return;
    }

    try (Jedis jedis = RedisHelper.use(redisResource).jedis()) {
      try (Pipeline pipeline = jedis.pipelined()) {

        for (DomainInfoUpsertCommand cmd : upsertCommands) {
          if (MapUtils.isEmpty(cmd.getUserProperties())) {
            continue;
          }
          final String key = key(cmd.getDomainId());
          // 需要被删除的字段
          List<String> delProperties = new LinkedList<>();
          // 需要递增的字段以及递增值
          Map<String, Integer> incrProperties = new HashMap<>();
          // 需要被更新的字段
          final Map<String, String> upsertProperties = new HashMap<>();

          for (Map.Entry<String, Object> entry : cmd.getUserProperties().entrySet()) {
            final String propertyKey = entry.getKey();
            final Object propertyValue = entry.getValue();

            // 删除空值
            if (propertyValue == null) {
              delProperties.add(propertyKey);
              continue;
            }

            // 处理操作指令
            if (propertyValue instanceof String) {
              final String propertyValueStr = StringUtils.strip((String) propertyValue);
              if (Regexpr.isMatch(Operators.DELETE, propertyValueStr)) {
                delProperties.add(propertyKey);
                continue;
              } else if (Regexpr.isMatch(Operators.INCR, propertyValueStr)) {
                int val = Integer
                    .parseInt(Regexpr.group(Operators.INCR, propertyValueStr, 1).orElseThrow(
                        () -> new RuntimeException(
                            "Could not parse the domain property expression: " + propertyValueStr)
                    ));
                incrProperties.put(propertyKey, val);
                continue;
              } else if (Regexpr.isMatch(Operators.DECR, propertyValueStr)) {
                int val = Integer
                    .parseInt(Regexpr.group(Operators.DECR, propertyValueStr, 1).orElseThrow(
                        () -> new RuntimeException(
                            "Could not parse the domain property expression: " + propertyValueStr)
                    ));
                incrProperties.put(propertyKey, -val);
                continue;
              }
            }

            upsertProperties.put(propertyKey, JSON.toJSONString(propertyValue));
          }

          if (CollectionUtils.isNotEmpty(delProperties)) {
            pipeline.hdel(key, delProperties.toArray(new String[]{}));
          }

          if (MapUtils.isNotEmpty(incrProperties)) {
            for (Map.Entry<String, Integer> entry : incrProperties.entrySet()) {
              pipeline.hincrBy(key, entry.getKey(), entry.getValue());
            }
          }

          if (MapUtils.isNotEmpty(upsertProperties)) {
            pipeline.hmset(key, upsertProperties);
          }
        }
      }
    }
  }

  @Override
  public Optional<String> scan(
      String cursor,
      String type,
      List<String> properties,
      List<PlaywellExpression> conditions,
      int count,
      Consumer<ScanDomainInfoContext> scanConsumer) {
    final String pattern = String.format("%s:*", namespace);
    final List<String> keys;
    try (Jedis jedis = RedisHelper.use(redisResource).jedis()) {
      ScanResult<String> scanResult = jedis.scan(
          cursor, new ScanParams().match(pattern).count(count));
      keys = scanResult.getResult();
      cursor = scanResult.getCursor();
    }

    final Collection<DomainInfoQueryCommand> queryCommands = keys.stream().map(key -> {
      final String domainId = key.substring(namespace.length() + 1);
      return new DomainInfoQueryCommand(
          type,
          domainId,
          properties
      );
    }).collect(Collectors.toList());

    final Collection<DomainInfo> domainInfoList = this.executeQueryCommands(queryCommands);
    int alreadyScannedNum = 0;
    for (DomainInfo domainInfo : domainInfoList) {
      try {
        if (!isMatchCondition(conditions, domainInfo)) {
          continue;
        }
        scanConsumer.accept(new RedisScanDomainInfoContext(domainInfo, ++alreadyScannedNum));
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
    }

    if ("0".equals(cursor)) {
      return Optional.empty();
    } else {
      return Optional.of(cursor);
    }
  }

  private String key(String domainId) {
    return String.format("%s:%s", namespace, domainId);
  }

  private boolean isMatchCondition(
      List<PlaywellExpression> conditions, DomainInfo domainInfo) {
    if (CollectionUtils.isEmpty(conditions)) {
      return true;
    }

    final PlaywellExpressionContext expressionContext = new SpELPlaywellExpressionContext();
    expressionContext.setRootObject(new RedisScanConditionRootContext(domainInfo));

    return conditions.stream().anyMatch(cond -> (boolean) cond.getResult(expressionContext));
  }

  @Override
  public void batchRemove(Collection<DomainInfo> domainInfoCollection) {
    if (CollectionUtils.isEmpty(domainInfoCollection)) {
      return;
    }

    String[] keys = domainInfoCollection.stream()
        .map(domainInfo -> key(domainInfo.getDomainId()))
        .collect(Collectors.toList())
        .toArray(new String[]{});

    try (Jedis jedis = RedisHelper.use(redisResource).jedis()) {
      jedis.del(keys);
    }
  }

  // 配置选项
  interface ConfigItems {

    String REDIS_RESOURCE = "redis_resource";

    String NAMESPACE = "namespace";
  }

  class RedisScanDomainInfoContext implements ScanDomainInfoContext {

    private final DomainInfo domainInfo;

    private final int alreadyScannedNum;

    RedisScanDomainInfoContext(DomainInfo domainInfo, int alreadyScannedNum) {
      this.domainInfo = domainInfo;
      this.alreadyScannedNum = alreadyScannedNum;
    }

    @Override
    public DomainInfo currentDomainInfo() {
      return this.domainInfo;
    }

    @Override
    public int alreadyScannedNum() {
      return this.alreadyScannedNum;
    }

    @Override
    public void remove() {
      RedisHelper.use(redisResource).call(jedis -> {
        jedis.del(key(domainInfo.getDomainId()));
      });
    }
  }

  class RedisScanConditionRootContext extends BaseArgumentRootContext {

    private final DomainInfo domainInfo;

    RedisScanConditionRootContext(DomainInfo domainInfo) {
      this.domainInfo = domainInfo;
    }

    public String getDomainId() {
      return domainInfo.getDomainId();
    }

    public EasyMap getProperties() {
      return new EasyMap(domainInfo.getProperties());
    }

    public boolean containsProperty(String propertyName) {
      return getProperties().contains(propertyName);
    }

    public Object property(String propertyName) {
      return getProperties().get(propertyName);
    }

    public Object property(String propertyName, Object defaultValue) {
      return getProperties().get(propertyName, defaultValue);
    }
  }
}
