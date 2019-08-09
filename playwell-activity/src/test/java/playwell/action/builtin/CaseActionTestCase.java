package playwell.action.builtin;

import com.google.common.collect.ImmutableMap;
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
 * CaseAction单元测试
 *
 * @author chihongze@gmail.com
 */
public class CaseActionTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    this.initStandardActivityRunnerIntergrationPlan(true);
  }

  @Test
  public void testCaseAction() throws Exception {
    TestUserBehaviorEvent event = new TestUserBehaviorEvent(
        "1",
        "提交订单",
        ImmutableMap.of("金额", 101),
        CachedTimestamp.nowMilliseconds()
    );
    Activity activity = spawn(
        "docs/sample/test_definitions/case.yml", event);
    TimeUnit.SECONDS.sleep(3L);
    Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), "1");
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertEquals("debug_all", activityThread.getCurrentAction());
    Assert.assertEquals(101, activityThread.getContext().get("amount"));

    event = new TestUserBehaviorEvent(
        "2",
        "提交订单",
        ImmutableMap.of("金额", 201),
        CachedTimestamp.nowMilliseconds()
    );
    sendMessage(event);
    TimeUnit.SECONDS.sleep(3L);
    activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), "2");
    Assert.assertTrue(activityThreadOptional.isPresent());
    activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertEquals("debug_all", activityThread.getCurrentAction());
    Assert.assertEquals(201, activityThread.getContext().get("amount"));

    event = new TestUserBehaviorEvent(
        "3",
        "提交订单",
        ImmutableMap.of("金额", 99),
        CachedTimestamp.nowMilliseconds()
    );
    sendMessage(event);
    TimeUnit.SECONDS.sleep(3L);
    activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), "3");
    Assert.assertTrue(activityThreadOptional.isPresent());
    activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertEquals("debug_amount", activityThread.getCurrentAction());
    Assert.assertEquals(99, activityThread.getContext().get("amount"));
  }

  @After
  public void tearDown() {
    this.cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
