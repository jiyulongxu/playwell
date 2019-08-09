package playwell.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Date Time Utils
 *
 * @author chihongze@gmail.com
 */
public final class DateUtils {

  // ThreadLocal for SimpleDateFormat Object
  private static final ThreadLocal<SimpleDateFormat> SDF_THREAD_LOCAL = ThreadLocal.withInitial(
      () -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

  private DateUtils() {

  }

  public static String format(Date date) {
    return SDF_THREAD_LOCAL.get().format(date);
  }

  public static Date parse(String dateText) {
    try {
      return SDF_THREAD_LOCAL.get().parse(dateText);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  public static Date parse(String dateText, String format) {
    try {
      final SimpleDateFormat sdf = new SimpleDateFormat(format);
      return sdf.parse(dateText);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }
}
