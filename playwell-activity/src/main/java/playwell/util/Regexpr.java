package playwell.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正则表达式相关的工具
 *
 * @author chihongze@gmail.com
 */
public final class Regexpr {

  /**
   * 浮点数模式
   */
  public static final Pattern DOUBLE_NUM_PATTERN = Pattern.compile("^\\d+(\\.\\d+)?$");

  /**
   * 布尔值模式
   */
  public static final Pattern BOOLEAN_VAL_PATTERN = Pattern.compile("^true|false$");

  /**
   * Nests标识符模式
   */
  public static final Pattern NESTS_INDENTIFIER_PATTERN = Pattern.compile(
      "^[a-zA-Z_][a-zA-Z_0-9]*(\\.[a-zA-Z_][a-zA-Z_0-9]*)*$");

  // 正则表达式缓存，避免重复编译
  private static final LoadingCache<String, Pattern> patternCache = CacheBuilder
      .newBuilder()
      .maximumSize(500)
      .build(new CacheLoader<String, Pattern>() {
        public Pattern load(String key) {
          return Pattern.compile(key);
        }
      });

  private Regexpr() {

  }

  /**
   * 基于缓存，获取编译后的正则表达式
   *
   * @param patternStr 正则表达式字符串
   * @return Pattern
   */
  public static Pattern compileWithCache(String patternStr) {
    try {
      return patternCache.get(patternStr);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 判断目标字符串是否符合给定的Pattern对象
   *
   * @param patternStr 给定的正则匹配模式
   * @param text 目标字符串
   * @return 是否匹配
   */
  public static boolean isMatch(String patternStr, String text) {
    return isMatch(compileWithCache(patternStr), text);
  }

  /**
   * 判断目标字符串是否符合给定的Pattern对象
   *
   * @param pattern 给定的正则匹配模式
   * @param text 目标字符串
   * @return 是否匹配
   */
  public static boolean isMatch(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find();
  }

  /**
   * 从正则group中提取子字符串
   *
   * @param patternStr 正则表达式
   * @param text 正文
   * @param index 索引
   * @return 提取结果Optional
   */
  public static Optional<String> group(String patternStr, String text, int index) {
    return group(compileWithCache(patternStr), text, index);
  }

  /**
   * 从正则group中提取子字符串
   *
   * @param pattern 正则表达式对象
   * @param text 正文
   * @param index 索引
   * @return 提取结果Optional
   */
  public static Optional<String> group(Pattern pattern, String text, int index) {
    final Matcher matcher = pattern.matcher(text);
    if (matcher.find()) {
      return Optional.of(matcher.group(index));
    }
    return Optional.empty();
  }

  public static List<String> groupAll(String pattern, String text) {
    return groupAll(compileWithCache(pattern), text);
  }

  /**
   * 将所有的group汇总成一个列表，从group 0开始
   *
   * @param pattern 正则表达式对象
   * @param text 正文
   * @return group result list
   */
  public static List<String> groupAll(Pattern pattern, String text) {
    final Matcher matcher = pattern.matcher(text);
    if (matcher.find()) {
      final int groupCount = matcher.groupCount();
      final List<String> result = new ArrayList<>(groupCount + 1);
      for (int i = 0; i <= groupCount; i++) {
        result.add(matcher.group(i));
      }
      return result;
    }
    return Collections.emptyList();
  }

  /**
   * 替换第一个查找到的字符串
   *
   * @param patternStr 正则
   * @param text 原文
   * @param replacement 替换字符串
   * @return 替换后的文本
   */
  public static String replaceFirst(String patternStr, String text, String replacement) {
    return replaceFirst(compileWithCache(patternStr), text, replacement);
  }

  /**
   * 替换第一个查找到的字符串
   *
   * @param pattern 正则
   * @param text 原文
   * @param replacement 替换字符串
   * @return 替换后的文本
   */
  public static String replaceFirst(Pattern pattern, String text, String replacement) {
    final Matcher matcher = pattern.matcher(text);
    return matcher.replaceFirst(replacement);
  }

  /**
   * 替换所有查找到的字符串
   *
   * @param pattern 正则
   * @param text 原文
   * @param replacement 替换字符串
   * @return 替换后的文本
   */
  public static String replaceAll(Pattern pattern, String text, String replacement) {
    final Matcher matcher = pattern.matcher(text);
    return matcher.replaceAll(replacement);
  }

  /**
   * 替换所有查找到的字符串
   *
   * @param patternStr 正则
   * @param text 原文
   * @param replacement 替换字符串
   * @return 替换后的文本
   */
  public static String replaceAll(String patternStr, String text, String replacement) {
    return replaceAll(compileWithCache(patternStr), text, replacement);
  }
}
