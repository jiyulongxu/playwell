package playwell.common.argument;

import playwell.activity.thread.ActivityThreadArgumentVar;

/**
 * 为提供ActivityThread元数据的参数表达式提供便捷访问接口
 */
public interface ActivityThreadAccessMixin {

  ActivityThreadArgumentVar getActivityThread();

  default int getActivityId() {
    return getActivityThread().getActivityId();
  }

  default String getDomainId() {
    return getActivityThread().getDomainId();
  }
}
