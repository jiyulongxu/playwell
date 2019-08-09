package playwell.activity.thread;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.activity.Activity;
import playwell.clock.CachedTimestamp;
import playwell.message.TestUserBehaviorEvent;
import playwell.message.sys.ActivityThreadCtrlMessage;
import playwell.message.sys.ActivityThreadCtrlMessage.Commands;

/**
 * Test case for PlaywellActivityThreadScheduler
 */
public class PlaywellActivityThreadSchedulerTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    initStandardActivityRunnerIntergrationPlan(true);
  }

  @Test
  public void testCtrlMessage() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/receive_with_foreach.yml",
        new TestUserBehaviorEvent(
            "1",
            "注册成功",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );
    TimeUnit.SECONDS.sleep(1L);
    final Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener
        .getThread(
            activity.getId(),
            "1"
        );
    Assert.assertTrue(activityThreadOptional.isPresent());
    final ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.WAITING, activityThread.getStatus());

    sendMessage(new ActivityThreadCtrlMessage(
        CachedTimestamp.nowMilliseconds(),
        "",
        "",
        activity.getId(),
        "1",
        Commands.PAUSE
    ));
    TimeUnit.SECONDS.sleep(1L);
    ActivityThreadStatus status = TestActivityThreadStatusListener.getStatus(
        activity.getId(), "1");
    Assert.assertEquals(ActivityThreadStatus.PAUSED, status);
    sendMessage(new TestUserBehaviorEvent(
        "1",
        "完善资料",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(1L);
    status = TestActivityThreadStatusListener.getStatus(
        activity.getId(), "1");
    Assert.assertEquals(ActivityThreadStatus.PAUSED, status);

    sendMessage(new ActivityThreadCtrlMessage(
        CachedTimestamp.nowMilliseconds(),
        "",
        "",
        activity.getId(),
        "1",
        Commands.CONTINUE
    ));
    TimeUnit.SECONDS.sleep(1L);
    status = TestActivityThreadStatusListener.getStatus(
        activity.getId(), "1");
    Assert.assertEquals(ActivityThreadStatus.WAITING, status);

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
    TimeUnit.SECONDS.sleep(1L);

    status = TestActivityThreadStatusListener.getStatus(
        activity.getId(), "1");
    Assert.assertEquals(ActivityThreadStatus.FINISHED, status);
  }

  @Test
  public void testRetry() throws Exception {
    Activity activity = spawn(
        "docs/sample/test_definitions/retry.yml",
        new TestUserBehaviorEvent(
            "1",
            "注册成功",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );
    TimeUnit.SECONDS.sleep(10L);
    Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener
        .getThread(
            activity.getId(),
            "1"
        );
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FAIL, activityThread.getStatus());
    Assert.assertEquals("retry_failure", activityThread.getCurrentAction());

    sendMessage(new TestUserBehaviorEvent(
        "2",
        "注册成功",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(1L);
    sendMessage(new TestUserBehaviorEvent(
        "2",
        "提交订单",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(2L);
    activityThreadOptional = TestActivityThreadStatusListener
        .getThread(
            activity.getId(),
            "2"
        );
    Assert.assertTrue(activityThreadOptional.isPresent());
    activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
  }

  @After
  public void tearDown() {
    cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
