package playwell.storage.jdbc;

import java.io.Closeable;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;

/**
 * 保存系统配置的所有数据源
 *
 * @author chihongze@gmail.com
 */
public class DataSourceManager implements Closeable {

  private static final Logger logger = LogManager.getLogger(DataSourceManager.class);

  private static final DataSourceManager INSTANCE = new DataSourceManager();

  // 所有的数据源 key为数据源名称 value为数据源对象
  private final Map<String, DataSource> allDataSource = new HashMap<>();

  // 是否已经初始化
  private volatile boolean inited = false;

  private DataSourceManager() {

  }

  public static DataSourceManager getInstance() {
    return INSTANCE;
  }

  /**
   * 依据配置，初始化所有数据源
   *
   * @param configuration 数据源配置
   */
  public synchronized void init(EasyMap configuration) {
    // 已经初始化过了
    if (inited) {
      return;
    }

    final List<EasyMap> dataSourceConfigList = configuration.getSubArgumentsList(
        ConfigItems.DATASOURCE);

    if (CollectionUtils.isEmpty(dataSourceConfigList)) {
      return;
    }

    dataSourceConfigList.forEach(dataSourceConfig -> {
      final String dataSourceName = dataSourceConfig.getString("name");
      final DataSource dataSource = createBasicDataSource(dataSourceConfig);
      allDataSource.put(dataSourceName, dataSource);
    });

    this.inited = true;
  }

  public DataSource getDataSource(String name) {
    // 数据源尚未被初始化
    if (!inited) {
      throw new IllegalStateException("The DataSourceManager has not been init!");
    }

    if (!allDataSource.containsKey(name)) {
      throw new RuntimeException(String.format("Could not found the DataSource '%s'", name));
    }

    return allDataSource.get(name);
  }

  // 基于配置创建单个DataSource对象
  private DataSource createBasicDataSource(EasyMap config) {
    BasicDataSource dataSource = new BasicDataSource();

    dataSource.setDriverClassName(config.getString("driver"));
    dataSource.setUrl(config.getString("url")); // 连接url
    dataSource.setMaxTotal(config.getInt("max_active")); // 最大连接数
    dataSource.setInitialSize(config.getInt("initial_size")); // 初始连接大小
    dataSource.setMaxIdle(config.getInt("max_idle")); // 最大空闲连接
    dataSource.setMaxWaitMillis(config.getInt("max_wait")); // 超时等待时间
    dataSource.setRemoveAbandonedOnMaintenance(
        config.getBoolean("remove_abandoned")); // 在连接池周期性维护时自动回收超时连接
    dataSource.setRemoveAbandonedTimeout(config.getInt("remove_abandoned_timeout", 300));
    dataSource.setValidationQuery("select 1");
    dataSource.setTestWhileIdle(true);
    dataSource.setTimeBetweenEvictionRunsMillis(TimeUnit.MINUTES.toMillis(5));
    dataSource.setNumTestsPerEvictionRun(config.getInt("max_active"));
    dataSource.setMinEvictableIdleTimeMillis(TimeUnit.MINUTES.toMillis(30));

    return dataSource;
  }

  @Override
  public void close() {
    allDataSource.values().forEach(dataSource -> {
      try {
        ((BasicDataSource) dataSource).close();
      } catch (SQLException e) {
        logger.error(e.getMessage(), e);
      }
    });
  }

  // 配置项
  interface ConfigItems {

    // 数据源列表
    String DATASOURCE = "datasource";
  }
}
