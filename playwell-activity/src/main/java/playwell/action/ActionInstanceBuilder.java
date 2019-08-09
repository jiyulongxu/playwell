package playwell.action;

import playwell.activity.thread.ActivityThread;

/**
 * 动态构建Action Instance的接口
 *
 * @author chihongze@gmail.com
 */
@FunctionalInterface
public interface ActionInstanceBuilder {

  Action build(ActivityThread activityThread);
}
