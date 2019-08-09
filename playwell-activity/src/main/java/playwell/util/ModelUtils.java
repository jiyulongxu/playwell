package playwell.util;

import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import playwell.common.Mappable;
import playwell.common.expression.PlaywellExpression;

/**
 * 在构建Model对象时的辅助工具
 *
 * @author chihongze@gmail.com
 */
public final class ModelUtils {

  private ModelUtils() {

  }

  /**
   * 将表达式参数转换为字符串参数描述
   *
   * @param args 表达式参数
   * @return 字符串参数描述
   */
  public static Map<String, Object> expressionMapToStr(Map<String, PlaywellExpression> args) {
    if (MapUtils.isEmpty(args)) {
      return Collections.emptyMap();
    }
    return args.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
        e -> e.getValue().getExpressionString()));
  }

  /**
   * 将Mappable对象集合转换为Map列表
   *
   * @param mappableObjects Mappable对象集合
   * @return Map对象列表
   */
  public static <T extends Mappable> List<Map<String, Object>> expandList(
      Collection<T> mappableObjects) {
    if (CollectionUtils.isEmpty(mappableObjects)) {
      return Collections.emptyList();
    }
    return mappableObjects.stream().map(T::toMap).collect(Collectors.toList());
  }

  /**
   * 将Map中的Mappable对象递归展开
   *
   * @param map 要展开的Map
   * @return 展开之后的结果
   */
  @SuppressWarnings({"unchecked"})
  public static Map<String, Object> expandMappable(Map<String, Object> map) {
    if (MapUtils.isEmpty(map)) {
      return Collections.emptyMap();
    }

    final Map<String, Object> result = Maps.newHashMapWithExpectedSize(map.size());
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      if (value instanceof Mappable) {
        result.put(key, expandMappable(((Mappable) value).toMap()));
      } else if (value instanceof Collection) {
        result.put(key, expandCollection((Collection<Object>) value));
      } else if (value instanceof Map) {
        result.put(key, expandMappable((Map<String, Object>) value));
      } else {
        result.put(key, value);
      }
    }
    return result;
  }

  @SuppressWarnings({"unchecked"})
  public static List<Object> expandCollection(Collection<Object> list) {
    if (CollectionUtils.isEmpty(list)) {
      return Collections.emptyList();
    }

    final List<Object> result = new LinkedList<>();
    for (Object obj : list) {
      if (obj instanceof Mappable) {
        result.add(expandMappable(((Mappable) obj).toMap()));
      } else if (obj instanceof Map) {
        result.add(expandMappable((Map<String, Object>) obj));
      } else if (obj instanceof Collection) {
        result.add(expandCollection((Collection<Object>) obj));
      } else {
        result.add(obj);
      }
    }

    return result;
  }

  public static Map<String, String> mapValueToString(Map<String, Object> map) {
    if (MapUtils.isEmpty(map)) {
      return Collections.emptyMap();
    }

    final Map<String, String> result = new HashMap<>(map.size());
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();
      if (value == null) {
        result.put(key, "");
      } else {
        result.put(key, value.toString());
      }
    }
    return result;
  }
}
