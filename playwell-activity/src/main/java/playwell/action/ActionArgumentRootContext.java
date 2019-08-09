package playwell.action;

import java.util.Map;
import playwell.activity.ActivityArgumentVar;
import playwell.activity.definition.ActivityDefArgumentVar;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ActivityThreadArgumentVar;
import playwell.common.EasyMap;
import playwell.common.argument.ActivityThreadAccessMixin;
import playwell.common.argument.BaseArgumentRootContext;
import playwell.common.argument.ConfigVarAccessMixin;
import playwell.common.argument.ContextVarAccessMixin;
import playwell.common.argument.EventVarAccessMixin;
import playwell.message.Message;
import playwell.message.MessageArgumentVar;

/**
 * Action执行时的顶级变量上下文
 *
 * @author chihongze@gmail.com
 */
public class ActionArgumentRootContext extends BaseArgumentRootContext implements
    ContextVarAccessMixin, EventVarAccessMixin, ConfigVarAccessMixin, ActivityThreadAccessMixin {

  /**
   * 活动定义
   */
  public final ActivityDefArgumentVar definition;

  /**
   * 活动自身元数据
   */
  public final ActivityArgumentVar activity;

  /**
   * 活动线程自身元数据
   */
  public final ActivityThreadArgumentVar thread;

  /**
   * 事件
   */
  public final MessageArgumentVar event;

  /**
   * 上下文
   */
  public final Map<String, Object> context;

  /**
   * 活动配置
   */
  public final EasyMap config;


  public ActionArgumentRootContext(ActivityThread activityThread) {
    this(activityThread, null);
  }

  public ActionArgumentRootContext(ActivityThread activityThread, Message message) {
    this.definition = new ActivityDefArgumentVar(activityThread.getActivityDefinition());
    this.activity = new ActivityArgumentVar(activityThread.getActivity());
    this.thread = new ActivityThreadArgumentVar(activityThread);
    this.event =
        message == null ? MessageArgumentVar.emptyMessage() : new MessageArgumentVar(message);
    this.context = activityThread.getContext();
    this.config = new EasyMap(activityThread.getActivity().getConfig());
  }

  @Override
  public Map<String, Object> getContext() {
    return context;
  }

  @Override
  public MessageArgumentVar getEvent() {
    return event;
  }

  @Override
  public EasyMap getConfig() {
    return config;
  }

  @Override
  public ActivityThreadArgumentVar getActivityThread() {
    return thread;
  }
}
