package playwell.launcher;

import playwell.integration.IntegrationPlan;
import playwell.integration.MessageRouteIntegrationPlan;
import playwell.route.MessageRoute;

/**
 * RouteLauncher
 */
public class RouteLauncher extends BaseLauncher {

  private static final String NAME = "route";

  public RouteLauncher() {
    super("playwell.integration.StandardMessageRouteIntegrationPlan");
  }

  @Override
  public String moduleName() {
    return NAME;
  }

  @Override
  protected void startRunner(IntegrationPlan integrationPlan) {
    final MessageRouteIntegrationPlan messageRouteIntegrationPlan =
        (MessageRouteIntegrationPlan) integrationPlan;
    final MessageRoute messageRoute = messageRouteIntegrationPlan.getMessageRoute();
    messageRoute.dispatch();
  }
}
