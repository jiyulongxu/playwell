package playwell.clock;


import com.alibaba.fastjson.JSONArray;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import playwell.message.Message;
import playwell.message.MessageDispatcherListener;
import playwell.storage.rocksdb.RocksDBHelper;
import playwell.storage.rocksdb.RocksDBOperation;
import playwell.util.VariableHolder;

/**
 * 基于RocksDB做存储的时钟服务，支持buffer和direct两种消息写入方式 buffer写入方式会提交到缓冲，当ActivityRunner
 * 执行一次消费循环结束后，再通过WriteBatch提交到RocksDB
 *
 * direct会不经过缓冲，每次直接写入到RocksDB
 *
 * @author chihongze@gmail.com
 */
public class RocksDBClock extends BaseClock implements MessageDispatcherListener {

  private static final Logger logger = LogManager.getLogger(RocksDBClock.class);

  private final Lock scanLock = new ReentrantLock();

  private String columnFamilyName;

  private ConcurrentLinkedQueue<ClockMessage> clockMessageBuffer = null;

  private volatile boolean stopMark = false;

  private long consumed;

  private long compact;

  public RocksDBClock() {

  }

  /**
   * 初始化配置和RocksDB列族
   * <pre>
   * clock:
   *   column_family:
   *   sync: false
   * </pre>
   *
   * @param configuration 配置信息
   */
  @Override
  public void initConfig(EasyMap configuration) {

    final EasyMap columnFamilyConfig = configuration.getSubArguments(ConfigItems.COLUMN_FAMILY);
    this.columnFamilyName = columnFamilyConfig.getString(
        ConfigItems.COLUMN_FAMILY_NAME, ConfigItems.DEFAULT_COLUMN_FAMILY_NAME_VALUE);

    boolean sync = configuration.getBoolean(ConfigItems.DIRECT, ConfigItems.DEFAULT_DIRECT_VALUE);
    if (!sync) {
      clockMessageBuffer = new ConcurrentLinkedQueue<>();
    }

    this.compact = configuration.getLong(ConfigItems.COMPACT, ConfigItems.DEFAULT_COMPACT);
  }

  @Override
  public void registerClockMessage(ClockMessage clockMessage) {
    if (clockMessageBuffer == null) {
      RocksDBHelper.useColumnFamily(columnFamilyName).merge(
          getKey(clockMessage), message2Bytes(clockMessage));
      sync(clockMessage);
    } else {
      clockMessageBuffer.add(clockMessage);
    }
    sendAddReplicationMessage(clockMessage);
  }

  @Override
  public Collection<ClockMessage> fetchClockMessages(long untilTimePoint) {
    final List<ClockMessage> events = new LinkedList<>();
    consumeWithTimePoint(untilTimePoint, events::add);
    return events;
  }

  @Override
  public void consumeClockMessage(long untilTimePoint, Consumer<ClockMessage> consumer) {
    this.consumeWithTimePoint(untilTimePoint, consumer);
  }

  @Override
  public void clean(long untilTimePoint) {
    this.removeRange(untilTimePoint);
    sendCleanReplicationMessage(untilTimePoint);
  }

  @Override
  public void scanAll(ClockMessageScanConsumer consumer) {
    logger.info("Ready to scan clock messages");
    final Thread thread = new Thread(() -> {
      if (!scanLock.tryLock()) {
        logger.warn("Get scan clock message lock failure!");
        return;
      }

      final VariableHolder<Integer> allScannedNum = new VariableHolder<>(0);
      final Multiset<Integer> activityScannedNum = HashMultiset.create();

      try {
        consume((k, v) -> stopMark, clockMessage -> {
          allScannedNum.setVar(allScannedNum.getVar() + 1);
          activityScannedNum.add(clockMessage.getActivityId());

          try {
            consumer.accept(new DefaultScanClockMessageContext(
                this, allScannedNum.getVar(), clockMessage));
          } catch (Exception e) {
            logger.error("Error happened when scanning clock message", e);
          }

          if (allScannedNum.getVar() % 10000 == 0) {
            logger
                .info(String.format(
                    "Already scanned clock messages: %d, activity clock message num: %s",
                    allScannedNum.getVar(),
                    activityScannedNum
                ));
          }
        });

        if (stopMark) {
          logger.info(String.format(
              "Scan clock messages stopped! All scanned num: %d, activity clock message num: %s",
              allScannedNum.getVar(),
              activityScannedNum
          ));
          consumer.onStop();
          return;
        }

        logger.info(String.format(
            "Scan clock messages finished, all scanned num: %d, activity clock message num: %s",
            allScannedNum.getVar(),
            activityScannedNum
        ));
        consumer.onEOF();
      } finally {
        this.stopMark = false;
        scanLock.unlock();
      }
    });
    thread.setDaemon(true);
    thread.start();
  }

