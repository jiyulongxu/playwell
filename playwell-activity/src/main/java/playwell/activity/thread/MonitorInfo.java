package playwell.activity.thread;

import java.util.Optional;
import playwell.common.EasyMap;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.service.ServiceMeta;
import playwell.service.ServiceMetaManager;

/**
 * ActivityThread Monitor信息
 */
public class MonitorInfo {

  private final ServiceMeta monitorService;

  private final MessageBus messageBus;

  private final boolean onlyRepair;  // 是否只接受Repair消息

  public MonitorInfo(
      ServiceMeta monitorService, MessageBus messageBus, boolean onlyRepair) {
    this.monitorService = monitorService;
    this.messageBus = messageBus;
    this.onlyRepair = onlyRepair;
  }

  /**
   * 从配置数据中构建MonitorInfo
   *
   * @param configData 配置数据
   * @return MonitorInfo
   */
  public static MonitorInfo fromConfigData(EasyMap configData) {
    final ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final ServiceMetaManager serviceMetaManager = integrationPlan.getServiceMetaManager();
    final MessageBusManager messageBusManager = integrationPlan.getMessageBusManager();

    final String serviceName = configData.getString(ConfigItems.SERVICE);
    final Optional<ServiceMeta> serviceMetaOptional = serviceMetaManager
        .getServiceMetaByName(serviceName);

    if (!serviceMetaOptional.isPresent()) {
      throw new NotifyMonitorException(String.format(
          "Could not found the monitor service: %s", serviceName));
    }

    final ServiceMeta serviceMeta = serviceMetaOptional.get();
    final String messageBusName = serviceMeta.getMessageBus();
    final Optional<MessageBus> messageBusOptional = messageBusManager
        .getMessageBusByName(messageBusName);
    if (!messageBusOptional.isPresent()) {
      throw new NotifyMonitorException(String.format(
          "Could not found the message bus %s of monitor %s",
          messageBusName,
          serviceName
      ));
    }
    final MessageBus messageBus = messageBusOptional.get();
    return new MonitorInfo(
        serviceMeta,
        messageBus,
        configData.getBoolean(ConfigItems.ONLY_REPAIR, false)
    );
  }

  public ServiceMeta getMonitorService() {
    return monitorService;
  }

  public MessageBus getMessageBus() {
    return messageBus;
  }

  public boolean isOnlyRepair() {
    return onlyRepair;
  }

  public interface ConfigItems {

    String SERVICE = "service";

    String ONLY_REPAIR = "only_repair";
  }
}
