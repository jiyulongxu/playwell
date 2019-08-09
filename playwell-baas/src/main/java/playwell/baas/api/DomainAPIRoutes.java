package playwell.baas.api;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import playwell.api.APIRoutes;
import playwell.baas.domain.DomainInfoScanConfiguration;
import playwell.baas.domain.DomainService;
import playwell.common.Result;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.ServiceRunnerIntegrationPlan;
import playwell.service.LocalServiceMeta;
import playwell.service.ServiceMeta;
import playwell.service.ServiceMetaManager;
import playwell.util.TextUtils;
import playwell.util.validate.Field;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * DomainAPIRoutes
 */
public class DomainAPIRoutes extends APIRoutes {

  @Override
  protected void registerRoutes(Service service) {
    service.path("/v1/domain", () -> {
      service.get("/", this::query);
      service.post("/", this::upsert);
      service.post("/scan", this::scan);
      service.post("/scan/stop", this::stopScan);
      service.get("/scan/all", this::getAllScanMarks);
    });
  }

  private String query(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        new Field[]{},
        args -> {
          final String serviceName = args.getString("service");
          final String type = args.getString("type");
          final String domainId = args.getString("id");
          final String propertiesText = args.getString("properties", "");
          final List<String> properties;
          if (StringUtils.isEmpty(propertiesText)) {
            properties = Collections.emptyList();
          } else {
            properties = TextUtils.splitWithoutEmpty(propertiesText, ',');
          }
          final DomainService domainService = getServiceObj(serviceName);
          return domainService.query(type, domainId, properties);
        }
    );
  }

  private String upsert(Request request, Response response) {
    return postResponse(
        request,
        response,
        new Field[]{},
        args -> {
          final String serviceName = args.getString("service");
          final String type = args.getString("type");
          final String domainId = args.getString("id");
          final Map<String, Object> properties = args.getSubArguments("properties").toMap();
          final DomainService domainService = getServiceObj(serviceName);
          return domainService.upsert(type, domainId, properties);
        }
    );
  }

  private String scan(Request request, Response response) {
    return postResponse(
        request,
        response,
        new Field[]{},
        args -> {
          String serviceName = args.getString("service");
          String type = args.getString("type");
          DomainInfoScanConfiguration scanConfiguration = DomainInfoScanConfiguration
              .buildFromConfiguration(args);
          DomainService domainService = getServiceObj(serviceName);
          return domainService.scanAll(type, scanConfiguration);
        }
    );
  }

  private String stopScan(Request request, Response response) {
    return postResponse(
        request,
        response,
        new Field[]{},
        args -> {
          String serviceName = args.getString("service");
          String mark = args.getString("mark");
          DomainService domainService = getServiceObj(serviceName);
          domainService.stopScan(mark);
          return Result.ok();
        }
    );
  }

  private String getAllScanMarks(Request request, Response response) {
    return getResponseWithQueryParam(
        request,
        response,
        new Field[]{},
        args -> {
          String serviceName = args.getString("service");
          DomainService domainService = getServiceObj(serviceName);
          Collection<String> allMarks = domainService.getScanningMarks();
          return Result.okWithData(Collections.singletonMap("marks", allMarks));
        }
    );
  }

  private DomainService getServiceObj(String serviceName) {
    ServiceRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    ServiceMetaManager serviceMetaManager = integrationPlan.getServiceMetaManager();
    Optional<ServiceMeta> serviceMetaOptional = serviceMetaManager
        .getServiceMetaByName(serviceName);
    if (!serviceMetaOptional.isPresent()) {
      throw new RuntimeException(String.format("Unknown service %s", serviceName));
    }
    LocalServiceMeta localServiceMeta = (LocalServiceMeta) serviceMetaOptional.get();
    return (DomainService) localServiceMeta.getPlaywellService();
  }
}
