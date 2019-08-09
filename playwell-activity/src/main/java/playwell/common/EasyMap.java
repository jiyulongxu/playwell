package playwell.common;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import playwell.common.exception.InvalidArgumentException;
import playwell.util.Regexpr;

/**
 * <p>
 * EasyMap对象提供了一种简易的方式来操作通过Map[String, Object]传递的参数
 * </p>
 * eg.
 *
 * <pre>
 *     Map<String, Object> args = new HashMap<>();
 *     args.put("rate", "0.5");
 *     args.put("name", "Jack");
 *     args.put("ignore", true)
 *
 *     EasyMap easyMap = new EasyMap(args);
 *     double rate = easyMap.getDouble("rate");
 *     String name = easyMap.getString("name", "Sam")
 *     boolean ignore = easyMap.getBoolean("ignore")
 * </pre>
 *
 * @author chihongze@gmail.com
 */
public class EasyMap implements Mappable {

  public static final EasyMap EMPTY = new EasyMap();

  // 被包装的原始数据
  private final Map<String, Object> data;

  public EasyMap() {
    this(Collections.emptyMap());
  }

  @SuppressWarnings({"unchecked"})
  public EasyMap(Map data) {
    if (data == null) {
      data = Collections.emptyMap();
    }
    this.data = (Map<String, Object>) data;
  }

  public static final EasyMap empty() {
    return EMPTY;
  }

  public static EasyMap of(Object... args) {
    if (ArrayUtils.isEmpty(args)) {
      return new EasyMap();
    }
    final Map<String, Object> data = new HashMap<>(args.length / 2);
    for (int i = 0; i < args.length; i++) {
      data.put((String) args[i], args[++i]);
    }
    return new EasyMap(data);
  }

  public boolean contains(String key) {
    return data.containsKey(key);
  }

  public Object get(String name) {
    return this.data.get(name);
  }

  public Object get(String name, Object defaultValue) {
    return this.data.getOrDefault(name, defaultValue);
  }

  @SuppressWarnings({"unchecked"})
  public <T> T getWithConfigItem(ConfigItem configItem) {
    return (T) get(configItem.getKey(), configItem.getDefaultValue());
  }

  public int getInt(String name) {
    return _getInt(name, true, 0);
  }

  public int getInt(String name, int defaultValue) {
    return _getInt(name, false, defaultValue);
  }

  public List<Integer> getIntegerList(String name) {
    return _getObjectList(name, "Integer", value -> _transInt(name, value));
  }

  private int _getInt(String name, boolean required, int defaultValue) {
    return _getObj(name, required, defaultValue, value -> _transInt(name, value));
  }

  private int _transInt(String name, Object value) {
    if (value instanceof String && StringUtils.isNumeric((String) value)) {
      return Integer.parseInt(StringUtils.strip((String) value));
    } else if (value instanceof Number) {
      return ((Number) value).intValue();
    } else {
      throw new InvalidArgumentException(name, "Invalid type, need int", value);
    }
  }

  public long getLong(String name) {
    return _getLong(name, true, 0);
  }

  public long getLong(String name, long defaultValue) {
    return _getLong(name, false, defaultValue);
  }

  public List<Long> getLongList(String name) {
    return _getObjectList(name, "Long", value -> _transLong(name, value));
  }

  private long _getLong(String name, boolean required, long defaultValue) {
    return _getObj(name, required, defaultValue, value -> _transLong(name, value));
  }

  private long _transLong(String name, Object value) {
    if (value instanceof String && StringUtils.isNumeric((String) value)) {
      return Long.parseLong(StringUtils.strip((String) value));
    } else if (value instanceof Number) {
      return ((Number) value).longValue();
    } else {
      throw new InvalidArgumentException(name, "Invalid type, need long", value);
    }
  }

  public boolean getBoolean(String name) {
    return _getBoolean(name, true, false);
  }

  public boolean getBoolean(String name, boolean defaultValue) {
    return _getBoolean(name, false, defaultValue);
  }

  public List<Boolean> getBooleanList(String name) {
    return _getObjectList(name, "Boolean", value -> _transBoolean(name, value));
  }

  private boolean _getBoolean(String name, boolean required, boolean defaultValue) {
    return _getObj(name, required, defaultValue, value -> _transBoolean(name, value));
  }

  private boolean _transBoolean(String name, Object value) {
    if (value instanceof String && Regexpr.isMatch(Regexpr.BOOLEAN_VAL_PATTERN, (String) value)) {
      return Boolean.parseBoolean(StringUtils.strip((String) value));
    } else if (value instanceof Boolean) {
      return (Boolean) value;
    } else {
      throw new InvalidArgumentException(name, "Invalid type, need boolean", value);
    }
  }

  public double getDouble(String name) {
    return _getDouble(name, true, 0);
  }

