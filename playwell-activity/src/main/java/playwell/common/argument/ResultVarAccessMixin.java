package playwell.common.argument;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import playwell.common.EasyMap;
import playwell.common.Result;

/**
 * 为提供执行结果变量的参数表达式提供便捷访问接口
 */
public interface ResultVarAccessMixin {

  Result getResult();

  default boolean resultOk() {
    return getResult().isOk();
  }

  default boolean resultFailure() {
    return getResult().isFail();
  }

  default boolean resultTimeout() {
    return getResult().isTimeout();
  }

  default boolean resultIgnore() {
    return getResult().isIgnore();
  }

  default String errorCode() {
    return getResult().getErrorCode();
  }

  default Map<String, Object> allResultVars() {
    final EasyMap resultData = getResult().getData();
    return resultData.toMap();
  }

  default Object resultVar(String name) {
    final EasyMap resultData = getResult().getData();
    if (!resultData.contains(name)) {
      throw new RuntimeException(String.format("Could not found the result variable: %s", name));
    }
    return resultData.get(name);
  }

  default Object resultVar(String name, Object defaultValue) {
    return getResult().get(name, defaultValue);
  }

  default int intResultVar(String name) {
    return intResultVar(name, 0);
  }

  default int intResultVar(String name, int defaultValue) {
    return getResult().getData().getInt(name, defaultValue);
  }

  default List<Integer> intListResultVar(String name) {
    return getResult().getData().getIntegerList(name);
  }

  default long longResultVar(String name) {
    return longResultVar(name, 0L);
  }

  default long longResultVar(String name, long defaultValue) {
    return getResult().getData().getLong(name, defaultValue);
  }

  default List<Long> longListResultVar(String name) {
    return getResult().getData().getLongList(name);
  }

  default boolean boolResultVar(String name) {
    return boolResultVar(name, false);
  }

  default boolean boolResultVar(String name, boolean defaultValue) {
    return getResult().getData().getBoolean(name, defaultValue);
  }

  default List<Boolean> boolListResultVar(String name) {
    return getResult().getData().getBooleanList(name);
  }

  default double doubleResultVar(String name) {
    return doubleResultVar(name, 0);
  }

  default double doubleResultVar(String name, double defaultValue) {
    return getResult().getData().getDouble(name, defaultValue);
  }

  default List<Double> doubleListResultVar(String name) {
    return getResult().getData().getDoubleList(name);
  }

  default BigDecimal decimalResultVar(String name) {
    return decimalResultVar(name, 0);
  }

  default BigDecimal decimalResultVar(String name, Object defaultValue) {
    return getResult().getData().getDecimal(name, defaultValue);
  }

  default List<BigDecimal> decimalListResultVar(String name) {
    return getResult().getData().getDecimalList(name);
  }

  default String strResultVar(String name) {
    return strResultVar(name, "");
  }

  default String strResultVar(String name, String defaultValue) {
    return getResult().getData().getString(name, defaultValue);
  }

  default List<String> strListResultVar(String name) {
    return getResult().getData().getStringList(name);
  }

  default List<Object> listResultVar(String name) {
    return getResult().getData().getObjectList(name);
  }

  default EasyMap mapResultVar(String name) {
    return getResult().getData().getSubArguments(name);
  }

  default List<EasyMap> mapListResultVar(String name) {
    return getResult().getData().getSubArgumentsList(name);
  }
}
