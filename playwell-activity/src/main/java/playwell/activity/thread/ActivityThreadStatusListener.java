package playwell.activity.thread;


import playwell.common.PlaywellComponent;

/**
 * 用于监听ActivityThread状态变化的监听器
 *
 * @author chihongze@gmail.com
 */
public interface ActivityThreadStatusListener extends PlaywellComponent {

  /**
   * 当新的ActivityThread被创建时，回调该方法
   *
   * @param newActivityThread 新的ActivityThread
   */
  default void onSpawn(ActivityThread newActivityThread) {
  }

  /**
   * 当ActivityThreadStatus状态发生了变化时，会回调该方法
   *
   * @param oldStatus 旧有的状态
   * @param targetThread 状态已经被改变的ActivityThread
   */
  default void onStatusChange(ActivityThreadStatus oldStatus, ActivityThread targetThread) {
  }

  /**
   * 当Activity调度出错时，回调该方法
   *
   * @param targetThread 目标ActivityThread
   * @param exception 错误异常信息
   */
  default void onScheduleError(ActivityThread targetThread, Throwable exception) {
  }

  /**
   * 当ActivityThread进入待修复状态时，回调该方法
   *
   * @param activityThread 待修复的ActivityThread
   * @param problem 问题描述
   */
  default void onRepair(ActivityThread activityThread, String problem) {
  }
}
