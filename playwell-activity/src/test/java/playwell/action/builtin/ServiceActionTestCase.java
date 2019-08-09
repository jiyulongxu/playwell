package playwell.action.builtin;

import com.google.common.collect.ImmutableMap;
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
import playwell.service.AddService;

/**
 * <p>Test case for service action</p>
 *
 * @author chihongze@gmail.com
 */
public class ServiceActionTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    initStandardActivityRunnerIntergrationPlan(true);
  }

  @Test
  public void testMockService() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/service.yml",
        new TestUserBehaviorEvent(
            "1",
            "test",
            ImmutableMap.of("name", "Sam"),
            CachedTimestamp.nowMilliseconds()
        )
    );

    // 3 seconds should be enough for every body : )
    TimeUnit.SECONDS.sleep(3L);

    final Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener
        .getThread(activity.getId(), "1");
    Assert.assertTrue(activityThreadOptional.isPresent());
    final ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
  }

  @Test
  public void testMockServiceWithTimeout() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/service_with_timeout_retry.yml",
        new TestUserBehaviorEvent(
            "1",
            "test",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );
    TimeUnit.SECONDS.sleep(7L);

    final Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener
        .getThread(activity.getId(), "1");
    Assert.assertTrue(activityThreadOptional.isPresent());
    final ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
  }

  @Test
  public void testNoAwait() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/service_without_await.yml",
        new TestUserBehaviorEvent(
            "1",
            "test",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );
    TimeUnit.SECONDS.sleep(2L);
    final Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener
        .getThread(activity.getId(), "1");
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    AddService addService = AddService.SELF;
    Optional<Integer> resultOptional = addService.get(1, 2);
    Assert.assertFalse(resultOptional.isPresent());
    TimeUnit.SECONDS.sleep(4L);
    resultOptional = addService.get(1, 2);
    Assert.assertTrue(resultOptional.isPresent());
    Assert.assertEquals(new Integer(3), resultOptional.get());
  }

  @After
  public void tearDown() {
    cleanStandardActivityRunnerIntergrationPlan(true);
    AddService.SELF.clear();
  }
}
