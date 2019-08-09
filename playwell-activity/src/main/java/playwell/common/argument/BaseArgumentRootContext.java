package playwell.common.argument;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import playwell.action.ActionCtrlType;
import playwell.clock.CachedTimestamp;
import playwell.util.Regexpr;
import playwell.util.TimeUtils;
import spark.utils.CollectionUtils;


/**
 * 参数渲染上下文基类，提供了公共的参数渲染常量和函数
 */
public abstract class BaseArgumentRootContext {

  public static final String all = "all";

  public static final RegexUtils regex = new RegexUtils();

  public static final ListUtils list = new ListUtils();

  public static final MapUtils map = new MapUtils();

  public static final CounterUtils counter = new CounterUtils();

  public static Math math;

  static {
    try {
      Constructor mathConstructor = Math.class.getDeclaredConstructor();
      mathConstructor.setAccessible(true);
      math = (Math) mathConstructor.newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected BaseArgumentRootContext() {

  }

  // bool function
  public static boolean all(Boolean... values) {
    for (Boolean val : values) {
      if (val == null || !val) {
        return false;
      }
    }
    return true;
  }

  public static boolean any(Boolean... values) {
    for (Boolean val : values) {
      if (val != null && val) {
        return true;
      }
    }
    return false;
  }

  public static String str(String string) {
    if (StringUtils.isEmpty(string)) {
      return "";
    }
    return string;
  }

  public static String str(String string, Object... args) {
    return String.format(string, args);
  }

  public static long timestamp(String desc) {
    return TimeUtils.getTimeDeltaFromDesc(desc);
  }

  public static long timestamp() {
    return CachedTimestamp.nowMilliseconds();
  }

  public static long seconds(long seconds) {
    return TimeUnit.SECONDS.toMillis(seconds);
  }

  public static long minutes(long minutes) {
    return TimeUnit.MINUTES.toMillis(minutes);
  }

  public static long hours(long hours) {
    return TimeUnit.HOURS.toMillis(hours);
  }

  public static long days(long days) {
    return TimeUnit.DAYS.toMillis(days);
  }

  public static List<Integer> range(int end) {
    return range(0, end);
  }

  public static List<Integer> range(int begin, int end) {
    return IntStream.range(begin, end).boxed().collect(Collectors.toList());
  }

  public static double random() {
    return Math.random();
  }

  public static int randInt(int bound) {
    return ThreadLocalRandom.current().nextInt(bound);
  }

  public static int randInt(int origin, int bound) {
    return ThreadLocalRandom.current().nextInt(origin, bound);
  }

  public static boolean randomBoolean(double rate) {
    return Math.random() < rate;
  }

  /**
   * new一个新列表
   *
   * @param args 新列表初始元素
   * @return 新列表对象
   */
  public static List<Object> list(Object... args) {
    if (args == null || args.length == 0) {
      return Collections.emptyList();
    }
    return Arrays.asList(args);
  }

  //////////// 类型转换函数 ///////////

  public static Map<Object, Object> map(Object... args) {
    if (ArrayUtils.isEmpty(args)) {
      return Collections.emptyMap();
    }

    if (args.length % 2 != 0) {
      throw new IllegalArgumentException("Invalid argument num, must be even!");
    }

    final Map<Object, Object> map = new HashMap<>(args.length / 2);

    for (int i = 0; i < args.length - 1; i += 2) {
      map.put(args[i], args[i + 1]);
    }

    return map;
  }

  public static Map<Object, Long> counter(Object... args) {
    return list.groupCount(list(args));
  }

  public static List<String> split(String text, String delimiter) {
    if (StringUtils.isEmpty(text)) {
      return Collections.emptyList();
    }

    return Arrays.asList(StringUtils.split(text, delimiter));
  }

  public static int toInt(Object value) {
    if (value instanceof String && StringUtils.isNumeric((String) value)) {
      return Integer.parseInt(StringUtils.strip((String) value));
    } else if (value instanceof Number) {
      return ((Number) value).intValue();
    } else {
      throw new RuntimeException(String.format("The value %s could not trans to int.", value));
    }
  }

  public static long toLong(Object value) {
    if (value instanceof String && StringUtils.isNumeric((String) value)) {
      return Long.parseLong(StringUtils.strip((String) value));
    } else if (value instanceof Number) {
      return ((Number) value).longValue();
    } else {
      throw new RuntimeException(String.format("The value %s could not trans to long", value));
    }
  }

  public static boolean toBoolean(Object value) {
    if (value instanceof String && Regexpr.isMatch(Regexpr.BOOLEAN_VAL_PATTERN, (String) value)) {
      return Boolean.parseBoolean(StringUtils.strip((String) value));
    } else if (value instanceof Boolean) {
      return (Boolean) value;
    } else {
      throw new RuntimeException(String.format("The value %s could not trans to boolean", value));
    }
  }

  public static double toDouble(Object value) {
    if (value instanceof String && Regexpr.isMatch(Regexpr.DOUBLE_NUM_PATTERN, (String) value)) {
      return Double.parseDouble(StringUtils.strip((String) value));
    } else if (value instanceof Number) {
      return ((Number) value).doubleValue();
    } else {
      throw new RuntimeException(String.format("The value %s could not trans to double", value));
    }
  }

  public static String toStr(Object value) {
    if (value == null) {
      return "null";
    } else if (value instanceof String) {
      return (String) value;
    } else {
      return value.toString();
    }
  }

  public static BigDecimal toDecimal(Object value) {
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
      throw new RuntimeException(String.format("The value %s could not trans to decimal", value));
    }
  }

  public static BigDecimal toDecimal(Object value, int scale, String roundingMode) {
    final BigDecimal result = toDecimal(value);
    return result.setScale(scale, roundingMode(roundingMode));
  }

  private static RoundingMode roundingMode(String roundingMode) {
    return RoundingMode.valueOf(roundingMode);
  }

  public static DateTime dateTime() {
    return new DateTime(CachedTimestamp.nowMilliseconds());
  }

  public static DateTime dateTime(long timestamp) {
    return new DateTime(timestamp);
  }

  public static DateTime dateTime(String dateText) {
    return dateTime(dateText, "yyyy-MM-dd HH:mm:ss");
  }

  public static DateTime dateTime(String dateText, String format) {
    return DateTime.parse(dateText, DateTimeFormat.forPattern(format));
  }

  public static int year() {
    return new DateTime(CachedTimestamp.nowMilliseconds()).getYear();
  }

  public static int month() {
    return new DateTime(CachedTimestamp.nowMilliseconds()).getMonthOfYear();
  }

  public static int day() {
    return new DateTime(CachedTimestamp.nowMilliseconds()).getDayOfMonth();
  }

  public static int hour() {
    return new DateTime(CachedTimestamp.nowMilliseconds()).getHourOfDay();
  }

  public static int minute() {
    return new DateTime(CachedTimestamp.nowMilliseconds()).getMinuteOfHour();
  }

  public static int second() {
    return new DateTime(CachedTimestamp.nowMilliseconds()).getSecondOfMinute();
  }

  public static LocalDate localDate() {
    return new LocalDate();
  }

  public static LocalDate localDate(String desc) {
    return localDate(desc, "yyyy-MM-dd");
  }

  public static LocalDate localDate(String desc, String format) {
    return LocalDate.parse(desc, DateTimeFormat.forPattern(format));
  }

  public static LocalTime localTime() {
    return new LocalTime();
  }

  public static LocalTime localTime(String desc) {
    return LocalTime.parse(desc);
  }

  public static LocalTime localTime(String desc, String format) {
    return LocalTime.parse(desc, DateTimeFormat.forPattern(format));
  }

  public static boolean isLocalTimeInScope(String desc, List<String> scope) {
    final LocalTime localTime = LocalTime.parse(desc);
    final LocalTime beginTime = LocalTime.parse(scope.get(0));
    final LocalTime endTime = LocalTime.parse(scope.get(1));
    return localTime.isAfter(beginTime) && localTime.isBefore(endTime);
  }

  public static Condition when(boolean condition) {
    return new Condition(condition);
  }

  public String call(String actionName) {
    return ActionCtrlType.CALL.getType() + " " + actionName;
  }

  public String fail() {
    return ActionCtrlType.FAIL.getType();
  }

  public String failBecause(String reason) {
    return ActionCtrlType.FAIL.getType() + " because " + reason;
  }

  public String retry(int count) {
    return ActionCtrlType.RETRY.getType() + " " + count;
  }

  public String retry(int count, String retryFailureAction) {
    return ActionCtrlType.RETRY.getType() + " " + count + " " + retryFailureAction;
  }

  public String repair(String problem) {
    return ActionCtrlType.REPAIRING.getType() + " " + problem;
  }

  public String finish() {
    return ActionCtrlType.FINISH.getType();
  }

  public boolean isEmpty(Object obj) {
    if (obj == null) {
      return true;
    }

    if (obj instanceof String) {
      return StringUtils.isEmpty((String) obj);
    }

    if (obj instanceof Collection) {
      return CollectionUtils.isEmpty((Collection) obj);
    }

    if (obj instanceof Map) {
      return org.apache.commons.collections4.MapUtils.isEmpty((Map) obj);
    }

    return false;
  }

  public boolean isNotEmpty(Object obj) {
    return !isEmpty(obj);
  }

  static class Condition {

    private boolean condition;

    private Object value;

    Condition(boolean condition) {
      this.condition = condition;
    }

    public Condition when(boolean condition) {
      this.condition = condition;
      return this;
    }

    public Condition then(Object value) {
      if (condition && this.value == null) {
        this.value = value;
      }
      return this;
    }

    public Object get() {
      return value;
    }

    public Object get(Object defaultValue) {
      return value == null ? defaultValue : value;
    }
  }
}
