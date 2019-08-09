package playwell.message.bus;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;

/**
 * BaseMessageBusManager
 */
public abstract class BaseMessageBusManager implements MessageBusManager {

  private static final Logger logger = LogManager.getLogger(BaseMessageBusManager.class);

  @Override
  public MessageBus newMessageBus(String clazz, Map<String, Object> config) {
    final MessageBus messageBus;
    try {
      final Class messageBusClass = Class.forName(clazz);
      messageBus = (MessageBus) messageBusClass.newInstance();
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      throw new RuntimeException(e);
    }
    messageBus.init(new EasyMap(config));
    return messageBus;
  }
}
