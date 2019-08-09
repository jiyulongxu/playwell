package playwell.integration;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.message.bus.MessageBusManager;
import playwell.service.ServiceMetaManager;
import playwell.service.ServiceRunner;
import playwell.util.Sleeper;

/**
 * 基于PlaywellComponent实现的标准ServiceRunner集成方案
 *
 * @author chihongze
 */
public class StandardServiceRunnerIntegrationPlan extends BaseStandardIntegrationPlan implements
    ServiceRunnerIntegrationPlan {

  private static final Logger logger = LogManager.getLogger(
      StandardServiceRunnerIntegrationPlan.class);

  public StandardServiceRunnerIntegrationPlan() {

  }

  @Override
  public MessageBusManager getMessageBusManager() {
    return checkInitThenReturn(TopComponentType.MESSAGE_BUS_MANAGER);
  }

  @Override
  public ServiceMetaManager getServiceMetaManager() {
    return checkInitThenReturn(TopComponentType.SERVICE_META_MANAGER);
  }

  @Override
  public ServiceRunner getServiceRunner() {
    return checkInitThenReturn(TopComponentType.SERVICE_RUNNER);
  }

  @Override
  protected List<Pair<TopComponentType, Boolean>> getTopComponents() {
    return Arrays.asList(
        Pair.of(TopComponentType.MESSAGE_BUS_MANAGER, true),
        Pair.of(TopComponentType.SERVICE_META_MANAGER, true),
        Pair.of(TopComponentType.SERVICE_RUNNER, true)
    );
  }

  @Override
  protected void close() {
    final ServiceRunner serviceRunner = getServiceRunner();
    serviceRunner.stop();
    while (!serviceRunner.isStopped()) {
      logger.info("Waiting ServiceRunner stopped");
      Sleeper.sleepInSeconds(1);
    }
  }
}
