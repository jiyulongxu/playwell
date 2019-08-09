package playwell.clock;

import java.util.HashMap;
import playwell.message.Message;

/**
 * CleanTimeRangeMessage用于通知Replication某个时间点的消息已经被清除
 */
public class CleanTimeRangeMessage extends Message {

  public static final String TYPE = "clean_time";

  private final long timePoint;

  public CleanTimeRangeMessage(long timePoint) {
    super(
        TYPE,
        "",
        "",
        new HashMap<>(1),
        CachedTimestamp.nowMilliseconds()
    );

    this.timePoint = timePoint;
    this.getAttributes().put(Attributes.TIME_POINT, timePoint);
  }

  public long getTimePoint() {
    return this.timePoint;
  }

  public interface Attributes {

    String TIME_POINT = "time_point";
  }
}
