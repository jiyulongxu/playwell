package playwell.action;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import playwell.common.Mappable;
import playwell.common.expression.PlaywellExpression;

/**
 * Action控制条件表达，由一个返回布尔值的条件判断和一个ActionCtrlInfo组成
 *
 * @author chihongze@gmail.com
 */
public class ActionCtrlCondition implements Mappable {

  // 判断条件
  private final PlaywellExpression whenCondition;

  // 控制动作
  private final PlaywellExpression thenExpression;

  // 要更新的上下文变量
  private final Map<String, PlaywellExpression> contextVars;

  public ActionCtrlCondition(
      PlaywellExpression whenCondition,
      PlaywellExpression thenExpression,
      Map<String, PlaywellExpression> contextVars) {
    this.whenCondition = whenCondition;
    this.thenExpression = thenExpression;
    this.contextVars = contextVars;
  }

  public PlaywellExpression getWhenCondition() {
    return whenCondition;
  }

  public PlaywellExpression getThenExpression() {
    return thenExpression;
  }

  public Map<String, PlaywellExpression> getContextVars() {
    return contextVars;
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.of(
        Fields.WHEN,
        whenCondition.toString(),
        Fields.THEN,
        thenExpression.getExpressionString(),
        Fields.CONTEXT_VARS,
        contextVars
    );
  }

  @Override
  public String toString() {
    final String dataString = JSON.toJSONString(toMap());
    return String.format("ActionCtrlCondition@%d%s", System.identityHashCode(this), dataString);
  }

  public interface Fields {

    String WHEN = "when";

    String THEN = "then";

    String CONTEXT_VARS = "context_vars";
  }
}
