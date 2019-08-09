package playwell.util.validate;


import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import playwell.common.Mappable;
import playwell.util.Regexpr;

/**
 * 验证字段描述
 *
 * @author chihongze@gmail.com
 */
public class Field implements Mappable {

  // 字段名称
  private static final String FIELD_NAME = "name";

  // 字段在UI当中的展示名称
  private static final String FIELD_DISPLAY_NAME = "display_name";

  // 该字段是否是必须的
  private static final String FIELD_REQUIRED = "required";

  // 是否经过strip处理后再判断
  private static final String FIELD_STRIP = "strip";

  // 字段类型
  private static final String FIELD_TYPE = "type";

  // 该字段的默认值
  private static final String FIELD_DEFAULT_VALUE = "default_value";

  // 最小长度
  private static final String FIELD_MIN_LEN = "min_len";

  // 最大长度
  private static final String FIELD_MAX_LEN = "max_len";

  // 需要满足的正则表达式
  private static final String FIELD_REGEX = "regex";

  private final String name;

  private final String displayName;

  private final boolean required;

  private final boolean strip;

  private final FieldType type;

  private final String defaultValue;

  private final int minLen;

  private final int maxLen;

  private final Pattern regex;

  public Field(
      String name,
      String displayName,
      boolean required,
      boolean strip,
      FieldType type,
      String defaultValue,
      int minLen,
      int maxLen,
      Pattern regex) {
    this.name = name;
    this.displayName = displayName;
    this.required = required;
    this.strip = strip;
    this.type = type;
    this.defaultValue = defaultValue;
    this.minLen = minLen;
    this.maxLen = maxLen;
    this.regex = regex;
  }

  public static Builder builder(String name) {
    return new Builder(name);
  }

  public String getName() {
    return name;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isRequired() {
    return required;
  }

  public boolean isStrip() {
    return strip;
  }

  public FieldType getType() {
    return type;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public int getMinLen() {
    return minLen;
  }

  public int getMaxLen() {
    return maxLen;
  }

  public Pattern getRegex() {
    return regex;
  }

  /**
   * 根据规则对字段值进行验证
   */
  @SuppressWarnings({"unchecked"})
  public <T> T validate(Object value) throws InvalidFieldException {
    // Trans Object to String
    String text;
    if (value == null) {
      text = null;
    } else {
      text = value.toString();
    }

    // 对目标值进行strip
    if (text != null && this.isStrip()) {
      text = text.trim();
    }

    // 验证是否为空
    if (StringUtils.isEmpty(text)) {
      // 字段是必须的，则不允许为空
      if (this.isRequired()) {
        throw new InvalidFieldException(String.format("字段'%s'不允许为空", this.name));
      } else {
        // 非必需字段为空的话不必再检查了
        try {
          return (T) this.getType()
              .trans(this.getDefaultValue() == null ? "" : this.getDefaultValue());
        } catch (FieldTypeTransException e) {
          throw new InvalidFieldException(
              String.format("字段'%s'类型格式不正确，必须为%s类型",
                  this.getDisplayName(), this.getType().getType()));
        }
      }
    }

    // 验证长度
    if (this.getMinLen() > 0 && text.length() < this.getMinLen()) {
      throw new InvalidFieldException(
          String.format("字段'%s'长度小于最小长度%d", this.getDisplayName(), this.getMinLen()));
    }

    if (this.getMaxLen() > 0 && text.length() > this.getMaxLen()) {
      throw new InvalidFieldException(
          String.format("字段'%s'长度大于最大长度%d", this.getDisplayName(), this.getMaxLen()));
    }

    // 验证正则
    if (this.regex != null && !Regexpr.isMatch(this.regex, text)) {
      throw new InvalidFieldException(
          String.format("字段'%s'格式有误，必须满足正则'%s'", this.getDisplayName(), this.regex.pattern()));
    }

    // 最后返回最终结果
    try {
      return (T) this.getType().trans(text);
    } catch (FieldTypeTransException e) {
      throw new InvalidFieldException(
          String.format("字段'%s'类型格式不正确，必须为%s类型", this.getDisplayName(), this.getType().getType()));
    }
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.<String, Object>builder()
        .put(FIELD_NAME, this.getName())
        .put(
            FIELD_DISPLAY_NAME,
            this.getDisplayName() == null ? this.getName() : this.getDisplayName())
        .put(FIELD_REQUIRED, this.isRequired())
        .put(FIELD_DEFAULT_VALUE, this.getDefaultValue() == null ? "" : this.getDefaultValue())
        .put(FIELD_STRIP, this.isStrip())
        .put(FIELD_TYPE, this.getType().getType())
        .put(FIELD_MIN_LEN, this.getMinLen())
        .put(FIELD_MAX_LEN, this.getMaxLen())
        .put(FIELD_REGEX, this.getRegex() == null ? "" : this.getRegex().pattern())
        .build();
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this);
  }

  public static class Builder {

    private String name;

    private String displayName;

    private boolean required;

    private boolean strip;

    private FieldType type;

    private String defaultValue;

    private int minLen;

    private int maxLen;

    private Pattern regex;

    public Builder(String name) {
      this.name = name;
      this.displayName = name;
      this.required = true;
      this.strip = true;
      this.type = FieldType.Str;
      this.defaultValue = "";
      this.minLen = 0;
      this.maxLen = 0;
      this.regex = null;
    }

    public Builder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public Builder required(boolean required) {
      this.required = required;
      return this;
    }

    public Builder strip(boolean strip) {
      this.strip = strip;
      return this;
    }

    public Builder type(FieldType type) {
      this.type = type;
      return this;
    }

    public Builder defaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
      return this;
    }

    public Builder minLen(int minLen) {
      this.minLen = minLen;
      return this;
    }

    public Builder maxLen(int maxLen) {
      this.maxLen = maxLen;
      return this;
    }

    public Builder regex(String regex) {
      this.regex = Pattern.compile(regex);
      return this;
    }

    public Builder regex(Pattern regex) {
      this.regex = regex;
      return this;
    }

    public Field build() {
      return new Field(
          this.name,
          this.displayName,
          this.required,
          this.strip,
          this.type,
          this.defaultValue,
          this.minLen,
          this.maxLen,
          this.regex);
    }
  }
}
