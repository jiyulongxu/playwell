package playwell.api;

import java.util.Collection;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.http.HttpServiceManager;
import spark.Service;

/**
 * Abstract Base API Server
 */
public abstract class BaseAPIServer {

  private static final Logger logger = LogManager.getLogger(BaseAPIServer.class);

  public void init(EasyMap configuration) {
    final String host = configuration.getString(ConfigItems.HOST);
    final int port = configuration.getInt(ConfigItems.PORT);

    final HttpServiceManager httpServiceManager = HttpServiceManager.getInstance();
    final Optional<Service> serviceOptional = httpServiceManager.getService(host, port);
    if (!serviceOptional.isPresent()) {
      throw new RuntimeException(String.format(
          "Could not found the http service configuration, host = %s, port = %d",
          host,
          port
      ));
    }

    final Service service = serviceOptional.get();
    getAllRoutes().forEach(r -> r.registerRoutes(service));

    service.notFound((request, response) -> {
      response.status(404);
      response.header("Content-Type", "application/json;charset=utf-8");
      return Result.failWithCodeAndMessage(
          "not_found",
          "The API not found"
      ).toJSONString();
    });
    service.exception(Exception.class, (exception, request, response) -> {
      logger.error("api error", exception);
      response.status(500);
      response.header("Content-Type", "application/json;charset=utf-8");
      response.body(Result.failWithCodeAndMessage(
          "error",
          exception.getMessage()
      ).toJSONString());
    });
  }

  protected abstract Collection<APIRoutes> getAllRoutes();

  interface ConfigItems {

    String HOST = "host";

    String PORT = "port";
  }
}
