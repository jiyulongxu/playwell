package playwell.action.builtin;


import java.util.Map;
import java.util.function.Consumer;
import org.joda.time.DateTime;
import playwell.action.ActionInstanceBuilder;
import playwell.action.AsyncAction;
import playwell.activity.thread.ActivityThread;
import playwell.clock.Clock;
import playwell.clock.ClockMessage;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.common.argument.Argument;
import playwell.common.argument.MapArgument;
import playwell.common.exception.BuildComponentException;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.message.Message;

/**
 * <p>ClockAction接受一个绝对的时间点作为参数
 * 当该时间点来临时，再来决策下一步的执行</p>
 *
 * <pre>
 *   - name: clock
 *     type: clock
 *     args:
 *       time: dateTime()  # 到明天9:30执行
 *        .plusDays(1)
 *        .withHourOfDay(9)
 *        .withMinuteOfHour(30)
 *     ctrl: call("next")
 * </pre>
 */
public class ClockAction extends AsyncAction {

  public static final String TYPE = "clock";

  public static final ActionInstanceBuilder BUILDER = ClockAction::new;

  public static final Consumer<Argument> ARG_SPEC = argument -> {
    if (!Argument.isMapArgument(argument)) {
      throw new BuildComponentException(
          "Invalid argument format, the clock action only accept map argument."
      );
    }

    final Map<String, Argument> mapArguments = ((MapArgument) argument).getArgs();
    if (!mapArguments.containsKey(ArgNames.TIME)) {
      throw new BuildComponentException(
          "Invalid argument format, the clock action required time argument."
      );
    }
  };

  public ClockAction(ActivityThread activityThread) {
    super(activityThread);
  }

  @Override
  public void sendRequest() {
    final EasyMap arguments = new EasyMap(this.getMapArguments());
    final DateTime time = (DateTime) arguments.get(ArgNames.TIME);
    ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    Clock clock = integrationPlan.getClock();
    clock.registerClockMessage(ClockMessage.buildForActivity(
        time.getMillis(),
        getActivity().getId(),
        getActivityThread().getDomainId(),
        this.getName()
    ));
  }

  @Override
  public Result handleResponse(Message message) {
    if (!(message instanceof ClockMessage)) {
      return Result.ignore();
    }

    ClockMessage clockMessage = (ClockMessage) message;
    if (!getName().equals(clockMessage.getAction()) ||
        clockMessage.getTimestamp() < activityThread.getCreatedOn()) {
      return Result.ignore();
    }

    return Result.ok();
  }

  interface ArgNames {

    String TIME = "time";
  }
}
