package playwell.activity.thread;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.activity.thread.message.MigrateActivityThreadMessage;
import playwell.activity.thread.message.RemoveActivityThreadMessage;
import playwell.clock.CachedTimestamp;
import playwell.common.EasyMap;
import playwell.message.Message;

/**
 * 基于内存存储的ActivityThreadPool
 *
 * @author chihongze@gmail.com
 */
public class MemoryActivityThreadPool extends BaseActivityThreadPool {

  private static final Logger logger = LogManager.getLogger(MemoryActivityThreadPool.class);

  private final Lock scanLock = new ReentrantLock();

  private ConcurrentMap<Integer, ConcurrentMap<String, ActivityThread>> allThreads;

  private int domainIdLevelCapacity;

  // 停止扫描标记
  private volatile boolean stopScan = false;

  public MemoryActivityThreadPool() {

  }

  @Override
  protected void initConfig(EasyMap configuration) {
    this.allThreads = new ConcurrentHashMap<>(
        configuration.getInt(ConfigItems.ACTIVITY_LEVEL_CAPACITY, 10));
    this.domainIdLevelCapacity = configuration.getInt(
        ConfigItems.DOMAIN_ID_LEVEL_CAPACITY, 1000);
  }

  @Override
  public void upsertActivityThread(ActivityThread activityThread) {
    upsert(activityThread);
    sync(activityThread);
  }

  private void upsert(ActivityThread activityThread) {
    activityThread.setUpdatedOn(CachedTimestamp.nowMilliseconds());
    final ConcurrentMap<String, ActivityThread> activityThreads = allThreads.computeIfAbsent(
        activityThread.getActivity().getId(),
        activityId -> new ConcurrentHashMap<>(domainIdLevelCapacity));
    if (REMOVE_STATUS.contains(activityThread.getStatus())) {
      activityThreads.remove(activityThread.getDomainId());
    } else {
      activityThreads.put(activityThread.getDomainId(), activityThread);
    }
    doReplication(activityThread);
  }

  @Override
  public Optional<ActivityThread> getActivityThread(int activityId, String domainId) {
    if (!allThreads.containsKey(activityId)) {
      return Optional.empty();
    }
    return Optional.ofNullable(allThreads.get(activityId).get(domainId));
  }

  @Override
  public Map<String, ActivityThread> multiGetActivityThreads(
      int activityId, Collection<String> domainIdCollection) {
    if (CollectionUtils.isEmpty(domainIdCollection)) {
      return Collections.emptyMap();
    }

    final ConcurrentMap<String, ActivityThread> activityThreads = allThreads.get(activityId);
    if (MapUtils.isEmpty(activityThreads)) {
      return Collections.emptyMap();
    }

    final Map<String, ActivityThread> result = Maps
        .newHashMapWithExpectedSize(domainIdCollection.size());
    for (String domainId : domainIdCollection) {
      if (activityThreads.containsKey(domainId)) {
        result.put(domainId, activityThreads.get(domainId));
      }
    }
    return result;
  }

  @Override
  public Collection<ActivityThread> multiGetActivityThreads(
      Collection<Pair<Integer, String>> identifiers) {
    if (CollectionUtils.isEmpty(identifiers)) {
      return Collections.emptyList();
    }

    final List<ActivityThread> result = new ArrayList<>(identifiers.size());

    for (Pair<Integer, String> identifier : identifiers) {
      final ConcurrentMap<String, ActivityThread> threads = allThreads.get(identifier.getKey());
      if (MapUtils.isEmpty(threads) || !threads.containsKey(identifier.getValue())) {
        continue;
      }
      result.add(threads.get(identifier.getValue()));
    }

    return result;
  }

  @Override
  public void scanAll(ActivityThreadScanConsumer consumer) {
    // 获取锁失败
    if (!scanLock.tryLock()) {
      logger.warn("Get scan activity thread lock failure!");
      return;
    }

    this.stopScan = false;

    try {
      logger.info("Start scan activity thread...");

      if (MapUtils.isEmpty(allThreads)) {
        logger.warn("There are no activity threads to scan!");
        return;
      }

      int scanned = 0;
      Multiset<Integer> activityScannedNum = HashMultiset.create();
      for (ConcurrentMap<String, ActivityThread> threads : allThreads.values()) {
        for (ActivityThread thread : threads.values()) {
          ++scanned;
          activityScannedNum.add(thread.getActivity().getId());

          if (stopScan) {
            logger.info(String.format(
                "Scan activity thread stopped! All scanned num: %d, Activity scanned num: %s",
                scanned,
                activityScannedNum
            ));
            consumer.onStop();
            return;
          }

          try {
            consumer.accept(new MemoryActivityThreadScanContext(threads, thread, scanned));
          } catch (Exception e) {
            logger.error("Scan activity thread error!", e);
          }
          if (scanned % 10000 == 0) {
            logger.info(String.format("Scanned activity threads num: %d", scanned));
          }
        }
      }

      logger.info(String.format("Scan finished! All scanned num: %d, Activity scanned num: %s",
          scanned,
          activityScannedNum
      ));

      consumer.onEOF();
    } catch (Exception e) {
      logger.error("Error happened when scanning activity thread", e);
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

    activityThreads.forEach(this::upsert);
  }

  @Override
  public void applyReplicationMessages(Collection<Message> messages) {
    if (CollectionUtils.isEmpty(messages)) {
      return;
    }

    for (Message message : messages) {
      if (message instanceof MigrateActivityThreadMessage) {
        // 处理迁移类型的消息
        final MigrateActivityThreadMessage migrateActivityThreadMessage =
            (MigrateActivityThreadMessage) message;
        final ActivityThread activityThread = migrateActivityThreadMessage.getActivityThread();
        final ConcurrentMap<String, ActivityThread> activityThreads = allThreads.computeIfAbsent(
            activityThread.getActivity().getId(),
            activityId -> new ConcurrentHashMap<>(domainIdLevelCapacity));
        activityThreads.put(activityThread.getDomainId(), activityThread);
      } else if (message instanceof RemoveActivityThreadMessage) {
        // 处理删除类型的消息
        final RemoveActivityThreadMessage removeActivityThreadMessage =
            (RemoveActivityThreadMessage) message;
        final int activityId = removeActivityThreadMessage.getActivityId();
        final String domainId = removeActivityThreadMessage.getDomainId();
        final ConcurrentMap<String, ActivityThread> activityThreads = allThreads.remove(activityId);
        if (MapUtils.isNotEmpty(activityThreads)) {
          activityThreads.remove(domainId);
        } else {
          logger.error(String.format(
              "Unknown replication message type: %s", message.getType()));
        }
      }
    }
  }

  interface ConfigItems {

    String ACTIVITY_LEVEL_CAPACITY = "activity_level_capacity";

    String DOMAIN_ID_LEVEL_CAPACITY = "domain_id_level_capacity";
  }

  // Scan Context
  private class MemoryActivityThreadScanContext implements ScanActivityThreadContext {

    final Map<String, ActivityThread> threads;

    final ActivityThread activityThread;

    final int allScannedNum;

    MemoryActivityThreadScanContext(
        Map<String, ActivityThread> threads, ActivityThread activityThread, int allScannedNum) {
      this.threads = threads;
      this.activityThread = activityThread;
      this.allScannedNum = allScannedNum;
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
      this.threads.remove(this.activityThread.getDomainId());
      sendRemoveReplicationMessage(activityThread);
    }

    @Override
    public void stop() {
      stopScan();
    }
  }
}
