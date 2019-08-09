package playwell.clock;

import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;

/**
 * CachedTimestamp的测试用例
 *
 * @author chihongze@gmail.com
 */
public class CachedTimestampTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    initStandardActivityRunnerIntergrationPlan(false);
  }

  @Test
  public void testGetTimestamp() throws Exception {
    for (int i = 0; i < 5; i++) {
      System.out.println("Now seconds: " + CachedTimestamp.nowSeconds());
      System.out.println("Now milliseconds: " + CachedTimestamp.nowMilliseconds());
      TimeUnit.SECONDS.sleep(1L);
    }
  }

  @After
  public void tearDown() {
    cleanStandardActivityRunnerIntergrationPlan(false);
  }
}
