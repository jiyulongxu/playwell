package playwell.message.domainid;

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
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ActivityThreadStatus;
import playwell.activity.thread.TestActivityThreadStatusListener;
import playwell.clock.CachedTimestamp;
import playwell.message.TestUserBehaviorEvent;

/**
 * <p>多个MessageDomainIDStrategy测试</p>
 *
 * @author chihongze@gmail.com
 */
public class MultiMessageDomainIdStrategyTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    initStandardActivityRunnerIntergrationPlan(true);
  }

  @Test
  public void testDomainIDStrategies() throws Exception {
    this.messageDomainIDStrategyManager.addMessageDomainIDStrategy(
        "user_id_order_id",
        "event.type == 'user_behavior' AND event.get('biz', '') == '订单'",
        "event.get('user_id') + '_' + event.get('order_id')"
    );
    ((MySQLMessageDomainIDStrategyManager) this.messageDomainIDStrategyManager).beforeLoop();

    final ActivityDefinition userDomainIdDef = createActivityDefinition(
        "docs/sample/test_definitions/domain_id/user_domain_id.yml");
    final ActivityDefinition userOrderDomainIdDef = createActivityDefinition(
        "docs/sample/test_definitions/domain_id/user_order_domain_id.yml");
    final Activity userDomainIdActivity = createActivity(
        userDomainIdDef.getName(),
        "user user id",
        Collections.emptyMap()
    );
    final Activity userOrderDomainIdActivity = createActivity(
        userOrderDomainIdDef.getName(),
        "user order user id",
        Collections.emptyMap()
    );

    sendMessage(new TestUserBehaviorEvent(
        "1",
        "登录",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));

    sendMessage(new TestUserBehaviorEvent(
        "2",
        "登录",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));

    sendMessage(new TestUserBehaviorEvent(
        "1",
        "提交订单",
        ImmutableMap.of(
            "order_id", "101",
            "biz", "订单"
        ),
        CachedTimestamp.nowMilliseconds()
    ));

    sendMessage(new TestUserBehaviorEvent(
        "2",
        "提交订单",
        ImmutableMap.of(
            "order_id", "102",
            "biz", "订单"
        ),
        CachedTimestamp.nowMilliseconds()
    ));

    sendMessage(new TestUserBehaviorEvent(
        "1",
        "已发货",
        ImmutableMap.of(
            "order_id", "101",
            "biz", "订单"
        ),
        CachedTimestamp.nowMilliseconds()
    ));

    TimeUnit.SECONDS.sleep(6L);

    final String[] idList = new String[]{"1", "2"};
    for (String id : idList) {
      Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener
          .getThread(userDomainIdActivity.getId(), id);
      Assert.assertTrue(activityThreadOptional.isPresent());
      ActivityThread activityThread = activityThreadOptional.get();
      Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    }

    Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener
        .getThread(userOrderDomainIdActivity.getId(), "1_101");
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertEquals("report", activityThread.getCurrentAction());

    activityThreadOptional = TestActivityThreadStatusListener
        .getThread(userOrderDomainIdActivity.getId(), "2_102");
    Assert.assertTrue(activityThreadOptional.isPresent());
    activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.FINISHED, activityThread.getStatus());
    Assert.assertEquals("alert", activityThread.getCurrentAction());
  }

  @After
  public void tearDown() {
    cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
