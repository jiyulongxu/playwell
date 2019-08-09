package playwell.activity.thread;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import playwell.activity.thread.message.MigrateActivityThreadMessage;
import playwell.activity.thread.message.RemoveActivityThreadMessage;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.message.Message;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;

/**
 * BaseActivityThreadPool
 */
public abstract class BaseActivityThreadPool implements ActivityThreadPool {

  // 当ActivityThread处于以下状态的时候，将会被从RocksDB中删除
  protected static final EnumSet<ActivityThreadStatus> REMOVE_STATUS = EnumSet.of(
      ActivityThreadStatus.FAIL,
      ActivityThreadStatus.FINISHED,
      ActivityThreadStatus.KILLED
  );

  private final Object replicationMessageBusOptLock = new Object();

  protected volatile boolean inited = false;

  protected List<String> syncMessageBusNames;

  protected List<String> replicationMessageBusNames;

  @Override
  public synchronized void init(Object config) {
    if (this.inited) {
      return;
    }
    final EasyMap configuration = (EasyMap) config;
    this.syncMessageBusNames = new CopyOnWriteArrayList<>(configuration.getStringList(
        ConfigItems.SYNC_MESSAGE_BUS));
    this.replicationMessageBusNames = new CopyOnWriteArrayList<>(configuration.getStringList(
        ConfigItems.REPLICATION_MESSAGE_BUS));
    initConfig(configuration);
    this.inited = true;
  }

  @Override
  public Result addReplicationMessageBus(String replicationMessageBus) {
    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final MessageBusManager messageBusManager = (MessageBusManager) integrationPlan
        .getTopComponent(TopComponentType.MESSAGE_BUS_MANAGER);
    final Optional<MessageBus> messageBusOptional = messageBusManager
        .getMessageBusByName(replicationMessageBus);
    if (!messageBusOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.REPLICATION_MESSAGE_BUS_NOT_FOUND,
          String.format("Could not found the replication message bus: %s", replicationMessageBus)
      );
    }

    synchronized (replicationMessageBusOptLock) {
      final boolean existed = replicationMessageBusNames
          .stream().anyMatch(replicationMessageBus::equals);
      if (existed) {
        return Result.failWithCodeAndMessage(
            ErrorCodes.REPLICATION_BUS_ALREADY_EXISTED,
            String.format("The replication message bus %s already existed", replicationMessageBus)
        );
      }

      this.replicationMessageBusNames.add(replicationMessageBus);
    }

    return Result.ok();
  }

  @Override
  public Result removeReplicationMessageBus(String replicationMessageBus) {
    synchronized (replicationMessageBusOptLock) {
      final boolean removed = replicationMessageBusNames.remove(replicationMessageBus);
      if (!removed) {
        return Result.failWithCodeAndMessage(
            ErrorCodes.REPLICATION_MESSAGE_BUS_NOT_FOUND,
            String.format("The replication message bus %s is not exist", replicationMessageBus)
        );
      }
    }
    return Result.ok();
  }

  @Override
  public Collection<MessageBus> getAllReplicationMessageBuses() {
    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final MessageBusManager messageBusManager = (MessageBusManager) integrationPlan
        .getTopComponent(TopComponentType.MESSAGE_BUS_MANAGER);
    return this.replicationMessageBusNames.stream().map(busName -> {
      final Optional<MessageBus> messageBusOptional = messageBusManager
          .getMessageBusByName(busName);
      if (!messageBusOptional.isPresent()) {
        throw new RuntimeException(String.format("Could not found the message bus: %s", busName));
      }
      return messageBusOptional.get();
    }).collect(Collectors.toList());
  }

  protected abstract void initConfig(EasyMap configuration);

  protected void sync(ActivityThread activityThread) {
    final List<MessageBus> syncMessageBusList = getSyncMessageBusList();
    if (CollectionUtils.isEmpty(syncMessageBusList)) {
      return;
    }
    final MigrateActivityThreadMessage message = toMigrateMessage(activityThread);
    syncMessageBusList.forEach(messageBus -> {
      try {
        messageBus.write(message);
      } catch (MessageBusNotAvailableException e) {
        throw new RuntimeException(String.format(
            "Sync activity thread error when using message bus %s", messageBus.name()), e);
      }
    });
  }

  private List<MessageBus> getSyncMessageBusList() {
    return getMessageBusList(syncMessageBusNames);
  }

  protected void doReplication(ActivityThread activityThread) {
    if (REMOVE_STATUS.contains(activityThread.getStatus())) {
      sendRemoveReplicationMessage(activityThread);
    } else {
      sendUpsertReplicationMessage(activityThread);
    }
  }

  protected void sendUpsertReplicationMessage(ActivityThread activityThread) {
    this.sendReplicationMessage(activityThread, this::toMigrateMessage);
  }

  protected void sendRemoveReplicationMessage(ActivityThread activityThread) {
    this.sendReplicationMessage(activityThread, this::toRemoveMessage);
  }

  private void sendReplicationMessage(
      ActivityThread activityThread, Function<ActivityThread, Message> messageMapper) {
    final List<MessageBus> replicationMessageBusList = getReplicationMessageBusList();
    if (CollectionUtils.isEmpty(replicationMessageBusList)) {
      return;
    }

    final Message message = messageMapper.apply(activityThread);

    replicationMessageBusList.forEach(messageBus -> {
      try {
        messageBus.write(message);
      } catch (MessageBusNotAvailableException e) {
        throw new RuntimeException(String.format(
            "Replica activity threads error when using message bus: %s", messageBus.name()), e);
      }
    });
  }

  private List<MessageBus> getReplicationMessageBusList() {
    return getMessageBusList(replicationMessageBusNames);
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

  private MigrateActivityThreadMessage toMigrateMessage(ActivityThread activityThread) {
    return new MigrateActivityThreadMessage(activityThread);
  }

  private RemoveActivityThreadMessage toRemoveMessage(ActivityThread activityThread) {
    return new RemoveActivityThreadMessage(activityThread);
  }

  interface ConfigItems {

    String SYNC_MESSAGE_BUS = "sync_message_bus";

    String REPLICATION_MESSAGE_BUS = "replication_message_bus";
  }

  interface ErrorCodes {

    String REPLICATION_MESSAGE_BUS_NOT_FOUND = "message_bus_not_found";

    String REPLICATION_BUS_ALREADY_EXISTED = "replication_bus_already_existed";
  }
}
