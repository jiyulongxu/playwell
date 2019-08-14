package playwell.action.builtin;

import java.util.Map;
import java.util.function.Consumer;
import playwell.action.ActionInstanceBuilder;
import playwell.action.AsyncAction;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ScheduleConfigItems;
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
 * <p>SleepAction会将当前ActivityThread休眠指定时间</p>
 * eg.
 *
 * <pre>
 *   - name: sleep
 *     type: sleep
 *     args:
 *       time: timestamp("10 seconds")
 *     ctrl:
 *       - default: call("xxx")
 * </pre>
 *
 * @author chihongze@gmail.com
 */
public class SleepAction extends AsyncAction {

  public static final String TYPE = "sleep";

  public static final ActionInstanceBuilder BUILDER = SleepAction::new;

  public static final Consumer<Argument> ARG_SPEC = argument -> {
    if (!Argument.isMapArgument(argument)) {
      throw new BuildComponentException(
          "Invalid argument format, the sleep action only accept map argument."
      );
    }

    final Map<String, Argument> mapArguments = ((MapArgument) argument).getArgs();
    if (!mapArguments.containsKey(ArgNames.TIME)) {
      throw new BuildComponentException(
          "Invalid argument format, the sleep action required time argument."
      );
    }
  };

  public SleepAction(ActivityThread activityThread) {
    super(activityThread);
  }

  @Override
  public void sendRequest() {
    final EasyMap arguments = new EasyMap(this.getMapArguments());
    final long timestamp = arguments.getLong("time");
    ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    Clock clock = integrationPlan.getClock();
    clock.registerClockMessage(ClockMessage.buildForActivity(
        System.currentTimeMillis() + timestamp,
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

    final ClockMessage clockMessage = (ClockMessage) message;
    if (!getName().equals(clockMessage.getAction()) ||
        clockMessage.getTimestamp() < getActivityThread().getCreatedOn()) {
      return Result.ignore();
    }

    final EasyMap config = new EasyMap(getActivity().getConfig());
    final boolean keepSleep = config.getWithConfigItem(ScheduleConfigItems.KEEP_SLEEP);

    if (keepSleep) {
      sendRequest();
      return Result.ignore();
    }

    return Result.ok();
  }

  interface ArgNames {

    String TIME = "time";
  }
}
