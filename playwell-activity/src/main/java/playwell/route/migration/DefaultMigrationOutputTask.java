package playwell.route.migration;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ActivityThreadPool;
import playwell.activity.thread.message.MigrateActivityThreadMessage;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;
import playwell.route.SlotsManager;
import playwell.util.VariableHolder;

/**
 * DefaultMigrationOutputTask
 */
public class DefaultMigrationOutputTask implements MigrationOutputTask {

  private static final Logger logger = LogManager.getLogger(DefaultMigrationOutputTask.class);

  private final Executor threadPool = Executors.newSingleThreadExecutor(
      new ThreadFactoryBuilder().setDaemon(true).build());

  private final MigrationPlanDataAccess migrationPlanDataAccess;

  private final MigrationProgressDataAccess migrationProgressDataAccess;

  // 任务停止标记
  private volatile boolean stopMark = false;

  // 是否已经真正停止
  private volatile boolean stopped = true;

  // 迁移是否正在进行
  private volatile boolean running = false;

  public DefaultMigrationOutputTask(String dataSource) {
    this.migrationPlanDataAccess = new MigrationPlanDataAccess(dataSource);
    this.migrationProgressDataAccess = new MigrationProgressDataAccess(dataSource);
  }

  @Override
  public Optional<MigrationProgress> getMigrationProgress(String serviceName) {
    return this.migrationProgressDataAccess.getProgressByOutputServiceName(serviceName);
  }

  // 启动迁移任务线程:
  @Override
  public void startOutputTask(MigrationProgress migrationProgress) {
    if (running) {
      return;
    }
    this.running = true;
    this.stopMark = false;
    this.stopped = false;
    changeService(migrationProgress.getSlots(), migrationProgress.getInputNode());
    threadPool.execute(() -> {
      try {
        doOutput(migrationProgress);
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      } finally {
        this.running = false;
      }
    });
  }

  // 执行slots输出
  // S2. 遍历所有的ActivityThread记录
  // S3. 筛选出符合条件的记录
  // S4. 通过MessageBus转发给相关的输入节点
  // S5. 当所有的数据遍历完毕之后，向输入端输出一个EOF消息，并修改output_finished为true
  private void doOutput(MigrationProgress progress) {
    logger.info("Migration output task start, the progress: " + progress);

    final Optional<MigrationPlan> migrationPlanOptional = migrationPlanDataAccess.get();
    if (!migrationPlanOptional.isPresent()) {
      logger.error("Could not found migration plan!");
      return;
    }
    final MigrationPlan migrationPlan = migrationPlanOptional.get();

    final ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final ActivityThreadPool activityThreadPool = integrationPlan.getActivityThreadPool();
    final SlotsManager slotsManager = integrationPlan.getSlotsManager();
    final MessageBusManager messageBusManager = integrationPlan.getMessageBusManager();

    final MessageBus migrationMessageBus = messageBusManager.newMessageBus(
        migrationPlan.getMessageBus(), migrationPlan.getOutputMessageBusConfig());
    migrationMessageBus.open();

    final Set<Integer> targetSlots = new HashSet<>(progress.getSlots());
    try {
      VariableHolder<Integer> counter = new VariableHolder<>(0);
      activityThreadPool.scanAll(scanContext -> {
        if (stopMark) {
          scanContext.stop();
          return;
        }
        final ActivityThread activityThread = scanContext.getCurrentActivityThread();
        final int slot = slotsManager.getSlotByKey(activityThread.getDomainId());

        // 属于要迁移的slot
        if (targetSlots.contains(slot)) {
          try {
            migrationMessageBus.write(new MigrateActivityThreadMessage(
                progress.getOutputNode(),
                progress.getInputNode(),
                activityThread
            ));
            counter.setVar(counter.getVar() + 1);
          } catch (MessageBusNotAvailableException e) {
            logger.error("The migration message bus is not available", e);
            throw new RuntimeException(e);
          }
        }
      });

      // EOF
      migrationMessageBus.write(new MigrateOutputFinishedMessage(
          progress.getOutputNode(),
          progress.getInputNode()
      ));
      // 修改output_finished
      migrationProgressDataAccess.updateOutputFinished(progress.getOutputNode());
      logger.info(String.format("Migration output finished, progress: %s, num: %d",
          progress, counter.getVar()));
    } catch (Exception e) {
      logger.error("Migration output task error!", e);
    } finally {
      migrationMessageBus.close();
      this.stopped = true;
    }
  }

  @Override
  public void stop() {
    this.stopMark = true;
  }

  @Override
  public boolean isStopped() {
    return this.stopped;
  }

  private void changeService(Collection<Integer> slots, String inputService) {
    ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    SlotsManager slotsManager = integrationPlan.getSlotsManager();
    slotsManager.modifyService(slots, inputService);
  }
}
