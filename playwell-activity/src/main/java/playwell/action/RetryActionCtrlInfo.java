package playwell.action;

import java.util.Map;

/**
 * 描述重试操作的ActionCtrlInfo
 */
public class RetryActionCtrlInfo extends ActionCtrlInfo {

  // 重试次数
  private final int count;

  // 重试失败后的行为
  private final ActionCtrlInfo retryFailureAction;

  public RetryActionCtrlInfo(String nextStep, String failureReason,
      int count, ActionCtrlInfo retryFailureAction, Map<String, Object> contextVars) {
    super(ActionCtrlType.RETRY, nextStep, failureReason, contextVars);
    this.count = count;
    this.retryFailureAction = retryFailureAction;
  }

  public int getCount() {
    return count;
  }

  public ActionCtrlInfo getRetryFailureAction() {
    return retryFailureAction;
  }
}
