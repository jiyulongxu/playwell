package playwell.clock;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections4.MapUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.clock.RocksDBClock.ConfigItems;
import playwell.common.EasyMap;
import playwell.message.Message;
import playwell.message.bus.MessageBus;
import playwell.util.Sleeper;

/**
 * TestCase of RocksDBClock
 *
 * @author chihongze@gmail.com
 */
public class RocksDBClockTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    this.initStandardActivityRunnerIntergrationPlan(false);
  }

  /**
   * 测试Direct的方式写入消息
   */
  @Test
  public void testDirect() throws Exception {
    final RocksDBClock clock = new RocksDBClock();
    clock.init(new EasyMap(ImmutableMap.of(
        ConfigItems.DIRECT, true,
        ConfigItems.COLUMN_FAMILY_NAME, "test_clock"
    )));

    // 添加消息
    clock.registerClockMessage(ClockMessage.buildForActivity(
        System.currentTimeMillis(),
        1,
        "1",
        "test",
        ImmutableMap.of("a", 1)
    ));
    clock.registerClockMessage(ClockMessage.buildForActivity(
        System.currentTimeMillis() + 3000,
        2,
        "2",
        "test"
    ));

    // 获取消息
    Collection<ClockMessage> clockMessages = clock.fetchClockMessages(
        System.currentTimeMillis());
    Assert.assertEquals(1, clockMessages.size());
    clockMessages.forEach(clockMessage -> {
      Assert.assertEquals(1, clockMessage.getActivityId());
      Assert.assertEquals("1", clockMessage.getDomainId());
      Map<String, Object> extraArgs = clockMessage.getExtraArgs();
      Assert.assertEquals(1, extraArgs.get("a"));
    });

    // 只要不执行clean，消息就会一直存在
    clockMessages = clock.fetchClockMessages(
        System.currentTimeMillis());
    Assert.assertEquals(1, clockMessages.size());

    // 执行clean
    clock.clean(System.currentTimeMillis());
    clockMessages = clock.fetchClockMessages(
        System.currentTimeMillis() + 1);
    Assert.assertEquals(0, clockMessages.size());

    // 歇三秒，再fetch另一个消息
    TimeUnit.SECONDS.sleep(5L);
    clockMessages = clock.fetchClockMessages(
        System.currentTimeMillis());
    Assert.assertEquals(1, clockMessages.size());
    clockMessages.forEach(clockMessage -> {
      Assert.assertEquals(2, clockMessage.getActivityId());
      Assert.assertEquals("2", clockMessage.getDomainId());
      Assert.assertTrue(MapUtils.isEmpty(clockMessage.getExtraArgs()));
    });
    clock.clean(System.currentTimeMillis());
    clockMessages = clock.fetchClockMessages(
        System.currentTimeMillis());
    Assert.assertEquals(0, clockMessages.size());

    // 写100w条，性能测试
    long beginTime = System.currentTimeMillis();
    Map<Long, Set<Integer>> records = new LinkedHashMap<>();
    for (int i = 0; i < 1000000; i++) {
      ClockMessage clockMessage = ClockMessage.buildForActivity(
          System.currentTimeMillis(),
          i,
          "" + i,
          "test",
          Collections.emptyMap()
      );
      clock.registerClockMessage(clockMessage);
      records.computeIfAbsent(clockMessage.getTimePoint(), t -> new TreeSet<>())
          .add(clockMessage.getActivityId());
    }
    System.out.println("Direct write 100w used time: " + (System.currentTimeMillis() - beginTime));

    // 读100w
    beginTime = System.currentTimeMillis();
    clockMessages = clock.fetchClockMessages(System.currentTimeMillis());
    System.out.println("Read 100w used time: " + (System.currentTimeMillis() - beginTime));
    Assert.assertEquals(1000000, clockMessages.size());
    // 测试顺序是否OK
    int i = 0;
    for (Map.Entry<Long, Set<Integer>> entry : records.entrySet()) {
      Set<Integer> activityIdSet = entry.getValue();
      for (int activityId : activityIdSet) {
        Assert.assertEquals(i++, activityId);
      }
    }

    // 清理100w
    beginTime = System.currentTimeMillis();
    clock.clean(System.currentTimeMillis());
    System.out.println("Clean 100w used time: " + (System.currentTimeMillis() - beginTime));
  }

  /**
   * 测试Buffer的方式写入消息
   */
  @Test
  public void testBuffer() throws Exception {
    final RocksDBClock clock = new RocksDBClock();
    clock.init(new EasyMap(ImmutableMap.of(
        ConfigItems.DIRECT, false,
        ConfigItems.COLUMN_FAMILY_NAME, "test_clock"
    )));

    // 添加消息
    clock.registerClockMessage(ClockMessage.buildForActivity(
        System.currentTimeMillis(),
        0,
        "0",
        "test",
        ImmutableMap.of("a", 1)
    ));
    clock.registerClockMessage(ClockMessage.buildForActivity(
        System.currentTimeMillis() + 3000,
        1,
        "1",
        "test"
    ));

    // 如果没有flush，那么应该是读取不了消息的
    Collection<ClockMessage> clockMessages = clock
        .fetchClockMessages(System.currentTimeMillis());
    Assert.assertEquals(0, clockMessages.size());
    // flush一下
    clock.afterLoop();
    clockMessages = clock.fetchClockMessages(System.currentTimeMillis());
    Assert.assertEquals(1, clockMessages.size());
    clockMessages.forEach(clockMessage -> {
      Assert.assertEquals(0, clockMessage.getActivityId());
      Assert.assertEquals("0", clockMessage.getDomainId());
      Map<String, Object> extraArgs = clockMessage.getExtraArgs();
      Assert.assertEquals(1, extraArgs.get("a"));
    });

    // clean
    clock.clean(System.currentTimeMillis());
    clockMessages = clock.fetchClockMessages(System.currentTimeMillis());
    Assert.assertEquals(0, clockMessages.size());

    TimeUnit.SECONDS.sleep(5L);
    clockMessages = clock.fetchClockMessages(System.currentTimeMillis());
    Assert.assertEquals(1L, clockMessages.size());
    clockMessages.forEach(clockMessage -> {
      Assert.assertEquals(1, clockMessage.getActivityId());
      Assert.assertEquals("1", clockMessage.getDomainId());
      Assert.assertTrue(MapUtils.isEmpty(clockMessage.getExtraArgs()));
    });
    clock.clean(System.currentTimeMillis());

    // 写100w，测性能
    long beginTime = System.currentTimeMillis();
    Map<Long, Set<Integer>> records = new LinkedHashMap<>();
    for (int i = 0; i < 1000000; i++) {
      ClockMessage clockMessage = ClockMessage.buildForActivity(
          System.currentTimeMillis(),
          i,
          "" + i,
          "test"
      );
      clock.registerClockMessage(clockMessage);
      records.computeIfAbsent(clockMessage.getTimePoint(), t -> new TreeSet<>())
          .add(clockMessage.getActivityId());
    }
    System.out.println("Register used time: " + (System.currentTimeMillis() - beginTime));
    beginTime = System.currentTimeMillis();
    clock.afterLoop();
    System.out
        .println("Buffer write 100w used time: " + (System.currentTimeMillis() - beginTime));

    // 读100w
    beginTime = System.currentTimeMillis();
    clockMessages = clock.fetchClockMessages(System.currentTimeMillis());
    System.out.println("Read 100w used time: " + (System.currentTimeMillis() - beginTime));
    Assert.assertEquals(1000000, clockMessages.size());
    int i = 0;
    // 检查读取顺序
    for (Map.Entry<Long, Set<Integer>> entry : records.entrySet()) {
      Set<Integer> activityIdSet = entry.getValue();
      for (int activityId : activityIdSet) {
        Assert.assertEquals(i++, activityId);
      }
    }

    clock.clean(System.currentTimeMillis());
  }

  @Test
  public void testScanSync() throws Exception {
    final RocksDBClock clock = new RocksDBClock();
    clock.init(new EasyMap(ImmutableMap.of(
        ConfigItems.DIRECT, true,
        ConfigItems.COLUMN_FAMILY_NAME, "test_clock"
    )));

    final int num = 10000;

    makeMessages(clock, num);

    Sleeper.sleepInSeconds(1);

    clock.scanAll(new UserScanOperation(
        Collections.emptyList(),
        1,
        -1,
        "",
        Arrays.asList("sync_bus1", "sync_bus2"),
        9
    ));

    Sleeper.sleepInSeconds(3);

    this.checkMessages(num);

    clock.clean(System.currentTimeMillis());
  }

  @Test
  public void testSync() throws Exception {
    final RocksDBClock clock = new RocksDBClock();
    clock.init(new EasyMap(ImmutableMap.of(
        ConfigItems.DIRECT, true,
        ConfigItems.COLUMN_FAMILY_NAME, "test_clock",
        "sync_message_bus", Arrays.asList("sync_bus1", "sync_bus2")
    )));

    final int num = 1000;
    this.makeMessages(clock, num);
    checkMessages(num);
    clock.clean(System.currentTimeMillis());
  }

  @Test
  public void testBatchSync() throws Exception {
    final RocksDBClock clock = new RocksDBClock();
    clock.init(new EasyMap(ImmutableMap.of(
        ConfigItems.DIRECT, false,
        ConfigItems.COLUMN_FAMILY_NAME, "test_clock",
        "sync_message_bus", Arrays.asList("sync_bus1", "sync_bus2")
    )));

    final int num = 1000;
    this.makeMessages(clock, num);
    clock.afterLoop();
    checkMessages(num);
    clock.clean(System.currentTimeMillis());
  }

  private void makeMessages(Clock clock, int num) {
    for (int i = 0; i < num; i++) {
      ClockMessage clockMessage = ClockMessage.buildForActivity(
          System.currentTimeMillis(),
          i,
          Integer.toString(i),
          "test",
          Collections.emptyMap()
      );
      clock.registerClockMessage(clockMessage);
    }
  }

  private void checkMessages(int num) throws Exception {
    final MessageBus syncBus1 = getMessageBus("sync_bus1");
    final MessageBus syncBus2 = getMessageBus("sync_bus2");

    for (MessageBus messageBus : new MessageBus[]{syncBus1, syncBus2}) {
      final Collection<Message> messages = messageBus.read(num * 10);
      Assert.assertEquals(num, messages.size());
      final Set<String> domainIdSet = new HashSet<>();
      for (Message message : messages) {
        ClockMessage clockMessage = (ClockMessage) message;
        Assert.assertFalse(domainIdSet.contains(clockMessage.getDomainId()));
        domainIdSet.add(clockMessage.getDomainId());
      }
      Assert.assertEquals(num, domainIdSet.size());
    }
  }

  @After
  public void tearDown() {
    this.cleanStandardActivityRunnerIntergrationPlan(false);
  }
}
