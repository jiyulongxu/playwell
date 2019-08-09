package playwell.action.builtin;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.collections4.MapUtils;
import playwell.action.ActionInstanceBuilder;
import playwell.action.ActionManager;
import playwell.action.ActionRuntimeException;
import playwell.action.AsyncAction;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ActivityThreadPool;
import playwell.activity.thread.ScheduleArgumentRootContext;
import playwell.clock.CachedTimestamp;
import playwell.clock.Clock;
import playwell.clock.ClockMessage;
import playwell.common.Result;
import playwell.common.argument.Argument;
import playwell.common.argument.ExpressionArgument;
import playwell.common.argument.ListArgument;
import playwell.common.argument.MapArgument;
import playwell.common.exception.BuildComponentException;
import playwell.common.expression.PlaywellExpressionContext;
import playwell.common.expression.spel.SpELPlaywellExpressionContext;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.message.Message;
import playwell.message.ServiceResponseMessage;

/**
 * <p>ConcurrentAction可以用于对多个AsyncAction执行并发请求，并对响应结果进行集中处理来决定下一步的走向</p>
 * eg. <br/>
 *
 * <pre>
 *   - name: concurrent
 *     type: concurrent
 *     args:
 *       actions:
 *
 *         - name: str("action_a")
 *           result_handle:
 *             - when: resultOk()
 *               context_vars:
 *                 ok_num: var("ok_num", 0) + 1
 *
 *         - name: str("action_b")
 *
 *       default_result_handle:
 *
 *         - when: resultOk()
 *           context_vars:
 *             ok_num: var("ok_num", 0) + 1
 *
 *       ctrl:
 *
 *         - when: var("ok_num", 0) >= 3
 *           then: call("next_action")
 *
 *         - after: timestamp("10 seconds")
 *           then: failBecause("timeout")
 *
 * </pre>
 *
 * @author chihongze@gmail.com
 */
public class ConcurrentAction extends AsyncAction {

  public static final String TYPE = "concurrent";

  public static final ActionInstanceBuilder BUILDER = ConcurrentAction::new;

  public static final Consumer<Argument> ARG_SPEC = argument -> {
    if (!Argument.isMapArgument(argument)) {
      throw new BuildComponentException(
          "Invalid argument format, the concurrent action only accept Map argument.");
    }

    final Map<String, Argument> mapArgumentsDef = ((MapArgument) argument).getArgs();
    if (!mapArgumentsDef.containsKey(ArgNames.ACTIONS)) {
      throw new BuildComponentException(
          "Invalid argument format, the concurrent action required actions argument");
    }
    if (!Argument.isListArgument(mapArgumentsDef.get(ArgNames.ACTIONS))) {
      throw new BuildComponentException(
          "Invalid argument format, the actions argument of the concurrent action must be List type");
    }

    if (mapArgumentsDef.containsKey(ArgNames.DEFAULT_RESULT_HANDLE)) {
      if (!Argument.isListArgument(mapArgumentsDef.get(ArgNames.DEFAULT_RESULT_HANDLE))) {
        throw new BuildComponentException(
            "Invalid argument format, the default_result_handle argument of "
                + "the concurrent action must be List type");
      }
    }

    if (!mapArgumentsDef.containsKey(ArgNames.CTRL)) {
      throw new BuildComponentException(
          "Invalid argument format, the concurrent action required ctrl argument");
    }
    if (!Argument.isListArgument(mapArgumentsDef.get(ArgNames.CTRL))) {
      throw new BuildComponentException(
          "Invalid argument format, the ctrl argument of concurrent action must be List type");
    }
  };

  public ConcurrentAction(ActivityThread activityThread) {
    super(activityThread);
  }

  /**
   * 查找所有服务，并依次对所有服务发出请求 如果ctrl中有设置after，则需要设置超时
   */
  @Override
  public void sendRequest() {
    final ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final ActionManager actionManager = integrationPlan.getActionManager();
    final Clock clock = integrationPlan.getClock();

    // 一次对所有的Action发送请求
    Map<String, Argument> arguments = ((MapArgument) getActionDefinition().getArguments())
        .getArgs();
    ListArgument actions = (ListArgument) arguments.get(ArgNames.ACTIONS);
    for (Argument arg : actions.getArgs()) {
      Map<String, Argument> actionArg = ((MapArgument) arg).getArgs();
      ExpressionArgument nameArg = (ExpressionArgument) actionArg.get(ArgNames.NAME);
      String name = (String) nameArg.getValue(getArgExpressionContext());
      activityThread.setCurrentAction(name);
      AsyncAction action = (AsyncAction) actionManager.getActionInstance(activityThread);
      action.sendRequest();
    }
    activityThread.setCurrentAction(this.name);

    // 找到超时处理
    ListArgument ctrlList = (ListArgument) arguments.get(ArgNames.CTRL);
    for (Argument arg : ctrlList.getArgs()) {
      Map<String, Argument> ctrlArg = ((MapArgument) arg).getArgs();
      if (ctrlArg.containsKey(ArgNames.AFTER)) {
        final ExpressionArgument afterExpression = (ExpressionArgument) ctrlArg.get(ArgNames.AFTER);
        final long timestamp = (long) afterExpression.getValue(getArgExpressionContext());
        clock.registerClockMessage(ClockMessage.buildForActivity(
            CachedTimestamp.nowMilliseconds() + timestamp,
            activity.getId(),
            activityThread.getDomainId(),
            this.name
        ));
        break;
      }
    }
  }

