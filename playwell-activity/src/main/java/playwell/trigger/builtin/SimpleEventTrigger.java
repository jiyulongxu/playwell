package playwell.trigger.builtin;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import playwell.activity.Activity;
import playwell.activity.definition.ActivityDefinition;
import playwell.common.EasyMap;
import playwell.message.Message;
import playwell.trigger.Trigger;
import playwell.trigger.TriggerInstanceBuilder;
import playwell.trigger.TriggerMatchResult;

/**
 * 简单事件触发器 遍历邮箱中的所有事件，一旦有事件满足了条件，就可以Spawn新的ActivityThread了
 *
 * @author chihongze@gmail.com
 */
public class SimpleEventTrigger extends Trigger {

  public static final String TYPE = "event";

  public static final TriggerInstanceBuilder BUILDER = SimpleEventTrigger::new;

  public SimpleEventTrigger(Activity activity, ActivityDefinition latestActivityDefinition) {
    super(activity, latestActivityDefinition);
  }

  @Override
  protected TriggerMatchResult isMatchCondition(String domainId, Collection<Message> mailbox) {
    Map<String, Object> initContextVars = Collections.emptyMap();
    List<Message> filteredMailBox = new LinkedList<>();
    boolean matched = false;
    for (Message message : mailbox) {
      EasyMap args = new EasyMap(getMapArguments(message));
      if (!matched) {
        matched = args.getBoolean(ArgFields.CONDITION, false);
        initContextVars = getInitContext(message);
      } else {
        filteredMailBox.add(message);
      }
    }

    if (matched) {
      return TriggerMatchResult.matchResult(filteredMailBox, initContextVars);
    } else {
      return TriggerMatchResult.unmatchedResult();
    }
  }

  public interface ArgFields {

    // 判断条件参数
    String CONDITION = "condition";
  }
}
