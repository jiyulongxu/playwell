package playwell.integration;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import playwell.common.EasyMap;
import playwell.common.PlaywellComponent;

/**
 * 集成辅助工具
 *
 * @author chihongze@gmail.com
 */
public final class IntergrationUtils {

  private IntergrationUtils() {

  }

  /**
   * 从配置中加载并初始化子组件
   *
   * @param subConfigList 子组件配置列表
   * @return 子组件集合
   */
  @SuppressWarnings({"unchecked"})
  public static Collection<PlaywellComponent> loadAndInitSubComponents(
      List<Object> subConfigList) {
    if (CollectionUtils.isEmpty(subConfigList)) {
      return Collections.emptyList();
    }

    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();

    return subConfigList.stream().map(configObj -> {
      // 如果是String类型，则是引用TopComponent
      if (configObj instanceof String) {
        return integrationPlan.getTopComponent(
            TopComponentType.valueOfByName((String) configObj));
      } // 如果是map类型，则反射加载对象并初始化
      else if (configObj instanceof Map) {
        final EasyMap configuration = new EasyMap((Map<String, Object>) configObj);
        return buildAndInitComponent(configuration);
      } else {
        throw new RuntimeException(String.format("Unknown sub config type: '%s'",
            configObj.getClass().getCanonicalName()));
      }
    }).collect(Collectors.toList());
  }

  @SuppressWarnings({"unchecked"})
  public static PlaywellComponent buildAndInitComponent(EasyMap config) {
    final String className = config.getString("class");
    try {
      Class clazz = Class.forName(className);
      PlaywellComponent component = (PlaywellComponent) clazz.getDeclaredConstructor()
          .newInstance();
      component.init(config);
      return component;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
