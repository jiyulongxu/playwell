package playwell.activity.definition;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
 * 基于MySQL存储的ActivityDefinitionManager 当对创建和修改ActivityDefinition的时候，会直接操作数据库
 * 每次执行ActivityRunner循环的时候，会将定义从数据库加载到内存中 所有对ActivityDefinition读取，都会通过内存进行
 *
 * @author chihongze@gmail.com
 */
public class MySQLActivityDefinitionManager extends MemoryActivityDefinitionManager implements
    MessageDispatcherListener {

  private static final Logger logger = LogManager.getLogger(MySQLActivityDefinitionManager.class);

  private static final String COMPARE_AND_CALLBACK_ITEM = "activity_definition";

  // Data access object
  private ActivityDefinitionDataAccess dataAccess;

  // Compare and callback updater
  private CompareAndCallback updater;

  // Expected version
  private int expectedVersion = 0;

  public MySQLActivityDefinitionManager() {
    super();
  }

  @Override
  protected void initActivityDefinitionManager(EasyMap config) {
    String dataSource = config.getString(ConfigItems.DATASOURCE);
    dataAccess = new ActivityDefinitionDataAccess(dataSource);
    updater = new MySQLCompareAndCallback(dataSource, COMPARE_AND_CALLBACK_ITEM);
  }

  @Override
  public Result newActivityDefinition(
      String codec, String version, String definitionString, boolean enable) {
    final Date now = new Date();
    final Result validateResult = this.validateActivityDefinition(
        codec, version, enable, definitionString, now, now);
    if (!validateResult.isOk()) {
      return validateResult;
    }

    final ActivityDefinition activityDefinition = validateResult.getFromResultData(
        ResultFields.DEFINITION);

    // 检查版本是否已经存在
    final Optional<ActivityDefinitionDTO> activityDefinitionOptional = dataAccess.get(
        activityDefinition.getName(), version);
    if (activityDefinitionOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.ALREADY_EXIST,
          String.format("The ActivityDefinition already exist, name: %s, version: %s",
              activityDefinition.getName(), version)
      );
    }

    // 写入新的版本
    dataAccess.insert(activityDefinition);
    updater.updateVersion();

    return Result.okWithData(Collections.singletonMap(
        ResultFields.DEFINITION, activityDefinition
    ));
  }

  @Override
  public Result modifyActivityDefinition(
      String codec, String version, String definitionString, boolean enable) {
    final Date now = new Date();
    final Result validateResult = this.validateActivityDefinition(
        codec, version, enable, definitionString, now, now);
    if (!validateResult.isOk()) {
      return validateResult;
    }

    final ActivityDefinition activityDefinition = validateResult.getFromResultData(
        ResultFields.DEFINITION);

    // 检查版本是否存在
    final Optional<ActivityDefinitionDTO> activityDefinitionOptional = dataAccess.get(
        activityDefinition.getName(), version);
    if (!activityDefinitionOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND,
          String.format(
              "The activity definition not found, name = '%s', version = '%s'",
              activityDefinition.getName(),
              version
          )
      );
    }

    // 执行更新
    dataAccess.update(activityDefinition);
    updater.updateVersion();

    return Result.okWithData(Collections.singletonMap(
        ResultFields.DEFINITION, activityDefinition
    ));
  }

  @Override
  public Result enableActivityDefinition(String name, String version) {
    return switchStatus(name, version, true);
  }

  @Override
  public Result disableActivityDefinition(String name, String version) {
    return switchStatus(name, version, false);
  }

  // 转换状态
  private Result switchStatus(String name, String version, boolean enable) {
    final String status = enable ? "enable" : "disable";

    // 检查版本是否存在
    final Optional<ActivityDefinitionDTO> activityDefinitionOptional = dataAccess.get(
        name, version);
    if (!activityDefinitionOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND,
          String.format(
              "The activity definition not found, name = '%s', version = '%s'",
              name,
              version
          )
      );
    }

    // 修改版本
    final int changedRows = dataAccess.updateEnable(name, version, enable);
    if (changedRows == 0) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.INVALID_STATUS,
          String.format(
              "The activity definition is already %s, name = %s, version = %s",
              status,
              name,
              version
          )
      );
    }

    updater.updateVersion();
    return Result.ok();
  }

  @Override
  public Result deleteActivityDefinition(String name, String version) {
    final Optional<ActivityDefinitionDTO> activityDefinitionOptional = dataAccess
        .get(name, version);
    if (!activityDefinitionOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND,
          String.format(
              "The activity not found, name = '%s', version = '%s'",
              name,
              version
          )
      );
    }

    dataAccess.delete(name, version);
    updater.updateVersion();

    return Result.ok();
  }

  /**
   * 每次执行消息循环之前，将数据库中的所有定义加载到内存中 Steps:
   * <ol>
   * <li>检查上次最新更新时间点，如果是null，则重新加载，并更新最新时间点</li>
   * <li>从数据库获取最新的updated_on，与更新时间点做比较</li>
   * <li>如果updated_on大于更新时间点，那么重新加载，并更新最新时间点</li>
   * </ol>
   */
  @Override
  public void beforeLoop() {
    this.expectedVersion = updater.compareAndCallback(
        expectedVersion, this::refreshAll);
  }

  // 重刷内存中的记录
  private void refreshAll() {
    try {
      logger.info("Refreshing MySQLActivityDefinitionManager...");
      rwLock.writeLock().lock();
      List<ActivityDefinition> allDefinitionsFromDB = dataAccess.getAll().parallelStream()
          .map(dto -> {
            Result result = this.validateActivityDefinition(
                dto.codec, dto.version, dto.enable, dto.definition, dto.createOn, dto.updatedOn);
            return (ActivityDefinition) result.getFromResultData(ResultFields.DEFINITION);
          }).collect(Collectors.toList());

      // 清理旧有缓存
      this.allDefinitions.clear();
      this.latestEnableDefinitions.clear();

      if (CollectionUtils.isNotEmpty(allDefinitionsFromDB)) {
        allDefinitionsFromDB.forEach(definition ->
            allDefinitions.computeIfAbsent(
                definition.getName(), name -> new LinkedList<>()).add(definition));

        // 更新Latest enable
        for (Map.Entry<String, List<ActivityDefinition>> entry : allDefinitions.entrySet()) {
          final String name = entry.getKey();
          final List<ActivityDefinition> versions = entry.getValue();
          versions.sort(comparator);
          for (ActivityDefinition definition : versions) {
            if (definition.isEnable()) {
              this.latestEnableDefinitions.put(name, definition);
              break;
            }
          }
        }
      }
      logger.info("MySQLActivityDefinitionManager refreshed");
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  // Don not touch, only for unit test
  public void removeAll() {
    dataAccess.truncate();
    ((MySQLCompareAndCallback) updater).removeItem(COMPARE_AND_CALLBACK_ITEM);
  }

  // 配置项
  interface ConfigItems {

    String DATASOURCE = "datasource";
  }

  static class ActivityDefinitionDTO {

    final String codec;

    final String name;

    final String version;

    final String displayName;

    final boolean enable;

    final String definition;

    final Date createOn;

    final Date updatedOn;

    ActivityDefinitionDTO(String codec, String name, String version, String displayName,
        boolean enable, String definition, Date createOn, Date updatedOn) {
      this.codec = codec;
      this.name = name;
      this.version = version;
      this.displayName = displayName;
      this.enable = enable;
      this.definition = definition;
      this.createOn = createOn;
      this.updatedOn = updatedOn;
    }
  }

  private static class ActivityDefinitionDataAccess {

    private static final String ALL_FIELDS = DBField.joinAllFields(Field.values());

    private static final String INSERT_FIELDS = DBField.joinInsertFields(Field.values());

    private static final RowMapper<ActivityDefinitionDTO> rowMapper = rs -> new ActivityDefinitionDTO(
        rs.getString(Field.CODEC.getName()),
        rs.getString(Field.NAME.getName()),
        rs.getString(Field.VERSION.getName()),
        rs.getString(Field.DISPLAY_NAME.getName()),
        rs.getBoolean(Field.ENABLE.getName()),
        rs.getString(Field.DEFINITION.getName()),
        rs.getTimestamp(Field.CREATED_ON.getName()),
        rs.getTimestamp(Field.UPDATED_ON.getName())
    );

    private final String dataSource;

    private ActivityDefinitionDataAccess(String dataSource) {
      this.dataSource = dataSource;
    }

    private void insert(ActivityDefinition activityDefinition) {
      final String sql =
          "INSERT INTO `activity_definition` (" + INSERT_FIELDS + ") VALUES ( " + DBField
              .joinPlaceHolders(Field.values().length - 1) + ")";
      JDBCHelper.insertAndGetId(
          dataSource,
          sql,
          activityDefinition.getName(),
          activityDefinition.getVersion(),
          activityDefinition.getCodec(),
          activityDefinition.getDisplayName(),
          activityDefinition.isEnable(),
          activityDefinition.getActivityDefinitionString(),
          activityDefinition.getCreatedOn(),
          activityDefinition.getUpdatedOn()
      );
    }

    private void update(ActivityDefinition activityDefinition) {
      final String sql = "UPDATE `activity_definition` SET `display_name` = ?, `enable` = ?, "
          + "`definition` = ?, updated_on = now() WHERE `name` = ? AND `version` = ?";
      JDBCHelper.insertAndGetId(
          dataSource,
          sql,
          activityDefinition.getDisplayName(),
          activityDefinition.isEnable(),
          activityDefinition.getActivityDefinitionString(),
          activityDefinition.getName(),
          activityDefinition.getVersion()
      );
    }

    private int updateEnable(String name, String version, boolean enable) {
      final String sql = "UPDATE `activity_definition` SET `enable` = ? WHERE `name` = ? AND `version` = ? AND `enable` = ?";
      return (int) JDBCHelper.execute(
          dataSource,
          sql,
          enable,
          name,
          version,
          !enable
      );
    }

    private void delete(String name, String version) {
      final String sql = "DELETE FROM `activity_definition` WHERE `name` = ? AND `version` = ?";
      JDBCHelper.execute(
          dataSource,
          sql,
          name,
          version
      );
    }

    private Optional<ActivityDefinitionDTO> get(String name, String version) {
      final String sql = "SELECT " + ALL_FIELDS + " FROM `activity_definition` "
          + "WHERE `name` = ? AND `version` = ?";
      return JDBCHelper.queryOne(
          dataSource,
          sql,
          rowMapper,
          name,
          version
      );
    }

    private Collection<ActivityDefinitionDTO> getAll() {
      final String sql = "SELECT " + ALL_FIELDS + " FROM `activity_definition`";
      return JDBCHelper.queryList(
          dataSource,
          sql,
          rowMapper
      );
    }

    private void truncate() {
      JDBCHelper.execute(dataSource, "TRUNCATE `activity_definition`");
    }

    // 数据库字段
    enum Field implements DBField {

      ID("id", true),

      NAME("name"),

      VERSION("version"),

      CODEC("codec"),

      DISPLAY_NAME("display_name"),

      ENABLE("enable"),

      DEFINITION("definition"),

      CREATED_ON("created_on"),

      UPDATED_ON("updated_on");

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
}
