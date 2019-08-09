package playwell.trigger;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.activity.Activity;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ActivityThreadStatus;
import playwell.clock.CachedTimestamp;
import playwell.common.EasyMap;
import playwell.message.Message;
import playwell.message.TestUserBehaviorEvent;
import playwell.trigger.builtin.SimpleEventTrigger;

/**
 * TestCase for SimpleEventTrigger
 *
 * @author chihongze@gmail.com
 */
public class SimpleMessageTriggerTestCase extends PlaywellBaseTestCase {

  private ActivityDefinition activityDefinition;

  private Activity activity;

  @Before
  public void setUp() {
    this.initStandardActivityRunnerIntergrationPlan(true);
    this.activityDefinition = createActivityDefinition("docs/sample/activity_demo.yml");
    this.activity = createActivity(
        activityDefinition.getName(),
        "test activity1", ImmutableMap.of("conf_a", 1, "conf_b", 2));
  }

  @Test
  public void testTrigger() throws Exception {
    final Trigger trigger = new SimpleEventTrigger(activity, activityDefinition);
    final List<TestUserBehaviorEvent> events = Arrays.asList(
        new TestUserBehaviorEvent(
            "Sam",
            "提交订单",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        ),
        new TestUserBehaviorEvent(
            "Jack",
            "进入注册页面",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        ),
        new TestUserBehaviorEvent(
            "Jack",
            "注册成功",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        ),
        new TestUserBehaviorEvent(
            "Jack",
            "进入首页",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        ),
        new TestUserBehaviorEvent(
            "Jack",
            "发表文章",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );

    final Map<ActivityThread, Collection<Message>> activityThreadEvents = new HashMap<>();
    trigger.handleMessageStream(activityThreadEvents, Collections.singletonMap("user_id",
        events.stream().collect(Collectors.groupingBy(
            TestUserBehaviorEvent::getUserId,
            Collectors.toCollection(LinkedList::new)
        ))
    ));
    // 此时应该有一个已经匹配了
    Assert.assertEquals(1, activityThreadEvents.size());
    activityThreadEvents.keySet().forEach(activityThread -> {
      Assert.assertEquals("Jack", activityThread.getDomainId());
      Assert.assertEquals(ActivityThreadStatus.SUSPENDING, activityThread.getStatus());
      Collection<Message> mailbox = activityThreadEvents.get(activityThread);
      Assert.assertEquals(2, mailbox.size());
      // 上下文是否已经初始化
      EasyMap context = new EasyMap(activityThread.getContext());
      Assert.assertEquals("Jack", context.getString("user_id"));
      Assert.assertEquals(3, context.getInt("config_item_sum"));
    });

    TimeUnit.SECONDS.sleep(5L);

    // 再触发一次，不会spawn出新的ActivityThread，保留所有事件到邮箱
    final Map<ActivityThread, Collection<Message>> activityThreadEvents2 = new HashMap<>();
    trigger
        .handleMessageStream(activityThreadEvents2, Collections.singletonMap("user_id",
            events.stream().collect(Collectors.groupingBy(
                TestUserBehaviorEvent::getUserId,
                Collectors.toCollection(LinkedList::new)))
        ));
    Assert.assertEquals(1, activityThreadEvents2.size());
    activityThreadEvents2.keySet().forEach(activityThread -> {
      Assert.assertEquals("Jack", activityThread.getDomainId());
      Assert.assertEquals(ActivityThreadStatus.SUSPENDING, activityThread.getStatus());
      Collection<Message> mailbox = activityThreadEvents2.get(activityThread);
      Assert.assertEquals(4, mailbox.size());
    });
  }

  @After
  public void tearDown() {
    this.cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
