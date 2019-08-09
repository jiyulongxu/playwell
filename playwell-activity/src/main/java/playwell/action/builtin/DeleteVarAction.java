package playwell.action.builtin;

import java.util.List;
import java.util.function.Consumer;
import playwell.action.ActionInstanceBuilder;
import playwell.action.ActionRuntimeException;
import playwell.action.SyncAction;
import playwell.activity.thread.ActivityThread;
import playwell.common.Result;
import playwell.common.argument.Argument;
import playwell.common.exception.BuildComponentException;

/**
 * <p>DeleteVarAction用于从上下文中删除变量或者清空整个上下文</p>
 *
 * 删除指定的变量： <br/>
 *
 * <pre>
 *   - name: delete_var
 *     type: delete_var
 *     args:
 *       - str("a")
 *       - str("b")
 *       - str("c")
 *     ctrl:
 *       - default: finish
 * </pre>
 *
 * 清空整个ActivityThread的上下文：<br/>
 *
 * <pre>
 *   - name: delete_var
 *     type: delete_var
 *     args: all
 *     ctrl:
 *       - default: finish
 * </pre>
 */
public class DeleteVarAction extends SyncAction {

  public static final String TYPE = "delete_var";

  public static final ActionInstanceBuilder BUILDER = DeleteVarAction::new;

  public static final Consumer<Argument> ARG_SPEC = argument -> {
    if (!(Argument.isExpressionArgument(argument) || Argument.isListArgument(argument))) {
      throw new BuildComponentException(
          "Invalid arguments format, the delete action only allow list or \"all\"");
    }
  };

  public DeleteVarAction(ActivityThread activityThread) {
    super(activityThread);
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public Result execute() {
    Argument argumentsDef = this.getActionDefinition().getArguments();

    // 表达式类型的参数
    if (Argument.isExpressionArgument(argumentsDef)) {
      Object valueObj = getValueArgument();
      if (valueObj instanceof String) {
        String value = this.getValueArgument();
        if ("all".equalsIgnoreCase(value)) {
          this.getActivityThread().getContext().clear();
          return Result.ok();
        } else {
          throw new ActionRuntimeException(
              CommonRuntimeErrorCodes.INVALID_ARGUMENT,
              "Unknown argument value: " + value
          );
        }
      } else if (valueObj instanceof List) {
        removeKeys((List<Object>) valueObj);
        return Result.ok();
      } else {
        throw new ActionRuntimeException(
            CommonRuntimeErrorCodes.INVALID_ARGUMENT,
            "Invalid argument type, only allow List or 'all'"
        );
      }
    } else if (Argument.isListArgument(argumentsDef)) {
      List<Object> argObjects = this.getListArguments();
      removeKeys(argObjects);
      return Result.ok();
    } else {
      throw new ActionRuntimeException(
          CommonRuntimeErrorCodes.INVALID_ARGUMENT,
          "Invalid argument type, only allow List or 'all'"
      );
    }
  }

  private void removeKeys(List<Object> argObjects) {
    argObjects.forEach(argObj -> {
      String key = (String) argObj;
      activityThread.getContext().remove(key);
    });
  }
}
