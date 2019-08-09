package playwell.route;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.IntergrationUtils;
import playwell.integration.MessageRouteIntegrationPlan;
import playwell.message.MessageDispatcher;
import playwell.message.MessageDispatcherListener;
import playwell.message.RoutedMessage;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;
import playwell.message.domainid.MessageDomainIDStrategy;
import playwell.message.domainid.MessageDomainIDStrategyManager;
import playwell.service.ServiceMeta;
import playwell.service.ServiceMetaManager;
import playwell.util.PerfLog;
import playwell.util.Sleeper;

/**
 * 消息路由：接收消息，计算DomainID及其Hash，根据Hash所属的slots，转发给对应的服务。 消息路由是无状态的，可以扩展任意多个
 */
public class MessageRoute implements MessageDispatcher {

  private static final Logger logger = LogManager.getLogger(MessageRoute.class);

  private String inputMessageBusName;

  private long sleepTime;

  private int maxFetchNum;

  private Collection<MessageDispatcherListener> listeners;

  private volatile boolean started = false;

  private volatile MessageRouteStatus expectedStatus = MessageRouteStatus.INIT;

  private volatile MessageRouteStatus actualStatus = MessageRouteStatus.INIT;

  private Class<? extends Exception> lastExceptionType = null;

  private long lastOutputExceptionTime = 0L;

  public MessageRoute() {
    this.listeners = new LinkedList<>();
  }

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    this.inputMessageBusName = configuration.getString(ConfigItems.INPUT_MESSAGE_BUS);
    this.sleepTime = configuration.getLong(ConfigItems.SLEEP_TIME, ConfigItems.DEFAULT_SLEEP_TIME);
    this.maxFetchNum = configuration
        .getInt(ConfigItems.MAX_FETCH_NUM, ConfigItems.DEFAULT_MAX_FETCH_NUM);

