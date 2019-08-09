package playwell.integration;

import java.util.HashMap;
import java.util.Map;
import playwell.action.ActionManager;
import playwell.activity.ActivityManager;
import playwell.activity.ActivityReplicationRunner;
import playwell.activity.ActivityRunner;
import playwell.activity.MemoryActivityManager;
import playwell.activity.definition.ActivityDefinitionManager;
import playwell.activity.definition.MySQLActivityDefinitionManager;
import playwell.activity.thread.ActivityThreadPool;
import playwell.activity.thread.ActivityThreadScheduler;
import playwell.activity.thread.MemoryActivityThreadPool;
import playwell.activity.thread.PlaywellActivityThreadScheduler;
import playwell.clock.Clock;
import playwell.clock.ClockReplicationRunner;
import playwell.clock.ClockRunner;
import playwell.clock.MemoryClock;
import playwell.common.PlaywellComponent;
import playwell.message.bus.MemoryMessageBusManager;
import playwell.message.bus.MessageBusManager;
import playwell.message.domainid.MemoryMessageDomainIDStrategyManager;
import playwell.message.domainid.MessageDomainIDStrategyManager;
import playwell.route.MessageRoute;
import playwell.route.MySQLSlotsManager;
import playwell.route.SlotsManager;
import playwell.service.MemoryServiceMetaManager;
import playwell.service.ServiceMetaManager;
import playwell.service.ServiceRunner;
import playwell.trigger.TriggerManager;

/**
 * Playwell顶级组件类型
 *
 * @author chihongze@gmail.com
 */
public enum TopComponentType {

  MESSAGE_DOMAIN_ID_STRATEGY_MANAGER(
      "message_domain_id_strategy_manager",
      MessageDomainIDStrategyManager.class,
      MemoryMessageDomainIDStrategyManager.class
  ),

  ACTIVITY_DEFINITION_MANAGER(
      "activity_definition_manager",
      ActivityDefinitionManager.class,
      MySQLActivityDefinitionManager.class
  ),

  ACTIVITY_MANAGER(
      "activity_manager",
      ActivityManager.class,
      MemoryActivityManager.class
  ),

  CLOCK(
      "clock",
      Clock.class,
      MemoryClock.class
  ),

  ACTIVITY_THREAD_POOL(
      "activity_thread_pool",
      ActivityThreadPool.class,
      MemoryActivityThreadPool.class
  ),

  MESSAGE_BUS_MANAGER(
      "message_bus_manager",
      MessageBusManager.class,
      MemoryMessageBusManager.class
  ),

  TRIGGER_MANAGER(
      "trigger_manager",
      TriggerManager.class,
      TriggerManager.class
  ),

  ACTION_MANAGER(
      "action_manager",
      ActionManager.class,
      ActionManager.class
  ),

  ACTIVITY_THREAD_SCHEDULER(
      "activity_thread_scheduler",
      ActivityThreadScheduler.class,
      PlaywellActivityThreadScheduler.class
  ),

  SERVICE_META_MANAGER(
      "service_meta_manager",
      ServiceMetaManager.class,
      MemoryServiceMetaManager.class
  ),

  SERVICE_RUNNER(
      "service_runner",
      ServiceRunner.class,
      ServiceRunner.class
  ),

  ACTIVITY_RUNNER(
      "activity_runner",
      ActivityRunner.class,
      ActivityRunner.class
  ),

  SLOTS_MANAGER(
      "slots_manager",
      SlotsManager.class,
      MySQLSlotsManager.class
  ),

  MESSAGE_ROUTE(
      "message_route",
      MessageRoute.class,
      MessageRoute.class
  ),

  CLOCK_RUNNER(
      "clock_runner",
      ClockRunner.class,
      ClockRunner.class
  ),

  ACTIVITY_REPLICATION_RUNNER(
      "activity_replication_runner",
      ActivityReplicationRunner.class,
      ActivityReplicationRunner.class
  ),

  CLOCK_REPLICATION_RUNNER(
      "clock_replication_runner",
      ClockReplicationRunner.class,
      ClockReplicationRunner.class
  ),

  ;

  private static final Map<String, TopComponentType> ALL_COMPONENTS = new HashMap<>();

  static {
    for (TopComponentType topComponentType : values()) {
      ALL_COMPONENTS.put(topComponentType.getName(), topComponentType);
    }
  }

  private final String name;

  private final Class<? extends PlaywellComponent> clazz;

  private final Class<? extends PlaywellComponent> defaultImplemention;

  TopComponentType(
      String name,
      Class<? extends PlaywellComponent> clazz,
      Class<? extends PlaywellComponent> defaultImplemention) {
    this.name = name;
    this.clazz = clazz;
    this.defaultImplemention = defaultImplemention;
  }

  public static TopComponentType valueOfByName(String name) {
    final TopComponentType type = ALL_COMPONENTS.get(name);
    if (type == null) {
      throw new RuntimeException(String.format("Unknown component type: %s", name));
    }
    return type;
  }

  public String getName() {
    return this.name;
  }

  public Class<? extends PlaywellComponent> getClazz() {
    return this.clazz;
  }

  public Class<? extends PlaywellComponent> getDefaultImplemention() {
    return defaultImplemention;
  }
}
