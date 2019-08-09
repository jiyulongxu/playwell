package playwell.common.exception;

/**
 * Playwell Base Exception，Playwell中所有的自定义异常均需要继承此类。
 *
 * @author chihongze@gmail.com
 */
public class PlaywellException extends RuntimeException {

  public PlaywellException(String message) {
    super(message);
  }

  public PlaywellException(String message, Throwable t) {
    super(message, t);
  }
}
