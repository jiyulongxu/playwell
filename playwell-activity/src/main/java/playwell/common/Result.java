package playwell.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import playwell.util.ModelUtils;

/**
 * 用于统一描述一次执行结果，比如Action的执行结果，接口的返回等等
 *
 * @author chihongze@gmail.com
 */
public class Result implements Mappable {

  public static final String STATUS_OK = "ok";

  public static final String STATUS_FAIL = "fail";

  public static final String STATUS_IGNORE = "ignore";

  public static final String STATUS_TIMEOUT = "timeout";

  // 结果状态
  private final String status;

  // 错误码
  private final String errorCode;

  // 结果消息
  private final String message;

  // 结果数据
  private final Map<String, Object> data;

  public Result(String status, String errorCode, String message, Map<String, Object> data) {
    this.status = status;
    this.errorCode = errorCode;
    this.message = message;
    this.data = data;
  }

  public static Result fromMap(EasyMap map) {
    return new Result(
        map.getString(Fields.STATUS),
        map.getString(Fields.ERROR_CODE, ""),
        map.getString(Fields.MESSAGE, ""),
        map.getSubArguments(Fields.DATA).toMap()
    );
  }

  public static Result ok() {
    return new Result(STATUS_OK, "", "", Collections.emptyMap());
  }

  public static Result okWithMsg(String message) {
    return new Result(STATUS_OK, "", message, Collections.emptyMap());
  }

  public static Result okWithData(Map<String, Object> data) {
    return new Result(STATUS_OK, "", "", data);
  }

  public static Result okWithMsgAndData(String message, Map<String, Object> data) {
    return new Result(STATUS_OK, "", message, data);
  }

  public static Result failWithCodeAndMessage(String errorCode, String message) {
    return new Result(STATUS_FAIL, errorCode, message, Collections.emptyMap());
  }

  public static Result failWithCodeAndMessageAndData(String errorCode, String message,
      Map<String, Object> data) {
    return new Result(STATUS_FAIL, errorCode, message, data);
  }

  public static Result ignore() {
    return new Result(STATUS_IGNORE, "", "", Collections.emptyMap());
  }

  public static Result ignoreWithMessage(String message) {
    return new Result(STATUS_IGNORE, "", message, Collections.emptyMap());
  }

  public static Result timeout() {
    return new Result(STATUS_TIMEOUT, "", "", Collections.emptyMap());
  }

  public static Result timeoutWithMessage(String message) {
    return new Result(STATUS_TIMEOUT, "", message, Collections.emptyMap());
  }

  public String getStatus() {
    return status;
  }

  public String getErrorCode() {
    return this.errorCode;
  }

  public String getMessage() {
    return message;
  }

  public EasyMap getData() {
    return new EasyMap(this.data);
  }

  public boolean isOk() {
    return Result.STATUS_OK.equals(status);
  }

  public boolean isFail() {
    return Result.STATUS_FAIL.equals(status);
  }

  public boolean isIgnore() {
    return Result.STATUS_IGNORE.equals(status);
  }

  public boolean isTimeout() {
    return Result.STATUS_TIMEOUT.equals(status);
  }

  public Object get(String key) {
    return getData().get(key);
  }

  public Object get(String key, Object defaultValue) {
    return getData().get(key, defaultValue);
  }

  @SuppressWarnings({"unchecked"})
  public <T> T getFromResultData(String fieldName) {
    return (T) getData().get(fieldName);
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.of(
        Fields.STATUS, this.status,
        Fields.ERROR_CODE, this.errorCode,
        Fields.MESSAGE, this.message,
        Fields.DATA, ModelUtils.expandMappable(this.data)
    );
  }

  public String toJSONString() {
    return JSONObject.toJSONString(toMap());
  }

  @Override
  public String toString() {
    final String dataText = JSON.toJSONString(toMap());
    return String.format("Result@%d%s", System.identityHashCode(this), dataText);
  }

  public interface Fields {

    String STATUS = "status";

    String ERROR_CODE = "error_code";

    String MESSAGE = "message";

    String DATA = "data";
  }
}
