package playwell.api;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import playwell.activity.Activity;
import playwell.activity.ActivityManager;
import playwell.activity.ActivityStatus;
import playwell.common.Result;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.util.validate.Field;
import playwell.util.validate.FieldType;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * Activity相关API
 */
class ActivityAPIRoutes extends APIRoutes {

  private static final Field[] CREATE_API_FIELDS = new Field[]{
      new Field.Builder("definition").type(FieldType.Str).required(true).build(),
      new Field.Builder("display_name").type(FieldType.Str)
          .required(false).defaultValue("").build()
  };

  private static final Field[] PAUSE_API_FIELDS = new Field[]{
      new Field.Builder("id").type(FieldType.Int).required(true).build()
  };

  private static final Field[] CONTINUE_API_FIELDS = new Field[]{
      new Field.Builder("id").type(FieldType.Int).required(true).build()
  };

  private static final Field[] KILL_API_FIELDS = new Field[]{
      new Field.Builder("id").type(FieldType.Int).required(true).build()
  };

  private static final Field[] MODIFY_CONFIG_API_FIELDS = new Field[]{
      new Field.Builder("id").type(FieldType.Int).required(true).build()
  };

  private static final Field[] PUT_CONFIG_ITEM_API_FIELDS = new Field[]{
      new Field.Builder("id").type(FieldType.Int).required(true).build(),
      new Field.Builder("key").type(FieldType.Str).required(true).build(),
  };

  private static final Field[] REMOVE_CONFIG_ITEM_API_FIELDS = new Field[]{
      new Field.Builder("id").type(FieldType.Int).required(true).build(),
      new Field.Builder("key").type(FieldType.Str).required(true).build(),
  };

  private static final Field[] QUERY_API_FIELDS = new Field[]{
      new Field.Builder("id").type(FieldType.Int).required(true).build()
  };

  private static final Field[] QUERY_BY_DEF_API_FIELDS = new Field[]{
      new Field.Builder("name").type(FieldType.Str).required(true).build()
  };

  private static final Field[] QUERY_BY_STATUS_API_FIELDS = new Field[]{
      new Field.Builder("status").type(FieldType.Str).required(true).build()
  };

  private static final Field[] QUERY_ALL_API_FIELDS = new Field[]{};

  public ActivityAPIRoutes() {

  }

  @Override
  protected void registerRoutes(Service service) {
    service.path("/v1/activity", () -> {
      service.post("/create", this::create);
      service.post("/pause", this::pause);
      service.post("/continue", this::_continue);
      service.post("/kill", this::kill);
      service.post("/modify/config", this::modifyConfig);
      service.post("/modify/config/item", this::putConfigItem);
      service.delete("/modify/config/item", this::removeConfigItem);
      service.get("/all", this::queryAll);
      service.get("/:id", this::queryActivity);
      service.get("/definition/:name", this::queryByDefinitionName);
      service.get("/status/:status", this::queryByStatus);
    });
  }

  private String create(Request request, Response response) {
    return postResponse(
        request,
        response,
        CREATE_API_FIELDS,
        args -> getActivityManager().createNewActivity(
            args.getString("definition"),
            args.getString("display_name"),
            args.getSubArguments("config").toMap()
        )
    );
  }

  private String pause(Request request, Response response) {
    return postResponse(
        request,
        response,
        PAUSE_API_FIELDS,
        args -> getActivityManager().pauseActivity(args.getInt("id"))
    );
  }

  private String _continue(Request request, Response response) {
    return postResponse(
        request,
        response,
        CONTINUE_API_FIELDS,
        args -> getActivityManager().continueActivity(args.getInt("id"))
    );
  }

  private String kill(Request request, Response response) {
    return postResponse(
        request,
        response,
        KILL_API_FIELDS,
        args -> getActivityManager().killActivity(args.getInt("id"))
    );
  }

  private String modifyConfig(Request request, Response response) {
    return postResponse(
        request,
        response,
        MODIFY_CONFIG_API_FIELDS,
        args -> getActivityManager().modifyConfig(
            args.getInt("id"),
            args.getSubArguments("config").toMap()
        )
    );
  }

