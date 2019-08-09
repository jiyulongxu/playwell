package playwell.route.migration;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.common.Result;
import playwell.message.MessageDispatcherListener;
import playwell.route.RouteBaseTestCase;

/**
 * TestCase for DefaultMigrationCoordinator
 */
public class DefaultMigrationCoordinatorTestCase extends RouteBaseTestCase {

  @Before
  public void setUp() {
    super.setUp();
  }

  @Test
  public void testAddNode() {
    // 测试添加新节点
    testMigration(
        ImmutableMap.of(
            "service_a", 50,
            "service_b", 50
        ),
        ImmutableMap.of(
            "service_a", 30,
            "service_b", 20,
            "service_c", 50
        )
    );
  }

  @Test
  public void testAddMultiNodes() {
    // 测试添加多个新节点
    testMigration(
        ImmutableMap.of(
            "service_a", 50,
            "service_b", 50
        ),
        ImmutableMap.of(
            "service_a", 30,
            "service_b", 20,
            "service_c", 20,
            "service_d", 10,
            "service_e", 20
        )
    );
  }

  @Test
  public void testRemoveNode() {
    // 测试删除节点
    testMigration(
        ImmutableMap.of(
            "service_a", 50,
            "service_b", 25,
            "service_c", 25
        ),
        ImmutableMap.of(
            "service_a", 0,
            "service_b", 50,
            "service_c", 50
        )
    );
  }

  @Test
  public void testRemoveMultiNodes() {
    // 测试删除多个节点
    testMigration(
        ImmutableMap.of(
            "service_a", 30,
            "service_b", 20,
            "service_c", 20,
            "service_d", 10,
            "service_e", 20
        ),
        ImmutableMap.of(
            "service_a", 0,
            "service_b", 0,
            "service_c", 50,
            "service_d", 25,
            "service_e", 25
        )
    );
  }

  @Test
  public void testTransSlots() {
    // 节点数目不变，内部slots负载迁移
    testMigration(
        ImmutableMap.of(
            "service_a", 30,
            "service_b", 20,
            "service_c", 20,
            "service_d", 10,
            "service_e", 20
        ),
        ImmutableMap.of(
            "service_a", 20,
            "service_b", 10,
            "service_c", 20,
            "service_d", 25,
            "service_e", 25
        )
    );
  }

  private void testMigration(Map<String, Integer> initSlots, Map<String, Integer> targetSlots) {
    int sum = initSlots.values().stream().reduce((a, b) -> a + b).orElse(0);
    Result result = slotsManager.allocSlots(sum, initSlots);
    Assert.assertTrue(result.isOk());
    ((MessageDispatcherListener) slotsManager).beforeLoop();
    result = coordinator.startMigrationPlan(
        "playwell.message.bus.KafkaPartitionMessageBus",
        ImmutableMap.<String, Object>builder()
            .put("name", "migration_bus")
            .put("producer", "playwell")
            .put("topic", "test_migration")
            .put("partition", 0)
            .put("consumer", ImmutableMap.builder()
                .put("bootstrap.servers", "localhost:9092")
                .put("group.id", "playwell")
                .put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
                .put("value.deserializer",
                    "org.apache.kafka.common.serialization.StringDeserializer")
                .put("enable.auto.commit", false)
                .put("max.poll.records", 5000)
                .build())
            .build(),
        ImmutableMap.<String, Object>builder()
            .put("name", "migration_bus")
            .put("producer", "playwell")
            .put("topic", "test_migration")
            .put("partition", 0)
            .build(),
        targetSlots,
        "test"
    );
    System.out.println(result);
    Assert.assertTrue(result.isOk());
    result = coordinator.getCurrentStatus();
    System.out.println(result);
  }

  @After
  public void tearDown() {
    super.tearDown();
    slotsManager.getMigrationCoordinator().stop();
    ((DefaultMigrationCoordinator) slotsManager.getMigrationCoordinator()).cleanAll();
  }
}
