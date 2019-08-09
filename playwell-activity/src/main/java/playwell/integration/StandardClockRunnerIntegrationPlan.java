package playwell.integration;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.clock.Clock;
import playwell.clock.ClockRunner;
import playwell.message.bus.MessageBusManager;
import playwell.service.ServiceMetaManager;
import playwell.util.Sleeper;

/**
 * StandardClockRunnerIntegrationPlan
 */
public class StandardClockRunnerIntegrationPlan extends
    BaseStandardIntegrationPlan implements ClockRunnerIntegrationPlan {

  private static final Logger logger = LogManager
      .getLogger(StandardClockRunnerIntegrationPlan.class);

  @Override
  public MessageBusManager getMessageBusManager() {
    return checkInitThenReturn(TopComponentType.MESSAGE_BUS_MANAGER);
  }

  @Override
  public ServiceMetaManager getServiceMetaManager() {
    return checkInitThenReturn(TopComponentType.SERVICE_META_MANAGER);
  }

  @Override
  public Clock getClock() {
    return checkInitThenReturn(TopComponentType.CLOCK);
  }

  @Override
  public ClockRunner getClockRunner() {
    return checkInitThenReturn(TopComponentType.CLOCK_RUNNER);
  }

  @Override
  protected List<Pair<TopComponentType, Boolean>> getTopComponents() {
    return Arrays.asList(
        Pair.of(TopComponentType.MESSAGE_BUS_MANAGER, true),
        Pair.of(TopComponentType.SERVICE_META_MANAGER, true),
        Pair.of(TopComponentType.CLOCK, true),
        Pair.of(TopComponentType.CLOCK_RUNNER, true)
    );
  }

  @Override
  protected void close() {
    final ClockRunner clockRunner = getClockRunner();
    clockRunner.stop();
    while (!clockRunner.isStopped()) {
      logger.info("Waiting ClockRunner stopped");
      Sleeper.sleepInSeconds(1);
    }
  }
}
