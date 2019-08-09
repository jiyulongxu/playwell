package playwell.assertion;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import playwell.common.Mappable;
import playwell.common.expression.PlaywellExpression;

/**
 * 断言表达式定义
 *
 * @author chihongze@gmail.com
 */
public class AssertDefinition implements Mappable {

  // 表达式
  private final PlaywellExpression expression;

  // 错误消息
  private final PlaywellExpression msg;

  public AssertDefinition(PlaywellExpression expression, PlaywellExpression msg) {
    this.expression = expression;
    this.msg = msg;
  }

  public PlaywellExpression getExpression() {
    return expression;
  }

  public PlaywellExpression getMsg() {
    return msg;
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.of(
        Fields.EXPRESSION, expression.getExpressionString(),
        Fields.MSG, msg.getExpressionString()
    );
  }

  @Override
  public String toString() {
    final String dataText = JSON.toJSONString(toMap());
    return String.format("AssertDefinition@%d%s", System.identityHashCode(this), dataText);
  }

  public interface Fields {

    String EXPRESSION = "expression";

    String MSG = "msg";
  }
}
