package playwell.activity.thread;

import java.util.Collection;
import java.util.Map;
import playwell.activity.Activity;
import playwell.activity.definition.ActivityDefinition;
import playwell.common.PlaywellComponent;
import playwell.message.Message;
import playwell.message.sys.ActivityThreadCtrlMessage;

/**
 * ActivityTreadScheduler
 *
 * @author chihongze@gmail.com
 */
public interface ActivityThreadScheduler extends PlaywellComponent {

  /**
   * 创建新的ActivityThread Spawn只有在Trigger中才能够调用
   *
   * @param activityDefinition 最新可用的ActivityDefinition版本
   * @param activity 活动实例
   * @param domainId DomainID
   * @param initContextVars 上下文初始化变量
   * @return 新创建的ActivityThread对象
   */
  ScheduleResult spawn(
      ActivityDefinition activityDefinition, Activity activity, String domainId,
      Map<String, Object> initContextVars);

  /**
   * 基于给定的mailbox消息来执行某个ActivityThread
   *
   * @param activityThread 要执行的ActivityThread
   * @param mailbox 消息mailbox
   */
  ScheduleResult schedule(ActivityThread activityThread, Collection<Message> mailbox);

  /**
   * 调度系统消息
   *
   * @param activityThread 要调度执行的ActivityThread
   * @param mailbox 系统消息mailbox
   */
  ScheduleResult scheduleWithCtrlMessages(ActivityThread activityThread,
      Collection<ActivityThreadCtrlMessage> mailbox);
}
