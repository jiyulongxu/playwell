package playwell.trigger;

import playwell.activity.Activity;
import playwell.activity.definition.ActivityDefinition;
import playwell.common.EasyMap;
import playwell.common.argument.ConfigVarAccessMixin;
import playwell.common.argument.EventVarAccessMixin;
import playwell.message.Message;
import playwell.message.MessageArgumentVar;

/**
 * 基于单个事件的触发器上下文
 *
 * @author chihongze@gmail.com
 */
public class SingleEventTriggerArgumentRootContext extends TriggerArgumentRootContext implements
    EventVarAccessMixin, ConfigVarAccessMixin {

  /**
   * 当前触发的事件信息
   */
  public final MessageArgumentVar event;

  /**
   * 当前活动的配置信息
   */
  public final EasyMap config;


  public SingleEventTriggerArgumentRootContext(
      ActivityDefinition activityDefinition, Activity activity, Message message) {
    super(activityDefinition, activity);
    this.event = new MessageArgumentVar(message);
    this.config = new EasyMap(activity.getConfig());
  }

  @Override
  public MessageArgumentVar getEvent() {
    return event;
  }

  @Override
  public EasyMap getConfig() {
    return config;
  }
}
