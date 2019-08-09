package playwell.message;

import java.util.HashMap;
import java.util.Map;

/**
 * BasicActivityThreadMessage 标准定向到具体ActivityThread的消息
 */
public class BasicActivityThreadMessage extends Message implements ActivityThreadMessage {

  public static final String ACTIVITY_ID = "$ACTIVITY";

  public static final String DOMAIN_ID = "$DID";

  private final int activityId;

  private final String domainId;

  public BasicActivityThreadMessage(
      String type,
      String sender,
      String receiver,
      int activityId,
      String domainId,
      Map<String, Object> attributes,
      long timestamp) {
    super(type, sender, receiver, new HashMap<>(2 + attributes.size()), timestamp);
    this.activityId = activityId;
    this.domainId = domainId;
    final Map<String, Object> allAttributes = getAttributes();
    allAttributes.put(ACTIVITY_ID, activityId);
    allAttributes.put(DOMAIN_ID, domainId);
    allAttributes.putAll(attributes);
  }

  @Override
  public int getActivityId() {
    return activityId;
  }

  @Override
  public String getDomainId() {
    return domainId;
  }
}
