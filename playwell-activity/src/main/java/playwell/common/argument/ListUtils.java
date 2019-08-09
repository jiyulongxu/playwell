package playwell.common.argument;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

/**
 * 可在参数中使用的通用List工具
 *
 * @author chihongze
 */
class ListUtils {

  ListUtils() {

  }

  ////////////// 增 ////////////

  /**
   * 在列表的末尾添加一个元素
   *
   * @param oldList 旧有的列表
   * @param element 要添加的新元素
   * @return 添加后的列表
   */
  public List<Object> add(List<Object> oldList, Object element) {
    final List<Object> list;
    if (CollectionUtils.isEmpty(oldList)) {
      list = new ArrayList<>();
      list.add(element);
    } else {
      list = new ArrayList<>(oldList.size() + 1);
      list.addAll(oldList);
      list.add(element);
    }
    return list;
  }

  /**
   * 将元素添加到列表的指定索引处
   *
   * @param oldList 旧有的列表
   * @param index 索引
   * @param element 要添加的元素
   * @return 添加后的列表
   */
  public List<Object> add(List<Object> oldList, int index, Object element) {
    final List<Object> list;

    // 旧有的列表是空
    if (CollectionUtils.isEmpty(oldList)) {
      if (index == 0) {
        list = new ArrayList<>();
        list.add(element);
      } else {
        throw new IndexOutOfBoundsException(String.format(
            "The list is empty, could not add new element to target index: %d",
            index
        ));
      }
    } else {
      if (index > oldList.size()) {
        throw new IndexOutOfBoundsException(String.format(
            "The list size is %d, could not add new element to target index: %d",
            oldList.size(),
            index
        ));
      } else if (index == oldList.size()) {
        list = add(oldList, element);
      } else {
        list = new ArrayList<>(oldList.size() + 1);
        int newIndex = 0;
        for (Object obj : oldList) {
          if (newIndex++ == index) {
            list.add(element);
          }
          list.add(obj);
        }
      }
    }
    return list;
  }

  /**
   * 如果元素在列表中存在，那么就不再添加；如果不存在，则添加列表的末尾
   *
   * @param oldList 旧有的列表
   * @param element 要添加的元素
   * @return 添加后的列表
   */
  public List<Object> addDistinct(List<Object> oldList, Object element) {
    final List<Object> list;

    // 旧有的列表为空
    if (CollectionUtils.isEmpty(oldList)) {
      list = new ArrayList<>();
      list.add(element);
    } else {
      final boolean existed = oldList.stream().anyMatch(obj -> obj.equals(element));
      if (existed) {
        list = oldList;
      } else {
        list = add(oldList, element);
      }
    }
    return list;
  }

  /**
   * 将元素按照顺序添加到列表，可以使用该函数构建有序列表
   *
   * @param oldList 要添加的列表
   * @param element 要添加的元素
   * @return 添加后的结果
   */
  @SuppressWarnings({"unchecked"})
  public List<Comparable> addOrder(List<Comparable> oldList, Comparable element) {
    if (element == null) {
      throw new NullPointerException("The addOrder function could not allow null element");
    }

    if (CollectionUtils.isEmpty(oldList)) {
      return Collections.singletonList(element);
    }

    final List<Comparable> list = new ArrayList<>(oldList.size() + 1);
    boolean add = false;
    for (Comparable ele : oldList) {
      if ((!add) && element.compareTo(ele) <= 0) {
        list.add(element);
        add = true;
      }
      list.add(ele);
    }
    if (!add) {
      list.add(element);
    }

    return list;
  }

  //////////////// 删 ///////////////

  /**
   * 根据指定索引删除列表中的元素
   *
   * @param oldList 列表
   * @param index 索引
   * @return 删除后的结果
   */
  public List<Object> removeByIndex(List<Object> oldList, int index) {
    if (CollectionUtils.isEmpty(oldList)) {
      throw new IndexOutOfBoundsException(String.format(
          "The list is empty, could not remove the index: %d",
          index
      ));
    }

    if (index >= oldList.size()) {
      throw new IndexOutOfBoundsException(String.format(
          "The list size is %d, could not remove the index: %d",
          oldList.size(),
          index
      ));
    }

    final List<Object> list = new ArrayList<>(oldList.size() - 1);
    for (int i = 0; i < oldList.size(); i++) {
      if (i == index) {
        continue;
      }
      list.add(oldList.get(i));
    }
    return list;
  }

  /**
   * 从列表中移除指定的元素
   *
   * @param oldList 列表
   * @param element 要移除的元素
   * @return 删除后的结果
   */
  public List<Object> removeByElement(List<Object> oldList, Object element) {
    if (CollectionUtils.isEmpty(oldList)) {
      throw new IndexOutOfBoundsException(String.format(
          "The list is empty, could not remove the element: %s",
          element
      ));
    }

    final List<Object> list = new ArrayList<>(oldList.size());
    for (Object ele : oldList) {
      if (eq(ele, element)) {
        continue;
      }
      list.add(ele);
    }
    return list;
  }

