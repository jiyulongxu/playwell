package playwell.common;

import java.util.Map;
import java.util.function.Function;
import playwell.common.exception.BuildComponentException;
import playwell.common.expression.PlaywellExpression;
import playwell.util.Regexpr;
import playwell.util.validate.Field;
import playwell.util.validate.InvalidFieldException;

/**
 * Abstract Component Builder
 *
 * @author chihongze@gmail.com
 */
public abstract class AbstractComponentBuilder<T> {

  protected <F> F checkField(F field, Field rule,
      Function<InvalidFieldException, String> msgBuilder) {
    try {
      return rule.validate(field);
    } catch (InvalidFieldException e) {
      throw new BuildComponentException(msgBuilder.apply(e));
    }
  }

  protected void addExpressionArg(
      String compiler, String name, String expressionStr, Map<String, PlaywellExpression> args,
      String invalidArgNameMsg, String argNameExistMsg,
      Function<Exception, String> compileErrorMsg) {
    if (!Regexpr.isMatch(Regexpr.NESTS_INDENTIFIER_PATTERN, name)) {
      throw new BuildComponentException(invalidArgNameMsg);
    }

    if (args.containsKey(name)) {
      throw new BuildComponentException(argNameExistMsg);
    }

    PlaywellExpression expression;
    try {
      expression = PlaywellExpression.compile(compiler, expressionStr);
    } catch (Exception e) {
      throw new BuildComponentException(compileErrorMsg.apply(e));
    }

    args.put(name, expression);
  }

  public abstract T build();
}
