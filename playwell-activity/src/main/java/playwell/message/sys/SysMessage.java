package playwell.message.sys;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.collections4.MapUtils;
import playwell.message.ActivityThreadMessage;
import playwell.message.Message;


/**
 * 系统消息基类，所有内部的系统消息均需要继承该类
 *
 * @author chihongze@gmail.com
 */
public abstract class SysMessage extends Message implements ActivityThreadMessage {

  public static final String TYPE = "SYSTEM";

  protected final int activityId;

  protected final String domainId;

  public SysMessage(long timestamp, String sender, String receiver,
      int activityId, String domainId, Map<String, Object> extraArgs) {
    super(
        TYPE,
        sender,
        receiver,
        ImmutableMap.<String, Object>builder()
            .put(Attributes.ACTIVITY_ID, activityId)
            .put(Attributes.DOMAIN_ID, domainId)
            .putAll(MapUtils.isEmpty(extraArgs) ? Collections.emptyMap() : extraArgs)
            .build(),
        timestamp
    );
    this.activityId = activityId;
    this.domainId = domainId;
  }

  @Override
  public int getActivityId() {
    return activityId;
  }

  @Override
  public String getDomainId() {
    return domainId;
  }

  /**
   * 事件属性名称
   */
  public interface Attributes {

    String ACTIVITY_ID = "activity";

    String DOMAIN_ID = "domain";
  }
}
