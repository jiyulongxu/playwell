package playwell.trigger;

import playwell.activity.Activity;
import playwell.activity.ActivityArgumentVar;
import playwell.activity.definition.ActivityDefArgumentVar;
import playwell.activity.definition.ActivityDefinition;
import playwell.common.argument.BaseArgumentRootContext;

/**
 * 触发器参数渲染的RootContext
 *
 * @author chihongze@gmail.com
 */
public abstract class TriggerArgumentRootContext extends BaseArgumentRootContext {

  /**
   * 当前活动实例信息
   */
  public final ActivityArgumentVar ACTIVITY;

  /**
   * 当前活动定义信息
   */
  public final ActivityDefArgumentVar DEFINITION;


  public TriggerArgumentRootContext(ActivityDefinition activityDefinition, Activity activity) {
    this.ACTIVITY = new ActivityArgumentVar(activity);
    this.DEFINITION = new ActivityDefArgumentVar(activityDefinition);
  }
}
