package playwell.common.argument;

import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import playwell.common.EasyMap;
import playwell.message.MessageArgumentVar;

/**
 * 为提供事件变量的参数表达式提供便捷访问接口
 */
public interface EventVarAccessMixin {

  MessageArgumentVar getEvent();

  default String eventType() {
    return getEvent().getType();
  }

  default boolean eventTypeIs(String type) {
    return getEvent().getType().equals(type);
  }

  default Map<String, Object> allEventAttributes() {
    return getEvent().getAllAttributes();
  }

  default Object eventAttr(String attrName) {
    final MessageArgumentVar event = getEvent();
    if (!event.contains(attrName)) {
      throw new RuntimeException(String.format(
          "Could not found the event attribute: %s", attrName));
    }
    return getEvent().get(attrName);
  }

  default Object eventAttr(String attrName, Object defaultValue) {
    return getEvent().get(attrName, defaultValue);
  }

  default int intEventAttr(String attrName) {
    return intEventAttr(attrName, 0);
  }

  default int intEventAttr(String attrName, int defaultValue) {
    return getEvent().getInt(attrName, defaultValue);
  }

  default List<Integer> intListEventAttr(String attrName) {
    return getEvent().getIntList(attrName);
  }

  default long longEventAttr(String attrName) {
    return longEventAttr(attrName, 0L);
  }

  default long longEventAttr(String attrName, long defaultValue) {
    return getEvent().getLong(attrName, defaultValue);
  }

  default List<Long> longListEventAttr(String attrName) {
    return getEvent().getLongList(attrName);
  }

  default boolean boolEventAttr(String attrName) {
    return boolEventAttr(attrName, false);
  }

  default boolean boolEventAttr(String attrName, boolean defaultValue) {
    return getEvent().getBoolean(attrName, defaultValue);
  }

  default List<Boolean> boolListEventAttr(String attrName) {
    return getEvent().getBooleanList(attrName);
  }

  default double doubleEventAttr(String attrName) {
    return doubleEventAttr(attrName, 0.0);
  }

  default double doubleEventAttr(String attrName, double defaultValue) {
    return getEvent().getDouble(attrName, defaultValue);
  }

  default List<Double> doubleListEventAttr(String attrName) {
    return getEvent().getDoubleList(attrName);
  }

  default String strEventAttr(String attrName) {
    return strEventAttr(attrName, "");
  }

  default String strEventAttr(String attrName, String defaultValue) {
    return getEvent().getString(attrName, defaultValue);
  }

  default List<String> strListEventAttr(String attrName) {
    return getEvent().getStringList(attrName);
  }

  default List<Object> listEventAttr(String attrName) {
    return getEvent().getList(attrName);
  }

  default EasyMap mapEventAttr(String attrName) {
    return getEvent().getSubArguments(attrName);
  }

  default List<EasyMap> mapListEventAttr(String attrName) {
    return getEvent().getSubArgumentsList(attrName);
  }

  default boolean containsAttr(String attrName) {
    return getEvent().contains(attrName);
  }

  default boolean isAttrEmpty(String attrName) {
    if (!containsAttr(attrName)) {
      return true;
    }

    Object val = eventAttr(attrName);
    if (val == null) {
      return true;
    }

    if (val instanceof String) {
      return StringUtils.isEmpty((String) val);
    }

    return false;
  }

  default boolean isAttrNotEmpty(String attrName) {
    return !isAttrEmpty(attrName);
  }
}
