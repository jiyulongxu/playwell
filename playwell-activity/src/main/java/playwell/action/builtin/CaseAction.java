package playwell.action.builtin;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import playwell.action.ActionInstanceBuilder;
import playwell.action.ActionRuntimeException;
import playwell.action.SyncAction;
import playwell.action.builtin.ReceiveAction.ArgNames;
import playwell.activity.thread.ActivityThread;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.common.argument.Argument;
import playwell.common.argument.ExpressionArgument;
import playwell.common.argument.ListArgument;
import playwell.common.argument.MapArgument;
import playwell.common.exception.BuildComponentException;

/**
 * <p>CaseAction用于进行条件测试，并依据条件测试的结果来决定ActivityThread的走向或者向上下文写入变量</p>
 * eg. <br/>
 *
 * <pre>
 *   - name: case
 *     type: case
 *     args:
 *       - when: var("a") == 1
 *         then: call("action_a")
 *         context_vars:
 *           a: 1
 *           b: 2
 *       - when: var("b") == 2
 *         then: call("action_b")
 *         context_vars:
 *           c: 3
 *           d: 4
 *       - default: call("action_c")
 *         context_vars:
 *           e: 4
 *           f: 5
 * </pre>
 */
public class CaseAction extends SyncAction {

  public static final String TYPE = "case";

  public static final ActionInstanceBuilder BUILDER = CaseAction::new;

  public static final Consumer<Argument> ARG_SPEC = argument -> {
    if (!Argument.isListArgument(argument)) {
      throw new BuildComponentException(
          "Invalid argument type, the case action only support List<Map> arguments");
    }

    final ListArgument listArgumentDef = (ListArgument) argument;
    for (Argument condArg : listArgumentDef.getArgs()) {
      if (!(condArg instanceof MapArgument)) {
        throw new BuildComponentException(
            "Invalid argument format, the case action only support map condition"
        );
      }
      final Map<String, Argument> mapArguments = ((MapArgument) condArg).getArgs();
      if ((!mapArguments.containsKey(ConditionArgNames.WHEN)) &&
          (!mapArguments.containsKey(ConditionArgNames.DEFAULT))) {
        throw new BuildComponentException(
            String.format(
                "Invalid condition: %s. The condition in the case action required when or default argument",
                mapArguments
            )
        );
      }
      if (mapArguments.containsKey(ConditionArgNames.WHEN) &&
          (!mapArguments.containsKey(ConditionArgNames.THEN))) {
        throw new BuildComponentException(
            String.format(
                "Invalid condition: %s. The when condition must required then argument",
                mapArguments
            )
        );
      }
    }
  };

  public CaseAction(ActivityThread activityThread) {
    super(activityThread);
  }

  @Override
  public Result execute() {
    ListArgument listArgumentDef = (ListArgument) getActionDefinition().getArguments();
    MapArgument defaultArgument = null;

    for (Argument argument : listArgumentDef.getArgs()) {
      final Map<String, Argument> mapArguments = ((MapArgument) argument).getArgs();
      if (mapArguments.containsKey(ConditionArgNames.WHEN)) {
        final boolean matched = (boolean) ((ExpressionArgument) mapArguments.get(ArgNames.WHEN))
            .getValue(getArgExpressionContext());

        if (matched) {
          EasyMap args = new EasyMap(((MapArgument) argument).getValueMap(
              getArgExpressionContext()));
          if (!args.contains(ConditionArgNames.THEN)) {
            throw new ActionRuntimeException(
                CommonRuntimeErrorCodes.INVALID_ARGUMENT,
                "Invalid argument format, no 'then' argument in the condition"
            );
          }
          String then = args.getString(ConditionArgNames.THEN);
          if (args.contains(ArgNames.CONTEXT_VARS)) {
            EasyMap context = args.getSubArguments(ConditionArgNames.CONTEXT_VARS);
            activityThread.getContext().putAll(context.toMap());
          }
          return Result.okWithData(Collections.singletonMap("$ctrl", then));
        }
      } else if (mapArguments.containsKey(ConditionArgNames.DEFAULT)) {
        defaultArgument = (MapArgument) argument;
      } else {
        // 无法识别的表达式
        throw new ActionRuntimeException(
            CommonRuntimeErrorCodes.INVALID_ARGUMENT,
            "Invalid case condition type, only support map!"
        );
      }
    }

    // 所有条件都没有满足，并且也没有默认条件，返回错误
    if (defaultArgument == null) {
      throw new ActionRuntimeException(
          ErrorCodes.NO_DEFAULT,
          "All conditions are not match, and there is no default condition to run"
      );
    }

    final EasyMap defaultArgs = new EasyMap(defaultArgument.getValueMap(getArgExpressionContext()));
    if (defaultArgs.contains(ConditionArgNames.CONTEXT_VARS)) {
      activityThread.getContext().putAll(
          defaultArgs.getSubArguments(ConditionArgNames.CONTEXT_VARS).toMap());
    }
    return Result.okWithData(Collections.singletonMap(
        "$ctrl", defaultArgs.getString(ConditionArgNames.DEFAULT)));
  }

  interface ConditionArgNames {

    String WHEN = "when";

    String THEN = "then";

    String CONTEXT_VARS = "context_vars";

    String DEFAULT = "default";
  }

  interface ErrorCodes {

    String NO_DEFAULT = "no_default";
  }
}
