package playwell.baas.domain;

import java.util.Map;

/**
 * Domain信息插入/更新命令
 */
public class DomainInfoUpsertCommand {

  private final String type;

  private final String domainId;

  private final Map<String, Object> userProperties;

  public DomainInfoUpsertCommand(String type, String domainId, Map<String, Object> userProperties) {
    this.type = type;
    this.domainId = domainId;
    this.userProperties = userProperties;
  }

  public String getType() {
    return this.type;
  }

  public String getDomainId() {
    return domainId;
  }

  public Map<String, Object> getUserProperties() {
    return userProperties;
  }
}
