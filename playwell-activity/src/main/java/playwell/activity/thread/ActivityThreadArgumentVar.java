package playwell.activity.thread;

import java.util.Date;
import playwell.util.DateUtils;

public class ActivityThreadArgumentVar {

  private final ActivityThread activityThread;

  public ActivityThreadArgumentVar(ActivityThread activityThread) {
    this.activityThread = activityThread;
  }

  public int getActivityId() {
    return activityThread.getActivity().getId();
  }

  public String getDomainId() {
    return activityThread.getDomainId();
  }

  public String getStatus() {
    return activityThread.getStatus().getStatus();
  }

  public String getCurrentAction() {
    return activityThread.getCurrentAction();
  }

  public String getCreatedOn() {
    return DateUtils.format(new Date(activityThread.getCreatedOn()));
  }
}
