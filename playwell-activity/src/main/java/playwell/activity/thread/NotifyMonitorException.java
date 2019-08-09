package playwell.activity.thread;

/**
 * 通知Monitor失败时，会抛出该异常，该异常不会再被Monitor处理
 */
public class NotifyMonitorException extends RuntimeException {

  public NotifyMonitorException(String message) {
    super(message);
  }

  public NotifyMonitorException(Throwable cause) {
    super(cause);
  }
}
