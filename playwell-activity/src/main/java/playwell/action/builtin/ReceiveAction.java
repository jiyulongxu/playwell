package playwell.action.builtin;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import playwell.action.ActionInstanceBuilder;
import playwell.action.AsyncAction;
import playwell.activity.thread.ActivityThread;
import playwell.clock.CachedTimestamp;
import playwell.clock.Clock;
import playwell.clock.ClockMessage;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.common.argument.Argument;
import playwell.common.argument.ExpressionArgument;
import playwell.common.argument.ListArgument;
import playwell.common.argument.MapArgument;
import playwell.common.exception.BuildComponentException;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.message.Message;

/**
 * <p>
 * ReceiveAction会使ActivityThread处于WAITING状态，接收事件，并对事件进行判断， 如果事件满足条件，则执行指定的操作。与Erlang的receive语句相似，不同之处在于，Erlang的receive当消息不匹配的时候
 * 也会继续保存在邮箱中，而ReceiveAction则会将当前消息循环不匹配的消息全部丢弃
 * </p>
 *
 * <pre>
 *   - name: receive
 *     type: receive
 *     args:
 *       - when: eventAttr("a") == 1
 *         then: call("action_1")
 *         context_vars:
 *           a: 1
 *       - when: eventAttr("a") == 2
 *         then: call("action_2")
 *         context_vars:
 *           a: 2
 *       - after: timestamp("1 day")
 *         then: failBecause("timeout")
 * </pre>
 */
public class ReceiveAction extends AsyncAction {

  public static final String TYPE = "receive";

  public static final ActionInstanceBuilder BUILDER = ReceiveAction::new;

  public static final Consumer<Argument> ARG_SPEC = argument -> {
    if (!Argument.isListArgument(argument)) {
      throw new BuildComponentException(
          "Invalid argument format, the receive action only accept List<Map> argument");
    }
    final ListArgument listDefArguments = (ListArgument) argument;
    for (Argument condArg : listDefArguments.getArgs()) {
      if (!(condArg instanceof MapArgument)) {
        throw new BuildComponentException(
            String.format(
                "Invalid condition format: %s, The condition of receive action required map",
                condArg
            )
        );
      }

      final Map<String, Argument> mapCondDef = ((MapArgument) condArg).getArgs();
      if (mapCondDef.containsKey(ArgNames.WHEN) || mapCondDef.containsKey(ArgNames.AFTER)) {
        if (!mapCondDef.containsKey(ArgNames.THEN)) {
          throw new BuildComponentException(
              String.format(
                  "Invalid condition format: %s, The condition of receive action required then argument",
                  condArg
              )
          );
        }
      }
    }
  };

  public ReceiveAction(ActivityThread activityThread) {
    super(activityThread);
  }

  /**
   * 如果参数中存在超时，那么在一开始需要预设时钟消息
   */
  @Override
  public void sendRequest() {
    final Argument defArguments = getActionDefinition().getArguments();
    final ListArgument listDefArguments = (ListArgument) defArguments;
    for (Argument argument : listDefArguments.getArgs()) {
      Map<String, Argument> mapArgument = ((MapArgument) argument).getArgs();
      if (mapArgument.containsKey(ArgNames.AFTER)) {
        Argument afterArgument = mapArgument.get(ArgNames.AFTER);
        final long timestamp = (long) ((ExpressionArgument) afterArgument).getValue(
            getArgExpressionContext());
        ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
        Clock clock = integrationPlan.getClock();
        clock.registerClockMessage(ClockMessage.buildForActivity(
            CachedTimestamp.nowMilliseconds() + timestamp,
            activity.getId(),
            activityThread.getDomainId(),
            actionDefinition.getName()
        ));
        return;
      }
    }
  }

  /**
   * 处理消息回调
   *
   * @param message 消息
   * @return 处理结果
   */
  @Override
  public Result handleResponse(Message message) {
    final ListArgument arguments = (ListArgument) getActionDefinition().getArguments();
    for (Argument arg : arguments.getArgs()) {
      final Map<String, Argument> mapArgument = ((MapArgument) arg).getArgs();

      // 处理超时事件
      if (ClockMessage.TYPE.equals(message.getType())) {

        // 非After条件，略过
        if (!mapArgument.containsKey(ArgNames.AFTER)) {
          continue;
        }

        ClockMessage clockMessage = (ClockMessage) message;
        if (clockMessage.getAction().equals(this.actionDefinition.getName()) &&
            clockMessage.getTimestamp() >= getActivityThread().getCreatedOn()) {
          EasyMap args = new EasyMap(((MapArgument) arg).getValueMap(
              getArgExpressionContext(message)));
          if (args.contains(ArgNames.CONTEXT_VARS)) {
            activityThread.getContext().putAll(
                args.getSubArguments(ArgNames.CONTEXT_VARS).toMap());
          }
          String thenCtrlString = args.getString(ArgNames.THEN);
          return Result.okWithData(Collections.singletonMap("$ctrl", thenCtrlString));
        } else {
          return Result.ignore();
        }
      } else if (mapArgument.containsKey(ArgNames.WHEN)) {
        ExpressionArgument whenArgument = (ExpressionArgument) mapArgument.get(ArgNames.WHEN);
        final boolean matched = (boolean) whenArgument.getValue(getArgExpressionContext(message));

        if (matched) {
          // 只有当符合条件了才渲染其他的参数
          EasyMap args = new EasyMap(((MapArgument) arg).getValueMap(
              getArgExpressionContext(message)));

          if (args.contains(ArgNames.CONTEXT_VARS)) {
            activityThread.getContext().putAll(
                args.getSubArguments(ArgNames.CONTEXT_VARS).toMap());
          }
          String thenCtrlString = args.getString(ArgNames.THEN);
          return Result.okWithData(Collections.singletonMap("$ctrl", thenCtrlString));
        }
      }
    }
    return Result.ignore();
  }

  interface ArgNames {

    String WHEN = "when";

    String THEN = "then";

    String AFTER = "after";

    String CONTEXT_VARS = "context_vars";
  }
}
