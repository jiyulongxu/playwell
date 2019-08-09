package playwell.api;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import playwell.common.Result;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.route.SlotsManager;
import playwell.route.migration.MigrationCoordinator;
import playwell.util.validate.Field;
import playwell.util.validate.FieldType;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * Slots管理API
 */
class SlotAPIRoutes extends APIRoutes {

  private static final Field[] ALLOC_FIELDS = new Field[]{
      new Field.Builder("slots_num").required(true).type(FieldType.Int).build()
  };

  private static final Field[] GET_SERVICE_BY_SLOT_FIELDS = new Field[]{
      new Field.Builder("slot").required(true).type(FieldType.Int).build()
  };

  private static final Field[] GET_SLOTS_BY_SERVICE_FIELDS = new Field[]{
      new Field.Builder("service").required(true).type(FieldType.Str).build()
  };

  private static final Field[] GET_SERVICE_BY_HASH_FIELDS = new Field[]{
      new Field.Builder("hash").required(true).type(FieldType.LongInt).build()
  };

  private static final Field[] GET_SERVICE_BY_KEY_FIELDS = new Field[]{
      new Field.Builder("key").required(true).type(FieldType.Str).build()
  };

  private static final Field[] GET_SLOT_BY_KEY_FIELDS = new Field[]{
      new Field.Builder("key").required(true).type(FieldType.Str).build()
  };

  @Override
  protected void registerRoutes(Service service) {
    service.path("/v1/slot", () -> {
      service.post("/alloc", this::alloc);
      service.post("/migration/start", this::startMigration);
      service.post("/migration/stop", this::stopMigration);
      service.post("/migration/continue", this::continueMigration);
      service.get("/migration/status", this::getMigrationStatus);
      service.get("/get_service_by_slot", this::getServiceBySlot);
      service.get("/get_slots_by_service", this::getSlotsByService);
      service.get("/distribution", this::getDistribution);
      service.get("/get_service_by_hash", this::getServiceByHash);
      service.get("/get_service_by_key", this::getServiceByKey);
      service.get("/get_slot_by_key", this::getSlotByKey);
    });
  }

  @SuppressWarnings({"unchecked"})
  private String alloc(Request request, Response response) {
    return postResponse(
        request,
        response,
        ALLOC_FIELDS,
        args -> {
          int slotsNum = args.getInt("slots_num");
          Map<String, Integer> slotsPerNode = (Map<String, Integer>) args.get("slots_per_node");
          return getMgr().allocSlots(slotsNum, slotsPerNode);
        }
    );
  }

  @SuppressWarnings({"unchecked"})
  private String startMigration(Request request, Response response) {
    return postResponse(
        request,
        response,
        new Field[]{},
        args -> {
          final String messageBus = args.getString("message_bus");
          final Map<String, Object> inputMessageBusConfig = (Map<String, Object>)
              args.get("input_message_bus_config");
          final Map<String, Object> outputMessageBusConfig = (Map<String, Object>)
              args.get("output_message_bus_config");
          final Map<String, Integer> slotsDistribution = (Map<String, Integer>)
              args.get("slots_distribution");
          final String comment = args.getString("comment", "");
          MigrationCoordinator migrationCoordinator = getMgr().getMigrationCoordinator();
          return migrationCoordinator.startMigrationPlan(
              messageBus,
              inputMessageBusConfig,
              outputMessageBusConfig,
              slotsDistribution,
              comment
          );
        }
    );
  }

  private String stopMigration(Request request, Response response) {
    return postResponse(
        request,
        response,
        new Field[]{},
        args -> {
          MigrationCoordinator migrationCoordinator = getMgr().getMigrationCoordinator();
          migrationCoordinator.stop();
          return Result.ok();
        }
    );
  }

  private String continueMigration(Request request, Response response) {
    return postResponse(
        request,
        response,
        new Field[]{},
        args -> {
          MigrationCoordinator migrationCoordinator = getMgr().getMigrationCoordinator();
          return migrationCoordinator.continueMigrationPlan();
        }
    );
  }

  private String getMigrationStatus(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        new Field[]{},
        args -> {
          MigrationCoordinator migrationCoordinator = getMgr().getMigrationCoordinator();
          return migrationCoordinator.getCurrentStatus();
        }
    );
  }

  private String getServiceBySlot(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        GET_SERVICE_BY_SLOT_FIELDS,
        args -> {
          int slot = args.getInt("slot");
          Optional<String> serviceNameOptional = getMgr().getServiceNameBySlot(slot);
          if (!serviceNameOptional.isPresent()) {
            return Result.failWithCodeAndMessage(
                "not_found", String.format("Could not found the slot: %d", slot));
          }
          String serviceName = serviceNameOptional.get();
          return Result.okWithData(Collections.singletonMap("service", serviceName));
        }
    );
  }

  private String getSlotsByService(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        GET_SLOTS_BY_SERVICE_FIELDS,
        args -> {
          String service = args.getString("service");
          Collection<Integer> slots = getMgr().getSlotsByServiceName(service);
          return Result.okWithData(Collections.singletonMap("slots", slots));
        }
    );
  }

  private String getDistribution(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        new Field[]{},
        args -> getMgr().getSlotsDistribution()
    );
  }

  private String getServiceByHash(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        GET_SERVICE_BY_HASH_FIELDS,
        args -> {
          long hash = args.getLong("hash");
          String serviceName = getMgr().getServiceByHash(hash);
          return Result.okWithData(Collections.singletonMap("service", serviceName));
        }
    );
  }

  private String getServiceByKey(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        GET_SERVICE_BY_KEY_FIELDS,
        args -> {
          String key = args.getString("key");
          String serviceName = getMgr().getServiceByKey(key);
          return Result.okWithData(Collections.singletonMap("service", serviceName));
        }
    );
  }

  private String getSlotByKey(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        GET_SLOT_BY_KEY_FIELDS,
        args -> {
          String key = args.getString("key");
          int slot = getMgr().getSlotByKey(key);
          return Result.okWithData(Collections.singletonMap("slot", slot));
        }
    );
  }

  private SlotsManager getMgr() {
    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    return (SlotsManager) integrationPlan.getTopComponent(TopComponentType.SLOTS_MANAGER);
  }
}
