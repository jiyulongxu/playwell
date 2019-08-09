package playwell.common.argument;

import java.util.List;
import playwell.common.EasyMap;

/**
 * 为提供配置变量的参数表达式提供便捷访问接口
 */
public interface ConfigVarAccessMixin {

  EasyMap getConfig();

  default Object config(String name) {
    final EasyMap config = getConfig();
    if (!config.contains(name)) {
      throw new RuntimeException(String.format("Could not found the config item: %s", name));
    }
    return config.get(name);
  }

  default Object config(String name, Object defaultValue) {
    return getConfig().get(name, defaultValue);
  }

  default int intConfig(String name) {
    return intConfig(name, 0);
  }

  default int intConfig(String name, int defaultValue) {
    return getConfig().getInt(name, defaultValue);
  }

  default List<Integer> intListConfig(String name) {
    return getConfig().getIntegerList(name);
  }

  default boolean boolConfig(String name) {
    return boolConfig(name, false);
  }

  default boolean boolConfig(String name, boolean defaultValue) {
    return getConfig().getBoolean(name, defaultValue);
  }

  default List<Boolean> boolListConfig(String name) {
    return getConfig().getBooleanList(name);
  }

  default double doubleConfig(String name) {
    return doubleConfig(name, 0.0);
  }

  default double doubleConfig(String name, double defaultValue) {
    return getConfig().getDouble(name, defaultValue);
  }

  default List<Double> doubleListConfig(String name) {
    return getConfig().getDoubleList(name);
  }

  default String strConfig(String name) {
    return strConfig(name, "");
  }

  default String strConfig(String name, String defaultValue) {
    return getConfig().getString(name, defaultValue);
  }

  default List<String> strListConfig(String name) {
    return getConfig().getStringList(name);
  }

  default List<Object> listConfig(String name) {
    return getConfig().getObjectList(name);
  }

  default EasyMap mapConfig(String name) {
    return getConfig().getSubArguments(name);
  }

  default List<EasyMap> mapListConfig(String name) {
    return getConfig().getSubArgumentsList(name);
  }
}
