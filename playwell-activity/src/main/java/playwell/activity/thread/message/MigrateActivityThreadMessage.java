package playwell.activity.thread.message;

import playwell.activity.thread.ActivityThread;
import playwell.clock.CachedTimestamp;
import playwell.message.Message;

/**
 * 该消息用于通过MessageBus来迁移ActivityThread
 */
public class MigrateActivityThreadMessage extends Message {

  public static final String TYPE = "migration";

  private final ActivityThread activityThread;

  public MigrateActivityThreadMessage(ActivityThread activityThread) {
    this("", "", activityThread);
  }

  public MigrateActivityThreadMessage(
      String outputService, String inputService, ActivityThread activityThread) {
    super(
        TYPE,
        outputService,
        inputService,
        activityThread.toDataMap(),
        CachedTimestamp.nowMilliseconds()
    );
    this.activityThread = activityThread;
  }

  public ActivityThread getActivityThread() {
    return this.activityThread;
  }
}
