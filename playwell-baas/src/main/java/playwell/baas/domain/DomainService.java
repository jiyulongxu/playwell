package playwell.baas.domain;

import com.alibaba.fastjson.JSONObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.clock.CachedTimestamp;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.ServiceRunnerIntegrationPlan;
import playwell.message.BasicActivityThreadMessage;
import playwell.message.Message;
import playwell.message.ServiceRequestMessage;
import playwell.message.ServiceResponseMessage;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;
import playwell.service.PlaywellService;
import playwell.service.ServiceMeta;
import playwell.service.ServiceMetaManager;
import playwell.util.Sleeper;
import playwell.util.VariableHolder;

/**
 * Domain信息服务
 *
 * 配置：
 *
 * <pre>
 *   - name: domain
 *     class: playwell.baas.domain.DomainService
 *     message_bus: service_bus
 *     domain_types:
 *       - type: user
 *         storage: redis
 *         redis_resource: default
 *         namespace: "p:u"
 *       - type: order
 *         storage: redis
 *         redis_resource: default
 *         namespace: "p:o"
 * </pre>
 *
 * 接收的请求形式：
 *
 * <p>查询指定的用户属性：</p>
 * <pre>
 *   {
 *     "opt": "query",
 *     "type": "user",
 *     "id": "10010",
 *     "properties": [
 *        "name",
 *        "gender"
 *     ]
 *   }
 * </pre>
 *
 * <p>查询用户的所有属性：</p>
 * <pre>
 *   {
 *     "opt": "query",
 *     "type": "user",
 *     "id": "10010",
 *     "properties": []
 *   }
 * </pre>
 *
 * <p>UPSERT用户属性：</p>
 * <pre>
 *   {
 *     "opt": "upsert",
 *     "type": "user",
 *     "id": "10010",
 *     "properties": {
 *       "name": "SamChi",
 *       "joined_activity": "$incr 1",
 *       "score": [
 *          {
 *            "subject": "Math",
 *            "grade": "A"
 *          },
 *          {
 *            "subject": "Chinese",
 *            "grade": "B"
 *          }
 *       ]
 *     }
 *   }
 * </pre>
 */
public class DomainService implements PlaywellService {

  private static final Logger logger = LogManager.getLogger(DomainService.class);

  private static final Logger scanDomainLogger = LogManager.getLogger("scan_domain");

  private static final Map<String, Function<EasyMap, DomainInfoDataAccess>> ALL_DATA_ACCESS = new HashMap<>();

  // 用于执行扫描操作的线程池
  private static final Executor scanExecutor = Executors.newCachedThreadPool();

  static {
    ALL_DATA_ACCESS.put(RedisDomainInfoDataAccess.TYPE, config -> {
      DomainInfoDataAccess dataAccess = new RedisDomainInfoDataAccess();
      dataAccess.init(config);
      return dataAccess;
    });
  }

  // 可用的数据访问层
  private final Map<String, DomainInfoDataAccess> allDataAccess = new HashMap<>();

  // 扫描停止标记
  private final Map<String, Boolean> scanStopMarks = new ConcurrentHashMap<>();

  // 服务名称
  private String serviceName;

  private volatile boolean inited = false;

