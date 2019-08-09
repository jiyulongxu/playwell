package playwell.common.expression;

import playwell.common.exception.PlaywellException;

/**
 * 当表达式已经被编译过，再去重复编译的时候会抛出该异常
 *
 * @author chihongze@gmail.com
 */
public class AlreadyCompiledException extends PlaywellException {

  public AlreadyCompiledException(String message) {
    super(message);
  }
}
