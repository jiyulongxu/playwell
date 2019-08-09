package playwell.action;

import java.util.List;
import java.util.Map;
import playwell.activity.Activity;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.thread.ActivityThread;
import playwell.common.argument.ExpressionArgument;
import playwell.common.argument.ListArgument;
import playwell.common.argument.MapArgument;
import playwell.common.expression.PlaywellExpressionContext;
import playwell.common.expression.spel.SpELPlaywellExpressionContext;
import playwell.message.Message;


/**
 * Action，所有Action实现的基类
 *
 * @author chihongze@gmail.com
 */
public abstract class Action {

  protected final String name;

  protected final String type;

  protected final ActionDefinition actionDefinition;

  protected final ActivityThread activityThread;

  protected final Activity activity;

  protected final ActivityDefinition activityDefinition;

  protected Action(ActivityThread activityThread) {
    this.activityDefinition = activityThread.getActivityDefinition();
    this.actionDefinition = activityDefinition
        .getActionDefinitionByName(activityThread.getCurrentAction());
    this.name = actionDefinition.getName();
    this.type = actionDefinition.getActionType();
    this.activityThread = activityThread;
    this.activity = activityThread.getActivity();
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public ActionDefinition getActionDefinition() {
    return actionDefinition;
  }

  public ActivityThread getActivityThread() {
    return activityThread;
  }

  public Activity getActivity() {
    return activity;
  }

  public ActivityDefinition getActivityDefinition() {
    return activityDefinition;
  }

  protected PlaywellExpressionContext getArgExpressionContext() {
    return getArgExpressionContext(null);
  }

  protected PlaywellExpressionContext getArgExpressionContext(Message message) {
    SpELPlaywellExpressionContext ctx = new SpELPlaywellExpressionContext();
    ctx.setRootObject(new ActionArgumentRootContext(this.activityThread, message));
    return ctx;
  }

  protected <T> T getValueArgument() {
    return this.getValueArgument(null);
  }

  @SuppressWarnings({"unchecked"})
  protected <T> T getValueArgument(Message message) {
    final ExpressionArgument argument = (ExpressionArgument) this.actionDefinition.getArguments();
    return (T) argument.getValue(getArgExpressionContext(message));
  }

  protected Map<String, Object> getMapArguments() {
    return this.getMapArguments(null);
  }

  protected Map<String, Object> getMapArguments(Message message) {
    final MapArgument arguments = (MapArgument) this.actionDefinition.getArguments();
    return arguments.getValueMap(getArgExpressionContext(message));
  }

  protected List<Object> getListArguments() {
    return this.getListArguments(null);
  }

  protected List<Object> getListArguments(Message message) {
    final ListArgument arguments = (ListArgument) this.actionDefinition.getArguments();
    return arguments.getValueList(getArgExpressionContext(message));
  }

  /**
   * 通用的运行时错误码
   */
  public interface CommonRuntimeErrorCodes {

    String INVALID_ARGUMENT = "invalid_argument";

    String SERVICE_NOT_FOUND = "service_not_found";

    String BUS_NOT_FOUND = "bus_not_found";

    String BUS_UNAVAILABLE = "bus_unavailable";
  }
}
