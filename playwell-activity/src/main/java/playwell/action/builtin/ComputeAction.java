package playwell.action.builtin;

import java.util.Map;
import playwell.action.ActionInstanceBuilder;
import playwell.action.SyncAction;
import playwell.activity.thread.ActivityThread;
import playwell.common.Result;
import playwell.common.argument.Argument;
import playwell.common.argument.ExpressionArgument;
import playwell.common.argument.ListArgument;
import playwell.common.argument.MapArgument;

/**
 * ComputeAction可以用于按顺序来计算表达式
 *
 * <pre>
 *   - name: compute
 *     type: compute
 *     args:
 *       - a: 1 + 1
 *       - b: 1 + var("a")
 *       - c: 1 + var("b")
 *     ctrl: call("next_action")
 * </pre>
 */
public class ComputeAction extends SyncAction {

  public static final String TYPE = "compute";

  public static final ActionInstanceBuilder BUILDER = ComputeAction::new;

  public ComputeAction(ActivityThread activityThread) {
    super(activityThread);
  }

  @Override
  public Result execute() {
    final ListArgument listArgumentDef = (ListArgument) getActionDefinition().getArguments();
    for (Argument argument : listArgumentDef.getArgs()) {
      final Map<String, Argument> mapArguments = ((MapArgument) argument).getArgs();
      for (Map.Entry<String, Argument> entry : mapArguments.entrySet()) {
        final String variableName = entry.getKey();
        final Object variableValue = ((ExpressionArgument) entry.getValue())
            .getValue(getArgExpressionContext());
        activityThread.putContextVar(variableName, variableValue);
      }
    }
    return Result.ok();
  }
}
