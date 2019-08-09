package playwell.api;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.MapUtils;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import playwell.common.MySQLCompareAndCallback;
import playwell.common.Result;
import playwell.kafka.KafkaConsumerManager;
import playwell.storage.rocksdb.BackupInfoModel;
import playwell.storage.rocksdb.RocksDBHelper;
import playwell.util.PerfLog;
import playwell.util.PerfLog.PerfRecord;
import playwell.util.validate.Field;
import playwell.util.validate.FieldType;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * 系统管理相关API
 */
class SystemAPIRoutes extends APIRoutes {

  private static final Field[] REFRESH_CAC_API_FIELDS = new Field[]{
      new Field.Builder("datasource").required(true).build(),
      new Field.Builder("item").required(true).build()
  };

  private static final Field[] SEEK_TO_OFFSET_API_FIELDS = new Field[]{
      new Field.Builder("consumer").required(true).build(),
      new Field.Builder("topic").required(true).build(),
      new Field.Builder("partition").required(false).type(FieldType.Int).defaultValue("-1").build(),
      new Field.Builder("offset").required(true).type(FieldType.LongInt).build()
  };

  private static final Field[] SEEK_TO_BEGINNING_API_FIELDS = new Field[]{
      new Field.Builder("consumer").required(true).build(),
      new Field.Builder("topic").required(true).build(),
      new Field.Builder("partition").required(false).type(FieldType.Int).defaultValue("-1").build(),
  };

  private static final Field[] SEEK_TO_END_API_FIELDS = new Field[]{
      new Field.Builder("consumer").required(true).build(),
      new Field.Builder("topic").required(true).build(),
      new Field.Builder("partition").required(false).type(FieldType.Int).defaultValue("-1").build(),
  };

  private static final Field[] SEEK_TO_TIMESTAMP_API_FIELDS = new Field[]{
      new Field.Builder("consumer").required(true).build(),
      new Field.Builder("topic").required(true).build(),
      new Field.Builder("partition").required(false).type(FieldType.Int).defaultValue("-1").build(),
      new Field.Builder("timestamp").required(true).type(FieldType.LongInt).build(),
  };

  private static final Field[] CLOSE_CONSUMER_API_FIELDS = new Field[]{
      new Field.Builder("consumer").required(true).build(),
  };

  private static final Field[] FULL_COMPACTION_API_FIELDS = new Field[]{
      new Field.Builder("cf").required(true).build(),
  };

  private static final Field[] CREATE_CHECKPOINT_API_FIELDS = new Field[]{
      new Field.Builder("dir").required(true).build(),
      new Field.Builder("async").required(false).defaultValue("false").build(),
  };

  private static final Field[] CREATE_BACKUP_API_FIELDS = new Field[]{
      new Field.Builder("flush_before_backup").required(false).defaultValue("false").build(),
  };

  private static final Field[] PURGE_OLD_BACKUPS_API_FIELDS = new Field[]{
      new Field.Builder("num").required(true).build(),
  };

  private static final Field[] SET_PERF_LOG_FIELDS = new Field[]{
      new Field.Builder("enable").required(true).build(),
      new Field.Builder("period").required(false).build(),
  };

  @Override
  protected void registerRoutes(Service service) {
    service.path("/v1/system", () -> {
      service.post("/CAC/refresh", this::refreshCAC);
      service.post("/kafka/seek_to_offset", this::seekToOffset);
      service.post("/kafka/seek_to_beginning", this::seekToBeginning);
      service.post("/kafka/seek_to_end", this::seekToEnd);
      service.post("/kafka/seek_to_timestamp", this::seekToTimestamp);
      service.post("/kafka/close_consumer", this::closeConsumer);
      service.post("/rocksdb/full_compact", this::fullCompact);
      service.post("/rocksdb/checkpoint", this::createCheckpoint);
      service.get("/rocksdb/backup", this::getBackupInfo);
      service.post("rocksdb/backup", this::createNewBackup);
      service.post("/rocksdb/backup/purge", this::purgeOldBackups);
      service.get("/perflog", this::getPerfLogRecords);
      service.post("/perflog", this::setPerfLogEnable);
    });
  }

