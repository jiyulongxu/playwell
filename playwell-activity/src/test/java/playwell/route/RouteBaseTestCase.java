package playwell.route;

import playwell.integration.IntegrationPlanFactory;
import playwell.integration.MessageRouteIntegrationPlan;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MySQLMessageBusManager;
import playwell.route.migration.MigrationCoordinator;

public abstract class RouteBaseTestCase {

  protected SlotsManager slotsManager;

  protected MigrationCoordinator coordinator;

  protected MessageBusManager messageBusManager;

  protected void setUp() {
    final IntegrationPlanFactory integrationPlanFactory = IntegrationPlanFactory.getInstance();
    integrationPlanFactory.clean();
    integrationPlanFactory.intergrateWithYamlConfigFile(
        "playwell.integration.StandardMessageRouteIntegrationPlan",
        "config/playwell_route.yml"
    );

    MessageRouteIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    this.slotsManager = integrationPlan.getSlotsManager();
    this.coordinator = this.slotsManager.getMigrationCoordinator();
    this.messageBusManager = integrationPlan.getMessageBusManager();
  }

  protected void tearDown() {
    IntegrationPlanFactory.getInstance().clean();
    ((MySQLSlotsManager) slotsManager).removeAll();
    ((MySQLMessageBusManager) messageBusManager).removeAll();
  }
}
