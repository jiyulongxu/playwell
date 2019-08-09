package playwell.message.bus;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import playwell.clock.CachedTimestamp;
import playwell.common.EasyMap;
import playwell.kafka.KafkaConsumerManager;
import playwell.kafka.KafkaProducerManager;
import playwell.message.TestUserBehaviorEvent;

public class KafkaTopicMessageBusTestCase {

  private MessageBus messageBus;

  @Before
  public void setUp() {
    final KafkaProducerManager kafkaProducerManager = KafkaProducerManager.getInstance();
    kafkaProducerManager.init(new EasyMap(ImmutableMap.of("producers", Collections.singletonList(
        ImmutableMap.builder()
            .put("name", "local_kafka_producer")
            .put("bootstrap.servers", "localhost:9092")
            .put("acks", "1")
            .put("retries", "3")
            .put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
            .put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
            .build()
    ))));

    final KafkaConsumerManager kafkaConsumerManager = KafkaConsumerManager.getInstance();
    kafkaConsumerManager.init(new EasyMap(ImmutableMap.of("consumers", Collections.singletonList(
        ImmutableMap.builder()
            .put("name", "test_kafka_bus")
            .put("bootstrap.servers", "localhost:9092")
            .put("group.id", "testbus2")
            .put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
            .put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
            .put("enable.auto.commit", false)
            .put("max.poll.records", 5000)
            .build()
    ))));

    this.messageBus = new KafkaTopicMessageBus();
    this.messageBus.init(new EasyMap(ImmutableMap.builder()
        .put("name", "test_kafka_bus")
        .put("producer", "local_kafka_producer")
        .put("topic", "testbus2")
        .put("commit_sync", true)
        .build()));
    this.messageBus.open();
  }

  @Test
  public void testBusOperations() throws Exception {
    long beginTime = System.currentTimeMillis();
    for (int i = 0; i < 5000; i++) {
      messageBus.write(new TestUserBehaviorEvent(
          "" + i,
          "test_message_bus",
          ImmutableMap.of("a", i, "b", 2),
          CachedTimestamp.nowMilliseconds()
      ));
    }
    System.out.println("Write 5k used time: " + (System.currentTimeMillis() - beginTime));

    final AtomicInteger counter = new AtomicInteger(0);

    while (true) {
      messageBus.readWithConsumer(0, msg -> counter.incrementAndGet());
      messageBus.ackMessages();
      System.out.println("Read msg count: " + counter.get());
      if (counter.get() >= 5000) {
        break;
      }
      TimeUnit.SECONDS.sleep(1L);
    }
  }

  @After
  public void tearDown() {
    KafkaProducerManager.getInstance().close();
    KafkaConsumerManager.getInstance().close();
  }
}
