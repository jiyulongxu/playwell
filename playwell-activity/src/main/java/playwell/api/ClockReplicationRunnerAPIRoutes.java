package playwell.api;

import com.google.common.collect.ImmutableMap;
import playwell.clock.ClockReplicationRunner;
import playwell.common.Result;
import playwell.integration.ClockReplicationRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.util.validate.Field;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * ClockReplicationRunner相关API
 */
public class ClockReplicationRunnerAPIRoutes extends APIRoutes {

  @Override
  protected void registerRoutes(Service service) {
    service.path("/v1/clock_replication_runner", () -> {
      service.get("/status", this::status);
      service.post("/pause", this::pause);
      service.post("/rerun", this::rerun);
      service.post("/stop", this::stop);
    });
  }

  private String status(Request request, Response response) {
    return getResponseWithQueryParam(request, response, new Field[]{}, args -> {
      final ClockReplicationRunner runner = getRunner();
      return Result.okWithData(ImmutableMap.of(
          "status", runner.getActualStatus().getStatus(),
          "last_alive", runner.getLastActive()
      ));
    });
  }

  private String pause(Request request, Response response) {
    return postResponse(request, response, new Field[]{}, args -> {
      final ClockReplicationRunner runner = getRunner();
      runner.pause();
      return Result.ok();
    });
  }

  private String rerun(Request request, Response response) {
    return postResponse(request, response, new Field[]{}, args -> {
      final ClockReplicationRunner runner = getRunner();
      runner.rerun();
      return Result.ok();
    });
  }

  private String stop(Request request, Response response) {
    return postResponse(request, response, new Field[]{}, args -> {
      final ClockReplicationRunner runner = getRunner();
      runner.stop();
      return Result.ok();
    });
  }

  private ClockReplicationRunner getRunner() {
    final ClockReplicationRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory
        .currentPlan();
    return integrationPlan.getClockReplicationRunner();
  }
}
