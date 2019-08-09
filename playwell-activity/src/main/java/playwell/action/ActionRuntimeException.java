package playwell.action;

import java.util.Collections;
import java.util.Map;

/**
 * Action运行时异常，由Action在运行时抛出，ActivityThreadScheduler进行捕获处理
 *
 * @author chihongze@gmail.com
 */
public class ActionRuntimeException extends RuntimeException {

  private final String errorCode;

  private final String message;

  private final Map<String, Object> data;

  public ActionRuntimeException(String errorCode, String message) {
    this(errorCode, message, Collections.emptyMap());
  }

  public ActionRuntimeException(String errorCode, String message, Map<String, Object> data) {
    super(message);
    this.errorCode = errorCode;
    this.message = message;
    this.data = data;
  }

  public String getErrorCode() {
    return errorCode;
  }

  @Override
  public String getMessage() {
    return message;
  }

  public Map<String, Object> getData() {
    return data;
  }
}
