package playwell.http;

import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import playwell.common.EasyMap;
import playwell.common.Result;
import spark.Service;

/**
 * Http服务管理器，用于HttpEventBus
 *
 * @author chihongze
 */
public class HttpServiceManager implements Closeable {

  private static final HttpServiceManager INSTANCE = new HttpServiceManager();

  private final Map<Pair<String, Integer>, Service> allServices = new HashMap<>();

  private volatile boolean inited = false;

  private HttpServiceManager() {

  }

  public static HttpServiceManager getInstance() {
    return INSTANCE;
  }

  public synchronized void init(EasyMap configuration) {
    if (isInited()) {
      return;
    }
    final List<EasyMap> serviceConfigs = configuration.getSubArgumentsList(ConfigItems.SERVICES);
    serviceConfigs.forEach(serviceConfig -> {
      final String host = serviceConfig.getString(ConfigItems.HOST);
      final int port = serviceConfig.getInt(ConfigItems.PORT);
      final int minThreads = serviceConfig.getInt(ConfigItems.MIN_THREADS);
      final int maxThreads = serviceConfig.getInt(ConfigItems.MAX_THREADS, minThreads);
      final int idleTime = serviceConfig.getInt(ConfigItems.IDLE_TIME, 60000);

      final Service service = Service.ignite()
          .ipAddress(host)
          .port(port)
          .threadPool(maxThreads, minThreads, idleTime);

      service.get("/_about", (req, res) -> {
        res.status(200);
        res.header(CommonHeaders.JSON_CONTENT_TYPE.getName(),
            CommonHeaders.JSON_CONTENT_TYPE.getValue());
        return Result.okWithMsg("Playwell HTTP Service").toJSONString();
      });

      allServices.put(Pair.of(host, port), service);
    });

    inited = true;
  }

  public boolean isInited() {
    return inited;
  }

  public Optional<Service> getService(String host, int port) {
    if (!isInited()) {
      throw new RuntimeException("The HttpServiceManager has not been inited!");
    }
    return Optional.ofNullable(allServices.get(Pair.of(host, port)));
  }

  @Override
  public void close() {
    allServices.values().forEach(Service::stop);
  }

  interface ConfigItems {

    String SERVICES = "services";

    String HOST = "host";

    String PORT = "port";

    String MIN_THREADS = "min_threads";

    String MAX_THREADS = "max_threads";

    String IDLE_TIME = "idle_time";
  }
}
