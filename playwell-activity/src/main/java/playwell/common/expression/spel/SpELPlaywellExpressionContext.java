package playwell.common.expression.spel;


import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import playwell.common.expression.PlaywellExpressionContext;

/**
 * 基于Spring EL StandardEvaluationContext所实现的Context对象
 *
 * @author chihongze@gmail.com
 */
public class SpELPlaywellExpressionContext implements PlaywellExpressionContext {

  private final StandardEvaluationContext ctx;

  private Map<String, Method> registeredFunctions;

  private Map<String, Object> registeredVariables;

  public SpELPlaywellExpressionContext() {
    this.ctx = new StandardEvaluationContext();
    this.registeredFunctions = null;
    this.registeredVariables = null;
  }

  @Override
  public Optional<Object> getRootObject() {
    return Optional.ofNullable(ctx.getRootObject().getValue());
  }

  @Override
  public void setRootObject(Object rootObject) {
    this.ctx.setRootObject(rootObject);
  }

  @Override
  public void setFunction(String name, Method mtd) {
    if (registeredFunctions == null) {
      registeredFunctions = new HashMap<>();
    }
    this.ctx.registerFunction(name, mtd);
    this.registeredFunctions.put(name, mtd);
  }

  @Override
  public Optional<Method> getFunction(String name) {
    return Optional.of(registeredFunctions.get(name));
  }

  @Override
  public void setVariable(String name, Object value) {
    if (registeredVariables == null) {
      registeredVariables = new HashMap<>();
    }
    this.ctx.setVariable(name, value);
    this.registeredVariables.put(name, value);
  }

  @Override
  public Optional<Object> getVariable(String name) {
    return Optional.of(registeredVariables.get(name));
  }

  public StandardEvaluationContext getStandardEvaluationContext() {
    return ctx;
  }

  @Override
  public Map<String, Method> getAllRegisteredFunctions() {
    return registeredFunctions;
  }

  @Override
  public Map<String, Object> getAllRegisteredVariables() {
    return registeredVariables;
  }

}
