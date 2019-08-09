package playwell.util.validate;

/**
 * 将字符串表示的Field值转化为对应类型值出错时抛出此异常
 *
 * @author chihongze@gmail.com
 */
public class FieldTypeTransException extends Exception {

  public FieldTypeTransException(String message, Throwable cause) {
    super(message, cause);
  }
}
