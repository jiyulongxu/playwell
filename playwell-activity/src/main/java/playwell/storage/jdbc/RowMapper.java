package playwell.storage.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * RowMapper用于将ResultSet映射成指定的对象类型
 *
 * @param <T> 目标对象类型
 * @author chihongze@gmail.com
 */
@FunctionalInterface
public interface RowMapper<T> {

  T map(ResultSet resultSet) throws SQLException;
}
