package playwell.trigger;

import playwell.activity.Activity;
import playwell.activity.definition.ActivityDefinition;

/**
 * 动态构建Trigger Instance的接口
 *
 * @author chihongze@gmail.com
 */
@FunctionalInterface
public interface TriggerInstanceBuilder {

  Trigger build(Activity activity, ActivityDefinition latestActivityDefinition);
}
