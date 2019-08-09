package playwell.message;


import com.alibaba.fastjson.JSON;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import playwell.common.Mappable;
import playwell.util.ModelUtils;

/**
 * 消息的本质，即由一个消息类型、消息发生时间，以及一堆KV消息属性组成。 当然，开发者可以自行继承该对象来扩展更专业和更易用的消息类型。
 *
 * @author chihongze@gmail.com
 */
public class Message implements Mappable {

  // 消息类型
  private final String type;

  // 消息发送者
  private final String sender;

  // 消息接收者
  private final String receiver;

  // 消息属性
  private final Map<String, Object> attributes;

  // 消息发生的物理时间戳
  private final long timestamp;

  public Message(String type, String sender, String receiver, Map<String, Object> attributes,
      long timestamp) {
    this.type = type;
    this.sender = sender;
    this.receiver = receiver;
    this.attributes = attributes;
    this.timestamp = timestamp;
  }

  public String getType() {
    return type;
  }

  public String getSender() {
    return sender;
  }

  public String getReceiver() {
    return receiver;
  }

  public Map<String, Object> getAttributes() {
    if (attributes == null) {
      return Collections.emptyMap();
    }
    return attributes;
  }

  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public Map<String, Object> toMap() {
    return ModelUtils.expandMappable(ImmutableMap.of(
        Fields.TYPE, this.getType(),
        Fields.SENDER, this.getSender(),
        Fields.RECEIVER, this.getReceiver(),
        Fields.ATTRIBUTES, ModelUtils.expandMappable(this.getAttributes()),
        Fields.TIMESTAMP, this.getTimestamp()
    ));
  }

  @Override
  public String toString() {
    final String data = JSON.toJSONString(toMap());
    return String.format("Message@%d%s", System.identityHashCode(this), data);
  }

  public interface Fields {

    String TYPE = "type";

    String SENDER = "sender";

    String RECEIVER = "receiver";

    String ATTRIBUTES = "attr";

    String TIMESTAMP = "time";
  }
}
