package playwell.activity.thread;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.activity.Activity;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.thread.message.MigrateActivityThreadMessage;
import playwell.clock.CachedTimestamp;
import playwell.common.EasyMap;
import playwell.message.Message;
import playwell.message.TestUserBehaviorEvent;
import playwell.message.bus.MessageBus;
import playwell.util.Sleeper;

/**
 * TestCase for activity thread pool
 */
public class ActivityThreadPoolTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    this.initStandardActivityRunnerIntergrationPlan(true);
  }

  @Test
  public void testScan() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/receive_with_while.yml",
        new TestUserBehaviorEvent(
            "1",
            "user_behavior",
            ImmutableMap.of(
                "behavior", "提交订单",
                "amount", 100
            ),
            CachedTimestamp.nowMilliseconds()
        )
    );

    for (int i = 0; i < 100; i++) {
      sendMessage(new TestUserBehaviorEvent(
          Integer.toString(i),
          "user_behavior",
          ImmutableMap.of(
              "behavior", "提交订单",
              "amount", 100
          ),
          CachedTimestamp.nowMilliseconds()
      ));
    }

    TimeUnit.SECONDS.sleep(2L);

    final AtomicInteger counter = new AtomicInteger(0);
    activityThreadPool.scanAll(scanContext -> {
      Assert.assertEquals(counter.incrementAndGet(), scanContext.getAllScannedNum());
      System.out.println(String.format("Scanned num: %d", scanContext.getAllScannedNum()));
      System.out.println(
          String.format("Current activity thread: %s", scanContext.getCurrentActivityThread()));
    });

    // 测试终止迭代
    counter.set(0);
    activityThreadPool.scanAll(context -> {
      if (context.getAllScannedNum() == 50) {
        activityThreadPool.stopScan();
      }
      counter.incrementAndGet();
    });
    Assert.assertEquals(50, counter.get());

    // 扫描并删除DomainID为偶数的实例
    counter.set(0);
    activityThreadPool.scanAll(scanContext -> {
      Assert.assertEquals(counter.incrementAndGet(), scanContext.getAllScannedNum());
      ActivityThread activityThread = scanContext.getCurrentActivityThread();
      int userId = Integer.parseInt(activityThread.getDomainId());
      if (userId % 2 == 0) {
        scanContext.remove();
      }
    });

    Optional<ActivityThread> activityThreadOptional = activityThreadPool
        .getActivityThread(activity.getId(), "2");
    Assert.assertFalse(activityThreadOptional.isPresent());

    // 移除之后将会触发新的实例
    sendMessage(new TestUserBehaviorEvent(
        "2",
        "user_behavior",
        ImmutableMap.of(
            "behavior", "提交订单",
            "amount", 100
        ),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(2L);
    activityThreadOptional = activityThreadPool.getActivityThread(activity.getId(), "2");
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(1, activityThread.getContext().get("order_count"));

    activityThreadPool.scanAll(ScanActivityThreadContext::remove);
  }

  @Test
  public void testScanAndSync() throws Exception {
    final ActivityDefinition activityDefinition = this.createActivityDefinition(
        "docs/sample/test_definitions/receive_with_foreach.yml");
    final Activity activity = this.createActivity(
        "test_foreach_receive",
        "Receive with foreach",
        Collections.emptyMap()
    );

    Sleeper.sleepInSeconds(2);

    for (int i = 0; i < 10000; i++) {
      sendMessage(new TestUserBehaviorEvent(
          Integer.toString(i),
          "注册成功",
          Collections.emptyMap(),
          CachedTimestamp.nowMilliseconds()
      ));
    }

    Sleeper.sleepInSeconds(3);

    activityThreadPool.scanAll(new UserScanOperation(
        Collections.emptyList(),
        -1,
        false,
        false,
        "",
        1,
        Arrays.asList("sync_bus1", "sync_bus2"),
        7
    ));

    final MessageBus syncBus1 = getMessageBus("sync_bus1");
    final MessageBus syncBus2 = getMessageBus("sync_bus2");

    final Collection<Message> syncMessages1 = syncBus1.read(100000);
    final Collection<Message> syncMessages2 = syncBus2.read(100000);
    for (Collection<Message> messages : Arrays.asList(syncMessages1, syncMessages2)) {
      Assert.assertEquals(10000, messages.size());
      final Set<String> domainIdSet = new HashSet<>();
      for (Message message : messages) {
        final ActivityThread activityThread = ((MigrateActivityThreadMessage) message)
            .getActivityThread();
        Assert.assertEquals(activityDefinition.getName(), activity.getDefinitionName());
        Assert.assertEquals(activity.getId(), activityThread.getActivity().getId());
        Assert.assertFalse(domainIdSet.contains(activityThread.getDomainId()));
        domainIdSet.add(activityThread.getDomainId());
      }
    }

    activityThreadPool.scanAll(new UserScanOperation(
        Collections.emptyList(),
        -1,
        false,
        true,
        "",
        1,
        Collections.emptyList(),
        1
    ));
  }

  @Test
  public void testSync() throws Exception {
    final ActivityThreadPool activityThreadPool = new RocksDBActivityThreadPool();
    activityThreadPool.init(new EasyMap(ImmutableMap.of(
        "column_family", ImmutableMap.of("name", "thread"),
        "direct", true,
        "sync_message_bus", Arrays.asList(
            "sync_bus1",
            "sync_bus2"
        )
    )));

    final int num = 1000;
    final ActivityDefinition activityDefinition = createActivityDefinition(
        "docs/sample/test_definitions/receive_with_foreach.yml");
    Sleeper.sleepInSeconds(1);
    final Activity activity = createActivity(
        "test_foreach_receive", "Test foreach receive", Collections.emptyMap());
    makeThreads(activityDefinition, activity, activityThreadPool, num);
    checkSyncThreads(num, activityDefinition, activity, activityThreadPool);
  }

  @Test
  public void testSyncInNoDirectMode() throws Exception {
    final RocksDBActivityThreadPool activityThreadPool = new RocksDBActivityThreadPool();
    activityThreadPool.init(new EasyMap(ImmutableMap.of(
        "column_family", ImmutableMap.of("name", "thread"),
        "direct", false,
        "sync_message_bus", Arrays.asList(
            "sync_bus1",
            "sync_bus2"
        )
    )));

    final ActivityDefinition activityDefinition = createActivityDefinition(
        "docs/sample/test_definitions/receive_with_foreach.yml");
    Sleeper.sleepInSeconds(1);
    final Activity activity = createActivity(
        "test_foreach_receive", "Test foreach receive", Collections.emptyMap());

    final int num = 1000;
    makeThreads(activityDefinition, activity, activityThreadPool, num);
    activityThreadPool.afterLoop();
    checkSyncThreads(num, activityDefinition, activity, activityThreadPool);
  }

  private void makeThreads(
      ActivityDefinition activityDefinition,
      Activity activity,
      ActivityThreadPool activityThreadPool, int num) {
    for (int i = 0; i < num; i++) {
      final long ts = CachedTimestamp.nowMilliseconds();
      activityThreadPool.upsertActivityThread(new ActivityThread(
          activity,
          activityDefinition,
          Integer.toString(i),
          ActivityThreadStatus.RUNNING,
          "receive",
          ts,
          ts,
          Collections.emptyMap()
      ));
    }
  }

  private void checkSyncThreads(
      int num,
      ActivityDefinition activityDefinition,
      Activity activity, ActivityThreadPool activityThreadPool) throws Exception {
    final MessageBus syncBus1 = getMessageBus("sync_bus1");
    final MessageBus syncBus2 = getMessageBus("sync_bus2");

    for (MessageBus messageBus : new MessageBus[]{syncBus1, syncBus2}) {
      final Collection<Message> messages = messageBus.read(num * 10);
      Assert.assertEquals(num, messages.size());
      final Set<String> domainIdSet = new HashSet<>();
      for (Message message : messages) {
        System.out.println(message);
        final MigrateActivityThreadMessage activityThreadMessage = (MigrateActivityThreadMessage) message;
        final ActivityThread activityThread = activityThreadMessage.getActivityThread();
        Assert.assertEquals(activity.getId(), activityThread.getActivity().getId());
        Assert.assertEquals(activityDefinition.getName(),
            activityThread.getActivityDefinition().getName());
        Assert.assertEquals("receive", activityThread.getCurrentAction());
        Assert.assertFalse(domainIdSet.contains(activityThread.getDomainId()));
        domainIdSet.add(activityThread.getDomainId());
      }
    }

    activityThreadPool.scanAll(new UserScanOperation(
        Collections.emptyList(),
        -1,
        false,
        true,
        "",
        1,
        Collections.emptyList(),
        1
    ));
  }

  @After
  public void tearDown() {
    this.cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
