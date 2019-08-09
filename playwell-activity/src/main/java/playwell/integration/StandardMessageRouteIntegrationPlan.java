package playwell.integration;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.message.bus.MessageBusManager;
import playwell.message.domainid.MessageDomainIDStrategyManager;
import playwell.route.MessageRoute;
import playwell.route.SlotsManager;
import playwell.route.migration.MigrationCoordinator;
import playwell.service.ServiceMetaManager;
import playwell.util.Sleeper;

/**
 * MessageRoute组件集成方案实现
 */
public class StandardMessageRouteIntegrationPlan
    extends BaseStandardIntegrationPlan implements MessageRouteIntegrationPlan {

  private static final Logger logger = LogManager
      .getLogger(StandardMessageRouteIntegrationPlan.class);

  @Override
  protected List<Pair<TopComponentType, Boolean>> getTopComponents() {
    return Arrays.asList(
        Pair.of(TopComponentType.MESSAGE_BUS_MANAGER, true),
        Pair.of(TopComponentType.SERVICE_META_MANAGER, true),
        Pair.of(TopComponentType.MESSAGE_DOMAIN_ID_STRATEGY_MANAGER, true),
        Pair.of(TopComponentType.SLOTS_MANAGER, true),
        Pair.of(TopComponentType.MESSAGE_ROUTE, true)
    );
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
  public MessageDomainIDStrategyManager getMessageDomainIDStrategyManager() {
    return checkInitThenReturn(TopComponentType.MESSAGE_DOMAIN_ID_STRATEGY_MANAGER);
  }

  @Override
  public SlotsManager getSlotsManager() {
    return checkInitThenReturn(TopComponentType.SLOTS_MANAGER);
  }

  @Override
  public MessageRoute getMessageRoute() {
    return checkInitThenReturn(TopComponentType.MESSAGE_ROUTE);
  }

  @Override
  protected void close() {
    final MessageRoute messageRoute = getMessageRoute();
    messageRoute.stop();
    while (!messageRoute.isStopped()) {
      logger.info("Waiting MessageRoute stopped");
      Sleeper.sleepInSeconds(1);
    }
    final SlotsManager slotsManager = getSlotsManager();
    final MigrationCoordinator coordinator = slotsManager.getMigrationCoordinator();
    if (coordinator != null) {
      coordinator.stop();
    }
  }
}
