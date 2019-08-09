package playwell.route.migration;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.thread.UserScanOperation;
import playwell.clock.CachedTimestamp;
import playwell.common.Result;
import playwell.kafka.KafkaConsumerManager;
import playwell.message.Message;
import playwell.message.TestUserBehaviorEvent;
import playwell.message.bus.MessageBus;
import playwell.util.Sleeper;

/**
 * 测试Slots迁移
 */
public class SlotsMigrationTestCase extends PlaywellBaseTestCase {

  private MigrationCoordinator migrationCoordinator;

  private MessageBus localServiceMessageBus;

  @Before
  public void setUp() {
    initStandardActivityRunnerIntergrationPlan(
        "config/playwell_migrate.yml", false);
    this.slotsManager.allocSlots(
        100, ImmutableMap.of("playwell", 100));
    this.migrationCoordinator = this.slotsManager.getMigrationCoordinator();
    Optional<MessageBus> messageBusOptional = messageBusManager
        .getMessageBusByName("local_service_bus");
    Assert.assertTrue(messageBusOptional.isPresent());
    this.localServiceMessageBus = messageBusOptional.get();
    new Thread(this.activityRunner::dispatch).start();
  }

  @Test
  public void testMigration() throws Exception {
    TimeUnit.SECONDS.sleep(3L);

    // 触发10000个活动实例
    ActivityDefinition activityDefinition = createActivityDefinition(
        "docs/sample/test_definitions/simple_receive.yml");
    createActivity(
        activityDefinition.getName(),
        "simple receive",
        Collections.emptyMap()
    );

    for (int i = 0; i < 10000; i++) {
      sendMessage(new TestUserBehaviorEvent(
          Integer.toString(i),
          "hi",
          Collections.emptyMap(),
          CachedTimestamp.nowMilliseconds()
      ));
    }

    TimeUnit.SECONDS.sleep(2L);

    final Map<String, Object> consumerConfig = ImmutableMap.<String, Object>builder()
        .put("bootstrap.servers", "localhost:9092")
        .put("group.id", "playwell")
        .put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
        .put("value.deserializer",
            "org.apache.kafka.common.serialization.StringDeserializer")
        .put("enable.auto.commit", false)
        .put("max.poll.records", 5000)
        .build();
    final KafkaConsumer<String, String> consumer = KafkaConsumerManager
        .getInstance().newConsumer(consumerConfig);
    final TopicPartition topicPartition = new TopicPartition("test_migration", 0);
    consumer.assign(Collections.singletonList(topicPartition));
    consumer.seekToEnd(Collections.singletonList(topicPartition));
    consumer.commitSync(
        ImmutableMap.of(topicPartition, new OffsetAndMetadata(consumer.position(topicPartition))));
    consumer.close();

    Result result = migrationCoordinator.startMigrationPlan(
        "playwell.message.bus.KafkaPartitionMessageBus",
        ImmutableMap.<String, Object>builder()
            .put("name", "migration_bus")
            .put("producer", "playwell")
            .put("topic", "test_migration")
            .put("partition", 0)
            .put("consumer", consumerConfig)
            .build(),
        ImmutableMap.<String, Object>builder()
            .put("name", "migration_bus")
            .put("producer", "playwell")
            .put("topic", "test_migration")
            .put("partition", 0)
            .build(),
        ImmutableMap.of("playwell", 50, "mock", 50),
        "test"
    );
    System.out.println(result);
    Assert.assertTrue(result.isOk());

    Sleeper.sleepInSeconds(15);

    TestMigrationInputTask migrationInputTask = new TestMigrationInputTask("playwell");
    Optional<MigrationProgress> migrationProgressOptional = migrationInputTask
        .getMigrationProgress("mock");
    Assert.assertTrue(migrationProgressOptional.isPresent());
    MigrationProgress migrationProgress = migrationProgressOptional.get();
    Set<Integer> targetSlots = new HashSet<>(migrationProgress.getSlots());
    int migrationCount = 0;
    for (int i = 0; i < 10000; i++) {
      int slot = slotsManager.getSlotByKey(Integer.toString(i));
      if (targetSlots.contains(slot)) {
        migrationCount++;
      }
    }
    System.out.println("Migration count: " + migrationCount);

    Collection<Message> redirectedMessages = localServiceMessageBus.read(20000);
    System.out.println("Redirected size: " + redirectedMessages.size());
    Assert.assertEquals(migrationCount, redirectedMessages.size());

    migrationInputTask.startInputTask(migrationProgress);
    System.out.println("Input task received: " + migrationInputTask.all().size());
    Assert.assertEquals(migrationCount, migrationInputTask.all().size());
    TimeUnit.SECONDS.sleep(2L);

    activityThreadPool.scanAll(new UserScanOperation(
        Collections.emptyList(),
        -1,
        true,
        false,
        "test_migration",
        1,
        Collections.emptyList(),
        1
    ));
  }

  @After
  public void tearDown() {
    migrationCoordinator.stop();
    while (!migrationCoordinator.isStopped()) {
      Sleeper.sleep(10);
    }
    activityRunner.stop();
    while (!activityRunner.isStopped()) {
      Sleeper.sleep(10);
    }
    cleanStandardActivityRunnerIntergrationPlan(false);
  }
}