  /**
   * 将列表中的所有元素去重
   *
   * @return 去重结果
   */
  public List<Object> distinct(List<Object> oldList) {
    if (CollectionUtils.isEmpty(oldList)) {
      return Collections.emptyList();
    } else {
      Set<Object> set = new HashSet<>(oldList);
      return new ArrayList<>(set);
    }
  }

  ////////// 改 ////////////

  /**
   * 在指定索引处修改列表元素
   *
   * @param oldList 列表
   * @param index 索引
   * @param element 要修改的元素值
   * @return 修改后的列表
   */
  public List<Object> set(List<Object> oldList, int index, Object element) {
    if (CollectionUtils.isEmpty(oldList)) {
      throw new IndexOutOfBoundsException(String.format(
          "The list is empty, could not set element on index: %d",
          index
      ));
    }

    if (index >= oldList.size()) {
      throw new IndexOutOfBoundsException(String.format(
          "The list size is %d, could not set element on index: %d",
          oldList.size(),
          index
      ));
    }

    final List<Object> list = new ArrayList<>(oldList);
    list.set(index, element);
    return list;
  }

  /**
   * 将列表中的元素"打散"
   *
   * @param oldList 列表
   * @return 结果
   */
  public List<Object> shuffle(List<Object> oldList) {
    if (CollectionUtils.isEmpty(oldList)) {
      return Collections.emptyList();
    }
    final List<Object> list = new ArrayList<>(oldList.size());
    list.addAll(oldList);
    Collections.shuffle(list);
    return list;
  }

  /**
   * 对列表进行排序
   *
   * @param oldList 旧列表
   * @return 排序结果
   */
  @SuppressWarnings({"unchecked"})
  public List<Comparable> sort(List<Comparable> oldList) {
    if (CollectionUtils.isEmpty(oldList)) {
      return Collections.emptyList();
    }
    final List<Comparable> list = new ArrayList<>(oldList);
    Collections.sort(list);
    return list;
  }

  /**
   * 对列表逆序
   *
   * @param oldList 旧列表
   * @return 逆序结果
   */
  public List<Object> reverse(List<Object> oldList) {
    if (CollectionUtils.isEmpty(oldList)) {
      return Collections.emptyList();
    }

    final List<Object> list = new ArrayList<>(oldList);
    Collections.reverse(list);
    return list;
  }

  /**
   * 将一个列表中的所有元素都转换为Boolean然后返回
   *
   * @param oldList 要转化的列表
   * @return 转化结果
   */
  public List<Boolean> toBooleanList(List<Object> oldList) {
    if (CollectionUtils.isEmpty(oldList)) {
      return Collections.emptyList();
    }

    return oldList.stream().map(BaseArgumentRootContext::toBoolean).collect(Collectors.toList());
  }

  /**
   * 将一个列表中的所有元素都转换为Integer然后返回
   *
   * @param oldList 要转化的列表
   * @return 转化结果
   */
  public List<Integer> toIntList(List<Object> oldList) {
    if (CollectionUtils.isEmpty(oldList)) {
      return Collections.emptyList();
    }

    return oldList.stream().map(BaseArgumentRootContext::toInt).collect(Collectors.toList());
  }

  /**
   * 将一个列表中的所有元素都转换为Double然后返回
   *
   * @param oldList 要转化的列表
   * @return 转化结果
   */
  public List<Double> toDoubleList(List<Object> oldList) {
    if (CollectionUtils.isEmpty(oldList)) {
      return Collections.emptyList();
    }

    return oldList.stream().map(BaseArgumentRootContext::toDouble).collect(Collectors.toList());
  }

  /**
   * 将一个列表中的所有元素都转换为String然后返回
   *
   * @param oldList 要转化的列表
   * @return 转化结果
   */
  public List<String> toStringList(List<Object> oldList) {
    if (CollectionUtils.isEmpty(oldList)) {
      return Collections.emptyList();
    }

    return oldList.stream().map(BaseArgumentRootContext::toStr).collect(Collectors.toList());
  }

  ///////// 查 ////////////

  /**
   * 获得指定索引处的元素的值
   *
   * @param oldList 列表
   * @param index 索引
   * @return 元素
   */
  public Object get(List<Object> oldList, int index) {
    if (CollectionUtils.isEmpty(oldList)) {
      throw new IndexOutOfBoundsException(String.format(
          "The list is empty, could not get element on index: %d",
          index
      ));
    }

    if (index >= oldList.size()) {
      throw new IndexOutOfBoundsException(String.format(
          "The list size is %d, could not get element on index: %d",
          oldList.size(),
          index
      ));
    }

    return oldList.get(index);
  }

