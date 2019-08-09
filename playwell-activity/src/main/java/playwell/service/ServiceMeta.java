package playwell.service;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Objects;
import playwell.common.Mappable;
import playwell.util.ModelUtils;

/**
 * 服务相关元信息
 *
 * @author chihongze@gmail.com
 */
public class ServiceMeta implements Mappable {

  // 服务名称
  private final String name;

  // 服务通讯所使用的消息总线
  private final String messageBus;

  // 其它配置参数，可供相关事件总线做参考
  private final Map<String, Object> config;

  public ServiceMeta(String name, String messageBus, Map<String, Object> config) {
    this.name = name;
    this.messageBus = messageBus;
    this.config = config;
  }

  public String getName() {
    return name;
  }

  public String getMessageBus() {
    return messageBus;
  }

  public Map<String, Object> getConfig() {
    return config;
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.of(
        Fields.NAME, name,
        Fields.MESSAGE_BUS, messageBus,
        Fields.CONFIG, ModelUtils.expandMappable(config)
    );
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ServiceMeta)) {
      return false;
    }
    ServiceMeta that = (ServiceMeta) o;
    return Objects.equals(getName(), that.getName());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName());
  }

  @Override
  public String toString() {
    final String data = JSON.toJSONString(toMap());
    return String.format("ServiceMeta@%d%s", System.identityHashCode(this), data);
  }

  public interface Fields {

    String NAME = "name";

    String MESSAGE_BUS = "message_bus";

    String CONFIG = "config";

  }
}
