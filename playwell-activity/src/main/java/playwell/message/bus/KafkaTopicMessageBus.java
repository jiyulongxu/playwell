package playwell.message.bus;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;

/**
 * 基于Kafka Topic的MessageBus， 具体的Partition策略由KafkaProducer和KafkaConsumer的配置决定
 */
public class KafkaTopicMessageBus extends BaseKafkaMessageBus {

  @Override
  public void open() {
    super.open();
    if (consumer != null) {
      consumer.subscribe(Collections.singletonList(topic));
      if (StringUtils.isNotEmpty(seekTo)) {
        final List<PartitionInfo> partitionInfoList = consumer.partitionsFor(topic);
        final List<TopicPartition> topicPartitions = partitionInfoList.stream()
            .map(pi -> new TopicPartition(topic, pi.partition()))
            .collect(Collectors.toList());

        if (SeekToPositions.BEGIN.equals(seekTo)) {
          // seek to beginning
          consumer.seekToBeginning(topicPartitions);
        } else if (SeekToPositions.END.equals(seekTo)) {
          // seek to end
          consumer.seekToEnd(topicPartitions);
        } else {
          // seek to positive offset
          final long position = Long.parseLong(seekTo);
          topicPartitions.forEach(tp -> consumer.seek(tp, position));
        }
      }
    }
  }

  @Override
  protected ProducerRecord<String, String> buildProducerRecord(String message) {
    return new ProducerRecord<>(topic, null, message);
  }
}
