package playwell.clock;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.commons.collections4.CollectionUtils;
import playwell.common.EasyMap;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.message.Message;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;
import playwell.service.ServiceMeta;
import playwell.service.ServiceMetaManager;

/**
 * MessageClock是一个"只写"的Clock组件 注册ClockMessage的时候，将随机选择一个远程的时钟服务进行注册 但是每次从其中读取的时候返回都是空的，而当到达时间点时，时钟服务会通过MessageBus，将ClockMessage发送给对应的
 * ActivityRunner进行处理
 */
public class MessageClock implements Clock {

  // 所有注册的时钟服务
  private String[] allClockServices;

  // 时钟服务权重
  private double[] allWeights;

  // Total weight
  private double totalWeight = 0.0;

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    final List<EasyMap> allClockServiceConfigList = configuration
        .getSubArgumentsList(ConfigItems.CLOCK_SERVICES);

    if (CollectionUtils.isEmpty(allClockServiceConfigList)) {
      throw new RuntimeException("There is no clock service of the message clock configuration!");
    }

    this.allClockServices = new String[allClockServiceConfigList.size()];
    this.allWeights = new double[allClockServiceConfigList.size()];

    for (int i = 0; i < allClockServiceConfigList.size(); i++) {
      final EasyMap clockServiceConfig = allClockServiceConfigList.get(i);
      final String serviceName = clockServiceConfig.getString(ServiceConfigItems.NAME);
      final double weight = clockServiceConfig.getDouble(
          ServiceConfigItems.WEIGHT, ServiceConfigItems.DEFAULT_WEIGHT);
      this.allClockServices[i] = serviceName;
      this.allWeights[i] = weight;
      this.totalWeight += weight;
    }
  }

  @Override
  public void registerClockMessage(ClockMessage clockMessage) {
    final MessageBus messageBus = getClockServiceMessageBus();
    try {
      messageBus.write(clockMessage);
    } catch (MessageBusNotAvailableException e) {
      throw new RuntimeException("Register clock message via message bus error", e);
    }
  }

  @Override
  public Collection<ClockMessage> fetchClockMessages(long untilTimePoint) {
    return Collections.emptyList();
  }

  @Override
  public void consumeClockMessage(long untilTimePoint, Consumer<ClockMessage> consumer) {
    // Do nothing
  }

  @Override
  public void clean(long untilTimePoint) {
    // Do nothing
  }

  @Override
  public void scanAll(ClockMessageScanConsumer consumer) {
    // Do nothing
  }

  @Override
  public void stopScan() {
    // Do nothing
  }

  @Override
  public void applyReplicationMessages(Collection<Message> messages) {

  }

  private MessageBus getClockServiceMessageBus() {
    final String service;
    if (allClockServices.length == 1) {
      service = allClockServices[0];
    } else {
      // 按注册权重获取
      int targetIndex = 0;
      double random = Math.random() * totalWeight;
      for (int i = 0; i < allClockServices.length; i++) {
        random -= allWeights[i];
        if (random <= 0.0d) {
          targetIndex = i;
          break;
        }
      }
      service = allClockServices[targetIndex];
    }

    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final ServiceMetaManager serviceMetaManager = (ServiceMetaManager) integrationPlan
        .getTopComponent(TopComponentType.SERVICE_META_MANAGER);
    final MessageBusManager messageBusManager = (MessageBusManager) integrationPlan
        .getTopComponent(TopComponentType.MESSAGE_BUS_MANAGER);

    final Optional<ServiceMeta> serviceMetaOptional = serviceMetaManager
        .getServiceMetaByName(service);
    final ServiceMeta serviceMeta = serviceMetaOptional.orElseThrow(() -> new RuntimeException(
        String.format("Register clock message error, unknown clock service: %s", service)));
    final Optional<MessageBus> messageBusOptional = messageBusManager
        .getMessageBusByName(serviceMeta.getMessageBus());
    return messageBusOptional.orElseThrow(() -> new RuntimeException(String.format(
        "Register clock message error, unknown message bus %s of clock service %s",
        serviceMeta.getMessageBus(),
        service
    )));
  }

  interface ConfigItems {

    String CLOCK_SERVICES = "clock_services";
  }

  interface ServiceConfigItems {

    String NAME = "name";

    String WEIGHT = "weight";

    double DEFAULT_WEIGHT = 1d;
  }
}
