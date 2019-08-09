package playwell.action.builtin;

import java.util.Collections;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.activity.Activity;
import playwell.activity.thread.TestActivityThreadStatusListener;
import playwell.clock.CachedTimestamp;
import playwell.message.TestUserBehaviorEvent;
import playwell.util.Sleeper;

/**
 * TestCase for compute action
 */
public class ComputeActionTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    this.initStandardActivityRunnerIntergrationPlan(true);
  }

  @Test
  public void testCompute() {
    final TestUserBehaviorEvent event = new TestUserBehaviorEvent(
        "1",
        "test_compute",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    );
    final Activity activity = spawn(
        "docs/sample/test_definitions/compute.yml", event);
    Sleeper.sleepInSeconds(2);
    final int a = (Integer) TestActivityThreadStatusListener
        .getFromCtx(activity.getId(), "1", "a");
    Assert.assertEquals(2, a);

    final int b = (Integer) TestActivityThreadStatusListener
        .getFromCtx(activity.getId(), "1", "b");
    Assert.assertEquals(3, b);

    final int c = (Integer) TestActivityThreadStatusListener.getFromCtx(
        activity.getId(), "1", "c");
    Assert.assertEquals(4, c);
  }

  @After
  public void tearDown() {
    this.cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
