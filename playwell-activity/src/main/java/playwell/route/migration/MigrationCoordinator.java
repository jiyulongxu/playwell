package playwell.route.migration;

import java.util.Map;
import playwell.common.Result;

/**
 * MigrationCoordinator用于创建整体迁移计划 以及监控、修改迁移状态
 */
public interface MigrationCoordinator {

  /**
   * 启动新的迁移计划
   *
   * @param messageBus 迁移所要使用的MessageBus
   * @param slotsDistribution 期望最终各个节点的slots分布数目
   * @param comment 本次迁移备注
   * @return 启动结果
   */
  Result startMigrationPlan(
      String messageBus,
      Map<String, Object> inputMessageBusConfig,
      Map<String, Object> outputMessageBusConfig,
      Map<String, Integer> slotsDistribution,
      String comment
  );

  /**
   * 获取当前的迁移状态
   *
   * @return 当前迁移状态
   */
  Result getCurrentStatus();

  /**
   * 继续当前未完成的MigrationPlan
   */
  Result continueMigrationPlan();

  /**
   * 终止coordinator线程的执行
   */
  void stop();

  /**
   * Coordinator是否已经处于停止状态
   *
   * @return 是否处于停止状态
   */
  boolean isStopped();

  interface ResultFields {

    String PLAN = "plan";

    String PROGRESS = "progress";
  }

  interface ErrorCodes {

    String INIT_INPUT_MESSAGE_BUS_ERROR = "init_input_message_bus_error";

    String INIT_OUTPUT_MESSAGE_BUS_ERROR = "init_output_message_bus_error";

    String INVALID_SLOTS = "invalid_slots";

    String ALREADY_EXIST = "already_exist";

    String NOT_FOUND = "not_found";
  }
}