  private String putConfigItem(Request request, Response response) {
    return postResponse(
        request,
        response,
        PUT_CONFIG_ITEM_API_FIELDS,
        args -> {
          final int activityId = args.getInt("id");
          final String configKey = args.getString("key");
          final String type = args.getString("type", "str");
          final String configValStr = args.getString("value");
          final ActivityManager activityManager = getActivityManager();
          final Optional<Activity> activityOptional = activityManager.getActivityById(activityId);
          if (!activityOptional.isPresent()) {
            return Result.failWithCodeAndMessage(
                "not_found",
                String.format("Could not found the activity: %d", activityId)
            );
          }
          final Activity activity = activityOptional.get();
          final Map<String, Object> config = new HashMap<>();
          if (activity.getConfig() != null) {
            config.putAll(activity.getConfig());
          }

          final Object configVal;
          if ("int".equals(type)) {
            configVal = Integer.parseInt(configValStr);
          } else if ("bool".equals(type)) {
            configVal = Boolean.valueOf(configValStr);
          } else if ("long".equals(type)) {
            configVal = Long.valueOf(configValStr);
          } else if ("double".equals(type)) {
            configVal = Double.valueOf(configValStr);
          } else if ("map".equals(type)) {
            configVal = JSONObject.parseObject(configValStr).getInnerMap();
          } else if ("list".equals(type)) {
            configVal = JSONArray.parseArray(configValStr).toJavaList(Object.class);
          } else {
            configVal = configValStr;
          }

          config.put(configKey, configVal);
          return activityManager.modifyConfig(activityId, config);
        }
    );
  }

  private String removeConfigItem(Request request, Response response) {
    return deleteResponse(
        request,
        response,
        REMOVE_CONFIG_ITEM_API_FIELDS,
        args -> {
          final int activityId = args.getInt("id");
          final String configKey = args.getString("key");
          final ActivityManager activityManager = getActivityManager();
          final Optional<Activity> activityOptional = activityManager.getActivityById(activityId);
          if (!activityOptional.isPresent()) {
            return Result.failWithCodeAndMessage(
                "not_found",
                String.format("Could not found the activity: %d", activityId)
            );
          }
          final Activity activity = activityOptional.get();
          final Map<String, Object> config = new HashMap<>();
          if (activity.getConfig() != null) {
            config.putAll(activity.getConfig());
          }
          config.remove(configKey);
          return activityManager.modifyConfig(activityId, config);
        }
    );
  }

  private String queryActivity(Request request, Response response) {
    return getResponseWithPathParam(
        request,
        response,
        QUERY_API_FIELDS,
        args -> {
          final int activityId = args.getInt("id");
          final Optional<Activity> activityOptional = getActivityManager()
              .getActivityById(activityId);
          if (!activityOptional.isPresent()) {
            return Result.failWithCodeAndMessage(
                "not_found",
                String.format("Could not found the activity: %d", activityId)
            );
          }

          final Activity activity = activityOptional.get();
          return Result.okWithData(activity.toMap());
        }
    );
  }

  private String queryByDefinitionName(Request request, Response response) {
    return getResponseWithPathParam(
        request,
        response,
        QUERY_BY_DEF_API_FIELDS,
        args -> {
          final String definitionName = args.getString("name");
          final Collection<Activity> activities = getActivityManager()
              .getActivitiesByDefinitionName(definitionName);
          return Result.okWithData(Collections.singletonMap("activities", activities));
        }
    );
  }

  private String queryByStatus(Request request, Response response) {
    return getResponseWithPathParam(
        request,
        response,
        QUERY_BY_STATUS_API_FIELDS,
        args -> {
          final String statusName = args.getString("status");
          final Optional<ActivityStatus> activityStatusOptional = ActivityStatus
              .valueOfByStatus(statusName);
          if (!activityStatusOptional.isPresent()) {
            return Result.failWithCodeAndMessage(
                "bad_request",
                String.format("Unknown activity status: %s", statusName)
            );
          }
          final ActivityStatus activityStatus = activityStatusOptional.get();
          final Collection<Activity> activities = getActivityManager()
              .getActivitiesByStatus(activityStatus);
          return Result.okWithData(Collections.singletonMap("activities", activities));
        }
    );
  }

  private String queryAll(Request request, Response response) {
    return getResponseWithPathParam(
        request,
        response,
        QUERY_ALL_API_FIELDS,
        args -> Result.okWithData(Collections.singletonMap(
            "activities", getActivityManager().getAllActivities()
        ))
    );
  }

  private ActivityManager getActivityManager() {
    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    return (ActivityManager) integrationPlan.getTopComponent(TopComponentType.ACTIVITY_MANAGER);
  }
}
