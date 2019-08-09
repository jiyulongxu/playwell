package playwell.api;

import java.util.Collections;
import java.util.Optional;
import playwell.common.Result;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.service.ServiceMeta;
import playwell.service.ServiceMetaManager;
import playwell.service.ServiceMetaManager.ErrorCodes;
import playwell.util.validate.Field;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * ServiceMeta相关API
 */
class ServiceMetaAPIRoutes extends APIRoutes {

  private static final Field[] REGISTER_API_FIELDS = new Field[]{
      new Field.Builder("name").required(true).build(),
      new Field.Builder("message_bus").required(true).build()
  };

  private static final Field[] REMOVE_API_FIELDS = new Field[]{
      new Field.Builder("name").required(true).build(),
  };

  private static final Field[] QUERY_API_FIELDS = new Field[]{
      new Field.Builder("name").required(true).build(),
  };

  @Override
  protected void registerRoutes(Service service) {
    service.path("/v1/service_meta", () -> {
      service.delete("", this::delete);
      service.delete("/", this::delete);
      service.post("/register", this::register);
      service.get("/all", this::queryAll);
      service.get("/:name", this::query);
    });
  }

  private String register(Request request, Response response) {
    return postResponse(
        request,
        response,
        REGISTER_API_FIELDS,
        args -> {
          ServiceMeta serviceMeta = new ServiceMeta(
              args.getString("name"),
              args.getString("message_bus"),
              args.getSubArguments("config").toMap()
          );
          return getMgr().registerServiceMeta(serviceMeta);
        }
    );
  }

  private String delete(Request request, Response response) {
    return deleteResponse(
        request,
        response,
        REMOVE_API_FIELDS,
        args -> getMgr().removeServiceMeta(args.getString("name"))
    );
  }

  private String queryAll(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        new Field[]{},
        args -> Result.okWithData(Collections.singletonMap(
            "services", getMgr().getAllServiceMeta()))
    );
  }

  private String query(Request request, Response response) {
    return getResponseWithPathParam(
        request,
        response,
        QUERY_API_FIELDS,
        args -> {
          final Optional<ServiceMeta> serviceMetaOptional = getMgr().getServiceMetaByName(
              args.getString("name"));
          if (!serviceMetaOptional.isPresent()) {
            return Result.failWithCodeAndMessage(
                ErrorCodes.NOT_FOUND,
                "Could not found the service meta: " + args.getString("name")
            );
          }

          final ServiceMeta serviceMeta = serviceMetaOptional.get();
          return Result.okWithData(serviceMeta.toMap());
        }
    );
  }

  private ServiceMetaManager getMgr() {
    IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    return (ServiceMetaManager) integrationPlan
        .getTopComponent(TopComponentType.SERVICE_META_MANAGER);
  }
}
