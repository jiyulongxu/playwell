package playwell.common.argument;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import playwell.common.EasyMap;


/**
 * 为提供上下文变量的参数表达式提供便捷访问接口
 */
public interface ContextVarAccessMixin {

  Map<String, Object> getContext();

  default Map<String, Object> allVars() {
    return getContext();
  }

  default Object $(String varName) {
    return var(varName);
  }

  default Object $(String varName, Object defaultValue) {
    return var(varName, defaultValue);
  }

  default Object var(String varName) {
    final Map<String, Object> context = getContext();
    if (!context.containsKey(varName)) {
      throw new RuntimeException(
          String.format("Could not found the context variable: %s", varName));
    }
    return context.get(varName);
  }

  default Object var(String varName, Object defaultValue) {
    return getContext().getOrDefault(varName, defaultValue);
  }

  default int intVar(String varName) {
    return intVar(varName, 0);
  }

  default int intVar(String varName, int defaultValue) {
    return new EasyMap(getContext()).getInt(varName, defaultValue);
  }

  default List<Integer> intListVar(String varName) {
    return new EasyMap(getContext()).getIntegerList(varName);
  }

  default long longVar(String varName) {
    return longVar(varName, 0L);
  }

  default long longVar(String varName, long defaultValue) {
    return new EasyMap(getContext()).getLong(varName, defaultValue);
  }

  default List<Long> longListVar(String varName) {
    return new EasyMap(getContext()).getLongList(varName);
  }

  default boolean boolVar(String varName) {
    return boolVar(varName, false);
  }

  default boolean boolVar(String varName, boolean defaultValue) {
    return new EasyMap(getContext()).getBoolean(varName, defaultValue);
  }

  default List<Boolean> boolListVar(String varName) {
    return new EasyMap(getContext()).getBooleanList(varName);
  }

  default double doubleVar(String varName) {
    return doubleVar(varName, 0.0);
  }

  default double doubleVar(String varName, double defaultValue) {
    return new EasyMap(getContext()).getDouble(varName, defaultValue);
  }

  default List<Double> doubleListVar(String varName) {
    return new EasyMap(getContext()).getDoubleList(varName);
  }

  default BigDecimal decimalVar(String varName) {
    return decimalVar(varName, 0);
  }

  default BigDecimal decimalVar(String varName, Object defaultValue) {
    return new EasyMap(getContext()).getDecimal(varName, defaultValue);
  }

  default List<BigDecimal> decimalListVar(String varName) {
    return new EasyMap(getContext()).getDecimalList(varName);
  }

  default String strVar(String varName) {
    return strVar(varName, "");
  }

  default String strVar(String varName, String defaultValue) {
    return new EasyMap(getContext()).getString(varName, defaultValue);
  }

  default List<String> strListVar(String varName) {
    return new EasyMap(getContext()).getStringList(varName);
  }

  default List<Object> listVar(String varName) {
    return new EasyMap(getContext()).getObjectList(varName);
  }

  default EasyMap mapVar(String varName) {
    return new EasyMap(getContext()).getSubArguments(varName);
  }

  default List<EasyMap> mapListVar(String varName) {
    return new EasyMap(getContext()).getSubArgumentsList(varName);
  }

  default List<Object> ref(String type, String name) {
    return ref(type, name, Collections.emptyMap());
  }

  default List<Object> ref(String type, String name, Map<String, Object> meta) {
    return new Ref(type, name, meta, false).toSequence();
  }

  default List<Object> tmpRef(String type, String name) {
    return tmpRef(type, name, Collections.emptyMap());
  }

  default List<Object> tmpRef(String type, String name, Map<String, Object> meta) {
    return new Ref(type, name, meta, true).toSequence();
  }
}
