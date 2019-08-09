package playwell.action;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import playwell.activity.thread.ActivityThread;
import playwell.clock.CachedTimestamp;
import playwell.clock.Clock;
import playwell.clock.ClockMessage;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.common.argument.Argument;
import playwell.common.argument.MapArgument;
import playwell.common.exception.BuildComponentException;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.message.Message;
import playwell.message.ServiceRequestMessage;
import playwell.message.ServiceResponseMessage;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;
import playwell.service.ServiceMeta;
import playwell.service.ServiceMetaManager;

/**
 * ServiceActionInstanceBuilder用来构建基于服务的AsyncAction
 *
 * @author chihongze@gmail.com
 */
public class ServiceActionInstanceBuilder implements ActionInstanceBuilder {

  private final String serviceName;

  public ServiceActionInstanceBuilder(String serviceName) {
    this.serviceName = serviceName;
  }

  @Override
  public Action build(ActivityThread activityThread) {
    return new ServiceAction(activityThread, serviceName);
  }

  /**
   * 通用的service action
   *
   * <pre>
   *   - name: test_service
   *     type: service_name
   *     args:
   *       request:
   *         req_arg1: xxx1
   *         req_arg2: xxx2
   *         req_arg3: xxx3
   *       timeout: TIMESTAMP("10 SECONDS")
   *     ctrl:
   *       - when: result.isTimeout() AND context.get("test_service.timeout") < 3:
   *         then: CALL("test_service")
   *         context_vars:
   *           test_service.timeout: context.get("test_service.timeout", 0) + 1
   *       - when: result.isOk()
   *         then: CALL("next_action")
   *       - when: result.isFail()
   *         then: FAIL_BECAUSE("error_code")
   * </pre>
   */
  public static class ServiceAction extends AsyncAction {

    private final String serviceName;

    public ServiceAction(
        ActivityThread activityThread, String serviceName) {
      super(activityThread);
      this.serviceName = serviceName;
    }

    static Consumer<Argument> getArgSpec(String serviceName) {
      return argument -> {
        if (!Argument.isMapArgument(argument)) {
          throw new BuildComponentException(
              String.format(
                  "The service action '%s' only accept map arguments", serviceName)
          );
        }

        final Map<String, Argument> mapArgumentsDef = ((MapArgument) argument).getArgs();
        if (!mapArgumentsDef.containsKey(ArgNames.REQUEST)) {
          throw new BuildComponentException(String.format(
              "The service action '%s' required 'request' argument", serviceName));
        }
      };
    }

    @Override
    public void sendRequest() {
      final ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
      final ServiceMetaManager serviceMetaManager = integrationPlan.getServiceMetaManager();
      final Clock clock = integrationPlan.getClock();
      final MessageBusManager messageBusManager = integrationPlan.getMessageBusManager();

      final Optional<ServiceMeta> serviceMetaOptional = serviceMetaManager
          .getServiceMetaByName(serviceName);
      if (!serviceMetaOptional.isPresent()) {
        throw new ActionRuntimeException(
            CommonRuntimeErrorCodes.SERVICE_NOT_FOUND,
            String.format("Could not found the service: '%s'", serviceName)
        );
      }
      final ServiceMeta serviceMeta = serviceMetaOptional.get();
      final String messageBusName = serviceMeta.getMessageBus();

      final Optional<MessageBus> messageBusOptional = messageBusManager
          .getMessageBusByName(messageBusName);
      if (!messageBusOptional.isPresent()) {
        throw new ActionRuntimeException(
            CommonRuntimeErrorCodes.BUS_NOT_FOUND,
            String.format("Could not found the message bus: '%s'", messageBusName)
        );
      }
      MessageBus messageBus = messageBusOptional.get();

      final EasyMap arguments = new EasyMap(getMapArguments());

      // 发出请求
      final Object reqObj = arguments.get(ArgNames.REQUEST);
      final boolean ignoreResult = !isAwait();

      final ServiceRequestMessage serviceRequestMessage = new ServiceRequestMessage(
          CachedTimestamp.nowMilliseconds(),
          activityThread,
          integrationPlan.getActivityRunner().getServiceName(),
          serviceMeta.getName(),
          reqObj,
          ignoreResult
      );

      try {
        messageBus.write(serviceRequestMessage);
      } catch (MessageBusNotAvailableException e) {
        throw new ActionRuntimeException(
            CommonRuntimeErrorCodes.BUS_UNAVAILABLE, e.getMessage());
      }

      // 如果有超时参数，注册时间事件
      if (arguments.contains(ArgNames.TIMEOUT)) {
        final long timestamp = arguments.getLong(ArgNames.TIMEOUT);
        clock.registerClockMessage(ClockMessage.buildForActivity(
            CachedTimestamp.nowMilliseconds() + timestamp,
            activity.getId(),
            activityThread.getDomainId(),
            this.name
        ));
      }
    }

    @Override
    public Result handleResponse(Message message) {
      // 收到超时消息
      if (message instanceof ClockMessage) {
        final ClockMessage clockMessage = (ClockMessage) message;
        if (this.name.equals(clockMessage.getAction())) {
          return Result.timeout();
        }
      } else if (message instanceof ServiceResponseMessage) {
        final ServiceResponseMessage responseMessage = (ServiceResponseMessage) message;
        if (this.name.equals(responseMessage.getAction())) {
          return ((ServiceResponseMessage) message).toResult();
        }
      }
      return Result.ignore();
    }

    // 参数名称
    interface ArgNames {

      String REQUEST = "request";

      String TIMEOUT = "timeout";
    }
  }
}
