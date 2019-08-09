package playwell.route;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multiset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.route.SlotsManager.ResultFields;

/**
 * TestCase for MySQLSlotsManager
 */
public class MySQLSlotsManagerTestCase extends RouteBaseTestCase {

  @Before
  public void setUp() {
    super.setUp();
  }

  @Test
  public void testSlotsOperations() {
    // 正常分配
    Result result = slotsManager.allocSlots(
        100,
        ImmutableMap.of(
            "service_a", 20,
            "service_b", 30,
            "service_c", 50
        )
    );
    Assert.assertTrue(result.isOk());
    ((MySQLSlotsManager) slotsManager).beforeLoop();
    result = slotsManager.getSlotsDistribution();
    EasyMap data = result.getData();
    Assert.assertEquals(100, data.getInt(ResultFields.SLOTS));
    EasyMap cluster = data.getSubArguments(ResultFields.DISTRIBUTION);
    Assert.assertEquals(20, cluster.getInt("service_a"));
    Assert.assertEquals(30, cluster.getInt("service_b"));
    Assert.assertEquals(50, cluster.getInt("service_c"));

    // 重复分配
    result = slotsManager.allocSlots(
        100,
        ImmutableMap.of(
            "service_a", 20,
            "service_b", 30,
            "service_c", 50
        )
    );
    Assert.assertTrue(result.isFail());

    Collection<Integer> slots = slotsManager.getSlotsByServiceName("service_a");
    Assert.assertEquals(20, new HashSet<>(slots).size());

    Optional<String> serviceOptional = slotsManager.getServiceNameBySlot(0);
    Assert.assertTrue(serviceOptional.isPresent());
    Assert.assertEquals("service_a", serviceOptional.get());

    Multiset<String> counter = HashMultiset.create();
    long beginTime = System.currentTimeMillis();
    for (int i = 1000000; i < 2000000; i++) {
      counter.add(slotsManager.getServiceByKey(Integer.toString(i)));
    }
    System.out.println("Used time: " + (System.currentTimeMillis() - beginTime));
    System.out.println(counter.count("service_a"));
    System.out.println(counter.count("service_b"));
    System.out.println(counter.count("service_c"));
  }

  @After
  public void tearDown() {
    super.tearDown();
  }
}
