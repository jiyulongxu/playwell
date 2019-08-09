package playwell.message.bus;

import java.util.Map;

/**
 * MessageBus元信息，通常用于持久化存储
 *
 * @author chihongze@gmail.com
 */
public class MessageBusMeta {

  private final String clazz;

  private final String name;

  private final Map<String, Object> config;

  private final boolean open;

  public MessageBusMeta(String clazz, String name,
      Map<String, Object> config, boolean open) {
    this.clazz = clazz;
    this.name = name;
    this.config = config;
    this.open = open;
  }

  public String getClazz() {
    return clazz;
  }

  public String getName() {
    return name;
  }

  public Map<String, Object> getConfig() {
    return config;
  }

  public boolean isOpen() {
    return open;
  }

  @Override
  public String toString() {
    return "MessageBusMeta{" +
        "clazz='" + clazz + '\'' +
        ", name='" + name + '\'' +
        ", config=" + config +
        ", open=" + open +
        '}';
  }
}