  @Override
  public Result handleResponse(Message message) {
    final ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final ActionManager actionManager = integrationPlan.getActionManager();
    final ActivityThreadPool activityThreadPool = integrationPlan.getActivityThreadPool();

    final Map<String, Argument> allArguments = ((MapArgument) getActionDefinition()
        .getArguments()).getArgs();
    final ListArgument ctrlArguments = (ListArgument) allArguments.get(ArgNames.CTRL);
    final ListArgument actionArguments = (ListArgument) allArguments.get(ArgNames.ACTIONS);
    final ListArgument defaultResultHandle = ((ListArgument) allArguments.get(
        ArgNames.DEFAULT_RESULT_HANDLE));

    // 处理超时消息
    if (message instanceof ClockMessage) {
      final ClockMessage clockMessage = (ClockMessage) message;
      if (clockMessage.getAction().equals(getName()) &&
          clockMessage.getTimestamp() >= getActivityThread().getCreatedOn()) {
        for (Argument arg : ctrlArguments.getArgs()) {
          final Map<String, Argument> ctrlArg = ((MapArgument) arg).getArgs();
          if (ctrlArg.containsKey(ArgNames.AFTER)) {
            ExpressionArgument thenArg = (ExpressionArgument) ctrlArg.get(ArgNames.THEN);
            if (ctrlArg.containsKey(ArgNames.CONTEXT_VARS)) {
              activityThread.putContextVars(((MapArgument) ctrlArg.get(ArgNames.CONTEXT_VARS))
                  .getValueMap(getArgExpressionContext()));
            }
            return Result.okWithData(
                Collections.singletonMap("$ctrl", thenArg.getValue(getArgExpressionContext())));
          }
        }
      }
    }

    // 处理服务响应消息
    if (message instanceof ServiceResponseMessage) {
      final ServiceResponseMessage responseMessage = (ServiceResponseMessage) message;
      final String actionName = responseMessage.getAction();

      // 查找目标Action
      ListArgument actionResultHandle = null;
      for (Argument arg : actionArguments.getArgs()) {
        final Map<String, Argument> actionArgs = ((MapArgument) arg).getArgs();
        final ExpressionArgument nameExpression = (ExpressionArgument) actionArgs
            .get(ArgNames.NAME);
        final String name = (String) nameExpression.getValue(getArgExpressionContext());
        if (actionName.equals(name)) {
          if (actionArgs.containsKey(ArgNames.RESULT_HANDLE)) {
            // 优先获取action自己带的result_handle
            actionResultHandle = (ListArgument) actionArgs.get(ArgNames.RESULT_HANDLE);
          } else if (defaultResultHandle != null) {
            actionResultHandle = defaultResultHandle;
          } else {
            throw new ActionRuntimeException(
                CommonRuntimeErrorCodes.INVALID_ARGUMENT,
                String.format("There is no result handle in sub action: %s", actionName)
            );
          }
        }
      }

      if (actionResultHandle != null) {
        activityThread.setCurrentAction(actionName);
        final AsyncAction asyncAction = (AsyncAction) actionManager
            .getActionInstance(activityThread);
        final Result result = asyncAction.handleResponse(message);
        final ScheduleArgumentRootContext rootObj = new ScheduleArgumentRootContext(
            activityThread, result);
        final PlaywellExpressionContext context = new SpELPlaywellExpressionContext();
        context.setRootObject(rootObj);

        boolean matched = false;
        Map<String, Object> defaultContextVars = null;

        for (Argument arg : actionResultHandle.getArgs()) {
          Map<String, Argument> handleArg = ((MapArgument) arg).getArgs();

          if (handleArg.containsKey(ArgNames.WHEN) && handleArg
              .containsKey(ArgNames.CONTEXT_VARS)) {
            matched = (boolean) ((ExpressionArgument) handleArg.get(ArgNames.WHEN))
                .getValue(context);

            if (matched) {
              activityThread.putContextVars(
                  ((MapArgument) handleArg.get(ArgNames.CONTEXT_VARS)).getValueMap(context));
              break;
            }
          } else if (handleArg.containsKey(ArgNames.DEFAULT)) {
            defaultContextVars = ((MapArgument) handleArg.get(ArgNames.DEFAULT))
                .getValueMap(context);
          }
        }

        activityThread.setCurrentAction(name);

        // 处理result_handle中的default
        if (!matched) {
          if (MapUtils.isNotEmpty(defaultContextVars)) {
            activityThread.putContextVars(defaultContextVars);
          }
        }

        // 依次处理ctrl条件
        for (Argument arg : ctrlArguments.getArgs()) {
          Map<String, Argument> ctrlArg = ((MapArgument) arg).getArgs();
          if (!ctrlArg.containsKey(ArgNames.WHEN)) {
            continue;  // 略过非when条件
          }
          ExpressionArgument whenExpression = (ExpressionArgument) ctrlArg.get(ArgNames.WHEN);
          if ((boolean) whenExpression.getValue(getArgExpressionContext())) {
            if (ctrlArg.containsKey(ArgNames.CONTEXT_VARS)) {
              activityThread.putContextVars(((MapArgument) ctrlArg.get(ArgNames.CONTEXT_VARS))
                  .getValueMap(getArgExpressionContext()));
            }
            String thenString = (String) ((ExpressionArgument) ctrlArg.get(ArgNames.THEN))
                .getValue(getArgExpressionContext());

            return Result.okWithData(Collections.singletonMap("$ctrl", thenString));
          }
        }
      }
    }

    activityThreadPool.upsertActivityThread(activityThread);
    return Result.ignore();
  }

  interface ArgNames {

    String ACTIONS = "actions";

    String NAME = "name";

    String RESULT_HANDLE = "result_handle";

    String WHEN = "when";

    String CONTEXT_VARS = "context_vars";

    String THEN = "then";

    String DEFAULT = "default";

    String DEFAULT_RESULT_HANDLE = "default_result_handle";

    String CTRL = "ctrl";

    String AFTER = "after";

  }
}
