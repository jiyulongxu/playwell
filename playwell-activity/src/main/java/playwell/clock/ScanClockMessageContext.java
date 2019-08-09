package playwell.clock;

/**
 * ScanClockMessageContext
 */
public interface ScanClockMessageContext {

  /**
   * 获取当前迭代到的ClockMessage
   *
   * @return ClockMessage
   */
  ClockMessage getCurrentClockMessage();

  /**
   * 获取扫描过的消息总数
   *
   * @return 扫描过的消息总数
   */
  int getAllScannedNum();

  /**
   * 停止扫描
   */
  void stop();
}
