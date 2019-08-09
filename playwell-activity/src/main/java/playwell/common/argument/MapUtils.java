package playwell.common.argument;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 用于操作Map的工具
 *
 * @author chihongze@gmail.com
 */
class MapUtils {

  MapUtils() {

  }

  ////////////////////// 增 /////////////////////

  public Map<Object, Object> put(Map<Object, Object> oldMap, Object key, Object value) {
    if (isEmpty(oldMap)) {
      return Collections.singletonMap(key, value);
    }

    final Map<Object, Object> map;
    if (oldMap.containsKey(key)) {
      map = new HashMap<>(oldMap.size());
    } else {
      map = new HashMap<>(oldMap.size() + 1);
    }

    map.putAll(oldMap);
    map.put(key, value);

    return map;
  }

  ///////////////////////// 删 ////////////////////

  public Map<Object, Object> removeByKey(Map<Object, Object> oldMap, Object key) {
    if (isEmpty(oldMap)) {
      return Collections.emptyMap();
    }

    if (oldMap.containsKey(key)) {
      final Map<Object, Object> map = new HashMap<>(oldMap.size() - 1);
      for (Map.Entry<Object, Object> entry : oldMap.entrySet()) {
        if (eq(entry.getKey(), key)) {
          continue;
        }
        map.put(entry.getKey(), entry.getValue());
      }
      return map;
    }

    return oldMap;
  }

  public Map<Object, Object> removeByValue(Map<Object, Object> oldMap, Object value) {
    if (isEmpty(oldMap)) {
      return Collections.emptyMap();
    }

    final Map<Object, Object> map = new HashMap<>();
    for (Map.Entry<Object, Object> entry : oldMap.entrySet()) {
      if (eq(entry.getValue(), value)) {
        continue;
      }
      map.put(entry.getKey(), entry.getValue());
    }
    return map;
  }

  ///////////////////////// 改 ////////////////////

  public Map<Object, Object> update(Map<Object, Object> oldMap, Map<Object, Object> newMap) {
    if (isEmpty(oldMap)) {
      if (isEmpty(newMap)) {
        return Collections.emptyMap();
      } else {
        return newMap;
      }
    }

    if (isEmpty(newMap)) {
      return oldMap;
    }

    final Map<Object, Object> map = new HashMap<>();
    map.putAll(oldMap);
    map.putAll(newMap);
    return map;
  }

  private boolean isEmpty(Map<Object, Object> map) {
    return org.apache.commons.collections4.MapUtils.isEmpty(map);
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
