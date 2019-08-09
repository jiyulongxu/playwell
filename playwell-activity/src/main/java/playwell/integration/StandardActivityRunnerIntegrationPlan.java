package playwell.integration;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.action.ActionManager;
import playwell.activity.ActivityManager;
import playwell.activity.ActivityRunner;
import playwell.activity.definition.ActivityDefinitionManager;
import playwell.activity.thread.ActivityThreadPool;
import playwell.activity.thread.ActivityThreadScheduler;
import playwell.clock.Clock;
import playwell.message.bus.MessageBusManager;
import playwell.message.domainid.MessageDomainIDStrategyManager;
import playwell.route.SlotsManager;
import playwell.route.migration.MigrationCoordinator;
import playwell.route.migration.MigrationInputTask;
import playwell.route.migration.MigrationOutputTask;
import playwell.service.ServiceMetaManager;
import playwell.service.ServiceRunner;
import playwell.trigger.TriggerManager;
import playwell.util.Sleeper;

/**
 * 基于PlaywellComponent实现的标准ActivityRunner集成方案
 *
 * @author chihongze
 */
public class StandardActivityRunnerIntegrationPlan extends BaseStandardIntegrationPlan implements
    ActivityRunnerIntegrationPlan {

  private static final Logger logger = LogManager.getLogger(
      StandardActivityRunnerIntegrationPlan.class);

  public StandardActivityRunnerIntegrationPlan() {

  }

  @Override
  public MessageDomainIDStrategyManager getMessageDomainIDStrategyManager() {
    return checkInitThenReturn(TopComponentType.MESSAGE_DOMAIN_ID_STRATEGY_MANAGER);
  }

  @Override
  public ActivityRunner getActivityRunner() {
    return checkInitThenReturn(TopComponentType.ACTIVITY_RUNNER);
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
  public Clock getClock() {
    return checkInitThenReturn(TopComponentType.CLOCK);
  }

  @Override
  public ActivityThreadPool getActivityThreadPool() {
    return checkInitThenReturn(TopComponentType.ACTIVITY_THREAD_POOL);
  }

  @Override
  public ActivityThreadScheduler getActivityThreadScheduler() {
    return checkInitThenReturn(TopComponentType.ACTIVITY_THREAD_SCHEDULER);
  }

  @Override
  public TriggerManager getTriggerManager() {
    return checkInitThenReturn(TopComponentType.TRIGGER_MANAGER);
  }

  @Override
  public ActionManager getActionManager() {
    return checkInitThenReturn(TopComponentType.ACTION_MANAGER);
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
  public ServiceRunner getServiceRunner() {
    return checkInitThenReturn(TopComponentType.SERVICE_RUNNER);
  }

  @Override
  public SlotsManager getSlotsManager() {
    return checkInitThenReturn(TopComponentType.SLOTS_MANAGER);
  }

  @Override
  protected void close() {
    final ActivityRunner activityRunner = getActivityRunner();
    activityRunner.stop();
    while (!activityRunner.isStopped()) {
      logger.info("Waiting ActivityRunner stopped");
      Sleeper.sleepInSeconds(1);
    }

    if (contains(TopComponentType.SERVICE_RUNNER)) {
      final ServiceRunner serviceRunner = getServiceRunner();
      serviceRunner.stop();
      while (!serviceRunner.isStopped()) {
        logger.info("Waiting ServiceRunner stopped");
        Sleeper.sleepInSeconds(1);
      }
    }

    if (contains(TopComponentType.SLOTS_MANAGER)) {
      final SlotsManager slotsManager = getSlotsManager();
      final MigrationCoordinator coordinator = slotsManager.getMigrationCoordinator();
      if (coordinator != null) {
        coordinator.stop();
        while (!coordinator.isStopped()) {
          logger.info("Waiting MigrationCoordinator stopped");
          Sleeper.sleepInSeconds(1);
        }
      }
      final MigrationOutputTask outputTask = slotsManager.getMigrationOutputTask();
      if (outputTask != null) {
        outputTask.stop();
        while (!outputTask.isStopped()) {
          logger.info("Waiting MigrationOutputTask stopped");
          Sleeper.sleepInSeconds(1);
        }
      }
      final MigrationInputTask inputTask = slotsManager.getMigrationInputTask();
      if (inputTask != null) {
        inputTask.stop();
        while (!inputTask.isStopped()) {
          logger.info("Waiting MigrationInputTask stopped");
          Sleeper.sleepInSeconds(1);
        }
      }
    }
  }

  @Override
  protected List<Pair<TopComponentType, Boolean>> getTopComponents() {
    return Arrays.asList(
        Pair.of(TopComponentType.ACTIVITY_DEFINITION_MANAGER, true),
        Pair.of(TopComponentType.ACTIVITY_MANAGER, true),
        Pair.of(TopComponentType.CLOCK, true),
        Pair.of(TopComponentType.ACTIVITY_THREAD_POOL, true),
        Pair.of(TopComponentType.MESSAGE_BUS_MANAGER, true),
        Pair.of(TopComponentType.TRIGGER_MANAGER, true),
        Pair.of(TopComponentType.ACTION_MANAGER, true),
        Pair.of(TopComponentType.ACTIVITY_THREAD_SCHEDULER, true),
        Pair.of(TopComponentType.SERVICE_META_MANAGER, true),
        Pair.of(TopComponentType.SERVICE_RUNNER, false),
        Pair.of(TopComponentType.MESSAGE_DOMAIN_ID_STRATEGY_MANAGER, true),
        Pair.of(TopComponentType.SLOTS_MANAGER, false),
        Pair.of(TopComponentType.ACTIVITY_RUNNER, true)
    );
  }
}
