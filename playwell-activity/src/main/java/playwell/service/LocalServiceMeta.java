package playwell.service;

import java.util.Map;

/**
 * LocalServiceMeta用于承载JVM本地服务的相关信息
 *
 * @author chihongze@gmail.com
 */
public class LocalServiceMeta extends ServiceMeta {

  private final PlaywellService playwellService;

  public LocalServiceMeta(
      String name, String messageBus, Map<String, Object> config, PlaywellService playwellService) {
    super(name, messageBus, config);
    this.playwellService = playwellService;
  }

  public PlaywellService getPlaywellService() {
    return playwellService;
  }
}
