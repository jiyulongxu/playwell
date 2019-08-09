package playwell.activity;

import com.alibaba.fastjson.JSONObject;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.definition.ActivityDefinitionManager;
import playwell.common.CompareAndCallback;
import playwell.common.EasyMap;
import playwell.common.MySQLCompareAndCallback;
import playwell.common.Result;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.message.MessageDispatcherListener;
import playwell.storage.jdbc.DBField;
import playwell.storage.jdbc.JDBCHelper;
import playwell.storage.jdbc.RowMapper;

/**
 * 基于MySQL存储的ActivityManager
 *
 * @author chihongze@gmail.com
 */
public class MySQLActivityManager extends MemoryActivityManager implements
    MessageDispatcherListener {

  private static final Logger logger = LogManager.getLogger(MySQLActivityManager.class);

  private static final String COMPARE_AND_CALLBACK_ITEM = "activity";

  // 数据访问
  private ActivityDataAccess dataAccess;

  // Compare and callback updater
  private CompareAndCallback updater;

  // Compare and callback expected version
  private int expectedVersion = 0;

  public MySQLActivityManager() {

  }

  @Override
  protected void initActivityManager(EasyMap configuration) {
    String dataSource = configuration.getString(ConfigItems.DATASOURCE);
    this.dataAccess = new ActivityDataAccess(dataSource);
    this.updater = new MySQLCompareAndCallback(dataSource, COMPARE_AND_CALLBACK_ITEM);
  }

  @Override
  protected Activity save(String displayName, String definitionName,
      Map<String, Object> activityConfig) {
    final Date now = new Date();
    final Activity activity = dataAccess
        .insert(displayName, definitionName, activityConfig, now, now);
    updater.updateVersion();
    return activity;
  }

  @Override
  protected Result changeActivityStatus(String action, int activityId,
      EnumSet<ActivityStatus> fromStatus, ActivityStatus toStatus) {
    // 检查Activity是否存在
    final Optional<Activity> activityOptional = dataAccess.get(activityId);
    if (!activityOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.ACTIVITY_NOT_FOUND, String.format("Activity not found: %d", activityId));
    }

    final Activity activity = activityOptional.get();

    if (!fromStatus.contains(activity.getStatus())) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.INVALID_STATUS,
          String.format("Could not %s the activity: %d, invalid status: %s",
              action, activityId, activity.getStatus().getStatus())
      );
    }

    final Activity newActivity = changeActivityStatus(activity, toStatus);
    dataAccess.updateStatus(
        newActivity.getId(), newActivity.getStatus(), newActivity.getUpdatedOn());
    updater.updateVersion();

    return Result.okWithData(Collections.singletonMap(ResultFields.ACTIVITY, newActivity));
  }

  @Override
  public Result modifyConfig(int activityId, Map<String, Object> newConfig) {
    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final ActivityDefinitionManager activityDefinitionManager = (ActivityDefinitionManager) integrationPlan
        .getTopComponent(TopComponentType.ACTIVITY_DEFINITION_MANAGER);

    final Optional<Activity> activityOptional = dataAccess.get(activityId);
    if (!activityOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.ACTIVITY_NOT_FOUND,
          String.format("Activity not found: %d", activityId)
      );
    }

    final Activity existedActivity = activityOptional.get();
    final Optional<ActivityDefinition> activityDefinitionOptional = activityDefinitionManager
        .getLatestEnableActivityDefinition(existedActivity.getDefinitionName());
    if (!activityDefinitionOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.DEF_NOT_FOUND,
          String.format(
              "Definition '%s' of activity %s not found",
              existedActivity.getDefinitionName(),
              existedActivity.getId()
          )
      );
    }

    final Map<String, Object> activityConfig = MapUtils.isEmpty(newConfig) ?
        Collections.emptyMap() : newConfig;
    dataAccess.updateConfig(activityId, activityConfig);
    updater.updateVersion();

    return Result.okWithData(Collections.singletonMap(
        ResultFields.ACTIVITY, activityConfig));
  }

  @Override
  public void beforeLoop() {
    expectedVersion = updater.compareAndCallback(expectedVersion, this::refreshAll);
  }

  private void refreshAll() {
    try {
      rwLock.writeLock().lock();
      logger.info("Refreshing MySQLActivityManager...");
      allActivities.clear();
      final Collection<Activity> allNotKilledActivities = dataAccess.getAllNotKilled();
      if (CollectionUtils.isNotEmpty(allNotKilledActivities)) {
        allNotKilledActivities.forEach(activity -> allActivities.put(activity.getId(), activity));
      }
      logger.info("MySQLActivityManager refreshed");
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  public void removeAll() {
    dataAccess.truncate();
    ((MySQLCompareAndCallback) updater).removeItem(COMPARE_AND_CALLBACK_ITEM);
  }

  // 配置项
  interface ConfigItems {

    String DATASOURCE = "datasource";
  }

  private static class ActivityDataAccess {

    private static final String ALL_FIELDS = DBField.joinAllFields(Field.values());

    private static final String INSERT_FIELDS = DBField.joinInsertFields(Field.values());

    private static final RowMapper<Activity> rowMapper = rs -> {
      int statusCode = rs.getInt(Field.STATUS.getName());
      Optional<ActivityStatus> activityStatusOptional = ActivityStatus.valueOfByCode(
          rs.getInt(Field.STATUS.getName()));
      if (!activityStatusOptional.isPresent()) {
        throw new RuntimeException("Unknown activity status code: " + statusCode);
      }

      return new Activity(
          rs.getInt(Field.ID.getName()),
          rs.getString(Field.DISPLAY_NAME.getName()),
          rs.getString(Field.DEFINITION_NAME.getName()),
          activityStatusOptional.get(),
          JSONObject.parseObject(rs.getString(Field.CONFIG.getName())).getInnerMap(),
          rs.getTimestamp(Field.CREATED_ON.getName()),
          rs.getTimestamp(Field.UPDATED_ON.getName())
      );
    };

    private final String dataSource;

    ActivityDataAccess(String dataSource) {
      this.dataSource = dataSource;
    }

    Optional<Activity> get(int id) {
      final String sql = "SELECT " + ALL_FIELDS + " FROM `activity` WHERE id = ?";
      return JDBCHelper.queryOne(
          dataSource,
          sql,
          rowMapper,
          id
      );
    }

    Collection<Activity> getAllNotKilled() {
      final String sql = "SELECT " + ALL_FIELDS + " FROM `activity` WHERE `status` != ?";
      return JDBCHelper.queryList(
          dataSource,
          sql,
          rowMapper,
          ActivityStatus.KILLED.getCode()
      );
    }

    Activity insert(
        String displayName, String definitionName, Map<String, Object> activityConfig,
        Date createdOn, Date updatedOn) {
      final String sql = "INSERT INTO `activity` (" + INSERT_FIELDS + ") VALUES ("
          + DBField.joinPlaceHolders(Field.values().length - 1) + ")";
      final String configJSON = JSONObject.toJSONString(activityConfig);
      final int id = (int) JDBCHelper.insertAndGetId(
          dataSource,
          sql,
          displayName,
          definitionName,
          ActivityStatus.COMMON.getCode(),
          configJSON,
          createdOn,
          updatedOn
      );
      return new Activity(
          id,
          displayName,
          definitionName,
          ActivityStatus.COMMON,
          activityConfig,
          createdOn,
          updatedOn
      );
    }

    void updateStatus(int id, ActivityStatus status, Date updatedOn) {
      final String sql = "UPDATE `activity` SET status = ?, updated_on = ? WHERE id = ?";
      JDBCHelper.execute(
          dataSource,
          sql,
          status.getCode(),
          updatedOn,
          id
      );
    }

    void updateConfig(int id, Map<String, Object> config) {
      final String sql = "UPDATE `activity` SET `config` = ?, `updated_on` = ? WHERE id = ?";
      final String configJSON;
      if (MapUtils.isEmpty(config)) {
        configJSON = "{}";
      } else {
        configJSON = JSONObject.toJSONString(config);
      }
      JDBCHelper.execute(
          dataSource,
          sql,
          configJSON,
          new Date(),
          id
      );
    }

    void truncate() {
      JDBCHelper.execute(dataSource, "TRUNCATE `activity`");
    }

    enum Field implements DBField {

      ID("id", true),

      DISPLAY_NAME("display_name"),

      DEFINITION_NAME("definition_name"),

      STATUS("status"),

      CONFIG("config"),

      CREATED_ON("created_on"),

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
}
