package playwell.api;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import playwell.activity.ActivityRunner;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ActivityThreadPool;
import playwell.activity.thread.UserScanOperation;
import playwell.clock.CachedTimestamp;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;
import playwell.message.sys.ActivityThreadCtrlMessage;
import playwell.message.sys.ActivityThreadCtrlMessage.Commands;
import playwell.service.ServiceMeta;
import playwell.service.ServiceMetaManager;
import playwell.util.validate.Field;
import playwell.util.validate.FieldType;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * ActivityThread相关API
 */
class ActivityThreadAPIRoutes extends APIRoutes {

  private static final Field[] QUERY_API_FIELDS = new Field[]{
      new Field.Builder("activity_id").type(FieldType.Int).required(true).build(),
      new Field.Builder("domain_id").type(FieldType.Str).required(true).build()
  };

  private static final Field[] PAUSE_API_FIELDS = new Field[]{
      new Field.Builder("activity_id").type(FieldType.Int).required(true).build(),
      new Field.Builder("domain_id").type(FieldType.Str).required(true).build()
  };

  private static final Field[] CONTINUE_API_FIELDS = new Field[]{
      new Field.Builder("activity_id").type(FieldType.Int).required(true).build(),
      new Field.Builder("domain_id").type(FieldType.Str).required(true).build()
  };

  private static final Field[] KILL_API_FIELDS = new Field[]{
      new Field.Builder("activity_id").type(FieldType.Int).required(true).build(),
      new Field.Builder("domain_id").type(FieldType.Str).required(true).build()
  };

  private static final Field[] ADD_REPLICATION_MESSAGE_BUS_API_FIELDS = new Field[]{
      new Field.Builder("message_bus").type(FieldType.Str).required(true).build(),
  };

  private static final Field[] REMOVE_REPLICATION_MESSAGE_BUS_API_FIELDS = new Field[]{
      new Field.Builder("message_bus").type(FieldType.Str).required(true).build(),
  };

  @Override
  protected void registerRoutes(Service service) {
    service.path("/v1/activity_thread", () -> {
      service.post("/replication_message_bus", this::addReplicationMessageBus);
      service.delete("/replication_message_bus", this::removeApplicationMessageBus);
      service.get("/replication_message_bus/all", this::viewAllReplicationMessageBuses);
      service.post("/pause", this::pauseActivityThread);
      service.post("/continue", this::continueActivityThread);
      service.post("/kill", this::killActivityThread);
      service.get("/:activity_id/:domain_id", this::queryActivityThread);
      service.post("/scan", this::scan);
      service.post("/stop_scan", this::stopScan);
    });
  }

  private String pauseActivityThread(Request request, Response response) {
    return postResponse(
        request,
        response,
        PAUSE_API_FIELDS,
        args -> this.sendCtrlMessage(args, Commands.PAUSE)
    );
  }

  private String continueActivityThread(Request request, Response response) {
    return postResponse(
        request,
        response,
        CONTINUE_API_FIELDS,
        args -> this.sendCtrlMessage(args, Commands.CONTINUE)
    );
  }

  private String killActivityThread(Request request, Response response) {
    return postResponse(
        request,
        response,
        KILL_API_FIELDS,
        args -> this.sendCtrlMessage(args, Commands.KILL)
    );
  }

  private Result sendCtrlMessage(EasyMap args, String command) {
    try {
      getMessageBus().write(new ActivityThreadCtrlMessage(
          CachedTimestamp.nowMilliseconds(),
          "",
          "",
          args.getInt("activity_id"),
          args.getString("domain_id"),
          command
      ));

      return Result.ok();
    } catch (MessageBusNotAvailableException e) {
      return Result.failWithCodeAndMessage(
          "bus_not_available",
          e.getMessage()
      );
    }
  }

