package playwell.clock;

import java.io.Closeable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import playwell.common.EasyMap;

/**
 * CachedTimestamp用于优化时间戳的获取 系统会缓存当前时间戳，并且每秒刷新，以避免每次系统调用所带来的开销
 *
 * @author chihongze@gmail.com
 */
public class CachedTimestamp implements Closeable {

  private static final CachedTimestamp INSTANCE = new CachedTimestamp();

  private final AtomicLong cachedTimestamp = new AtomicLong(System.currentTimeMillis());

  private ScheduledExecutorService scheduler;

  private volatile boolean inited = false;

  private CachedTimestamp() {
  }

  public static CachedTimestamp getInstance() {
    return INSTANCE;
  }

  public static long nowSeconds() {
    return TimeUnit.MILLISECONDS.toSeconds(INSTANCE.getTimtstamp());
  }

  public static long nowMilliseconds() {
    return INSTANCE.getTimtstamp();
  }

  public synchronized void init(EasyMap configuration) {
    if (inited) {
      return;
    }
    this.scheduler = Executors.newSingleThreadScheduledExecutor();
    final long period = configuration.getLong(ConfigItems.PERIOD);
    scheduler.scheduleAtFixedRate(
        () -> cachedTimestamp.set(System.currentTimeMillis()),
        0,
        period,
        TimeUnit.MILLISECONDS
    );
    inited = true;
  }

  public long getTimtstamp() {
    return cachedTimestamp.get();
  }

  @Override
  public void close() {
    if (scheduler != null) {
      scheduler.shutdown();
    }
  }

  interface ConfigItems {

    String PERIOD = "period";
  }
}
