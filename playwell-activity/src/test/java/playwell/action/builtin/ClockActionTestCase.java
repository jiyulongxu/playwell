package playwell.action.builtin;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.activity.Activity;
import playwell.activity.thread.ActivityThreadStatus;
import playwell.activity.thread.TestActivityThreadStatusListener;
import playwell.clock.CachedTimestamp;
import playwell.message.TestUserBehaviorEvent;

/**
 * 针对ClockAction的测试用例
 *
 * @author chihongze@gmail.com
 */
public class ClockActionTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    initStandardActivityRunnerIntergrationPlan(true);
  }

  @Test
  public void testClock() throws Exception {
    Activity activity = spawn(
        "docs/sample/test_definitions/clock.yml",
        new TestUserBehaviorEvent(
            "test_clock",
            "test",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );
    TimeUnit.SECONDS.sleep(1L);

    Assert.assertEquals(
        ActivityThreadStatus.WAITING,
        TestActivityThreadStatusListener.getStatus(activity.getId(), "test_clock")
    );

    TimeUnit.SECONDS.sleep(4L);

    Assert.assertEquals(
        ActivityThreadStatus.FINISHED,
        TestActivityThreadStatusListener.getStatus(activity.getId(), "test_clock")
    );
  }

  @After
  public void tearDown() {
    cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
