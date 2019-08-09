package playwell.action;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import playwell.common.exception.BuildComponentException;

/**
 * Action控制信息
 *
 * @author chihongze@gmail.com
 */
public class ActionCtrlInfo {

  private final ActionCtrlType ctrlType;

  private final String nextStep;

  private final String failureReason;

  private final Map<String, Object> contextVars;

  public ActionCtrlInfo(
      ActionCtrlType ctrlType,
      String nextStep,
      String failureReason,
      Map<String, Object> contextVars) {
    this.ctrlType = ctrlType;
    this.nextStep = nextStep;
    this.failureReason = failureReason;
    this.contextVars = contextVars;
  }

  /**
   * 基于字符串描述来构建ActionCtrlInfo对象
   *
   * @param ctrlString 字符串描述
   * @return ActionCtrlInfo对象
   */
  public static ActionCtrlInfo fromCtrlString(
      String ctrlString, Map<String, Object> contextVars) throws BuildComponentException {

    if (StringUtils.isEmpty(ctrlString)) {
      throw new BuildComponentException("Ctrl string should not be empty!");
    }

    final String[] tokens = StringUtils.split(ctrlString);
    return fromCtrlStringTokens(ctrlString, tokens, contextVars);
  }

  private static ActionCtrlInfo fromCtrlStringTokens(
      String ctrlString, String[] tokens, Map<String, Object> contextVars)
      throws BuildComponentException {
    final Optional<ActionCtrlType> actionCtrlTypeOpt = ActionCtrlType.valueOfByType(tokens[0]);
    if (!actionCtrlTypeOpt.isPresent()) {
      throw new BuildComponentException(String.format("Invalid ctrl expression: %s", tokens[0]));
    }

    ActionCtrlType actionCtrlType = actionCtrlTypeOpt.get();
    if (actionCtrlType == ActionCtrlType.CALL) {
      if (tokens.length != 2) {
        throw new BuildComponentException(
            String.format("Invalid ctrl expression: '%s'", ctrlString));
      }

      return new ActionCtrlInfo(actionCtrlType, tokens[1], "", contextVars);
    } else if (actionCtrlType == ActionCtrlType.FAIL) {
      if (tokens.length >= 3 && "because".equals(tokens[1])) {
        // 带有错误原因的fail
        return new ActionCtrlInfo(
            actionCtrlType,
            "",
            StringUtils.join(Arrays.copyOfRange(tokens, 2, tokens.length), ""),
            contextVars
        );
      } else {
        // 不带错误原因的fail
        return new ActionCtrlInfo(
            actionCtrlType,
            "",
            "",
            contextVars
        );
      }
    } else if (actionCtrlType == ActionCtrlType.RETRY) {
      if (tokens.length == 2) {
        return new RetryActionCtrlInfo(
            "",
            "",
            Integer.parseInt(tokens[1]),
            new ActionCtrlInfo(
                ActionCtrlType.FAIL,
                "",
                "retry_failure",
                contextVars
            ),
            contextVars
        );
      } else if (tokens.length > 2) {
        return new RetryActionCtrlInfo(
            "",
            "",
            Integer.parseInt(tokens[1]),
            fromCtrlStringTokens(
                ctrlString,
                ArrayUtils.subarray(tokens, 2, tokens.length),
                contextVars
            ),
            contextVars
        );
      } else {
        throw new BuildComponentException(String.format(
            "Invalid ctrl expression: '%s'", ctrlString));
      }
    } else if (actionCtrlType == ActionCtrlType.REPAIRING) {
      if (tokens.length != 2) {
        throw new BuildComponentException(String.format(
            "Invalid ctrl expression: '%s'", ctrlString));
      }
      return new RepairActionCtrlInfo(
          tokens[1],
          contextVars
      );
    } else {
      return new ActionCtrlInfo(
          actionCtrlType,
          "",
          "",
          contextVars
      );
    }
  }

  public ActionCtrlType getCtrlType() {
    return ctrlType;
  }

  public String getNextStep() {
    return nextStep;
  }

  public String getFailureReason() {
    return this.failureReason;
  }

  public Map<String, Object> getContextVars() {
    return contextVars;
  }

  /**
   * 获取控制描述字符串
   */
  public String getCtrlString() {
    if (ctrlType == ActionCtrlType.CALL) {
      return String.format("%s %s", ActionCtrlType.CALL.getType(), nextStep);
    } else {
      return ctrlType.getType();
    }
  }

  @Override
  public String toString() {
    return String.format(
        "ActionCtrlInfo@%d{\"ctrl\": \"%s\"}",
        System.identityHashCode(this), this.getCtrlString());
  }
}
