package playwell.activity.thread.message;

import java.util.HashMap;
import playwell.activity.thread.ActivityThread;
import playwell.clock.CachedTimestamp;
import playwell.message.Message;

/**
 * 该消息用于Replication，当消息被删除的时候，会使用该消息进行同步
 */
public class RemoveActivityThreadMessage extends Message {

  public static final String TYPE = "rm_thread";

  private final int activityId;

  private final String domainId;

  public RemoveActivityThreadMessage(ActivityThread activityThread) {
    this(activityThread.getActivity().getId(), activityThread.getDomainId());
  }

  public RemoveActivityThreadMessage(int activityId, String domainId) {
    this("", "", activityId, domainId);
  }

  public RemoveActivityThreadMessage(
      String outputService, String inputService, int activityId, String domainId) {
    super(
        TYPE,
        outputService,
        inputService,
        new HashMap<>(2),
        CachedTimestamp.nowMilliseconds()
    );

    this.activityId = activityId;
    this.getAttributes().put(Attributes.ACTIVITY_ID, activityId);

    this.domainId = domainId;
    this.getAttributes().put(Attributes.DOMAIN_ID, domainId);
  }

  public int getActivityId() {
    return activityId;
  }

  public String getDomainId() {
    return domainId;
  }

  public interface Attributes {

    String ACTIVITY_ID = "activity_id";

    String DOMAIN_ID = "domain_id";
  }
}
