package playwell.message.bus;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import playwell.common.EasyMap;
import playwell.common.Mappable;

/**
 * Base MessageBus
 *
 * @author chihongze@gmail.com
 */
public abstract class BaseMessageBus implements MessageBus, Mappable {

  private String name;

  // MessageBus是否处于打开状态
  private volatile boolean opened = true;

  private volatile boolean alive = false;

  // 配置信息
  private Map<String, Object> config = Collections.emptyMap();

  protected BaseMessageBus() {

  }

  @Override
  public void init(Object config) {
    EasyMap configuration = (EasyMap) config;
    this.config = configuration.toMap();
    this.name = configuration.getString(ConfigItems.NAME);
    long checkPeriod = configuration.getLong(ConfigItems.CHECK_PERIOD, 0L);
    if (checkPeriod != 0) {
      final ScheduledThreadPoolExecutor scheduler = (ScheduledThreadPoolExecutor) Executors
          .newSingleThreadScheduledExecutor();
      scheduler.scheduleAtFixedRate(
          () -> {
            if (this.opened) {
              this.alive = checkAlive();
            }
          },
          0L, checkPeriod,
          TimeUnit.MILLISECONDS
      );
      Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));
    } else {
      this.alive = true;
    }
    initMessageBus(configuration);
  }

  protected abstract void initMessageBus(EasyMap configuration);

  @Override
  public String name() {
    return name;
  }

  @Override
  public void ackMessages() {

  }

  protected boolean isAvailable() {
    if (!this.opened) {
      return false;
    }

    return this.alive;
  }

  protected boolean checkAlive() {
    return true;
  }

  @Override
  public boolean isOpen() {
    return this.opened;
  }

  @Override
  public void open() {
    this.opened = true;
  }

  @Override
  public void close() {
    this.opened = false;
  }

  protected void checkAvailable() throws MessageBusNotAvailableException {
    if (!isAvailable()) {
      throw new MessageBusNotAvailableException(name());
    }
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.<String, Object>builder()
        .put(Fields.NAME, this.name)
        .put(Fields.CLASS, this.getClass().getCanonicalName())
        .put(Fields.OPENED, this.opened)
        .put(Fields.ALIVE, this.alive)
        .put(Fields.AVAILABLE, this.isAvailable())
        .put(Fields.CONFIG, this.config)
        .put(Fields.STATUS, getStatus())
        .build();
  }

  protected Map<String, Object> getStatus() {
    return Collections.emptyMap();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BaseMessageBus)) {
      return false;
    }
    BaseMessageBus that = (BaseMessageBus) o;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public String toString() {
    return String.format(
        "MessageBus@%d%s",
        System.identityHashCode(this),
        new JSONObject(toMap()).toJSONString()
    );
  }

  // 配置项
  interface ConfigItems {

    String NAME = "name";

    String CHECK_PERIOD = "check_period";
  }

  interface Fields {

    String NAME = "name";

    String CLASS = "class";

    String OPENED = "opened";

    String ALIVE = "alive";

    String AVAILABLE = "available";

    String CONFIG = "config";

    String STATUS = "status";
  }
}
