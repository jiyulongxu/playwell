package playwell.activity.thread;

import java.util.Collections;
import playwell.common.ConfigItem;

/**
 * 调度相关的配置项
 *
 * @author chihongze@gmail.com
 */
public enum ScheduleConfigItems implements ConfigItem {

  MAX_CONTINUE_PERIODS("$schedule.max_continue_periods", -1),

  SUSPEND_TIME("$schedule.suspend_time", 1000),

  PAUSE_CONTINUE_OLD("$schedule.pause_continue_old", false),

  MONITORS("$monitors", Collections.emptyList()),

  KEEP_SLEEP("$keep_sleep", false),

  ;

  private final String key;

  private final Object defaultValue;

  ScheduleConfigItems(String key, Object defaultValue) {
    this.key = key;
    this.defaultValue = defaultValue;
  }

  public String getKey() {
    return key;
  }

  public Object getDefaultValue() {
    return defaultValue;
  }
}
