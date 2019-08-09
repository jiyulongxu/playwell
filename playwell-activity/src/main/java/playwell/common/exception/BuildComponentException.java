package playwell.common.exception;

/**
 * 该异常通常会用在Builder当中，当Builder构建失败时会抛出
 *
 * @author chihongze@gmail.com
 */
public class BuildComponentException extends PlaywellException {

  public BuildComponentException(String message) {
    super(message);
  }

  public BuildComponentException(String message, Throwable t) {
    super(message, t);
  }
}
