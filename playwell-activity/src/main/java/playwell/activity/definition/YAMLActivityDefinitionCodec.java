package playwell.activity.definition;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.yaml.snakeyaml.Yaml;
import playwell.action.ActionDefinition;
import playwell.action.ActionManager;
import playwell.common.EasyMap;
import playwell.common.argument.Argument;
import playwell.common.argument.MapArgument;
import playwell.common.exception.BuildComponentException;
import playwell.common.expression.PlaywellExpression;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.message.domainid.MessageDomainIDStrategyManager;
import playwell.trigger.TriggerDefinition;


/**
 * 基于YAML格式的ActivityDefinition解码器
 *
 * @author chihongze@gmail.com
 */
public class YAMLActivityDefinitionCodec implements ActivityDefinitionCodec {

  public static final String NAME = "yaml";

  public YAMLActivityDefinitionCodec() {

  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void init(Object config) {
    // Do Nothing
  }

  @Override
  public ActivityDefinition decode(
      String version, boolean enable, String definitionText, Date createdOn, Date updatedOn)
      throws BuildComponentException {
    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final Yaml yaml = new Yaml();
    final EasyMap data = new EasyMap(yaml.load(definitionText));

    // 取得顶层Activity元素
    if (!data.contains("activity")) {
      throw new BuildComponentException("There is no top activity element in the definition text");
    }
    EasyMap activityData = data.getSubArguments("activity");

    // 解析基本的元信息
    if (!activityData.contains("name")) {
      throw new BuildComponentException(
          "[activity.name] The name argument of activity is required");
    }
    final String name = activityData.getString("name");

    final String domainIdStrategy = activityData.getString("domain_id_strategy");
    final MessageDomainIDStrategyManager domainIDStrategyManager = (MessageDomainIDStrategyManager) integrationPlan
        .getTopComponent(TopComponentType.MESSAGE_DOMAIN_ID_STRATEGY_MANAGER);
    final boolean didStrategyExisted = domainIDStrategyManager.getAllMessageDomainIDStrategies()
        .stream().anyMatch(s -> s.name().equals(domainIdStrategy));
    if (!didStrategyExisted) {
      throw new BuildComponentException(String.format(
          "Could not found the domain id strategy: %s", domainIdStrategy));
    }

    final String displayName = activityData.getString("display_name", name);
    final String description = activityData.getString("description", displayName);

    // 解析配置
    final EasyMap config = activityData.getSubArguments("config");

    // 解析Trigger定义
    final Object triggerDefObj = activityData.get("trigger");
    final TriggerDefinition.TriggerDefinitionBuilder triggerDefinitionBuilder;
    if (triggerDefObj instanceof Map) {
      final EasyMap triggerData = activityData.getSubArguments("trigger");
      if (!triggerData.contains("type")) {
        throw new BuildComponentException(
            "[activity.trigger.type] The type argument of trigger is required");
      }
      final String triggerType = triggerData.getString("type");
      triggerDefinitionBuilder = TriggerDefinition.builder(name, triggerType);
      if (triggerData.contains("args")) {
        triggerDefinitionBuilder.arguments(
            Argument.parse(triggerData.get("args"), PlaywellExpression.Compilers.SPRING_EL));
      }
      if (triggerData.contains("context_vars")) {
        triggerDefinitionBuilder.contextVars(
            (MapArgument) Argument
                .parse(triggerData.get("context_vars"), PlaywellExpression.Compilers.SPRING_EL));
      }
    } else if (triggerDefObj instanceof String) {
      triggerDefinitionBuilder = TriggerDefinition.builder(name, "event");
      triggerDefinitionBuilder.arguments(Argument.parse(
          ImmutableMap.of("condition", (String) triggerDefObj),
          PlaywellExpression.Compilers.SPRING_EL));
    } else {
      throw new BuildComponentException("Invalid trigger definition, only accept map or string");
    }

    // 构建Builder
    final ActivityDefinition.ActivityDefinitionBuilder builder = ActivityDefinition
        .builder(
            name,
            version,
            NAME,
            domainIdStrategy,
            triggerDefinitionBuilder.build(),
            enable,
            definitionText,
            createdOn,
            updatedOn
        );
    builder.displayName(displayName);
    builder.description(description);
    builder.addConfigItems(config.toMap());

    // 构建Actions
    final List<EasyMap> actionDataList = activityData.getSubArgumentsList("actions");
    if (CollectionUtils.isEmpty(actionDataList)) {
      throw new BuildComponentException(
          String.format("Could not found actions definition in the activity: '%s'", name));
    }

    for (int i = 0; i < actionDataList.size(); i++) {
      try {
        final EasyMap actionData = actionDataList.get(i);
        builder.addAction(buildAction(name, actionData));
      } catch (Exception e) {
        throw new BuildComponentException(
            String.format(
                "Build action failure, activity = '%s', action index = %d. %s",
                name,
                i,
                e.getMessage()
            ),
            e
        );
      }
    }

    return builder.build();
  }

  // 构建ActionDefinition
  private ActionDefinition buildAction(String activityDefinitionName, EasyMap actionData) {
    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final ActionManager actionManager = (ActionManager) integrationPlan.getTopComponent(
        TopComponentType.ACTION_MANAGER);

    // 获取基本元数据
    if (!actionData.contains("name")) {
      throw new BuildComponentException("The name argument of action is required.");
    }
    final String name = actionData.getString("name");
    final String type = actionData.getString("type", name);
    if (!actionManager.isExist(type)) {
      throw new BuildComponentException(String.format("Unknown action type: %s", type));
    }

    // Builder
    final ActionDefinition.ActionDefinitionBuilder builder = ActionDefinition.builder(
        activityDefinitionName, PlaywellExpression.Compilers.SPRING_EL, name, type);

    // 获取 & 检查参数
    if (actionData.contains("args")) {
      Argument argument = Argument.parse(
          actionData.get("args"), PlaywellExpression.Compilers.SPRING_EL);
      actionManager.getArgSpec(type).ifPresent(spec -> spec.accept(argument));
      builder.arguments(argument);
    }

    // 处理控制条件
    if (actionData.get("ctrl") instanceof List) {
      final List<EasyMap> ctrlDataList = actionData.getSubArgumentsList("ctrl");
      for (EasyMap ctrlData : ctrlDataList) {
        if (ctrlData.contains("when")) {
          final String when = ctrlData.getString("when");
          final String then = ctrlData.getString("then");
          final Map<String, Object> contextVars = ctrlData.getSubArguments("context_vars").toMap();
          builder.addCtrlCondition(when, then, contextVars);
        } else if (ctrlData.contains("default")) {
          builder.defaultCtrlExpression(ctrlData.getString("default"));
          builder.defaultContextVars(ctrlData.getSubArguments("context_vars").toMap());
        }
      }
    } else if (actionData.get("ctrl") instanceof String) {
      builder.defaultCtrlExpression(actionData.getString("ctrl"));
      builder.defaultContextVars(Collections.emptyMap());
    }

    builder.await(actionData.getBoolean("await", true));

    return builder.build();
  }

}
