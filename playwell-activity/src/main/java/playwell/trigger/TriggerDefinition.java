package playwell.trigger;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import playwell.common.AbstractComponentBuilder;
import playwell.common.Mappable;
import playwell.common.argument.Argument;
import playwell.common.argument.MapArgument;
import playwell.common.exception.BuildComponentException;
import playwell.util.Regexpr;
import playwell.util.validate.Field;

/**
 * 触发器定义
 *
 * @author chihongze@gmail.com
 */
public class TriggerDefinition implements Mappable {

  // 触发器定义类型
  private final String type;

  // 触发器参数
  private final Argument arguments;

  // 初始化上下文变量
  private final MapArgument contextVars;

  public TriggerDefinition(String type, Argument arguments, MapArgument contextVars) {
    this.type = type;
    this.arguments = arguments;
    this.contextVars = contextVars;
  }

  public static TriggerDefinitionBuilder builder(
      String activityDefinitionName, String type) {
    return new TriggerDefinitionBuilder(activityDefinitionName, type);
  }

  public String getType() {
    return type;
  }

  public Argument getArguments() {
    return arguments;
  }

  public MapArgument getContextVars() {
    return contextVars;
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.of(
        Fields.TYPE, this.type,
        Fields.ARGS, Argument.getArgumentRepr(arguments),
        Fields.CONTEXT_VARS, Argument.getArgumentRepr(contextVars)
    );
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public String toString() {
    final String dataText = JSON.toJSONString(toMap());
    return String.format("TriggerDefinition@%d%s", System.identityHashCode(this), dataText);
  }

  public interface Fields {

    String TYPE = "type";
    Field TYPE_RULE = Field.builder(TYPE).required(true).regex(Regexpr.NESTS_INDENTIFIER_PATTERN)
        .build();

    String ARGS = "args";

    String CONTEXT_VARS = "context_vars";
  }

  public static class TriggerDefinitionBuilder extends AbstractComponentBuilder<TriggerDefinition> {

    private final String type;

    private Argument arguments = new MapArgument(Collections.emptyMap());

    private MapArgument contextVars = new MapArgument(Collections.emptyMap());

    public TriggerDefinitionBuilder(String activityDefinitionName, String type) {
      this.type = checkField(type, Fields.TYPE_RULE,
          e -> String.format("构建活动定义 '%s' 中的Trigger出现错误。%s", activityDefinitionName,
              e.getMessage()));
    }

    public TriggerDefinitionBuilder arguments(Argument arguments) {
      if (!(arguments instanceof MapArgument)) {
        throw new BuildComponentException("The trigger args error, must be dict");
      }

      MapArgument mapArguments = (MapArgument) arguments;
      if (!mapArguments.containsArg("condition")) {
        throw new BuildComponentException("There is no 'condition' in trigger args");
      }

      this.arguments = arguments;
      return this;
    }

    public TriggerDefinitionBuilder contextVars(MapArgument contextVars) {
      this.contextVars = contextVars;
      return this;
    }

    public TriggerDefinition build() {
      return new TriggerDefinition(type, arguments, contextVars);
    }
  }
}
