package playwell.action.builtin;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
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
import playwell.message.TestUserBehaviorEvent;

/**
 * ForeachAction测试用例
 *
 * @author chihongze@gmail.com
 */
public class ForeachActionTestCase extends PlaywellBaseTestCase {

  private TestUserBehaviorEvent behaviorEvent;

  @Before
  public void setUp() {
    initStandardActivityRunnerIntergrationPlan(true);
    behaviorEvent = new TestUserBehaviorEvent(
        "1",
        "提交订单",
        ImmutableMap.of(
            "shopping_list", Arrays.asList(
                ImmutableMap.of(
                    "name", "iPhone",
                    "price", 1
                ),
                ImmutableMap.of(
                    "name", "Mac",
                    "price", 2
                ),
                ImmutableMap.of(
                    "name", "Apple Watch",
                    "price", 3
                )
            )
        ),
        System.currentTimeMillis()
    );
  }

  @Test
  public void testForeach() throws Exception {
    Activity activity = spawn(
        "docs/sample/test_definitions/foreach.yml", behaviorEvent);
    TimeUnit.SECONDS.sleep(3L);
    Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), behaviorEvent.getUserId());
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(3, activityThread.getContext().get("count"));
    Assert.assertFalse(activityThread.getContext().containsKey("$foreach.ele"));
    Assert.assertFalse(activityThread.getContext().containsKey("$foreach.idx"));
  }

  @Test
  public void testForeachYamlList() throws Exception {
    Activity activity = spawn(
        "docs/sample/test_definitions/foreach_yaml_list.yml",
        behaviorEvent
    );
    TimeUnit.SECONDS.sleep(3L);
    Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), behaviorEvent.getUserId());
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
  }

  @After
  public void tearDown() {
    cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
