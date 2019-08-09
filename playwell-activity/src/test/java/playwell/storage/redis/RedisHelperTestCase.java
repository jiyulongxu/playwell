package playwell.storage.redis;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;

/**
 * TestCase for RedisHelper
 */
public class RedisHelperTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    initStandardActivityRunnerIntergrationPlan(false);
  }

  @Test
  public void testRedisOperations() {
    RedisHelper.use("default").call(jedis -> {
      jedis.set("a", "1");
      Assert.assertEquals("1", jedis.get("a"));
    });
  }

  @After
  public void tearDown() {
    cleanStandardActivityRunnerIntergrationPlan(false);
  }
}
