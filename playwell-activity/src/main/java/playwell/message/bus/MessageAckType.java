package playwell.message.bus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 消息确认类型
 */
public enum MessageAckType {

  /**
   * 读取完毕消息之后就立即确认
   */
  AFTER_READ("after_read"),

  /**
   * 处理完毕消息之后就立即确认
   */
  AFTER_HANDLE("after_handle"),

  ;

  private static final Map<String, MessageAckType> ALL_TYPES = new HashMap<>();

  static {
    for (MessageAckType type : values()) {
      ALL_TYPES.put(type.getType(), type);
    }
  }

  private final String type;

  MessageAckType(String type) {
    this.type = type;
  }

  public static Optional<MessageAckType> valueOfByType(String type) {
    return Optional.ofNullable(ALL_TYPES.get(type));
  }

  public String getType() {
    return type;
  }
}
