package playwell.api;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import playwell.integration.ActivityReplicationRunnerIntegrationPlan;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.ClockReplicationRunnerIntegrationPlan;
import playwell.integration.ClockRunnerIntegrationPlan;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.ServiceRunnerIntegrationPlan;
import playwell.integration.TopComponentType;

/**
 * Playwell API Http Server
 */
public class PlaywellAPIServer extends BaseAPIServer {

  private static final PlaywellAPIServer INSTANCE = new PlaywellAPIServer();

  public static PlaywellAPIServer getInstance() {
    return INSTANCE;
  }

  @Override
  protected Collection<APIRoutes> getAllRoutes() {
    IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final List<APIRoutes> allRoutes = new LinkedList<>();

    if (integrationPlan instanceof ActivityRunnerIntegrationPlan) {
      allRoutes.add(new ActivityDefinitionAPIRoutes());
      allRoutes.add(new ActivityAPIRoutes());
      allRoutes.add(new ActivityThreadAPIRoutes());
      allRoutes.add(new ClockAPIRoutes());
      allRoutes.add(new DomainIDStrategyAPIRoutes());
      allRoutes.add(new MessageBusAPIRoutes());
      allRoutes.add(new ServiceMetaAPIRoutes());
      allRoutes.add(new ActivityRunnerAPIRoutes());
      allRoutes.add(new SlotAPIRoutes());
      allRoutes.add(new SystemAPIRoutes());

      if (integrationPlan.contains(TopComponentType.SERVICE_RUNNER)) {
        allRoutes.add(new ServiceRunnerAPIRoutes());
      }
    } else if (integrationPlan instanceof ServiceRunnerIntegrationPlan) {
      allRoutes.add(new MessageBusAPIRoutes());
      allRoutes.add(new ServiceMetaAPIRoutes());
      allRoutes.add(new ServiceRunnerAPIRoutes());
      allRoutes.add(new SystemAPIRoutes());
    } else if (integrationPlan instanceof ClockRunnerIntegrationPlan) {
      allRoutes.add(new MessageBusAPIRoutes());
      allRoutes.add(new ServiceMetaAPIRoutes());
      allRoutes.add(new ClockAPIRoutes());
      allRoutes.add(new ClockRunnerAPIRoutes());
      allRoutes.add(new SystemAPIRoutes());
    } else if (integrationPlan instanceof ActivityReplicationRunnerIntegrationPlan) {
      allRoutes.add(new ActivityDefinitionAPIRoutes());
      allRoutes.add(new ActivityAPIRoutes());
      allRoutes.add(new ActivityThreadAPIRoutes());
      allRoutes.add(new ClockAPIRoutes());
      allRoutes.add(new DomainIDStrategyAPIRoutes());
      allRoutes.add(new MessageBusAPIRoutes());
      allRoutes.add(new ServiceMetaAPIRoutes());
      allRoutes.add(new ActivityReplicationRunnerAPIRoutes());
      allRoutes.add(new SystemAPIRoutes());
    } else if (integrationPlan instanceof ClockReplicationRunnerIntegrationPlan) {
      allRoutes.add(new MessageBusAPIRoutes());
      allRoutes.add(new ServiceMetaAPIRoutes());
      allRoutes.add(new ClockAPIRoutes());
      allRoutes.add(new ClockReplicationRunnerAPIRoutes());
      allRoutes.add(new SystemAPIRoutes());
    }

    return allRoutes;
  }
}