  @Override
  public synchronized void init(Object config) {
    if (inited) {
      logger.warn("The DomainService has already been inited");
      return;
    }

    final EasyMap configuration = (EasyMap) config;
    this.serviceName = configuration.getString(ConfigItems.NAME);
    final List<EasyMap> domainTypeConfigList = configuration
        .getSubArgumentsList(ConfigItems.DOMAIN_TYPES);
    domainTypeConfigList.forEach(domainTypeConfig -> {
      final String type = domainTypeConfig.getString(DomainTypeConfigItems.TYPE);
      final String storage = domainTypeConfig.getString(DomainTypeConfigItems.STORAGE);
      if (!ALL_DATA_ACCESS.containsKey(storage)) {
        throw new RuntimeException(String.format("Unknown domain data storage: %s", storage));
      }
      DomainInfoDataAccess dataAccess = ALL_DATA_ACCESS.get(storage).apply(domainTypeConfig);
      allDataAccess.put(type, dataAccess);
    });

    inited = true;
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public Collection<ServiceResponseMessage> handle(Collection<ServiceRequestMessage> messages) {
    if (CollectionUtils.isEmpty(messages)) {
      return Collections.emptyList();
    }

    final List<ServiceResponseMessage> allResponse = new ArrayList<>(messages.size());
    final Map<String, List<Pair<ServiceRequestMessage, DomainInfoQueryCommand>>> readRequests =
        new HashMap<>();
    final Map<String, List<Pair<ServiceRequestMessage, DomainInfoUpsertCommand>>> writeRequests =
        new HashMap<>();

    messages.forEach(requestMessage -> {
      try {
        final EasyMap args = new EasyMap((Map<String, Object>) requestMessage.getArgs());
        final String type = StringUtils.strip(args.getString(ArgNames.TYPE, ""));
        if (StringUtils.isEmpty(type)) {
          allResponse.add(buildFailureResponse(
              requestMessage,
              ErrorCodes.NO_TYPE,
              ""
          ));
          return;
        }

        if (!allDataAccess.containsKey(type)) {
          allResponse.add(buildFailureResponse(
              requestMessage,
              ErrorCodes.UNKNOWN_TYPE,
              String.format("Unknown domain data type: %s", type)
          ));
          return;
        }

        final String operation = args.getString(ArgNames.OPT, "");
        if (Operations.QUERY.equals(operation)) {
          final String domainId = args.get(ArgNames.ID).toString();
          final List<String> properties = args.getStringList(ArgNames.PROPERTIES);
          final DomainInfoQueryCommand cmd = new DomainInfoQueryCommand(type, domainId, properties);
          readRequests
              .computeIfAbsent(type, k -> new ArrayList<>())
              .add(Pair.of(requestMessage, cmd));
        } else if (Operations.UPSERT.equals(operation)) {
          final String domainId = args.get(ArgNames.ID).toString();
          final Map<String, Object> userProperties = args.getSubArguments(ArgNames.PROPERTIES)
              .toMap();
          final DomainInfoUpsertCommand cmd = new DomainInfoUpsertCommand(type, domainId,
              userProperties);
          writeRequests
              .computeIfAbsent(type, k -> new ArrayList<>())
              .add(Pair.of(requestMessage, cmd));
        } else {
          allResponse.add(buildFailureResponse(
              requestMessage,
              ErrorCodes.UNKNOWN_OPT,
              String.format("Unknown domain data operation: %s", operation)
          ));
        }
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
        allResponse.add(buildFailureResponse(
            requestMessage,
            ErrorCodes.SERVER_ERROR,
            ""
        ));
      }
    });

    // 处理Write请求
    handleWriteRequests(allResponse, writeRequests);
    // 处理Read请求
    handleReadRequests(allResponse, readRequests);

    return allResponse;
  }

  private void handleWriteRequests(List<ServiceResponseMessage> allResponse,
      Map<String, List<Pair<ServiceRequestMessage, DomainInfoUpsertCommand>>> writeRequests) {
    // 处理Upsert请求
    if (MapUtils.isNotEmpty(writeRequests)) {
      writeRequests.entrySet().parallelStream().forEach(entry -> {
        int index = 0;
        try {
          final String type = entry.getKey();
          final Collection<DomainInfoUpsertCommand> commands = entry
              .getValue().stream().map(Pair::getValue).collect(Collectors.toList());
          allDataAccess.get(type).executeUpsertCommands(commands);
          synchronized (allResponse) {
            for (; index < entry.getValue().size(); index++) {
              final ServiceRequestMessage requestMessage = entry.getValue().get(index).getKey();
              allResponse.add(new ServiceResponseMessage(
                  CachedTimestamp.nowMilliseconds(),
                  requestMessage,
                  Result.ok()
              ));
            }
          }
        } catch (Exception e) {
          logger.error(e.getMessage(), e);
          synchronized (allResponse) {
            for (; index < entry.getValue().size(); index++) {
              final ServiceRequestMessage requestMessage = entry.getValue().get(index).getKey();
              allResponse.add(buildFailureResponse(
                  requestMessage,
                  ErrorCodes.SERVER_ERROR,
                  ""
              ));
            }
          }
        }
      });
    }
  }

  private void handleReadRequests(List<ServiceResponseMessage> allResponse,
      Map<String, List<Pair<ServiceRequestMessage, DomainInfoQueryCommand>>> readRequests) {
    if (MapUtils.isNotEmpty(readRequests)) {
      readRequests.entrySet().parallelStream().forEach(entry -> {
        int index = 0;
        try {
          final String type = entry.getKey();
          final Collection<DomainInfoQueryCommand> commands = entry
              .getValue().stream().map(Pair::getValue).collect(Collectors.toList());
          final Collection<DomainInfo> domainInfos = allDataAccess
              .get(type).executeQueryCommands(commands);
          synchronized (allResponse) {
            for (DomainInfo domainInfo : domainInfos) {
              ServiceRequestMessage requestMessage = entry.getValue().get(index).getKey();
              allResponse.add(new ServiceResponseMessage(
                  CachedTimestamp.nowMilliseconds(),
                  requestMessage,
                  Result.okWithData(domainInfo.getProperties())
              ));
              index++;
            }
          }
        } catch (Exception e) {
          logger.error(e.getMessage(), e);
          synchronized (allResponse) {
            for (; index < entry.getValue().size(); index++) {
              final ServiceRequestMessage requestMessage = entry.getValue().get(index).getKey();
              allResponse.add(buildFailureResponse(
                  requestMessage,
                  ErrorCodes.SERVER_ERROR,
                  ""
              ));
            }
          }
        }
      });
    }
  }

  private ServiceResponseMessage buildFailureResponse(
      ServiceRequestMessage requestMessage, String errorCode, String msg) {
    return new ServiceResponseMessage(
        CachedTimestamp.nowMilliseconds(),
        requestMessage,
        Result.failWithCodeAndMessage(
            errorCode,
            msg
        )
    );
  }

  public Result query(String type, String domainId, List<String> properties) {
    if (!allDataAccess.containsKey(type)) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.UNKNOWN_TYPE,
          String.format("Unknown domain type: %s", type)
      );
    }

