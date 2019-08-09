package playwell.activity.thread;

import com.google.common.collect.ImmutableMap;
import playwell.common.Result;


/**
 * ActivityThread调度器执行结果，通常返回给ActivityRunner进行参照
 *
 * @author chihongze@gmail.com
 */
public class ScheduleResult extends Result {

  // 调度之后，新的ActivityThread实例
  private final ActivityThread activityThread;

  public ScheduleResult(
      String status, String errorCode, String message, ActivityThread activityThread) {
    super(status, errorCode, message, ImmutableMap.of(
        Attributes.ACTIVITY_THREAD, activityThread
    ));
    this.activityThread = activityThread;
  }

  public static ScheduleResult ok(ActivityThread activityThread) {
    return new ScheduleResult(
        Result.STATUS_OK,
        "",
        "",
        activityThread
    );
  }

  public static ScheduleResult fail(
      String errorCode, String message, ActivityThread activityThread) {
    return new ScheduleResult(
        Result.STATUS_FAIL,
        errorCode,
        message,
        activityThread
    );
  }

  public ActivityThread getActivityThread() {
    return activityThread;
  }

  public interface Attributes {

    String ACTIVITY_THREAD = "activity_thread";
  }
}
