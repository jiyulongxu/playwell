package playwell.common.expression;

import playwell.common.exception.PlaywellException;

/**
 * 如果使用了未经编译的表达式进行求值，那么就会抛出此异常
 *
 * @author chihongze@gmail.com
 */
public class NotCompiledException extends PlaywellException {

  public NotCompiledException(String message) {
    super(message);
  }
}
