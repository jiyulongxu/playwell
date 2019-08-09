package playwell.integration;

import playwell.message.bus.MessageBusManager;
import playwell.message.domainid.MessageDomainIDStrategyManager;
import playwell.route.MessageRoute;
import playwell.route.SlotsManager;
import playwell.service.ServiceMetaManager;

/**
 * MessageRouteIntegrationPlan
 */
public interface MessageRouteIntegrationPlan extends IntegrationPlan {

  /**
   * 获取集成完毕的MessageBusManager
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
   * 获取集成完毕的MessageDomainIDStrategyManager
   *
   * @return MessageDomainIDStrategyManager
   */
  MessageDomainIDStrategyManager getMessageDomainIDStrategyManager();

  /**
   * 获取集成完毕的SlotsManager
   *
   * @return SlotsManager
   */
  SlotsManager getSlotsManager();

  /**
   * 获取集成完毕的MessageRoute
   *
   * @return MessageRoute
   */
  MessageRoute getMessageRoute();

}
