package playwell.api;

import playwell.clock.Clock;
import playwell.clock.UserScanOperation;
import playwell.common.Result;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.util.validate.Field;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * Clock相关API
 */
class ClockAPIRoutes extends APIRoutes {

  ClockAPIRoutes() {

  }

  @Override
  protected void registerRoutes(Service service) {
    service.path("/v1/clock", () -> {
      service.post("/scan", this::scan);
      service.post("/stop_scan", this::stopScan);
    });
  }

  private String scan(Request request, Response response) {
    return postResponse(
        request,
        response,
        new Field[]{},
        args -> {
          getClock().scanAll(UserScanOperation.buildWithArgs(args));
          return Result.ok();
        }
    );
  }

  private String stopScan(Request request, Response response) {
    return postResponse(
        request,
        response,
        new Field[]{},
        args -> {
          getClock().stopScan();
          return Result.ok();
        }
    );
  }

  private Clock getClock() {
    IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    return (Clock) integrationPlan.getTopComponent(TopComponentType.CLOCK);
  }
}
