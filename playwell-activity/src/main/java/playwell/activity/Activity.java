package playwell.activity;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.ImmutableMap;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import playwell.common.Mappable;
import playwell.util.DateUtils;

/**
 * 活动 活动总是基于某个定义来创建 一个活动中包含了众多的ActivityThread 活动拥有不同的状态
 *
 * @author chihongze@gmail.com
 */
public class Activity implements Mappable {

  // 活动ID，创建活动的时候由系统自动分配
  private final int id;

  // 活动的展示名称，由用户指定，方便检索
  private final String displayName;

  // 活动所使用的定义名称
  private final String definitionName;

  // 活动的状态
  private final ActivityStatus status;

  // 活动的配置，这些配置信息继承自ActivityDefinition中的配置信息，
  // 创建新的活动时可以覆盖
  private final Map<String, Object> config;

  // 创建时间
  private final Date createdOn;

  // 最后更新时间
  private final Date updatedOn;

  public Activity(
      int id, String displayName, String definitionName, ActivityStatus status,
      Map<String, Object> config, Date createdOn, Date updatedOn) {
    this.id = id;
    this.displayName = displayName;
    this.definitionName = definitionName;
    this.status = status;
    this.config = config;
    this.createdOn = createdOn;
    this.updatedOn = updatedOn;
  }

  public int getId() {
    return id;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getDefinitionName() {
    return definitionName;
  }

  public ActivityStatus getStatus() {
    return status;
  }

  public Map<String, Object> getConfig() {
    return config;
  }

  public Date getCreatedOn() {
    return createdOn;
  }

  public Date getUpdatedOn() {
    return updatedOn;
  }

  public boolean isPaused() {
    return this.status == ActivityStatus.PAUSED;
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.<String, Object>builder()
        .put(Fields.ID, this.getId())
        .put(Fields.DISPLAY_NAME, this.getDisplayName())
        .put(Fields.DEFINITION, this.getDefinitionName())
        .put(Fields.STATUS, this.getStatus().getStatus())
        .put(Fields.CONFIG, this.getConfig())
        .put(Fields.CREATED_ON, DateUtils.format(this.getCreatedOn()))
        .put(Fields.UPDATED_ON, DateUtils.format(this.getUpdatedOn()))
        .build();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Activity activity = (Activity) o;
    return id == activity.id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    String data = JSON.toJSONString(toMap());
    return String.format("Activity@%d%s", System.identityHashCode(this), data);
  }

  public interface Fields {

    String ID = "id";

    String DISPLAY_NAME = "display_name";

    String DEFINITION = "definition";

    String STATUS = "status";

    String CONFIG = "config";

    String CREATED_ON = "created_on";

    String UPDATED_ON = "updated_on";
  }
}
