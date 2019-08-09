package playwell.message.domainid;

import java.util.Optional;
import playwell.common.PlaywellComponent;
import playwell.message.Message;

/**
 * 消息DomainID提取策略，用于动态的从消息属性当中提取DomainID
 *
 * @author chihongze@gmail.com
 */
public interface MessageDomainIDStrategy extends PlaywellComponent {

  String name();

  Optional<String> domainId(Message message);
}
