package playwell.common;


import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.common.exception.InvalidArgumentException;

/**
 * TestCase for playwell.common.EasyMap
 *
 * @author chihongze@gmail.com
 */
public class EasyMapTestCase {

  private EasyMap easyMap;

  @Before
  public void setUp() {
    easyMap = new EasyMap(ImmutableMap.<String, Object>builder()
        .put("int_a", 123)
        .put("int_b", "123")
        .put("int_x", "abc")
        .put("int_list", Arrays.asList(1, "2", 3, "4"))
        .put("int_list_x", Arrays.asList("1", "abc", 2))
        .put("long_a", 1000000000000000L)
        .put("long_b", "1000000000000000")
        .put("long_x", "abc")
        .put("long_list", Arrays.asList(1000000000000000L, 1000000000000001L, "1000000000000002"))
        .put("long_list_x", Arrays.asList(1000000000000000L, 1000000000000001L, "abc"))
        .put("double_a", 3.14)
        .put("double_b", "3.14")
        .put("double_x", "abc")
        .put("bool_a", true)
        .put("bool_b", "true")
        .put("str_a", "Sam")
        .put("str_x", 12345)
        .put("map_a", ImmutableMap.of("a", 1))
        .put("map_list", Arrays.asList(
            ImmutableMap.of("a", "2"),
            ImmutableMap.of("b", 3)
        ))
        .build());
  }

  @Test
  public void testGetInteger() {
    Assert.assertEquals(123, easyMap.getInt("int_a"));
    Assert.assertEquals(123, easyMap.getInt("int_b"));
    try {
      easyMap.getInt("int_c");
      Assert.fail("Should throw out InvalidArgumentException");
    } catch (InvalidArgumentException e) {
      System.out.println(e.getMessage());
      Assert.assertEquals("int_c", e.getName());
    }
    try {
      easyMap.getInt("int_x");
      Assert.fail("Should throw out InvalidArgumentException");
    } catch (InvalidArgumentException e) {
      System.out.println(e.getMessage());
      Assert.assertEquals("int_x", e.getName());
    }
    Assert.assertEquals(123, easyMap.getInt("int_c", 123));
  }

  @Test
  public void testGetIntegerList() {
    Assert.assertArrayEquals(
        Arrays.asList(1, 2, 3, 4).toArray(),
        easyMap.getIntegerList("int_list").toArray());
    try {
      easyMap.getIntegerList("int_list_x");
    } catch (InvalidArgumentException e) {
      System.out.println(e.getMessage());
      Assert.assertEquals("int_list_x", e.getName());
    }
  }

  @Test
  public void testGetLong() {
    Assert.assertEquals(1000000000000000L, easyMap.getLong("long_a"));
    Assert.assertEquals(1000000000000000L, easyMap.getLong("long_b"));
    try {
      easyMap.getLong("long_c");
      Assert.fail("Should throw out InvalidArgumentException");
    } catch (InvalidArgumentException e) {
      System.out.println(e.getMessage());
      Assert.assertEquals("long_c", e.getName());
    }
    try {
      easyMap.getLong("long_x");
      Assert.fail("Should throw out InvalidArgumentException");
    } catch (InvalidArgumentException e) {
      System.out.println(e.getMessage());
      Assert.assertEquals("long_x", e.getName());
    }
    Assert.assertEquals(
        1000000000000000L, easyMap.getLong("long_c", 1000000000000000L));
  }

  @Test
  public void testGetLongList() {
    Assert.assertArrayEquals(
        Arrays.asList(1000000000000000L, 1000000000000001L, 1000000000000002L).toArray(),
        easyMap.getLongList("long_list").toArray());
    try {
      easyMap.getLongList("long_list_x");
    } catch (InvalidArgumentException e) {
      System.out.println(e.getMessage());
      Assert.assertEquals("long_list_x", e.getName());
    }
  }

  @Test
  public void testGetDouble() {
    Assert.assertEquals(314, (int) (easyMap.getDouble("double_a") * 100));
    Assert.assertEquals(314, (int) (easyMap.getDouble("double_b") * 100));
    try {
      easyMap.getDouble("double_c");
      Assert.fail("Should throw out InvalidArgumentException");
    } catch (InvalidArgumentException e) {
      System.out.println(e.getMessage());
      Assert.assertEquals("double_c", e.getName());
    }
    try {
      easyMap.getDouble("double_x");
      Assert.fail("Should throw out InvalidArgumentException");
    } catch (InvalidArgumentException e) {
      System.out.println(e.getMessage());
      Assert.assertEquals("double_x", e.getName());
    }
    Assert.assertEquals(314, (int) (easyMap.getDouble("double_c", 3.14) * 100));
  }

  @Test
  public void testGetBoolean() {
    Assert.assertTrue(easyMap.getBoolean("bool_a"));
    Assert.assertTrue(easyMap.getBoolean("bool_b"));
    try {
      easyMap.getBoolean("bool_c");
      Assert.fail("Should throw out InvalidArgumentException");
    } catch (InvalidArgumentException e) {
      System.out.println(e.getMessage());
      Assert.assertEquals("bool_c", e.getName());
    }
    try {
      easyMap.getBoolean("bool_x");
      Assert.fail("Should throw out InvalidArgumentException");
    } catch (InvalidArgumentException e) {
      System.out.println(e.getMessage());
      Assert.assertEquals("bool_x", e.getName());
    }
    Assert.assertFalse(easyMap.getBoolean("bool_x", false));
  }

  @Test
  public void testGetString() {
    Assert.assertEquals("Sam", easyMap.getString("str_a"));
    try {
      easyMap.getString("str_b");
      Assert.fail("Should throw out InvalidArgumentException");
    } catch (InvalidArgumentException e) {
      System.out.println(e.getMessage());
      Assert.assertEquals("str_b", e.getName());
    }
    Assert.assertEquals("Jack", easyMap.getString("str_b", "Jack"));
  }

  @Test
  public void testGetSubArguments() {
    EasyMap easyMap = this.easyMap.getSubArguments("map_a");
    Assert.assertEquals(1, easyMap.getInt("a"));

    Assert.assertEquals(Collections.emptyMap(),
        this.easyMap.getSubArguments("map_b").toMap());
  }

  @Test
  public void testGetSubArgumentsList() {
    List<EasyMap> easyMapList = this.easyMap.getSubArgumentsList("map_list");
    Assert.assertEquals(2, easyMapList.size());
  }
}
