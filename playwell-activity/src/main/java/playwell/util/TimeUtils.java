package playwell.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.collections4.map.CaseInsensitiveMap;

/**
 * 时间表达式解析工具
 *
 * @author chihongze@gmail.com
 */
public class TimeUtils {

  private static final long SECOND = 1000;

  private static final long MINUTE = SECOND * 60;

  private static final long HOUR = MINUTE * 60;

  private static final long DAY = HOUR * 24;

  private static final Map<String, Long> timeDeltaMap =
      new CaseInsensitiveMap<String, Long>() {
        {
          put("second", SECOND);
          put("seconds", SECOND);

          put("minute", MINUTE);
          put("minutes", MINUTE);

          put("hour", HOUR);
          put("hours", HOUR);

          put("day", DAY);
          put("days", DAY);
        }
      };

  private static final Pattern TIME_DESC_PATTERN =
      Pattern.compile("^(in\\s+)*(\\d+)\\s+(day|days|hour|hours|minute|minutes|second|seconds)$");

  private TimeUtils() {
  }

  /**
   * 从字符串的时间描述中获取时间差值的毫秒时间戳表示
   */
  public static long getTimeDeltaFromDesc(String timeDesc) {
    Matcher m = TIME_DESC_PATTERN.matcher(timeDesc);

    if (!m.find()) {
      throw new RuntimeException(
          String.format("The timestamp expression is invalid: %s", timeDesc));
    }

    long num = Long.parseLong(m.group(2));
    String unit = m.group(3);

    return num * timeDeltaMap.get(unit);
  }
}
