package playwell.action.builtin;

import com.alibaba.fastjson.JSONObject;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import playwell.action.ActionInstanceBuilder;
import playwell.action.SyncAction;
import playwell.activity.thread.ActivityThread;
import playwell.common.Result;
import playwell.common.argument.Argument;

/**
 * <p>StdoutAction用于向stdout输出信息，建议仅用于测试</p>
 * eg. <br/>
 *
 * <pre>
 *   - name: echo
 *     type: stdout
 *     args:
 *       - str("How are you")
 *       - str("Fine, Thank you and you?")
 *       - str("I'm well, thanks")
 *     ctrl: call("next_action")
 * </pre>
 *
 * or <br/>
 *
 * <pre>
 *   - name: echo
 *     type: stdout
 *     args: list("How are you", "Fine, Thank you and you?", "I'm well, thanks")
 *     ctrl: call("next_action")
 * </pre>
 */
public class StdoutAction extends SyncAction {

  public static final String TYPE = "stdout";

  public static final ActionInstanceBuilder BUILDER = StdoutAction::new;

  public static final Consumer<Argument> ARG_SPEC = argument -> {
  };

  public StdoutAction(ActivityThread activityThread) {
    super(activityThread);
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public Result execute() {
    final Argument defArgument = this.actionDefinition.getArguments();

    // List类型的参数
    if (Argument.isListArgument(defArgument)) {
      final List<Object> arguments = getListArguments();
      arguments.forEach(this::echo);
    } else if (Argument.isMapArgument(defArgument)) {
      final Map<String, Object> arguments = getMapArguments();
      echo(JSONObject.toJSONString(arguments));
    } else {
      final Object varObj = getValueArgument();
      if (varObj instanceof List) {
        ((List<Object>) varObj).forEach(this::echo);
      } else {
        echo(varObj);
      }
    }

    return Result.ok();
  }

  private void echo(Object value) {
    System.out.printf(
        "ActivityThread[%d, %s]: %s\n",
        getActivity().getId(),
        getActivityThread().getDomainId(),
        value
    );
  }
}
