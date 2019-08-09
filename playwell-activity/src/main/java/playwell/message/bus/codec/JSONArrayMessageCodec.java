package playwell.message.bus.codec;

import com.alibaba.fastjson.JSONArray;
import java.util.Collection;
import playwell.message.Message;

/**
 * 基于JSON数组的序列编解码器实现
 */
public class JSONArrayMessageCodec extends SequenceMapMessageCodec {

  public JSONArrayMessageCodec() {

  }

  @Override
  public Collection<Message> decode(Object object) {
    return super.decode(JSONArray.parseArray((String) object));
  }

  @Override
  public Object encode(Collection<Message> messages) {
    return JSONArray.toJSONString(super.encode(messages));
  }
}
