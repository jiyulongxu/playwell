package playwell.activity;

import java.util.Map;

/**
 * 用于监听Activity状态变化的Listener
 *
 * @author chihongze@gmail.com
 */
public interface ActivityStatusListener {

  /**
   * 初始化，在系统集成的时候将会被回调
   *
   * @param config Listener配置信息
   */
  void init(Map<String, Object> config);

  /**
   * 当一个新的活动被创建时，会回调该方法
   *
   * @param activity 活动实例
   */
  default void onCreated(Activity activity) {
  }

  /**
   * 当一个活动被暂停时，会回调该方法
   *
   * @param activity 活动实例
   */
  default void onPaused(Activity activity) {
  }

  /**
   * 当一个活动被恢复执行时，会回调该方法
   *
   * @param activity 活动实例
   */
  default void onContinued(Activity activity) {
  }

  /**
   * 当一个活动被kill时，会回调该方法
   *
   * @param activity 活动实例
   */
  default void onKilled(Activity activity) {
  }
}
