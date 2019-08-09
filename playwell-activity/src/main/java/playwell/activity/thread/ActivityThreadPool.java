package playwell.activity.thread;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import playwell.common.PlaywellComponent;
import playwell.common.Result;
import playwell.message.Message;
import playwell.message.bus.MessageBus;

/**
 * ActivityThreadPool用于管理所有的ActivityThread实例 并为其他组件提供各种CRUD服务
 *
 * @author chihongze@gmail.com
 */
public interface ActivityThreadPool extends PlaywellComponent {

  /**
   * 向ActivityThreadPool中加入/更新一个ActivityThread
   *
   * @param activityThread 要操作的ActivityThread实例
   */
  void upsertActivityThread(ActivityThread activityThread);

  /**
   * 获取单个ActivityThread，也可以用于定位一个ActivityThread是否存在
   *
   * @param activityId 活动ID
   * @param domainId DomainID
   * @return ActivityThread Optional
   */
  Optional<ActivityThread> getActivityThread(int activityId, String domainId);

  /**
   * 根据ActivityID和DomainID集合来批量获取ActivityThread实例
   *
   * @param activityId 活动ID
   * @param domainIdCollection DomainID集合
   * @return 返回Map，Key是DomainID，Value是ActivityThread
   */
  Map<String, ActivityThread> multiGetActivityThreads(
      int activityId, Collection<String> domainIdCollection);

  /**
   * 根据传入的[ActivityId, DomainID]唯一标识符，批量获取ActivityThread实例
   *
   * @param identifiers 标识符集合
   * @return ActivityThread实例列表
   */
  Collection<ActivityThread> multiGetActivityThreads(Collection<Pair<Integer, String>> identifiers);

  /**
   * 对ActivityThread进行扫描后执行回调操作
   *
   * @param consumer 回调上下文
   */
  void scanAll(ActivityThreadScanConsumer consumer);

  /**
   * 停止对ActivityThread的扫描
   */
  void stopScan();

  /**
   * 批量写入ActivityThread对象，通常用于集群迁移
   *
   * @param activityThreads ActivityThreads Collections
   */
  void batchSaveActivityThreads(Collection<ActivityThread> activityThreads);

  /**
   * 应用同步Replication消息到本实例
   *
   * @param messages Replication messages
   */
  void applyReplicationMessages(Collection<Message> messages);

  /**
   * 添加Replication MessageBus
   *
   * @param replicationMessageBus Replication message bus name
   * @return add result
   */
  Result addReplicationMessageBus(String replicationMessageBus);

  /**
   * 删除Replication Message Bus
   *
   * @param replicationMessageBus Replication message bus name
   * @return remove result
   */
  Result removeReplicationMessageBus(String replicationMessageBus);

  /**
   * 获取所有的Replication message bus
   *
   * @return All replication message bus
   */
  Collection<MessageBus> getAllReplicationMessageBuses();
}
