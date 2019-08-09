package playwell.trigger;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.activity.Activity;
import playwell.activity.ActivityStatus;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ActivityThreadPool;
import playwell.activity.thread.ActivityThreadScheduler;
import playwell.activity.thread.ScheduleResult;
import playwell.common.argument.ListArgument;
import playwell.common.argument.MapArgument;
import playwell.common.expression.spel.SpELPlaywellExpressionContext;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.message.Message;

/**
 * 事件流触发器
 * <p>
 * 事件流触发器接受一个时间窗口内的事件流，并完成两项工作：
 *
 * <ol>
 * <li>如果事件满足了触发条件，那么创建新的ActivityThread</li>
 * <li>过滤出已有ActivityThread的事件Mailbox</li>
 * </ol>
 *
 * @author chihongze@gmail.com
 */
public abstract class Trigger {

  private static final Logger logger = LogManager.getLogger(Trigger.class);

  // 活动信息
  protected final Activity activity;

  // 最新可用版本的活动定义
  protected final ActivityDefinition latestEnableActivityDefinition;

  // 最新可用版本的Trigger定义
  protected final TriggerDefinition triggerDefinition;

  protected Trigger(Activity activity, ActivityDefinition latestEnableActivityDefinition) {
    this.activity = activity;
    this.latestEnableActivityDefinition = latestEnableActivityDefinition;
    this.triggerDefinition = latestEnableActivityDefinition.getTriggerDefinition();
  }

  /**
   * 处理事件流 Steps:
   * <ol>
   * <li>根据Trigger的ActivityID和events中的DomainID Keys结合，从ActivityThreadPool中批量获取ActivityThread</li>
   * <li>检索所有的message，如果DomainID有对应的ActivityThread，那么无需触发新的ActivityThread，直接加入到collector中</li>
   * <li>如果没有对应的ActivityThread，那么就尝试对其进行触发，创建新的ActivityThread</li>
   * <li>将新创建的ActivityThread添加到ActivityThreadPool当中，将触发事件剔除，并加入到collector中</li>
   * </ol>
   *
   * @param collector ActivityThread 对应的消息收集器，可以理解为一个抽象的邮箱
   * @param messagesByStrategies 按照DomainID进行分组的消息集合
   */
  public void handleMessageStream(
      Map<ActivityThread, Collection<Message>> collector,
      Map<String, Map<String, Collection<Message>>> messagesByStrategies) {

    if (MapUtils.isEmpty(messagesByStrategies)) {
      return;
    }

    Map<String, Collection<Message>> messages = messagesByStrategies
        .get(latestEnableActivityDefinition.getDomainIdStrategy());
    if (MapUtils.isEmpty(messages)) {
      return;
    }

    final ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final ActivityThreadPool activityThreadPool = integrationPlan.getActivityThreadPool();
    final ActivityThreadScheduler activityThreadScheduler = integrationPlan
        .getActivityThreadScheduler();

    final int activityId = activity.getId();
    final Map<String, ActivityThread> threadsMap = activityThreadPool.multiGetActivityThreads(
        activityId, messages.keySet());

    messages.entrySet().parallelStream().forEach(entry -> {
      final String domainId = entry.getKey();
      final Collection<Message> mailbox = entry.getValue();

      if (CollectionUtils.isEmpty(mailbox)) {
        return;
      }

      try {
        if (threadsMap.containsKey(domainId)) {
          final ActivityThread activityThread = threadsMap.get(domainId);
          collector.put(activityThread, mailbox);
        } else if (activity.getStatus() == ActivityStatus.COMMON) {
          TriggerMatchResult result = isMatchCondition(domainId, mailbox);
          if (result != null && result.isMatched()) {
            ScheduleResult scheduleResult = activityThreadScheduler.spawn(
                latestEnableActivityDefinition, activity, domainId, result.getInitContextVars());
            if (scheduleResult.isOk()) {
              collector.put(scheduleResult.getActivityThread(), result.getTrailingMessages());
            }
          }
        }
      } catch (Exception e) {
        logger.error(String.format("Handle messages error with trigger! "
                + "Activity Definition = %s, Activity = %d, Domain ID = %s, Mailbox = %s",
            activity.getDefinitionName(),
            activity.getId(),
            domainId,
            mailbox
        ), e);
      }
    });
  }

  protected abstract TriggerMatchResult isMatchCondition(String domainId,
      Collection<Message> mailbox);

  /**
   * 渲染Map形式的参数
   *
   * @param message 事件
   * @return 渲染后的参数Map
   */
  protected Map<String, Object> getMapArguments(Message message) {
    final MapArgument arguments = (MapArgument) triggerDefinition.getArguments();
    SpELPlaywellExpressionContext ctx = new SpELPlaywellExpressionContext();
    ctx.setRootObject(new SingleEventTriggerArgumentRootContext(
        latestEnableActivityDefinition, activity, message));
    return arguments.getValueMap(ctx);
  }

  /**
   * 渲染List形式的参数
   *
   * @param message 事件
   * @return 渲染后的参数List
   */
  protected List<Object> getListArguments(Message message) {
    final ListArgument arguments = (ListArgument) triggerDefinition.getArguments();
    SpELPlaywellExpressionContext ctx = new SpELPlaywellExpressionContext();
    ctx.setRootObject(new SingleEventTriggerArgumentRootContext(
        latestEnableActivityDefinition, activity, message));
    return arguments.getValueList(ctx);
  }

  /**
   * 根据单个事件，渲染初始化上下文变量，子类可选择使用
   *
   * @return 渲染后的上下文变量
   */
  protected Map<String, Object> getInitContext(Message message) {
    final MapArgument contextVars = triggerDefinition.getContextVars();
    if (contextVars != null) {
      SpELPlaywellExpressionContext ctx = new SpELPlaywellExpressionContext();
      ctx.setRootObject(new SingleEventTriggerArgumentRootContext(
          latestEnableActivityDefinition, activity, message));
      return contextVars.getValueMap(ctx);
    } else {
      return Collections.emptyMap();
    }
  }
}
