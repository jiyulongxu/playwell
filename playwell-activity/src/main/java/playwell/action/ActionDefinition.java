package playwell.action;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.MapUtils;
import playwell.assertion.AssertDefinition;
import playwell.common.AbstractComponentBuilder;
import playwell.common.Mappable;
import playwell.common.argument.Argument;
import playwell.common.argument.MapArgument;
import playwell.common.expression.PlaywellExpression;
import playwell.util.Regexpr;
import playwell.util.validate.Field;

/**
 * <p>ActionDefinition中保存了从外界加载的Action定义，
 * 执行引擎在执行时会去参照这些定义，并基于当前的执行上下文来构建真正的Action对象</p>
 * <p>
 * 本质上，ActionDefinition定义了两种类型的参数：<br/>
 *
 * <ul>
 * <li>供执行引擎参照的执行参数：比如执行概率、流程控制等等</li>
 * <li>供Action自身执行使用的静态参数，这些参数是写死在定义中的，未经上下文变量去动态的渲染</li>
 * </ul>
 *
 * @author chihongze@gmail.com
 */
public class ActionDefinition implements Mappable {

  // Action名称
  private final String name;

  // 引用的Action类型
  private final String actionType;

  // Action的参数
  private final Argument arguments;

  // 控制表达式
  private final List<ActionCtrlCondition> ctrlConditions;

  // 默认控制表达式
  private final PlaywellExpression defaultCtrlExpression;

  // 默认上下文表达式
  private final Map<String, PlaywellExpression> defaultContextVars;

  // 断言表达式
  private final List<AssertDefinition> assertions;

  // 是否等待响应结果
  private final boolean await;


  public ActionDefinition(
      String name, String actionType,
      Argument arguments,
      List<ActionCtrlCondition> ctrlConditions,
      PlaywellExpression defaultCtrlExpression,
      Map<String, PlaywellExpression> defaultContextVars,
      List<AssertDefinition> assertions,
      boolean await) {
    this.name = name;
    this.actionType = actionType;
    this.arguments = arguments;
    this.ctrlConditions = ctrlConditions;
    this.defaultCtrlExpression = defaultCtrlExpression;
    this.defaultContextVars = defaultContextVars;
    this.assertions = assertions;
    this.await = await;
  }

  public static ActionDefinitionBuilder builder(
      String activityDefinitionName, String compiler, String name, String actionType) {
    return new ActionDefinitionBuilder(activityDefinitionName, compiler, name, actionType);
  }

  public String getName() {
    return name;
  }

  public String getActionType() {
    return actionType;
  }

  public Argument getArguments() {
    return arguments;
  }

  public List<ActionCtrlCondition> getCtrlConditions() {
    return ctrlConditions;
  }

  public PlaywellExpression getDefaultCtrlExpression() {
    return this.defaultCtrlExpression;
  }

  public Map<String, PlaywellExpression> getDefaultContextVars() {
    return defaultContextVars;
  }

  public List<AssertDefinition> getAssertions() {
    return assertions;
  }

  public boolean isAwait() {
    return await;
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.<String, Object>builder()
        .put(Fields.NAME, this.name)
        .put(Fields.TYPE, this.actionType)
        .put(Fields.ARGS, Argument.getArgumentRepr(arguments))
        .put(Fields.CTRL,
            ctrlConditions.stream().map(ActionCtrlCondition::toMap).collect(Collectors.toList()))
        .put(Fields.CTRL_DEFAULT, defaultCtrlExpression.getExpressionString())
        .put(Fields.CTRL_DEFAULT_CONTEXT, this.defaultContextVars)
        .put(Fields.ASSERTIONS, assertions.stream()
            .map(a -> ImmutableMap.of(
                AssertDefinition.Fields.EXPRESSION, a.getExpression().getExpressionString(),
                AssertDefinition.Fields.MSG, a.getMsg().getExpressionString()
            )).collect(Collectors.toList()))
        .put(Fields.AWAIT, this.await)
        .build();
  }

  @Override
  public String toString() {
    final String dataText = JSON.toJSONString(toMap());
    return String.format("ActionDefinition@%d%s", System.identityHashCode(this), dataText);
  }

  // 字段名常量
  public interface Fields {

    String NAME = "name";
    Field NAME_RULE = Field.builder(NAME).required(true).regex(Regexpr.NESTS_INDENTIFIER_PATTERN)
        .build();

    String TYPE = "type";
    Field TYPE_RULE = Field.builder(TYPE).required(true).regex(Regexpr.NESTS_INDENTIFIER_PATTERN)
        .build();

    String ARGS = "args";

    String CTRL = "ctrl";

    String CTRL_DEFAULT = "ctrl_default";

    String CTRL_DEFAULT_CONTEXT = "ctrl_default_context";

    String ASSERTIONS = "assert";

