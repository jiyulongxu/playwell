package playwell.service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.collections4.CollectionUtils;
import playwell.action.ActionManager;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.IntergrationUtils;
import playwell.integration.TopComponentType;

/**
 * 基于内存的ServiceMetaManager
 *
 * @author chihongze@gmail.com
 */
public class MemoryServiceMetaManager implements ServiceMetaManager {

  private final Map<String, ServiceMeta> allServiceMeta = new ConcurrentHashMap<>();

  public MemoryServiceMetaManager() {

  }

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;

    // 加载Local service
    final List<EasyMap> localServiceConfigList = configuration.getSubArgumentsList(
        ConfigItems.LOCAL_SERVICE);
    if (CollectionUtils.isNotEmpty(localServiceConfigList)) {
      localServiceConfigList.forEach(serviceConfig -> {
        final String name = serviceConfig.getString(ConfigItems.SERVICE_NAME);
        final String messageBus = serviceConfig.getString(ConfigItems.MESSAGE_BUS);
        final PlaywellService playwellService = (PlaywellService) IntergrationUtils
            .buildAndInitComponent(serviceConfig);
        final ServiceMeta serviceMeta = new LocalServiceMeta(
            name,
            messageBus,
            serviceConfig.toMap(),
            playwellService
        );
        allServiceMeta.put(serviceMeta.getName(), serviceMeta);
      });
    }

    // 加载Remote service
    final List<EasyMap> remoteServiceConfigList = configuration.getSubArgumentsList(
        ConfigItems.REMOTE_SERVICE);
    if (CollectionUtils.isNotEmpty(remoteServiceConfigList)) {
      remoteServiceConfigList.forEach(serviceConfig -> {
        final String name = serviceConfig.getString(ConfigItems.SERVICE_NAME);
        final String messageBus = serviceConfig.getString(ConfigItems.MESSAGE_BUS);
        final ServiceMeta serviceMeta = new ServiceMeta(name, messageBus, serviceConfig.toMap());
        allServiceMeta.put(serviceMeta.getName(), serviceMeta);
      });
    }

    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    if (integrationPlan.contains(TopComponentType.ACTION_MANAGER)) {
      final ActionManager actionManager = (ActionManager) integrationPlan.getTopComponent(
          TopComponentType.ACTION_MANAGER);
      getAllServiceMeta()
          .forEach(serviceMeta -> actionManager.registerServiceAction(serviceMeta.getName()));
    }
  }

  @Override
  public synchronized Result registerServiceMeta(ServiceMeta serviceMeta) {
    allServiceMeta.put(serviceMeta.getName(), serviceMeta);
    IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    if (integrationPlan instanceof ActivityRunnerIntegrationPlan) {
      ActivityRunnerIntegrationPlan activityRunnerIntegrationPlan = (ActivityRunnerIntegrationPlan) integrationPlan;
      activityRunnerIntegrationPlan.getActionManager()
          .registerServiceAction(serviceMeta.getName());
    }
    return Result.okWithData(Collections.singletonMap(
        ResultFields.SERVICE_META, serviceMeta));
  }

  @Override
  public synchronized Result removeServiceMeta(String name) {
    final ServiceMeta serviceMeta = allServiceMeta.remove(name);
    if (serviceMeta == null) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND,
          String.format("The service '%s' not found", name)
      );
    }
    return Result.okWithData(Collections.singletonMap(
        ResultFields.SERVICE_META, serviceMeta));
  }

  @Override
  public Collection<ServiceMeta> getAllServiceMeta() {
    return allServiceMeta.values();
  }

  @Override
  public Optional<ServiceMeta> getServiceMetaByName(String name) {
    return Optional.ofNullable(allServiceMeta.get(name));
  }

  interface ConfigItems {

    String LOCAL_SERVICE = "local_services";

    String REMOTE_SERVICE = "remote_services";

    String SERVICE_NAME = "name";

    String MESSAGE_BUS = "message_bus";
  }
}
