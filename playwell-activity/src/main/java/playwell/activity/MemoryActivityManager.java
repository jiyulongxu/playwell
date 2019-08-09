package playwell.activity;


import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.definition.ActivityDefinitionManager;
import playwell.activity.thread.ScheduleConfigItems;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;


/**
 * 基于内存存储的ActivityManager
 *
 * @author chihongze@gmail.com
 */
public class MemoryActivityManager extends BaseActivityManager {

  // All activities
  protected final Map<Integer, Activity> allActivities = new HashMap<>();

  // Read write lock
  protected final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  // Id generator
  private final AtomicInteger idGenerator = new AtomicInteger(0);


  public MemoryActivityManager() {

  }

  @Override
  protected void initActivityManager(EasyMap configuration) {

  }

  /**
   * 创建新活动 Steps:
   * <ol>
   * <li>检查ActivityDefinition是否存在</li>
   * <li>覆盖定义中的配置</li>
   * <li>组装新的Activity对象</li>
   * <li>保存</li>
   * </ol>
   *
   * @param definitionName 活动定义名称
   * @param displayName 活动的展示名称
   * @param config 活动配置
   * @return 创建结果
   */
  @Override
  public Result createNewActivity(
      String definitionName, String displayName, Map<String, Object> config) {
    IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    ActivityDefinitionManager activityDefinitionManager = (ActivityDefinitionManager) integrationPlan
        .getTopComponent(TopComponentType.ACTIVITY_DEFINITION_MANAGER);

    // 检查有无可用版本的ActivityDefinition
    final Optional<ActivityDefinition> activityDefinitionOptional = activityDefinitionManager
        .getLatestEnableActivityDefinition(definitionName);
    if (!activityDefinitionOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.DEF_NOT_FOUND,
          String.format("There are no enable version in the activity definition '%s'",
              definitionName)
      );
    }
    final ActivityDefinition activityDefinition = activityDefinitionOptional.get();

    // 覆盖Config
    Map<String, Object> activityConfig = getActivityConfig(activityDefinition, config);

    if (StringUtils.isEmpty(displayName)) {
      displayName = "";
    } else {
      displayName = StringUtils.strip(displayName);
    }

    Activity activity = save(displayName, definitionName, activityConfig);
    callbackListeners(activityStatusListener -> activityStatusListener.onCreated(activity));

