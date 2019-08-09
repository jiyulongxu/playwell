package playwell.common;

/**
 * 抽象了一种逻辑行为，用于比较当前进程所持有的数据版本，是否与实际存储中 当前最新的版本一致，如果是，则不回调指定逻辑，如果不是，则需要回调。
 *
 * @author chihongze@gmail.com
 */
public interface CompareAndCallback {

  /**
   * 如果传递的expectedVersion和实际的version不一致，则回调callback
   *
   * @param expectedVersion Expected version
   * @param callback callback logic
   * @return 最新的版本号
   */
  int compareAndCallback(int expectedVersion, Runnable callback);

  /**
   * 更新实际的版本号
   */
  void updateVersion();
}
