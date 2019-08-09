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
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ActivityThreadStatus;
import playwell.activity.thread.TestActivityThreadStatusListener;
import playwell.clock.CachedTimestamp;
import playwell.message.TestUserBehaviorEvent;

/**
 * SendAction测试用例
 *
 * @author chihongze@gmail.com
 */
public class SendActionTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    initStandardActivityRunnerIntergrationPlan(true);
  }

  @Test
  public void testParentAndChild() throws Exception {
    final ActivityDefinition parentDefinition = createActivityDefinition(
        "docs/sample/test_definitions/send_receive/parent.yml");
    final ActivityDefinition childDefinition = createActivityDefinition(
        "docs/sample/test_definitions/send_receive/child.yml");
    final Activity parentActivity = createActivity(
        parentDefinition.getName(), "test parent activity", Collections.emptyMap());
    final Activity childActivity = createActivity(
        childDefinition.getName(), "test child activity", Collections.emptyMap());

    sendMessage(new TestUserBehaviorEvent(
        "1",
        "test",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));

    TimeUnit.SECONDS.sleep(3L);

    final Optional<ActivityThread> parentActivityThreadOptional = TestActivityThreadStatusListener
        .getThread(parentActivity.getId(), "1");
    Assert.assertTrue(parentActivityThreadOptional.isPresent());
    final ActivityThread parentActivityThread = parentActivityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, parentActivityThread.getStatus());

    final Optional<ActivityThread> childActivityThreadOptional = TestActivityThreadStatusListener
        .getThread(
            childActivity.getId(), "1");
    Assert.assertTrue(childActivityThreadOptional.isPresent());
    final ActivityThread childActivityThread = childActivityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, childActivityThread.getStatus());
  }

  @After
  public void tearDown() {
    cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
