package playwell.common.argument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 计数器工具
 *
 * @author chihongze
 */
class CounterUtils extends MapUtils {

  CounterUtils() {

  }

  //////////// 增 //////////

  /**
   * 为某一项增加指定的数值
   *
   * @param oldCounter 要操作的计数器
   * @param item 要操作的项
   * @param num 要增加的数值
   * @return 增加后的结果
   */
  public Map<Object, Long> incr(Map<Object, Long> oldCounter, Object item, int num) {
    final Map<Object, Long> counter;

    if (org.apache.commons.collections4.MapUtils.isEmpty(oldCounter)) {
      counter = Collections.singletonMap(item, (long) num);
    } else {
      if (oldCounter.containsKey(item)) {
        counter = new HashMap<>(oldCounter.size());
        counter.putAll(oldCounter);
        long count = counter.get(item) + num;
        counter.put(item, count);
      } else {
        counter = new HashMap<>(oldCounter.size() + 1);
        counter.putAll(oldCounter);
        counter.put(item, (long) num);
      }
    }

    return counter;
  }

  public Map<Object, Long> incr(Map<Object, Long> oldCounter, Object item) {
    return incr(oldCounter, item, 1);
  }

  public Map<Object, Long> decr(Map<Object, Long> oldCounter, Object item) {
    return incr(oldCounter, item, -1);
  }

  public Map<Object, Long> decr(Map<Object, Long> oldCounter, Object item, int num) {
    return incr(oldCounter, item, -num);
  }

  ///////////////// 查 /////////////////

  /**
   * 从计数器中获取制定项目的值，如果项目不存在，则返回0
   *
   * @param counter 计数器
   * @param item 项目
   * @return 计数值
   */
  public long get(Map<Object, Long> counter, Object item) {
    return counter.getOrDefault(item, 0L);
  }

  /**
   * 获取计数器中出现最多的N个条目
   *
   * @param counter 计数器
   * @param n 条目数目
   */
  public List<Map.Entry<Object, Long>> mostCommon(Map<Object, Long> counter, int n) {
    if (n <= 0) {
      throw new IllegalArgumentException("The n for mostCommon function must more than zero");
    }

    if (org.apache.commons.collections4.MapUtils.isEmpty(counter)) {
      return Collections.emptyList();
    }

    final List<Map.Entry<Object, Long>> entries = new ArrayList<>(counter.entrySet());
    entries.sort(Comparator.<Entry<Object, Long>>comparingLong(Map.Entry::getValue).reversed());
    if (n >= entries.size()) {
      return entries;
    } else {
      return entries.subList(0, n);
    }
  }
}
