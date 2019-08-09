package playwell.common.exception;

/**
 * 当检查到用户提供的参数不合法时，会抛出此异常。 该异常信息中会包含参数名称、参数描述以及错误值来辅助用户排查问题
 *
 * @author chihongze@gmail.com
 */
public class InvalidArgumentException extends PlaywellException {

  // 不合法的参数名称
  private final String name;

  // 错误描述
  private final String description;

  // 错误的原始参数值
  private final Object errorValue;


  public InvalidArgumentException(String name, String description, Object errorValue) {
    super(String.format(
        "Invalid argument: '%s', because: '%s', error value: %s", name, description, errorValue));
    this.name = name;
    this.description = description;
    this.errorValue = errorValue;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public Object getErrorValue() {
    return errorValue;
  }
}
