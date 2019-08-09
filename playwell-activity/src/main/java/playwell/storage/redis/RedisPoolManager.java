package playwell.storage.redis;

import java.io.Closeable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.util.Pool;

/**
 * Redis连接池管理
 */
public class RedisPoolManager implements Closeable {

  private static final Logger logger = LogManager.getLogger(RedisPoolManager.class);

  private static final RedisPoolManager INSTANCE = new RedisPoolManager();

  // 管理的连接池
  private final Map<String, Pool<Jedis>> allJedisPool = new HashMap<>();

  // 是否已经初始化过
  private volatile boolean inited = false;

  private RedisPoolManager() {

  }

  public static RedisPoolManager getInstance() {
    return INSTANCE;
  }

  /**
   * 对所有连接池按照配置进行初始化
   *
   * @param configuration 配置信息
   */
  public synchronized void init(EasyMap configuration) {
    if (inited) {
      logger.warn("The RedisPoolManager has already been inited!");
      return;
    }

    final List<EasyMap> poolConfigList = configuration.getSubArgumentsList(ConfigItems.POOLS);
    poolConfigList.forEach(config -> {

      final String name = config.getString(CommonPoolConfigItems.NAME);

      final JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
      jedisPoolConfig.setMaxTotal(config.getInt(CommonPoolConfigItems.MAX_TOTAL));
      jedisPoolConfig.setMaxIdle(config.getInt(CommonPoolConfigItems.MAX_IDLE));
      jedisPoolConfig.setMaxWaitMillis(config.getLong(CommonPoolConfigItems.MAX_WAIT));
      jedisPoolConfig.setTestOnCreate(config.getBoolean(CommonPoolConfigItems.TEST_ON_CREATE));
      jedisPoolConfig.setTestOnBorrow(config.getBoolean(CommonPoolConfigItems.TEST_ON_BORROW));
      jedisPoolConfig.setTestOnReturn(config.getBoolean(CommonPoolConfigItems.TEST_ON_RETURN));

      final String type = config.getString(ConfigItems.TYPE);
      final Pool<Jedis> pool;
      if (PoolTypes.COMMON.equals(type)) {
        pool = new JedisPool(
            jedisPoolConfig,
            config.getString(CommonPoolConfigItems.HOST),
            config.getInt(CommonPoolConfigItems.PORT),
            config.getInt(CommonPoolConfigItems.TIMEOUT),
            config.getString(CommonPoolConfigItems.PASSWORD, null),
            config.getInt(CommonPoolConfigItems.DATABASE, 0),
            config.getString(CommonPoolConfigItems.CLIENT_NAME, "playwell")
        );
      } else if (PoolTypes.SENTINEL.equals(type)) {
        pool = new JedisSentinelPool(
            config.getString(SentinelPoolConfigItems.MASTER_NAME),
            new HashSet<>(config.getStringList(SentinelPoolConfigItems.SENTINELS)),
            jedisPoolConfig,
            config.getInt(CommonPoolConfigItems.TIMEOUT),
            config.getString(CommonPoolConfigItems.PASSWORD),
            config.getInt(CommonPoolConfigItems.DATABASE, 0),
            config.getString(CommonPoolConfigItems.CLIENT_NAME, "playwell")
        );
      } else {
        throw new RuntimeException(String.format("Unknown redis resource type: %s", type));
      }

      allJedisPool.put(name, pool);
    });

    inited = true;
  }

  /**
   * 获取Jedis实例
   *
   * @param resourceName 配置的Redis资源名称
   * @return Jedis实例
   */
  public Jedis jedis(String resourceName) {
    checkInited();
    final Pool<Jedis> pool = allJedisPool.get(resourceName);
    if (pool == null) {
      throw new RuntimeException(String.format(
          "Could not found the redis resource: %s", resourceName));
    }
    return pool.getResource();
  }

  /**
   * 获取并基于consumer来回调Jedis实例，无需用户手工处理Jedis对象归还等问题
   *
   * @param resourceName 配置的Redis资源名称
   * @param consumer Jedis Consumer
   */
  public void call(String resourceName, Consumer<Jedis> consumer) {
    try (Jedis jedis = jedis(resourceName)) {
      consumer.accept(jedis);
    }
  }

  /**
   * 获取并基于function来回调Jedis实例，并返回回调结果，无需用户手工处理Jedis对象归还等问题
   *
   * @param resourceName 配置Redis资源名称
   * @param function Jedis Function
   * @param <T> Return type
   * @return function返回值
   */
  public <T> T call(String resourceName, Function<Jedis, T> function) {
    try (Jedis jedis = jedis(resourceName)) {
      return function.apply(jedis);
    }
  }

  private void checkInited() {
    if (!inited) {
      throw new RuntimeException("The RedisPoolManager has not been inited!");
    }
  }

  @Override
  public void close() {
    allJedisPool.values().forEach(Pool::close);
  }

  interface PoolTypes {

    String COMMON = "common";

    String SENTINEL = "sentinel";
  }

  interface ConfigItems {

    String POOLS = "pools";

    String TYPE = "type";
  }

  interface CommonPoolConfigItems {

    String NAME = "name";

    String HOST = "host";

    String PORT = "port";

    String TIMEOUT = "timeout";

    String PASSWORD = "password";

    String DATABASE = "database";

    String CLIENT_NAME = "client_name";

    String MAX_TOTAL = "max_total";

    String MAX_IDLE = "max_idle";

    String MAX_WAIT = "max_wait";

    String TEST_ON_CREATE = "test_on_create";

    String TEST_ON_BORROW = "test_on_borrow";

    String TEST_ON_RETURN = "test_on_return";
  }

  interface SentinelPoolConfigItems {

    String MASTER_NAME = "master_name";

    String SENTINELS = "sentinels";
  }
}
