package playwell.activity;

import playwell.common.EasyMap;
import playwell.util.DateUtils;


/**
 * ActivityArgumentVar用于渲染活动定义参数中的ACTIVITY变量
 *
 * @author chihongze@gmail.com
 */
public class ActivityArgumentVar {

  private final Activity activity;

  public ActivityArgumentVar(Activity activity) {
    this.activity = activity;
  }

  public int getId() {
    return activity.getId();
  }

  public String getDisplayName() {
    return activity.getDisplayName();
  }

  public String getStatus() {
    return activity.getStatus().getStatus();
  }

  public EasyMap getConfig() {
    return new EasyMap(activity.getConfig());
  }

  public String getCreatedOn() {
    return DateUtils.format(activity.getCreatedOn());
  }
}
