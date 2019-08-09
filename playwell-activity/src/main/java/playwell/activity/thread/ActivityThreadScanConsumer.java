package playwell.activity.thread;

import java.util.function.Consumer;

/**
 * ActivityThread扫描回调操作抽象
 *
 * @author chihongze
 */
public interface ActivityThreadScanConsumer extends Consumer<ScanActivityThreadContext> {

  /**
   * 当扫描完毕的时候回调该方法
   */
  default void onEOF() {
  }

  /**
   * 当扫描停止时回调该方法
   */
  default void onStop() {
  }
}
