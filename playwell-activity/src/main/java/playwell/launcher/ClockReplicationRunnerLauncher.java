package playwell.launcher;

import playwell.clock.ClockReplicationRunner;
import playwell.integration.ClockReplicationRunnerIntegrationPlan;
import playwell.integration.IntegrationPlan;

/**
 * ClockReplicationRunnerLauncher
 */
public class ClockReplicationRunnerLauncher extends BaseLauncher {

  private static final String MODULE = "clock_replication";

  public ClockReplicationRunnerLauncher() {
    super("playwell.integration.StandardClockReplicationRunnerIntegrationPlan");
  }

  @Override
  protected void startRunner(IntegrationPlan integrationPlan) {
    final ClockReplicationRunnerIntegrationPlan clockReplicationIntegrationPlan =
        (ClockReplicationRunnerIntegrationPlan) integrationPlan;
    final ClockReplicationRunner runner = clockReplicationIntegrationPlan
        .getClockReplicationRunner();
    runner.dispatch();
  }

  @Override
  public String moduleName() {
    return MODULE;
  }
}
