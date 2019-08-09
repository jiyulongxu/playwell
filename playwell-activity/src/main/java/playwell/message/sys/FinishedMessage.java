package playwell.message.sys;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import playwell.activity.thread.ActivityThread;
import playwell.message.ServiceRequestMessage;

/**
 * 当ActivityThread执行完毕时，会向Monitor发送该消息
 */
public class FinishedMessage extends ServiceRequestMessage {

  private static final String EVENT = "finished";

  private final Map<String, Object> context;

  public FinishedMessage(long timestamp, ActivityThread activityThread, String sender,
      String receiver) {
    super(
        timestamp,
        activityThread,
        sender,
        receiver,
        ImmutableMap.of(
            Args.EVENT, FinishedMessage.EVENT,
            Args.CONTEXT, activityThread.getContext()
        ),
        true
    );
    this.context = activityThread.getContext();
  }

  public String getEvent() {
    return FinishedMessage.EVENT;
  }

  public Map<String, Object> getContext() {
    return this.context;
  }

  public interface Args {

    String EVENT = "event";

    String CONTEXT = "context";
  }

}
