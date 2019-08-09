package playwell.baas.domain;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import playwell.common.EasyMap;
import playwell.common.expression.PlaywellExpression;
import playwell.common.expression.spel.SpELPlaywellExpression;

/**
 * DomainInfo扫描配置
 */
public class DomainInfoScanConfiguration {

  // 筛选条件
  private final List<PlaywellExpression> conditions;

  // 属性列表
  private final List<String> properties;

  // 是否删除该记录
  private final boolean remove;

  // 事件映射配置
  private final EventMappingConfiguration eventMappingConfiguration;

  // 扫描标记
  private final String mark;

  // 每批次的扫描数目
  private final int batchSize;

  // 每批次扫描间隔时间，milliseconds
  private final long sleepTime;

  // 每扫描多少条输出一条日志
  private final int logPerCount;

  // 扫描上限
  private final int limit;

  public DomainInfoScanConfiguration(
      List<PlaywellExpression> conditions, List<String> properties, boolean remove,
      EventMappingConfiguration eventMappingConfiguration, String mark, int batchSize,
      long sleepTime,
      int logPerCount,
      int limit) {
    this.conditions = conditions;
    this.properties = properties;
    this.remove = remove;
    this.eventMappingConfiguration = eventMappingConfiguration;
    this.mark = mark;
    this.batchSize = batchSize;
    this.sleepTime = sleepTime;
    this.logPerCount = logPerCount;
    this.limit = limit;
  }

  /**
   * 从配置参数中构建
   *
   * @param configuration 配置参数
   * @return DomainInfoScanConfiguration
   */
  public static DomainInfoScanConfiguration buildFromConfiguration(EasyMap configuration) {
    final List<PlaywellExpression> conditions = configuration
        .getStringList(ConfigItems.CONDITIONS).stream().map(exprText -> {
          PlaywellExpression expression = new SpELPlaywellExpression(exprText);
          expression.compile();
          return expression;
        }).collect(Collectors.toList());
    final List<String> properties = configuration.getStringList(ConfigItems.PROPERTIES);
    final boolean remove = configuration.getBoolean(ConfigItems.REMOVE, false);
    final EventMappingConfiguration eventMappingConfiguration;
    if (configuration.contains(ConfigItems.EVENT_MAPPING)) {
      eventMappingConfiguration = EventMappingConfiguration
          .buildFromConfiguration(configuration.getSubArguments(ConfigItems.EVENT_MAPPING));
    } else {
      eventMappingConfiguration = null;
    }
    final String mark = configuration.getString(
        ConfigItems.MARK, RandomStringUtils.randomAlphanumeric(6));
    final int batchSize = configuration.getInt(ConfigItems.BATCH_SIZE, 1000);
    final long sleepTime = configuration.getLong(ConfigItems.SLEEP_TIME, 0);
    final int logPerCount = configuration.getInt(ConfigItems.LOG_PER_COUNT, 1);
    final int limit = configuration.getInt(ConfigItems.LIMIT, 0);
    return new DomainInfoScanConfiguration(
        conditions,
        properties,
        remove,
        eventMappingConfiguration,
        mark,
        batchSize,
        sleepTime,
        logPerCount,
        limit
    );
  }

  public List<PlaywellExpression> getConditions() {
    return conditions;
  }

  public List<String> getProperties() {
    return properties;
  }

  public boolean isRemove() {
    return remove;
  }

  public EventMappingConfiguration getEventMappingConfiguration() {
    return eventMappingConfiguration;
  }

  public String getMark() {
    return mark;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public long getSleepTime() {
    return sleepTime;
  }

  public int getLogPerCount() {
    return logPerCount;
  }

  public int getLimit() {
    return limit;
  }

  interface ConfigItems {

    String CONDITIONS = "conditions";

    String PROPERTIES = "properties";

    String REMOVE = "remove";

    String EVENT_MAPPING = "event_mapping";

    String MARK = "mark";

    String BATCH_SIZE = "batch_size";

    String SLEEP_TIME = "sleep_time";

    String LOG_PER_COUNT = "log_per_count";

    String LIMIT = "limit";
  }
}
