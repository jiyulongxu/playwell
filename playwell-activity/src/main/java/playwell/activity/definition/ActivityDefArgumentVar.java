package playwell.activity.definition;

import playwell.common.EasyMap;


/**
 * ActivityArgumentDefVar用于渲染活动定义参数中的DEFINITION变量。
 *
 * @author chihongze@gmail.com
 */
public class ActivityDefArgumentVar {

  private final ActivityDefinition activityDefinition;

  public ActivityDefArgumentVar(ActivityDefinition activityDefinition) {
    this.activityDefinition = activityDefinition;
  }

  public String getName() {
    return activityDefinition.getName();
  }

  public String getDisplayName() {
    return activityDefinition.getDisplayName();
  }

  public String getDescription() {
    return activityDefinition.getDescription();
  }

  public EasyMap getConfig() {
    return new EasyMap(activityDefinition.getConfig());
  }
}
