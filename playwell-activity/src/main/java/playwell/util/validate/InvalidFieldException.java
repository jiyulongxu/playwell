package playwell.util.validate;

/**
 * 当字段规则验证出错时，抛出此异常
 *
 * @author chihongze@gmail.com
 */
public class InvalidFieldException extends Exception {

  public InvalidFieldException(String msg) {
    super(msg);
  }
}
