package playwell.clock;

import java.util.function.Consumer;

/**
 * 针对ClockMessage的扫描回调
 */
public interface ClockMessageScanConsumer extends Consumer<ScanClockMessageContext> {

  /**
   * 当扫描结束的时候回调该方法
   */
  default void onEOF() {
  }

  /**
   * 当扫描被终止的时候回调该方法
   */
  default void onStop() {
  }
}
