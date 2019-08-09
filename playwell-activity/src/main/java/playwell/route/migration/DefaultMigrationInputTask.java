package playwell.route.migration;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ActivityThreadPool;
import playwell.activity.thread.message.MigrateActivityThreadMessage;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.route.SlotsManager;
import playwell.util.VariableHolder;

/**
 * DefaultMigrationInputTask
 */
public class DefaultMigrationInputTask implements MigrationInputTask {

  private static final Logger logger = LogManager.getLogger(DefaultMigrationInputTask.class);

  private final MigrationPlanDataAccess migrationPlanDataAccess;

  private final MigrationProgressDataAccess migrationProgressDataAccess;

  private volatile boolean stopMark = false;

  private volatile boolean stopped = true;

  public DefaultMigrationInputTask(String dataSource) {
    this.migrationPlanDataAccess = new MigrationPlanDataAccess(dataSource);
    this.migrationProgressDataAccess = new MigrationProgressDataAccess(dataSource);
  }

  @Override
  public Optional<MigrationProgress> getMigrationProgress(String serviceName) {
    return migrationProgressDataAccess.getProgressByInputServiceName(serviceName);
  }

  // 启动输入任务
  // Steps:
  // S1. 获取迁入需要的组件：MigrationPlan、SlotsManager、MessageBus
  // S2. 开始从MessageBus读取消息
  // S3. 判断消息类型，以及消息所包含的ActivityThread是否是属于该节点的slots
  // S4. 向持久化存储写入ActivityThread
  // S5. 如果读到了EOF消息，则暂停消费，将input_finished标记为true
  @Override
  public void startInputTask(MigrationProgress migrationProgress) {
    final ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final SlotsManager slotsManager = integrationPlan.getSlotsManager();
    final MessageBusManager messageBusManager = integrationPlan.getMessageBusManager();

    final Optional<MigrationPlan> migrationPlanOptional = migrationPlanDataAccess.get();
    if (!migrationPlanOptional.isPresent()) {
      logger.error("Could not found the migration plan!");
      return;
    }

    final MigrationPlan migrationPlan = migrationPlanOptional.get();

    final String outputService = migrationProgress.getOutputNode();
    final String inputService = migrationProgress.getInputNode();
    final Set<Integer> targetSlots = new HashSet<>(migrationProgress.getSlots());
    final List<ActivityThread> receivedActivityThreads = new LinkedList<>();

    this.stopMark = false;
    this.stopped = false;

    final MessageBus messageBus = messageBusManager.newMessageBus(
        migrationPlan.getMessageBus(), migrationPlan.getInputMessageBusConfig());
    messageBus.open();

    final VariableHolder<Boolean> eof = new VariableHolder<>(false);

    try {
      while (true) {
        if (stopMark) {
          logger.info("The migration input task stopped");
          break;
        }

        try {
          messageBus.readWithConsumer(5000, message -> {

            if (message instanceof MigrateActivityThreadMessage) {
              final MigrateActivityThreadMessage threadMessage = (MigrateActivityThreadMessage) message;
              if (outputService.equals(threadMessage.getSender()) &&
                  inputService.equals(threadMessage.getReceiver())) {
                final ActivityThread activityThread = threadMessage.getActivityThread();
                final int slot = slotsManager.getSlotByKey(activityThread.getDomainId());
                if (targetSlots.contains(slot)) {
                  receivedActivityThreads.add(activityThread);
                  if (receivedActivityThreads.size() == 5000) {
                    batchSave(receivedActivityThreads);
                    receivedActivityThreads.clear();
                  }
                } else {
                  logger.error(String.format(
                      "Invalid MigrationActivityThreadMessage, the slot(%d) of ActivityThread(%s) is not match",
                      slot,
                      activityThread
                  ));
                }
              } else {
                logger.error(
                    String
                        .format("Invalid MigrateActivityThreadMessage: (output = %s, input = %s), "
                                + "current progress: (output = %s, input = %s)",
                            message.getSender(),
                            message.getReceiver(),
                            migrationProgress.getOutputNode(),
                            migrationProgress.getInputNode()
                        ));
              }
            } else if (message instanceof MigrateOutputFinishedMessage) {
              final MigrateOutputFinishedMessage eofMessage = (MigrateOutputFinishedMessage) message;
              if (outputService.equals(eofMessage.getSender()) &&
                  inputService.equals(eofMessage.getReceiver())) {
                eof.setVar(true);
              } else {
                logger.error(
                    String
                        .format("Invalid MigrateOutputFinishedMessage: (output = %s, input = %s), "
                                + "current progress: (output = %s, input = %s)",
                            message.getSender(),
                            message.getReceiver(),
                            migrationProgress.getOutputNode(),
                            migrationProgress.getInputNode()
                        ));
              }
            } else {
              logger.error(String.format(
                  "Unknown message: %s, "
                      + "The migration input task only accept MigrateActivityThreadMessage "
                      + "or MigrateOutputFinishedMessage",
                  message
              ));
            }
          });

          if (CollectionUtils.isNotEmpty(receivedActivityThreads)) {
            batchSave(receivedActivityThreads);
            receivedActivityThreads.clear();
          }
        } catch (Exception e) {
          logger.error("Migration input task error!", e);
          break;
        } finally {
          messageBus.ackMessages();
        }

        if (eof.getVar()) {
          migrationProgressDataAccess.updateInputFinished(migrationProgress.getInputNode());
          migrationProgressDataAccess.updateStatus(
              migrationProgress.getOutputNode(),
              migrationProgress.getInputNode(),
              MigrationProgressStatus.FINISHED
          );
          break;
        }
      }
    } finally {
      messageBus.close();
    }

    this.stopped = true;
  }

  protected void batchSave(Collection<ActivityThread> receivedActivityThreads) {
    final ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final ActivityThreadPool activityThreadPool = integrationPlan.getActivityThreadPool();
    activityThreadPool.batchSaveActivityThreads(receivedActivityThreads);
  }

  @Override
  public void stop() {
    this.stopMark = true;
  }

  @Override
  public boolean isStopped() {
    return this.stopped;
  }
}
