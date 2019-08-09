package playwell.trigger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import playwell.activity.Activity;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.definition.ActivityDefinitionManager;
import playwell.common.EasyMap;
import playwell.common.PlaywellComponent;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.trigger.builtin.SimpleEventTrigger;
import playwell.trigger.builtin.VoidTrigger;


/**
 * 用于注册管理所有类型的Trigger，并辅助在运行时动态构建各种Trigger实例
 *
 * @author chihongze@gmail.com
 */
public class TriggerManager implements PlaywellComponent {

  private final Map<String, TriggerInstanceBuilder> instanceBuilders = new HashMap<>();

  public TriggerManager() {

  }

  @Override
  @SuppressWarnings({"unchecked"})
  public void init(Object config) {
    EasyMap configuration = (EasyMap) config;

    // 注册本地trigger
    List<Class<? extends Trigger>> triggerClassList = new LinkedList<>();
    triggerClassList.add(SimpleEventTrigger.class);

    triggerClassList.addAll(configuration.getStringList(ConfigItems.TRIGGERS).stream()
        .map(className -> {
          try {
            return (Class<? extends Trigger>) Class.forName(className);
          } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
          }
        }).collect(Collectors.toList()));

    triggerClassList.forEach(this::registerTrigger);
  }

  /**
   * 注册Trigger Steps
   *
   * <ol>
   * <li>检查并获取TYPE字段的值</li>
   * <li>检查并获取BUILDER字段，如果有BUILDER，则直接使用，如果没有，则基于构造函数来创建</li>
   * <li>将TYPE和对应的TriggerInstanceBuilder保存到字典当中</li>
   * </ol>
   *
   * @param triggerClass Trigger Object Class
   */
  public void registerTrigger(Class<? extends Trigger> triggerClass) {
    // 获取type
    final String type;
    try {
      final Field typeField = triggerClass.getField("TYPE");
      typeField.setAccessible(true);
      type = (String) typeField.get(triggerClass);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    // 获取builder
    TriggerInstanceBuilder builder;
    try {
      final Field builderField = triggerClass.getField("BUILDER");
      builderField.setAccessible(true);
      builder = (TriggerInstanceBuilder) builderField.get(triggerClass);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      try {
        Constructor<? extends Trigger> constructor = triggerClass.getConstructor(Activity.class);
        builder = (activity, latestActivityDefinition) -> {
          try {
            return constructor.newInstance(activity, latestActivityDefinition);
          } catch (Exception ce) {
            throw new RuntimeException(ce);
          }
        };
      } catch (Exception ce) {
        throw new RuntimeException(ce);
      }
    }

    // 保存
    instanceBuilders.put(type, builder);
  }

  /**
   * 获取Trigger实例
   *
   * @param activity 活动对象
   * @return Trigger实例
   */
  public Trigger getTriggerInstance(Activity activity) {
    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final ActivityDefinitionManager activityDefinitionManager = (ActivityDefinitionManager) integrationPlan
        .getTopComponent(TopComponentType.ACTIVITY_DEFINITION_MANAGER);
    final Optional<ActivityDefinition> activityDefinitionOptional = activityDefinitionManager
        .getLatestEnableActivityDefinition(activity.getDefinitionName());

    // 存在最新可用的ActivityDefinition
    if (activityDefinitionOptional.isPresent()) {
      final ActivityDefinition activityDefinition = activityDefinitionOptional.get();
      final TriggerDefinition triggerDefinition = activityDefinition.getTriggerDefinition();
      final String triggerType = triggerDefinition.getType();
      if (!instanceBuilders.containsKey(triggerType)) {
        throw new RuntimeException(String.format("Unknown trigger type: %s", triggerType));
      }

      final TriggerInstanceBuilder triggerInstanceBuilder = instanceBuilders.get(triggerType);
      return triggerInstanceBuilder.build(activity, activityDefinition);
    } else {
      return new VoidTrigger(activity);
    }
  }

  // 配置项
  interface ConfigItems {

    String TRIGGERS = "triggers";
  }
}
