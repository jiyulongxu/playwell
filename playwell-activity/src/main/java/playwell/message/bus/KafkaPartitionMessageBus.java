package playwell.message.bus;


import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import playwell.common.EasyMap;

/**
 * 基于Kafka partition的MessageBus
 */
public class KafkaPartitionMessageBus extends BaseKafkaMessageBus {

  private int partition;

  @Override
  protected void initMessageBus(EasyMap configuration) {
    super.initMessageBus(configuration);
    this.partition = configuration.getInt(ConfigItems.PARTITION);
  }

  @Override
  public void open() {
    super.open();
    if (this.consumer != null) {
      List<PartitionInfo> partitionInfoList = consumer.partitionsFor(this.topic);
      if (CollectionUtils.isEmpty(partitionInfoList)) {
        throw new RuntimeException(String.format(
            "There is no partition in the kafka topic: %s", this.topic));
      }

      Optional<PartitionInfo> partitionInfoOptional = partitionInfoList.stream().filter(
          pi -> pi.partition() == this.partition).findFirst();
      if (!partitionInfoOptional.isPresent()) {
        throw new RuntimeException(String.format(
            "Could not found the partition %d in the topic %s", this.partition, this.topic));
      }

      final TopicPartition topicPartition = new TopicPartition(topic, partition);
      consumer.assign(Collections.singletonList(topicPartition));

      if (StringUtils.isNotEmpty(seekTo)) {
        if (SeekToPositions.BEGIN.equals(seekTo)) {
          // seek to beginning
          consumer.seekToBeginning(Collections.singletonList(topicPartition));
        } else if (SeekToPositions.END.equals(seekTo)) {
          // seek to end
          consumer.seekToEnd(Collections.singletonList(topicPartition));
        } else {
          // seek to positive position
          final long position = Long.parseLong(seekTo);
          consumer.seek(topicPartition, position);
        }
      }
    }
  }

  @Override
  protected ProducerRecord<String, String> buildProducerRecord(String message) {
    return new ProducerRecord<>(topic, partition, null, message);
  }

  interface ConfigItems {

    String PARTITION = "partition";
  }
}
