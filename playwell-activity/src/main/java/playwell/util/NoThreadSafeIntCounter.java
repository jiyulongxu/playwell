package playwell.util;

/**
 * 非线程安全的计数器，用在同步执行的回调函数中
 */
public class NoThreadSafeIntCounter {

  private int count;

  public NoThreadSafeIntCounter() {
    this(0);
  }

  public NoThreadSafeIntCounter(int count) {
    this.count = count;
  }

  public int get() {
    return this.count;
  }

  public void set(int count) {
    this.count = count;
  }

  public int incrementAndGet() {
    return ++count;
  }

  public int getAndIncrement() {
    return count++;
  }
}
