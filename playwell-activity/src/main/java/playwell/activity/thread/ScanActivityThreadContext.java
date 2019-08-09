package playwell.activity.thread;

/**
 * 对ActivityThread进行迭代扫描操作时的上下文， 用于获取或者删除当前迭代的ActivityThread对象
 */
public interface ScanActivityThreadContext {

  /**
   * 获取当前迭代的ActivityThread对象
   *
   * @return 当前迭代的ActivityThread对象
   */
  ActivityThread getCurrentActivityThread();

  /**
   * 当前已经扫描的数目
   *
   * @return 当前已经扫描的ActivityThread数目
   */
  int getAllScannedNum();

  /**
   * 从ActivityThreadPool中移除当前迭代的ActivityThread
   */
  void remove();

  /**
   * 通知迭代器结束执行
   */
  void stop();
}