  /**
   * 获得指定索引处的元素的值，如果索引越界，则返回默认值
   *
   * @param oldList 列表
   * @param index 索引
   * @param defaultValue 元素默认值
   * @return 元素
   */
  public Object get(List<Object> oldList, int index, Object defaultValue) {
    try {
      return get(oldList, index);
    } catch (IndexOutOfBoundsException e) {
      return defaultValue;
    }
  }

  /**
   * 统计element在List中出现了多少次
   *
   * @param list 列表
   * @param element 元素
   * @return 次数
   */
  public long count(List<Object> list, Object element) {
    if (CollectionUtils.isEmpty(list)) {
      return 0;
    }

    return list.stream().filter(ele -> eq(ele, element)).count();
  }

  /**
   * 统计列表中所有元素各自出现的次数
   *
   * @param list 列表
   * @return 各个元素出现的次数
   */
  public Map<Object, Long> groupCount(List<Object> list) {
    if (CollectionUtils.isEmpty(list)) {
      return Collections.emptyMap();
    }

    return list.stream()
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
  }

  /**
   * 获取列表中的最小值
   *
   * @param list 列表
   * @return 最小值
   */
  @SuppressWarnings({"unchecked"})
  public Object min(List<Comparable> list) {
    if (CollectionUtils.isEmpty(list)) {
      throw new IllegalArgumentException("The list is empty, could not get the min value");
    }

    return Collections.min(list);
  }

  /**
   * 获取列表中的最大值
   *
   * @param list 列表
   * @return 最大值
   */
  @SuppressWarnings({"unchecked"})
  public Object max(List<Comparable> list) {
    if (CollectionUtils.isEmpty(list)) {
      throw new IllegalArgumentException("The list is empty, could not get the max value");
    }

    return Collections.max(list);
  }

  /**
   * 将列表中的所有元素转换为int，然后得出int的和
   *
   * @param numbers 要求和的数字列表
   * @return int和
   */
  public int sumInt(List<Number> numbers) {
    if (CollectionUtils.isEmpty(numbers)) {
      return 0;
    }

    return numbers.stream().mapToInt(Number::intValue).sum();
  }

  /**
   * 将列表中所有元素转换为Double，然后得出double的和
   *
   * @param numbers 要求和的数字列表
   * @return double和
   */
  public double sumDouble(List<Number> numbers) {
    if (CollectionUtils.isEmpty(numbers)) {
      return 0;
    }

    return numbers.stream().mapToDouble(Number::doubleValue).sum();
  }

  /**
   * 对所有Decimal元素进行求和，没有精度
   *
   * @param numbers Decimal元素列表
   * @return 和
   */
  public BigDecimal sumDecimal(List<Object> numbers) {
    if (CollectionUtils.isEmpty(numbers)) {
      return BigDecimal.ZERO;
    }

    return numbers.stream().map(BaseArgumentRootContext::toDecimal)
        .reduce(BigDecimal::add).orElse(BigDecimal.ZERO);
  }

  /**
   * 求列表中的平均值
   *
   * @param numbers 数值列表
   * @return 平均值，返回double类型
   */
  public double avg(List<Number> numbers) {
    if (CollectionUtils.isEmpty(numbers)) {
      throw new IllegalArgumentException("The list is empty, could not get the average");
    }

    OptionalDouble avgOpt = numbers.stream().mapToDouble(Number::doubleValue).average();
    if (avgOpt.isPresent()) {
      return avgOpt.getAsDouble();
    }
    throw new IllegalArgumentException("The list is empty, could not get the average");
  }


  /**
   * 从列表中随机选择一个元素
   *
   * @param list 列表
   * @return 随机选择的元素
   */
  public Object randomChoice(List<Object> list) {
    if (CollectionUtils.isEmpty(list)) {
      throw new IllegalArgumentException("The list is empty, could not choice the element");
    }
    final int targetIndex = ThreadLocalRandom.current().nextInt(list.size());
    return list.get(targetIndex);
  }

  /**
   * 将列表中的元素使用界定符拼接成统一的字符串
   *
   * @param list 列表
   * @param delimiter 分隔符
   * @return 拼接字符串
   */
  public String join(List<Object> list, String delimiter) {
    if (CollectionUtils.isEmpty(list)) {
      return "";
    }
    final StringBuilder buffer = new StringBuilder();
    int index = 0;
    for (Object ele : list) {
      buffer.append(ele);
      if (index++ < list.size() - 1) {
        buffer.append(delimiter);
      }
    }
    return buffer.toString();
  }

  private boolean eq(Object ele1, Object ele2) {
    if (ele1 == null) {
      return ele2 == null;
    } else {
      if (ele2 == null) {
        return false;
      }
      return ele1.equals(ele2);
    }
  }
}
