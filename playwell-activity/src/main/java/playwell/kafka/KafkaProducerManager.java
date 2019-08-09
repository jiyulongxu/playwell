package playwell.kafka;

import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.apache.commons.collections4.MapUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import playwell.common.EasyMap;

/**
 * 用于初始化以及管理系统中的所有KafkaProducer对象
 */
public class KafkaProducerManager implements Closeable {

  private static final KafkaProducerManager INSTANCE = new KafkaProducerManager();

  private final Map<String, KafkaProducer<String, String>> ALL_PRODUCERS = new HashMap<>();

  private KafkaProducerManager() {

  }

  public static KafkaProducerManager getInstance() {
    return INSTANCE;
  }

  /**
   * 对KafkaProducerManager进行初始化
   *
   * @param configuration 配置
   */
  public void init(EasyMap configuration) {
    final List<EasyMap> producerConfigs = configuration.getSubArgumentsList(
        ConfigItems.PRODUCERS);
    producerConfigs.forEach(config -> {
      final String producerName = config.getString(ConfigItems.NAME);
      Map<String, Object> configProperties = new HashMap<>(config.toMap());
      configProperties.remove(ConfigItems.NAME);
      final KafkaProducer<String, String> producer = newProducer(configProperties);
      ALL_PRODUCERS.put(producerName, producer);
    });
  }

  /**
   * 根据配置信息，创建新的KafkaProducer对象
   *
   * @param producerConfig Kafka configuration
   * @return KafkaProducer Object
   */
  public KafkaProducer<String, String> newProducer(Map<String, Object> producerConfig) {
    final Properties kafkaProperties = new Properties();
    producerConfig.forEach((k, v) ->
        kafkaProperties.setProperty(k, v.toString()));
    return new KafkaProducer<>(kafkaProperties);
  }

  /**
   * 根据名称，获取Producer对象
   *
   * @param name 注册的KafkaProducer名称
   * @return KafkaProducer Optional
   */
  public Optional<KafkaProducer<String, String>> getProducer(String name) {
    return Optional.ofNullable(ALL_PRODUCERS.get(name));
  }

  @Override
  public void close() {
    if (MapUtils.isEmpty(ALL_PRODUCERS)) {
      return;
    }

    ALL_PRODUCERS.values().forEach(KafkaProducer::close);
  }

  interface ConfigItems {

    String PRODUCERS = "producers";

    String NAME = "name";
  }
}
