package playwell.storage.redis;

import java.util.function.Consumer;
import java.util.function.Function;
import redis.clients.jedis.Jedis;

/**
 * RedisHelper
 */
public class RedisHelper {

  private final String resourceName;

  private RedisHelper(String resourceName) {
    this.resourceName = resourceName;
  }

  public static RedisHelper use(String resourceName) {
    return new RedisHelper(resourceName);
  }

  public Jedis jedis() {
    return RedisPoolManager.getInstance().jedis(resourceName);
  }

  public void call(Consumer<Jedis> consumer) {
    RedisPoolManager.getInstance().call(resourceName, consumer);
  }

  public <T> T call(Function<Jedis, T> function) {
    return RedisPoolManager.getInstance().call(resourceName, function);
  }
}
