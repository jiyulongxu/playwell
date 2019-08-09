package playwell.common.argument;

import playwell.common.expression.PlaywellExpression;
import playwell.common.expression.PlaywellExpressionContext;

/**
 * 表达式参数，最原子的参数类型
 *
 * @author chihongze@gmail.com
 */
public class ExpressionArgument extends Argument {

  private final PlaywellExpression expression;

  public ExpressionArgument(PlaywellExpression expression) {
    this.expression = expression;
  }

  public PlaywellExpression getExpression() {
    return expression;
  }

  public Object getValue(PlaywellExpressionContext context) {
    return this.expression.getResult(context);
  }

  @Override
  public String toString() {
    return expression.getExpressionString();
  }
}
