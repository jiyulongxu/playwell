package playwell.baas.domain;

import java.util.Map;

/**
 * Domain Model Info
 */
public class DomainInfo {

  private final String domainId;

  private final Map<String, Object> properties;

  public DomainInfo(String userId, Map<String, Object> properties) {
    this.domainId = userId;
    this.properties = properties;
  }

  public String getDomainId() {
    return domainId;
  }

  public Map<String, Object> getProperties() {
    return properties;
  }
}
