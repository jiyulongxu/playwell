package playwell.activity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import playwell.common.PlaywellComponent;
import playwell.common.Result;

/**
 * ActivityManager可以用于创建、操作以及根据状态获取活动信息等 可以有多种实现，比如活动信息可以保存到内存中或者数据库中等
 *
 * @author chihongze@gmail.com
 */
public interface ActivityManager extends PlaywellComponent {

  /**
   * 创建新活动
   *
   * @param definitionName 活动定义名称
   * @param displayName 活动的展示名称
   * @param config 活动配置
   * @return 创建执行结果
   */
  Result createNewActivity(String definitionName, String displayName, Map<String, Object> config);

  /**
   * 根据ID来获取活动实例，也可以用于测试活动是否存在
   *
   * @param id 活动ID
   * @return 活动实例Optional
   */
  Optional<Activity> getActivityById(int id);

  /**
   * 根据活动状态来获取活动实例
   *
   * @param status 活动状态
   * @return 该状态下的所有活动实例列表
   */
  Collection<Activity> getActivitiesByStatus(ActivityStatus status);

  /**
   * 获取当前所有可调度的活动列表 可调度的定义：处于正常状态的Activity或者处于暂停状态但是设置有 $schedule.pause_continue_old的Activity
   *
   * @return 可调度的活动列表
   */
  Collection<Activity> getSchedulableActivities();

  /**
   * 获取所有的活动定义
   */
  Collection<Activity> getAllActivities();

  /**
   * 根据活动定义名称来获取相关活动集合
   *
   * @param definitionName 活动定义名称
   * @return 活动集合
   */
  Collection<Activity> getActivitiesByDefinitionName(String definitionName);

  /**
   * 暂停一个活动实例
   *
   * @param activityId 活动ID
   * @return 暂停操作执行结果
   */
  Result pauseActivity(int activityId);

  /**
   * 恢复一个活动实例的执行
   *
   * @param activityId 活动ID
   * @return 恢复操作执行结果
   */
  Result continueActivity(int activityId);

  /**
   * 杀死一个活动实例
   *
   * @param activityId 活动ID
   * @return Kill操作结果
   */
  Result killActivity(int activityId);


  /**
   * 修改活动配置
   */
  Result modifyConfig(int activityId, Map<String, Object> newConfig);


  // createNewActivity的错误码
  interface ErrorCodes {

    /**
     * 找不到活动定义
     */
    String DEF_NOT_FOUND = "def_not_found";

    /**
     * 找不到活动实例
     */
    String ACTIVITY_NOT_FOUND = "activity_not_found";

    /**
     * 不合法的活动状态
     */
    String INVALID_STATUS = "invalid_status";
  }


  // Result data 字段
  interface ResultFields {

    String ACTIVITY = "activity";
  }
}
