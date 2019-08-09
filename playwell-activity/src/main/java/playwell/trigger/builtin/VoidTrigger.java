package playwell.trigger.builtin;

import java.util.Collection;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.activity.Activity;
import playwell.activity.thread.ActivityThread;
import playwell.message.Message;
import playwell.trigger.Trigger;
import playwell.trigger.TriggerMatchResult;

/**
 * 当活动没有最新版本的可用定义时，使用该触发器，记录日志，什么也不做
 *
 * @author chihongze
 */
public class VoidTrigger extends Trigger {

  public static final String TYPE = "__void";

  private static final Logger logger = LogManager.getLogger(VoidTrigger.class);

  public VoidTrigger(Activity activity) {
    super(activity, null);
  }

  @Override
  public void handleMessageStream(Map<ActivityThread, Collection<Message>> collector,
      Map<String, Map<String, Collection<Message>>> messages) {
    logger.info(String.format(
        "There is not latest enable definition for activity: %d", this.activity.getId()));
  }

  @Override
  protected TriggerMatchResult isMatchCondition(String domainId, Collection<Message> mailbox) {
    return null;
  }
}
