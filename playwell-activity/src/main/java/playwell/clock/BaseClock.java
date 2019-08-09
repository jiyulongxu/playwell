package playwell.clock;


import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import playwell.common.EasyMap;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.message.Message;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;

/**
 * BaseClock
 */
public abstract class BaseClock implements Clock {

  protected List<String> syncMessageBusNames;

  protected List<String> replicationMessageBusNames;

  protected volatile boolean inited = false;

  protected BaseClock() {

  }

  @Override
  public synchronized void init(Object config) {
    if (inited) {
      return;
    }

    final EasyMap configuration = (EasyMap) config;

    this.syncMessageBusNames = configuration.getStringList(ConfigItems.SYNC_MESSAGE_BUS);
    this.replicationMessageBusNames = configuration
        .getStringList(ConfigItems.REPLICATION_MESSAGE_BUS);

    initConfig(configuration);
    this.inited = true;
  }

  protected abstract void initConfig(EasyMap configuration);

  protected void sync(ClockMessage clockMessage) {
    final List<MessageBus> syncMessageBusList = getSyncMessageBusList();
    if (CollectionUtils.isEmpty(syncMessageBusList)) {
      return;
    }

    syncMessageBusList.forEach(messageBus -> {
      try {
        messageBus.write(clockMessage);
      } catch (MessageBusNotAvailableException e) {
        throw new RuntimeException(String.format(
            "Sync clock message error when using message bus %s", messageBus.name()), e);
      }
    });
  }

  @SuppressWarnings({"unchecked"})
  protected void batchSync(Collection<ClockMessage> clockMessages) {
    final List<MessageBus> syncMessageBusList = getSyncMessageBusList();
    if (CollectionUtils.isEmpty(syncMessageBusList)) {
      return;
    }
    if (CollectionUtils.isEmpty(clockMessages)) {
      return;
    }

    syncMessageBusList.forEach(messageBus -> {
      try {
        messageBus.write((Collection) clockMessages);
      } catch (MessageBusNotAvailableException e) {
        throw new RuntimeException(String.format(
            "Sync clock messages error when using message bus %s", messageBus.name()), e);
      }
    });
  }

  private List<MessageBus> getSyncMessageBusList() {
    return getMessageBusList(this.syncMessageBusNames);
  }

  protected void sendAddReplicationMessage(ClockMessage clockMessage) {
    sendReplicationMessage(clockMessage);
  }

  protected void sendCleanReplicationMessage(long timePoint) {
    sendReplicationMessage(new CleanTimeRangeMessage(timePoint));
  }

  private void sendReplicationMessage(Message message) {
    final List<MessageBus> messageBusList = getReplicationMessageBusList();
    if (CollectionUtils.isEmpty(messageBusList)) {
      return;
    }
    messageBusList.forEach(messageBus -> {
      try {
        messageBus.write(message);
      } catch (MessageBusNotAvailableException e) {
        throw new RuntimeException(String.format(
            "Replicate clock message error when using message bus %s", messageBus.name()), e);
      }
    });
  }

  private List<MessageBus> getReplicationMessageBusList() {
    return getMessageBusList(this.replicationMessageBusNames);
  }

  private List<MessageBus> getMessageBusList(List<String> messageBusNames) {
    if (CollectionUtils.isEmpty(messageBusNames)) {
      return Collections.emptyList();
    }

    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final MessageBusManager messageBusManager = (MessageBusManager) integrationPlan.getTopComponent(
        TopComponentType.MESSAGE_BUS_MANAGER);
    return messageBusNames.stream().map(busName -> {
      final Optional<MessageBus> messageBusOptional = messageBusManager
          .getMessageBusByName(busName);
      return messageBusOptional.orElseThrow(() -> new RuntimeException(
          String.format("Unknown message bus: %s", busName)));
    }).filter(MessageBus::isOpen).collect(Collectors.toList());
  }

  public interface ConfigItems {

    String SYNC_MESSAGE_BUS = "sync_message_bus";

    String REPLICATION_MESSAGE_BUS = "replication_message_bus";
  }
}