  private String refreshCAC(Request request, Response response) {
    return postResponse(
        request,
        response,
        REFRESH_CAC_API_FIELDS,
        args -> {
          String dataSource = args.getString("datasource");
          String item = args.getString("item");
          new MySQLCompareAndCallback(dataSource, item).updateVersion();
          return Result.ok();
        }
    );
  }

  private String seekToOffset(Request request, Response response) {
    return postResponse(
        request,
        response,
        SEEK_TO_OFFSET_API_FIELDS,
        args -> {
          final KafkaConsumerManager consumerManager = KafkaConsumerManager.getInstance();
          final Optional<KafkaConsumer<String, String>> consumerOptional = consumerManager
              .getConsumer(
                  args.getString("consumer"));
          if (!consumerOptional.isPresent()) {
            return Result.failWithCodeAndMessage(
                "consumer_not_found",
                String.format("Consumer %s not found", args.getString("consumer"))
            );
          }
          final KafkaConsumer<String, String> consumer = consumerOptional.get();
          final String topic = args.getString("topic");
          final int partition = args.getInt("partition", -1);
          final long offset = args.getLong("offset");
          if (partition == -1) {
            List<PartitionInfo> partitionInfoList = consumer.partitionsFor(topic);
            partitionInfoList.forEach(pi -> consumer.seek(
                new TopicPartition(topic, pi.partition()), offset));
          } else {
            consumer.seek(new TopicPartition(topic, partition), offset);
          }
          return Result.ok();
        }
    );
  }

  private String seekToBeginning(Request request, Response response) {
    return postResponse(
        request,
        response,
        SEEK_TO_BEGINNING_API_FIELDS,
        args -> {
          final KafkaConsumerManager consumerManager = KafkaConsumerManager.getInstance();
          final Optional<KafkaConsumer<String, String>> consumerOptional = consumerManager
              .getConsumer(
                  args.getString("consumer"));
          if (!consumerOptional.isPresent()) {
            return Result.failWithCodeAndMessage(
                "consumer_not_found",
                String.format("Consumer %s not found", args.getString("consumer"))
            );
          }
          final KafkaConsumer<String, String> consumer = consumerOptional.get();
          final String topic = args.getString("topic");
          final int partition = args.getInt("partition", -1);
          if (partition == -1) {
            List<PartitionInfo> partitionInfoList = consumer.partitionsFor(topic);
            consumer.seekToBeginning(partitionInfoList.stream()
                .map(pi -> new TopicPartition(topic, pi.partition()))
                .collect(Collectors.toList()));
          } else {
            consumer.seekToBeginning(Collections.singletonList(
                new TopicPartition(topic, partition)));
          }

          return Result.ok();
        }
    );
  }

  private String seekToEnd(Request request, Response response) {
    return postResponse(
        request,
        response,
        SEEK_TO_END_API_FIELDS,
        args -> {
          final KafkaConsumerManager consumerManager = KafkaConsumerManager.getInstance();
          final Optional<KafkaConsumer<String, String>> consumerOptional = consumerManager
              .getConsumer(
                  args.getString("consumer"));
          if (!consumerOptional.isPresent()) {
            return Result.failWithCodeAndMessage(
                "consumer_not_found",
                String.format("Consumer %s not found", args.getString("consumer"))
            );
          }
          final KafkaConsumer<String, String> consumer = consumerOptional.get();
          final String topic = args.getString("topic");
          final int partition = args.getInt("partition", -1);
          if (partition == -1) {
            List<PartitionInfo> partitionInfoList = consumer.partitionsFor(topic);
            consumer.seekToEnd(partitionInfoList.stream()
                .map(pi -> new TopicPartition(topic, pi.partition()))
                .collect(Collectors.toList()));
          } else {
            consumer.seekToEnd(Collections.singletonList(
                new TopicPartition(topic, partition)));
          }

          return Result.ok();
        }
    );
  }

