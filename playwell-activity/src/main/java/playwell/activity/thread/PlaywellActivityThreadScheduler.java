package playwell.activity.thread;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.action.Action;
import playwell.action.ActionCtrlCondition;
import playwell.action.ActionCtrlInfo;
import playwell.action.ActionCtrlType;
import playwell.action.ActionDefinition;
import playwell.action.ActionManager;
import playwell.action.ActionRuntimeException;
import playwell.action.AsyncAction;
import playwell.action.RepairActionCtrlInfo;
import playwell.action.RetryActionCtrlInfo;
import playwell.action.SyncAction;
import playwell.activity.Activity;
import playwell.activity.definition.ActivityDefinition;
import playwell.clock.CachedTimestamp;
import playwell.clock.Clock;
import playwell.clock.ClockMessage;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.common.expression.PlaywellExpression;
import playwell.common.expression.PlaywellExpressionContext;
import playwell.common.expression.spel.SpELPlaywellExpressionContext;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.IntergrationUtils;
import playwell.message.Message;
import playwell.message.bus.MessageBusNotAvailableException;
import playwell.message.sys.ActivityThreadCtrlMessage;
import playwell.message.sys.FailureMessage;
import playwell.message.sys.FinishedMessage;
import playwell.message.sys.NeedRepairMessage;
import playwell.message.sys.RepairArguments;
import playwell.message.sys.RepairArguments.RepairCtrl;


/**
 * 该实现为Playwell内置的ActivityThread调度器
 *
 * @author chihongze@gmail.com
 */
public class PlaywellActivityThreadScheduler implements ActivityThreadScheduler {

  private static final Logger logger = LogManager.getLogger(PlaywellActivityThreadScheduler.class);

  // ActivityThreadStatusListeners
  private final List<ActivityThreadStatusListener> activityThreadStatusListeners = new LinkedList<>();