    final DomainInfoDataAccess dataAccess = allDataAccess.get(type);
    final Optional<DomainInfo> domainInfoOptional = dataAccess.query(type, domainId, properties);
    if (!domainInfoOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND,
          String.format("Domain info (type=%s, id=%s) not found", type, domainId)
      );
    }

    final DomainInfo domainInfo = domainInfoOptional.get();
    return Result.okWithData(domainInfo.getProperties());
  }

  public Result upsert(String type, String domainId, Map<String, Object> properties) {
    if (!allDataAccess.containsKey(type)) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.UNKNOWN_TYPE,
          String.format("Unknown domain type: %s", type)
      );
    }

    // Nothing to do
    if (MapUtils.isEmpty(properties)) {
      return Result.ok();
    }

    final DomainInfoDataAccess dataAccess = allDataAccess.get(type);
    dataAccess.upsert(type, domainId, properties);
    return Result.ok();
  }

  /**
   * 通过调用存储层，对数据进行扫描
   *
   * @param type DomainInfo类型
   * @param scanConfiguration 扫描配置
   */
  public synchronized Result scanAll(
      String type, DomainInfoScanConfiguration scanConfiguration) {
    if (!allDataAccess.containsKey(type)) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.UNKNOWN_TYPE, String.format("Unknown domain type: %s", type));
    }

    final String mark = scanConfiguration.getMark();
    if (scanStopMarks.putIfAbsent(mark, false) != null) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.SCAN_MARK_EXISTED, String.format("The scan mark %s already existed!", mark));
    }

    final DomainInfoDataAccess dataAccess = allDataAccess.get(type);
    scanExecutor.execute(() -> scanAll(
        mark, type, scanConfiguration, dataAccess));

    return Result.ok();
  }

  private void scanAll(
      String mark,
      String type,
      DomainInfoScanConfiguration scanConfiguration,
      DomainInfoDataAccess dataAccess) {
    final int limit = scanConfiguration.getLimit();
    final int logPerCount = scanConfiguration.getLogPerCount();
    final EventMappingConfiguration eventMappingConfiguration = scanConfiguration
        .getEventMappingConfiguration();
    final List<DomainInfo> domainInfoList = new LinkedList<>();
    final List<Message> messages = new LinkedList<>(); // messages collector
    final VariableHolder<Integer> allScannedNum = new VariableHolder<>(0);

    logger.info(String.format("Domain info scan [%s] start!", mark));

    try {
      Optional<String> cursorOptional = Optional.of("0");
      do {
        if (scanStopMarks.getOrDefault(mark, false)) {
          logger.info(String.format("The domain info scan operation '%s' stopped", mark));
          scanStopMarks.remove(mark);
          return;
        }

        try {
          cursorOptional = dataAccess.scan(
              cursorOptional.get(),
              type,
              scanConfiguration.getProperties(),
              scanConfiguration.getConditions(),
              scanConfiguration.getBatchSize(),
              context -> {
                int num = allScannedNum.getVar();
                DomainInfo currentDomain = context.currentDomainInfo();
                domainInfoList.add(currentDomain);

                if (logPerCount != 0 && num % logPerCount == 0) {
                  scanDomainLogger.info(String.format(
                      "%s - %s - %s - %s",
                      mark,
                      type,
                      currentDomain.getDomainId(),
                      new JSONObject(currentDomain.getProperties()).toJSONString()
                  ));
                }

                if (eventMappingConfiguration != null) {
                  messages.add(domainInfo2Message(eventMappingConfiguration, currentDomain));
                }

                allScannedNum.setVar(num + 1);
              }
          );

          if (CollectionUtils.isNotEmpty(domainInfoList)) {
            if (scanConfiguration.isRemove()) {
              dataAccess.batchRemove(domainInfoList);
            }
            domainInfoList.clear();
          }

          if (CollectionUtils.isNotEmpty(messages)) {
            sendMessages(eventMappingConfiguration, messages);
            messages.clear();
          }

          if (limit > 0 && allScannedNum.getVar() >= limit) {
            break;
          }

          if (scanConfiguration.getSleepTime() > 0) {
            Sleeper.sleep(scanConfiguration.getSleepTime());
          }
        } catch (Exception e) {
          logger.error(e.getMessage(), e);
        }

      } while (cursorOptional.isPresent());

      logger.info(String.format(
          "Domain info scan [%s] finished! All scanned num: %d", mark, allScannedNum.getVar()));
    } finally {
      scanStopMarks.remove(mark);
    }
  }

  // Trans domain info to message
  private Message domainInfo2Message(
      EventMappingConfiguration eventMappingConfiguration, DomainInfo domainInfo) {
    final String eventType = eventMappingConfiguration.getType();
    final int activityId = eventMappingConfiguration.getActivityId();
    final String domainId = domainInfo.getDomainId();
    final Map<String, Object> domainProperties = domainInfo.getProperties();
    final Map<String, Object> extraAttributes = MapUtils.isEmpty(
        eventMappingConfiguration.getExtraAttributes()) ?
        Collections.emptyMap() : eventMappingConfiguration.getExtraAttributes();

    final Map<String, Object> attributes = new HashMap<>(
        domainProperties.size() + extraAttributes.size());
    attributes.putAll(domainProperties);
    attributes.putAll(extraAttributes);

    return new BasicActivityThreadMessage(
        eventType,
        serviceName,
        eventMappingConfiguration.getSendTo(),
        activityId,
        domainId,
        attributes,
        CachedTimestamp.nowMilliseconds()
    );
  }

  // 将转换完毕的消息发送到目的地
  private void sendMessages(
      EventMappingConfiguration eventMappingConfiguration, List<Message> messages)
      throws MessageBusNotAvailableException {
    final String targetService = eventMappingConfiguration.getSendTo();
    final ServiceRunnerIntegrationPlan serviceRunnerIntegrationPlan = IntegrationPlanFactory
        .currentPlan();
    final ServiceMetaManager serviceMetaManager = serviceRunnerIntegrationPlan
        .getServiceMetaManager();
    final MessageBusManager messageBusManager = serviceRunnerIntegrationPlan.getMessageBusManager();
    final Optional<ServiceMeta> serviceMetaOptional = serviceMetaManager
        .getServiceMetaByName(targetService);
    if (!serviceMetaOptional.isPresent()) {
      throw new RuntimeException(String.format(
          "Target service %s not found", targetService));
    }
    final ServiceMeta serviceMeta = serviceMetaOptional.get();
    final String messageBusName = serviceMeta.getMessageBus();
    final Optional<MessageBus> messageBusOptional = messageBusManager
        .getMessageBusByName(messageBusName);
    if (!messageBusOptional.isPresent()) {
      throw new RuntimeException(String.format(
          "The message bus %s of service %s is not found",
          messageBusName,
          targetService
      ));
    }
    final MessageBus messageBus = messageBusOptional.get();
    messageBus.write(messages);
  }

  // 停止扫描
  public void stopScan(String mark) {
    this.scanStopMarks.put(mark, true);
  }

  // 获取正在扫描的标记
  public Collection<String> getScanningMarks() {
    return scanStopMarks.keySet();
  }

  interface ArgNames {

    String TYPE = "type";

    String OPT = "opt";

    String ID = "id";

    String PROPERTIES = "properties";
  }

  interface Operations {

    String QUERY = "query";

    String UPSERT = "upsert";
  }

  interface ConfigItems {

    String NAME = "name";

    String DOMAIN_TYPES = "domain_types";
  }

  interface DomainTypeConfigItems {

    String TYPE = "type";

    String STORAGE = "storage";
  }

  interface ErrorCodes {

    String NO_TYPE = "no_type";

    String UNKNOWN_TYPE = "unknown_type";

    String UNKNOWN_OPT = "unknown_opt";

    String SERVER_ERROR = "err";

    String SCAN_MARK_EXISTED = "scan_mark_existed";

    String NOT_FOUND = "not_found";
  }
}
