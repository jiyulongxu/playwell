package playwell.common.argument;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.activity.Activity;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ActivityThreadStatus;
import playwell.activity.thread.TestActivityThreadStatusListener;
import playwell.clock.CachedTimestamp;
import playwell.message.TestUserBehaviorEvent;

/**
 * <p>测试参数表达式中的内置函数</p>
 *
 * @author chihongze@gmail.com
 */
public class ArgumentInnerFunctionTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    initStandardActivityRunnerIntergrationPlan(true);
  }

  @Test
  public void testRegex() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/inner_functions/regex.yml",
        new TestUserBehaviorEvent(
            "test_regex",
            "test",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );

    TimeUnit.SECONDS.sleep(3L);

    final Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener
        .getThread(
            activity.getId(),
            "test_regex"
        );
    System.out.println(">>>>> ALL Threads: " + TestActivityThreadStatusListener.ALL_THREADS);
    Assert.assertTrue(activityThreadOptional.isPresent());
    final ActivityThread activityThread = activityThreadOptional.get();
    final Map<String, Object> context = activityThread.getContext();
    Assert.assertEquals("chihongze", context.get("test_group"));
    Assert.assertEquals(
        Arrays.asList("chihongze@gmail.com", "chihongze", "gmail"),
        context.get("group_all")
    );
    Assert.assertTrue((boolean) context.get("match"));
    Assert.assertFalse((boolean) context.get("not_match"));
    Assert.assertEquals(
        "chihongze is a good man, chz will be a cool man",
        context.get("replace_first")
    );
    Assert.assertEquals(
        "chihongze is a good man, chihongze will be a cool man",
        context.get("replace_all")
    );
  }

  @Test
  public void testList() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/inner_functions/list.yml",
        new TestUserBehaviorEvent(
            "test_list",
            "test",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );

    TimeUnit.SECONDS.sleep(3L);

    Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener
        .getThread(
            activity.getId(),
            "test_list"
        );
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Map<String, Object> context = activityThread.getContext();

    // 测试add
    Assert.assertEquals(
        Arrays.asList("ChiHongze", "is", "a", "good", "man", "!"),
        context.get("split_result")
    );
    Assert.assertEquals(
        Arrays.asList(1, 2, 3, 4, 5, 6),
        context.get("numbers_a")
    );
    Assert.assertEquals(
        Arrays.asList(0, 1, 2, 3, 4, 5, 6),
        context.get("numbers_b")
    );

    // 测试join字符串
    nextListTest();
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertEquals("ChiHongze is a good man !", TestActivityThreadStatusListener.getFromCtx(
        activity.getId(), "test_list", "joined_text"));

    // 测试从列表中删除元素
    nextListTest();
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertEquals(
        Arrays.asList("ChiHongze", "is", "a", "good", "man"),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(), "test_list", "split_result")
    );
    Assert.assertEquals(
        Arrays.asList(1, 2, 3, 4, 5, 6),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(), "test_list", "numbers_b")
    );
    Assert.assertEquals(
        Arrays.asList(1, 2, 3, 4, 5, 6),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(), "test_list", "numbers_c")
    );

    // 测试set操作
    nextListTest();
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertEquals(
        Arrays.asList("Chihz", "is", "a", "good", "man"),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(), "test_list", "split_result")
    );

    // 测试shuffle操作
    nextListTest();
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertNotEquals(
        Arrays.asList(1, 2, 3, 4, 5, 6),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(), "test_list", "numbers_c")
    );

    // 测试sort操作
    nextListTest();
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertEquals(
        Arrays.asList(1, 2, 3, 4, 5, 6),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(), "test_list", "numbers_c")
    );

    // 测试reverse操作
    nextListTest();
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertEquals(
        Arrays.asList(6, 5, 4, 3, 2, 1),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(), "test_list", "numbers_c")
    );

    // 测试count
    nextListTest();
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertEquals(3L, TestActivityThreadStatusListener.getFromCtx(
        activity.getId(), "test_list", "count"));
    Assert.assertEquals(
        new HashMap<>(ImmutableMap.of(
            1, 1L,
            2, 1L,
            3, 2L,
            4, 3L,
            5, 2L
        )),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(), "test_list", "group_count"));

    // 测试min和max
    nextListTest();
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertEquals(1, TestActivityThreadStatusListener.getFromCtx(
        activity.getId(), "test_list", "min"));
    Assert.assertEquals(6, TestActivityThreadStatusListener.getFromCtx(
        activity.getId(), "test_list", "max"));

    // 测试random choice
    nextListTest();
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertTrue(IntStream.range(1, 7).boxed().anyMatch(ele -> ele.equals(
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(), "test_list", "random_element")
    )));

    // 测试add order
    nextListTest();
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertEquals(
        Arrays.asList(2, 2, 3, 4, 4, 5, 6, 7, 8),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(), "test_list", "order_list")
    );

    nextListTest();
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertEquals(ActivityThreadStatus.FINISHED, TestActivityThreadStatusListener.getStatus(
        activity.getId(),
        "test_list"
    ));
  }

  private void nextListTest() {
    sendMessage(new TestUserBehaviorEvent(
        "test_list",
        "next",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
  }

  @Test
  @SuppressWarnings({"unchecked"})
  public void testCounter() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/inner_functions/counter.yml",
        new TestUserBehaviorEvent(
            "test_counter",
            "start",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertEquals(
        new HashMap<>(ImmutableMap.of(
            "a", 1L,
            "b", 1L,
            "c", 2L,
            "d", 1L,
            "e", 3L
        )),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(),
            "test_counter",
            "counter_a"
        )
    );

    sendMessage(new TestUserBehaviorEvent(
        "test_counter",
        "next",
        Collections.singletonMap("next", "test_incr"),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertEquals(
        new HashMap<>(ImmutableMap.of(
            "a", 6L,
            "b", 1L,
            "c", 2L,
            "d", 1L,
            "e", 3L
        )),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(),
            "test_counter",
            "counter_a"
        )
    );
    Assert.assertEquals(
        new HashMap<>(ImmutableMap.of(
            "x", 1L
        )),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(),
            "test_counter",
            "counter_b"
        )
    );

    sendMessage(new TestUserBehaviorEvent(
        "test_counter",
        "next",
        Collections.singletonMap("next", "test_most_common"),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(2L);
    List<Entry<Object, Long>> mostCommonEntries = (List<Entry<Object, Long>>)
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(),
            "test_counter",
            "most_common"
        );
    Assert.assertEquals(
        Arrays.asList(
            Pair.of("a", 6L),
            Pair.of("e", 3L),
            Pair.of("c", 2L)
        ),
        mostCommonEntries
    );

    sendMessage(new TestUserBehaviorEvent(
        "test_counter",
        "finished",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertEquals(
        ActivityThreadStatus.FINISHED,
        TestActivityThreadStatusListener.getStatus(activity.getId(), "test_counter")
    );
  }

  @Test
  public void testMap() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/inner_functions/map.yml",
        new TestUserBehaviorEvent(
            "test_map",
            "start",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertEquals(
        new HashMap<>(ImmutableMap.of(
            "name", "SamChi",
            "gender", "male",
            "age", 108
        )),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(),
            "test_map",
            "map_a"
        )
    );
    Assert.assertEquals(
        new HashMap<>(ImmutableMap.of(
            "name", "Tom",
            "lang", "Python",
            "age", 92
        )),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(),
            "test_map",
            "map_b"
        )
    );

    sendMessage(new TestUserBehaviorEvent(
        "test_map",
        "next",
        Collections.singletonMap("next", "test_put"),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertEquals(
        new HashMap<>(ImmutableMap.of(
            "name", "SamChi",
            "gender", "male",
            "age", 108,
            "lang", "Java"
        )),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(),
            "test_map",
            "map_a"
        )
    );
    Assert.assertEquals(
        new HashMap<>(ImmutableMap.of(
            "name", "Tom",
            "gender", "male",
            "age", 92,
            "lang", "Python",
            "grade", "A"
        )),
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(),
            "test_map",
            "map_b"
        )
    );

    sendMessage(new TestUserBehaviorEvent(
        "test_map",
        "finish",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(2L);
    Assert.assertEquals(
        ActivityThreadStatus.FINISHED,
        TestActivityThreadStatusListener.getStatus(activity.getId(), "test_map")
    );
  }

  @Test
  public void testMath() throws Exception {
    final Activity activity = spawn(
        "docs/sample/test_definitions/inner_functions/math.yml",
        new TestUserBehaviorEvent(
            "test_map",
            "start",
            Collections.emptyMap(),
            CachedTimestamp.nowMilliseconds()
        )
    );

    TimeUnit.SECONDS.sleep(2L);

    Assert.assertEquals(1, TestActivityThreadStatusListener.getFromCtx(
        activity.getId(),
        "test_map",
        "a"
    ));
  }

  @After
  public void tearDown() {
    cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
