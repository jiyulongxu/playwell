package playwell.action.builtin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import playwell.action.ActionInstanceBuilder;
import playwell.action.ActionRuntimeException;
import playwell.action.SyncAction;
import playwell.action.builtin.SendAction.ArgNames.MsgArgNames;
import playwell.activity.ActivityRunner;
import playwell.activity.thread.ActivityThread;
import playwell.clock.CachedTimestamp;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.common.argument.Argument;
import playwell.common.argument.MapArgument;
import playwell.common.exception.BuildComponentException;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.message.Message;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;
import playwell.service.ServiceMeta;
import playwell.service.ServiceMetaManager;

/**
 * <p>SendAction可用于向指定的Service发送消息</p>
 * eg.
 *
 * <pre>
 *   - name: send
 *     type: send
 *     args:
 *       service: service_name
 *       messages:
 *         - type: xxxxxx
 *           attributes:
 *             a: 1
 *             b: 2
 *     ctrl: call("receive")
 * </pre>
 *
 * @author chihongze@gmail.com
 */
public class SendAction extends SyncAction {

  public static final String TYPE = "send";

  public static final ActionInstanceBuilder BUILDER = SendAction::new;

  public static final Consumer<Argument> ARG_SPEC = argument -> {
    if (!Argument.isMapArgument(argument)) {
      throw new BuildComponentException(
          "Invalid argument format, the send action only accept map argument"
      );
    }
    final Map<String, Argument> mapArguments = ((MapArgument) argument).getArgs();
    if (!mapArguments.containsKey(ArgNames.SERVICE)) {
      throw new BuildComponentException(
          "Invalid argument format, the send action required service argument"
      );
    }
    if (!mapArguments.containsKey(ArgNames.MESSAGES)) {
      throw new BuildComponentException(
          "Invalid argument format, the send action required messages argument"
      );
    }
  };

  public SendAction(ActivityThread activityThread) {
    super(activityThread);
  }

  @Override
  public Result execute() {
    final EasyMap args = new EasyMap(getMapArguments());
    final String serviceName = args.getString(ArgNames.SERVICE);

    ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    ActivityRunner activityRunner = integrationPlan.getActivityRunner();
    ServiceMetaManager serviceMetaManager = integrationPlan.getServiceMetaManager();
    MessageBusManager messageBusManager = integrationPlan.getMessageBusManager();

    final Optional<ServiceMeta> serviceMetaOptional = serviceMetaManager
        .getServiceMetaByName(serviceName);
    if (!serviceMetaOptional.isPresent()) {
      throw new ActionRuntimeException(
          ErrorCodes.SERVICE_NOT_FOUND, String.format("The service '%s' not found.", serviceName));
    }
    final ServiceMeta serviceMeta = serviceMetaOptional.get();

    final Optional<MessageBus> messageBusOptional = messageBusManager
        .getMessageBusByName(serviceMeta.getMessageBus());
    if (!messageBusOptional.isPresent()) {
      throw new ActionRuntimeException(
          ErrorCodes.MESSAGE_BUS_NOT_FOUND,
          String.format("The message bus '%s' not found", serviceMeta.getMessageBus())
      );
    }
    final MessageBus messageBus = messageBusOptional.get();

    final List<Message> messages = args.getSubArgumentsList(ArgNames.MESSAGES).stream()
        .map(subArgs ->
            new Message(
                subArgs.getString(MsgArgNames.TYPE),
                activityRunner.getServiceName(),
                serviceName,
                subArgs.getSubArguments(MsgArgNames.ATTRIBUTES).toMap(),
                CachedTimestamp.nowMilliseconds()
            )).collect(Collectors.toList());

    try {
      messageBus.write(messages);
    } catch (MessageBusNotAvailableException e) {
      throw new ActionRuntimeException(
          CommonRuntimeErrorCodes.BUS_UNAVAILABLE, e.getMessage());
    }

    return Result.ok();
  }

  interface ArgNames {

    String SERVICE = "service";

    String MESSAGES = "messages";

    interface MsgArgNames {

      String TYPE = "type";

      String ATTRIBUTES = "attributes";
    }
  }

  interface ErrorCodes {

    String SERVICE_NOT_FOUND = "service_not_found";

    String MESSAGE_BUS_NOT_FOUND = "message_bus";
  }
}