    String AWAIT = "await";
  }

  public static class ActionDefinitionBuilder extends AbstractComponentBuilder<ActionDefinition> {

    private final String activityDefinitionName;

    private final String compiler;

    private final String name;

    private final String actionType;

    private final List<ActionCtrlCondition> ctrlConditions = new ArrayList<>();

    private final List<AssertDefinition> assertions = new ArrayList<>();

    private Argument arguments = new MapArgument(Collections.emptyMap());

    private PlaywellExpression defaultCtrlExpression = null;

    private Map<String, PlaywellExpression> defaultContextVars = new HashMap<>();

    private boolean await = true;

    public ActionDefinitionBuilder(String activityDefinitionName, String compiler, String name,
        String actionType) {
      this.activityDefinitionName = activityDefinitionName;
      this.compiler = compiler;
      this.name = checkField(
          name, Fields.NAME_RULE,
          e -> String
              .format("构建活动定义 '%s' 中的Action出现错误。%s", activityDefinitionName, e.getMessage()));
      this.actionType = checkField(
          actionType, Fields.TYPE_RULE,
          e -> String.format("构建活动定义 '%s' 中的Action '%s' 出现错误。%s",
              activityDefinitionName, this.name, e.getMessage()));
    }

    public ActionDefinitionBuilder arguments(Argument arguments) {
      this.arguments = arguments;
      return this;
    }

    public ActionDefinitionBuilder addCtrlCondition(
        String whenCondition, String thenExpression, Map<String, Object> rawContextExpressions) {
      Map<String, PlaywellExpression> contextVarExpressions;
      if (MapUtils.isEmpty(rawContextExpressions)) {
        contextVarExpressions = Collections.emptyMap();
      } else {
        contextVarExpressions = new HashMap<>(rawContextExpressions.size());
        for (Map.Entry<String, Object> entry : rawContextExpressions.entrySet()) {
          final String varName = entry.getKey();
          final String expression = entry.getValue() == null ? "" : entry.getValue().toString();
          this.addExpressionArg(
              compiler,
              varName,
              expression,
              contextVarExpressions,
              String.format("构建活动定义 '%s' 中的Action '%s' 出现错误。Action的上下文变量名称 '%s' 不是合法的标识符。",
                  activityDefinitionName, this.name, name),
              String.format("构建活动定义 '%s' 中的Action '%s' 出现错误。Action的上下文变量 '%s' 已经存在。",
                  activityDefinitionName, this.name, name),
              e -> String.format("构建活动定义 '%s' 中的Action '%s' 出现错误。上下文变量表达式 '%s': '%s'编译出错。%s",
                  activityDefinitionName, this.name, name, expression, e.getMessage())
          );
        }
      }
      final ActionCtrlCondition ctrlCondition = new ActionCtrlCondition(
          PlaywellExpression.compile(compiler, whenCondition),
          PlaywellExpression.compile(compiler, thenExpression),
          contextVarExpressions
      );
      ctrlConditions.add(ctrlCondition);
      return this;
    }

    public ActionDefinitionBuilder defaultCtrlExpression(String ctrlExpression) {
      this.defaultCtrlExpression = PlaywellExpression.compile(compiler, ctrlExpression);
      return this;
    }

    public ActionDefinitionBuilder defaultContextVars(Map<String, Object> rawContextVars) {
      if (MapUtils.isNotEmpty(rawContextVars)) {
        for (Map.Entry<String, Object> entry : rawContextVars.entrySet()) {
          final String varName = entry.getKey();
          final String expressionStr = entry.getValue() == null ? "" : entry.getValue().toString();
          super.addExpressionArg(
              compiler,
              varName,
              expressionStr,
              defaultContextVars,
              String.format("构建活动定义 '%s' 中的Action '%s' 出现错误。Action的上下文变量名称 '%s' 不是合法的标识符。",
                  activityDefinitionName, this.name, name),
              String.format("构建活动定义 '%s' 中的Action '%s' 出现错误。Action的上下文变量 '%s' 已经存在。",
                  activityDefinitionName, this.name, name),
              e -> String.format("构建活动定义 '%s' 中的Action '%s' 出现错误。上下文变量表达式 '%s': '%s'编译出错。%s",
                  activityDefinitionName, this.name, name, expressionStr, e.getMessage())
          );
        }
      }
      return this;
    }

    public ActionDefinitionBuilder await(boolean await) {
      this.await = await;
      return this;
    }

    public ActionDefinitionBuilder addAssertion(AssertDefinition assertDefinition) {
      this.assertions.add(assertDefinition);
      return this;
    }

    @Override
    public ActionDefinition build() {
      return new ActionDefinition(
          name,
          actionType,
          arguments,
          ctrlConditions,
          defaultCtrlExpression,
          defaultContextVars,
          assertions,
          await
      );
    }
  }
}
