package playwell.route;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multiset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.clock.CachedTimestamp;
import playwell.common.Result;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.MessageRouteIntegrationPlan;
import playwell.message.Message;
import playwell.message.TestUserBehaviorEvent;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MySQLMessageBusManager;
import playwell.message.domainid.MessageDomainIDStrategyManager;
import playwell.message.domainid.MySQLMessageDomainIDStrategyManager;
import playwell.service.MySQLServiceMetaManager;
import playwell.service.ServiceMeta;
import playwell.service.ServiceMetaManager;

/**
 * TestCase for message route
 */
public class MessageRouteTestCase {

  private final String[] serviceNames = new String[]{
      "runner_a",
      "runner_b",
      "runner_c"
  };

  private MessageDomainIDStrategyManager strategyManager;

  private SlotsManager slotsManager;

  private MessageRoute messageRoute;

  private ServiceMetaManager serviceMetaManager;

  private MessageBusManager messageBusManager;

  @Before
  public void setUp() throws Exception {
    final IntegrationPlanFactory integrationPlanFactory = IntegrationPlanFactory.getInstance();
    integrationPlanFactory.clean();
    integrationPlanFactory.intergrateWithYamlConfigFile(
        "playwell.integration.StandardMessageRouteIntegrationPlan",
        "config/playwell_route.yml"
    );

    MessageRouteIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    this.strategyManager = integrationPlan.getMessageDomainIDStrategyManager();
    this.slotsManager = integrationPlan.getSlotsManager();
    this.messageRoute = integrationPlan.getMessageRoute();
    this.serviceMetaManager = integrationPlan.getServiceMetaManager();
    this.messageBusManager = integrationPlan.getMessageBusManager();

    // 添加DomainIDStrategy
    this.strategyManager.addMessageDomainIDStrategy(
        "user_id",
        "containsAttr('user_id')",
        "eventAttr('user_id')"
    );
    this.strategyManager.addMessageDomainIDStrategy(
        "user_order_id",
        "containsAttr('order_id') AND containsAttr('user_id')",
        "str('%s_%s', eventAttr('user_id'), eventAttr('order_id'))"
    );

    // 注册服务
    for (String serviceName : serviceNames) {
      Result result = serviceMetaManager.registerServiceMeta(new ServiceMeta(
          serviceName,
          String.format("%s_bus", serviceName),
          Collections.emptyMap()
      ));
      if (!result.isOk()) {
        throw new RuntimeException(String.format(
            "Register service %s error, error_code: %s, error_msg: %s",
            serviceName,
            result.getErrorCode(),
            result.getMessage()
        ));
      }
    }

    // 分配slots
    Result result = slotsManager.allocSlots(100, ImmutableMap.of(
        "runner_a", 20,
        "runner_b", 30,
        "runner_c", 50
    ));
    if (!result.isOk()) {
      throw new RuntimeException(String.format(
          "Alloc slots error, error_code: %s, error_msg: %s",
          result.getErrorCode(),
          result.getMessage()
      ));
    }

    new Thread(messageRoute::dispatch).start();

    TimeUnit.SECONDS.sleep(1L);
  }

  @Test
  public void testRoute() throws Exception {
    final MessageBus routeBus = messageBusManager.getMessageBusByName("route_bus")
        .orElseThrow(() -> new RuntimeException("Route bus not found"));
    final List<MessageBus> runnerBusList = Arrays.stream(serviceNames).map(serviceName -> {
      String busName = String.format("%s_bus", serviceName);
      return messageBusManager.getMessageBusByName(busName)
          .orElseThrow(() -> new RuntimeException(busName + " not found"));
    }).collect(Collectors.toList());

    final Multiset<String> serviceCount = HashMultiset.create();

    for (int i = 0; i < 50; i++) {
      Map<String, Object> attrs;
      String service = slotsManager.getServiceByKey("" + i);
      serviceCount.add(service);
      if (i % 2 == 0) {
        attrs = ImmutableMap.of(
            "user_id", i,
            "order_id", i * 100
        );
        service = slotsManager.getServiceByKey(i + "_" + i * 100);
        serviceCount.add(service);
      } else {
        attrs = ImmutableMap.of(
            "user_id", i
        );
      }
      routeBus.write(new TestUserBehaviorEvent(
          "" + i,
          "test",
          attrs,
          CachedTimestamp.nowMilliseconds()
      ));
    }

    TimeUnit.SECONDS.sleep(1L);

    for (int i = 0; i < runnerBusList.size(); i++) {
      MessageBus runnerBus = runnerBusList.get(i);
      String service = serviceNames[i];
      Collection<Message> messages = runnerBus.read(1000);
      Assert.assertEquals(serviceCount.count(service), messages.size());
      messages.forEach(System.out::println);
    }
  }

  @After
  public void tearDown() {
    messageRoute.stop();
    while (!messageRoute.isStopped()) {
      try {
        TimeUnit.MILLISECONDS.sleep(10L);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    IntegrationPlanFactory.getInstance().clean();

    ((MySQLSlotsManager) slotsManager).removeAll();
    ((MySQLServiceMetaManager) serviceMetaManager).removeAll();
    ((MySQLMessageDomainIDStrategyManager) strategyManager).removeAll();
    ((MySQLMessageBusManager) messageBusManager).removeAll();
  }
}
