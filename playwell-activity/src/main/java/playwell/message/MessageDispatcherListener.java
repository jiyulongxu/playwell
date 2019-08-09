package playwell.message;


import playwell.common.PlaywellComponent;

/**
 * MessageDispatcherListener可以切入到MessageDispatcher运行的各个生命周期， 从而可以对MessageDispatcher进行一系列额外的扩展。
 *
 * @author chihongze@gmail.com
 */
public interface MessageDispatcherListener extends PlaywellComponent {

  /**
   * 系统集成的时候会回调该方法对Listener进行初始化
   *
   * @param config Listener配置数据
   */
  void init(Object config);

  /**
   * beginLoop方法会在一次循环之初被回调
   */
  default void beforeLoop() {
  }

  /**
   * afterLoop方法会在一次循环结束后被回调
   */
  default void afterLoop() {
  }

  /**
   * errorHappened方法会在出错时被回调
   *
   * @param error 异常信息
   */
  default void errorHappened(Throwable error) {
  }
}
