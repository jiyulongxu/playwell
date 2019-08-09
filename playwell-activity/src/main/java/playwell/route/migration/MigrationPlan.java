package playwell.route.migration;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import org.apache.commons.collections4.MapUtils;
import playwell.common.Mappable;
import playwell.util.DateUtils;
import playwell.util.ModelUtils;

/**
 * Slots迁移计划
 */
public class MigrationPlan implements Mappable {

  // 迁移所使用的MessageBus
  private final String messageBus;

  // 读取迁移数据所使用的MessageBus配置信息
  private final Map<String, Object> inputMessageBusConfig;

  // 写入迁移数据所使用的MessageBus配置信息
  private final Map<String, Object> outputMessageBusConfig;

  // slots分布
  private final Map<String, Integer> slotsDistribution;

  // 迁移说明
  private final String comment;

  // 迁移开始时间
  private final Date createdOn;

  public MigrationPlan(
      String messageBus,
      Map<String, Object> inputMessageBusConfig,
      Map<String, Object> outputMessageBusConfig,
      Map<String, Integer> slotsDistribution,
      String comment, Date createdOn) {
    this.messageBus = messageBus;
    this.inputMessageBusConfig = MapUtils.isEmpty(inputMessageBusConfig) ?
        Collections.emptyMap() : inputMessageBusConfig;
    this.outputMessageBusConfig = MapUtils.isEmpty(outputMessageBusConfig) ?
        Collections.emptyMap() : outputMessageBusConfig;
    this.slotsDistribution = slotsDistribution;
    this.comment = comment;
    this.createdOn = createdOn;
  }

  public String getMessageBus() {
    return messageBus;
  }

  public Map<String, Object> getInputMessageBusConfig() {
    return inputMessageBusConfig;
  }

  public Map<String, Object> getOutputMessageBusConfig() {
    return outputMessageBusConfig;
  }

  public Map<String, Integer> getSlotsDistribution() {
    return this.slotsDistribution;
  }

  public String getComment() {
    return comment;
  }

  public Date getCreatedOn() {
    return createdOn;
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.<String, Object>builder()
        .put(Fields.MESSAGE_BUS, messageBus)
        .put(Fields.INPUT_MESSAGE_BUS_CONFIG, ModelUtils.expandMappable(inputMessageBusConfig))
        .put(Fields.OUTPUT_MESSAGE_BUS_CONFIG, ModelUtils.expandMappable(outputMessageBusConfig))
        .put(Fields.SLOTS_DISTRIBUTION, slotsDistribution)
        .put(Fields.COMMENT, comment)
        .put(Fields.CREATED_ON, DateUtils.format(createdOn)).build();
  }

  @Override
  public String toString() {
    return String.format(
        "MigrationPlan@%d%s",
        System.identityHashCode(this),
        JSONObject.toJSONString(toMap())
    );
  }

  interface Fields {

    String MESSAGE_BUS = "message_bus";

    String INPUT_MESSAGE_BUS_CONFIG = "input_message_bus_config";

    String OUTPUT_MESSAGE_BUS_CONFIG = "output_message_bus_config";

    String SLOTS_DISTRIBUTION = "slots_distribution";

    String COMMENT = "comment";

    String CREATED_ON = "created_on";
  }
}
