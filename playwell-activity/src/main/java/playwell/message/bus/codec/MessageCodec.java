package playwell.message.bus.codec;

import playwell.message.Message;

/**
 * 消息编解码器抽象
 *
 * @author chihongze@gmail.com
 */
public interface MessageCodec {

  Message decode(Object object);

  Object encode(Message message);
}
