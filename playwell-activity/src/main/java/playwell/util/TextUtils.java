package playwell.util;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * StringUtils
 */
public final class TextUtils {

  private TextUtils() {

  }

  /**
   * 将split结果过滤掉空字符串
   *
   * @param text Text
   * @param character 分隔符
   * @return 过滤结果
   */
  public static List<String> splitWithoutEmpty(String text, Character character) {
    text = StringUtils.stripToEmpty(text);
    final String[] tokens = StringUtils.split(text, character);
    final List<String> tokenList = new ArrayList<>();
    for (String token : tokens) {
      token = StringUtils.strip(token);
      if (StringUtils.isEmpty(token)) {
        continue;
      }
      tokenList.add(token);
    }
    return tokenList;
  }
}
