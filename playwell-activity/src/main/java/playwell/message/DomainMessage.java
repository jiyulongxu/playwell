package playwell.message;

/**
 * DomainMessage接口用来描述已经计算出DomainID的消息
 *
 * @author chihongze@gmail.com
 */
public interface DomainMessage {

  String getDomainId();
}
