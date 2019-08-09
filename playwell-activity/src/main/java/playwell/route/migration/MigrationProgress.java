package playwell.route.migration;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import playwell.common.Mappable;
import playwell.util.DateUtils;

/**
 * 节点迁移进度描述
 */
public class MigrationProgress implements Mappable {

  private final MigrationProgressStatus status;

  private final List<Integer> slots;

  private final String outputNode;

  private final String inputNode;

  private final String outputLatestKey;

  private final String inputLatestKey;

  private final boolean outputFinished;

  private final boolean inputFinished;

  private final Date beginTime;

  private final Date endTime;

  public MigrationProgress(
      MigrationProgressStatus status,
      List<Integer> slots,
      String outputNode,
      String inputNode,
      String outputLatestKey,
      String inputLatestKey,
      boolean outputFinished,
      boolean inputFinished,
      Date beginTime,
      Date endTime
  ) {
    this.status = status;
    this.slots = slots;
    this.outputNode = outputNode;
    this.inputNode = inputNode;
    this.outputLatestKey = outputLatestKey;
    this.inputLatestKey = inputLatestKey;
    this.outputFinished = outputFinished;
    this.inputFinished = inputFinished;
    this.beginTime = beginTime;
    this.endTime = endTime;
  }

  public MigrationProgressStatus getStatus() {
    return status;
  }

  public List<Integer> getSlots() {
    return slots;
  }

  public String getOutputNode() {
    return outputNode;
  }

  public String getInputNode() {
    return inputNode;
  }

  public String getOutputLatestKey() {
    return outputLatestKey;
  }

  public String getInputLatestKey() {
    return inputLatestKey;
  }

  public boolean isOutputFinished() {
    return outputFinished;
  }

  public boolean isInputFinished() {
    return inputFinished;
  }

  public Date getBeginTime() {
    return beginTime;
  }

  public Date getEndTime() {
    return endTime;
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.<String, Object>builder()
        .put(Fields.STATUS, this.getStatus().getStatus())
        .put(Fields.SLOTS, this.getSlots())
        .put(Fields.OUTPUT_NODE, this.getOutputNode())
        .put(Fields.INPUT_NODE, this.getInputNode())
        .put(Fields.OUTPUT_LATEST_KEY, this.getOutputLatestKey())
        .put(Fields.INPUT_LATEST_KEY, this.getInputLatestKey())
        .put(Fields.BEGIN_TIME, this.beginTime == null ? "" : DateUtils.format(this.getBeginTime()))
        .put(Fields.END_TIME, this.endTime == null ? "" : DateUtils.format(this.getEndTime()))
        .build();
  }

  @Override
  public String toString() {
    return String.format(
        "MigrationProgress@%d%s",
        System.identityHashCode(this),
        JSONObject.toJSONString(toMap())
    );
  }

  interface Fields {

    String STATUS = "status";

    String SLOTS = "slots";

    String OUTPUT_NODE = "output_node";

    String INPUT_NODE = "input_node";

    String OUTPUT_LATEST_KEY = "output_latest_key";

    String INPUT_LATEST_KEY = "input_latest_key";

    String BEGIN_TIME = "begin_time";

    String END_TIME = "end_time";
  }
}