    return Result.okWithData(Collections.singletonMap(ResultFields.ACTIVITY, activity));
  }

  protected Activity save(
      String displayName, String definitionName, Map<String, Object> activityConfig) {
    try {
      rwLock.writeLock().lock();

      final Date now = new Date();

      // 组装Activity对象
      int id = idGenerator.getAndIncrement();
      final Activity activity = new Activity(
          id,
          displayName,
          definitionName,
          ActivityStatus.COMMON,
          activityConfig,
          now,
          now
      );

      // 保存
      allActivities.put(id, activity);
      return activity;
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  @Override
  public Optional<Activity> getActivityById(int id) {
    try {
      rwLock.readLock().lock();
      if (!allActivities.containsKey(id) ||
          allActivities.get(id).getStatus() == ActivityStatus.KILLED) {
        return Optional.empty();
      }
      return Optional.of(allActivities.get(id));
    } finally {
      rwLock.readLock().unlock();
    }
  }

  @Override
  public Collection<Activity> getActivitiesByStatus(ActivityStatus status) {
    if (status == ActivityStatus.KILLED) {
      return Collections.emptyList();
    }

    try {
      rwLock.readLock().lock();
      return allActivities.values().stream()
          .filter(activity -> activity.getStatus() == status)
          .collect(Collectors.toList());
    } finally {
      rwLock.readLock().unlock();
    }
  }


  @Override
  public Collection<Activity> getSchedulableActivities() {
    try {
      rwLock.readLock().lock();

      return allActivities.values().stream().filter(activity -> {

        if (ActivityStatus.COMMON == activity.getStatus()) {
          return true;
        } else if (ActivityStatus.PAUSED == activity.getStatus()) {
          final EasyMap config = new EasyMap(activity.getConfig());
          return config.getWithConfigItem(ScheduleConfigItems.PAUSE_CONTINUE_OLD);
        }
        return false;
      }).collect(Collectors.toList());
    } finally {
      rwLock.readLock().unlock();
    }
  }

  @Override
  public Collection<Activity> getAllActivities() {
    try {
      rwLock.readLock().lock();
      return this.allActivities.values().stream().filter(
          activity -> activity.getStatus() != ActivityStatus.KILLED).collect(Collectors.toList());
    } finally {
      rwLock.readLock().unlock();
    }
  }

  @Override
  public Collection<Activity> getActivitiesByDefinitionName(String definitionName) {
    try {
      rwLock.readLock().lock();
      return this.allActivities.values().stream()
          .filter(activity ->
              activity.getStatus() != ActivityStatus.KILLED &&
                  activity.getDefinitionName().equals(definitionName))
          .collect(Collectors.toList());
    } finally {
      rwLock.readLock().unlock();
    }
  }

  /**
   * 暂停Activity Steps:
   * <ol>
   * <li>查找Activity是否存在</li>
   * <li>判断当前Activity状态，只有处于COMMON状态的Activity才可以被暂停</li>
   * <li>构建新状态的Activity</li>
   * <li>保存</li>
   * </ol>
   *
   * @param activityId 活动ID
   * @return 暂停结果
   */
  @Override
  public Result pauseActivity(int activityId) {
    final Result result = changeActivityStatus(
        Actions.PAUSE, activityId, EnumSet.of(ActivityStatus.COMMON), ActivityStatus.PAUSED);
    if (result.isOk()) {
      final Activity activity = result.getFromResultData(ResultFields.ACTIVITY);
      callbackListeners(listener -> listener.onPaused(activity));
    }
    return result;
  }

  /**
   * 从暂停中恢复Activity Steps:
   * <ol>
   * <li>查找Activity是否存在</li>
   * <li>判断当前Activity状态，只有暂停状态的Activity才可以被恢复执行</li>
   * <li>构建新状态的Activity</li>
   * <li>保存Activity</li>
   * </ol>
   *
   * @param activityId 活动ID
   * @return 恢复结果
   */
  @Override
  public Result continueActivity(int activityId) {
    final Result result = changeActivityStatus(
        Actions.CONTINUE, activityId, EnumSet.of(ActivityStatus.PAUSED), ActivityStatus.COMMON);
    if (result.isOk()) {
      final Activity activity = result.getFromResultData(ResultFields.ACTIVITY);
      callbackListeners(listener -> listener.onContinued(activity));
    }
    return result;
  }


  @Override
  public Result killActivity(int activityId) {
    final Result result = changeActivityStatus(
        Actions.KILL,
        activityId,
        EnumSet.of(ActivityStatus.COMMON, ActivityStatus.PAUSED),
        ActivityStatus.KILLED
    );
    if (result.isOk()) {
      final Activity activity = result.getFromResultData(ResultFields.ACTIVITY);
      callbackListeners(listener -> listener.onKilled(activity));
    }
    return result;
  }

  @Override
  public Result modifyConfig(int activityId, Map<String, Object> newConfig) {
    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final ActivityDefinitionManager activityDefinitionManager = (ActivityDefinitionManager) integrationPlan
        .getTopComponent(TopComponentType.ACTIVITY_DEFINITION_MANAGER);

    try {
      rwLock.writeLock().lock();
      final Activity existActivity = allActivities.get(activityId);
      if (existActivity == null) {
        return Result.failWithCodeAndMessage(
            ErrorCodes.ACTIVITY_NOT_FOUND,
            String.format("Activity ID not found: %d", activityId)
        );
      }

      final Optional<ActivityDefinition> activityDefinitionOptional = activityDefinitionManager
          .getLatestEnableActivityDefinition(existActivity.getDefinitionName());
      if (!activityDefinitionOptional.isPresent()) {
        return Result.failWithCodeAndMessage(
            ErrorCodes.DEF_NOT_FOUND,
            String.format("Definition '%s' of activity %d not found",
                existActivity.getDefinitionName(), activityId)
        );
      }

      final ActivityDefinition activityDefinition = activityDefinitionOptional.get();
      final Map<String, Object> activityConfig = getActivityConfig(activityDefinition, newConfig);

      final Activity newActivity = new Activity(
          existActivity.getId(),
          existActivity.getDisplayName(),
          existActivity.getDefinitionName(),
          existActivity.getStatus(),
          activityConfig,
          existActivity.getCreatedOn(),
          new Date()
      );

      allActivities.put(activityId, newActivity);

      return Result.okWithData(Collections.singletonMap(ResultFields.ACTIVITY, newActivity));
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  protected Map<String, Object> getActivityConfig(
      ActivityDefinition definition, Map<String, Object> newConfig) {
    final Map<String, Object> activityConfig = new HashMap<>();
    if (MapUtils.isNotEmpty(definition.getConfig())) {
      activityConfig.putAll(definition.getConfig());
    }
    if (MapUtils.isNotEmpty(newConfig)) {
      activityConfig.putAll(newConfig);
    }
    return activityConfig;
  }

  protected Result changeActivityStatus(
      String action, int activityId, EnumSet<ActivityStatus> fromStatus, ActivityStatus toStatus) {
    try {
      rwLock.writeLock().lock();

      // 判断Activity是否存在
      if (!allActivities.containsKey(activityId) ||
          allActivities.get(activityId).getStatus() == ActivityStatus.KILLED) {
        return Result.failWithCodeAndMessage(
            ErrorCodes.ACTIVITY_NOT_FOUND, String.format("Activity not found: %d", activityId));
      }

      // 判断当前Activity状态
      final Activity activity = allActivities.get(activityId);
      if (!fromStatus.contains(activity.getStatus())) {
        return Result.failWithCodeAndMessage(
            ErrorCodes.INVALID_STATUS,
            String.format("Could not %s the activity: %d, invalid status: %s",
                action, activityId, activity.getStatus().getStatus())
        );
      }

      // 创建新状态的Activity实例
      final Activity newActivity = changeActivityStatus(activity, toStatus);
      allActivities.put(activityId, newActivity);

      return Result.okWithData(Collections.singletonMap(ResultFields.ACTIVITY, newActivity));
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  protected Activity changeActivityStatus(Activity activity, ActivityStatus status) {
    return new Activity(
        activity.getId(),
        activity.getDisplayName(),
        activity.getDefinitionName(),
        status,
        activity.getConfig(),
        activity.getCreatedOn(),
        new Date()
    );
  }

  interface Actions {

    String PAUSE = "pause";

    String CONTINUE = "continue";

    String KILL = "kill";
  }
}
