package playwell.baas.api;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import playwell.api.APIRoutes;
import playwell.api.PlaywellAPIServer;

/**
 * PlaywellBaasAPIServer
 */
public class PlaywellBaasAPIServer extends PlaywellAPIServer {

  @Override
  protected Collection<APIRoutes> getAllRoutes() {
    final List<APIRoutes> allRoutes = new LinkedList<>(super.getAllRoutes());
    allRoutes.add(new DomainAPIRoutes());
    return allRoutes;
  }
}
