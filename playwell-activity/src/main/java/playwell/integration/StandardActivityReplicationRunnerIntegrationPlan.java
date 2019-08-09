package playwell.integration;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import playwell.util.Sleeper;

/**
 * StandardActivityReplicationRunnerIntegrationPlan
 */
public class StandardActivityReplicationRunnerIntegrationPlan
    extends BaseStandardIntegrationPlan implements ActivityReplicationRunnerIntegrationPlan {

  private static final Logger logger = LogManager
      .getLogger(StandardActivityReplicationRunnerIntegrationPlan.class);

  @Override
  public MessageDomainIDStrategyManager getMessageDomainIDStrategyManager() {
    return checkInitThenReturn(TopComponentType.MESSAGE_DOMAIN_ID_STRATEGY_MANAGER);
  }

  @Override
  public MessageBusManager getMessageBusManager() {
    return checkInitThenReturn(TopComponentType.MESSAGE_BUS_MANAGER);
  }

  @Override
  public ServiceMetaManager getServiceMetaManager() {
    return checkInitThenReturn(TopComponentType.SERVICE_META_MANAGER);
  }

  @Override
  public TriggerManager getTriggerManager() {
    return checkInitThenReturn(TopComponentType.TRIGGER_MANAGER);
  }

  @Override
  public ActionManager getActionManager() {
    return checkInitThenReturn(TopComponentType.ACTIVITY_MANAGER);
  }

  @Override
  public ActivityDefinitionManager getActivityDefinitionManager() {
    return checkInitThenReturn(TopComponentType.ACTIVITY_DEFINITION_MANAGER);
  }

  @Override
  public ActivityManager getActivityManager() {
    return checkInitThenReturn(TopComponentType.ACTIVITY_MANAGER);
  }

  @Override
  public ActivityThreadPool getActivityThreadPool() {
    return checkInitThenReturn(TopComponentType.ACTIVITY_THREAD_POOL);
  }

  @Override
  public Clock getClock() {
    return checkInitThenReturn(TopComponentType.CLOCK);
  }

  @Override
  public ActivityReplicationRunner getActivityReplicationRunner() {
    return checkInitThenReturn(TopComponentType.ACTIVITY_REPLICATION_RUNNER);
  }

  @Override
  protected List<Pair<TopComponentType, Boolean>> getTopComponents() {
    return Arrays.asList(
        Pair.of(TopComponentType.MESSAGE_DOMAIN_ID_STRATEGY_MANAGER, true),
        Pair.of(TopComponentType.MESSAGE_BUS_MANAGER, true),
        Pair.of(TopComponentType.SERVICE_META_MANAGER, true),
        Pair.of(TopComponentType.TRIGGER_MANAGER, true),
        Pair.of(TopComponentType.ACTION_MANAGER, true),
        Pair.of(TopComponentType.ACTIVITY_DEFINITION_MANAGER, true),
        Pair.of(TopComponentType.ACTIVITY_MANAGER, true),
        Pair.of(TopComponentType.ACTIVITY_THREAD_POOL, true),
        Pair.of(TopComponentType.CLOCK, false),
        Pair.of(TopComponentType.ACTIVITY_REPLICATION_RUNNER, true)
    );
  }

  @Override
  protected void close() {
    final ActivityReplicationRunner runner = getActivityReplicationRunner();
    runner.stop();
    while (!runner.isStopped()) {
      logger.info("Waiting ActivityReplicationRunner stopped");
      Sleeper.sleepInSeconds(1);
    }
  }
}
