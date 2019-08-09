package playwell.clock;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * MemoryClockLogic的测试用例
 *
 * @author chihongze@gmail.com
 */
public class MemoryClockTestCase {

  private MemoryClock memoryClockLogic;

  @Before
  public void setUp() {
    memoryClockLogic = new MemoryClock();
  }

  @Test
  public void testFecthClockMessages() throws Exception {
    long now = System.currentTimeMillis();

    // 注册3秒之后的消息
    memoryClockLogic.registerClockMessage(new ClockMessage(
        "",
        "",
        now + TimeUnit.SECONDS.toMillis(3L),
        1,
        "test",
        "test",
        CachedTimestamp.nowMilliseconds()
    ));
    // 注册5秒之后的消息
    memoryClockLogic.registerClockMessage(new ClockMessage(
        "",
        "",
        now + TimeUnit.SECONDS.toMillis(6L),
        1,
        "test2",
        "test",
        CachedTimestamp.nowMilliseconds()
    ));

    // 过1s之后，神马都fetch不出来
    TimeUnit.SECONDS.sleep(1L);
    Collection<ClockMessage> messages = memoryClockLogic
        .fetchClockMessages(System.currentTimeMillis());
    Assert.assertEquals(0, messages.size());

    // 再过2.5s，应该可以fetch出一个来
    TimeUnit.MILLISECONDS.sleep(2500L);
    now = System.currentTimeMillis();
    messages = memoryClockLogic.fetchClockMessages(now);
    Assert.assertEquals(1L, messages.size());
    for (ClockMessage message : messages) {
      Assert.assertEquals("test", message.getDomainId());
    }
    memoryClockLogic.clean(now);

    // 再过3s，还可以再fetch出一个来
    TimeUnit.MILLISECONDS.sleep(3000L);
    now = System.currentTimeMillis();
    messages = memoryClockLogic.fetchClockMessages(now);
    Assert.assertEquals(1L, messages.size());
    for (ClockMessage message : messages) {
      Assert.assertEquals("test2", message.getDomainId());
    }
    messages = memoryClockLogic.fetchClockMessages(now);
    Assert.assertEquals(1L, messages.size());
    memoryClockLogic.clean(now);
    messages = memoryClockLogic.fetchClockMessages(now);
    Assert.assertEquals(0L, messages.size());
  }
}
