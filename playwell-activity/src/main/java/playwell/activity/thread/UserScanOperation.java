package playwell.activity.thread;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.activity.ActivityRunner;
import playwell.activity.thread.message.MigrateActivityThreadMessage;
import playwell.common.EasyMap;
import playwell.common.argument.ActivityThreadAccessMixin;
import playwell.common.argument.BaseArgumentRootContext;
import playwell.common.argument.ContextVarAccessMixin;
import playwell.common.expression.PlaywellExpression;
import playwell.common.expression.PlaywellExpressionContext;
import playwell.common.expression.spel.SpELPlaywellExpression;
import playwell.common.expression.spel.SpELPlaywellExpressionContext;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;
import playwell.route.SlotsManager;
import playwell.util.TimeUtils;

/**
 * 面向用户的ActivityThread扫描操作： 指定筛选条件，然后针对筛选出的ActivityThread执行指定的动作
 */
public class UserScanOperation implements ActivityThreadScanConsumer {

  private static final Logger logger = LogManager.getLogger("scan");

  // 筛选条件表达式，如果没有指定条件，则筛选所有的ActivityThread对象
  private final List<PlaywellExpression> conditions;

  // 筛选最大数目
  private final int limit;

  // 移除slots不符合当前节点的ActivityThread
  private final boolean removeSlotNoMatch;

  // 移除筛选出来的ActivityThread
  private final boolean removeThread;

  // 扫描标记，用于在日志中标记一整次扫描，方便解析
  private final String mark;

  // 每迭代多少条就输出一条日志
  private final int logPerRecords;

  // ActivityRunner service name
  private final String activityRunnerServiceName;

  // SlotsManager
  private final SlotsManager slotsManager;

  // Sync message bus
  private final List<String> syncMessageBusNames;

  // Sync batch num
  private final int syncBatchNum;

  // Sync buffer
  private final List<MigrateActivityThreadMessage> syncBuffer = new LinkedList<>();

  // All scanned num
  private int allScannedNum = 0;

  // All matched num
  private int allMatchedNum = 0;

  public UserScanOperation(
      List<PlaywellExpression> conditions,
      int limit,
      boolean removeSlotNoMatch,
      boolean removeThread,
      String mark,
      int logPerRecords,
      List<String> syncMessageBusNames,
      int syncBatchNum) {
    this.conditions = conditions;
    this.limit = limit;
    this.removeSlotNoMatch = removeSlotNoMatch;
    this.removeThread = removeThread;
    this.mark = mark;
    this.logPerRecords = logPerRecords;
    IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    if (integrationPlan.contains(TopComponentType.SLOTS_MANAGER)) {
      this.slotsManager = (SlotsManager) integrationPlan
          .getTopComponent(TopComponentType.SLOTS_MANAGER);
      this.activityRunnerServiceName = ((ActivityRunner) integrationPlan
          .getTopComponent(TopComponentType.ACTIVITY_RUNNER)).getServiceName();
    } else {
      this.slotsManager = null;
      this.activityRunnerServiceName = null;
    }
    this.syncMessageBusNames = syncMessageBusNames;
    if (syncBatchNum <= 0) {
      throw new IllegalArgumentException("The sync batch num must be more than zero!");
    }
    this.syncBatchNum = syncBatchNum;
  }

  public static UserScanOperation buildWithArgs(EasyMap args) {
    final List<PlaywellExpression> conditions = args
        .getStringList(Args.CONDITIONS).stream().map(exprText -> {
          PlaywellExpression expression = new SpELPlaywellExpression(exprText);
          expression.compile();
          return expression;
        }).collect(Collectors.toList());
    return new UserScanOperation(
        conditions,
        args.getInt(Args.LIMIT, -1),
        args.getBoolean(Args.REMOVE_SLOT_NO_MATCH, false),
        args.getBoolean(Args.REMOVE_THREAD, false),
        args.getString(Args.MARK, RandomStringUtils.randomAlphanumeric(6)),
        args.getInt(Args.LOG_PER_RECORDS, Args.DEFAULT_LOG_PER_RECORDS),
        args.getStringList(Args.SYNC_MESSAGE_BUS),
        args.getInt(Args.SYNC_BATCH_NUM, Args.DEFAULT_SYNC_BATCH_NUM)
    );
  }

  /**
   * 本次扫描是否只有只读操作
   *
   * @return 只读
   */
  public boolean readOnly() {
    return (!removeSlotNoMatch) && (!removeThread);
  }

