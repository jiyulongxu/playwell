package playwell.baas.domain;

import java.util.regex.Pattern;

/**
 * Domain属性操作符
 */
public interface Operators {

  /**
   * 删除操作符
   */
  Pattern DELETE = Pattern.compile("^\\$delete$");

  /**
   * 递增操作符
   */
  Pattern INCR = Pattern.compile("^\\$incr\\s+(-*\\d+)$");

  /**
   * 递减操作符
   */
  Pattern DECR = Pattern.compile("^\\$decr\\s+(-*\\d+)$");
}
