package playwell.baas;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.util.CollectionUtils;
import playwell.clock.CachedTimestamp;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.ServiceRunnerIntegrationPlan;
import playwell.message.Message;
import playwell.message.ServiceRequestMessage;
import playwell.message.ServiceResponseMessage;
import playwell.message.bus.ConcurrentLinkedQueueMessageBus;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;
import playwell.service.LocalServiceMeta;
import playwell.service.MySQLServiceMetaManager;
import playwell.service.ServiceMeta;
import playwell.service.ServiceMetaManager;
import playwell.service.ServiceRunner;
import playwell.storage.redis.RedisHelper;
import playwell.util.Sleeper;
import playwell.util.VariableHolder;
import redis.clients.jedis.Jedis;

/**
 * Base Andrew test case
 */
public abstract class BaseBaasTestCase {

  protected ServiceRunnerIntegrationPlan serviceRunnerIntegrationPlan;

  protected MessageBusManager messageBusManager;

  protected ServiceMetaManager serviceMetaManager;

  protected ServiceRunner serviceRunner;

  protected MessageBus activityBus;

  protected MessageBus localServiceBus;

  // 初始化单元测试所需要的各种公共组件
  protected void init(boolean startServiceRunner) {
    final IntegrationPlanFactory integrationPlanFactory = IntegrationPlanFactory.getInstance();
    integrationPlanFactory.clean();
    integrationPlanFactory.intergrateWithYamlConfigFile(
        "playwell.integration.StandardServiceRunnerIntegrationPlan",
        "config/baas.yml"
    );

    this.serviceRunnerIntegrationPlan = IntegrationPlanFactory.currentPlan();
    this.messageBusManager = serviceRunnerIntegrationPlan.getMessageBusManager();
    this.serviceMetaManager = serviceRunnerIntegrationPlan.getServiceMetaManager();
    this.serviceRunner = serviceRunnerIntegrationPlan.getServiceRunner();

    Optional<MessageBus> messageBusOptional = messageBusManager.getMessageBusByName("activity_bus");
    if (!messageBusOptional.isPresent()) {
      throw new RuntimeException("Could not found the message bus: activity_bus");
    }
    this.activityBus = messageBusOptional.get();

    messageBusOptional = messageBusManager.getMessageBusByName("local_service_bus");
    if (!messageBusOptional.isPresent()) {
      throw new RuntimeException("Could not found the message bus: local_service_bus");
    }
    this.localServiceBus = messageBusOptional.get();

    // Add a mock activity service
    this.serviceMetaManager.registerServiceMeta(new ServiceMeta(
        "activity",
        "activity_bus",
        Collections.emptyMap()
    ));
    ((MySQLServiceMetaManager) serviceMetaManager).beforeLoop();

    if (startServiceRunner) {
      new Thread(serviceRunner::dispatch).start();
    }
  }

  // 批量向指定的服务发送请求
  protected void sendRequests(String toService, Collection<Object> batchArgs) {
    if (CollectionUtils.isEmpty(batchArgs)) {
      return;
    }

    final VariableHolder<Integer> index = new VariableHolder<>(0);
    final Collection<Message> requestMessages = batchArgs.stream().map(
        args -> {
          ServiceRequestMessage requestMessage = new ServiceRequestMessage(
              CachedTimestamp.nowMilliseconds(),
              0,
              Integer.toString(index.getVar()),
              toService,
              "activity",
              toService,
              args,
              false
          );
          index.setVar(index.getVar() + 1);
          return requestMessage;
        }
    ).collect(Collectors.toList());

    try {
      this.localServiceBus.write(requestMessages);
    } catch (MessageBusNotAvailableException e) {
      throw new RuntimeException(e);
    }
  }

  protected Collection<ServiceResponseMessage> getResponse(int num) {
    try {
      return this.activityBus.read(num)
          .stream().map(message -> (ServiceResponseMessage) message)
          .collect(Collectors.toList());
    } catch (MessageBusNotAvailableException e) {
      throw new RuntimeException(e);
    }
  }

  protected LocalServiceMeta getLocalServiceMeta(String serviceName) {
    final Optional<ServiceMeta> serviceMetaOptional = serviceMetaManager
        .getServiceMetaByName(serviceName);
    if (!serviceMetaOptional.isPresent()) {
      throw new RuntimeException(String.format("Unknown service: %s", serviceName));
    }

    return (LocalServiceMeta) serviceMetaOptional.get();
  }

  // 清理单元测试所需要的各种资源
  protected void cleanAll(boolean stopServiceRunner) {
    if (stopServiceRunner) {
      serviceRunner.stop();
      while (!serviceRunner.isStopped()) {
        Sleeper.sleep(10L);
      }
    }

    IntegrationPlanFactory.getInstance().clean();
    ((MySQLServiceMetaManager) serviceMetaManager).removeAll();
    RedisHelper.use("default").call(Jedis::flushDB);
    ((ConcurrentLinkedQueueMessageBus) activityBus).cleanAll();
    ((ConcurrentLinkedQueueMessageBus) localServiceBus).cleanAll();
  }
}
