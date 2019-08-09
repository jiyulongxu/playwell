package playwell.service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import playwell.clock.CachedTimestamp;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.message.ServiceRequestMessage;
import playwell.message.ServiceResponseMessage;
import playwell.util.Sleeper;


/**
 * Add service
 */
public class AddService implements PlaywellService {

  public static AddService SELF = null;

  private final ConcurrentMap<Pair<Integer, Integer>, Integer> cache = new ConcurrentHashMap<>();

  @Override
  public void init(Object config) {
    SELF = this;
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public Collection<ServiceResponseMessage> handle(Collection<ServiceRequestMessage> messages) {
    return messages.stream().filter(msg -> {
      if (msg.isIgnoreResult()) {
        final EasyMap args = new EasyMap((Map<String, Object>) msg.getArgs());
        final int seconds = args.getInt("sleep", 0);
        Sleeper.sleepInSeconds(seconds);
        final int a = args.getInt("a");
        final int b = args.getInt("b");
        cache.put(Pair.of(a, b), a + b);
        return false;
      }
      return true;
    }).map(req -> {
      final EasyMap args = new EasyMap((Map<String, Object>) req.getArgs());
      final int a = args.getInt("a");
      final int b = args.getInt("b");
      return new ServiceResponseMessage(
          CachedTimestamp.nowMilliseconds(),
          req,
          Result.okWithData(Collections.singletonMap("result", a + b))
      );
    }).collect(Collectors.toList());
  }

  public Optional<Integer> get(int a, int b) {
    return Optional.ofNullable(cache.get(Pair.of(a, b)));
  }

  public void clear() {
    cache.clear();
  }
}