    // 加载MessageDispatcherListener
    IntergrationUtils.loadAndInitSubComponents(configuration.getObjectList(ConfigItems.LISTENERS))
        .forEach(listenerObj -> listeners.add((MessageDispatcherListener) listenerObj));
  }

  @Override
  public void dispatch() {
    final MessageRouteIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final MessageBusManager messageBusManager = integrationPlan.getMessageBusManager();
    final ServiceMetaManager serviceMetaManager = integrationPlan.getServiceMetaManager();
    final MessageDomainIDStrategyManager messageDomainIDStrategyManager = integrationPlan
        .getMessageDomainIDStrategyManager();
    final SlotsManager slotsManager = integrationPlan.getSlotsManager();

    this.started = true;
    this.actualStatus = MessageRouteStatus.RUNNING;

    while (true) {

      if (this.expectedStatus == MessageRouteStatus.STOPPED) {
        logger.info("MessageRoute stopped.");
        this.actualStatus = MessageRouteStatus.STOPPED;
        break;
      }

      if (this.expectedStatus == MessageRouteStatus.PAUSED) {
        this.actualStatus = MessageRouteStatus.PAUSED;
        logger.info("MessageRoute paused.");
        Sleeper.sleepInSeconds(3);
        continue;
      } else {
        this.actualStatus = MessageRouteStatus.RUNNING;
      }

      try {
        PerfLog.beginSpan("loop");

        PerfLog.beginSpan("before_loop_listeners");
        this.callbackListeners(MessageDispatcherListener::beforeLoop);
        PerfLog.endSpan();  // end before loop listeners

        final Optional<MessageBus> messageBusOptional = messageBusManager
            .getMessageBusByName(inputMessageBusName);
        if (!messageBusOptional.isPresent()) {
          throw new RuntimeException(
              "Could not found the input message bus: " + inputMessageBusName);
        }
        final MessageBus messageBus = messageBusOptional.get();

        PerfLog.beginSpan("get_domain_id_strategies");
        final Collection<MessageDomainIDStrategy> messageDomainIDStrategies = messageDomainIDStrategyManager
            .getAllMessageDomainIDStrategies();
        PerfLog.endSpan();  // end span for get domain id strategies

        PerfLog.beginSpan("get_runner_message_bus");
        Collection<String> allActivityRunnerServices = slotsManager.getAllServices();
        Map<String, MessageBus> allActivityRunnerMessageBus = new HashMap<>(
            allActivityRunnerServices.size());
        allActivityRunnerServices.forEach(serviceName -> {
          Optional<ServiceMeta> serviceMetaOptional = serviceMetaManager
              .getServiceMetaByName(serviceName);
          ServiceMeta serviceMeta = serviceMetaOptional.orElseThrow(
              () -> new RuntimeException(
                  String.format("Unknown ActivityRunner service: %s", serviceName)));
          Optional<MessageBus> runnerMsgBusOptional = messageBusManager
              .getMessageBusByName(serviceMeta.getMessageBus());
          MessageBus runnerMessageBus = runnerMsgBusOptional.orElseThrow(
              () -> new RuntimeException(String.format("Unknown MessageBus %s of the service: %s",
                  serviceMeta.getMessageBus(), serviceMeta.getName())));
          allActivityRunnerMessageBus.put(serviceName, runnerMessageBus);
        });
        PerfLog.endSpan();  // end span for get runner message bus

        PerfLog.beginSpan("redirect_messages");
        messageBus.readWithConsumer(maxFetchNum, message ->
            messageDomainIDStrategies.forEach(strategy -> {
              Optional<String> domainIdOptional = strategy.domainId(message);
              domainIdOptional.ifPresent(domainId -> {
                String serviceName = slotsManager.getServiceByKey(domainId);
                MessageBus runnerMessageBus = allActivityRunnerMessageBus.get(serviceName);
                try {
                  runnerMessageBus.write(new RoutedMessage(strategy.name(), domainId, message));
                } catch (MessageBusNotAvailableException e) {
                  throw new RuntimeException(e);
                }
              });
            }));
        PerfLog.endSpan();  // end span for redirect messages

        PerfLog.beginSpan("ack_messages");
        messageBus.ackMessages();
        PerfLog.endSpan();  // end span for ack messages

        PerfLog.beginSpan("after_loop_listeners");
        callbackListeners(MessageDispatcherListener::afterLoop);
        PerfLog.endSpan();
      } catch (Exception e) {
        logException(e);
        callbackListeners(listeners -> listeners.errorHappened(e));
      } finally {
        PerfLog.endRootSpan();  // end span for loop
        PerfLog.outputPerfLog();
        PerfLog.clear();
        sleep();
      }
    }
  }

  @Override
  public boolean isStarted() {
    return started;
  }

  @Override
  public void stop() {
    this.expectedStatus = MessageRouteStatus.STOPPED;
    logger.info("MessageRoute received the stop signal.");
  }

  @Override
  public boolean isStopped() {
    return this.actualStatus == MessageRouteStatus.STOPPED;
  }

  @Override
  public void pause() {
    this.expectedStatus = MessageRouteStatus.PAUSED;
    logger.info("MessageRoute received the pause signal");
  }

  @Override
  public void rerun() {
    this.expectedStatus = MessageRouteStatus.RUNNING;
    logger.info("MessageRoute received the rerun signal");
  }

  @Override
  public boolean isPaused() {
    return this.actualStatus == MessageRouteStatus.PAUSED;
  }

  public MessageRouteStatus getStatus() {
    return this.actualStatus;
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

  private void sleep() {
    if (this.sleepTime > 0) {
      try {
        TimeUnit.MILLISECONDS.sleep(this.sleepTime);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  // MessageRoute配置项
  interface ConfigItems {

    String INPUT_MESSAGE_BUS = "input_message_bus";

    String SLEEP_TIME = "sleep_time";
    long DEFAULT_SLEEP_TIME = 100;

    String MAX_FETCH_NUM = "max_fetch_num";
    int DEFAULT_MAX_FETCH_NUM = 5000;

    String LISTENERS = "listeners";
  }
}