  private String seekToTimestamp(Request request, Response response) {
    return postResponse(
        request,
        response,
        SEEK_TO_TIMESTAMP_API_FIELDS,
        args -> {
          final KafkaConsumerManager consumerManager = KafkaConsumerManager.getInstance();
          final Optional<KafkaConsumer<String, String>> consumerOptional = consumerManager
              .getConsumer(
                  args.getString("consumer"));
          if (!consumerOptional.isPresent()) {
            return Result.failWithCodeAndMessage(
                "consumer_not_found",
                String.format("Consumer %s not found", args.getString("consumer"))
            );
          }
          final KafkaConsumer<String, String> consumer = consumerOptional.get();
          final String topic = args.getString("topic");
          final int partition = args.getInt("partition", -1);
          final long timestamp = args.getInt("timestamp");

          List<TopicPartition> partitions;
          if (partition == -1) {
            partitions = consumer.partitionsFor(topic).stream()
                .map(pi -> new TopicPartition(topic, pi.partition()))
                .collect(Collectors.toList());
          } else {
            partitions = Collections.singletonList(
                new TopicPartition(topic, partition));
          }

          Map<TopicPartition, OffsetAndTimestamp> offsets = consumer
              .offsetsForTimes(partitions.stream().collect(Collectors.toMap(
                  Function.identity(), v -> timestamp)));

          if (MapUtils.isNotEmpty(offsets)) {
            offsets.forEach((tp, offset) -> consumer.seek(tp, offset.offset()));
          }

          return Result.ok();
        }
    );
  }

  private String closeConsumer(Request request, Response response) {
    return postResponse(
        request,
        response,
        CLOSE_CONSUMER_API_FIELDS,
        args -> {
          final String consumerName = args.getString("consumer");
          final Optional<KafkaConsumer<String, String>> consumerOptional = KafkaConsumerManager
              .getInstance().getConsumer(consumerName);
          if (!consumerOptional.isPresent()) {
            return Result.failWithCodeAndMessage(
                "not_found", String.format("The consumer %s not found", consumerName));
          }
          final KafkaConsumer<String, String> consumer = consumerOptional.get();
          consumer.close();
          return Result.ok();
        });
  }

  private String fullCompact(Request request, Response response) {
    return postResponse(
        request,
        response,
        FULL_COMPACTION_API_FIELDS,
        args -> {
          final String columnFamily = args.getString("cf");
          RocksDBHelper.useColumnFamily(columnFamily).compactRange();
          return Result.ok();
        }
    );
  }

  private String createCheckpoint(Request request, Response response) {
    return postResponse(
        request,
        response,
        CREATE_CHECKPOINT_API_FIELDS,
        args -> {
          final String dir = args.getString("dir");
          final boolean async = args.getBoolean("async", false);
          if (async) {
            final Thread thread = new Thread(
                () -> RocksDBHelper.getInstance().createCheckpoint(dir));
            thread.setDaemon(true);
            thread.start();
            return Result.ok();
          } else {
            return RocksDBHelper.getInstance().createCheckpoint(dir);
          }
        }
    );
  }

  private String getBackupInfo(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        new Field[]{},
        args -> {
          final List<BackupInfoModel> backupInfoList = RocksDBHelper.getInstance().getBackupInfo();
          return Result.okWithData(ImmutableMap.of(
              "num",
              backupInfoList.size(),
              "backup",
              backupInfoList.stream().map(BackupInfoModel::toMap).collect(Collectors.toList())
          ));
        }
    );
  }

  private String createNewBackup(Request request, Response response) {
    return postResponse(
        request,
        response,
        CREATE_BACKUP_API_FIELDS,
        args -> {
          final boolean flushBeforeBackup = args.getBoolean(
              "flush_before_backup", false);
          return RocksDBHelper.getInstance().createNewBackup(flushBeforeBackup);
        }
    );
  }

  private String purgeOldBackups(Request request, Response response) {
    return postResponse(
        request,
        response,
        PURGE_OLD_BACKUPS_API_FIELDS,
        args -> {
          final int num = args.getInt("num");
          return RocksDBHelper.getInstance().purgeOldBackups(num);
        }
    );
  }

  private String getPerfLogRecords(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        new Field[]{},
        args -> Result.okWithData(Collections.singletonMap(
            "records",
            PerfLog.getPerfRecords().stream().map(PerfRecord::toMap).collect(Collectors.toList())
        ))
    );
  }

  private String setPerfLogEnable(Request request, Response response) {
    return postResponse(
        request,
        response,
        SET_PERF_LOG_FIELDS,
        args -> {
          final boolean enable = args.getBoolean("enable");
          PerfLog.setEnable(enable);
          final long period = args.getLong("period", 1000);
          PerfLog.setOutputPeriod(period);
          return Result.ok();
        }
    );
  }
}
