package playwell.activity;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ActivityThreadStatus;
import playwell.activity.thread.TestActivityThreadStatusListener;
import playwell.clock.CachedTimestamp;
import playwell.message.TestUserBehaviorEvent;

/**
 * 测试Activity Config修改
 *
 * @author chihongze@gmail.com
 */
public class ActivityConfigTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    initStandardActivityRunnerIntergrationPlan(true);
  }

  @Test
  public void testModifyConfig() throws Exception {
    final ActivityDefinition activityDefinition = this.createActivityDefinition(
        "docs/sample/test_definitions/activity_config.yml");
    final Activity activity = createActivity(
        activityDefinition.getName(),
        "Test activity",
        Collections.singletonMap("TEXT_B", "How do you do")
    );

    sendMessage(new TestUserBehaviorEvent(
        "1",
        "test",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));

    TimeUnit.SECONDS.sleep(1L);

    Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener
        .getThread(activity.getId(), "1");
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertEquals("Hello", activityThread.getActivity().getConfig().get("TEXT_A"));
    Assert.assertEquals("How do you do", activityThread.getActivity().getConfig().get("TEXT_B"));

    activityManager.modifyConfig(activity.getId(), Collections.singletonMap("TEXT_C", "bad"));
    TimeUnit.SECONDS.sleep(1L);

    sendMessage(new TestUserBehaviorEvent(
        "2",
        "test",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(1L);

    activityThreadOptional = TestActivityThreadStatusListener
        .getThread(activity.getId(), "2");
    Assert.assertTrue(activityThreadOptional.isPresent());
    activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertNull(activityThread.getActivity().getConfig().get("TEXT_A"));
    Assert.assertNull(activityThread.getActivity().getConfig().get("TEXT_B"));
    Assert.assertEquals("bad", activityThread.getActivity().getConfig().get("TEXT_C"));
  }

  @After
  public void tearDown() {
    cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
