package playwell.message;


import java.util.HashMap;

/**
 * 经过路由，已经计算出DomainID的消息
 */
public class RoutedMessage extends Message implements DomainMessage {

  public static final String DOMAIN_ID = "$DID";

  public static final String STRATEGY = "$DID_STRATEGY";

  public RoutedMessage(String strategy, String domainId, Message message) {
    super(
        message.getType(),
        message.getSender(),
        message.getReceiver(),
        new HashMap<>(message.getAttributes().size() + 2),
        message.getTimestamp()
    );

    this.getAttributes().put(STRATEGY, strategy);
    this.getAttributes().put(DOMAIN_ID, domainId);
    this.getAttributes().putAll(message.getAttributes());
  }

  @Override
  public String getDomainId() {
    return (String) getAttributes().get(DOMAIN_ID);
  }

  public String getDomainIDStrategy() {
    return (String) getAttributes().get(STRATEGY);
  }
}
