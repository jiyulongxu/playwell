package playwell.message.bus;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.integration.IntergrationUtils;

/**
 * 基于内存进行存储的MessageBusManager
 *
 * @author chihongze@gmail.com
 */
public class MemoryMessageBusManager extends BaseMessageBusManager {

  private static final Logger logger = LogManager.getLogger(MemoryMessageBusManager.class);

  private final Map<String, MessageBus> allMessageBus = new ConcurrentHashMap<>();

  public MemoryMessageBusManager() {

  }

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    IntergrationUtils.loadAndInitSubComponents(
        configuration.getObjectList(ConfigItems.MESSAGE_BUS)).forEach(messageBusObj -> {
      final MessageBus messageBus = (MessageBus) messageBusObj;
      allMessageBus.put(messageBus.name(), messageBus);
    });
  }

  @Override
  public Result openMessageBus(String name) {
    final Optional<MessageBus> messageBusOptional = getMessageBusByName(name);
    if (!messageBusOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND,
          String.format("The message bus '%s' not found", name)
      );
    }

    final MessageBus messageBus = messageBusOptional.get();
    messageBus.open();
    return Result.ok();
  }

  @Override
  public Result closeMessageBus(String name) {
    final Optional<MessageBus> messageBusOptional = getMessageBusByName(name);
    if (!messageBusOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND,
          String.format("The message bus '%s' not found", name)
      );
    }

    final MessageBus messageBus = messageBusOptional.get();
    messageBus.close();
    return Result.ok();
  }

  @Override
  public Result registerMessageBus(String clazz, Map<String, Object> config) {
    MessageBus messageBus;
    try {
      Class messageBusClass = Class.forName(clazz);
      messageBus = (MessageBus) messageBusClass.newInstance();
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      return Result.failWithCodeAndMessage(ErrorCodes.LOAD_ERROR, e.getMessage());
    }

    messageBus.init(new EasyMap(config));

    allMessageBus.put(messageBus.name(), messageBus);
    return Result.ok();
  }

  @Override
  public Collection<MessageBus> getAllMessageBus() {
    return allMessageBus.values();
  }

  @Override
  public Optional<MessageBus> getMessageBusByName(String name) {
    return Optional.ofNullable(allMessageBus.get(name));
  }

  @Override
  public Result deleteMessageBus(String name) {
    MessageBus messageBus = allMessageBus.remove(name);
    if (messageBus != null) {
      return Result.ok();
    } else {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND, String.format("The message bus '%s' not found", name));
    }
  }

  interface ConfigItems {

    String MESSAGE_BUS = "message_bus";
  }
}
