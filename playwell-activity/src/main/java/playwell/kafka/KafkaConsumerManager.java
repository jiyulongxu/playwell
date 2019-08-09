package playwell.kafka;


import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import playwell.common.EasyMap;

/**
 * 用于管理和初始化系统中的所有KafkaConsumer对象
 */
public class KafkaConsumerManager implements Closeable {

  private static final KafkaConsumerManager INSTANCE = new KafkaConsumerManager();

  private final Map<String, KafkaConsumer<String, String>> allConsumers = new HashMap<>();

  private final Map<String, Boolean> autoCommitSettings = new HashMap<>();

  private KafkaConsumerManager() {

  }

  public static KafkaConsumerManager getInstance() {
    return INSTANCE;
  }

  public void init(EasyMap configuration) {
    final List<EasyMap> consumerConfigs = configuration.getSubArgumentsList(ConfigItems.CONSUMERS);
    consumerConfigs.forEach(config -> {
      final String name = config.getString(ConfigItems.NAME);
      final Map<String, Object> consumerConfig = new HashMap<>(config.toMap());
      consumerConfig.remove(ConfigItems.NAME);
      Properties configProperties = new Properties();
      configProperties.setProperty(ConfigItems.AUTO_COMMIT, "true");
      consumerConfig.forEach((k, v) -> {
        configProperties.setProperty(k, v.toString());
        if (ConfigItems.AUTO_COMMIT.equals(k)) {
          autoCommitSettings.put(name, config.getBoolean(k));
        }
      });
      final KafkaConsumer<String, String> consumer = new KafkaConsumer<>(configProperties);
      allConsumers.put(name, consumer);
    });
  }

  public KafkaConsumer<String, String> newConsumer(Map<String, Object> consumerConfig) {
    Properties configProperties = new Properties();
    configProperties.setProperty(ConfigItems.AUTO_COMMIT, "true");
    consumerConfig.forEach((k, v) -> configProperties.setProperty(k, v.toString()));
    return new KafkaConsumer<>(configProperties);
  }

  public Optional<KafkaConsumer<String, String>> getConsumer(String name) {
    return Optional.ofNullable(allConsumers.get(name));
  }

  public boolean isAutoCommit(String name) {
    Boolean v = autoCommitSettings.get(name);
    if (v == null) {
      throw new RuntimeException(String.format("Unknown consumer: %s", name));
    }
    return v;
  }

  @Override
  public void close() {
    allConsumers.values().forEach(consumer -> {
      try {
        consumer.commitSync();
      } finally {
        consumer.close();
      }
    });
  }

  public interface ConfigItems {

    String CONSUMERS = "consumers";

    String NAME = "name";

    String AUTO_COMMIT = "enable.auto.commit";
  }

}
