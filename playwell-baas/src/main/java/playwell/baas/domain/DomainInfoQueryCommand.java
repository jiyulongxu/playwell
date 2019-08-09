package playwell.baas.domain;

import java.util.List;

/**
 * Domain信息查询命令
 */
public class DomainInfoQueryCommand {

  private final String type;

  private final String domainId;

  private final List<String> properties;

  public DomainInfoQueryCommand(String type, String domainId, List<String> properties) {
    this.type = type;
    this.domainId = domainId;
    this.properties = properties;
  }

  public String getType() {
    return type;
  }

  public String getDomainId() {
    return domainId;
  }

  public List<String> getProperties() {
    return properties;
  }
}
