package playwell.activity;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.activity.thread.ActivityThreadPool;
import playwell.activity.thread.message.MigrateActivityThreadMessage;
import playwell.activity.thread.message.RemoveActivityThreadMessage;
import playwell.clock.CleanTimeRangeMessage;
import playwell.clock.Clock;
import playwell.clock.ClockMessage;
import playwell.common.EasyMap;
import playwell.integration.ActivityReplicationRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.IntergrationUtils;
import playwell.integration.TopComponentType;
import playwell.message.Message;
import playwell.message.MessageDispatcher;
import playwell.message.MessageDispatcherListener;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.util.PerfLog;
import playwell.util.Sleeper;

/**
 * ActivityReplicationRunner
 */
public class ActivityReplicationRunner implements MessageDispatcher {

  private static final Logger logger = LogManager.getLogger(ActivityReplicationRunner.class);

  // Listeners
  private final Collection<MessageDispatcherListener> listeners = new LinkedList<>();

  // Main input message bus
  private String inputMessageBusName;

  // 每次循环的休眠时间
  private long sleepTime;

  // 最大fetch消息数目
  private int maxFetchNum;

  // Expected status
  private volatile ActivityReplicationRunnerStatus expectedStatus = ActivityReplicationRunnerStatus.INIT;

  // Actual status
  private volatile ActivityReplicationRunnerStatus actualStatus = ActivityReplicationRunnerStatus.INIT;

  private Class<? extends Exception> lastExceptionType = null;

  private long lastOutputExceptionTime = 0L;

  private volatile long lastActive = 0L;

  private volatile boolean started = false;

  public ActivityReplicationRunner() {

  }

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    this.inputMessageBusName = configuration.getString(ConfigItems.INPUT_MESSAGE_BUS);
    this.sleepTime = configuration.getLong(
        ConfigItems.SLEEP_TIME, ConfigItems.DEFAULT_SLEEP_TIME);
    this.maxFetchNum = configuration.getInt(
        ConfigItems.MAX_FETCH_NUM, ConfigItems.DEFAULT_MAX_FETCH_NUM);
    // 加载MessageDispatcherListener
    IntergrationUtils.loadAndInitSubComponents(configuration.getObjectList(ConfigItems.LISTENERS))
        .forEach(listenerObj -> listeners.add((MessageDispatcherListener) listenerObj));
  }

  @Override
  public void dispatch() {
    final ActivityReplicationRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory
        .currentPlan();
    final ActivityThreadPool activityThreadPool = integrationPlan.getActivityThreadPool();
    final Clock clock;
    if (integrationPlan.contains(TopComponentType.CLOCK)) {
      clock = integrationPlan.getClock();
    } else {
      clock = null;
    }

    this.started = true;
    while (true) {
      this.lastActive = System.currentTimeMillis();

      if (expectedStatus == ActivityReplicationRunnerStatus.STOPPED) {
        this.actualStatus = ActivityReplicationRunnerStatus.STOPPED;
        logger.info("The ActivityReplicationRunner stopped.");
        break;
      }

      if (expectedStatus == ActivityReplicationRunnerStatus.PAUSED) {
        this.actualStatus = ActivityReplicationRunnerStatus.PAUSED;
        logger.info("The ActivityReplicationRunner paused.");
        Sleeper.sleepInSeconds(3);
        continue;
      } else {
        this.actualStatus = ActivityReplicationRunnerStatus.RUNNING;
      }

      try {
        PerfLog.beginSpan("loop");

        // Callback before loop
        PerfLog.beginSpan("before_loop_listeners");
        this.callbackListeners(MessageDispatcherListener::beforeLoop);
        PerfLog.endSpan();  // end span for before_loop_listeners

        PerfLog.beginSpan("read_messages");
        final MessageBus inputMessageBus = getInputMessageBus();
        final List<Message> activityMessages = new LinkedList<>();
        final List<Message> clockMessages = new LinkedList<>();
        final int readCount = inputMessageBus.readWithConsumer(maxFetchNum, message -> {
          if (isActivityThreadMsg(message)) {
            activityMessages.add(message);
          } else if (isClockMsg(message)) {
            clockMessages.add(message);
          } else {
            logger.error(
                "The ActivityReplicationRunner could not handle the message type: " + message
                    .getType());
          }
        });
        PerfLog.endSpan(
            String.format("Read messages count: %d", readCount)); // end span for read messages

        if (CollectionUtils.isNotEmpty(activityMessages)) {
          PerfLog.beginSpan("apply_activity_thread_messages");
          activityThreadPool.applyReplicationMessages(activityMessages);
          PerfLog.endSpan();  // end span for apply activity thread messages
        }

        if (clock != null && CollectionUtils.isNotEmpty(clockMessages)) {
          PerfLog.beginSpan("apply_clock_messages");
          clock.applyReplicationMessages(clockMessages);
          PerfLog.endSpan();  // end span for apply clock messages
        }

        // Callback after loop
        PerfLog.beginSpan("after_loop_listeners");
        callbackListeners(MessageDispatcherListener::afterLoop);
        PerfLog.endSpan();  // end span for after loop listeners
      } catch (Exception e) {
        logException(e);
      } finally {
        PerfLog.endRootSpan();  // end loop span
        PerfLog.outputPerfLog();
        PerfLog.clear();
        Sleeper.sleep(this.sleepTime);
      }
    }
  }

  private boolean isActivityThreadMsg(Message message) {
    return MigrateActivityThreadMessage.TYPE.equals(message.getType()) ||
        RemoveActivityThreadMessage.TYPE.equals(message.getType());
  }

  private boolean isClockMsg(Message message) {
    return ClockMessage.TYPE.equals(message.getType()) ||
        CleanTimeRangeMessage.TYPE.equals(message.getType());
  }

  public long getLastActive() {
    return this.lastActive;
  }

  public ActivityReplicationRunnerStatus getActualStatus() {
    return actualStatus;
  }

  @Override
  public boolean isStarted() {
    return this.started;
  }

  @Override
  public void stop() {
    logger.info("ActivityReplicationRunner received stop signal");
    this.expectedStatus = ActivityReplicationRunnerStatus.STOPPED;
  }

  @Override
  public boolean isStopped() {
    return this.actualStatus == ActivityReplicationRunnerStatus.STOPPED;
  }

  @Override
  public void pause() {
    logger.info("ActivityReplicationRunner received pause signal");
    this.expectedStatus = ActivityReplicationRunnerStatus.PAUSED;
  }

  @Override
  public void rerun() {
    logger.info("ActivityReplicationRunner received rerun signal");
    this.expectedStatus = ActivityReplicationRunnerStatus.RUNNING;
  }

  @Override
  public boolean isPaused() {
    return this.actualStatus == ActivityReplicationRunnerStatus.PAUSED;
  }

  private MessageBus getInputMessageBus() {
    final ActivityReplicationRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory
        .currentPlan();
    final MessageBusManager messageBusManager = integrationPlan.getMessageBusManager();
    final Optional<MessageBus> inputMessageBusOptional = messageBusManager
        .getMessageBusByName(inputMessageBusName);
    if (!inputMessageBusOptional.isPresent()) {
      throw new RuntimeException(String.format(
          "Unknown ActivityReplicationRunner input message bus: %s", inputMessageBusName));
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
