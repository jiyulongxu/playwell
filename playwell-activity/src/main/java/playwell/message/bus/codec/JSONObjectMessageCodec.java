package playwell.message.bus.codec;

import com.alibaba.fastjson.JSONObject;
import playwell.message.Message;

/**
 * 基于JSON对象的消息编解码器实现
 *
 * @author chihongze@gmail.com
 */
public class JSONObjectMessageCodec extends MapMessageCodec {

  public JSONObjectMessageCodec() {

  }

  @Override
  public Message decode(Object object) {
    return super.decode(JSONObject.parseObject((String) object));
  }

  @Override
  public Object encode(Message message) {
    return JSONObject.toJSONString(super.encode(message));
  }
}
