package playwell.clock;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import playwell.integration.ClockRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.IntergrationUtils;
import playwell.message.Message;
import playwell.message.MessageDispatcher;
import playwell.message.MessageDispatcherListener;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;
import playwell.service.ServiceMeta;
import playwell.service.ServiceMetaManager;
import playwell.util.PerfLog;
import playwell.util.Sleeper;


/**
 * ClockRunner不断地从MessageBus中获取远程服务发送来的ClockMessage， 并将ClockMessage注册到本地的Clock组件中，当相应的时间点到达时，
 * 再将ClockMessage转发给对应的服务进行通知。
 */
public class ClockRunner implements MessageDispatcher {

  private static final Logger logger = LogManager.getLogger(ClockRunner.class);

  // Listeners
  private final Collection<MessageDispatcherListener> listeners = new LinkedList<>();

  // 服务名称
  private String serviceName;

  // 该ClockRunner使用的MessageBus
  private String inputMessageBusName;

  // 每次循环的休眠时间
  private long sleepTime;

  // 最大fetch消息数目
  private int maxFetchNum;

  // 停止接受事件注册
  private volatile boolean accept = false;

  // ClockRunner是否已经开始运行
  private volatile boolean started = false;

  // Expected ClockRunner status
  private volatile ClockRunnerStatus expectedStatus = ClockRunnerStatus.INIT;

  // Actual ClockRunner status
  private volatile ClockRunnerStatus actualStatus = ClockRunnerStatus.INIT;

  private Class<? extends Exception> lastExceptionType = null;

  private long lastOutputExceptionTime = 0L;

  private volatile long lastActive = 0L;

