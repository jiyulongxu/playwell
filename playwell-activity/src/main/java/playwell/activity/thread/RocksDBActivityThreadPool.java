package playwell.activity.thread;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.primitives.Ints;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.activity.Activity;
import playwell.activity.ActivityManager;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.definition.ActivityDefinitionManager;
import playwell.activity.thread.message.MigrateActivityThreadMessage;
import playwell.activity.thread.message.RemoveActivityThreadMessage;
import playwell.clock.CachedTimestamp;
import playwell.common.EasyMap;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.message.Message;
import playwell.message.MessageDispatcherListener;
import playwell.storage.rocksdb.RocksDBHelper;
import playwell.storage.rocksdb.RocksDBOperation;
import playwell.util.VariableHolder;

/**
 * 基于RocksDB存储的ActivityThreadPool 支持direct和buffer两种写入方式，当采用direct的时候，会直接将ActivityThread
 * put到ActivityThreadPool 当采用buffer时，会先将修改加入到缓冲，当ActivityRunner执行完毕一个消费循环时，再将buffer批量写入到RocksDB当中。
 *
 * @author chihongze@gmail.com
 */
public class RocksDBActivityThreadPool extends BaseActivityThreadPool implements
    MessageDispatcherListener {

  private static final Logger logger = LogManager.getLogger(RocksDBActivityThreadPool.class);

  // Scan lock
  private final Lock scanLock = new ReentrantLock();

  // 使用的列族名称
  private String columnFamilyName;

  // ActivityThreadBuffer
  private Map<Pair<Integer, String>, ActivityThread> activityThreadBuffer;

  // 停止扫描标记
  private volatile boolean stopScan = false;

  public RocksDBActivityThreadPool() {

  }

  @Override
  protected void initConfig(EasyMap configuration) {
    final EasyMap columnFamilyConfig = configuration.getSubArguments(
        ConfigItems.COLUMN_FAMILY);
    this.columnFamilyName = columnFamilyConfig.getString(
        ConfigItems.COLUMN_FAMILY_NAME,
        ConfigItems.DEFAULT_COLUMN_FAMILY_NAME_VALUE);

    boolean sync = configuration.getBoolean(ConfigItems.DIRECT,
        ConfigItems.DEFAULT_DIRECT_VALUE);
    if (!sync) {
      int bufferInitSize = configuration.getInt(
          ConfigItems.BUFFER_INIT_SIZE, ConfigItems.DEFAULT_BUFFER_INIT_SIZE);
      activityThreadBuffer = new ConcurrentHashMap<>(bufferInitSize);
    }
  }

  /**
   * 写入新的ActivityThread状态 如果没有配置buffer，那么直接转化为bytes写入到RocksDB中 如果有配置buffer，则将对象写入到buffer中
   *
   * @param activityThread 要操作的ActivityThread实例
   */
  @Override
  public void upsertActivityThread(ActivityThread activityThread) {
    activityThread.setUpdatedOn(CachedTimestamp.nowMilliseconds());
    if (activityThreadBuffer == null) {
      write(RocksDBHelper.useColumnFamily(columnFamilyName), activityThread);
    } else {
      activityThreadBuffer.put(
          Pair.of(activityThread.getActivity().getId(), activityThread.getDomainId()),
          activityThread
      );
    }
    doReplication(activityThread);
    sync(activityThread);
  }

  @Override
  public Optional<ActivityThread> getActivityThread(int activityId, String domainId) {
    byte[] key = getKey(activityId, domainId);
    byte[] value = RocksDBHelper.useColumnFamily(columnFamilyName).getBytes(key);
    if (value == null) {
      return Optional.empty();
    } else {
      return Optional.ofNullable(bytes2ActivityThread(
          getActivityDefinitionManager(), getActivityManager(), key, value));
    }
  }

  @Override
  public Map<String, ActivityThread> multiGetActivityThreads(int activityId,
      Collection<String> domainIdCollection) {
    if (CollectionUtils.isEmpty(domainIdCollection)) {
      return Collections.emptyMap();
    }

    final List<byte[]> keys = domainIdCollection.stream()
        .map(did -> getKey(activityId, did)).collect(Collectors.toCollection(LinkedList::new));

    final Map<byte[], byte[]> rawRecords = RocksDBHelper.useColumnFamily(columnFamilyName)
        .multiGet(keys);
    if (MapUtils.isEmpty(rawRecords)) {
      return Collections.emptyMap();
    }

    final ActivityDefinitionManager activityDefinitionManager = getActivityDefinitionManager();
    final ActivityManager activityManager = getActivityManager();
    return rawRecords.entrySet().stream()
        .map(entry -> bytes2ActivityThread(
            activityDefinitionManager, activityManager, entry.getKey(), entry.getValue()))
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(ActivityThread::getDomainId, Function.identity()));
  }

  @Override
  public Collection<ActivityThread> multiGetActivityThreads(
      Collection<Pair<Integer, String>> identifiers) {
    if (CollectionUtils.isEmpty(identifiers)) {
      return Collections.emptyList();
    }
    final List<byte[]> keys = identifiers.stream()
        .map(pair -> getKey(pair.getKey(), pair.getValue()))
        .collect(Collectors.toCollection(LinkedList::new));
    final Map<byte[], byte[]> rawRecords = RocksDBHelper.useColumnFamily(columnFamilyName)
        .multiGet(keys);
    if (MapUtils.isEmpty(rawRecords)) {
      return Collections.emptyList();
    }
    final ActivityDefinitionManager activityDefinitionManager = getActivityDefinitionManager();
    final ActivityManager activityManager = getActivityManager();
    return rawRecords.entrySet().stream().map(entry -> bytes2ActivityThread(
        activityDefinitionManager,
        activityManager,
        entry.getKey(),
        entry.getValue())
    ).collect(Collectors.toCollection(LinkedList::new));
  }

  @Override
  public void scanAll(ActivityThreadScanConsumer consumer) {
    if (!scanLock.tryLock()) {
      logger.warn("Get scan activity thread lock failure!");
      return;
    }

    this.stopScan = false;

    IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    ActivityDefinitionManager activityDefinitionManager = (ActivityDefinitionManager) integrationPlan
        .getTopComponent(TopComponentType.ACTIVITY_DEFINITION_MANAGER);
    ActivityManager activityManager = (ActivityManager) integrationPlan
        .getTopComponent(TopComponentType.ACTIVITY_MANAGER);

    try {
      logger.info("Start scanning activity threads...");

      final VariableHolder<Integer> scannedNumCounter = new VariableHolder<>(0);
      final Multiset<Integer> activityScannedNum = HashMultiset.create();

      RocksDBHelper
          .useColumnFamily(columnFamilyName)
          .iterateFromFirstWithConsumer(
              (keyBytes, valueBytes) -> stopScan,
              (keyBytes, valueBytes) -> {
                final int allScannedNum = scannedNumCounter.getVar() + 1;
                scannedNumCounter.setVar(allScannedNum);
                try {
                  final ActivityThread activityThread = bytes2ActivityThread(
                      activityDefinitionManager, activityManager, keyBytes, valueBytes);
                  if (activityThread == null) {
                    return;
                  }
                  activityScannedNum.add(activityThread.getActivity().getId());
                  consumer.accept(new RocksDBActivityThreadScanContext(
                      activityThread, allScannedNum, keyBytes));
                } catch (Exception e) {
                  logger.error("Scan activity thread error!", e);
                }
              },
              true
          );

      if (stopScan) {
        logger.info(String.format(
            "Scan activity thread stopped! All scanned num: %d, Activity scanned num: %s",
            scannedNumCounter.getVar(),
            activityScannedNum
        ));
        consumer.onStop();
        return;
      }

      logger.info(String.format(
          "Scan activity thread finished! All scanned num: %d, Activity scanned num: %s",
          scannedNumCounter.getVar(),
          activityScannedNum
      ));
      consumer.onEOF();
    } finally {
      scanLock.unlock();
    }
  }

  @Override
  public void stopScan() {
    this.stopScan = true;
  }

  @Override
  public void batchSaveActivityThreads(Collection<ActivityThread> activityThreads) {
    if (CollectionUtils.isEmpty(activityThreads)) {
      return;
    }

    final RocksDBOperation rocksDBOperation = RocksDBHelper
        .useColumnFamily(columnFamilyName)
        .beginWriteBatch();
    activityThreads.forEach(activityThread -> {
      write(rocksDBOperation, activityThread);
      doReplication(activityThread);
    });
    rocksDBOperation.endWriteBatch();
  }

  @Override
  public void applyReplicationMessages(Collection<Message> messages) {
    if (CollectionUtils.isEmpty(messages)) {
      return;
    }

    final RocksDBOperation rocksDBOperation = RocksDBHelper
        .useColumnFamily(columnFamilyName)
        .beginWriteBatch();

    for (Message message : messages) {
      if (message instanceof MigrateActivityThreadMessage) {
        final MigrateActivityThreadMessage migrateActivityThreadMessage =
            (MigrateActivityThreadMessage) message;
        final ActivityThread activityThread = migrateActivityThreadMessage.getActivityThread();
        rocksDBOperation.put(getKey(activityThread), activityThread2Bytes(activityThread));
      } else if (message instanceof RemoveActivityThreadMessage) {
        final RemoveActivityThreadMessage removeActivityThreadMessage =
            (RemoveActivityThreadMessage) message;
        final int activityId = removeActivityThreadMessage.getActivityId();
        final String domainId = removeActivityThreadMessage.getDomainId();
        rocksDBOperation.delete(getKey(activityId, domainId));
      }
    }
    rocksDBOperation.endWriteBatch();
  }

  /**
   * 消费掉Buffer所积累下的所有ActivityThread
   */
  @Override
  public void afterLoop() {
    if (MapUtils.isEmpty(activityThreadBuffer)) {
      return;
    }

    final RocksDBOperation rocksDBOperation = RocksDBHelper
        .useColumnFamily(columnFamilyName)
        .beginWriteBatch();

    activityThreadBuffer.values().forEach(
        activityThread -> write(rocksDBOperation, activityThread));

    rocksDBOperation.endWriteBatch();
    activityThreadBuffer.clear();
  }

  private ActivityManager getActivityManager() {
    IntegrationPlan plan = IntegrationPlanFactory.currentPlan();
    return (ActivityManager) plan.getTopComponent(TopComponentType.ACTIVITY_MANAGER);
  }

  private ActivityDefinitionManager getActivityDefinitionManager() {
    IntegrationPlan plan = IntegrationPlanFactory.currentPlan();
    return (ActivityDefinitionManager) plan
        .getTopComponent(TopComponentType.ACTIVITY_DEFINITION_MANAGER);
  }

  /**
   * 获取基于bytes的activity key 前4byte固定用于存储activityId 剩余的byte都是用于存储DomainId
   */
  private byte[] getKey(ActivityThread activityThread) {
    return getKey(activityThread.getActivity().getId(), activityThread.getDomainId());
  }

  private byte[] getKey(int activityId, String domainId) {
    final byte[] activityIdBytes = Ints.toByteArray(activityId);
    final byte[] domainIdBytes = domainId.getBytes();
    final byte[] keyBytes = new byte[4 + domainIdBytes.length];
    System.arraycopy(activityIdBytes, 0, keyBytes, 0, 4);
    System.arraycopy(domainIdBytes, 0, keyBytes, 4, domainIdBytes.length);
    return keyBytes;
  }

  /**
   * 将ActivityThread转化为JSON bytes
   * <pre>
   *  [version, status, currentAction, createdOn, {context}]
   * </pre>
   */
  private byte[] activityThread2Bytes(ActivityThread activityThread) {
    return JSONArray.toJSONBytes(new Object[]{
        activityThread.getActivityDefinition().getVersion(),
        activityThread.getStatus().getCode(),
        activityThread.getCurrentAction(),
        activityThread.getUpdatedOn(),
        activityThread.getCreatedOn(),
        activityThread.getContext()
    });
  }

  /**
   * 将bytes转化为ActivityThread对象
   */
  private ActivityThread bytes2ActivityThread(ActivityDefinitionManager activityDefinitionManager,
      ActivityManager activityManager, byte[] keyBytes, byte[] valueBytes) {
    final int activityId = Ints.fromByteArray(ArrayUtils.subarray(keyBytes, 0, 4));
    final Optional<Activity> activityOptional = activityManager.getActivityById(activityId);
    if (!activityOptional.isPresent()) {
      logger.error(String.format("Unknown activity id: %d", activityId));
      return null;
    }
    final Activity activity = activityOptional.get();
    final String domainId = new String(ArrayUtils.subarray(keyBytes, 4, keyBytes.length));
    final JSONArray jsonArray = (JSONArray) JSONArray.parse(valueBytes);
    final String version = jsonArray.getString(0);
    final Optional<ActivityDefinition> activityDefinitionOptional = activityDefinitionManager
        .getActivityDefinition(activity.getDefinitionName(), version);
    if (!activityDefinitionOptional.isPresent()) {
      logger.error(String.format(
          "Could not found the activity definition, name: '%s', version: '%s'",
          activity.getDefinitionName(),
          version
      ));
      return null;
    }
    final ActivityDefinition activityDefinition = activityDefinitionOptional.get();
    final Optional<ActivityThreadStatus> statusOptional = ActivityThreadStatus
        .valueOfByCode(jsonArray.getInteger(1));
    if (!statusOptional.isPresent()) {
      throw new ActivityThreadRuntimeException(
          SysScheduleErrorCodes.INVALID_THREAD_STATUS,
          String.format("Unknown activity thread status code: %d", jsonArray.getInteger(0))
      );
    }
    final ActivityThreadStatus status = statusOptional.get();
    final String currentAction = jsonArray.getString(2);
    final long updatedOn = jsonArray.getLong(3);
    final long createdOn = jsonArray.getLong(4);
    final JSONObject contextJson = jsonArray.getJSONObject(5);
    Map<String, Object> context;
    if (contextJson == null || contextJson.size() == 0) {
      context = new HashMap<>();
    } else {
      context = new HashMap<>(contextJson.getInnerMap());
    }

    return new ActivityThread(
        activity,
        activityDefinition,
        domainId,
        status,
        currentAction,
        updatedOn,
        createdOn,
        context
    );
  }

  private void write(RocksDBOperation rocksDBOperation, ActivityThread activityThread) {
    if (REMOVE_STATUS.contains(activityThread.getStatus())) {
      rocksDBOperation.delete(getKey(activityThread));
    } else {
      rocksDBOperation.put(
          getKey(activityThread), activityThread2Bytes(activityThread));
    }
  }

  /**
   * 配置项
   */
  public interface ConfigItems {

    // 列族相关配置
    String COLUMN_FAMILY = "column_family";

    // 列族名称
    String COLUMN_FAMILY_NAME = "name";
    String DEFAULT_COLUMN_FAMILY_NAME_VALUE = "clock";

    // 是否同步写入
    String DIRECT = "direct";
    boolean DEFAULT_DIRECT_VALUE = false;

    // Buffer大小
    String BUFFER_INIT_SIZE = "buffer_init_size";
    int DEFAULT_BUFFER_INIT_SIZE = 1024;
  }

  private class RocksDBActivityThreadScanContext implements ScanActivityThreadContext {

    private final ActivityThread activityThread;

    private final int allScannedNum;

    private final byte[] keyBytes;

    RocksDBActivityThreadScanContext(
        ActivityThread activityThread, int allScannedNum, byte[] keyBytes) {
      this.activityThread = activityThread;
      this.allScannedNum = allScannedNum;
      this.keyBytes = keyBytes;
    }

    @Override
    public ActivityThread getCurrentActivityThread() {
      return this.activityThread;
    }

    @Override
    public int getAllScannedNum() {
      return allScannedNum;
    }

    @Override
    public void remove() {
      RocksDBHelper.useColumnFamily(columnFamilyName).delete(keyBytes);
      sendRemoveReplicationMessage(activityThread);
    }

    @Override
    public void stop() {
      stopScan();
    }
  }
}