  private String queryActivityThread(Request request, Response response) {
    return getResponseWithPathParam(
        request,
        response,
        QUERY_API_FIELDS,
        args -> {
          final int activityId = args.getInt("activity_id");
          final String domainId = args.getString("domain_id");

          Optional<ActivityThread> activityThreadOptional = getActivityThreadPool()
              .getActivityThread(
                  activityId,
                  domainId
              );

          return activityThreadOptional
              .map(activityThread -> Result.okWithData(activityThread.toMap()))
              .orElse(Result.failWithCodeAndMessage(
                  "not_found",
                  String.format(
                      "Could not found the activity thread, activity id: %d, domain id: %s",
                      activityId,
                      domainId
                  )
              ));
        }
    );
  }

  private String scan(Request request, Response response) {
    return postResponse(
        request,
        response,
        new Field[]{},
        args -> {
          final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
          if (integrationPlan.contains(TopComponentType.ACTIVITY_RUNNER)) {
            final ActivityRunner activityRunner = (ActivityRunner) integrationPlan
                .getTopComponent(TopComponentType.ACTIVITY_RUNNER);
            activityRunner.startScanProcess(args);
            return Result.ok();
          } else {
            final ActivityThreadPool activityThreadPool = (ActivityThreadPool) integrationPlan
                .getTopComponent(TopComponentType.ACTIVITY_THREAD_POOL);
            final Thread scanThread = new Thread(() -> activityThreadPool
                .scanAll(UserScanOperation.buildWithArgs(args)));
            scanThread.setDaemon(true);
            scanThread.start();
            return Result.ok();
          }
        }
    );
  }

  private String stopScan(Request request, Response response) {
    return postResponse(
        request,
        response,
        new Field[]{},
        args -> {
          ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
          ActivityThreadPool activityThreadPool = integrationPlan.getActivityThreadPool();
          activityThreadPool.stopScan();
          return Result.ok();
        }
    );
  }

  private String addReplicationMessageBus(Request request, Response response) {
    return postResponse(
        request,
        response,
        ADD_REPLICATION_MESSAGE_BUS_API_FIELDS,
        args -> {
          final ActivityThreadPool activityThreadPool = getActivityThreadPool();
          return activityThreadPool.addReplicationMessageBus(args.getString("message_bus"));
        }
    );
  }

  private String removeApplicationMessageBus(Request request, Response response) {
    return deleteResponse(
        request,
        response,
        REMOVE_REPLICATION_MESSAGE_BUS_API_FIELDS,
        args -> {
          final ActivityThreadPool activityThreadPool = getActivityThreadPool();
          return activityThreadPool.removeReplicationMessageBus(args.getString("message_bus"));
        }
    );
  }

  private String viewAllReplicationMessageBuses(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        new Field[]{},
        args -> Result.okWithData(ImmutableMap.of(
            "buses", getActivityThreadPool().getAllReplicationMessageBuses()))
    );
  }

  private ActivityThreadPool getActivityThreadPool() {
    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    return (ActivityThreadPool) integrationPlan
        .getTopComponent(TopComponentType.ACTIVITY_THREAD_POOL);
  }

  private MessageBus getMessageBus() {
    final ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final MessageBusManager messageBusManager = integrationPlan.getMessageBusManager();
    final ActivityRunner activityRunner = integrationPlan.getActivityRunner();
    final ServiceMetaManager serviceMetaManager = integrationPlan.getServiceMetaManager();
    final Optional<ServiceMeta> serviceMetaOptional = serviceMetaManager
        .getServiceMetaByName(activityRunner.getServiceName());
    if (!serviceMetaOptional.isPresent()) {
      throw new RuntimeException(
          "Could not found the activity runner service: " + activityRunner.getServiceName());
    }
    ServiceMeta serviceMeta = serviceMetaOptional.get();
    final Optional<MessageBus> messageBusOptional = messageBusManager
        .getMessageBusByName(serviceMeta.getMessageBus());
    if (!messageBusOptional.isPresent()) {
      throw new RuntimeException(
          "Could not found the message bus: " + serviceMeta.getMessageBus());
    }
    return messageBusOptional.get();
  }
}
