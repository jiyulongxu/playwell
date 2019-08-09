package playwell.common;

import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.storage.jdbc.DBField;
import playwell.storage.jdbc.JDBCHelper;

/**
 * 基于MySQL存储实现的CompareAndCallback行为
 *
 * @author chihongze
 */
public class MySQLCompareAndCallback implements CompareAndCallback {

  private static final Logger logger = LogManager.getLogger(MySQLCompareAndCallback.class);

  // 数据源
  private final String dataSource;

  // 更新项
  private final String item;

  // 上次打印异常的时间，每隔10s打印一次异常，防止频繁打印
  private long lastOutputExceptionTime = 0L;

  public MySQLCompareAndCallback(String dataSource, String item) {
    this.dataSource = dataSource;
    this.item = item;
  }

  /**
   * Steps:
   * <ol>
   * <li>从MySQL中获取当前的版本号</li>
   * <li>比较expectedVersion和当前版本号</li>
   * <li>如果从MySQL中获取的版本号为空或者不相同，则回调callback</li>
   * <li>如果相同，则直接返回</li>
   * </ol>
   *
   * @param expectedVersion Expected version
   * @param callback callback logic
   * @return 数据库中最新的版本号，如果数据库中没有，则返回0
   */
  @Override
  public int compareAndCallback(int expectedVersion, Runnable callback) {
    final String sql = "SELECT `version`, (`version` = ?) AS expected FROM `compare_and_callback` "
        + "WHERE `item` = ? LIMIT 1";

    try {
      final Optional<Pair<Integer, Boolean>> compareResultOptional = JDBCHelper.queryOne(
          dataSource,
          sql,
          rs -> Pair.of(
              rs.getInt(Field.VERSION.name),
              rs.getBoolean("expected")
          ),
          expectedVersion,
          item
      );

      if (compareResultOptional.isPresent()) {
        Pair<Integer, Boolean> compareResult = compareResultOptional.get();
        int version = compareResult.getLeft();
        boolean expected = compareResult.getRight();
        if (!expected) {
          callback.run();
        }
        return version;
      }
      return 0;
    } catch (Exception e) {
      // Cache住异常，这样在MySQL崩溃的时候可以继续用内存中的版本继续工作
      if (System.currentTimeMillis() - this.lastOutputExceptionTime >= 10000) {
        this.lastOutputExceptionTime = System.currentTimeMillis();
        logger.error(e.getMessage(), e);
      }
    }

    return expectedVersion;
  }

  @Override
  public void updateVersion() {
    final String sql = "INSERT INTO `compare_and_callback` (item, version, updated_on) "
        + "VALUES (?, 1, now()) "
        + "ON DUPLICATE KEY UPDATE `version` = `version` + 1, updated_on = now()";
    JDBCHelper.execute(
        dataSource,
        sql,
        item
    );
  }

  // Don't touch! Only for unit test case
  public void removeItem(String item) {
    JDBCHelper.execute(
        dataSource,
        "DELETE FROM `compare_and_callback` WHERE `item` = ? LIMIT 1",
        item
    );
  }

  // 数据库字段
  enum Field implements DBField {

    ID("id", true),

    ITEM("item"),

    VERSION("version"),

    UPDATED_ON("updated_on"),

    ;

    private final String name;

    private final boolean primaryKey;

    Field(String name) {
      this(name, false);
    }

    Field(String name, boolean primaryKey) {
      this.name = name;
      this.primaryKey = primaryKey;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public boolean isPrimaryKey() {
      return primaryKey;
    }
  }
}
