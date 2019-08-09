package playwell.route.migration;

import java.util.Optional;

/**
 * slots迁移输入任务
 */
public interface MigrationInputTask {

  /**
   * 根据输入节点来获取迁移进程
   *
   * @param serviceName 输入节点名称
   * @return 迁移进程信息
   */
  Optional<MigrationProgress> getMigrationProgress(String serviceName);

  /**
   * 启动迁入进程
   *
   * @param migrationProgress 迁移进程信息
   */
  void startInputTask(MigrationProgress migrationProgress);

  /**
   * 停止输入任务
   */
  void stop();

  /**
   * 任务是否已经停止
   *
   * @return 是否已经停止
   */
  boolean isStopped();
}