  public double getDouble(String name, double defaultValue) {
    return _getDouble(name, false, defaultValue);
  }

  public List<Double> getDoubleList(String name) {
    return _getObjectList(name, "Double", value -> _transDouble(name, value));
  }

  private double _getDouble(String name, boolean required, double defaultValue) {
    return _getObj(name, required, defaultValue, value -> _transDouble(name, value));
  }

  private double _transDouble(String name, Object value) {
    if (value instanceof String && Regexpr.isMatch(Regexpr.DOUBLE_NUM_PATTERN, (String) value)) {
      return Double.parseDouble(StringUtils.strip((String) value));
    } else if (value instanceof Number) {
      return ((Number) value).doubleValue();
    } else {
      throw new InvalidArgumentException(name, "Invalid type, need double", value);
    }
  }

  public BigDecimal getDecimal(String name) {
    return _getDecimal(name, true, 0);
  }

  public BigDecimal getDecimal(String name, Object defaultValue) {
    return _getDecimal(name, false, defaultValue);
  }

  public List<BigDecimal> getDecimalList(String name) {
    return _getObjectList(name, "BigDecimal", value -> _transDecimal(name, value));
  }

  private BigDecimal _getDecimal(String name, boolean required, Object defaultValue) {
    return _getObjWithDefaultGenerator(
        name,
        required,
        () -> _transDecimal(name, defaultValue),
        value -> _transDecimal(name, value)
    );
  }

  private BigDecimal _transDecimal(String name, Object value) {
    if (value instanceof BigDecimal) {
      return (BigDecimal) value;
    } else if (value instanceof String) {
      return new BigDecimal((String) value);
    } else if (value instanceof Integer) {
      return new BigDecimal((Integer) value);
    } else if (value instanceof Long) {
      return new BigDecimal((Long) value);
    } else if (value instanceof Float) {
      return new BigDecimal((Float) value);
    } else if (value instanceof Double) {
      return new BigDecimal((Double) value);
    } else {
      throw new InvalidArgumentException(name, "Trans decimal failure", value);
    }
  }

  public String getString(String name) {
    return _getString(name, true, "");
  }

  public String getString(String name, String defaultValue) {
    return _getString(name, false, defaultValue);
  }

  public List<String> getStringList(String name) {
    return _getObjectList(name, "String", this::_transString);
  }

  private String _getString(String name, boolean required, String defaultValue) {
    return _getObj(name, required, defaultValue, this::_transString);
  }

  private String _transString(Object value) {
    return value == null ? null : value.toString();
  }

  public EasyMap getSubArguments(String name) {
    return _getObj(
        name, false, new EasyMap(Collections.emptyMap()), value -> _transArguments(name, value));
  }

  public List<EasyMap> getSubArgumentsList(String name) {
    return _getObjectList(name, "Map", value -> _transArguments(name, value));
  }

  public List<Object> getObjectList(String name) {
    return _getObjectList(name, "Object", Function.identity());
  }

  @SuppressWarnings({"unchecked"})
  private EasyMap _transArguments(String name, Object value) {
    if (value instanceof Map) {
      return new EasyMap((Map<String, Object>) value);
    }
    throw new InvalidArgumentException(name, "Invalid type, need easyMap map", value);
  }

  public Map<String, Object> toMap() {
    return this.data;
  }

  private <T> T _getObjWithDefaultGenerator(
      String name,
      boolean required,
      Supplier<T> defaultValueGenerator, Function<Object, T> transfer) {
    final Object value = data.get(name);

    if (value == null) {
      if (required) {
        throw new InvalidArgumentException(name, "This argument must be required", null);
      } else {
        return defaultValueGenerator.get();
      }
    }

    return transfer.apply(value);
  }

  private <T> T _getObj(String name, boolean required, T defaultValue,
      Function<Object, T> transfer) {
    final Object value = data.get(name);

    if (value == null) {
      if (required) {
        throw new InvalidArgumentException(name, "This argument must be required", null);
      } else {
        return defaultValue;
      }
    }

    return transfer.apply(value);
  }

  @SuppressWarnings({"unchecked"})
  private <T> List<T> _getObjectList(String name, String type, Function<Object, T> transfer) {
    return _getObj(name, false, Collections.emptyList(), value -> {
      if (value instanceof List) {
        List<Object> objectList = (List<Object>) value;
        return objectList.stream().map(transfer).collect(Collectors.toList());
      }
      throw new InvalidArgumentException(name, String.format("Invalid type, need List[%s]", type),
          value);
    });
  }

  public int size() {
    return this.data.size();
  }

  public boolean isEmpty() {
    return MapUtils.isEmpty(this.data);
  }

  @Override
  public String toString() {
    return String.format("Argument@%d{%s}", System.identityHashCode(this), this.data.toString());
  }
}
