package playwell.clock;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import playwell.common.argument.BaseArgumentRootContext;
import playwell.common.argument.EventVarAccessMixin;
import playwell.common.expression.PlaywellExpression;
import playwell.common.expression.PlaywellExpressionContext;
import playwell.common.expression.spel.SpELPlaywellExpression;
import playwell.common.expression.spel.SpELPlaywellExpressionContext;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.message.MessageArgumentVar;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;


/**
 * 面向用户的ClockMessage扫描操作：指定筛选条件，然后输出指定的ClockMessage
 * <pre>
 *   {
 *     "conditions": [
 *        "activityId == 100 AND domainId == 'SamChi' AND eventAttr('xxx') == 'xxx'",
 *     ],
 *
 *     "log_per_record": 1,
 *     "limit": 100,
 *     "mark": "xxxx"
 *   }
 * </pre>
 */
public class UserScanOperation implements ClockMessageScanConsumer {

  private static final Logger logger = LogManager.getLogger("scan_clock");

  private final List<PlaywellExpression> conditions;

  private final int logPerRecords;

  private final int limit;

  private final String mark;

  private final List<String> syncMessageBusNames;

  private final int batchSyncNum;

  private final List<ClockMessage> syncBuffer = new LinkedList<>();

  public UserScanOperation(
      List<PlaywellExpression> conditions,
      int logPerRecords,
      int limit,
      String mark,
      List<String> syncMessageBusNames,
      int batchSyncNum
  ) {
    this.conditions = conditions;
    this.logPerRecords = logPerRecords;
    this.limit = limit;
    this.mark = mark;
    this.syncMessageBusNames = syncMessageBusNames;
    this.batchSyncNum = batchSyncNum;
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
        args.getInt(Args.LOG_PER_RECORDS, 1),
        args.getInt(Args.LIMIT, -1),
        args.getString(Args.MARK, RandomStringUtils.randomAlphanumeric(6)),
        args.getStringList(Args.SYNC_MESSAGE_BUS),
        args.getInt(Args.BATCH_SYNC_NUM, Args.DEFAULT_BATCH_SYNC_NUM)
    );
  }

  @Override
  public void accept(ScanClockMessageContext scanClockMessageContext) {
    final int allScannedNum = scanClockMessageContext.getAllScannedNum();
    final ClockMessage clockMessage = scanClockMessageContext.getCurrentClockMessage();

    // 达到了指定的扫描上限
    if (limit != -1 && allScannedNum > limit) {
      scanClockMessageContext.stop();
      return;
    }

    if (!isMatchCondition(clockMessage)) {
      return;
    }

    // 处理日志
    if (logPerRecords != 0 && allScannedNum % logPerRecords == 0) {
      logger.info(String.format("%s - %s", this.mark, clockMessage.toString()));
    }

    // 同步数据
    if (batchSyncNum != 0) {
      this.syncBuffer.add(clockMessage);
      if (allScannedNum % batchSyncNum == 0) {
        sync();
      }
    }
  }

  private boolean isMatchCondition(ClockMessage clockMessage) {
    if (CollectionUtils.isEmpty(conditions)) {
      return true;
    }

    final PlaywellExpressionContext expressionContext = new SpELPlaywellExpressionContext();
    expressionContext.setRootObject(new ExpressionContext(clockMessage));

    return conditions.stream()
        .anyMatch(cond -> (boolean) cond.getResult(expressionContext));
  }

  @Override
  public void onEOF() {
    sync();
  }

  @Override
  public void onStop() {
    sync();
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
          String.format("Unknown clock message sync bus: %s", busName)));
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

    String LOG_PER_RECORDS = "log_per_records";

    String LIMIT = "limit";

    String MARK = "mark";

    String SYNC_MESSAGE_BUS = "sync_message_bus";

    String BATCH_SYNC_NUM = "batch_sync_num";

    int DEFAULT_BATCH_SYNC_NUM = 0;
  }

  class ExpressionContext extends BaseArgumentRootContext implements EventVarAccessMixin {

    private final ClockMessage clockMessage;

    ExpressionContext(ClockMessage clockMessage) {
      this.clockMessage = clockMessage;
    }

    public int getActivityId() {
      return clockMessage.getActivityId();
    }

    public String getDomainId() {
      return clockMessage.getDomainId();
    }

    @Override
    public MessageArgumentVar getEvent() {
      return new MessageArgumentVar(clockMessage);
    }
  }
}
