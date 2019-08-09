package playwell.action.builtin;

import com.google.common.collect.ImmutableMap;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.activity.Activity;
import playwell.message.TestUserBehaviorEvent;

public class SleepActionTestCase extends PlaywellBaseTestCase {

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
  public void testSleep() throws Exception {
    Activity activity = spawn(
        "docs/sample/test_definitions/sleep.yml",
        behaviorEvent
    );

    TimeUnit.SECONDS.sleep(10L);
  }

  @After
  public void tearDown() {
    this.cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
