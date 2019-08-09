package playwell.activity.thread;

import playwell.common.exception.PlaywellException;

/**
 * 当在调度ActivityThread产生错误时抛出此异常，该异常不会在日志中打印堆栈，只会记录错误码和错误消息
 *
 * @author chihongze@gmail.com
 */
public class ActivityThreadRuntimeException extends PlaywellException {

  private final String errorCode;

  public ActivityThreadRuntimeException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
