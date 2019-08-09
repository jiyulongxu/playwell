package playwell.activity.thread;


import com.alibaba.fastjson.JSON;
import com.google.common.collect.ImmutableMap;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.collections4.MapUtils;
import playwell.activity.Activity;
import playwell.activity.definition.ActivityDefinition;
import playwell.common.Mappable;
import playwell.util.DateUtils;
import playwell.util.ModelUtils;


/**
 * 活动线程 每次当触发器满足了条件，都会创建一个活动线程实例
 *
 * @author chihongze@gmail.com
 */
public class ActivityThread implements Mappable {

  // 该线程所隶属的活动
  private final Activity activity;

  // 该线程所使用的活动定义
  private final ActivityDefinition activityDefinition;

  // 与该线程相关联的DomainID
  private final String domainId;

  // 线程创建时间
  private final long createdOn;

  // 线程更新时间
  private long updatedOn;

  // 线程状态
  private ActivityThreadStatus status;

  // 当前执行单元
  private String currentAction;

  // 上下文数据
  private Map<String, Object> context;

  public ActivityThread(
      Activity activity, ActivityDefinition activityDefinition, String domainId,
      ActivityThreadStatus status, String currentAction, long updatedOn, long createdOn,
      Map<String, Object> context) {
    this.activity = activity;
    this.activityDefinition = activityDefinition;
    this.domainId = domainId;
    this.status = status;
    this.currentAction = currentAction;
    this.updatedOn = updatedOn;
    this.createdOn = createdOn;
    this.context = MapUtils.isEmpty(context) ? new HashMap<>() : context;
  }

  public Activity getActivity() {
    return activity;
  }

  public ActivityDefinition getActivityDefinition() {
    return activityDefinition;
  }

  public String getDomainId() {
    return domainId;
  }

  public ActivityThreadStatus getStatus() {
    return status;
  }

  public void setStatus(ActivityThreadStatus status) {
    this.status = status;
  }

  public String getCurrentAction() {
    return currentAction;
  }

  public void setCurrentAction(String currentAction) {
    this.currentAction = currentAction;
  }

  public long getUpdatedOn() {
    return this.updatedOn;
  }

  public void setUpdatedOn(long updatedOn) {
    this.updatedOn = updatedOn;
  }

  public long getCreatedOn() {
    return createdOn;
  }

  public Map<String, Object> getContext() {
    return context;
  }

  public Map<String, Object> removeContextVar(String key) {
    this.context.remove(key);
    return context;
  }

  public Map<String, Object> putContextVar(String key, Object value) {
    this.context.put(key, value);
    return context;
  }

  public Map<String, Object> putContextVars(Map<String, Object> vars) {
    if (MapUtils.isEmpty(vars)) {
      return context;
    }

    context.putAll(vars);
    return context;
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.<String, Object>builder()
        .put(Fields.DOMAIN_ID, domainId)
        .put(Fields.ACTIVITY_ID, activity.getId())
        .put(Fields.DEFINITION_NAME, activityDefinition.getName())
        .put(Fields.VERSION, activityDefinition.getVersion())
        .put(Fields.STATUS, status.getStatus())
        .put(Fields.CURRENT_ACTION, currentAction)
        .put(Fields.UPDATED_ON, DateUtils.format(new Date(updatedOn)))
        .put(Fields.CREATED_ON, DateUtils.format(new Date(createdOn)))
        .put(Fields.CONTEXT, ModelUtils.expandMappable(context))
        .build();
  }

  public Map<String, Object> toDataMap() {
    return ImmutableMap.<String, Object>builder()
        .put(Fields.DOMAIN_ID, domainId)
        .put(Fields.ACTIVITY_ID, activity.getId())
        .put(Fields.DEFINITION_NAME, activityDefinition.getName())
        .put(Fields.VERSION, activityDefinition.getVersion())
        .put(Fields.STATUS, status.getCode())
        .put(Fields.CURRENT_ACTION, currentAction)
        .put(Fields.UPDATED_ON, updatedOn)
        .put(Fields.CREATED_ON, createdOn)
        .put(Fields.CONTEXT, ModelUtils.expandMappable(context))
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
    ActivityThread that = (ActivityThread) o;
    return activity.getId() == that.activity.getId() &&
        Objects.equals(domainId, that.domainId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(activity.getId(), domainId);
  }

  @Override
  public String toString() {
    final String data = JSON.toJSONString(toMap());
    return String.format("ActivityThread@%d%s", System.identityHashCode(this), data);
  }

  public interface Fields {

    String DOMAIN_ID = "domain_id";

    String ACTIVITY_ID = "activity_id";

    String DEFINITION_NAME = "definition_name";

    String VERSION = "version";

    String STATUS = "status";

    String CURRENT_ACTION = "current_action";

    String UPDATED_ON = "updated_on";

    String CREATED_ON = "created_on";

    String CONTEXT = "context";
  }
}
