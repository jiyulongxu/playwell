package playwell.action.builtin;

import java.util.function.Consumer;
import playwell.action.ActionInstanceBuilder;
import playwell.action.SyncAction;
import playwell.activity.thread.ActivityThread;
import playwell.common.Result;
import playwell.common.argument.Argument;
import playwell.common.exception.BuildComponentException;


/**
 * <p>VarAction可以用来向上下文中写入变量</p>
 * eg. <br/>
 *
 * <pre>
 * - name: var
 *   type: update_var
 *   args:
 *     a: 1
 *     b: "{1, 2, 3, 4}"
 *     c:
 *       d: 1
 *       e: 2
 *   ctrl:
 *     - default: finish
 * </pre>
 *
 * @author chihongze@gmail.com
 */
public class UpdateVarAction extends SyncAction {

  public static final String TYPE = "update_var";

  public static final ActionInstanceBuilder BUILDER = UpdateVarAction::new;

  public static final Consumer<Argument> ARG_SPEC = argument -> {
    if (!Argument.isMapArgument(argument)) {  // 字典类型的参数
      throw new BuildComponentException(
          "Invalid argument type, the update_var action only accept map argument"
      );
    }
  };

  public UpdateVarAction(ActivityThread activityThread) {
    super(activityThread);
  }

  @Override
  public Result execute() {
    this.getActivityThread().putContextVars(getMapArguments());
    return Result.ok();
  }
}
