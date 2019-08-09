package playwell.activity.thread;

import java.util.Map;
import playwell.activity.ActivityArgumentVar;
import playwell.activity.definition.ActivityDefArgumentVar;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.common.argument.ActivityThreadAccessMixin;
import playwell.common.argument.BaseArgumentRootContext;
import playwell.common.argument.ConfigVarAccessMixin;
import playwell.common.argument.ContextVarAccessMixin;
import playwell.common.argument.ResultVarAccessMixin;

/**
 * 调度渲染ctrl string和上下文时的顶级变量
 *
 * @author chihongze@gmail.com
 */
public class ScheduleArgumentRootContext extends BaseArgumentRootContext implements
    ContextVarAccessMixin, ConfigVarAccessMixin, ResultVarAccessMixin, ActivityThreadAccessMixin {

  public final ActivityDefArgumentVar definition;

  public final ActivityArgumentVar activity;

  public final ActivityThreadArgumentVar thread;

  public final Map<String, Object> context;

  public final Result result;

  public final EasyMap config;

  public ScheduleArgumentRootContext(ActivityThread activityThread, Result result) {
    thread = new ActivityThreadArgumentVar(activityThread);
    definition = new ActivityDefArgumentVar(activityThread.getActivityDefinition());
    activity = new ActivityArgumentVar(activityThread.getActivity());
    context = activityThread.getContext();
    this.result = result;
    config = new EasyMap(activityThread.getActivity().getConfig());
  }

  @Override
  public Map<String, Object> getContext() {
    return context;
  }

  @Override
  public EasyMap getConfig() {
    return config;
  }

  @Override
  public Result getResult() {
    return result;
  }

  @Override
  public ActivityThreadArgumentVar getActivityThread() {
    return thread;
  }
}
