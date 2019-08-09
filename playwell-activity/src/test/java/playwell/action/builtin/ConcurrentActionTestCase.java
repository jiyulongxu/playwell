package playwell.action.builtin;

import java.util.Arrays;
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
 * 测试ConcurrentAction
 *
 * @author chihongze@gmail.com
 */
public class ConcurrentActionTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    initStandardActivityRunnerIntergrationPlan(true);
  }

  @Test
  public void testConcurrent() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/concurrent_same_type.yml",
        new TestUserBehaviorEvent(
            "1",
            "test",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );
    TimeUnit.SECONDS.sleep(3L);

    final Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener
        .getThread(activity.getId(), "1");
    Assert.assertTrue(activityThreadOptional.isPresent());
    final ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Object[] contents = new Object[]{
        activityThread.getContext().get("a_content"),
        activityThread.getContext().get("b_content"),
        activityThread.getContext().get("c_content")
    };
    Assert.assertTrue(Arrays.deepEquals(
        new String[]{"a", "b", "c"}, contents));
  }

  @Test
  public void testConcurrentWithTimeout() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/concurrent_with_timeout.yml",
        new TestUserBehaviorEvent(
            "1",
            "test",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );
    TimeUnit.SECONDS.sleep(3L);

    final Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener
        .getThread(activity.getId(), "1");
    Assert.assertTrue(activityThreadOptional.isPresent());
    final ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FAIL, activityThread.getStatus());
    Assert.assertEquals("timeout", activityThread.getContext().get("_FR"));
  }

  @Test
  public void testConcurrentDiff() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/concurrent_diff_type.yml",
        new TestUserBehaviorEvent(
            "1",
            "test",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );
    TimeUnit.SECONDS.sleep(3L);

    final Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener
        .getThread(activity.getId(), "1");
    Assert.assertTrue(activityThreadOptional.isPresent());
    final ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertEquals(100, activityThread.getContext().get("sum"));
  }

  @After
  public void tearDown() {
    cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
