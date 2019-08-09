package playwell.message;

import java.util.HashMap;
import java.util.Map;
import playwell.common.Result;

/**
 * 服务响应消息，当Action完成了工作，通过该消息向活动引擎反馈工作结果
 *
 * @author chihongze@gmail.com
 */
public class ServiceResponseMessage extends Message implements ActivityThreadMessage {

  public static String TYPE = "res";

  private final int activityId;

  private final String domainId;

  private final String action;

  private final String status;

  private final String errorCode;

  private final String message;

  private final Map<String, Object> data;

  public ServiceResponseMessage(
      long timestamp, ServiceRequestMessage requestMessage, Result result) {
    this(
        timestamp,
        requestMessage.getActivityId(),
        requestMessage.getDomainId(),
        requestMessage.getAction(),
        requestMessage.getReceiver(),
        requestMessage.getSender(),
        result.getStatus(),
        result.getErrorCode(),
        result.getMessage(),
        result.getData().toMap()
    );
  }

  public ServiceResponseMessage(
      long timestamp, int activityId, String domainId, String action,
      String sender, String receiver,
      String status, String errorCode, String message, Map<String, Object> data) {
    super(TYPE, sender, receiver, new HashMap<>(), timestamp);

    final Map<String, Object> attributes = this.getAttributes();

    this.activityId = activityId;
    attributes.put(Attributes.ACTIVITY_ID, activityId);

    this.domainId = domainId;
    attributes.put(Attributes.DOMAIN_ID, domainId);

    this.action = action;
    attributes.put(Attributes.ACTION, action);

    this.status = status;
    attributes.put(Attributes.STATUS, status);

    this.errorCode = errorCode;
    attributes.put(Attributes.ERROR_CODE, errorCode);

    this.message = message;
    attributes.put(Attributes.MESSAGE, message);

    this.data = data;
    attributes.put(Attributes.DATA, data);
  }

  @Override
  public int getActivityId() {
    return activityId;
  }

  @Override
  public String getDomainId() {
    return domainId;
  }

  public String getAction() {
    return action;
  }

  public String getStatus() {
    return status;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getMessage() {
    return message;
  }

  public Map<String, Object> getData() {
    return data;
  }

  public Result toResult() {
    return new Result(status, errorCode, message, data);
  }

  public interface Attributes {

    String ACTIVITY_ID = "activity";

    String DOMAIN_ID = "domain";

    String ACTION = "action";

    String STATUS = "status";

    String ERROR_CODE = "error_code";

    String MESSAGE = "message";

    String DATA = "data";
  }
}
