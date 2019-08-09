package playwell.launcher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.activity.ActivityRunner;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlan;
import playwell.integration.TopComponentType;
import playwell.service.ServiceRunner;

/**
 * ActivityRunnerLauncher
 */
public class ActivityRunnerLauncher extends BaseLauncher {

  private static final String NAME = "activity";

  public ActivityRunnerLauncher() {
    super("playwell.integration.StandardActivityRunnerIntegrationPlan");
  }

  @Override
  public String moduleName() {
    return NAME;
  }

  @Override
  protected void startRunner(IntegrationPlan integrationPlan) {
    final ActivityRunnerIntegrationPlan activityRunnerIntegrationPlan =
        (ActivityRunnerIntegrationPlan) integrationPlan;
    final ActivityRunner activityRunner = activityRunnerIntegrationPlan.getActivityRunner();
    new Thread(() -> {
      try {
        activityRunner.dispatch();
      } catch (Throwable t) {
        final Logger logger = LogManager.getLogger(ActivityRunnerLauncher.class);
        logger.error(t.getMessage(), t);
        System.exit(1);
      }
    }).start();
    if (activityRunnerIntegrationPlan.contains(TopComponentType.SERVICE_RUNNER)) {
      final ServiceRunner serviceRunner = activityRunnerIntegrationPlan.getServiceRunner();
      new Thread(() -> {
        try {
          serviceRunner.dispatch();
        } catch (Throwable t) {
          final Logger logger = LogManager.getLogger(ActivityRunnerLauncher.class);
          logger.error(t.getMessage(), t);
          System.exit(1);
        }
      }).start();
    }
  }
}
