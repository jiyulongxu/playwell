package playwell.baas.domain;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import playwell.common.EasyMap;
import playwell.common.Mappable;

/**
 * 事件映射配置
 */
public class EventMappingConfiguration implements Mappable {

  // 要映射成的事件类型
  private final String type;

  // 活动ID，如果指定了活动ID，事件会被映射成BasicActivityThreadMessage
  private final int activityId;

  // 接收的服务或者回调地址
  private final String sendTo;

  // 额外的附加参数
  private final Map<String, Object> extraAttributes;

  public EventMappingConfiguration(String type, int activityId, String sendTo,
      Map<String, Object> extraAttributes) {
    this.type = type;
    this.activityId = activityId;
    this.sendTo = sendTo;
    this.extraAttributes = extraAttributes;
  }

  /**
   * 从配置数据中进行构建
   *
   * @param configuration 配置数据
   * @return EventMappingConfiguration
   */
  public static EventMappingConfiguration buildFromConfiguration(EasyMap configuration) {
    return new EventMappingConfiguration(
        configuration.getString(DataFields.TYPE),
        configuration.getInt(DataFields.ACTIVITY_ID, 0),
        configuration.getString(DataFields.SEND_TO),
        configuration.getSubArguments(DataFields.EXTRA_ATTRIBUTES).toMap()
    );
  }

  public String getType() {
    return type;
  }

  public int getActivityId() {
    return activityId;
  }

  public String getSendTo() {
    return sendTo;
  }

  public Map<String, Object> getExtraAttributes() {
    return extraAttributes;
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.of(
        DataFields.TYPE, this.type,
        DataFields.ACTIVITY_ID, this.activityId,
        DataFields.SEND_TO, this.sendTo,
        DataFields.EXTRA_ATTRIBUTES, this.extraAttributes
    );
  }

  interface DataFields {

    String TYPE = "type";

    String ACTIVITY_ID = "activity_id";

    String SEND_TO = "send_to";

    String EXTRA_ATTRIBUTES = "extra_attributes";
  }
}
