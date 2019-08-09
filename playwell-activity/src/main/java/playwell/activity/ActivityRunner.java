package playwell.activity;

import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ActivityThreadPool;
import playwell.activity.thread.ActivityThreadScheduler;
import playwell.activity.thread.ScheduleResult;
import playwell.activity.thread.UserScanOperation;
import playwell.clock.Clock;
import playwell.common.EasyMap;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.IntergrationUtils;
import playwell.integration.TopComponentType;
import playwell.message.ActivityThreadMessage;
import playwell.message.DomainMessage;
import playwell.message.Message;
import playwell.message.MessageDispatcher;
import playwell.message.MessageDispatcherListener;
import playwell.message.RoutedMessage;
import playwell.message.bus.MessageAckType;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;
import playwell.message.domainid.MessageDomainIDStrategy;
import playwell.message.domainid.MessageDomainIDStrategyManager;
import playwell.message.sys.ActivityThreadCtrlMessage;
import playwell.route.SlotsManager;
import playwell.route.migration.MigrationInputTask;
import playwell.route.migration.MigrationOutputTask;
import playwell.service.ServiceMeta;
import playwell.service.ServiceMetaManager;
import playwell.trigger.Trigger;
import playwell.trigger.TriggerManager;
import playwell.util.PerfLog;
import playwell.util.VariableHolder;


/**
 * 基于EventDispatcher的ActivityRunner，从外部源源不断接受消息，运行活动
 *
 * @author chihongze@gmail.com
 */
public class ActivityRunner implements MessageDispatcher {

  private static final Logger logger = LogManager.getLogger(ActivityRunner.class);

  // MessageDispatcherListeners
  private final Collection<MessageDispatcherListener> listeners;

  // ActivityRunner的服务名称，用于消息通信
  private String serviceName;

  // 消息输入总线名称
  private String inputMessageBusName;

  // 休眠时间
  private long sleepTime;

  // 每个循环周期能获取的最大消息数目
  private int maxFetchNum;

  // 消息确认类型
  private MessageAckType messageAckType;

  // 是否已经启动
  private volatile boolean started = false;

  // 客户请求的目标状态
  private volatile ActivityRunnerStatus expectedStatus = ActivityRunnerStatus.INIT;

  // 系统的真实状态
  private volatile ActivityRunnerStatus actualStatus = ActivityRunnerStatus.INIT;

  // 最近活跃时间
  private volatile long lastActive = 0L;

  private MessageBusManager messageBusManager = null;

  private ServiceMetaManager serviceMetaManager = null;

  private SlotsManager slotsManager = null;

  private ActivityThreadPool activityThreadPool = null;

  private volatile UserScanOperation userScanOperation = null;

  // 上次发生的异常类型
  private Class<? extends Exception> lastExceptionType = null;

  // 最近输出异常的时间
  private long lastOutputExceptionTime = 0L;

  public ActivityRunner() {
    this.listeners = new LinkedList<>();
  }

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    this.serviceName = configuration.getString(ConfigItems.SERVICE_NAME);
    this.inputMessageBusName = configuration.getString(ConfigItems.INPUT_MESSAGE_BUS);
    this.sleepTime = configuration.getLong(ConfigItems.SLEEP_TIME, ConfigItems.DEFAULT_SLEEP_TIME);
    this.maxFetchNum = configuration
        .getInt(ConfigItems.MAX_FETCH_NUM, ConfigItems.DEFAULT_MAX_FETCH_NUM);
    Optional<MessageAckType> messageAckTypeOptional = MessageAckType.valueOfByType(
        configuration
            .getString(ConfigItems.MESSAGE_ACK_TYPE, ConfigItems.DEFAULT_MESSAGE_ACK_TYPE));
    if (!messageAckTypeOptional.isPresent()) {
      throw new RuntimeException(String.format(
          "Unknown message ack type: %s", configuration.getString(ConfigItems.MESSAGE_ACK_TYPE)));
    }
    this.messageAckType = messageAckTypeOptional.get();

    // 加载MessageDispatcherListener
    IntergrationUtils.loadAndInitSubComponents(configuration.getObjectList(ConfigItems.LISTENERS))
        .forEach(listenerObj -> listeners.add((MessageDispatcherListener) listenerObj));

