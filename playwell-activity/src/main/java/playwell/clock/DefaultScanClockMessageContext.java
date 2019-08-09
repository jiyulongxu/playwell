package playwell.clock;

/**
 * DefaultScanClockMessageContext
 */
public class DefaultScanClockMessageContext implements ScanClockMessageContext {

  private final Clock clock;

  private final int allScannedNum;

  private final ClockMessage clockMessage;

  public DefaultScanClockMessageContext(Clock clock, int allScannedNum, ClockMessage clockMessage) {
    this.clock = clock;
    this.allScannedNum = allScannedNum;
    this.clockMessage = clockMessage;
  }

  @Override
  public ClockMessage getCurrentClockMessage() {
    return this.clockMessage;
  }

  @Override
  public int getAllScannedNum() {
    return this.allScannedNum;
  }

  @Override
  public void stop() {
    this.clock.stopScan();
  }
}
