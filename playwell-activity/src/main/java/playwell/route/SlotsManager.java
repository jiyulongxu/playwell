package playwell.route;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import playwell.common.PlaywellComponent;
import playwell.common.Result;
import playwell.route.migration.MigrationCoordinator;
import playwell.route.migration.MigrationInputTask;
import playwell.route.migration.MigrationOutputTask;

/**
 * 对Slots进行管理，并对消息按照Slots进行路由
 */
public interface SlotsManager extends PlaywellComponent {

  /**
   * 对Slots进行初始化分配，只在集群首次创建时执行
   *
   * @param slotsNum 集群所包含的slots总数
   * @param slotsNumPerNode 每个ActivityRunner所分配的slots数目
   * @return 分配结果
   */
  Result allocSlots(int slotsNum, Map<String, Integer> slotsNumPerNode);

  /**
   * 获取某个ActivityRunner下的所有slots
   *
   * @param serviceName ActivityRunner
   * @return slots列表
   */
  Collection<Integer> getSlotsByServiceName(String serviceName);

  /**
   * 获取某个slot所对应的服务节点
   *
   * @param slot 目标slot
   * @return 服务节点
   */
  Optional<String> getServiceNameBySlot(int slot);

  /**
   * 获取当前的slots分布
   *
   * @return 查询结果
   */
  Result getSlotsDistribution();

  /**
   * 根据Hash值来获得所属的服务节点名称
   *
   * @param hashCode HashCode
   * @return 服务节点名称
   */
  String getServiceByHash(long hashCode);

  /**
   * 计算字符串的Hash值来获得
   *
   * @param string 计算Hash值的字符串
   * @return 所属的服务节点
   */
  String getServiceByKey(String string);

  /**
   * 根据key获取对应的slot
   *
   * @param string key string
   * @return slot index
   */
  int getSlotByKey(String string);

  /**
   * 获取MigrationCoordinator
   *
   * @return MigrationCoordinator
   */
  MigrationCoordinator getMigrationCoordinator();

  /**
   * 获取MigrationOutputTask
   *
   * @return MigrationOutputTask
   */
  MigrationOutputTask getMigrationOutputTask();

  /**
   * 获取MigrationInputTask
   *
   * @return MigrationInputTask
   */
  MigrationInputTask getMigrationInputTask();

  /**
   * 获取所有的节点
   *
   * @return 节点列表
   */
  Collection<String> getAllServices();

  /**
   * 修改Slots所属的service
   *
   * @param slots slots list
   * @param service new belong service
   */
  void modifyService(Collection<Integer> slots, String service);

  interface ErrorCodes {

    String INVALID_NODE_SLOTS_NUM = "invalid_node_slots_num";

    String ALREADY_ALLOCATED = "already_allocated";
  }

  interface ResultFields {

    String SLOTS = "slots";

    String DISTRIBUTION = "distribution";
  }
}
