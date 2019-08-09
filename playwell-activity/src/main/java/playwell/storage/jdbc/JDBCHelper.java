package playwell.storage.jdbc;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;

/**
 * JDBCHelper用于辅助方便操作JDBC数据源
 *
 * @author chihongze@gmail.com
 */
public final class JDBCHelper {

  private JDBCHelper() {

  }

  /**
   * 查询列表，并映射成目标对象
   *
   * @param dataSourceName 数据源名称
   * @param sql SQL语句
   * @param rowMapper RowMapper
   * @param sqlParams SQL参数
   * @param <T> 结果对象类型
   * @return 由结果对象组成的类型
   */
  public static <T> List<T> queryList(
      String dataSourceName, String sql, RowMapper<T> rowMapper, Object... sqlParams) {
    return JDBCHelper.query(
        dataSourceName,
        sql,
        rs -> {
          List<T> resultList = new ArrayList<>();
          while (rs.next()) {
            resultList.add(rowMapper.map(rs));
          }
          return resultList;
        },
        sqlParams);
  }

  /**
   * 查询单条记录，并映射成目标对象
   *
   * @param dataSourceName 数据源名称
   * @param sql 只返回一行记录的SELECT语句
   * @param rowMapper RowMapper
   * @param sqlParams SQL参数
   * @param <T> 返回结果对象类型
   * @return 目标结果对象
   */
  public static <T> Optional<T> queryOne(
      String dataSourceName, String sql, RowMapper<T> rowMapper, Object... sqlParams) {
    return JDBCHelper.query(
        dataSourceName,
        sql,
        rs -> {
          if (rs.next()) {
            return Optional.of(rowMapper.map(rs));
          }
          return Optional.empty();
        },
        sqlParams);
  }

  /**
   * 查询指定的单个字段
   *
   * @param dataSourceName 数据源名称
   * @param sql 只查询单个字段并且只返回一行的SELECT语句
   * @param fieldName 字段名称
   * @param fieldType 字段类型
   * @param sqlParams SQL参数
   * @param <T> 字段类型
   * @return 要查询的单个字段值
   */
  public static <T> Optional<T> queryOneField(
      String dataSourceName,
      String sql,
      String fieldName,
      Class<T> fieldType,
      Object... sqlParams) {
    return JDBCHelper.query(
        dataSourceName,
        sql,
        rs -> {
          if (rs.next()) {
            return Optional.of(rs.getObject(fieldName, fieldType));
          }
          return Optional.empty();
        },
        sqlParams);
  }

  /**
   * 查询由单个字段所组成的列表
   *
   * @param dataSourceName 数据源名称
   * @param sql 只查询单个字段的SELECT语句
   * @param fieldName 字段名称
   * @param fieldType 字段类型
   * @param sqlParams SQL参数
   * @param <T> 字段类型
   * @return 要查询的单字段列表
   */
  public static <T> List<T> queryFieldList(
      String dataSourceName,
      String sql,
      String fieldName,
      Class<T> fieldType,
      Object... sqlParams) {
    return JDBCHelper.query(
        dataSourceName,
        sql,
        rs -> {
          List<T> resultList = new ArrayList<>();
          while (rs.next()) {
            resultList.add(rs.getObject(fieldName, fieldType));
          }
          return resultList;
        },
        sqlParams);
  }

  /**
   * 执行SELECT查询
   *
   * @param dataSourceName 数据源名称
   * @param sql SQL语句
   * @param handler 结果处理器
   * @param sqlParams SQL参数
   * @param <T> 返回查询对象类型
   * @return 查询对象
   */
  public static <T> T query(
      String dataSourceName, String sql, ResultSetHandler<T> handler, Object... sqlParams) {
    DataSource dataSource = getDataSource(dataSourceName);
    QueryRunner runner = new QueryRunner(dataSource);
    try {
      return runner.query(sql, handler, sqlParams);
    } catch (SQLException e) {
      // rethrow sql exception with runtime
      throw new RuntimeException(e);
    }
  }

  /**
   * 执行更新性质的SQL语句，比如UPDATE或者DELETE
   *
   * @param dataSourceName 数据源名称
   * @param sql SQL语句
   * @param sqlParams SQL参数
   * @return 修改行数
   */
  public static long execute(String dataSourceName, String sql, Object... sqlParams) {
    DataSource dataSource = getDataSource(dataSourceName);
    QueryRunner runner = new QueryRunner(dataSource);
    try {
      return runner.update(sql, sqlParams);
    } catch (SQLException e) {
      // rethrow sql exception with runtime
      throw new RuntimeException(e);
    }
  }

  /**
   * 插入，并获得所插入记录的主键
   *
   * @param dataSourceName 数据源名称
   * @param sql 要执行的SQL语句
   * @param sqlParams SQL参数
   * @return 主键ID
   */
  public static long insertAndGetId(String dataSourceName, String sql, Object... sqlParams) {
    DataSource dataSource = getDataSource(dataSourceName);
    QueryRunner runner = new QueryRunner(dataSource);
    try {
      return runner.insert(
          sql,
          rs -> {
            if (rs.next()) {
              return rs.getLong(1);
            }
            return 0L;
          },
          sqlParams);
    } catch (SQLException e) {
      // rethrow sql exception with runtime
      throw new RuntimeException(e);
    }
  }

  /**
   * 针对一条SQL语句，批量应用参数执行
   *
   * @param dataSourceName 数据源名称
   * @param sql SQL语句
   * @param sqlParams 由二维数组构成的SQL参数
   * @return 批量执行所修改的条数
   */
  public static int[] batchExecute(String dataSourceName, String sql, Object[][] sqlParams) {
    DataSource dataSource = getDataSource(dataSourceName);
    QueryRunner runner = new QueryRunner(dataSource);
    try {
      return runner.batch(sql, sqlParams);
    } catch (SQLException e) {
      // rethrow sql exception with runtime
      throw new RuntimeException(e);
    }
  }

  /**
   * 展开SQL参数中的集合
   *
   * @param args SQL参数
   * @return 展开之后的结果
   */
  @SuppressWarnings({"unchecked"})
  public static Object[] flattenSQLArgs(Object... args) {
    List<Object> argList = new ArrayList<>(args.length * 2);
    for (Object arg : args) {
      if (arg instanceof Collection) {
        argList.addAll((Collection<Object>) arg);
      } else {
        argList.add(arg);
      }
    }
    return argList.toArray();
  }

  private static DataSource getDataSource(String dataSourceName) {
    return DataSourceManager.getInstance().getDataSource(dataSourceName);
  }
}
