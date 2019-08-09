package playwell.message.bus.codec;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import playwell.message.Message;

/**
 * 基于List[Map]结构的序列MessageCodec
 */
public class SequenceMapMessageCodec implements SequenceMessageCodec {

  private final MapMessageCodec mapMessageCodec = new MapMessageCodec();

  public SequenceMapMessageCodec() {

  }

  @Override
  @SuppressWarnings({"unchecked"})
  public Collection<Message> decode(Object object) {
    final List<Object> list = (List<Object>) object;
    return list.stream().map(mapMessageCodec::decode).collect(Collectors.toList());
  }

  @Override
  public Object encode(Collection<Message> messages) {
    if (CollectionUtils.isEmpty(messages)) {
      return Collections.emptyList();
    }
    return messages.stream().map(mapMessageCodec::encode).collect(Collectors.toList());
  }
}