  @Override
  public void accept(ScanActivityThreadContext scanActivityThreadContext) {
    this.allScannedNum = scanActivityThreadContext.getAllScannedNum();
    final ActivityThread activityThread = scanActivityThreadContext.getCurrentActivityThread();

    // 不满足筛选条件
    if (!isMatchCondition(activityThread)) {
      return;
    }

    allMatchedNum++;

    // 处理日志
    if (logPerRecords != 0 && allScannedNum % logPerRecords == 0) {
      logger.info(String.format("%s - %s", this.mark, activityThread.toString()));
    }

    // 删除
    if (removeThread) {
      scanActivityThreadContext.remove();
    } else if (removeSlotNoMatch && !isSlotMatch(activityThread)) {
      scanActivityThreadContext.remove();
    }

    // 处理同步
    if (CollectionUtils.isNotEmpty(syncMessageBusNames)) {
      syncBuffer.add(new MigrateActivityThreadMessage(activityThread));
      if (allScannedNum % syncBatchNum == 0) {
        sync();
      }
    }

    if (limit != -1 && allMatchedNum >= limit) {
      scanActivityThreadContext.stop();
    }
  }

  private boolean isMatchCondition(ActivityThread activityThread) {
    if (CollectionUtils.isEmpty(conditions)) {
      return true;
    }

    final PlaywellExpressionContext expressionContext = new SpELPlaywellExpressionContext();
    expressionContext.setRootObject(new ExpressionContext(activityThread));

    return conditions.stream().anyMatch(cond -> (boolean) cond.getResult(expressionContext));
  }

  private boolean isSlotMatch(ActivityThread activityThread) {
    if (slotsManager == null) {
      return true;
    }

    return activityRunnerServiceName.equals(
        slotsManager.getServiceByKey(activityThread.getDomainId()));
  }

  @Override
  public void onEOF() {
    this.sync();
    logger.info(String.format(
        "%s - [EOF] All scanned num: %d, all matched num: %d",
        this.mark,
        this.allScannedNum,
        this.allMatchedNum
    ));
  }

  @Override
  public void onStop() {
    this.sync();
    logger.info(String.format(
        "%s - [Stopped] All scanned num: %d, all matched num: %d",
        this.mark,
        this.allScannedNum,
        this.allMatchedNum
    ));
  }

  @SuppressWarnings({"unchecked"})
  private void sync() {
    if (CollectionUtils.isEmpty(syncBuffer) || CollectionUtils.isEmpty(syncMessageBusNames)) {
      syncBuffer.clear();
      return;
    }

    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final MessageBusManager messageBusManager = (MessageBusManager) integrationPlan
        .getTopComponent(TopComponentType.MESSAGE_BUS_MANAGER);
    final List<MessageBus> messageBusList = this.syncMessageBusNames.stream().map(busName -> {
      final Optional<MessageBus> messageBusOptional = messageBusManager
          .getMessageBusByName(busName);
      return messageBusOptional.orElseThrow(() -> new RuntimeException(
          String.format("Unknown activity thread sync bus: %s", busName)));
    }).collect(Collectors.toList());
    messageBusList.forEach(messageBus -> {
      try {
        messageBus.write((Collection) syncBuffer);
      } catch (MessageBusNotAvailableException e) {
        throw new RuntimeException(e);
      }
    });

    syncBuffer.clear();
  }

  interface Args {

    String CONDITIONS = "conditions";

    String LIMIT = "limit";

    String REMOVE_SLOT_NO_MATCH = "remove_slot_no_match";

    String REMOVE_THREAD = "remove_thread";

    String MARK = "mark";

    String LOG_PER_RECORDS = "log_per_records";

    int DEFAULT_LOG_PER_RECORDS = 1;

    String SYNC_MESSAGE_BUS = "sync_message_bus";

    String SYNC_BATCH_NUM = "sync_batch_num";

    int DEFAULT_SYNC_BATCH_NUM = 1;
  }

  class ExpressionContext extends BaseArgumentRootContext implements
      ContextVarAccessMixin, ActivityThreadAccessMixin {

    private final ActivityThread activityThread;

    ExpressionContext(ActivityThread activityThread) {
      this.activityThread = activityThread;
    }

    @Override
    public ActivityThreadArgumentVar getActivityThread() {
      return new ActivityThreadArgumentVar(activityThread);
    }

    @Override
    public Map<String, Object> getContext() {
      return activityThread.getContext();
    }

    public String getStatus() {
      return activityThread.getStatus().getStatus();
    }

    public boolean idle(String timeDesc) {
      final long idleTimestamp = TimeUtils.getTimeDeltaFromDesc(timeDesc);
      return System.currentTimeMillis() - activityThread.getUpdatedOn() >= idleTimestamp;
    }

    public boolean slotNotBelongToThisNode() {
      if (slotsManager != null && activityRunnerServiceName != null) {
        return !activityRunnerServiceName
            .equals(slotsManager.getServiceByKey(activityThread.getDomainId()));
      }
      return false;
    }
  }
}
