package playwell.clock;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.collections4.MapUtils;
import playwell.activity.ActivityRunner;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.message.ActivityThreadMessage;
import playwell.message.Message;


/**
 * 时钟事件，等到达指定的时刻时，系统会将该事件传递给指定的ActivityThread
 *
 * @author chihongze@gmail.com
 */
public class ClockMessage extends Message implements Comparable<ClockMessage>,
    ActivityThreadMessage {

  public static final String TYPE = "clock";

  private final long timePoint;

  private final int activityId;

  private final String domainId;

  private final String action;

  private final Map<String, Object> extraArgs;

  public ClockMessage(String sender, String receiver, long timePoint, int activity, String domainId,
      String action,
      long createdOn) {
    this(sender, receiver, timePoint, activity, domainId, action, Collections.emptyMap(),
        createdOn);
  }

  public ClockMessage(String sender, String receiver, long timePoint, int activityId,
      String domainId, String action,
      Map<String, Object> extraArgs, long createdOn) {
    this(TYPE, sender, receiver, timePoint, activityId, domainId, action, extraArgs, createdOn);
  }

  private ClockMessage(
      String type,
      String sender,
      String receiver,
      long timePoint,
      int activityId,
      String domainId,
      String action,
      Map<String, Object> extraArgs,
      long createdOn) {
    super(
        type,
        sender,
        receiver,
        ImmutableMap.<String, Object>builder()
            .put(Attributes.TIME_POINT, timePoint)
            .put(Attributes.ACTIVITY, activityId)
            .put(Attributes.DOMAIN_ID, domainId)
            .put(Attributes.ACTION, action)
            .put(Attributes.EXTRA, MapUtils.isEmpty(extraArgs) ? Collections.emptyMap() : extraArgs)
            .build(),
        createdOn
    );

    this.timePoint = timePoint;
    this.activityId = activityId;
    this.domainId = domainId;
    this.action = action;
    this.extraArgs = extraArgs;
  }

  public static ClockMessage buildForActivity(long timePoint, int activity, String domainId,
      String action) {
    return buildForActivity(timePoint, activity, domainId, action, Collections.emptyMap());
  }

  public static ClockMessage buildForActivity(
      long timePoint, int activity, String domainId, String action, Map<String, Object> extraArgs) {
    final ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final ActivityRunner activityRunner = integrationPlan.getActivityRunner();
    return new ClockMessage(
        activityRunner.getServiceName(),
        "",
        timePoint,
        activity,
        domainId,
        action,
        extraArgs,
        CachedTimestamp.nowMilliseconds()
    );
  }

  public long getTimePoint() {
    return timePoint;
  }

  @Override
  public int getActivityId() {
    return activityId;
  }

  @Override
  public String getDomainId() {
    return domainId;
  }

  public String getAction() {
    return action;
  }

  public Map<String, Object> getExtraArgs() {
    return extraArgs;
  }

  @Override
  public int compareTo(ClockMessage anotherClockMessage) {
    if (anotherClockMessage == null) {
      throw new IllegalArgumentException("Not allow compare with null");
    }

    return Long.compare(this.getTimePoint(), anotherClockMessage.getTimePoint());
  }

  public interface Attributes {

    String TIME_POINT = "time_point";

    String ACTIVITY = "activity";

    String DOMAIN_ID = "domain_id";

    String ACTION = "action";

    String EXTRA = "extra";
  }
}
