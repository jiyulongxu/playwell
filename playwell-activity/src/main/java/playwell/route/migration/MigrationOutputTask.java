package playwell.route.migration;

import java.util.Optional;

/**
 * Slots迁移输出任务
 */
public interface MigrationOutputTask {

  /**
   * 如果当前节点是一个即将执行迁移的输出节点，那么返回MigrationProgress
   *
   * @param serviceName 当前ActivityRunner节点名称
   * @return MigrationProgress Optional
   */
  Optional<MigrationProgress> getMigrationProgress(String serviceName);

  /**
   * 启动输出任务线程
   *
   * @param migrationProgress 当前正在执行的migration progress
   */
  void startOutputTask(MigrationProgress migrationProgress);

  /**
   * 停止输出线程
   */
  void stop();

  /**
   * 输出线程是否已经停止
   */
  boolean isStopped();
}
