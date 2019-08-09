package playwell.clock;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import playwell.integration.ClockReplicationRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.IntergrationUtils;
import playwell.message.Message;
import playwell.message.MessageDispatcher;
import playwell.message.MessageDispatcherListener;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.util.PerfLog;
import playwell.util.Sleeper;

/**
 * ClockReplicationRunner
 */
public class ClockReplicationRunner implements MessageDispatcher {

  private static final Logger logger = LogManager.getLogger(ClockReplicationRunner.class);

  // Listeners
  private final Collection<MessageDispatcherListener> listeners = new LinkedList<>();

  // Main input message bus
  private String inputMessageBusName;

  // 每次循环的休眠时间
  private long sleepTime;

  // 最大fetch消息数目
  private int maxFetchNum;

  // Expected status
  private volatile ClockReplicationRunnerStatus expectedStatus = ClockReplicationRunnerStatus.INIT;

  // Actual status
  private volatile ClockReplicationRunnerStatus actualStatus = ClockReplicationRunnerStatus.INIT;

  private Class<? extends Exception> lastExceptionType = null;

  private long lastOutputExceptionTime = 0L;

  private volatile long lastActive = 0L;

  private volatile boolean started = false;

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    this.inputMessageBusName = configuration.getString(ConfigItems.INPUT_MESSAGE_BUS);
    this.sleepTime = configuration.getLong(
        ConfigItems.SLEEP_TIME, ConfigItems.DEFAULT_SLEEP_TIME);
    this.maxFetchNum = configuration.getInt(
        ConfigItems.MAX_FETCH_NUM, ConfigItems.DEFAULT_MAX_FETCH_NUM);
    // 加载MessageDispatcherListener
    IntergrationUtils
        .loadAndInitSubComponents(configuration.getObjectList(ConfigItems.LISTENERS))
        .forEach(listenerObj -> listeners.add((MessageDispatcherListener) listenerObj));
  }

  @Override
  public void dispatch() {
    final ClockReplicationRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory
        .currentPlan();
    final Clock clock = integrationPlan.getClock();

    this.started = true;
    while (true) {
      this.lastActive = System.currentTimeMillis();

      if (expectedStatus == ClockReplicationRunnerStatus.STOPPED) {
        this.actualStatus = ClockReplicationRunnerStatus.STOPPED;
        logger.info("The ClockReplicationRunner stopped.");
        break;
      }

      if (expectedStatus == ClockReplicationRunnerStatus.PAUSED) {
        this.actualStatus = ClockReplicationRunnerStatus.PAUSED;
        logger.info("The ClockReplicationRunner paused.");
        Sleeper.sleepInSeconds(3);
        continue;
      } else {
        this.actualStatus = ClockReplicationRunnerStatus.RUNNING;
      }

      try {
        PerfLog.beginSpan("loop");

        // Callback before loop
        PerfLog.beginSpan("before_loop_listeners");
        this.callbackListeners(MessageDispatcherListener::beforeLoop);
        PerfLog.endSpan();

        PerfLog.beginSpan("read_messages");
        final MessageBus inputMessageBus = getInputMessageBus();
        final Collection<Message> messages = inputMessageBus.read(maxFetchNum);
        if (CollectionUtils.isEmpty(messages)) {
          continue;
        }
        PerfLog.endSpan();  // end span for read messages

        PerfLog.beginSpan("apply_clock_messages");
        clock.applyReplicationMessages(messages);
        PerfLog.endSpan();

        // Callback after loop
        PerfLog.beginSpan("after_loop_listeners");
        callbackListeners(MessageDispatcherListener::afterLoop);
        PerfLog.endSpan();  // end span for after loop listeners
      } catch (Exception e) {
        logException(e);
      } finally {
        PerfLog.endRootSpan();  // end span for loop
        PerfLog.outputPerfLog();
        PerfLog.clear();
        Sleeper.sleep(this.sleepTime);
      }
    }
  }

  @Override
  public boolean isStarted() {
    return this.started;
  }

  @Override
  public void stop() {
    logger.info("The ClockReplicationRunner received stop signal");
    this.expectedStatus = ClockReplicationRunnerStatus.STOPPED;
  }

  @Override
  public boolean isStopped() {
    return this.actualStatus == ClockReplicationRunnerStatus.STOPPED;
  }

  @Override
  public void pause() {
    logger.info("The ClockReplicationRunner received pause signal");
    this.expectedStatus = ClockReplicationRunnerStatus.PAUSED;
  }

  @Override
  public void rerun() {
    logger.info("The ClockReplicationRunner received rerun signal");
    this.expectedStatus = ClockReplicationRunnerStatus.RUNNING;
  }

  @Override
  public boolean isPaused() {
    return this.actualStatus == ClockReplicationRunnerStatus.PAUSED;
  }

  public ClockReplicationRunnerStatus getActualStatus() {
    return actualStatus;
  }

  public long getLastActive() {
    return lastActive;
  }

  private MessageBus getInputMessageBus() {
    final ClockReplicationRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory
        .currentPlan();
    final MessageBusManager messageBusManager = integrationPlan.getMessageBusManager();
    final Optional<MessageBus> inputMessageBusOptional = messageBusManager
        .getMessageBusByName(inputMessageBusName);
    if (!inputMessageBusOptional.isPresent()) {
      throw new RuntimeException(String.format(
          "Unknown ClockReplicationRunner input message bus: %s", inputMessageBusName));
    }
    return inputMessageBusOptional.get();
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

  interface ConfigItems {

    String INPUT_MESSAGE_BUS = "input_message_bus";

    String SLEEP_TIME = "sleep_time";

    int DEFAULT_SLEEP_TIME = 100;

    String MAX_FETCH_NUM = "max_fetch_num";

    int DEFAULT_MAX_FETCH_NUM = 10000;

    String LISTENERS = "listeners";
  }
}
