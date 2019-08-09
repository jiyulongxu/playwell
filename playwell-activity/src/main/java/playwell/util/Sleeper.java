package playwell.util;

import java.util.concurrent.TimeUnit;

/**
 * Simple sleep
 */
public final class Sleeper {

  private Sleeper() {

  }

  public static void sleepInSeconds(int seconds) {
    sleep(TimeUnit.SECONDS.toMillis(seconds));
  }

  public static void sleep(long milliSeconds) {
    try {
      Thread.sleep(milliSeconds);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
