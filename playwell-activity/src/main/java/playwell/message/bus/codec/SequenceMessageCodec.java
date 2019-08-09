package playwell.message.bus.codec;

import java.util.Collection;
import playwell.message.Message;

/**
 * 消息序列编解码器抽象
 *
 * @author chihongze@gmail.com
 */
public interface SequenceMessageCodec {

  Collection<Message> decode(Object object);

  Object encode(Collection<Message> messages);
}
