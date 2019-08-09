package playwell.message.bus;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import playwell.kafka.KafkaConsumerManager;
import playwell.kafka.KafkaProducerManager;
import playwell.message.Message;
import playwell.message.bus.codec.JSONObjectMessageCodec;

/**
 * KafkaMessageBus基类，提供Kafka的公共配置和操作
 */
public abstract class BaseKafkaMessageBus extends BaseMessageBus {

  private static final Logger logger = LogManager.getLogger(BaseKafkaMessageBus.class);

  private static final JSONObjectMessageCodec codec = new JSONObjectMessageCodec();

  // Kafka Producer
  protected KafkaProducer<String, String> kafkaProducer;

  protected KafkaConsumer<String, String> consumer;

  // 读写的主题
  protected String topic;

  // 是否采用同步提交
  protected boolean commitSync;

  // 是否采用的资源声明中的Producer
  protected boolean resourceProducer = false;

  // 是否采用的资源声明中的consumer
  protected boolean resourceConsumer = false;

  // Auto commit
  protected boolean autoCommit = false;

  // Seek to
  protected String seekTo;

  private EasyMap configuration;

  @Override
  protected void initMessageBus(EasyMap configuration) {
    this.configuration = configuration;
    this.topic = configuration.getString(ConfigItems.TOPIC);
    this.seekTo = configuration.getString(ConfigItems.SEEK_TO, "");
  }

  @Override
  public void open() {
    super.open();
    // 开启MessageBus所需要的Producer
    this.openKafkaProducer();
    // 开启MessageBus所需要的Consumer
    this.openKafkaConsumer();
  }

  private void openKafkaProducer() {
    final KafkaProducerManager producerManager = KafkaProducerManager.getInstance();
    final Object producerObj = configuration.get(ConfigItems.PRODUCER);
    if (producerObj instanceof String) {
      final String producerName = (String) producerObj;
      final Optional<KafkaProducer<String, String>> producerOptional = producerManager
          .getProducer(producerName);
      if (producerOptional.isPresent()) {
        this.kafkaProducer = producerOptional.get();
        this.resourceProducer = true;
      }
    } else if (producerObj instanceof Map) {
      final EasyMap producerConfig = configuration.getSubArguments(ConfigItems.PRODUCER);
      this.kafkaProducer = producerManager.newProducer(producerConfig.toMap());
    }
  }

  private void openKafkaConsumer() {
    KafkaConsumerManager kafkaConsumerManager = KafkaConsumerManager.getInstance();
    // 有指定Consumer配置，从配置中设置Consumer对象
    if (configuration.contains(ConfigItems.CONSUMER)) {
      final EasyMap consumerConfig = configuration.getSubArguments(ConfigItems.CONSUMER);
      logger.info("Init kafka consumer with bus configuration");
      this.autoCommit = consumerConfig.getBoolean(
          KafkaConsumerManager.ConfigItems.AUTO_COMMIT, true);
      this.consumer = kafkaConsumerManager.newConsumer(consumerConfig.toMap());
      this.commitSync = configuration.getBoolean(ConfigItems.COMMIT_SYNC, true);
    } else {
      // 没有指定，默认从资源中寻找与MessageBus同名的Consumer配置
      Optional<KafkaConsumer<String, String>> consumerOptional = kafkaConsumerManager
          .getConsumer(name());
      if (consumerOptional.isPresent()) {
        logger.info(String.format("Kafka consumer %s found", name()));
        this.consumer = consumerOptional.get();
        this.commitSync = configuration.getBoolean(ConfigItems.COMMIT_SYNC, true);
        this.resourceConsumer = true;
      }
    }
  }

  @Override
  public void write(Message message) throws MessageBusNotAvailableException {
    checkAvailable();
    write(kafkaProducer, message);
  }

  @Override
  public void write(Collection<Message> messages) throws MessageBusNotAvailableException {
    checkAvailable();

    if (CollectionUtils.isEmpty(messages)) {
      return;
    }

    for (Message message : messages) {
      this.write(kafkaProducer, message);
    }
  }

  private void write(KafkaProducer<String, String> producer, Message message)
      throws MessageBusNotAvailableException {
    checkAvailable();

    producer.send(buildProducerRecord((String) codec.encode(message)), ((metadata, exception) -> {
      if (exception != null) {
        logger.error(exception.getMessage(), exception);
      }
    }));
  }

  protected abstract ProducerRecord<String, String> buildProducerRecord(String message);

  @Override
  public Collection<Message> read(int maxFetchNum) throws MessageBusNotAvailableException {
    checkAvailable();

    if (this.consumer == null) {
      throw new RuntimeException(String.format(
          "There is no available KafkaConsumer for the message bus: %s", name()));
    }

    final ConsumerRecords<String, String> records = consumer.poll(Duration.ZERO);
    if (records.isEmpty()) {
      return Collections.emptyList();
    }

    final List<Message> messages = new LinkedList<>();

    for (ConsumerRecord<String, String> record : records) {
      messages.add(codec.decode(record.value()));
    }

    return messages;
  }

  @Override
  public int readWithConsumer(int maxFetchNum, Consumer<Message> eventConsumer)
      throws MessageBusNotAvailableException {
    checkAvailable();

    if (this.consumer == null) {
      throw new RuntimeException(String.format(
          "There is no available KafkaConsumer for the message bus: %s", name()));
    }

    final ConsumerRecords<String, String> records = consumer.poll(Duration.ZERO);
    if (records.isEmpty()) {
      return 0;
    }

    int consumed = 0;
    for (ConsumerRecord<String, String> record : records) {
      final Message message = codec.decode(record.value());
      eventConsumer.accept(message);
      consumed++;
    }
    return consumed;
  }

  @Override
  public void ackMessages() {
    if (autoCommit) {
      return;
    }
    if (resourceConsumer && KafkaConsumerManager.getInstance().isAutoCommit(name())) {
      return;
    }

    if (commitSync) {
      consumer.commitSync();
    } else {
      consumer.commitAsync();
    }
  }

  @Override
  public void close() {
    super.close();

    // 关闭非资源声明的producer和consumer
    if ((!resourceProducer) && kafkaProducer != null) {
      try {
        this.kafkaProducer.close();
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
    }

    if ((!resourceConsumer) && consumer != null) {
      try {
        this.consumer.close();
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
    }
  }

  interface ConfigItems {

    String PRODUCER = "producer";

    String CONSUMER = "consumer";

    String TOPIC = "topic";

    String COMMIT_SYNC = "commit_sync";

    String SEEK_TO = "seek_to";
  }

  interface SeekToPositions {

    String BEGIN = "begin";

    String END = "end";
  }
}
