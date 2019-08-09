package playwell.message;


import playwell.common.PlaywellComponent;

/**
 * 消息分发器，从指定队列中接收消息，然后调用相关的处理器处理
 *
 * @author chihongze@gmail.com
 */
public interface MessageDispatcher extends PlaywellComponent {

  /**
   * 执行消息分发 从队列源源不断的获取消息，然后通过调度器去处理消息
   */
  void dispatch();

  /**
   * MessageDispatcher是否已经启动运行
   *
   * @return 是否已经启动运行
   */
  boolean isStarted();

  /**
   * 停止一个MessageDispatcher的执行
   */
  void stop();

  /**
   * MessageDispatcher是否处于停止状态
   *
   * @return 是否停止
   */
  boolean isStopped();

  /**
   * 暂停一个MessageDispatcher的执行，暂停后可恢复
   */
  void pause();

  /**
   * 恢复一个暂停的MessageDispatcher，继续执行
   */
  void rerun();

  /**
   * MessageDispatcher是否暂停
   *
   * @return 是否暂停
   */
  boolean isPaused();
}
