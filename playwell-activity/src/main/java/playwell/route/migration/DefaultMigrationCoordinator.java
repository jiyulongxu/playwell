package playwell.route.migration;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multiset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.Result;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.message.bus.MessageBusManager;
import playwell.route.SlotsManager;

/**
 * DefaultMigrationCoordinator
 */
public class DefaultMigrationCoordinator implements MigrationCoordinator {

  private static final Logger logger = LogManager.getLogger(DefaultMigrationCoordinator.class);

  private final Executor coordinatorExecutor = Executors.newSingleThreadScheduledExecutor();

  private MigrationPlanDataAccess migrationPlanDataAccess;

  private MigrationProgressDataAccess migrationProgressDataAccess;

  private volatile boolean stopMark = false;

  private volatile boolean stopped = true;

  public DefaultMigrationCoordinator(String dataSource) {
    this.migrationPlanDataAccess = new MigrationPlanDataAccess(dataSource);
    this.migrationProgressDataAccess = new MigrationProgressDataAccess(dataSource);
  }

  @Override
  public Result startMigrationPlan(
      String messageBus,
      Map<String, Object> inputMessageBusConfig,
      Map<String, Object> outputMessageBusConfig,
      Map<String, Integer> slotsDistribution,
      String comment) {
    logger.info("[Coordinator] Received migration request");
    final IntegrationPlan currentIntegrationPlan = IntegrationPlanFactory.currentPlan();
    final MessageBusManager messageBusManager = (MessageBusManager) currentIntegrationPlan
        .getTopComponent(TopComponentType.MESSAGE_BUS_MANAGER);
    final SlotsManager slotsManager = (SlotsManager) currentIntegrationPlan
        .getTopComponent(TopComponentType.SLOTS_MANAGER);

    try {
      messageBusManager.newMessageBus(messageBus, inputMessageBusConfig);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      return Result.failWithCodeAndMessage(
          ErrorCodes.INIT_INPUT_MESSAGE_BUS_ERROR, e.getMessage());
    }

    try {
      messageBusManager.newMessageBus(messageBus, outputMessageBusConfig);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      return Result.failWithCodeAndMessage(
          ErrorCodes.INIT_OUTPUT_MESSAGE_BUS_ERROR, e.getMessage());
    }

    if (MapUtils.isEmpty(slotsDistribution)) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.INVALID_SLOTS,
          "The slots distribution is empty!"
      );
    }

    final MigrationPlan migrationPlan = new MigrationPlan(
        messageBus,
        inputMessageBusConfig,
        outputMessageBusConfig,
        slotsDistribution,
        comment,
        new Date()
    );

    logger.info("[Coordinator] Created new migration plan:" + migrationPlan);

    Result result = slotsManager.getSlotsDistribution();
    if (!result.isOk()) {
      return result;
    }

    // slots总数
    final int allSlotsNum = result.getFromResultData(SlotsManager.ResultFields.SLOTS);
    // 当前的slots分布
    final Map<String, Long> currentSlotsDistribution = result
        .getFromResultData(SlotsManager.ResultFields.DISTRIBUTION);

    if (allSlotsNum != slotsDistribution.values().stream().reduce((a, b) -> a + b).orElse(0)) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.INVALID_SLOTS,
          String.format("The sum of slots num must be: %d", allSlotsNum)
      );
    }

    // slots输出详情
    final Map<String, LinkedList<Integer>> outputDetails = new HashMap<>();
    // slots输入计数
    final Multiset<String> inputCounter = HashMultiset.create();
    slotsDistribution.forEach((service, num) -> {
      int currentSlotsNum = currentSlotsDistribution.getOrDefault(service, 0L).intValue();
      // 节点当前持有的slots数目大于目标数目，需要输出slots
      if (currentSlotsNum > num) {
        // 挑选出要输出的slots
        final List<Integer> slots = new ArrayList<>(slotsManager.getSlotsByServiceName(service));
        final int outputNum = currentSlotsNum - num;
        outputDetails.put(service, new LinkedList<>(slots.subList(0, outputNum)));
      }
      // 节点当前持有的slots数目小于目标数目，需要输入slots
      else if (currentSlotsNum < num) {
        inputCounter.add(service, num - currentSlotsNum);
      }
    });

    // 生成迁移计划
    final int changed = migrationPlanDataAccess.insert(migrationPlan);
    if (changed != 1) {
      // 抢占失败
      return Result.failWithCodeAndMessage(
          ErrorCodes.ALREADY_EXIST,
          "The migration plan already exist!"
      );
    }
    final List<String> allInputService = new ArrayList<>(inputCounter.elementSet());
    AtomicInteger inputIndex = new AtomicInteger(0);
    final List<MigrationProgress> allProgress = new LinkedList<>();
    // 生成具体的迁移进度
    outputDetails.forEach((outputService, slots) -> {
      while (slots.size() > 0) {
        final int maxOutputNum = slots.size();
        final String inputService = allInputService.get(inputIndex.get());
        final int expectedInputNum = inputCounter.count(inputService);

        // 输出节点的slots可以全部分配给输入节点
        if (maxOutputNum <= expectedInputNum) {
          inputCounter.remove(inputService, maxOutputNum);
          allProgress.add(buildMigrationProgress(outputService, inputService, slots));
          if (inputCounter.count(inputService) == 0) {
            // 可以换到下一个节点了
            inputIndex.incrementAndGet();
          }
          return;
        } else {
          // 输出节点的slots一部分可以分配给输入节点
          final List<Integer> targetSlots = new LinkedList<>();
          for (int i = 0; i < expectedInputNum; i++) {
            targetSlots.add(slots.pop());
          }
          allProgress.add(buildMigrationProgress(outputService, inputService, targetSlots));
          inputIndex.incrementAndGet();
        }
      }
    });

    allProgress.forEach(progress -> logger.info(
        "[Coordinator] New migration progress:" + progress));

    migrationProgressDataAccess.insert(allProgress);

    // 启动迁移线程
    this.stopMark = false;
    coordinatorExecutor.execute(this::coordinate);

    return Result.ok();
  }

  // 执行具体的迁移计划
  // S1. 获取当前的MigrationPlan
  // S2. 进入循环
  // S3. 获取所有的MigrationProgress
  // S4. 如果所有的MigrationProgress都处于完成状态，则本次迁移完毕，清理迁移数据
  // S5. 如果有MigrationProgress处于迁移中状态，则等待其迁移完成
  // S6. 其他情况则选择一个PENDING状态的Progress，将其修改为迁移中状态，开始迁移
  private void coordinate() {
    logger.info("The migration coordinator start!");
    this.stopped = false;

    Optional<MigrationPlan> migrationPlanOptional = migrationPlanDataAccess.get();
    if (!migrationPlanOptional.isPresent()) {
      logger.error("MigrationPlan not found, the coordinator will exit");
      return;
    }

    while (true) {
      if (stopMark) {
        logger.info("The migration coordinator stopped!");
        stopped = true;
        break;
      }

      Collection<MigrationProgress> allProgress = migrationProgressDataAccess.getAllProgress();
      if (CollectionUtils.isEmpty(allProgress)) {
        logger.error("There is no progress in the migration plan!");
        break;
      }

      // 是否所有progress都已经完成
      boolean allFinished = allProgress.stream().allMatch(
          progress -> progress.getStatus() == MigrationProgressStatus.FINISHED);
      if (allFinished) {
        logger.info("Congratulations! The migration plan all finished!");
        migrationProgressDataAccess.clean();
        migrationPlanDataAccess.clean();
        break;
      }

      // 获取处于正在迁移状态的progress，然后等待
      Optional<MigrationProgress> migrationProgressOptional = allProgress.stream()
          .filter(progress -> progress.getStatus() == MigrationProgressStatus.MIGRATING)
          .findFirst();
      if (migrationProgressOptional.isPresent()) {
        MigrationProgress migrationProgress = migrationProgressOptional.get();
        logger.info(String.format(
            "Output node: %s, Input node: %s, Slots: %s",
            migrationProgress.getOutputNode(),
            migrationProgress.getInputNode(),
            migrationProgress.getSlots()
        ));
        try {
          TimeUnit.SECONDS.sleep(1L);
          continue;
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }

      // 获得一个处于pending状态的progress开始迁移
      migrationProgressOptional = allProgress
          .stream()
          .filter(progress -> progress.getStatus() == MigrationProgressStatus.PENDING)
          .findFirst();
      if (!migrationProgressOptional.isPresent()) {
        logger.error("There is no pending progress in the migration plan!");
        break;
      }

      MigrationProgress migrationProgress = migrationProgressOptional.get();
      this.migrationProgressDataAccess.updateStatus(
          migrationProgress.getOutputNode(),
          migrationProgress.getInputNode(),
          MigrationProgressStatus.MIGRATING
      );
    }

    this.stopped = true;
  }

  @Override
  public void stop() {
    this.stopMark = true;
  }

  @Override
  public boolean isStopped() {
    return this.stopped;
  }

  @Override
  public Result getCurrentStatus() {
    Optional<MigrationPlan> migrationPlanOptional = migrationPlanDataAccess.get();
    if (!migrationPlanOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND,
          "Could not found the migration plan"
      );
    }

    MigrationPlan migrationPlan = migrationPlanOptional.get();
    Collection<MigrationProgress> progress = migrationProgressDataAccess.getAllProgress();

    return Result.okWithData(ImmutableMap.of(
        ResultFields.PLAN, migrationPlan,
        ResultFields.PROGRESS, progress
    ));
  }

  @Override
  public Result continueMigrationPlan() {
    Optional<MigrationPlan> migrationPlanOptional = migrationPlanDataAccess.get();
    if (!migrationPlanOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND,
          "Could not found the migration plan"
      );
    }

    coordinatorExecutor.execute(this::coordinate);
    return Result.ok();
  }

  // Clean all data，only for test
  public void cleanAll() {
    migrationPlanDataAccess.clean();
    migrationProgressDataAccess.clean();
  }

  private MigrationProgress buildMigrationProgress(
      String outputService,
      String inputService,
      List<Integer> slots) {
    return new MigrationProgress(
        MigrationProgressStatus.PENDING,
        slots,
        outputService,
        inputService,
        "",
        "",
        false,
        false,
        null,
        null
    );
  }

}
