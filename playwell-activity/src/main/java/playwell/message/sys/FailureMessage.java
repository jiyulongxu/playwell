package playwell.message.sys;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import playwell.activity.thread.ActivityThread;
import playwell.message.ServiceRequestMessage;

/**
 * 当ActivityThread执行失败时，将此消息发送给monitor
 */
public class FailureMessage extends ServiceRequestMessage {

  private static final String EVENT = "failure";

  private final String failureReason;

  private final Map<String, Object> context;

  public FailureMessage(
      long timestamp,
      ActivityThread activityThread,
      String sender,
      String receiver,
      String failureReason
  ) {
    super(
        timestamp,
        activityThread,
        sender,
        receiver,
        ImmutableMap.of(
            Args.EVENT, FailureMessage.EVENT,
            Args.REASON, failureReason,
            Args.CONTEXT, activityThread.getContext()
        ),
        true
    );
    this.context = activityThread.getContext();
    this.failureReason = failureReason;
  }

  public String getFailureReason() {
    return this.failureReason;
  }

  public Map<String, Object> getContext() {
    return this.context;
  }

  public interface Args {

    String EVENT = "event";

    String REASON = "reason";

    String CONTEXT = "context";
  }
}
