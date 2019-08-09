package playwell.storage.jdbc;


import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;


/**
 * 数据库字段描述
 *
 * @author chihongze
 */
public interface DBField {

  /**
   * 获取所有插入字段的拼接，会过滤掉主键
   */
  static String joinInsertFields(DBField... fields) {
    return StringUtils.join(Arrays.stream(fields)
        .filter(f -> !f.isPrimaryKey()).map(DBField::getName)
        .collect(Collectors.toList()), ", ");
  }

  /**
   * 获取所有字段的拼接，不会过滤掉主键
   */
  static String joinAllFields(DBField... fields) {
    return StringUtils.join(Arrays.stream(fields)
        .map(DBField::getName)
        .collect(Collectors.toList()), ", ");
  }

  /**
   * 生成参数占位符
   */
  static String joinPlaceHolders(int num) {
    return StringUtils.join(IntStream.range(0, num).mapToObj(i -> "?")
        .collect(Collectors.toList()), ", ");
  }

  String getName();

  boolean isPrimaryKey();
}
