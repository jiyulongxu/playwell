package playwell.message.sys;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import playwell.activity.thread.ActivityThread;
import playwell.message.ServiceRequestMessage;

/**
 * NeedRepairMessage 当ActivityThread调用了repair("problem")函数时，会向Monitor发送该消息
 */
public class NeedRepairMessage extends ServiceRequestMessage {

  private static final String EVENT = "repair";

  private final String problem;

  private final Map<String, Object> context;

  public NeedRepairMessage(
      long timestamp,
      ActivityThread activityThread,
      String sender,
      String receiver,
      String problem) {
    super(
        timestamp,
        activityThread,
        sender,
        receiver,
        ImmutableMap.of(
            Args.EVENT, NeedRepairMessage.EVENT,
            Args.PROBLEM, problem,
            Args.CONTEXT, activityThread.getContext()
        ),
        true
    );
    this.problem = problem;
    this.context = activityThread.getContext();
  }

  public String getProblem() {
    return problem;
  }

  public Map<String, Object> getContext() {
    return this.context;
  }

  public interface Args {

    String EVENT = "event";

    String PROBLEM = "problem";

    String CONTEXT = "context";
  }
}