  public PlaywellActivityThreadScheduler() {

  }

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    IntergrationUtils.loadAndInitSubComponents(configuration.getObjectList("listeners"))
        .forEach(listenerObj -> activityThreadStatusListeners.add(
            (ActivityThreadStatusListener) listenerObj));
  }

  /**
   * Spawn的过程非常轻量，构建一个新的ActivityThread实例 最后通过ActivityThreadPool更新即可
   *
   * @param activity 活动实例
   * @param domainId DomainID
   * @param initContextVars 上下文初始化变量
   * @return 操作执行结果
   */
  @Override
  public ScheduleResult spawn(
      ActivityDefinition activityDefinition, Activity activity, String domainId,
      Map<String, Object> initContextVars) {
    ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    ActivityThreadPool activityThreadPool = integrationPlan.getActivityThreadPool();

    long now = CachedTimestamp.nowMilliseconds();

    // 构建一个新的ActivityThread实例
    final ActivityThread activityThread = new ActivityThread(
        activity,
        activityDefinition,
        domainId,
        ActivityThreadStatus.SUSPENDING,
        activityDefinition.getActionDefinitions().get(0).getName(),
        now,
        now,
        initContextVars
    );

    // 通过ActivityThreadPool进行更新
    try {
      activityThreadPool.upsertActivityThread(activityThread);
      ActivityThreadLogger.logSpawnSuccess(activityThread);
      callbackListeners(activityThread, listener -> listener.onSpawn(activityThread));
      return ScheduleResult.ok(activityThread);
    } catch (Exception e) {
      ActivityThreadLogger.logSpawnError(activityThread, e);
      callbackListeners(activityThread, listener -> listener.onScheduleError(activityThread, e));
      return ScheduleResult.fail(SpawnErrorCodes.ERROR, e.getMessage(), activityThread);
    }
  }

  @Override
  public ScheduleResult schedule(ActivityThread activityThread, Collection<Message> mailbox) {
    ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    Clock clock = integrationPlan.getClock();
    ActivityThreadPool activityThreadPool = integrationPlan.getActivityThreadPool();
    ActionManager actionManager = integrationPlan.getActionManager();

    // 检查定义版本是否可用
    ScheduleResult checkDefResult = checkDefinitionEnable(activityThreadPool, activityThread);
    if (!checkDefResult.isOk()) {
      return checkDefResult;
    }

    // 获取调度所需要的相关配置
    final Activity activity = activityThread.getActivity();
    final int activityId = activity.getId();
    final String domainId = activityThread.getDomainId();
    final EasyMap activityConfig = new EasyMap(activity.getConfig());
    final boolean pauseContinueOld = activityConfig
        .getWithConfigItem(ScheduleConfigItems.PAUSE_CONTINUE_OLD);
    if (activity.isPaused() && !pauseContinueOld) {
      return ScheduleResult.ok(activityThread);
    }
    final int maxContinuePeriods = activityConfig
        .getWithConfigItem(ScheduleConfigItems.MAX_CONTINUE_PERIODS);
    final int suspendTime = activityConfig
        .getWithConfigItem(ScheduleConfigItems.SUSPEND_TIME); // +1s

    // 只有处于suspend、waiting以及running的ActivityThread才可以被调度
    ActivityThreadStatus status = activityThread.getStatus();
    if (status != ActivityThreadStatus.SUSPENDING && status != ActivityThreadStatus.WAITING) {
      return ScheduleResult.fail(
          ScheduleErrorCodes.INVALID_STATUS,
          String.format("Invalid schedule status: [%d, %s, %d]",
              activity.getId(), activityThread.getDomainId(), status.getCode()),
          activityThread
      );
    }

    int period = 0;  // 当前调度周期
    int messageConsumed = 0; // 邮箱消息消费数目

    try {
      while (true) {
        status = activityThread.getStatus();

        // 已经达到了最大的调度周期，将ActivityThread置于Suspend状态，
        // 并注册一个时钟消息，用于到时唤醒调度器工作
        if (period++ >= maxContinuePeriods && maxContinuePeriods != -1 &&
            status == ActivityThreadStatus.RUNNING) {
          clock.registerClockMessage(ClockMessage.buildForActivity(
              CachedTimestamp.nowMilliseconds() + suspendTime,
              activityId,
              domainId,
              ClockMessageActions.SUSPEND,
              Collections.emptyMap()
          ));
          activityThread.setStatus(ActivityThreadStatus.SUSPENDING);
          activityThreadPool.upsertActivityThread(activityThread);
          ActivityThreadLogger.logBecomeSuspending(activityThread);
          callbackListeners(activityThread, listener -> listener.onStatusChange(
              ActivityThreadStatus.RUNNING, activityThread));
          return ScheduleResult.ok(activityThread);
        }

        // 如果当前是处于suspend或running状态，那么执行下一个单元
        if (status == ActivityThreadStatus.SUSPENDING || status == ActivityThreadStatus.RUNNING) {
          if (status == ActivityThreadStatus.SUSPENDING) {
            activityThread.setStatus(ActivityThreadStatus.RUNNING);
            activityThreadPool.upsertActivityThread(activityThread);
            ActivityThreadLogger.logBecomeRunning(activityThread);
            callbackListeners(activityThread, listener -> listener.onStatusChange(
                ActivityThreadStatus.SUSPENDING, activityThread));
          }

          Action action = actionManager.getActionInstance(activityThread);
          if (action instanceof SyncAction) {
            final SyncAction syncAction = (SyncAction) action;
            final ScheduleResult result = executeSyncAction(
                activityThreadPool, activityThread, syncAction);
            if (result.isOk()) {
              if (activityThread.getStatus() != ActivityThreadStatus.RUNNING) {
                return result;
              }
            } else {
              return result;
            }
          } else if (action instanceof AsyncAction) {
            final AsyncAction asyncAction = (AsyncAction) action;
            // 发出Async请求
            if (asyncAction.isAwait()) {
              makeAsyncRequest(activityThreadPool, activityThread, asyncAction);
              if (CollectionUtils.isEmpty(mailbox) || messageConsumed >= mailbox.size()) {
                return ScheduleResult.ok(activityThread);
              }
            } else {
              final ScheduleResult result = makeAsyncRequestWithNoAwait(
                  activityThreadPool, activityThread, asyncAction);
              if (result.isOk()) {
                if (ActivityThreadStatus.RUNNING != activityThread.getStatus()) {
                  return result;
                }
              } else {
                return result;
              }
            }
          }
        }
        // 如果当前是处于waiting状态，那么从邮箱中获取消息进行匹配
        if (status == ActivityThreadStatus.WAITING) {
          if (CollectionUtils.isNotEmpty(mailbox)) {
            AsyncAction action = (AsyncAction) actionManager.getActionInstance(activityThread);
            int msgIndex = 0;
            for (Message message : mailbox) {
              if (msgIndex++ >= messageConsumed) {
                messageConsumed++;
              } else {
                continue;  // 越过之前已经消费了的消息
              }

              // 忽略邮箱中的suspending消息
              if (message instanceof ClockMessage &&
                  ClockMessageActions.SUSPEND.equals(((ClockMessage) message).getAction())) {
                if (messageConsumed >= mailbox.size()) {
                  return ScheduleResult.ok(activityThread);
                } else {
                  continue;
                }
              }

              final ScheduleResult result = handleAsyncResponse(
                  activityThreadPool, activityThread, action, message);

              if (result.isOk()) {
                // 状态未达到，继续保持waiting状态接收邮箱中剩余的消息
                if (activityThread.getStatus() == ActivityThreadStatus.WAITING) {
                  if (messageConsumed >= mailbox.size()) {
                    // 消息已经消费完毕，结束循环
                    return ScheduleResult.ok(activityThread);
                  } else {
                    continue;
                  }
                }

                // 状态已经达到，停止获取消息，执行下一个单元
                if (activityThread.getStatus() == ActivityThreadStatus.RUNNING) {
                  break;
                }

                // 活动已经完成了
                if (activityThread.getStatus() == ActivityThreadStatus.FINISHED) {
                  return result;
                }
              }

              // 执行失败
              if (result.isFail()) {
                return result;
              }
            }
          } else {
            // 邮箱为空
            return ScheduleResult.ok(activityThread);
          }
        }
      }
    } catch (ActionRuntimeException e) {
      markActivityThreadFailure(activityThreadPool, activityThread, e.getErrorCode());
      ActivityThreadLogger.logScheduleError(activityThread, e.getErrorCode(), e.getMessage());
      return ScheduleResult.fail(e.getErrorCode(), e.getMessage(), activityThread);
    } catch (ActivityThreadRuntimeException e) {
      markActivityThreadFailure(activityThreadPool, activityThread, e.getErrorCode());
      ActivityThreadLogger.logScheduleError(activityThread, e.getErrorCode(), e.getMessage());
      return ScheduleResult.fail(e.getErrorCode(), e.getMessage(), activityThread);
    } catch (Exception e) {
      markActivityThreadFailure(activityThreadPool, activityThread, ScheduleErrorCodes.ERROR);
      ActivityThreadLogger.logScheduleError(activityThread, e);
      return ScheduleResult.fail(ScheduleErrorCodes.ERROR, e.getMessage(), activityThread);
    }
  }

  // 执行同步的Action
  // S1. 执行execute
  // S2. 解析ctrl表达式的最终结果
  // S3. 如果是Fail，那么标记失败，返回失败
  // S4. 如果是Finish，那么标记成功，返回成功
  // S5. 如果是Goto，那么更新上下文，设置状态为RUNNING，以及设置下一步要执行的Action，返回成功
  // S6. 如果是Retry，则对当前Action进行重试
  // S7. 如果是Repair，则进入WAITING状态，并设置修复标记
  private ScheduleResult executeSyncAction(
      ActivityThreadPool activityThreadPool, ActivityThread activityThread, SyncAction syncAction) {
    final Result result = syncAction.execute();
    final ActionCtrlInfo ctrlInfo = getActionCtrlInfo(syncAction, result);
    if (ActionCtrlType.FAIL == ctrlInfo.getCtrlType()) {
      markActivityThreadFailure(activityThreadPool, activityThread, ctrlInfo.getFailureReason());
      ActivityThreadLogger.logSyncActionResult(activityThread, syncAction, result);
      return ScheduleResult.fail(result.getErrorCode(), result.getMessage(), activityThread);
    } else if (ActionCtrlType.FINISH == ctrlInfo.getCtrlType()) {
      markActivityThreadFinished(activityThreadPool, activityThread);
      ActivityThreadLogger.logSyncActionResult(activityThread, syncAction, result);
      return ScheduleResult.ok(syncAction.getActivityThread());
    } else if (ActionCtrlType.CALL == ctrlInfo.getCtrlType()) {
      markGotoNext(activityThreadPool, activityThread, ctrlInfo);
      ActivityThreadLogger.logSyncActionResult(activityThread, syncAction, result);
      return ScheduleResult.ok(activityThread);
    } else if (ActionCtrlType.RETRY == ctrlInfo.getCtrlType()) {
      retry(activityThreadPool, activityThread, (RetryActionCtrlInfo) ctrlInfo);
      ActivityThreadLogger.logRetry(activityThread);
      return ScheduleResult.ok(activityThread);
    } else if (ActionCtrlType.REPAIRING == ctrlInfo.getCtrlType()) {
      final RepairActionCtrlInfo repairActionCtrlInfo = (RepairActionCtrlInfo) ctrlInfo;
      waitingRepair(repairActionCtrlInfo.getProblem(), activityThreadPool, activityThread);
      return ScheduleResult.ok(activityThread);
    } else {
      throw new IllegalStateException(
          "Could not handle the action ctrl type: " + ctrlInfo.getCtrlType().getType());
    }
  }

  // 执行AsyncAction的异步请求部分
  // S1. 执行sendRequest
  // S2. 设置状态为WAITING，保持当前要执行的Action和上下文
  private void makeAsyncRequest(
      ActivityThreadPool activityThreadPool, ActivityThread activityThread,
      AsyncAction asyncAction) {
    final ActivityThreadStatus oldStatus = activityThread.getStatus();
    asyncAction.sendRequest();
    activityThread.setStatus(ActivityThreadStatus.WAITING);
    activityThreadPool.upsertActivityThread(activityThread);
    ActivityThreadLogger.logMakeAsyncRequest(activityThread);
    callbackListeners(activityThread,
        listener -> listener.onStatusChange(oldStatus, activityThread));
  }

  private ScheduleResult makeAsyncRequestWithNoAwait(
      ActivityThreadPool activityThreadPool, ActivityThread activityThread,
      AsyncAction asyncAction) {
    asyncAction.sendRequest();
    final ActionCtrlInfo ctrlInfo = getActionCtrlInfo(asyncAction, Result.ok());
    if (ActionCtrlType.FAIL == ctrlInfo.getCtrlType()) {
      markActivityThreadFailure(activityThreadPool, activityThread, ctrlInfo.getFailureReason());
      ActivityThreadLogger.logAsyncActionNoAwait(activityThread, asyncAction);
      return ScheduleResult.fail(
          ctrlInfo.getFailureReason(), ctrlInfo.getFailureReason(), activityThread);
    } else if (ActionCtrlType.FINISH == ctrlInfo.getCtrlType()) {
      markActivityThreadFinished(activityThreadPool, activityThread);
      ActivityThreadLogger.logAsyncActionNoAwait(activityThread, asyncAction);
      return ScheduleResult.ok(activityThread);
    } else if (ActionCtrlType.CALL == ctrlInfo.getCtrlType()) {
      markGotoNext(activityThreadPool, activityThread, ctrlInfo);
      ActivityThreadLogger.logAsyncActionNoAwait(activityThread, asyncAction);
      return ScheduleResult.ok(activityThread);
    } else {
      throw new ActivityThreadRuntimeException(
          "invalid_ctrl",
          String.format("Invalid ctrl info '%s' of AsyncAction '%s', "
                  + "the no await AsyncAction ctrl only support fail, finish, call",
              ctrlInfo.getCtrlType().getType(), asyncAction.getName())
      );
    }
  }

  // 响应AsyncAction的异步消息
  // S1. 执行handleResponse
  // S2. 解析返回结果中的控制信息
  // S3. 如果是WAITING，那么什么都不做，直接返回
  // S4. 如果是FINISH，那么标记成功，返回成功
  // S5. 如果是FAIL，那么标记失败，返回失败
  // S6. 如果是GOTO，那么更新上下文，设置状态为RUNNING，以及下一步要执行的Action
  // S7. 如果是RETRY，那么进入重试操作
  // S8. 如果是REPAIRING，则进入WAITING状态，并设置待修复标记
  private ScheduleResult handleAsyncResponse(
      ActivityThreadPool activityThreadPool, ActivityThread activityThread, AsyncAction asyncAction,
      Message message) {
    final Result result = asyncAction.handleResponse(message);
    if (result.isIgnore()) {
      // 消息被Action忽略，状态依然是waiting
      return ScheduleResult.ok(activityThread);
    }

    final ActionCtrlInfo ctrlInfo = getActionCtrlInfo(asyncAction, result);

    if (ActionCtrlType.WAITING == ctrlInfo.getCtrlType()) {
      return ScheduleResult.ok(activityThread);
    } else if (ActionCtrlType.FINISH == ctrlInfo.getCtrlType()) {
      markActivityThreadFinished(activityThreadPool, activityThread);
      ActivityThreadLogger.logReceiveAsyncResponse(
          activityThread, asyncAction, message, result);
      return ScheduleResult.ok(activityThread);
    } else if (ActionCtrlType.FAIL == ctrlInfo.getCtrlType()) {
      markActivityThreadFailure(activityThreadPool, activityThread, ctrlInfo.getFailureReason());
      ActivityThreadLogger.logReceiveAsyncResponse(
          activityThread, asyncAction, message, result);
      return ScheduleResult.fail(result.getErrorCode(), result.getMessage(), activityThread);
    } else if (ActionCtrlType.CALL == ctrlInfo.getCtrlType()) {
      markGotoNext(activityThreadPool, activityThread, ctrlInfo);
      ActivityThreadLogger.logReceiveAsyncResponse(
          activityThread, asyncAction, message, result);
      return ScheduleResult.ok(activityThread);
    } else if (ActionCtrlType.RETRY == ctrlInfo.getCtrlType()) {
      retry(activityThreadPool, activityThread, (RetryActionCtrlInfo) ctrlInfo);
      ActivityThreadLogger.logRetry(activityThread);
      return ScheduleResult.ok(activityThread);
    } else if (ActionCtrlType.REPAIRING == ctrlInfo.getCtrlType()) {
      final RepairActionCtrlInfo repairActionCtrlInfo = (RepairActionCtrlInfo) ctrlInfo;
      waitingRepair(repairActionCtrlInfo.getProblem(), activityThreadPool, activityThread);
      return ScheduleResult.ok(activityThread);
    } else {
      throw new IllegalStateException(
          "Could not handle the action ctrl type: " + ctrlInfo.getCtrlType().getType());
    }
  }

  // 解析ActionCtrlInfo
  private ActionCtrlInfo getActionCtrlInfo(Action action, Result result) {
    final ActionDefinition actionDefinition = action.getActionDefinition();
    final List<ActionCtrlCondition> ctrlConditions = actionDefinition.getCtrlConditions();
    final ScheduleArgumentRootContext rootContext = new ScheduleArgumentRootContext(
        action.getActivityThread(), result);
    final PlaywellExpressionContext expressionContext = new SpELPlaywellExpressionContext();
    expressionContext.setRootObject(rootContext);

    String ctrlString = "FAIL because no_ctrl";
    if (CollectionUtils.isNotEmpty(ctrlConditions)) {
      for (ActionCtrlCondition condition : ctrlConditions) {
        PlaywellExpression whenExpression = condition.getWhenCondition();
        // 符合条件，直接返回
        if ((boolean) whenExpression.getResult(expressionContext)) {
          ctrlString = (String) condition.getThenExpression().getResult(expressionContext);
          return ActionCtrlInfo.fromCtrlString(
              ctrlString,
              getContextVars(condition.getContextVars(), action.getActivityThread(), result)
          );
        }
      }
    }

    final PlaywellExpression defaultCtrlExpression = actionDefinition.getDefaultCtrlExpression();
    if (defaultCtrlExpression == null) {
      // 如果不存在默认的，则在结果中获取$ctrl字段，如果结果里面也没有，那就只能fail了
      final EasyMap resultData = result.getData();
      final String resultCtrlString = resultData.getString("$ctrl", "");
      if (StringUtils.isNotEmpty(resultCtrlString)) {
        ctrlString = resultCtrlString;
      }
    } else {
      // 存在默认的ctrl设置，则直接计算默认的表达式
      ctrlString = (String) defaultCtrlExpression.getResult(expressionContext);
    }

    // 解析得到最终的ActionCtrlInfo对象
    return ActionCtrlInfo.fromCtrlString(
        ctrlString,
        getContextVars(
            actionDefinition.getDefaultContextVars(),
            action.getActivityThread(),
            result
        )
    );
  }

  private Map<String, Object> getContextVars(
      Map<String, PlaywellExpression> contextExpressions, ActivityThread activityThread,
      Result result) {
    if (MapUtils.isNotEmpty(contextExpressions)) {
      final Map<String, Object> contextVars = new HashMap<>(contextExpressions.size());
      final PlaywellExpressionContext expressionContext = new SpELPlaywellExpressionContext();
      expressionContext.setRootObject(new ScheduleArgumentRootContext(activityThread, result));
      for (Map.Entry<String, PlaywellExpression> entry : contextExpressions.entrySet()) {
        contextVars.put(entry.getKey(), entry.getValue().getResult(expressionContext));
      }
      return contextVars;
    }
    return Collections.emptyMap();
  }

  /**
   * 处理系统消息
   * <p>
   * Steps:
   * <ol>
   * <li>检索出mailbox中消息，最终选择一个命令执行
   * 如果有kill，直接执行kill； 如果当前是continue，并且之前有pause，那么抵消； 如果当前是pause，并且之前有continue，那么是pause
   * </li>
   * <li>根据从mailbox中选择出来的command，执行具体的调度操作</li>
   * </ol>
   *
   * @param activityThread 要调度执行的ActivityThread
   * @param mailbox 系统消息mailbox
   * @return 处理结果
   */
  @Override
  public ScheduleResult scheduleWithCtrlMessages(
      ActivityThread activityThread, Collection<ActivityThreadCtrlMessage> mailbox) {
    if (CollectionUtils.isEmpty(mailbox)) {
      return ScheduleResult.ok(activityThread);
    }

    ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    ActivityThreadPool activityThreadPool = integrationPlan.getActivityThreadPool();

    // 检查定义版本是否可用
    ScheduleResult checkDefResult = checkDefinitionEnable(activityThreadPool, activityThread);
    if (!checkDefResult.isOk()) {
      return checkDefResult;
    }

    String command = "";
    Map<String, Object> args = Collections.emptyMap();
    for (ActivityThreadCtrlMessage ctrlMessage : mailbox) {
      if (ActivityThreadCtrlMessage.Commands.KILL.equals(ctrlMessage.getCommand())) {
        command = ActivityThreadCtrlMessage.Commands.KILL;
        args = ctrlMessage.getArgs();
        break;
      }

      command = ctrlMessage.getCommand();
      args = ctrlMessage.getArgs();
    }

    if (ActivityThreadCtrlMessage.Commands.KILL.equals(command)) {
      return killActivityThread(activityThreadPool, activityThread);
    } else if (ActivityThreadCtrlMessage.Commands.PAUSE.equals(command)) {
      return pauseActivityThread(activityThreadPool, activityThread);
    } else if (ActivityThreadCtrlMessage.Commands.CONTINUE.equals(command)) {
      return continueActivityThread(activityThreadPool, activityThread);
    } else if (ActivityThreadCtrlMessage.Commands.REPAIR.equals(command)) {
      return repairActivityThread(
          RepairArguments.fromArgs(args), activityThread, activityThreadPool);
    } else {
      // Nothing to do
      return new ScheduleResult(
          Result.STATUS_OK,
          "",
          "",
          activityThread
      );
    }
  }

  private ScheduleResult killActivityThread(
      ActivityThreadPool activityThreadPool, ActivityThread activityThread) {
    final ActivityThreadStatus currentStatus = activityThread.getStatus();
    // 判断ActivityThread是否已经被杀死
    if (currentStatus == ActivityThreadStatus.KILLED) {
      return ScheduleResult.fail(
          ScheduleErrorCodes.ALREADY_KILLED,
          String.format(
              "The ActivityThread[%d, %s] has already been killed",
              activityThread.getActivity().getId(),
              activityThread.getDomainId()
          ),
          activityThread
      );
    }
    activityThread.setStatus(ActivityThreadStatus.KILLED);
    activityThreadPool.upsertActivityThread(activityThread);
    ActivityThreadLogger.logActivityThreadKilled(activityThread);
    callbackListeners(activityThread,
        listener -> listener.onStatusChange(currentStatus, activityThread));
    return ScheduleResult.ok(activityThread);
  }

  private ScheduleResult pauseActivityThread(
      ActivityThreadPool activityThreadPool, ActivityThread activityThread) {
    // 获取当前状态，只有处于suspend和waiting状态的ActivityThread才可以被暂停
    final ActivityThreadStatus currentStatus = activityThread.getStatus();
    if (currentStatus == ActivityThreadStatus.SUSPENDING ||
        currentStatus == ActivityThreadStatus.WAITING ||
        currentStatus == ActivityThreadStatus.RUNNING) {
      // 在上下文中记录下暂停之前的ActivityThread状态，以供重新运行时参照
      activityThread
          .putContextVar(ScheduleContextVars.BEFORE_PAUSE_STATUS, currentStatus.getCode());
      activityThread.setStatus(ActivityThreadStatus.PAUSED);
      activityThreadPool.upsertActivityThread(activityThread);
      ActivityThreadLogger.logActivityThreadPaused(activityThread);
      callbackListeners(activityThread,
          listener -> listener.onStatusChange(currentStatus, activityThread));
      return ScheduleResult.ok(activityThread);
    } else {
      return ScheduleResult.fail(
          ScheduleErrorCodes.INVALID_STATUS,
          String.format(
              "Could not pause the ActivityThread[%d, %s], invalid status: %s",
              activityThread.getActivity().getId(),
              activityThread.getDomainId(),
              activityThread.getStatus().getStatus()
          ),
          activityThread
      );
    }
  }

  private ScheduleResult continueActivityThread(
      ActivityThreadPool activityThreadPool, ActivityThread activityThread) {
    // 获取当前状态，只有处于paused状态的ActivityThread才可以被继续执行
    final ActivityThreadStatus currentStatus = activityThread.getStatus();
    if (currentStatus != ActivityThreadStatus.PAUSED) {
      return ScheduleResult.fail(
          ScheduleErrorCodes.INVALID_STATUS,
          String.format(
              "Could not continue the ActivityThread[%d, %s], invalid status: %s",
              activityThread.getActivity().getId(),
              activityThread.getDomainId(),
              activityThread.getStatus().getStatus()
          ),
          activityThread
      );
    }

    EasyMap context = new EasyMap(activityThread.getContext());
    int bpsCode = context.getInt(ScheduleContextVars.BEFORE_PAUSE_STATUS, -1);

    // 上下文中找不到BPS变量
    if (bpsCode == -1) {
      this.markActivityThreadFailure(
          activityThreadPool, activityThread, ScheduleErrorCodes.BPS_NOT_FOUND);

      final String message = String.format(
          "Could not continue the ActivityThread[%d, %s], bps not found",
          activityThread.getActivity().getId(),
          activityThread.getDomainId()
      );
      ActivityThreadLogger.logActivityThreadContinueFailure(
          activityThread,
          ScheduleErrorCodes.BPS_NOT_FOUND,
          message
      );

      return ScheduleResult.fail(
          ScheduleErrorCodes.BPS_NOT_FOUND,
          message,
          activityThread
      );
    }

    Optional<ActivityThreadStatus> bpStatusOpt = ActivityThreadStatus.valueOfByCode(bpsCode);
    if (!bpStatusOpt.isPresent()) {
      this.markActivityThreadFailure(
          activityThreadPool, activityThread, ScheduleErrorCodes.INVALID_BPS);

      final String message = String.format(
          "Could not continue the ActivityThread[%d, %s], invalid bps var: %d",
          activityThread.getActivity().getId(),
          activityThread.getDomainId(),
          bpsCode
      );
      ActivityThreadLogger.logActivityThreadContinueFailure(
          activityThread,
          ScheduleErrorCodes.INVALID_BPS,
          message
      );

      return ScheduleResult.fail(
          ScheduleErrorCodes.INVALID_BPS,
          message,
          activityThread
      );
    }
    ActivityThreadStatus bps = bpStatusOpt.get();

    // 如果之前的状态是等待，那么继续等待好了
    if (bps == ActivityThreadStatus.WAITING) {
      activityThread.setStatus(ActivityThreadStatus.WAITING);
      activityThreadPool.upsertActivityThread(activityThread);
      ActivityThreadLogger.logActivityThreadContinueSuccess(activityThread);
      callbackListeners(
          activityThread,
          listener -> listener.onStatusChange(ActivityThreadStatus.WAITING, activityThread)
      );
      return ScheduleResult.ok(activityThread);
    }
    // 如果之前的状态是挂起，那么需要继续执行
    else if (bps == ActivityThreadStatus.SUSPENDING) {
      ActivityThreadLogger.logActivityThreadContinueSuccess(activityThread);
      return schedule(activityThread, Collections.emptyList());
    } else {
      this.markActivityThreadFailure(
          activityThreadPool, activityThread, ScheduleErrorCodes.INVALID_BPS);

      final String message = String.format(
          "Could not continue the ActivityThread[%d, %s], invalid bps var: %d",
          activityThread.getActivity().getId(),
          activityThread.getDomainId(),
          bpsCode
      );
      ActivityThreadLogger.logActivityThreadContinueFailure(
          activityThread, ScheduleErrorCodes.INVALID_BPS, message);

      return ScheduleResult.fail(
          ScheduleErrorCodes.INVALID_BPS,
          message,
          activityThread
      );
    }
  }

  private ScheduleResult repairActivityThread(
      RepairArguments repairArguments, ActivityThread activityThread,
      ActivityThreadPool activityThreadPool) {
    if (!isInRepairing(activityThread)) {
      return ScheduleResult.fail(
          ScheduleErrorCodes.INVALID_STATUS,
          String.format(
              "Could not repair the ActivityThread[%d, %s], invalid status: %s",
              activityThread.getActivity().getId(),
              activityThread.getDomainId(),
              activityThread.getStatus().getStatus()
          ),
          activityThread
      );
    }

    final Map<String, Object> repairContextVars = repairArguments.getContextVars();
    final ActivityDefinition activityDefinition = activityThread.getActivityDefinition();

    // 处理修复指令
    final RepairCtrl repairCtrl = repairArguments.getCtrl();
    if (repairCtrl == RepairCtrl.GOTO) {
      // 跳转到指定的Action执行
      final String targetAction = repairArguments.getGotoAction();
      if (activityDefinition.getActionDefinitionByName(targetAction) == null) {
        return ScheduleResult.fail(
            ScheduleErrorCodes.ACTION_NOT_FOUND,
            String.format(
                "Could not repair the ActivityThread[%d, %s], invalid status: %s",
                activityThread.getActivity().getId(),
                activityThread.getDomainId(),
                activityThread.getStatus().getStatus()
            ),
            activityThread
        );
      }

      cleanRepairMark(activityThread);
      if (MapUtils.isNotEmpty(repairContextVars)) {
        activityThread.putContextVars(repairContextVars);
      }
      activityThread.setStatus(ActivityThreadStatus.SUSPENDING);
      activityThread.setCurrentAction(targetAction);
      return schedule(activityThread, Collections.emptyList());
    } else if (repairCtrl == RepairCtrl.RETRY) {
      // 重试当前操作
      cleanRepairMark(activityThread);
      if (MapUtils.isNotEmpty(repairContextVars)) {
        activityThread.putContextVars(repairContextVars);
      }
      activityThread.setStatus(ActivityThreadStatus.SUSPENDING);
      return schedule(activityThread, Collections.emptyList());
    } else if (repairCtrl == RepairCtrl.WAITING) {
      final ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
      final ActionManager actionManager = integrationPlan.getActionManager();
      final Action action = actionManager.getActionInstance(activityThread);
      if (action instanceof SyncAction) {
        return ScheduleResult.fail(
            ScheduleErrorCodes.INVALID_STATUS,
            String.format(
                "Could not repair the ActivityThread[%d, %s], "
                    + "current action %s is a SyncAction, "
                    + "could not make it waiting",
                activityThread.getActivity().getId(),
                activityThread.getDomainId(),
                activityThread.getCurrentAction()
            ),
            activityThread
        );
      }

      // 继续保持WAITING状态
      cleanRepairMark(activityThread);
      if (MapUtils.isNotEmpty(repairContextVars)) {
        activityThread.putContextVars(repairContextVars);
      }
      activityThreadPool.upsertActivityThread(activityThread);
      return ScheduleResult.ok(activityThread);
    } else {
      // 未知的修复指令
      return ScheduleResult.fail(
          ScheduleErrorCodes.UNKNOWN_REPAIR_CTRL,
          String.format(
              "Could not repair the ActivityThread[%d, %s], invalid repair ctrl: %s",
              activityThread.getActivity().getId(),
              activityThread.getDomainId(),
              activityThread.getStatus().getStatus()
          ),
          activityThread
      );
    }
  }

  private boolean isInRepairing(ActivityThread activityThread) {
    final EasyMap context = new EasyMap(activityThread.getContext());
    final boolean inRepairing = context.getBoolean(ScheduleContextVars.IN_REPAIRING, false);
    return ActivityThreadStatus.WAITING == activityThread.getStatus() && inRepairing;
  }

  private void markActivityThreadFailure(
      ActivityThreadPool activityThreadPool, ActivityThread activityThread, String failureReason) {
    final ActivityThreadStatus oldStatus = activityThread.getStatus();
    cleanRetryCountVar(activityThread);
    cleanRepairMark(activityThread);
    activityThread.setStatus(ActivityThreadStatus.FAIL);
    if (StringUtils.isNotEmpty(failureReason)) {
      activityThread.putContextVar(ScheduleContextVars.FAIL_REASON, failureReason);
    }
    activityThreadPool.upsertActivityThread(activityThread);
    notifyMonitorsWithFailureMsg(activityThread, failureReason);
    callbackListeners(activityThread,
        listener -> listener.onStatusChange(oldStatus, activityThread));
  }

  private void markActivityThreadFinished(
      ActivityThreadPool activityThreadPool, ActivityThread activityThread) {
    final ActivityThreadStatus oldStatus = activityThread.getStatus();
    cleanRetryCountVar(activityThread);
    cleanRepairMark(activityThread);
    activityThread.setStatus(ActivityThreadStatus.FINISHED);
    activityThreadPool.upsertActivityThread(activityThread);
    notifyMonitorsWithFinishedMsg(activityThread);
    callbackListeners(activityThread,
        listener -> listener.onStatusChange(oldStatus, activityThread));
  }

  private void markGotoNext(
      ActivityThreadPool activityThreadPool,
      ActivityThread activityThread,
      ActionCtrlInfo ctrlInfo) {
    final ActivityThreadStatus oldStatus = activityThread.getStatus();
    if (MapUtils.isNotEmpty(ctrlInfo.getContextVars())) {
      activityThread.putContextVars(ctrlInfo.getContextVars());
    }

    cleanRetryCountVar(activityThread);
    cleanRepairMark(activityThread);

    final String nextStep = ctrlInfo.getNextStep();
    activityThread.setStatus(ActivityThreadStatus.RUNNING);
    activityThread.setCurrentAction(nextStep);
    activityThreadPool.upsertActivityThread(activityThread);
    callbackListeners(activityThread,
        listener -> listener.onStatusChange(oldStatus, activityThread));
  }

  private void retry(
      ActivityThreadPool activityThreadPool,
      ActivityThread activityThread,
      RetryActionCtrlInfo ctrlInfo) {

    final EasyMap context = new EasyMap(activityThread.getContext());
    final String retryCountVar = String.format("$%s.retry", activityThread.getCurrentAction());
    final int retryCount = context.getInt(retryCountVar, 0);

    // 重试次数达到了上限
    if (retryCount >= ctrlInfo.getCount()) {
      final ActionCtrlInfo failureCtrl = ctrlInfo.getRetryFailureAction();
      if (failureCtrl.getCtrlType() == ActionCtrlType.FAIL) {
        markActivityThreadFailure(activityThreadPool, activityThread,
            failureCtrl.getFailureReason());
      } else if (failureCtrl.getCtrlType() == ActionCtrlType.FINISH) {
        markActivityThreadFinished(activityThreadPool, activityThread);
      } else if (failureCtrl.getCtrlType() == ActionCtrlType.CALL) {
        markGotoNext(activityThreadPool, activityThread, failureCtrl);
      }
    } else {
      if (MapUtils.isNotEmpty(ctrlInfo.getContextVars())) {
        activityThread.putContextVars(ctrlInfo.getContextVars());
      }
      activityThread.putContextVar(retryCountVar, retryCount + 1);
      activityThread.setStatus(ActivityThreadStatus.RUNNING);
      activityThreadPool.upsertActivityThread(activityThread);
    }
  }

  private void cleanRetryCountVar(ActivityThread activityThread) {
    final String retryCountVar = String.format("$%s.retry", activityThread.getCurrentAction());
    activityThread.removeContextVar(retryCountVar);
  }

  private void waitingRepair(
      String problem, ActivityThreadPool activityThreadPool, ActivityThread activityThread) {
    activityThread.setStatus(ActivityThreadStatus.WAITING);
    activityThread.putContextVar(ScheduleContextVars.IN_REPAIRING, true);
    activityThreadPool.upsertActivityThread(activityThread);
    ActivityThreadLogger.logRepair(activityThread);
    callbackListeners(activityThread,
        listener -> listener.onRepair(activityThread, problem));
    notifyMonitorsWithRepairMsg(problem, activityThread);
  }

  private void cleanRepairMark(ActivityThread activityThread) {
    activityThread.removeContextVar(ScheduleContextVars.IN_REPAIRING);
  }

  // 通知monitor进行修复
  private void notifyMonitorsWithRepairMsg(String problem, ActivityThread activityThread) {
    final ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final Collection<MonitorInfo> allMonitorInfo = getAllMonitorInfo(activityThread);
    allMonitorInfo.forEach(monitorInfo -> {
      try {
        monitorInfo.getMessageBus().write(new NeedRepairMessage(
            CachedTimestamp.nowMilliseconds(),
            activityThread,
            integrationPlan.getActivityRunner().getServiceName(),
            monitorInfo.getMonitorService().getName(),
            problem
        ));
      } catch (MessageBusNotAvailableException e) {
        logger.error("notify monitor error!", e);
      }
    });
  }

  private void notifyMonitorsWithFinishedMsg(ActivityThread activityThread) {
    final ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final Collection<MonitorInfo> allMonitorInfo = getAllMonitorInfo(activityThread);
    allMonitorInfo.forEach(monitorInfo -> {
      if (monitorInfo.isOnlyRepair()) {
        return;
      }

      try {
        monitorInfo.getMessageBus().write(new FinishedMessage(
            CachedTimestamp.nowMilliseconds(),
            activityThread,
            integrationPlan.getActivityRunner().getServiceName(),
            monitorInfo.getMonitorService().getName()
        ));
      } catch (MessageBusNotAvailableException e) {
        logger.error("Notify monitor error!", e);
      }
    });
  }

  private void notifyMonitorsWithFailureMsg(ActivityThread activityThread, String reason) {
    final ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final Collection<MonitorInfo> allMonitorInfo = getAllMonitorInfo(activityThread);
    allMonitorInfo.forEach(monitorInfo -> {
      if (monitorInfo.isOnlyRepair()) {
        return;
      }

      try {
        monitorInfo.getMessageBus().write(new FailureMessage(
            CachedTimestamp.nowMilliseconds(),
            activityThread,
            integrationPlan.getActivityRunner().getServiceName(),
            monitorInfo.getMonitorService().getName(),
            reason
        ));
      } catch (MessageBusNotAvailableException e) {
        logger.error("Notify monitor error!", e);
      }
    });
  }

  private List<MonitorInfo> getAllMonitorInfo(ActivityThread activityThread) {
    final Activity activity = activityThread.getActivity();
    final EasyMap configuration = new EasyMap(activity.getConfig());
    final List<EasyMap> allMonitorData = configuration.getSubArgumentsList(
        ScheduleConfigItems.MONITORS.getKey());

    if (CollectionUtils.isEmpty(allMonitorData)) {
      return Collections.emptyList();
    }

    return allMonitorData.stream().map(MonitorInfo::fromConfigData).collect(Collectors.toList());
  }

  // 回调所有的ActivityThreadStatusListener
  private void callbackListeners(ActivityThread activityThread,
      Consumer<ActivityThreadStatusListener> callback) {
    if (CollectionUtils.isEmpty(activityThreadStatusListeners)) {
      return;
    }
    activityThreadStatusListeners.forEach(activityThreadStatusListener -> {
      try {
        callback.accept(activityThreadStatusListener);
      } catch (Exception e) {
        logger.error(
            String.format("Callback ActivityThreadStatusListener [%d, %s] error.",
                activityThread.getActivity().getId(), activityThread.getDomainId()), e);
      }
    });
  }

  // 检查定义版本是否可用
  private ScheduleResult checkDefinitionEnable(
      ActivityThreadPool activityThreadPool, ActivityThread activityThread) {
    if (!activityThread.getActivityDefinition().isEnable()) {
      markActivityThreadFailure(
          activityThreadPool, activityThread, ScheduleErrorCodes.DEFINITION_NOT_ENABLE);

      String message = String.format(
          "The activity definition is not enable, name: '%s', version: '%s'",
          activityThread.getActivityDefinition().getName(),
          activityThread.getActivityDefinition().getVersion());

      ActivityThreadLogger.logScheduleError(
          activityThread, ScheduleErrorCodes.DEFINITION_NOT_ENABLE, message);

      return ScheduleResult.fail(
          ScheduleErrorCodes.DEFINITION_NOT_ENABLE,
          message,
          activityThread
      );
    }
    return ScheduleResult.ok(activityThread);
  }

  private interface ClockMessageActions {

    String SUSPEND = "$suspend";
  }

  /**
   * 辅助调度的内置上下文变量
   */
  public interface ScheduleContextVars {

    String BEFORE_PAUSE_STATUS = "_BPS";

    String FAIL_REASON = "_FR";

    String IN_REPAIRING = "_RP";
  }

  /**
   * Spawn操作错误码
   */
  public interface SpawnErrorCodes {

    String ERROR = "error";

  }

  /**
   * 对ActivityThread进行Schedule时的错误码
   */
  public interface ScheduleErrorCodes {

    String ERROR = "error";

    String INVALID_STATUS = "invalid_status";

    String ALREADY_KILLED = "already_killed";

    String BPS_NOT_FOUND = "bps_not_found";

    String INVALID_BPS = "invalid_bps";

    String DEFINITION_NOT_ENABLE = "def_not_enable";

    String ACTION_NOT_FOUND = "action_not_found";

    String UNKNOWN_REPAIR_CTRL = "unknown_repair_ctrl";
  }
}