  public ClockRunner() {

  }

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    this.serviceName = configuration.getString(ConfigItems.SERVICE_NAME);
    this.inputMessageBusName = configuration.getString(ConfigItems.INPUT_MESSAGE_BUS);
    this.sleepTime = configuration.getLong(ConfigItems.SLEEP_TIME, ConfigItems.DEFAULT_SLEEP_TIME);
    this.maxFetchNum = configuration
        .getInt(ConfigItems.MAX_FETCH_NUM, ConfigItems.DEFAULT_MAX_FETCH_NUM);
    this.accept = configuration.getBoolean(ConfigItems.ACCEPT, ConfigItems.DEFAULT_ACCEPT);
    // 加载MessageDispatcherListener
    IntergrationUtils.loadAndInitSubComponents(configuration.getObjectList(ConfigItems.LISTENERS))
        .forEach(listenerObj -> listeners.add((MessageDispatcherListener) listenerObj));
    final ClockRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final ServiceMetaManager serviceMetaManager = integrationPlan.getServiceMetaManager();
    serviceMetaManager.registerServiceMeta(new ServiceMeta(
        serviceName,
        inputMessageBusName,
        Collections.emptyMap()
    ));
  }

  @Override
  public void dispatch() {
    final ClockRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final ServiceMetaManager serviceMetaManager = integrationPlan.getServiceMetaManager();
    final MessageBusManager messageBusManager = integrationPlan.getMessageBusManager();
    final Clock clock = integrationPlan.getClock();

    this.started = true;
    this.actualStatus = ClockRunnerStatus.RUNNING;

    logger.info(String.format("The clock runner %s started", serviceName));

    while (true) {
      this.lastActive = System.currentTimeMillis();

      if (expectedStatus == ClockRunnerStatus.STOPPED) {
        this.actualStatus = ClockRunnerStatus.STOPPED;
        logger.info("The clock runner stopped.");
        break;
      }

      if (expectedStatus == ClockRunnerStatus.PAUSED) {
        this.actualStatus = ClockRunnerStatus.PAUSED;
        logger.info("The clock runner paused.");
        Sleeper.sleepInSeconds(3);
        continue;
      } else {
        this.actualStatus = ClockRunnerStatus.RUNNING;
      }

      try {
        PerfLog.beginSpan("loop");

        // Callback before loop
        PerfLog.beginSpan("before_loop_listeners");
        this.callbackListeners(MessageDispatcherListener::beforeLoop);
        PerfLog.endSpan();  // end span for before loop listeners

        final MessageBus inputMessageBus = this.getInputMessageBus();

        // 从input message bus获取clock message，然后注册到clock中
        if (this.accept) {
          PerfLog.beginSpan("read_messages");
          inputMessageBus.readWithConsumer(maxFetchNum, message -> {
            if (ClockMessage.TYPE.equals(message.getType())) {
              clock.registerClockMessage((ClockMessage) message);
            } else {
              logger.error(String.format(
                  "Received invalid message, the clock runner required ClockMessage. Message details: %s",
                  message
              ));
            }
          });
          PerfLog.endSpan();  // end span for read messages

          PerfLog.beginSpan("ack_messages");
          inputMessageBus.ackMessages();
          PerfLog.endSpan();  // end span for ack messages
        }

        final long now = System.currentTimeMillis();

        // 从clock中读取到点的ClockMessage
        PerfLog.beginSpan("fetch_messages");
        final Map<String, List<Message>> messagesByBus = new HashMap<>();
        clock.consumeClockMessage(now, clockMessage -> {

          final Optional<ServiceMeta> serviceMetaOptional = serviceMetaManager
              .getServiceMetaByName(clockMessage.getSender());
          if (!serviceMetaOptional.isPresent()) {
            throw new RuntimeException(String.format(
                "Unknown clock message sender service: %s", clockMessage.getSender()));
          }
          final ServiceMeta serviceMeta = serviceMetaOptional.get();

          messagesByBus.computeIfAbsent(
              serviceMeta.getMessageBus(), k -> new LinkedList<>()).add(clockMessage);
        });
        PerfLog.endSpan();

        if (MapUtils.isNotEmpty(messagesByBus)) {
          PerfLog.beginSpan("send_messages");
          batchSend(messageBusManager, messagesByBus);
          PerfLog.endSpan();  // end span for send messages
        }

        PerfLog.beginSpan("clean");
        clock.clean(now);
        PerfLog.endSpan();  // end span for clean

        PerfLog.beginSpan("after_loop_listeners");
        callbackListeners(MessageDispatcherListener::afterLoop);
        PerfLog.endSpan();  // end span for after loop listeners
      } catch (Exception e) {
        logException(e);
        callbackListeners(listener -> listener.errorHappened(e));
      } finally {
        PerfLog.endRootSpan();  // end span for loop
        PerfLog.outputPerfLog();
        PerfLog.clear();
        sleep();
      }
    }
  }

  private void batchSend(
      MessageBusManager messageBusManager, Map<String, List<Message>> messagesByBus) {
    messagesByBus.entrySet().parallelStream().forEach(entry -> {
      final String busName = entry.getKey();
      final List<Message> clockMessages = entry.getValue();
      final Optional<MessageBus> messageBusOptional = messageBusManager
          .getMessageBusByName(busName);
      if (!messageBusOptional.isPresent()) {
        throw new RuntimeException(String.format(
            "Unknown message bus %s",
            busName
        ));
      }
      final MessageBus messageBus = messageBusOptional.get();
      try {
        PerfLog.beginSpan("send_messages_with_bus");
        messageBus.write(clockMessages);
        PerfLog.endSpan("Message bus: " + busName);  // end span for send messages with bus
      } catch (MessageBusNotAvailableException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public boolean isStarted() {
    return this.started;
  }

  @Override
  public void stop() {
    logger.info("The clock runner received the stop signal");
    this.expectedStatus = ClockRunnerStatus.STOPPED;
  }

  @Override
  public boolean isStopped() {
    return this.actualStatus == ClockRunnerStatus.STOPPED;
  }

  @Override
  public void pause() {
    logger.info("The clock runner received the paused signal");
    this.expectedStatus = ClockRunnerStatus.PAUSED;
  }

  @Override
  public void rerun() {
    this.expectedStatus = ClockRunnerStatus.RUNNING;
  }

  @Override
  public boolean isPaused() {
    return this.actualStatus == ClockRunnerStatus.PAUSED;
  }

  public ClockRunnerStatus getActualStatus() {
    return actualStatus;
  }

  public void setAccept(boolean accept) {
    this.accept = accept;
  }

  public long getLastActive() {
    return lastActive;
  }

  private MessageBus getInputMessageBus() {
    final ClockRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final MessageBusManager messageBusManager = integrationPlan.getMessageBusManager();
    final Optional<MessageBus> inputMessageBusOptional = messageBusManager
        .getMessageBusByName(inputMessageBusName);
    if (!inputMessageBusOptional.isPresent()) {
      throw new RuntimeException(String.format(
          "Unknown clock runner input message bus: %s", inputMessageBusName));
    }
    return inputMessageBusOptional.get();
  }

  private void sleep() {
    if (this.sleepTime > 0) {
      Sleeper.sleep(sleepTime);
    }
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

  interface ConfigItems {

    String SERVICE_NAME = "service";

    String INPUT_MESSAGE_BUS = "input_message_bus";

    String SLEEP_TIME = "sleep_time";

    int DEFAULT_SLEEP_TIME = 100;

    String MAX_FETCH_NUM = "max_fetch_num";

    int DEFAULT_MAX_FETCH_NUM = 5000;

    String LISTENERS = "listeners";

    String ACCEPT = "accept";

    boolean DEFAULT_ACCEPT = true;
  }
}
