package playwell.integration;

import playwell.clock.Clock;
import playwell.clock.ClockReplicationRunner;
import playwell.message.bus.MessageBusManager;
import playwell.service.ServiceMetaManager;

/**
 * ClockReplicationRunner组件集成方案
 */
public interface ClockReplicationRunnerIntegrationPlan extends IntegrationPlan {

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
   * 获取集成完毕的Clock对象
   *
   * @return Clock component
   */
  Clock getClock();

  /**
   * 获取集成完毕的ClockReplicationRunner对象
   *
   * @return ClockReplicationRunner
   */
  ClockReplicationRunner getClockReplicationRunner();
}
