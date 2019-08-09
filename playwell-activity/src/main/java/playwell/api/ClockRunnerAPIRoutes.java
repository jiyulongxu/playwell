package playwell.api;

import com.google.common.collect.ImmutableMap;
import playwell.clock.ClockRunner;
import playwell.common.Result;
import playwell.integration.ClockRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.util.validate.Field;
import spark.Request;
import spark.Response;
import spark.Service;


/**
 * ClockRunner相关API
 */
public class ClockRunnerAPIRoutes extends APIRoutes {

  @Override
  protected void registerRoutes(Service service) {
    service.path("/v1/clock_runner", () -> {
      service.get("/status", this::status);
      service.post("/pause", this::pause);
      service.post("/rerun", this::rerun);
    });
  }

  private String status(Request request, Response response) {
    return getResponseWithQueryParam(request, response, new Field[]{}, args -> {
      final ClockRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
      final ClockRunner clockRunner = integrationPlan.getClockRunner();
      return Result.okWithData(ImmutableMap.of(
          "status", clockRunner.getActualStatus().getStatus(),
          "last_active", clockRunner.getLastActive()
      ));
    });
  }

  private String pause(Request request, Response response) {
    return postResponse(request, response, new Field[]{}, args -> {
      final ClockRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
      final ClockRunner clockRunner = integrationPlan.getClockRunner();
      clockRunner.pause();
      return Result.ok();
    });
  }

  private String rerun(Request request, Response response) {
    return postResponse(request, response, new Field[]{}, args -> {
      final ClockRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
      final ClockRunner clockRunner = integrationPlan.getClockRunner();
      clockRunner.rerun();
      return Result.ok();
    });
  }
}
