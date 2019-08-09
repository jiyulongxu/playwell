package playwell.launcher;

import playwell.clock.ClockRunner;
import playwell.integration.ClockRunnerIntegrationPlan;
import playwell.integration.IntegrationPlan;

/**
 * ClockRunnerLauncher
 */
public class ClockRunnerLauncher extends BaseLauncher {

  private static final String NAME = "clock";

  public ClockRunnerLauncher() {
    super("playwell.integration.StandardClockRunnerIntegrationPlan");
  }

  @Override
  protected void startRunner(IntegrationPlan integrationPlan) {
    final ClockRunnerIntegrationPlan clockRunnerIntegrationPlan = (ClockRunnerIntegrationPlan) integrationPlan;
    final ClockRunner clockRunner = clockRunnerIntegrationPlan.getClockRunner();
    clockRunner.dispatch();
  }

  @Override
  public String moduleName() {
    return NAME;
  }
}
