package playwell.api;


import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.definition.ActivityDefinitionManager;
import playwell.activity.definition.YAMLActivityDefinitionCodec;
import playwell.common.Result;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.util.validate.Field;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * ActivityDefinition的相关API
 */
class ActivityDefinitionAPIRoutes extends APIRoutes {

  private static final Field[] CREATE_API_FIELDS = new Field[]{
      new Field.Builder("codec").required(false)
          .defaultValue(YAMLActivityDefinitionCodec.NAME).build(),
      new Field.Builder("version").required(true).build(),
      new Field.Builder("definition").required(true).build(),
      new Field.Builder("enable").required(false).defaultValue("true").build()
  };

  private static final Field[] VALIDATE_API_FIELDS = new Field[]{
      new Field.Builder("codec").required(false)
          .defaultValue(YAMLActivityDefinitionCodec.NAME).build(),
      new Field.Builder("definition").required(true).build(),
  };

  private static final Field[] MODIFY_API_FIELDS = new Field[]{
      new Field.Builder("codec").required(false)
          .defaultValue(YAMLActivityDefinitionCodec.NAME).build(),
      new Field.Builder("version").required(true).build(),
      new Field.Builder("definition").required(true).build(),
      new Field.Builder("enable").required(false)
          .defaultValue("true").build()
  };

  private static final Field[] ENABLE_API_FIELDS = new Field[]{
      new Field.Builder("name").required(true).build(),
      new Field.Builder("version").required(true).build(),
  };

  private static final Field[] DISABLE_API_FIELDS = new Field[]{
      new Field.Builder("name").required(true).build(),
      new Field.Builder("version").required(true).build(),
  };

  private static final Field[] DELETE_API_FIELDS = new Field[]{
      new Field.Builder("name").required(true).build(),
      new Field.Builder("version").required(true).build()
  };

  private static final Field[] GET_BY_NAME_API_FIELDS = new Field[]{
      new Field.Builder("name").required(true).build()
  };

  private static final Field[] GET_BY_NAME_VERSION_API_FIELDS = new Field[]{
      new Field.Builder("name").required(true).build(),
      new Field.Builder("version").required(true).build()
  };

  private static final Field[] GET_ALL_LATEST_API_FIELDS = new Field[]{};

  @Override
  protected void registerRoutes(Service service) {
    service.path("/v1/definition", () -> {
      service.post("/validate", this::validate);
      service.post("/create", this::create);
      service.post("/modify", this::modify);
      service.post("/enable", this::enable);
      service.post("/disable", this::disable);
      service.delete("/delete", this::delete);
      service.get("/all/latest", this::getAllLatest);
      service.get("/:name", this::getByName);
      service.get("/:name/:version", this::getByNameAndVersion);
    });
  }

  private String validate(Request request, Response response) {
    return postResponse(
        request,
        response,
        VALIDATE_API_FIELDS,
        args -> getDefManager().validateActivityDefinition(
            args.getString("codec"),
            args.getString("definition")
        )
    );
  }

  private String create(Request request, Response response) {
    return postResponse(
        request,
        response,
        CREATE_API_FIELDS,
        args -> getDefManager().newActivityDefinition(
            args.getString("codec"),
            args.getString("version"),
            args.getString("definition"),
            args.getBoolean("enable")
        )
    );
  }

  private String modify(Request request, Response response) {
    return postResponse(
        request,
        response,
        MODIFY_API_FIELDS,
        args -> getDefManager().modifyActivityDefinition(
            args.getString("codec"),
            args.getString("version"),
            args.getString("definition"),
            args.getBoolean("enable")
        )
    );
  }

  private String enable(Request request, Response response) {
    return postResponse(
        request,
        response,
        ENABLE_API_FIELDS,
        args -> {
          final String name = args.getString("name");
          final String version = args.getString("version");
          final ActivityDefinitionManager activityDefinitionManager = getDefManager();
          return activityDefinitionManager.enableActivityDefinition(name, version);
        }
    );
  }

  private String disable(Request request, Response response) {
    return postResponse(
        request,
        response,
        DISABLE_API_FIELDS,
        args -> {
          final String name = args.getString("name");
          final String version = args.getString("version");
          final ActivityDefinitionManager activityDefinitionManager = getDefManager();
          return activityDefinitionManager.disableActivityDefinition(name, version);
        }
    );
  }

  private String delete(Request request, Response response) {
    return deleteResponse(
        request,
        response,
        DELETE_API_FIELDS,
        args -> getDefManager().deleteActivityDefinition(
            args.getString("name"),
            args.getString("version")
        )
    );
  }

  private String getByName(Request request, Response response) {
    return getResponseWithPathParam(
        request,
        response,
        GET_BY_NAME_API_FIELDS,
        args -> {
          Collection<ActivityDefinition> definitions = getDefManager()
              .getActivityDefinitionsByName(args.getString("name"));
          return Result.okWithData(Collections.singletonMap("definitions", definitions));
        }
    );
  }

  private String getByNameAndVersion(Request request, Response response) {
    return getResponseWithPathParam(
        request,
        response,
        GET_BY_NAME_VERSION_API_FIELDS,
        args -> {
          final String name = args.getString("name");
          final String version = args.getString("version");
          Optional<ActivityDefinition> activityDefinitionOptional = getDefManager()
              .getActivityDefinition(name, version);
          return activityDefinitionOptional
              .map(activityDefinition ->
                  Result.okWithData(activityDefinition.toMap()))
              .orElse(Result.failWithCodeAndMessage(
                  "not_found",
                  String.format(
                      "Could not found the activity definition, name: %s, version: %s",
                      name,
                      version
                  )
              ));
        }
    );
  }

  private String getAllLatest(Request request, Response response) {
    return getResponseWithPathParam(
        request,
        response,
        GET_ALL_LATEST_API_FIELDS,
        args -> {
          Collection<ActivityDefinition> allLatestDefinitions = getDefManager()
              .getAllLatestDefinitions();
          return Result.okWithData(Collections.singletonMap("definitions", allLatestDefinitions));
        }
    );
  }

  private ActivityDefinitionManager getDefManager() {
    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    return (ActivityDefinitionManager) integrationPlan
        .getTopComponent(TopComponentType.ACTIVITY_DEFINITION_MANAGER);
  }

}
