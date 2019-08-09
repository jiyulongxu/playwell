package playwell.message;

import java.util.HashMap;
import java.util.Map;
import playwell.activity.thread.ActivityThread;
import playwell.common.EasyMap;

/**
 * 服务请求消息，当Action需要调用某个服务的时候，就会向消息总线传递该消息
 *
 * @author chihongze@gmail.com
 */
public class ServiceRequestMessage extends Message implements ActivityThreadMessage {

  public static final String TYPE = "req";

  // 活动ID
  private final int activityId;

  // DomainID
  private final String domainId;

  // Action Name
  private final String action;

  // 调用参数，可能是List，也可能是Map
  private final Object args;

  // 忽略结果
  private final boolean ignoreResult;

  public ServiceRequestMessage(
      long timestamp,
      ActivityThread activityThread,
      String sender,
      String receiver,
      Object args,
      boolean ignoreResult) {
    this(
        timestamp,
        activityThread.getActivity().getId(),
        activityThread.getDomainId(),
        activityThread.getCurrentAction(),
        sender,
        receiver,
        args,
        ignoreResult
    );
  }

  public ServiceRequestMessage(
      long timestamp,
      int activityId,
      String domainId,
      String action,
      String sender,
      String receiver,
      Object args,
      boolean ignoreResult) {
    super(TYPE, sender, receiver, new HashMap<>(4), timestamp);

    this.activityId = activityId;
    this.getAttributes().put(Attributes.ACTIVITY_ID, activityId);

    this.domainId = domainId;
    this.getAttributes().put(Attributes.DOMAIN_ID, domainId);

    this.action = action;
    this.getAttributes().put(Attributes.ACTION, action);

    this.args = args;
    this.getAttributes().put(Attributes.ARGS, args);

    this.ignoreResult = ignoreResult;
    this.getAttributes().put(Attributes.IGNORE_RESULT, ignoreResult);
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

  public Object getArgs() {
    return args;
  }

  @SuppressWarnings({"unchecked"})
  public EasyMap getMapArgs() {
    return new EasyMap((Map<String, Object>) args);
  }

  public boolean isIgnoreResult() {
    return ignoreResult;
  }

  public interface Attributes {

    String ACTIVITY_ID = "activity";

    String DOMAIN_ID = "domain";

    String ACTION = "action";

    String ARGS = "args";

    String IGNORE_RESULT = "ignore_result";
  }
}
