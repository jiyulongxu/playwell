package playwell.api;

import java.util.Collections;
import playwell.common.Result;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.message.domainid.MessageDomainIDStrategyManager;
import playwell.util.validate.Field;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * DomainIDStrategy 相关API
 */
class DomainIDStrategyAPIRoutes extends APIRoutes {

  private static Field[] ADD_API_FIELDS = new Field[]{
      new Field.Builder("name").required(true).build(),
      new Field.Builder("cond_expression").required(true).build(),
      new Field.Builder("domain_id_expression").required(true).build()
  };

  private static Field[] DELETE_API_FIELDS = new Field[]{
      new Field.Builder("name").required(true).build(),
  };

  private static Field[] QUERY_ALL_API_FIELDS = new Field[]{};

  @Override
  protected void registerRoutes(Service service) {
    service.path("/v1/domain_id", () -> {
      service.delete("", this::delete);
      service.delete("/", this::delete);
      service.post("/add", this::add);
      service.get("/all", this::queryAll);
    });
  }

  private String add(Request request, Response response) {
    return postResponse(
        request,
        response,
        ADD_API_FIELDS,
        args -> getMgr().addMessageDomainIDStrategy(
            args.getString("name"),
            args.getString("cond_expression"),
            args.getString("domain_id_expression")
        )
    );
  }

  private String delete(Request request, Response response) {
    return deleteResponse(
        request,
        response,
        DELETE_API_FIELDS,
        args -> getMgr().removeMessageDomainIDStrategy(args.getString("name"))
    );
  }

  private String queryAll(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        QUERY_ALL_API_FIELDS,
        args -> Result.okWithData(Collections.singletonMap(
            "strategies", getMgr().getAllMessageDomainIDStrategies()
        ))
    );
  }

  private MessageDomainIDStrategyManager getMgr() {
    IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    return (MessageDomainIDStrategyManager) integrationPlan.getTopComponent(
        TopComponentType.MESSAGE_DOMAIN_ID_STRATEGY_MANAGER);
  }
}
