package playwell.integration;

import playwell.message.bus.MessageBusManager;
import playwell.service.ServiceMetaManager;
import playwell.service.ServiceRunner;

/**
 * 构建ServiceRunner的集成方案
 *
 * @author chihongze
 */
public interface ServiceRunnerIntegrationPlan extends IntegrationPlan {

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
   * 获取集成完毕的ServiceRunner
   *
   * @return ServiceRunner
   */
  ServiceRunner getServiceRunner();
}
