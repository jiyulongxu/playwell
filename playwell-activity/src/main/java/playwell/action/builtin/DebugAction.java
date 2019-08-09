package playwell.action.builtin;

import com.alibaba.fastjson.JSONObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.action.ActionInstanceBuilder;
import playwell.action.ActionRuntimeException;
import playwell.action.SyncAction;
import playwell.activity.thread.ActivityThread;
import playwell.common.Result;
import playwell.common.argument.Argument;
import playwell.common.exception.BuildComponentException;


/**
 * <p>用于输出上下文变量，可以将上下文变量输出到debug日志中</p>
 *
 * <p>
 * 输出指定的变量：<br/>
 *
 * <pre>
 *   - name: debug
 *     type: debug
 *     args:
 *      - str("var_a")
 *      - str("var_b")
 *     ctrl:
 *      - default: finish
 * </pre>
 *
 * 输出全部变量：<br/>
 *
 * <pre>
 *   - name: debug
 *     type: debug
 *     args: all
 *     ctrl:
 *      - default: finish
 * </pre>
 * </p>
 *
 * 日志格式：
 *
 * <pre>
 *   ActivityDefinitionName - ActivityId - DomainId - ActionName - ContextVars
 * </pre>
 *
 * @author chihongze@gmail.com
 */
public class DebugAction extends SyncAction {

  public static final String TYPE = "debug";

  public static final ActionInstanceBuilder BUILDER = DebugAction::new;

  public static final Consumer<Argument> ARG_SPEC = argument -> {
    if (!(Argument.isExpressionArgument(argument) || Argument.isListArgument(argument))) {
      throw new BuildComponentException(
          "Invalid arguments format, the debug action only allow list or \"all\"");
    }
  };

  // Debug context logger
  private static final Logger logger = LogManager.getLogger("debug_context");

  public DebugAction(ActivityThread activityThread) {
    super(activityThread);
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public Result execute() {
    Argument actionArguments = this.getActionDefinition().getArguments();
    if (Argument.isExpressionArgument(actionArguments)) {
      Object argVal = getValueArgument();
      if (argVal instanceof String) {
        final String value = (String) argVal;
        // 输出ActivityThread所有的信息
        if ("all".equalsIgnoreCase(value)) {
          writeDebugLog(activityThread.getContext());
          return Result.ok();
        } else {
          throw new ActionRuntimeException(
              CommonRuntimeErrorCodes.INVALID_ARGUMENT,
              "Unknown argument expression: " + value
          );
        }
      } else if (argVal instanceof List) {
        writeTargetVars((List<Object>) argVal);
      } else {
        throw new ActionRuntimeException(
            CommonRuntimeErrorCodes.INVALID_ARGUMENT,
            "Invalid arguments format, only allow list or \"all\""
        );
      }
      return Result.ok();
    } else if (Argument.isListArgument(actionArguments)) {
      // 只输出指定的变量
      final List<Object> argsObjects = this.getListArguments();
      writeTargetVars(argsObjects);
      return Result.ok();
    } else {
      throw new ActionRuntimeException(
          CommonRuntimeErrorCodes.INVALID_ARGUMENT,
          "Invalid arguments format, only allow list or \"all\""
      );
    }
  }

  private void writeTargetVars(List<Object> argsObjects) {
    Map<String, Object> contextVars = new HashMap<>(argsObjects.size());
    argsObjects.forEach(argObj -> {
      String varName = (String) argObj;
      Object varValue = activityThread.getContext().get(varName);
      contextVars.put(varName, varValue);
    });
    writeDebugLog(contextVars);
  }

  // 输出Debug日志
  private void writeDebugLog(Map<String, Object> contextVars) {
    final String record = String.format(
        "%s - %d - %s - %s - %s",
        activityThread.getActivityDefinition().getName(),
        activityThread.getActivity().getId(),
        activityThread.getDomainId(),
        this.getActionDefinition().getName(),
        JSONObject.toJSONString(contextVars)
    );
    logger.info(record);
  }
}
