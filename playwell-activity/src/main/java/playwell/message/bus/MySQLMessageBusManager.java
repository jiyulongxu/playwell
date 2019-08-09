package playwell.message.bus;

import com.alibaba.fastjson.JSONObject;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.CompareAndCallback;
import playwell.common.EasyMap;
import playwell.common.MySQLCompareAndCallback;
import playwell.common.Result;
import playwell.message.MessageDispatcherListener;
import playwell.storage.jdbc.DBField;
import playwell.storage.jdbc.JDBCHelper;
import playwell.storage.jdbc.RowMapper;

/**
 * 基于MySQL存储的MessageBusManager
 *
 * @author chihongze@gmail.com
 */
public class MySQLMessageBusManager extends BaseMessageBusManager implements
    MessageDispatcherListener {

  private static final Logger logger = LogManager.getLogger(MySQLMessageBusManager.class);

  private final Map<String, MessageBus> allPersistMessageBus = new ConcurrentHashMap<>();

  private DataAccess dataAccess;

  private CompareAndCallback updater;

  // Compare and callback expected version
  private int expectedVersion = 0;

  /**
   * 初始化 从配置buses项目中加载MessageBus配置 在数据库中更新其选项并打开它们
   *
   * @param config Listener配置数据
   */
  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    this.dataAccess = new DataAccess(configuration.getString(ConfigItems.DATASOURCE));
    this.updater = new MySQLCompareAndCallback(
        configuration.getString(ConfigItems.DATASOURCE),
        "message_bus"
    );

    final List<EasyMap> busConfigList = configuration.getSubArgumentsList(ConfigItems.BUSES);
    if (CollectionUtils.isNotEmpty(busConfigList)) {
      busConfigList.forEach(busConfig -> {
        registerMessageBus(
            busConfig.getString(ConfigItems.CLASS), busConfig.toMap());
        // 配置中声明的会在初始化的时候自动打开
        openMessageBus(busConfig.getString(BaseMessageBus.ConfigItems.NAME));
      });
    }

    beforeLoop();
  }

  @Override
  public Result registerMessageBus(String clazz, Map<String, Object> config) {
    try {
      final Class messageBusClass = Class.forName(clazz);
      messageBusClass.newInstance();
    } catch (Exception e) {
      return Result.failWithCodeAndMessage(ErrorCodes.LOAD_ERROR, e.getMessage());
    }

    dataAccess.insert(
        (String) config.get(BaseMessageBus.ConfigItems.NAME),
        clazz,
        false,
        config
    );
    updater.updateVersion();

    return Result.ok();
  }

  /**
   * 更新数据库中MessageBus的打开状态 等刷新的时候再执行具体的打开操作
   *
   * @param name MessageBus名称
   */
  @Override
  public Result openMessageBus(String name) {
    final Optional<MessageBusMeta> messageBusOptional = dataAccess.getByName(name);
    if (!messageBusOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND,
          String.format("The message bus '%s' not found", name)
      );
    }

    final MessageBusMeta messageBusMeta = messageBusOptional.get();
    if (messageBusMeta.isOpen()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.ALREADY_OPENED,
          String.format("The message bus '%s' already opened", name)
      );
    }

    dataAccess.updateOpened(name, true);
    updater.updateVersion();

    return Result.ok();
  }

  @Override
  public Result closeMessageBus(String name) {
    final Optional<MessageBusMeta> messageBusMetaOptional = dataAccess.getByName(name);
    if (!messageBusMetaOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND,
          String.format("The message bus '%s' not found", name)
      );
    }

    final MessageBusMeta messageBusMeta = messageBusMetaOptional.get();
    if (!messageBusMeta.isOpen()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.ALREADY_CLOSED,
          String.format("The message bus '%s' already closed", name)
      );
    }

    dataAccess.updateOpened(name, false);
    updater.updateVersion();

    return Result.ok();
  }

  @Override
  public Collection<MessageBus> getAllMessageBus() {
    return allPersistMessageBus.values();
  }

  @Override
  public Optional<MessageBus> getMessageBusByName(String name) {
    return Optional.ofNullable(allPersistMessageBus.get(name));
  }

  @Override
  public Result deleteMessageBus(String name) {
    final long changedRows = dataAccess.delete(name);
    if (changedRows > 0) {
      updater.updateVersion();
      return Result.ok();
    } else {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND,
          String.format("Could not found the message bus '%s'", name)
      );
    }
  }

  /**
   * 清理所有的记录，只供测试用
   */
  public void removeAll() {
    dataAccess.truncate();
  }

  @Override
  public void beforeLoop() {
    this.expectedVersion = updater.compareAndCallback(expectedVersion, this::refresh);
  }

  // 从数据库刷新
  private void refresh() {
    logger.info("Refreshing MySQLMessageBusManager...");
    final Collection<MessageBusMeta> messageBusMetaCollection = dataAccess.getAll();

    // 所有的总线名称
    final Set<String> allBusNames = new HashSet<>();

    messageBusMetaCollection.forEach(meta -> {
      final String name = meta.getName();
      allBusNames.add(name);

      if (allPersistMessageBus.containsKey(name)) {
        final MessageBus messageBus = allPersistMessageBus.get(name);
        if (messageBus.isOpen() != meta.isOpen()) {
          if (meta.isOpen()) {
            messageBus.open();
          } else {
            messageBus.close();
          }
        }
      } else {
        try {
          final Class messageBusClass = Class.forName(meta.getClazz());
          MessageBus messageBus = (MessageBus) messageBusClass.newInstance();
          messageBus.init(new EasyMap(meta.getConfig()));
          if (meta.isOpen()) {
            messageBus.open();
          } else {
            messageBus.close();
          }
          allPersistMessageBus.put(messageBus.name(), messageBus);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });

    // 清理掉被删除的MessageBus
    allPersistMessageBus.entrySet().removeIf(
        entry -> !allBusNames.contains(entry.getKey()));

    logger.info(
        "MySQLMessageBusManager refreshed, now message bus: " + allPersistMessageBus.keySet());
  }

  interface ConfigItems {

    String DATASOURCE = "datasource";

    String BUSES = "buses";

    String CLASS = "class";
  }

  /**
   * 数据库访问层
   */
  private static class DataAccess {

    private static final RowMapper<MessageBusMeta> rowMapper = resultSet -> new MessageBusMeta(
        resultSet.getString(DBFields.CLAZZ.name),
        resultSet.getString(DBFields.NAME.name),
        JSONObject.parseObject(resultSet.getString(DBFields.CONFIG.name)).getInnerMap(),
        resultSet.getBoolean(DBFields.OPENED.name)
    );

    private final String dataSource;

    DataAccess(String dataSource) {
      this.dataSource = dataSource;
    }

    public long insert(String name, String clazz, boolean opened, Map<String, Object> config) {
      final String SQL = "INSERT INTO `message_bus` (name, clazz, opened, config) "
          + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE clazz = ?, config = ?";
      final String configJSON = new JSONObject(config).toJSONString();
      return JDBCHelper.execute(
          dataSource,
          SQL,
          name,
          clazz,
          opened,
          configJSON,
          clazz,
          configJSON
      );
    }

    public long updateOpened(String name, boolean opened) {
      final String SQL = "UPDATE `message_bus` SET `opened` = ? WHERE `name` = ?";
      return JDBCHelper.execute(
          dataSource,
          SQL,
          opened,
          name
      );
    }

    public Collection<MessageBusMeta> getAll() {
      final String SQL =
          "SELECT " + DBField.joinAllFields(DBFields.values()) + " FROM `message_bus`";
      return JDBCHelper.queryList(
          dataSource,
          SQL,
          rowMapper
      );
    }

    public boolean isExisted(String name) {
      final String SQL = "SELECT `name` FROM `message_bus` WHERE `name` = ?";
      return JDBCHelper.queryOneField(
          dataSource,
          SQL,
          "name",
          String.class,
          name
      ).isPresent();
    }

    public Optional<MessageBusMeta> getByName(String name) {
      final String SQL = "SELECT " + DBField.joinAllFields(DBFields.values()) +
          " FROM `message_bus` WHERE `name` = ?";
      return JDBCHelper.queryOne(
          dataSource,
          SQL,
          rowMapper,
          name
      );
    }

    public long delete(String name) {
      final String SQL = "DELETE FROM `message_bus` WHERE `name` = ?";
      return JDBCHelper.execute(
          dataSource,
          SQL,
          name
      );
    }

    public void truncate() {
      final String SQL = "TRUNCATE `message_bus`";
      JDBCHelper.execute(
          dataSource,
          SQL
      );
    }

    /**
     * 数据库字段
     */
    private enum DBFields implements DBField {

      NAME("name", true),

      CLAZZ("clazz", false),

      OPENED("opened", false),

      CONFIG("config", false),
      ;

      private final String name;

      private final boolean primaryKey;

      DBFields(String name, boolean primaryKey) {
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
}
