package playwell.action;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.action.builtin.CaseAction;
import playwell.action.builtin.ClockAction;
import playwell.action.builtin.ComputeAction;
import playwell.action.builtin.ConcurrentAction;
import playwell.action.builtin.DebugAction;
import playwell.action.builtin.DeleteVarAction;
import playwell.action.builtin.ForeachAction;
import playwell.action.builtin.RandomChoiceAction;
import playwell.action.builtin.ReceiveAction;
import playwell.action.builtin.SendAction;
import playwell.action.builtin.SleepAction;
import playwell.action.builtin.StdoutAction;
import playwell.action.builtin.UpdateVarAction;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.thread.ActivityThread;
import playwell.common.EasyMap;
import playwell.common.PlaywellComponent;
import playwell.common.argument.Argument;
import playwell.service.ServiceMeta;
import playwell.service.ServiceMetaManager;

/**
 * 用于注册管理所有类型的Action，并辅助在运行时动态构建各种Action实例
 *
 * @author chihongze@gmail.com
 */
public class ActionManager implements PlaywellComponent {

  private static final Logger logger = LogManager.getLogger(ActionManager.class);

  private final Map<String, ActionInstanceBuilder> instanceBuilders = new ConcurrentHashMap<>();

  private final Map<String, Consumer<Argument>> allArgSpecs = new ConcurrentHashMap<>();

  public ActionManager() {

  }

  @Override
  @SuppressWarnings({"unchecked"})
  public void init(Object config) {
    EasyMap configuration = (EasyMap) config;

    // 加载本地的Action
    final List<Class<? extends Action>> actionClassList = new LinkedList<>();

    // 内置Action
    actionClassList.add(DebugAction.class);
    actionClassList.add(UpdateVarAction.class);
    actionClassList.add(DeleteVarAction.class);
    actionClassList.add(ComputeAction.class);
    actionClassList.add(CaseAction.class);
    actionClassList.add(RandomChoiceAction.class);
    actionClassList.add(ClockAction.class);
    actionClassList.add(SleepAction.class);
    actionClassList.add(ForeachAction.class);
    actionClassList.add(ReceiveAction.class);
    actionClassList.add(StdoutAction.class);
    actionClassList.add(SendAction.class);
    actionClassList.add(ConcurrentAction.class);

    // 自定义Action
    List<String> customizeActionClassNames = configuration.getStringList(ConfigItems.ACTIONS);
    if (CollectionUtils.isNotEmpty(customizeActionClassNames)) {
      actionClassList.addAll(customizeActionClassNames.stream().map(name -> {
        try {
          return (Class<? extends Action>) Class.forName(name);
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
      }).collect(Collectors.toList()));
    }

    actionClassList.forEach(this::registerAction);

    // 加载远程的Action
//    ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
//    ServiceMetaManager serviceMetaManager = integrationPlan.getServiceMetaManager();
//    loadFromServiceMetaManager(serviceMetaManager);
  }

  /**
   * 判断Action类型是否存在
   *
   * @param type Action类型
   * @return 是否存在
   */
  public boolean isExist(String type) {
    return instanceBuilders.containsKey(type);
  }

  @SuppressWarnings({"unchecked"})
  private void registerAction(Class<? extends Action> actionClass) {
    // 获取type
    final String type;
    try {
      final Field typeField = actionClass.getField("TYPE");
      typeField.setAccessible(true);
      type = (String) typeField.get(actionClass);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    Consumer<Argument> argSpec;
    try {
      final Field specArgField = actionClass.getField("ARG_SPEC");
      specArgField.setAccessible(true);
      argSpec = (Consumer<Argument>) specArgField.get(actionClass);
      allArgSpecs.put(type, argSpec);
    } catch (NoSuchFieldException e) {
      logger.warn(String.format("The action %s has no argument spec", type));
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    // 获取builder
    ActionInstanceBuilder builder;
    try {
      final Field builderField = actionClass.getField("BUILDER");
      builderField.setAccessible(true);
      builder = (ActionInstanceBuilder) builderField.get(actionClass);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      try {
        Constructor<? extends Action> constructor = actionClass.getConstructor(
            ActionDefinition.class, ActivityThread.class);
        builder = activityThread -> {
          try {
            return constructor.newInstance(activityThread);
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

  public void registerServiceAction(String serviceName) {
    instanceBuilders.putIfAbsent(
        serviceName,
        new ServiceActionInstanceBuilder(serviceName)
    );

    // 注册ArgSpec
    final Consumer<Argument> argSpec = ServiceActionInstanceBuilder
        .ServiceAction.getArgSpec(serviceName);
    allArgSpecs.putIfAbsent(serviceName, argSpec);
  }

  /**
   * 从ServiceManager加载基于服务的Action
   *
   * @param serviceMetaManager ServiceMetaManager
   */
  private void loadFromServiceMetaManager(ServiceMetaManager serviceMetaManager) {
    final Collection<ServiceMeta> allServiceMeta = serviceMetaManager.getAllServiceMeta();
    if (CollectionUtils.isNotEmpty(allServiceMeta)) {
      allServiceMeta.forEach(serviceMeta -> {
        final String serviceName = serviceMeta.getName();
        instanceBuilders.put(
            serviceName,
            new ServiceActionInstanceBuilder(serviceName)
        );

        // 注册ArgSpec
        final Consumer<Argument> argSpec = ServiceActionInstanceBuilder
            .ServiceAction.getArgSpec(serviceName);
        allArgSpecs.put(serviceName, argSpec);
      });
    }
  }

  public Action getActionInstance(ActivityThread activityThread) {
    final ActivityDefinition activityDefinition = activityThread.getActivityDefinition();
    final ActionDefinition actionDefinition = activityDefinition
        .getActionDefinitionByName(activityThread.getCurrentAction());
    if (actionDefinition == null) {
      throw new RuntimeException(
          String.format("There is no action %s in the definition", activityThread.getCurrentAction()));
    }

    final String actionType = actionDefinition.getActionType();
    if (!instanceBuilders.containsKey(actionType)) {
      throw new RuntimeException(String.format("Unknown action type: %s", actionType));
    }

    final ActionInstanceBuilder builder = instanceBuilders.get(actionType);
    return builder.build(activityThread);
  }

  public Optional<Consumer<Argument>> getArgSpec(String type) {
    return Optional.ofNullable(allArgSpecs.get(type));
  }

  // 配置项
  interface ConfigItems {

    String ACTIONS = "actions";
  }
}