    // 注册服务
    ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    ServiceMetaManager serviceMetaManager = integrationPlan.getServiceMetaManager();
    serviceMetaManager.registerServiceMeta(new ServiceMeta(
        serviceName,
        inputMessageBusName,
        Collections.emptyMap()
    ));
  }

  @Override
  public void dispatch() {
    this.actualStatus = ActivityRunnerStatus.RUNNING;

    final ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final MessageDomainIDStrategyManager messageDomainIDStrategyManager = integrationPlan
        .getMessageDomainIDStrategyManager();
    final ActivityManager activityManager = integrationPlan.getActivityManager();
    final Clock clock = integrationPlan.getClock();
    this.messageBusManager = integrationPlan.getMessageBusManager();

    this.activityThreadPool = integrationPlan.getActivityThreadPool();
    final ActivityThreadScheduler activityThreadScheduler = integrationPlan
        .getActivityThreadScheduler();
    final TriggerManager triggerManager = integrationPlan.getTriggerManager();

    if (integrationPlan.contains(TopComponentType.SLOTS_MANAGER)) {
      this.slotsManager = integrationPlan.getSlotsManager();
    } else {
      this.slotsManager = null;
    }

    this.serviceMetaManager = integrationPlan.getServiceMetaManager();

    this.started = true;

    while (true) {
      PerfLog.beginSpan("loop");

      if (expectedStatus == ActivityRunnerStatus.STOPPED) {
        this.actualStatus = ActivityRunnerStatus.STOPPED;
        logger.info("ActivityRunner stopped.");
        break;
      }

      if (expectedStatus == ActivityRunnerStatus.PAUSED) {
        this.actualStatus = ActivityRunnerStatus.PAUSED;
        logger.info("ActivityRunner paused.");
        try {
          TimeUnit.SECONDS.sleep(3L);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        continue;
      }

      PerfLog.beginSpan("before_loop_listeners");
      this.callbackListeners(MessageDispatcherListener::beforeLoop);
      PerfLog.endSpan();  // end span for before_loop_listeners

      if (expectedStatus == ActivityRunnerStatus.SCANNING) {
        this.actualStatus = ActivityRunnerStatus.SCANNING;
        try {
          this.activityThreadPool.scanAll(this.userScanOperation);
        } catch (Exception e) {
          logger.error("Scan activity thread error", e);
        } finally {
          this.expectedStatus = ActivityRunnerStatus.RUNNING;
        }
      }

      if (slotsManager != null) {
        // 判断当前节点是否是迁移输出节点
        final MigrationOutputTask migrationOutputTask = slotsManager.getMigrationOutputTask();
        if (migrationOutputTask != null) {
          migrationOutputTask.getMigrationProgress(serviceName)
              .ifPresent(migrationOutputTask::startOutputTask);
        }
        // 判断当前节点是否是迁移输入节点
        final MigrationInputTask migrationInputTask = slotsManager.getMigrationInputTask();
        if (migrationInputTask != null) {
          migrationInputTask.getMigrationProgress(serviceName).ifPresent(process -> {
            this.actualStatus = ActivityRunnerStatus.MIGRATING_IN;
            migrationInputTask.startInputTask(process);
          });
        }
      }

      this.actualStatus = ActivityRunnerStatus.RUNNING;

      try {
        this.lastActive = System.currentTimeMillis();

        PerfLog.beginSpan("get_domain_id_strategies");
        final Collection<MessageDomainIDStrategy> messageDomainIDStrategies = messageDomainIDStrategyManager
            .getAllMessageDomainIDStrategies();
        PerfLog.endSpan();  // end span for get_domain_id_strategies

        // 根据配置，从队列中获取指定数目的消息，计算出相应的domainId，并将系统消息单独分开处理
        final Map<String, Map<String, Collection<Message>>> commonMessagesByStrategies =
            Maps.newHashMapWithExpectedSize(messageDomainIDStrategies.size());  // 普通消息
        messageDomainIDStrategies.forEach(strategy ->
            commonMessagesByStrategies.put(
                strategy.name(), new HashMap<>(maxFetchNum * 3 / 2)));

        final Map<Pair<Integer, String>, Collection<Message>> activityThreadMessages =
            Maps.newHashMapWithExpectedSize(maxFetchNum * 3 / 2); // ActivityThread消息
        final Map<Pair<Integer, String>, Collection<ActivityThreadCtrlMessage>> ctrlMessages =
            Maps.newHashMap(); // 系统控制消息

        PerfLog.beginSpan("get_input_message_bus");
        final Optional<MessageBus> inputMessageBusOptional = messageBusManager
            .getMessageBusByName(inputMessageBusName);
        if (!inputMessageBusOptional.isPresent()) {
          throw new RuntimeException(
              "Could not found the input message bus: " + inputMessageBusName);
        }
        final MessageBus inputMessageBus = inputMessageBusOptional.get();
        PerfLog.endSpan();  // end span for get_input_message_bus

        PerfLog.beginSpan("read_messages");
        int readCount = inputMessageBus.readWithConsumer(maxFetchNum, message -> {

          if (ifMessageNotBelongToThisNodeThenRedirectToTheRightNode(message)) {
            return;
          }

          if (message instanceof ActivityThreadCtrlMessage) {  // 收集系统消息
            ActivityThreadCtrlMessage ctrlMessage = (ActivityThreadCtrlMessage) message;
            ctrlMessages.computeIfAbsent(
                Pair.of(ctrlMessage.getActivityId(), ctrlMessage.getDomainId()),
                p -> new LinkedList<>()).add(ctrlMessage);
          } else if (message instanceof ActivityThreadMessage) {
            ActivityThreadMessage activityThreadMessage = (ActivityThreadMessage) message;
            activityThreadMessages.computeIfAbsent(
                Pair.of(activityThreadMessage.getActivityId(), activityThreadMessage.getDomainId()),
                p -> new LinkedList<>()).add(message);
          } else if (message instanceof RoutedMessage) {
            RoutedMessage routedMessage = (RoutedMessage) message;
            Map<String, Collection<Message>> commonMessages = commonMessagesByStrategies.get(
                routedMessage.getDomainIDStrategy());
            commonMessages.computeIfAbsent(
                routedMessage.getDomainId(), did -> new LinkedList<>()).add(routedMessage);
          } else {
            for (MessageDomainIDStrategy domainIDStrategy : messageDomainIDStrategies) {
              Map<String, Collection<Message>> commonMessages = commonMessagesByStrategies.get(
                  domainIDStrategy.name());
              Optional<String> domainIdOptional = domainIDStrategy.domainId(message);
              domainIdOptional.ifPresent(domainId ->
                  commonMessages.computeIfAbsent(domainId, did -> new LinkedList<>()).add(message));
            }
          }
        });
        PerfLog.endSpan(
            String.format("Read messages count: %d", readCount));  // end span for read_messages

        if (messageAckType == MessageAckType.AFTER_READ) {
          PerfLog.beginSpan("ack_messages");
          inputMessageBus.ackMessages();
          PerfLog.endSpan();  // end span for ack_messages
        }

        // 优先处理系统消息
        if (MapUtils.isNotEmpty(ctrlMessages)) {
          PerfLog.beginSpan("handle_sys_messages");
          parallelScheduleCtrlMessages(activityThreadPool, activityThreadScheduler, ctrlMessages);
          PerfLog.endSpan();  // end span for handle_sys_messages
        }

        // 再处理ActivityThread消息
        if (MapUtils.isNotEmpty(activityThreadMessages)) {
          PerfLog.beginSpan("handle_activity_thread_messages");
          parallelSchedule(activityThreadPool, activityThreadScheduler, activityThreadMessages);
          PerfLog.endSpan();  // end span for handle_activity_thread_messages
        }

        // 最后，通过触发器来处理普通消息
        PerfLog.beginSpan("get_schedulable_activities");
        final Collection<Activity> activities = activityManager.getSchedulableActivities();
        if (CollectionUtils.isEmpty(activities)) {
          continue;
        }
        PerfLog.endSpan();  // end span for get_schedulable_activities

        PerfLog.beginSpan("trigger");
        final Map<ActivityThread, Collection<Message>> collector = new ConcurrentHashMap<>(
            maxFetchNum * 3 / 2);
        activities.parallelStream().forEach(activity -> {
          Trigger trigger = triggerManager.getTriggerInstance(activity);
          trigger.handleMessageStream(collector, commonMessagesByStrategies);
        });
        PerfLog.endSpan(String
            .format("All activity thread count: %d", collector.size()));  // end span for trigger

        // 可以执行ActivityThread了
        PerfLog.beginSpan("schedule_activity_threads");
        collector.keySet().parallelStream().forEach(activityThread -> {
          final Collection<Message> mailbox = collector.get(activityThread);
          ScheduleResult result = activityThreadScheduler.schedule(activityThread, mailbox);
          logScheduleResult(result);
        });
        PerfLog.endSpan();  // end span for schedule_activity_threads

        if (messageAckType == MessageAckType.AFTER_HANDLE) {
          PerfLog.beginSpan("ack_messages");
          inputMessageBus.ackMessages();
          PerfLog.endSpan();  // end span for ack messages
        }

        // 处理时钟消息
        PerfLog.beginSpan("handle_clock_messages");
        final long now = System.currentTimeMillis();
        final int consumeBatchSize = 1000;
        final VariableHolder<Map<Pair<Integer, String>, Collection<Message>>> groupedClockMessages =
            new VariableHolder<>(new HashMap<>(consumeBatchSize));
        clock.consumeClockMessage(now, clockMessage -> {
          // 转发不属于该节点的时钟消息
          if (ifMessageNotBelongToThisNodeThenRedirectToTheRightNode(clockMessage)) {
            return;
          }

          final Pair<Integer, String> threadIdentifier = Pair.of(
              clockMessage.getActivityId(), clockMessage.getDomainId());
          groupedClockMessages.getVar()
              .computeIfAbsent(threadIdentifier, k -> new LinkedList<>())
              .add(clockMessage);

          if (groupedClockMessages.getVar().size() >= consumeBatchSize) {
            parallelSchedule(activityThreadPool, activityThreadScheduler,
                groupedClockMessages.getVar());
            groupedClockMessages.setVar(new HashMap<>(consumeBatchSize));
          }
        });
        // 消费掉剩余的时钟事件
        if (MapUtils.isNotEmpty(groupedClockMessages.getVar())) {
          parallelSchedule(activityThreadPool, activityThreadScheduler,
              groupedClockMessages.getVar());
        }

        PerfLog.beginSpan("clean");
        clock.clean(now);
        PerfLog.endSpan();  // end span for clean

        PerfLog.endSpan();  // end span for handle_clock_messages

        PerfLog.beginSpan("after_loop_listeners");
        callbackListeners(MessageDispatcherListener::afterLoop);
        PerfLog.endSpan();  // end span for after_loop_listeners
      } catch (Exception e) {
        this.logException(e);
        callbackListeners(listeners -> listeners.errorHappened(e));
      } finally {
        PerfLog.endRootSpan();  // end span for loop
        PerfLog.outputPerfLog();
        PerfLog.clear();
        sleep();
      }
    }

    this.actualStatus = ActivityRunnerStatus.STOPPED;
  }

  // 判断消息是否属于该节点，如果属于，则返回false；如果不属于，则转发给对应的正确节点，然后返回true
  private boolean ifMessageNotBelongToThisNodeThenRedirectToTheRightNode(Message message) {

    if (slotsManager != null && message instanceof DomainMessage) {
      DomainMessage domainMessage = (DomainMessage) message;
      String nodeServiceName = slotsManager.getServiceByKey(domainMessage.getDomainId());
      if (!this.serviceName.equals(nodeServiceName)) {
        Optional<ServiceMeta> serviceMetaOptional = serviceMetaManager
            .getServiceMetaByName(nodeServiceName);
        if (!serviceMetaOptional.isPresent()) {
          throw new RuntimeException(String.format(
              "Unknown service %s for domain id %s",
              nodeServiceName,
              domainMessage.getDomainId()
          ));
        }
        ServiceMeta serviceMeta = serviceMetaOptional.get();

        Optional<MessageBus> messageBusOptional = messageBusManager
            .getMessageBusByName(serviceMeta.getMessageBus());
        if (!messageBusOptional.isPresent()) {
          throw new RuntimeException(
              String.format(
                  "Unknown message bus %s of service %s",
                  serviceMeta.getMessageBus(),
                  nodeServiceName
              )
          );
        }
        MessageBus messageBus = messageBusOptional.get();

        try {
          messageBus.write(message);
        } catch (MessageBusNotAvailableException e) {
          throw new RuntimeException(e);
        }

        return true;
      }
    }

    return false;
  }

  @Override
  public boolean isStarted() {
    return this.started;
  }

  @Override
  public void stop() {
    this.expectedStatus = ActivityRunnerStatus.STOPPED;
    if (slotsManager != null) {
      final MigrationOutputTask outputTask = slotsManager.getMigrationOutputTask();
      if (outputTask != null) {
        outputTask.stop();
      }
      final MigrationInputTask inputTask = slotsManager.getMigrationInputTask();
      if (inputTask != null) {
        inputTask.stop();
      }
    }
    logger.info("ActivityRunner received the stop signal.");
  }

  @Override
  public boolean isStopped() {
    return this.actualStatus == ActivityRunnerStatus.STOPPED;
  }

  @Override
  public void pause() {
    this.expectedStatus = ActivityRunnerStatus.PAUSED;
    logger.info("ActivityRunner received the pause signal");
  }

  @Override
  public void rerun() {
    this.expectedStatus = ActivityRunnerStatus.RUNNING;
  }

  @Override
  public boolean isPaused() {
    return this.actualStatus == ActivityRunnerStatus.PAUSED;
  }

  public ActivityRunnerStatus getStatus() {
    return this.actualStatus;
  }

  public long getLastActive() {
    return this.lastActive;
  }

  public void startScanProcess(EasyMap scanArgs) {
    final UserScanOperation userScanOperation = UserScanOperation.buildWithArgs(scanArgs);
    // 如果是只读的，那么可以单独开启一个DAEMON线程扫描
    if (userScanOperation.readOnly()) {
      final Thread scanThread = new Thread(() -> activityThreadPool.scanAll(userScanOperation));
      scanThread.setDaemon(true);
      scanThread.start();
    } else {
      // 非只读，在事件处理循环中同步扫描
      this.userScanOperation = userScanOperation;
      this.expectedStatus = ActivityRunnerStatus.SCANNING;
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

  private void parallelSchedule(
      ActivityThreadPool activityThreadPool, ActivityThreadScheduler activityThreadScheduler,
      Map<Pair<Integer, String>, Collection<Message>> messages) {

    PerfLog.beginSpan("multi_get_threads");
    final Collection<ActivityThread> activityThreads = activityThreadPool
        .multiGetActivityThreads(messages.keySet());
    if (CollectionUtils.isEmpty(activityThreads)) {
      return;
    }
    PerfLog.endSpan(String.format("Query count: %d", messages.keySet().size()));

    PerfLog.beginSpan("schedule_threads");
    activityThreads.parallelStream().forEach(activityThread -> {
      if (activityThread == null) {
        return;
      }
      final Pair<Integer, String> identifier = Pair.of(
          activityThread.getActivity().getId(), activityThread.getDomainId());
      final Collection<Message> mailbox = messages.get(identifier);
      ScheduleResult result = activityThreadScheduler.schedule(activityThread, mailbox);
      logScheduleResult(result);
    });
    PerfLog.endSpan();
  }

  private void parallelScheduleCtrlMessages(
      ActivityThreadPool activityThreadPool,
      ActivityThreadScheduler activityThreadScheduler,
      Map<Pair<Integer, String>, Collection<ActivityThreadCtrlMessage>> messages) {

    PerfLog.beginSpan("multi_get_threads");
    final Collection<ActivityThread> activityThreads = activityThreadPool
        .multiGetActivityThreads(messages.keySet());
    if (CollectionUtils.isEmpty(activityThreads)) {
      return;
    }
    PerfLog.endSpan(String.format("Query count: %d", messages.keySet().size()));

    PerfLog.beginSpan("schedule_threads");
    activityThreads.parallelStream().forEach(activityThread -> {
      if (activityThread == null) {
        return;
      }
      final Pair<Integer, String> identifier = Pair.of(
          activityThread.getActivity().getId(), activityThread.getDomainId());
      final Collection<ActivityThreadCtrlMessage> mailbox = messages.get(identifier);
      ScheduleResult result = activityThreadScheduler
          .scheduleWithCtrlMessages(activityThread, mailbox);
      logScheduleResult(result);
    });
    PerfLog.endSpan();
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

  private void logScheduleResult(ScheduleResult scheduleResult) {
    if (logger.isDebugEnabled()) {
      logger.debug(scheduleResult.toString());
    }
  }

  public String getServiceName() {
    return this.serviceName;
  }

  /**
   * 配置项目
   */
  public interface ConfigItems {

    /**
     * 服务名称
     */
    String SERVICE_NAME = "service_name";

    /**
     * ActivityRunner的消息输入总线
     */
    String INPUT_MESSAGE_BUS = "input_message_bus";

    /**
     * 每个循环周期的休眠时间，单位毫秒
     */
    String SLEEP_TIME = "sleep_time";
    long DEFAULT_SLEEP_TIME = 100;

    /**
     * 每个循环周期能连续从队列中获取的最大消息数目
     */
    String MAX_FETCH_NUM = "max_fetch_num";
    int DEFAULT_MAX_FETCH_NUM = 1000;

    /**
     * ActivityRunnerListeners
     */
    String LISTENERS = "listeners";

    /**
     * 消息确认类型
     */
    String MESSAGE_ACK_TYPE = "message_ack_type";
    String DEFAULT_MESSAGE_ACK_TYPE = MessageAckType.AFTER_READ.getType();
  }
}
