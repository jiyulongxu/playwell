package playwell.action.builtin;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.activity.Activity;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ActivityThreadStatus;
import playwell.activity.thread.TestActivityThreadStatusListener;
import playwell.clock.CachedTimestamp;
import playwell.message.TestUserBehaviorEvent;

/**
 * TestCase for receive action
 *
 * @author chihongze@gmail.com
 */
public class ReceiveActionTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    initStandardActivityRunnerIntergrationPlan(true);
  }

  @Test
  public void testCommonReceive() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/simple_receive.yml",
        new TestUserBehaviorEvent(
            "1",
            "test",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );

    TimeUnit.SECONDS.sleep(1L);
    Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), "1");
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.WAITING, activityThread.getStatus());
    Assert.assertEquals("receive_event", activityThread.getCurrentAction());

    // 先发送一些不匹配的事件
    // 条件匹配，但domain id不匹配
    sendMessage(new TestUserBehaviorEvent(
        "2",
        "提交订单",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(3L);
    activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), "1");
    Assert.assertTrue(activityThreadOptional.isPresent());
    activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.WAITING, activityThread.getStatus());
    Assert.assertEquals("receive_event", activityThread.getCurrentAction());

    // 关闭2触发的测试，避免对后续的测试造成影响
    sendMessage(new TestUserBehaviorEvent(
        "2",
        "提交订单",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));

    // DomainID 匹配但是条件不匹配
    sendMessage(new TestUserBehaviorEvent(
        "1",
        "用户登录",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(1L);
    activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), "1");
    Assert.assertTrue(activityThreadOptional.isPresent());
    activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.WAITING, activityThread.getStatus());
    Assert.assertEquals("receive_event", activityThread.getCurrentAction());

    // 完全匹配的事件
    sendMessage(new TestUserBehaviorEvent(
        "1",
        "提交订单",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(1L);
    activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), "1");
    Assert.assertTrue(activityThreadOptional.isPresent());
    activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertEquals("say_behavior", activityThread.getCurrentAction());


  }

  @Test
  public void testTimeout() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/simple_receive.yml",
        new TestUserBehaviorEvent(
            "1",
            "test",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );
    TimeUnit.SECONDS.sleep(11L);
    final Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener
        .getThread(activity.getId(), "1");
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertEquals("say_timeout", activityThread.getCurrentAction());
  }

  @Test
  public void testReceiveWithWhile() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/receive_with_while.yml",
        new TestUserBehaviorEvent(
            "1",
            "提交订单",
            Collections.singletonMap("amount", 10),
            CachedTimestamp.nowMilliseconds()
        )
    );
    sendMessage(new TestUserBehaviorEvent(
        "1",
        "提交订单",
        Collections.singletonMap("amount", 20),
        CachedTimestamp.nowMilliseconds()
    ));
    sendMessage(new TestUserBehaviorEvent(
        "1",
        "提交订单",
        Collections.singletonMap("amount", 25),
        CachedTimestamp.nowMilliseconds()
    ));
    sendMessage(new TestUserBehaviorEvent(
        "1",
        "提交订单",
        Collections.singletonMap("amount", 30),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(3L);
    Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener
        .getThread(activity.getId(), "1");
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertEquals(55, activityThread.getContext().get("summary"));

    sendMessage(new TestUserBehaviorEvent(
        "2",
        "提交订单",
        Collections.singletonMap("amount", 10),
        CachedTimestamp.nowMilliseconds()
    ));

    TimeUnit.SECONDS.sleep(5L);
    activityThreadOptional = TestActivityThreadStatusListener
        .getThread(activity.getId(), "2");
    Assert.assertTrue(activityThreadOptional.isPresent());
    activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FAIL, activityThread.getStatus());
  }

  @Test
  public void testReceiveWithForeach() throws Exception {
    Activity activity = spawn(
        "docs/sample/test_definitions/receive_with_foreach.yml",
        new TestUserBehaviorEvent(
            "1",
            "注册成功",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );

    sendMessage(new TestUserBehaviorEvent(
        "1",
        "完善资料",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    sendMessage(new TestUserBehaviorEvent(
        "1",
        "浏览商品",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    sendMessage(new TestUserBehaviorEvent(
        "1",
        "提交订单",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));

    TimeUnit.SECONDS.sleep(3L);
    Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), "1");
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertEquals(4, activityThread.getContext().get("count"));

    sendMessage(new TestUserBehaviorEvent(
        "2",
        "注册成功",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    sendMessage(new TestUserBehaviorEvent(
        "2",
        "浏览商品",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(1L);
    activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), "2");
    Assert.assertTrue(activityThreadOptional.isPresent());
    activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.WAITING, activityThread.getStatus());
    Assert.assertEquals(1, activityThread.getContext().get("count"));
    sendMessage(new TestUserBehaviorEvent(
        "2",
        "完善资料",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(1L);
    activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), "2");
    Assert.assertTrue(activityThreadOptional.isPresent());
    activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.WAITING, activityThread.getStatus());
    Assert.assertEquals(2, activityThread.getContext().get("count"));
    for (int i = 0; i < 100000; i++) {
      sendMessage(new TestUserBehaviorEvent(
          "2",
          "浏览商品",
          Collections.emptyMap(),
          CachedTimestamp.nowMilliseconds()
      ));
    }
    sendMessage(new TestUserBehaviorEvent(
        "2",
        "提交订单",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(3L);
    activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), "2");
    Assert.assertTrue(activityThreadOptional.isPresent());
    activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertEquals(4, activityThread.getContext().get("count"));
  }

  @After
  public void tearDown() {
    cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
