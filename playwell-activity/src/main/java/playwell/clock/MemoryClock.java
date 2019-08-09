package playwell.clock;


import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import playwell.message.Message;

/**
 * 基于SkipList的Alarm服务，所有消息都会被存储在内存中，当程序终止时，消息都会丢失。 通常仅用于测试以及对事务要求不严格的场景
 *
 * @author chihongze@gmail.com
 */
public class MemoryClock extends BaseClock {

  private static final Logger logger = LogManager.getLogger(MemoryClock.class);

  private final ConcurrentSkipListMap<Long, ConcurrentLinkedQueue<ClockMessage>> allClockMessages =
      new ConcurrentSkipListMap<>();

  private final Lock scanLock = new ReentrantLock();

  private volatile boolean stopScanMark = false;

  public MemoryClock() {

  }

  @Override
  protected void initConfig(EasyMap configuration) {

  }

  @Override
  public void registerClockMessage(ClockMessage clockMessage) {
    add(clockMessage);
    sendAddReplicationMessage(clockMessage);
    sync(clockMessage);
  }

  @Override
  public Collection<ClockMessage> fetchClockMessages(long untilTimePoint) {
    final ConcurrentNavigableMap<Long, ConcurrentLinkedQueue<ClockMessage>> fetchedMessages = allClockMessages
        .headMap(untilTimePoint, true);

    if (MapUtils.isEmpty(fetchedMessages)) {
      return Collections.emptyList();
    }

    return fetchedMessages.values().stream().flatMap(ConcurrentLinkedQueue::stream)
        .collect(Collectors.toList());
  }

  @Override
  public void consumeClockMessage(long untilTimePoint, Consumer<ClockMessage> consumer) {
    final ConcurrentNavigableMap<Long, ConcurrentLinkedQueue<ClockMessage>> fetchedMessages = allClockMessages
        .headMap(untilTimePoint, true);

    if (MapUtils.isEmpty(fetchedMessages)) {
      return;
    }

    fetchedMessages.values().stream().flatMap(ConcurrentLinkedQueue::stream).forEach(consumer);
  }

  @Override
  public void clean(long untilTimePoint) {
    removeRange(untilTimePoint);
    sendCleanReplicationMessage(untilTimePoint);
  }

  @Override
  public void scanAll(ClockMessageScanConsumer consumer) {
    logger.info("Ready to scan clock messages");
    final Thread scanThread = new Thread(() -> {
      if (!scanLock.tryLock()) {
        logger.warn("Get scan clock message lock failure!");
        return;
      }

      try {
        int allScannedNum = 0;
        final Multiset<Integer> activityScannedNum = HashMultiset.create();

        for (Map.Entry<Long, ConcurrentLinkedQueue<ClockMessage>> entry : allClockMessages
            .entrySet()) {
          if (CollectionUtils.isNotEmpty(entry.getValue())) {
            for (ClockMessage clockMessage : entry.getValue()) {
              ++allScannedNum;
              activityScannedNum.add(clockMessage.getActivityId());

              if (stopScanMark) {
                break;
              }

              try {
                consumer.accept(new DefaultScanClockMessageContext(
                    this, allScannedNum, clockMessage));
              } catch (Exception e) {
                logger.error("Error happened when scanning clock messages", e);
              }

              if (allScannedNum % 10000 == 0) {
                logger.info(String.format(
                    "Already scanned num: %d, activity clock message num: %s",
                    allScannedNum,
                    activityScannedNum
                ));
              }
            }
          }

          if (stopScanMark) {
            logger.info(String.format(
                "Scan clock messages stopped! All scanned num: %d, activity clock message num: %s",
                allScannedNum,
                activityScannedNum
            ));
            consumer.onStop();
            return;
          }
        }

        logger.info(String.format(
            "Scan clock messages finished, all scanned num: %d, activity clock message num: %s",
            allScannedNum,
            activityScannedNum
        ));
        consumer.onEOF();
      } finally {
        stopScanMark = false;
        scanLock.unlock();
      }
    });
    scanThread.setDaemon(true);
    scanThread.start();
  }

  @Override
  public void stopScan() {
    this.stopScanMark = true;
  }

  @Override
  public void applyReplicationMessages(Collection<Message> messages) {
    if (CollectionUtils.isEmpty(messages)) {
      return;
    }

    // 找出CleanMessage中的最近时间点
    final long latestTimePoint = messages.stream()
        .filter(message -> CleanTimeRangeMessage.TYPE.equals(message.getType()))
        .map(message -> ((CleanTimeRangeMessage) message).getTimePoint())
        .max(Long::compare)
        .orElse(0L);

    for (Message message : messages) {
      if (message instanceof ClockMessage) {
        final ClockMessage clockMessage = (ClockMessage) message;
        if (clockMessage.getTimePoint() <= latestTimePoint) {
          continue;
        }
        add(clockMessage);
      }
    }

    if (latestTimePoint != 0) {
      this.removeRange(latestTimePoint);
    }
  }

  private void add(ClockMessage clockMessage) {
    final ConcurrentLinkedQueue<ClockMessage> messages = allClockMessages.computeIfAbsent(
        clockMessage.getTimePoint(), tp -> new ConcurrentLinkedQueue<>());
    messages.add(clockMessage);
  }

  private void removeRange(long untilTimePoint) {
    final ConcurrentNavigableMap<Long, ConcurrentLinkedQueue<ClockMessage>> fetchedMessages =
        allClockMessages.headMap(untilTimePoint, true);

    if (MapUtils.isEmpty(fetchedMessages)) {
      return;
    }

    final Iterator<Long> tpIter = fetchedMessages.keySet().iterator();
    while (tpIter.hasNext()) {
      tpIter.next();
      tpIter.remove();
    }
  }
}
