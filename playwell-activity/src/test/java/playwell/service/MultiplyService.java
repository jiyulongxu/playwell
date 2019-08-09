package playwell.service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import playwell.clock.CachedTimestamp;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.message.ServiceRequestMessage;
import playwell.message.ServiceResponseMessage;

public class MultiplyService implements PlaywellService {

  @Override
  public void init(Object config) {

  }

  @Override
  @SuppressWarnings({"unchecked"})
  public Collection<ServiceResponseMessage> handle(Collection<ServiceRequestMessage> messages) {
    return messages.stream().map(req -> {
      final EasyMap args = new EasyMap((Map<String, Object>) req.getArgs());
      final int a = args.getInt("a");
      final int b = args.getInt("b");
      return new ServiceResponseMessage(
          CachedTimestamp.nowMilliseconds(),
          req,
          Result.okWithData(Collections.singletonMap("result", a * b))
      );
    }).collect(Collectors.toList());
  }
}
