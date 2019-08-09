package playwell.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.IntergrationUtils;
import playwell.integration.ServiceRunnerIntegrationPlan;
import playwell.message.MessageDispatcher;
import playwell.message.MessageDispatcherListener;
import playwell.message.ServiceRequestMessage;
import playwell.message.ServiceResponseMessage;
import playwell.message.bus.MessageAckType;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.util.PerfLog;
import playwell.util.Sleeper;

/**
 * ServiceRunner实现了MessageDispatcher，提供了一个通用的服务运行时 ServiceRunner会从MessageBus源源不断地批量获取ServiceRequestMessage
 * 然后路由到进程所持有的LocalService之中，并分别对各个LocalService进行回调 最后再通过MessageBus将ServiceResponseMessage反馈给服务调用者。
 *
 * @author chihongze@gmail.com
 */
public class ServiceRunner implements MessageDispatcher {

  private static final Logger logger = LogManager.getLogger(ServiceRunner.class);

  // MessageDispatcherListeners
  private final Collection<MessageDispatcherListener> listeners = new LinkedList<>();

  private String inputMessageBusName;

  private long sleepTime;

  private int maxFetchNum;

  private volatile boolean started = false;

  private volatile ServiceRunnerStatus expectedStatus = ServiceRunnerStatus.INIT;

  private volatile ServiceRunnerStatus actualStatus = ServiceRunnerStatus.INIT;

  private volatile long lastActive = 0L;

  private MessageAckType messageAckType;

  private Class<? extends Exception> lastExceptionType = null;

  private long lastOutputExceptionTime = 0L;

  public ServiceRunner() {

  }

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    this.inputMessageBusName = configuration.getString(ConfigItems.INPUT_MESSAGE_BUS);
    this.sleepTime = configuration.getLong(
        ConfigItems.SLEEP_TIME, ConfigItems.DEFAULT_SLEEP_TIME);
    this.maxFetchNum = configuration.getInt(
        ConfigItems.MAX_FETCH_NUM, ConfigItems.DEFAULT_MAX_FETCH_NUM);

    Optional<MessageAckType> messageAckTypeOptional = MessageAckType.valueOfByType(
        configuration
            .getString(ConfigItems.MESSAGE_ACK_TYPE, ConfigItems.DEFAULT_MESSAGE_ACK_TYPE));
    if (!messageAckTypeOptional.isPresent()) {
      throw new RuntimeException(String.format(
          "Unknown message ack type: %s", configuration.getString(ConfigItems.MESSAGE_ACK_TYPE)));
    }
    this.messageAckType = messageAckTypeOptional.get();

