package playwell.clock;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.clock.MessageClock.ConfigItems;
import playwell.clock.MessageClock.ServiceConfigItems;
import playwell.common.EasyMap;
import playwell.integration.ClockRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.message.Message;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.util.Sleeper;

/**
 * Test case for message clock
 */
public class MessageClockTestCase {

  private ClockRunner clockRunner;

  private MessageClock messageClock;

  private MessageBus messageBus;

  @Before
  public void setUp() {
    IntegrationPlanFactory.getInstance().intergrateWithYamlConfigFile(
        "playwell.integration.StandardClockRunnerIntegrationPlan",
        "config/playwell_clock.yml"
    );
    ClockRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    this.clockRunner = integrationPlan.getClockRunner();
    new Thread(clockRunner::dispatch).start();
    Sleeper.sleepInSeconds(1);
    this.messageClock = new MessageClock();
    this.messageClock.init(new EasyMap(ImmutableMap.of(ConfigItems.CLOCK_SERVICES,
        Collections.singletonList(
            ImmutableMap.of(
                ServiceConfigItems.NAME,
                "clock_runner"
            )
        ))));
    final MessageBusManager messageBusManager = integrationPlan.getMessageBusManager();
    this.messageBus = messageBusManager.getMessageBusByName("test_bus")
        .orElseThrow(() -> new RuntimeException("Could not found the test_bus"));
  }

  @Test
  public void test() throws Exception {
    this.messageClock.registerClockMessage(new ClockMessage(
        "test_service",
        "",
        System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5),
        5,
        "James",
        "hello",
        CachedTimestamp.nowMilliseconds()
    ));
    Sleeper.sleepInSeconds(2);
    Collection<Message> messages = this.messageBus.read(100);
    Assert.assertTrue(CollectionUtils.isEmpty(messages));
    Sleeper.sleepInSeconds(4);
    messages = this.messageBus.read(100);
    Assert.assertEquals(1, messages.size());
    for (Message message : messages) {
      final ClockMessage clockMessage = (ClockMessage) message;
      Assert.assertEquals("test_service", clockMessage.getSender());
      Assert.assertEquals(5, clockMessage.getActivityId());
      Assert.assertEquals("James", clockMessage.getDomainId());
      Assert.assertEquals("hello", clockMessage.getAction());
    }
  }

  @After
  public void tearDown() {
    clockRunner.stop();
    while (!clockRunner.isStopped()) {
      Sleeper.sleepInSeconds(1);
    }
    IntegrationPlanFactory.getInstance().clean();
  }
}
