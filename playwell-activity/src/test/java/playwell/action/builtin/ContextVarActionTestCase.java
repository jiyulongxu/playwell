package playwell.action.builtin;


import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections4.MapUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.activity.Activity;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ActivityThreadStatus;
import playwell.activity.thread.TestActivityThreadStatusListener;
import playwell.message.TestUserBehaviorEvent;

/**
 * 测试上下文变量操作相关的Action，包括：debug/delete_var/update_var
 *
 * @author chihongze@gmail.com
 */
public class ContextVarActionTestCase extends PlaywellBaseTestCase {

  private TestUserBehaviorEvent behaviorEvent;

  @Before
  public void setUp() {
    behaviorEvent = new TestUserBehaviorEvent(
        "1",
        "加入购物车",
        ImmutableMap.of(
            "goods", "Apple watch",
            "price", 2
        ),
        System.currentTimeMillis()
    );
    this.initStandardActivityRunnerIntergrationPlan(true);
  }

  @Test
  public void testDebug() throws Exception {
    Activity activity = spawn(
        "docs/sample/test_definitions/debug_var.yml",
        behaviorEvent
    );
    TimeUnit.SECONDS.sleep(5L);
    Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), behaviorEvent.getUserId());
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertEquals(5, activityThread.getContext().get("count"));
  }

  @Test
  public void testModify() throws Exception {
    Activity activity = spawn(
        "docs/sample/test_definitions/modify_var.yml",
        behaviorEvent
    );
    TimeUnit.SECONDS.sleep(8L);
    Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), behaviorEvent.getUserId());
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertEquals(100000, activityThread.getContext().get("count"));
  }

  @Test
  public void testDelete() throws Exception {
    Activity activity = spawn(
        "docs/sample/test_definitions/delete_var.yml",
        behaviorEvent
    );
    TimeUnit.SECONDS.sleep(5L);
    Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), behaviorEvent.getUserId());
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertTrue(MapUtils.isEmpty(activityThread.getContext()));
  }

  @After
  public void tearDown() {
    this.cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
