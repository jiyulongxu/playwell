package playwell.api;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import playwell.common.EasyMap;
import playwell.common.Mappable;
import playwell.common.Result;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;
import playwell.message.bus.codec.MapMessageCodec;
import playwell.message.bus.codec.MessageCodec;
import playwell.util.validate.Field;
import playwell.util.validate.FieldType;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * MessageBus相关API
 */
class MessageBusAPIRoutes extends APIRoutes {

  private static final Field[] OPEN_API_FIELDS = new Field[]{
      new Field.Builder("name").required(true).build(),
  };

  private static final Field[] WRITE_API_FIELDS = new Field[]{
      new Field.Builder("message_bus").type(FieldType.Str).required(true).build(),
  };

  private static final Field[] CLOSE_API_FIELDS = new Field[]{
      new Field.Builder("name").required(true).build(),
  };

  private static final Field[] QUERY_ALL_API_FIELDS = new Field[]{};

  private static final Field[] QUERY_BY_NAME_API_FIELDS = new Field[]{
      new Field.Builder("name").required(true).build(),
  };

  private static final Field[] DELETE_API_FIELDS = new Field[]{
      new Field.Builder("name").required(true).build(),
  };

  @Override
  protected void registerRoutes(Service service) {
    service.path("/v1/message_bus", () -> {
      service.post("/register", this::register);
      service.post("/open", this::open);
      service.post("/write", this::write);
      service.post("/close", this::close);
      service.get("/all", this::queryAll);
      service.get("/:name", this::queryByName);
      service.delete("", this::delete);
      service.delete("/", this::delete);
    });
  }

  private String register(Request request, Response response) {
    return postResponse(
        request,
        response,
        new Field[]{},
        args -> {
          final EasyMap config = args.getSubArguments("config");
          final String clazz = config.getString("class");
          if (config.isEmpty()) {
            return Result.failWithCodeAndMessage(
                "bad_request", "The config of the message bus is required!");
          }

          if (!config.contains("name")) {
            return Result.failWithCodeAndMessage(
                "bad_request", "The config item 'name' is required!");
          }

          return getMgr().registerMessageBus(clazz, config.toMap());
        }
    );
  }

  private String open(Request request, Response response) {
    return postResponse(
        request,
        response,
        OPEN_API_FIELDS,
        args -> getMgr().openMessageBus(args.getString("name"))
    );
  }

  private String write(Request request, Response response) {
    return postResponse(
        request,
        response,
        WRITE_API_FIELDS,
        args -> {
          final String messageBusName = args.getString("message_bus");
          final List<EasyMap> messages = args.getSubArgumentsList("messages");
          if (CollectionUtils.isEmpty(messages)) {
            return Result.failWithCodeAndMessage(
                "bad_request",
                "There are no messages to write"
            );
          }
          final MessageBusManager messageBusManager = getMgr();
          final Optional<MessageBus> messageBusOptional = messageBusManager
              .getMessageBusByName(messageBusName);
          if (!messageBusOptional.isPresent()) {
            return Result.failWithCodeAndMessage(
                "bad_request",
                String.format("Could not found the message bus: %s", messageBusName)
            );
          }
          final MessageBus messageBus = messageBusOptional.get();

          final MessageCodec messageCodec = new MapMessageCodec();
          try {
            messageBus.write(messages.stream().map(m -> messageCodec.decode(m.toMap())).collect(
                Collectors.toList()));
          } catch (MessageBusNotAvailableException e) {
            throw new RuntimeException(e);
          }

          return Result.ok();
        }
    );
  }

  private String close(Request request, Response response) {
    return postResponse(
        request,
        response,
        CLOSE_API_FIELDS,
        args -> getMgr().closeMessageBus(args.getString("name"))
    );
  }

  private String queryAll(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        QUERY_ALL_API_FIELDS,
        args -> Result.okWithData(Collections.singletonMap(
            "buses", getMgr().getAllMessageBus()))
    );
  }

  private String queryByName(Request request, Response response) {
    return getResponseWithPathParam(
        request,
        response,
        QUERY_BY_NAME_API_FIELDS,
        args -> {
          String name = args.getString("name");
          Optional<MessageBus> messageBusOptional = getMgr()
              .getMessageBusByName(name);
          return messageBusOptional
              .map(messageBus -> Result.okWithData(((Mappable) messageBus).toMap()))
              .orElse(Result.failWithCodeAndMessage(
                  "not_found",
                  String.format("Could not found the message bus: %s", name)
              ));
        }
    );
  }

  private String delete(Request request, Response response) {
    return deleteResponse(
        request,
        response,
        DELETE_API_FIELDS,
        args -> getMgr().deleteMessageBus(args.getString("name"))
    );
  }

  private MessageBusManager getMgr() {
    IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    return (MessageBusManager) integrationPlan
        .getTopComponent(TopComponentType.MESSAGE_BUS_MANAGER);
  }
}
