package playwell.integration;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.util.StringUtils;
import playwell.common.EasyMap;
import playwell.common.PlaywellComponent;

public abstract class BaseStandardIntegrationPlan implements IntegrationPlan {

  private static final Logger logger = LogManager.getLogger(BaseStandardIntegrationPlan.class);

  // 已经被初始化了的TopComponent
  private final EnumSet<TopComponentType> inited = EnumSet.noneOf(TopComponentType.class);

  // 所有的Top Component
  private final EnumMap<TopComponentType, PlaywellComponent> allComponents = new EnumMap<>(
      TopComponentType.class);

  private final List<Closeable> closeableResources = new LinkedList<>();

  private volatile boolean ready = false;

  @Override
  public synchronized void intergrate(Map<String, Object> config) {
    if (ready) {
      throw new IllegalStateException("The IntegrationPlan has already been intergrated!");
    }
    final EasyMap configuration = new EasyMap(config).getSubArguments(ConfigItems.PLAYWELL);
    TimeZone.setDefault(TimeZone.getTimeZone(configuration.getString(ConfigItems.TIMEZONE)));
    loadAllResources(configuration);
    loadAllTopComponents(configuration);
    getTopComponents().forEach(topComponentTypeConf -> {
      TopComponentType topComponentType = topComponentTypeConf.getKey();
      if (!allComponents.containsKey(topComponentType)) {
        return;
      }
      allComponents.get(topComponentType)
          .init(configuration.getSubArguments(topComponentType.getName()));
      inited.add(topComponentType);
    });
    ready = true;
  }

  @Override
  public boolean isReady() {
    return ready;
  }

  @Override
  public PlaywellComponent getTopComponent(TopComponentType topComponentType) {
    final PlaywellComponent playwellComponent = allComponents.get(topComponentType);
    if (playwellComponent == null) {
      throw new RuntimeException(
          String.format("Component not found: %s", topComponentType.getName()));
    }
    return playwellComponent;
  }

  // 加载系统所需要的各种资源
  @SuppressWarnings({"unchecked"})
  private void loadAllResources(EasyMap configuration) {
    final List<EasyMap> subResourceConfigList = configuration.getSubArgumentsList(
        ConfigItems.RESOURCES);
    if (CollectionUtils.isNotEmpty(subResourceConfigList)) {
      subResourceConfigList.forEach(resourceConfig -> {
        final String className = resourceConfig.getString("class");
        try {
          final Class clazz = Class.forName(className);
          final Field instanceField = clazz.getDeclaredField("INSTANCE");
          instanceField.setAccessible(true);
          final Object instance = instanceField.get(clazz);
          if (instance instanceof Closeable) {
            closeableResources.add((Closeable) instance);
          }
          final Method initMethod = clazz.getMethod("init", EasyMap.class);
          initMethod.invoke(instance, resourceConfig);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    }
  }

  // 加载所有的顶级Component
  @SuppressWarnings({"unchecked"})
  private void loadAllTopComponents(EasyMap configuration) {
    getTopComponents().forEach(topComponentTypeConf -> {
      final TopComponentType topComponentType = topComponentTypeConf.getKey();
      final Boolean required = topComponentTypeConf.getValue();

      EasyMap componentConfig = configuration.getSubArguments(topComponentType.getName());
      String className = componentConfig.getString(ConfigItems.CLASS, "");

      // 非必须且配置为空
      if (!required && componentConfig.isEmpty()) {
        logger.info(
            String.format("The component %s has no configuration", topComponentType.getName()));
        return;
      }

      try {
        PlaywellComponent componentObject;
        if (StringUtils.isEmpty(className)) {
          // 基于默认类型来构建
          componentObject = TopComponentType.valueOfByName(topComponentType.getName())
              .getDefaultImplemention().getConstructor().newInstance();
        } else {
          // 基于指定类型来构建
          Class clazz = Class.forName(className);
          componentObject = (PlaywellComponent) clazz.getDeclaredConstructor().newInstance();
        }

        allComponents.put(topComponentType, componentObject);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  @SuppressWarnings({"unchecked"})
  protected <T extends PlaywellComponent> T checkInitThenReturn(TopComponentType topComponentType) {
    if (!inited.contains(topComponentType)) {
      throw new IllegalStateException(
          String.format("The topComponentType %s has not been inited", topComponentType.getName()));
    }
    return (T) allComponents.get(topComponentType);
  }

  protected abstract List<Pair<TopComponentType, Boolean>> getTopComponents();

  @Override
  public boolean contains(TopComponentType topComponentType) {
    return allComponents.containsKey(topComponentType);
  }

  @Override
  public void closeAll() {
    close();

    closeableResources.forEach(resource -> {
      try {
        resource.close();
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
    });
  }

  protected abstract void close();

  interface ConfigItems {

    String PLAYWELL = "playwell";

    String TIMEZONE = "tz";

    String CLASS = "class";

    String RESOURCES = "resources";
  }

}
