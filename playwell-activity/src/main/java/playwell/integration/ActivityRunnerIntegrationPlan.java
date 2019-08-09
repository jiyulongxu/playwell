package playwell.integration;

import playwell.action.ActionManager;
import playwell.activity.ActivityManager;
import playwell.activity.ActivityRunner;
import playwell.activity.definition.ActivityDefinitionManager;
import playwell.activity.thread.ActivityThreadPool;
import playwell.activity.thread.ActivityThreadScheduler;
import playwell.clock.Clock;
import playwell.message.domainid.MessageDomainIDStrategyManager;
import playwell.route.SlotsManager;
import playwell.trigger.TriggerManager;

/**
 * 构建ActionRunner的集成方案
 *
 * @author chihongze@gmail.com
 */
public interface ActivityRunnerIntegrationPlan extends ServiceRunnerIntegrationPlan {

  /**
   * 获取MessageDomainIDStrategyManager
   *
   * @return MessageDomainIDStrategyManager
   */
  MessageDomainIDStrategyManager getMessageDomainIDStrategyManager();

  /**
   * 获取集成完毕的ActivityRunner
   *
   * @return ActivityRunner
   */
  ActivityRunner getActivityRunner();

  /**
   * 获取集成完毕的ActivityDefinitionManager
   *
   * @return ActivityDefinitionManager
   */
  ActivityDefinitionManager getActivityDefinitionManager();

  /**
   * 获取集成完毕的ActivityManager
   *
   * @return ActivityManager
   */
  ActivityManager getActivityManager();

  /**
   * 获取集成完毕的ClockLogic
   *
   * @return Clock
   */
  Clock getClock();

  /**
   * 获取集成完毕的ActivityThreadPool
   *
   * @return ActivityThreadPool
   */
  ActivityThreadPool getActivityThreadPool();

  /**
   * 获取集成完毕的ActivityThreadScheduler
   *
   * @return ActivityThreadScheduler
   */
  ActivityThreadScheduler getActivityThreadScheduler();

  /**
   * 获取集成完毕的TriggerManager
   *
   * @return TriggerManager
   */
  TriggerManager getTriggerManager();

  /**
   * 获取集成完毕的ActionManager
   *
   * @return ActionManager
   */
  ActionManager getActionManager();

  /**
   * 获取集成完毕的SlotsManager
   *
   * @return SlotsManager
   */
  SlotsManager getSlotsManager();
}
