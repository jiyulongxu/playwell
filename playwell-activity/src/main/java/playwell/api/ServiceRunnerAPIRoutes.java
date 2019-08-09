package playwell.api;

import com.google.common.collect.ImmutableMap;
import playwell.common.Result;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.ServiceRunnerIntegrationPlan;
import playwell.service.ServiceRunner;
import playwell.util.validate.Field;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * ServiceRunner相关API
 */
class ServiceRunnerAPIRoutes extends APIRoutes {

  @Override
  protected void registerRoutes(Service service) {
    service.path("/v1/service_runner", () -> {
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
          ServiceRunner serviceRunner = getServiceRunner();
          return Result.okWithData(ImmutableMap.of(
              "status", serviceRunner.getStatus(),
              "last_active", serviceRunner.getLastActive()
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
          getServiceRunner().pause();
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
          getServiceRunner().rerun();
          return Result.ok();
        }
    );
  }

  private ServiceRunner getServiceRunner() {
    ServiceRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    return integrationPlan.getServiceRunner();
  }
}
