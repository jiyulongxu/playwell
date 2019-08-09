package playwell.integration;

import playwell.clock.Clock;
import playwell.clock.ClockRunner;
import playwell.message.bus.MessageBusManager;
import playwell.service.ServiceMetaManager;

/**
 * ClockRunner组件集成方案
 */
public interface ClockRunnerIntegrationPlan extends IntegrationPlan {

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
   * 获取集成完毕的ClockRunner
   *
   * @return ClockRunner
   */
  ClockRunner getClockRunner();
}
