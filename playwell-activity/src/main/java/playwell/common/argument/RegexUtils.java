package playwell.common.argument;

import java.util.List;
import java.util.Optional;
import playwell.util.Regexpr;

class RegexUtils {

  RegexUtils() {

  }

  public boolean match(String pattern, String text) {
    return Regexpr.isMatch(pattern, text);
  }

  public String group(String pattern, String text, int index) {
    final Optional<String> groupOptional = Regexpr.group(pattern, text, index);
    if (groupOptional.isPresent()) {
      return groupOptional.get();
    } else {
      throw new RuntimeException(
          String.format(
              "Could not get the group of index: %d, pattern='%s', text='%s'",
              index,
              pattern,
              text
          )
      );
    }
  }

  public List<String> groupAll(String pattern, String text) {
    return Regexpr.groupAll(pattern, text);
  }

  public String replaceFirst(String pattern, String text, String replacement) {
    return Regexpr.replaceFirst(pattern, text, replacement);
  }

  public String replaceAll(String pattern, String text, String replacement) {
    return Regexpr.replaceAll(pattern, text, replacement);
  }
}
