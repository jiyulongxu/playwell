package playwell.launcher;

import playwell.activity.ActivityReplicationRunner;
import playwell.integration.ActivityReplicationRunnerIntegrationPlan;
import playwell.integration.IntegrationPlan;

public class ActivityReplicationRunnerLauncher extends BaseLauncher {

  private static final String NAME = "activity_replication";

  public ActivityReplicationRunnerLauncher() {
    super("playwell.integration.StandardActivityReplicationRunnerIntegrationPlan");
  }

  @Override
  protected void startRunner(IntegrationPlan integrationPlan) {
    final ActivityReplicationRunnerIntegrationPlan replicaIntegrationPlan =
        (ActivityReplicationRunnerIntegrationPlan) integrationPlan;
    final ActivityReplicationRunner runner = replicaIntegrationPlan.getActivityReplicationRunner();
    runner.dispatch();
  }

  @Override
  public String moduleName() {
    return NAME;
  }
}
