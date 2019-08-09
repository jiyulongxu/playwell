package playwell.common.expression.spel;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.collections4.MapUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import playwell.common.expression.AlreadyCompiledException;
import playwell.common.expression.NotCompiledException;
import playwell.common.expression.PlaywellExpression;
import playwell.common.expression.PlaywellExpressionContext;

/**
 * 针对Spring-el Expression对象的封装
 *
 * @author chihongze@gmail.com
 */
public class SpELPlaywellExpression implements PlaywellExpression {

  public static final ExpressionParser DEFAULT_PARSER = new SpelExpressionParser();

  // 表达式编译所需要使用的Parser
  private final ExpressionParser elParser;

  // 原始的表达式字符串
  private final String expressionString;

  // 编译后的表达式
  private volatile Expression compiledExpression = null;

  // 默认上下文
  private PlaywellExpressionContext defaultContext = new SpELPlaywellExpressionContext();

  public SpELPlaywellExpression(String expressionString) {
    this(expressionString, DEFAULT_PARSER);
  }

  public SpELPlaywellExpression(String expressionString, ExpressionParser elParser) {
    this.elParser = elParser;
    this.expressionString = expressionString;
  }

  @Override
  public synchronized PlaywellExpression compile() {
    if (compiledExpression != null) {
      throw new AlreadyCompiledException(
          String.format("The expression '%s' has already been compiled", expressionString));
    }
    if (expressionString.contains("${")) {
      compiledExpression = elParser.parseExpression(
          expressionString, new TemplateParserContext("${", "}"));
    } else {
      compiledExpression = elParser.parseExpression(expressionString);
    }
    return this;
  }

  @Override
  public synchronized PlaywellExpression compile(PlaywellExpressionContext defaultContext) {
    this.compile();
    this.defaultContext = defaultContext;
    return this;
  }

  @Override
  public String getExpressionString() {
    return this.expressionString;
  }

  @Override
  public Object getResult() {
    checkCompiled();
    if (this.defaultContext == null) {
      return compiledExpression.getValue();
    } else {
      return compiledExpression.getValue(
          ((SpELPlaywellExpressionContext) defaultContext).getStandardEvaluationContext());
    }
  }

  @Override
  public Object getResult(PlaywellExpressionContext context) {
    checkCompiled();
    return compiledExpression.getValue(
        ((SpELPlaywellExpressionContext) context).getStandardEvaluationContext());
  }

  @Override
  public Object getResult(PlaywellExpressionContext context, boolean extendsDefaultContext) {
    checkCompiled();
    if (extendsDefaultContext) {
      extendsDefaultContext(context);
    }
    return compiledExpression.getValue(
        ((SpELPlaywellExpressionContext) context).getStandardEvaluationContext());
  }

  @Override
  public <T> T getResultWithType(Class<T> clazz) {
    checkCompiled();
    if (this.defaultContext == null) {
      return compiledExpression.getValue(clazz);
    } else {
      return compiledExpression.getValue(
          ((SpELPlaywellExpressionContext) defaultContext).getStandardEvaluationContext(), clazz);
    }
  }

  @Override
  public <T> T getResultWithType(PlaywellExpressionContext context, Class<T> clazz) {
    checkCompiled();
    return compiledExpression.getValue(
        ((SpELPlaywellExpressionContext) context).getStandardEvaluationContext(), clazz);
  }

  @Override
  public <T> T getResultWithType(PlaywellExpressionContext context, Class<T> clazz,
      boolean extendsDefaultContext) {
    checkCompiled();
    if (extendsDefaultContext) {
      extendsDefaultContext(context);
    }
    return compiledExpression.getValue(
        ((SpELPlaywellExpressionContext) context).getStandardEvaluationContext(), clazz);
  }

  // 判断当前表达式是否已经被编译过，如果没有编译，那么会抛出NotCompiledException
  private void checkCompiled() {
    if (this.compiledExpression == null) {
      throw new NotCompiledException(
          String.format("The expression '%s' has not been compiled", this.expressionString));
    }
  }

  // 继承默认上下文后得到新的上下文
  private void extendsDefaultContext(PlaywellExpressionContext currentContext) {
    if (defaultContext == null || currentContext == null) {
      // 我可以回答你一句无可继承
      return;
    }

    // Handle functions
    if (MapUtils.isNotEmpty(defaultContext.getAllRegisteredFunctions())) {
      for (Map.Entry<String, Method> entry : defaultContext.getAllRegisteredFunctions()
          .entrySet()) {
        String funcName = entry.getKey();
        if (!currentContext.getAllRegisteredFunctions().containsKey(funcName)) {
          currentContext.setFunction(funcName, entry.getValue());
        }
      }
    }

    // Handle variables
    if (MapUtils.isNotEmpty(defaultContext.getAllRegisteredVariables())) {
      for (Map.Entry<String, Object> entry : defaultContext.getAllRegisteredVariables()
          .entrySet()) {
        String varName = entry.getKey();
        if (!currentContext.getAllRegisteredVariables().containsKey(varName)) {
          currentContext.setVariable(varName, entry.getValue());
        }
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SpELPlaywellExpression that = (SpELPlaywellExpression) o;
    return Objects.equals(expressionString, that.expressionString);
  }

  @Override
  public int hashCode() {
    return Objects.hash(expressionString);
  }

  @Override
  public String toString() {
    return this.expressionString;
  }
}
