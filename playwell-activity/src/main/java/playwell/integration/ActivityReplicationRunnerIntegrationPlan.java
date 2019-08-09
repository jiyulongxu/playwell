package playwell.integration;

import playwell.action.ActionManager;
import playwell.activity.ActivityManager;
import playwell.activity.ActivityReplicationRunner;
import playwell.activity.definition.ActivityDefinitionManager;
import playwell.activity.thread.ActivityThreadPool;
import playwell.clock.Clock;
import playwell.message.bus.MessageBusManager;
import playwell.message.domainid.MessageDomainIDStrategyManager;
import playwell.service.ServiceMetaManager;
import playwell.trigger.TriggerManager;

/**
 * ActivityReplicationRunnerIntegrationPlan
 */
public interface ActivityReplicationRunnerIntegrationPlan extends IntegrationPlan {

  /**
   * 获取集成完毕的消息总线
   *
   * @return MessageBusManager
   */
  MessageBusManager getMessageBusManager();


  /**
   * 获取集成完毕的ServiceMetaManager
   *
   * @return ServiceMetaManager
   */
  ServiceMetaManager getServiceMetaManager();

  /**
   * 获取MessageDomainIDStrategyManager
   *
   * @return MessageDomainIDStrategyManager
   */
  MessageDomainIDStrategyManager getMessageDomainIDStrategyManager();

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
   * 获取集成完毕的ActivityThreadPool
   *
   * @return ActivityThreadPool
   */
  ActivityThreadPool getActivityThreadPool();

  /**
   * 获取集成完毕的Clock
   *
   * @return Clock
   */
  Clock getClock();

  /**
   * 获取集成完毕的ActivityReplicationRunner
   *
   * @return ActivityReplicationRunner
   */
  ActivityReplicationRunner getActivityReplicationRunner();
}
