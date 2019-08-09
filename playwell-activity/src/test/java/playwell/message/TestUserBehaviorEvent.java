package playwell.message;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户行为事件
 */
public class TestUserBehaviorEvent extends Message {

  public static final String TYPE = "user_behavior";

  private final String userId;

  private final String behavior;

  private final Map<String, Object> eventAttributes;

  public TestUserBehaviorEvent(String userId, String behavior, Map<String, Object> eventAttributes,
      long timestamp) {
    super(TYPE, "", "", new HashMap<>(), timestamp);

    this.userId = userId;
    this.getAttributes().put(Attributes.USER_ID, userId);

    this.behavior = behavior;
    this.getAttributes().put(Attributes.BEHAVIOR, behavior);

    this.eventAttributes = eventAttributes;
    this.getAttributes().putAll(eventAttributes);
  }

  public String getUserId() {
    return userId;
  }

  public String getBehavior() {
    return behavior;
  }

  public Map<String, Object> getEventAttributes() {
    return eventAttributes;
  }

  public interface Attributes {

    String USER_ID = "user_id";

    String BEHAVIOR = "behavior";

    String ATTRIBUTES = "attributes";
  }
}
