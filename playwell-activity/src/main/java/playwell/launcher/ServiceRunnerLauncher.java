package playwell.launcher;

import playwell.integration.IntegrationPlan;
import playwell.integration.ServiceRunnerIntegrationPlan;
import playwell.service.ServiceRunner;

/**
 * ServiceRunnerLauncher
 */
public class ServiceRunnerLauncher extends BaseLauncher {

  private static final String NAME = "service";

  public ServiceRunnerLauncher() {
    super(
        "playwell.integration.StandardServiceRunnerIntegrationPlan");
  }

  @Override
  public String moduleName() {
    return NAME;
  }

  @Override
  protected void startRunner(IntegrationPlan integrationPlan) {
    final ServiceRunnerIntegrationPlan serviceRunnerIntegrationPlan =
        (ServiceRunnerIntegrationPlan) integrationPlan;
    final ServiceRunner serviceRunner = serviceRunnerIntegrationPlan.getServiceRunner();
    serviceRunner.dispatch();
  }
}
