package playwell.util.validate;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 字段类型枚举
 *
 * @author chihongze@gmail.com
 */
public enum FieldType {

  // 整形
  Int("int") {
    @Override
    Integer trans(String text) throws FieldTypeTransException {
      try {
        return Integer.parseInt(text);
      } catch (Exception e) {
        throw new FieldTypeTransException(e.getMessage(), e);
      }
    }
  },

  // 长整形
  LongInt("long") {
    @Override
    Long trans(String text) throws FieldTypeTransException {
      try {
        return Long.parseLong(text);
      } catch (Exception e) {
        throw new FieldTypeTransException(e.getMessage(), e);
      }
    }
  },

  // 字符串
  Str("str") {
    @Override
    String trans(String text) {
      return text;
    }
  },

  // 布尔型
  Bool("bool") {
    @Override
    Object trans(String text) throws FieldTypeTransException {
      try {
        return Boolean.parseBoolean(text);
      } catch (Exception e) {
        throw new FieldTypeTransException(e.getMessage(), e);
      }
    }
  },

  // 浮点类型
  Double("double") {
    @Override
    Object trans(String text) throws FieldTypeTransException {
      try {
        return java.lang.Double.parseDouble(text);
      } catch (Exception e) {
        throw new FieldTypeTransException(e.getMessage(), e);
      }
    }
  };

  private static final Map<String, FieldType> types =
      Arrays.stream(values()).collect(Collectors.toMap(FieldType::getType, Function.identity()));

  String type;

  FieldType(String type) {
    this.type = type;
  }

  public static Optional<FieldType> valueOfByType(String type) {
    return Optional.ofNullable(types.get(type));
  }

  public String getType() {
    return this.type;
  }

  abstract Object trans(String text) throws FieldTypeTransException;
}
