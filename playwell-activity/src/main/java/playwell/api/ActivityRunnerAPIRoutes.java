package playwell.api;

import com.google.common.collect.ImmutableMap;
import playwell.activity.ActivityRunner;
import playwell.common.Result;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.util.validate.Field;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * ActivityRunner相关的API
 */
class ActivityRunnerAPIRoutes extends APIRoutes {

  @Override
  protected void registerRoutes(Service service) {
    service.path("/v1/activity_runner", () -> {
      service.get("/status", this::status);
      service.post("/pause", this::pause);
      service.post("/rerun", this::rerun);
    });
  }

  private String status(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        new Field[]{},
        args -> {
          final ActivityRunner activityRunner = getRunner();
          return Result.okWithData(ImmutableMap.of(
              "status", activityRunner.getStatus().getStatus(),
              "last_active", activityRunner.getLastActive()
          ));
        }
    );
  }

  private String pause(Request request, Response response) {
    return postResponse(
        request,
        response,
        new Field[]{},
        args -> {
          getRunner().pause();
          return Result.ok();
        }
    );
  }

  private String rerun(Request request, Response response) {
    return postResponse(
        request,
        response,
        new Field[]{},
        args -> {
          getRunner().rerun();
          return Result.ok();
        }
    );
  }

  private ActivityRunner getRunner() {
    ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    return integrationPlan.getActivityRunner();
  }
}
