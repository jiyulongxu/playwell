package playwell.message.domainid;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.CompareAndCallback;
import playwell.common.EasyMap;
import playwell.common.MySQLCompareAndCallback;
import playwell.common.Result;
import playwell.message.MessageDispatcherListener;
import playwell.storage.jdbc.JDBCHelper;

/**
 * 基于MySQL存储的MessageDomainIDStrategyManager 每次刷新的时候从数据库加载最新的MessageDomainIDStrategy
 */
public class MySQLMessageDomainIDStrategyManager implements MessageDomainIDStrategyManager,
    MessageDispatcherListener {

  private static final Logger logger = LogManager
      .getLogger(MySQLMessageDomainIDStrategyManager.class);

  private static final String COMPARE_AND_CALLBACK_ITEM = "domain_id_strategy";

  // Data Access Object
  private DataAccess dataAccess;

  // Compare and callback updater
  private CompareAndCallback updater;

  // Compare and callback expected version
  private int expectedVersion = 0;

  private Collection<MessageDomainIDStrategy> allStrategies = Collections.emptyList();

  public MySQLMessageDomainIDStrategyManager() {

  }

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    final String dataSource = configuration.getString(ConfigItems.DATASOURCE);
    this.dataAccess = new DataAccess(dataSource);
    this.updater = new MySQLCompareAndCallback(dataSource, COMPARE_AND_CALLBACK_ITEM);
    this.beforeLoop();
  }

  @Override
  public Result addMessageDomainIDStrategy(String name, String condExpr, String domainIdExpr) {
    if (dataAccess.isExisted(name)) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.ALREADY_EXISTED,
          String.format("The domain id strategy '%s' already existed", name)
      );
    }

    try {
      final MessageDomainIDStrategy domainIDStrategy = new ExpressionMessageDomainIDStrategy();
      domainIDStrategy.init(new EasyMap(ImmutableMap.of(
          ExpressionMessageDomainIDStrategy.ConfigItems.NAME,
          name,
          ExpressionMessageDomainIDStrategy.ConfigItems.COND,
          condExpr,
          ExpressionMessageDomainIDStrategy.ConfigItems.DOMAIN_ID,
          domainIdExpr
      )));
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      return Result.failWithCodeAndMessage(ErrorCodes.SERVICE_ERROR, e.getMessage());
    }

    dataAccess.insert(name, condExpr, domainIdExpr);
    updater.updateVersion();
    return Result.ok();
  }

  @Override
  public Result removeMessageDomainIDStrategy(String name) {
    final long changedRow = dataAccess.delete(name);
    if (changedRow > 0) {
      updater.updateVersion();
      return Result.ok();
    } else {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND,
          String.format("Domain id strategy not found: '%s'", name)
      );
    }
  }

  @Override
  public Collection<MessageDomainIDStrategy> getAllMessageDomainIDStrategies() {
    return allStrategies;
  }

  @Override
  public void beforeLoop() {
    expectedVersion = updater.compareAndCallback(
        expectedVersion, () -> {
          logger.info("Refreshing MySQLMessageDomainIDStrategyManager...");
          this.allStrategies = dataAccess.getAll();
          logger.info("MySQLMessageDomainIDStrategyManager refreshed");
        });
  }

  // 清理所有策略，仅供测试使用
  public void removeAll() {
    dataAccess.truncate();
  }

  interface ConfigItems {

    String DATASOURCE = "datasource";
  }

  private static class DataAccess {

    private final String dataSource;

    DataAccess(String dataSource) {
      this.dataSource = dataSource;
    }

    void insert(String name, String condExpr, String domainIdExpr) {
      final String SQL = "INSERT INTO `domain_id_strategy` (name, cond_expr, domain_id_expr, "
          + "updated_on, created_on) VALUES (?, ?, ?, ?, ?)";
      final Date now = new Date();
      JDBCHelper.execute(
          dataSource,
          SQL,
          name,
          condExpr,
          domainIdExpr,
          now,
          now
      );
    }

    long delete(String name) {
      final String SQL = "DELETE FROM `domain_id_strategy` WHERE `name` = ?";
      return JDBCHelper.execute(dataSource, SQL, name);
    }

    Collection<MessageDomainIDStrategy> getAll() {
      final String SQL = "SELECT `name`, `cond_expr`, `domain_id_expr` FROM `domain_id_strategy`";
      return JDBCHelper.queryList(
          dataSource,
          SQL,
          resultSet -> {
            ExpressionMessageDomainIDStrategy messageDomainIDStrategy = new ExpressionMessageDomainIDStrategy();
            messageDomainIDStrategy.init(new EasyMap(ImmutableMap.of(
                ExpressionMessageDomainIDStrategy.ConfigItems.NAME,
                resultSet.getString("name"),
                ExpressionMessageDomainIDStrategy.ConfigItems.COND,
                resultSet.getString("cond_expr"),
                ExpressionMessageDomainIDStrategy.ConfigItems.DOMAIN_ID,
                resultSet.getString("domain_id_expr")
            )));
            return messageDomainIDStrategy;
          }
      );
    }

    boolean isExisted(String name) {
      final String SQL = "SELECT `name` FROM `domain_id_strategy` WHERE `name` = ?";
      return JDBCHelper.queryOneField(
          dataSource,
          SQL,
          "name",
          String.class,
          name
      ).isPresent();
    }

    void truncate() {
      JDBCHelper.execute(dataSource, "TRUNCATE `domain_id_strategy`");
    }
  }
}