  @Override
  public void stopScan() {
    this.stopMark = true;
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

    final RocksDBOperation rocksDBOperation = RocksDBHelper
        .useColumnFamily(columnFamilyName).beginWriteBatch();
    for (Message message : messages) {
      if (message instanceof ClockMessage) {
        final ClockMessage clockMessage = (ClockMessage) message;
        if (clockMessage.getTimePoint() <= latestTimePoint) {
          continue;
        }
        rocksDBOperation.merge(getKey(clockMessage), message2Bytes(clockMessage));
      }
    }
    rocksDBOperation.endWriteBatch();
    removeRange(latestTimePoint);
  }

  private void consumeWithTimePoint(
      long timePoint, Consumer<ClockMessage> consumer) {
    final byte[] keyBytes = Longs.toByteArray(timePoint + 1);
    RocksDBHelper.useColumnFamily(columnFamilyName).iterateFromPrevKeyWithConsumer(
        keyBytes,
        key -> Longs.fromByteArray(ArrayUtils.subarray(key, 0, 8)),
        Function.identity(),
        null,
        (timestamp, value) -> {
          JSONArray jsonArray = JSONArray.parseArray("[" + new String(value) + "]");
          for (int i = 0; i < jsonArray.size(); i++) {
            ClockMessage clockEvent = json2Message(timestamp, jsonArray.getJSONArray(i));
            consumer.accept(clockEvent);
            consumed++;
          }
        },
        false
    );
  }

  private void consume(BiPredicate<Long, byte[]> until, Consumer<ClockMessage> consumer) {
    RocksDBHelper.useColumnFamily(columnFamilyName).iterateFromFirstWithConsumer(
        key -> Longs.fromByteArray(ArrayUtils.subarray(key, 0, 8)),
        Function.identity(),
        until,
        (timestamp, value) -> {
          JSONArray jsonArray = JSONArray.parseArray("[" + new String(value) + "]");
          for (int i = 0; i < jsonArray.size(); i++) {
            ClockMessage clockEvent = json2Message(timestamp, jsonArray.getJSONArray(i));
            consumer.accept(clockEvent);
          }
        },
        true
    );
  }

  @Override
  public void afterLoop() {
    if (CollectionUtils.isEmpty(clockMessageBuffer)) {
      return;
    }

    batchSync(clockMessageBuffer);

    final RocksDBOperation rocksDBOperation = RocksDBHelper
        .useColumnFamily(columnFamilyName)
        .beginWriteBatch();

    while (!clockMessageBuffer.isEmpty()) {
      final ClockMessage clockMessage = clockMessageBuffer.poll();
      rocksDBOperation.merge(getKey(clockMessage), message2Bytes(clockMessage));
    }

    rocksDBOperation.endWriteBatch();
  }


  private void removeRange(long untilTimePoint) {
    RocksDBHelper.useColumnFamily(columnFamilyName).deleteRange(
        Longs.toByteArray(0L),
        Longs.toByteArray(untilTimePoint + 1)
    );
    if (compact != -1 && consumed >= compact) {
      RocksDBHelper.useColumnFamily(columnFamilyName).compactRange();
      consumed = 0;
    }
  }

  // Key由8个byte的时间戳和4个byte的ActivityId_DomainId hash值构成
  private byte[] getKey(ClockMessage clockMessage) {
    final byte[] keyBytes = new byte[12];
    System.arraycopy(Longs.toByteArray(clockMessage.getTimePoint()), 0, keyBytes, 0, 8);
    final int hash = String
        .format("%d_%s", clockMessage.getActivityId(), clockMessage.getDomainId())
        .hashCode();
    System.arraycopy(Ints.toByteArray(hash), 0, keyBytes, 8, 4);
    return keyBytes;
  }

  private byte[] message2Bytes(ClockMessage clockMessage) {
    if (MapUtils.isNotEmpty(clockMessage.getExtraArgs())) {
      return JSONArray.toJSONBytes(new Object[]{
          clockMessage.getSender(),
          clockMessage.getReceiver(),
          clockMessage.getActivityId(),
          clockMessage.getDomainId(),
          clockMessage.getAction(),
          clockMessage.getExtraArgs(),
          clockMessage.getTimestamp()
      });
    } else {
      return JSONArray.toJSONBytes(new Object[]{
          clockMessage.getSender(),
          clockMessage.getReceiver(),
          clockMessage.getActivityId(),
          clockMessage.getDomainId(),
          clockMessage.getAction(),
          clockMessage.getTimestamp()
      });
    }
  }

  private ClockMessage json2Message(long timepoint, JSONArray jsonArray) {
    if (jsonArray.size() > 6) {
      return new ClockMessage(
          jsonArray.getString(0),
          jsonArray.getString(1),
          timepoint,
          jsonArray.getInteger(2),
          jsonArray.getString(3),
          jsonArray.getString(4),
          jsonArray.getJSONObject(5).getInnerMap(),
          jsonArray.getLong(6)
      );
    } else {
      return new ClockMessage(
          jsonArray.getString(0),
          jsonArray.getString(1),
          timepoint,
          jsonArray.getInteger(2),
          jsonArray.getString(3),
          jsonArray.getString(4),
          Collections.emptyMap(),
          jsonArray.getLong(5)
      );
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

    // 触发合并的条数
    String COMPACT = "compact";
    int DEFAULT_COMPACT = -1;
  }
}