    // 加载MessageDispatcherListener
    IntergrationUtils
        .loadAndInitSubComponents(configuration.getObjectList(ConfigItems.LISTENERS))
        .forEach(listenerObj -> listeners.add((MessageDispatcherListener) listenerObj));
  }

  @Override
  public void dispatch() {
    final ServiceRunnerIntegrationPlan intergrationPlan = IntegrationPlanFactory.currentPlan();
    final ServiceMetaManager serviceManager = intergrationPlan.getServiceMetaManager();
    final MessageBusManager messageBusManager = intergrationPlan.getMessageBusManager();

    final Optional<MessageBus> inputMessageBusOptional = messageBusManager.getMessageBusByName(
        inputMessageBusName);
    if (!inputMessageBusOptional.isPresent()) {
      throw new RuntimeException(
          "Input message bus for service runner not found: " + inputMessageBusName);
    }
    final MessageBus inputMessageBus = inputMessageBusOptional.get();

    this.started = true;
    this.actualStatus = ServiceRunnerStatus.RUNNING;

    while (true) {
      if (this.expectedStatus == ServiceRunnerStatus.STOPPED) {
        this.actualStatus = ServiceRunnerStatus.STOPPED;
        logger.info("The ServiceRunner stopped.");
        return;
      }

      if (this.expectedStatus == ServiceRunnerStatus.PAUSED) {
        logger.info("ServiceRunner paused.");
        this.actualStatus = ServiceRunnerStatus.PAUSED;
        Sleeper.sleepInSeconds(3);
        continue;
      } else {
        this.actualStatus = ServiceRunnerStatus.RUNNING;
      }

      try {
        PerfLog.beginSpan("loop");
        this.lastActive = System.currentTimeMillis();

        PerfLog.beginSpan("before_loop_listeners");
        this.callbackListeners(MessageDispatcherListener::beforeLoop);
        PerfLog.endSpan();  // end span for before loop listeners

        // 从总线中读取消息
        PerfLog.beginSpan("read_messages");
        final Map<String, Collection<ServiceRequestMessage>> messages = new HashMap<>();
        inputMessageBus.readWithConsumer(this.maxFetchNum, message -> {
          if (!(message instanceof ServiceRequestMessage)) {
            throw new RuntimeException(
                "The ServiceRunner only accept ServiceRequestMessage, invalid message type: "
                    + message.getClass().getCanonicalName());
          }

          final ServiceRequestMessage serviceRequestMessage = (ServiceRequestMessage) message;
          messages.computeIfAbsent(serviceRequestMessage.getReceiver(), k -> new LinkedList<>())
              .add(serviceRequestMessage);
        });
        PerfLog.endSpan();

        if (MapUtils.isEmpty(messages)) {
          continue;
        }

        if (messageAckType == MessageAckType.AFTER_READ) {
          PerfLog.beginSpan("ack_messages");
          inputMessageBus.ackMessages();
          PerfLog.endSpan();
        }

        PerfLog.beginSpan("handle_messages");
        messages.keySet().parallelStream().forEach(serviceName -> {
          final Optional<ServiceMeta> serviceMetaOptional = serviceManager
              .getServiceMetaByName(serviceName);
          if (!serviceMetaOptional.isPresent()) {
            logger.error(
                String.format("Could not found service: %s", serviceName));
            return;
          }

          final ServiceMeta serviceMeta = serviceMetaOptional.get();
          if (!(serviceMeta instanceof LocalServiceMeta)) {
            logger
                .error(String.format("The service %s is not local service", serviceMeta.getName()));
            return;
          }

          PerfLog.beginSpan("handle_message_by_service");
          final LocalServiceMeta localServiceMeta = (LocalServiceMeta) serviceMeta;
          final Collection<ServiceResponseMessage> serviceResponseMessages = localServiceMeta
              .getPlaywellService()
              .handle(messages.get(serviceName));
          PerfLog.endSpan("Service: " + serviceName);  // end span for handle message by service

          if (CollectionUtils.isNotEmpty(serviceResponseMessages)) {
            PerfLog.beginSpan("send_response");
            serviceResponseMessages.forEach(responseMessage -> {
              try {
                final Optional<ServiceMeta> receiverServiceMetaOptional = serviceManager
                    .getServiceMetaByName(responseMessage.getReceiver());

                if (!receiverServiceMetaOptional.isPresent()) {
                  logger.error(String.format("Could not found the receiver service: %s",
                      responseMessage.getReceiver()));
                  return;
                }
                final ServiceMeta receiverServiceMeta = receiverServiceMetaOptional.get();

                final Optional<MessageBus> messageBusOptional = messageBusManager
                    .getMessageBusByName(receiverServiceMeta.getMessageBus());
                if (!messageBusOptional.isPresent()) {
                  logger.error(String.format("Could not found the receiver service message bus: %s",
                      receiverServiceMeta.getMessageBus()));
                  return;
                }
                final MessageBus messageBus = messageBusOptional.get();

                messageBus.write(responseMessage);
              } catch (Exception e) {
                logger.error("Error happened when handle service postResponse", e);
              }
            });
            PerfLog.endSpan();  // end span for send response
          }
        });
        PerfLog.endSpan();  // end span for handle messages

        if (messageAckType == MessageAckType.AFTER_HANDLE) {
          PerfLog.beginSpan("ack_messages");
          inputMessageBus.ackMessages();
          PerfLog.endSpan();
        }

        PerfLog.beginSpan("after_loop_listeners");
        this.callbackListeners(MessageDispatcherListener::afterLoop);
        PerfLog.endSpan();  // end span for after loop listeners
      } catch (Exception e) {
        logException(e);
        this.callbackListeners(listener -> listener.errorHappened(e));
      } finally {
        PerfLog.endRootSpan();  // end span for loop
        PerfLog.outputPerfLog();
        PerfLog.clear();
        sleep();
      }
    }
  }

  private void sleep() {
    if (this.sleepTime > 0) {
      try {
        TimeUnit.MILLISECONDS.sleep(this.sleepTime);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public boolean isStarted() {
    return this.started;
  }

  @Override
  public void stop() {
    this.expectedStatus = ServiceRunnerStatus.STOPPED;
  }

  @Override
  public boolean isStopped() {
    return this.actualStatus == ServiceRunnerStatus.STOPPED;
  }

  @Override
  public void pause() {
    this.expectedStatus = ServiceRunnerStatus.PAUSED;
  }

  @Override
  public void rerun() {
    this.expectedStatus = ServiceRunnerStatus.RUNNING;
  }

  @Override
  public boolean isPaused() {
    return this.actualStatus == ServiceRunnerStatus.PAUSED;
  }

  public ServiceRunnerStatus getStatus() {
    return this.actualStatus;
  }

  public long getLastActive() {
    return lastActive;
  }

  private void callbackListeners(Consumer<MessageDispatcherListener> callback) {
    if (CollectionUtils.isEmpty(listeners)) {
      return;
    }

    listeners.parallelStream().forEach(listener -> {
      try {
        callback.accept(listener);
      } catch (Exception e) {
        logger.error("Callback MessageDispatcherListener error!", e);
      }
    });
  }

  private void logException(Exception e) {
    final Class<? extends Exception> exceptionType = e.getClass();
    if (e.getClass().equals(this.lastExceptionType)) {
      if (System.currentTimeMillis() - this.lastOutputExceptionTime >= 5000) {
        logger.error("Error happened when dispatch messages", e);
        this.lastOutputExceptionTime = System.currentTimeMillis();
      }
    } else {
      logger.error("Error happened when dispatch messages", e);
      this.lastOutputExceptionTime = System.currentTimeMillis();
    }
    this.lastExceptionType = exceptionType;
  }

  public interface ConfigItems {

    String INPUT_MESSAGE_BUS = "input_message_bus";

    String SLEEP_TIME = "sleep_time";
    long DEFAULT_SLEEP_TIME = 100;

    String MAX_FETCH_NUM = "max_fetch_num";
    int DEFAULT_MAX_FETCH_NUM = 1000;

    String LISTENERS = "listeners";

    String MESSAGE_ACK_TYPE = "message_ack_type";
    String DEFAULT_MESSAGE_ACK_TYPE = MessageAckType.AFTER_READ.getType();
  }
}
