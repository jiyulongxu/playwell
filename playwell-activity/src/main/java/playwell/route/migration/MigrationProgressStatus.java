package playwell.route.migration;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 迁移进程状态
 */
public enum MigrationProgressStatus {

  /**
   * 尚未开始迁移
   */
  PENDING(0),

  /**
   * 迁移中
   */
  MIGRATING(1),

  /**
   * 迁移完毕
   */
  FINISHED(2),
  ;

  private static final Map<Integer, MigrationProgressStatus> allStatus = Arrays.stream(values())
      .collect(Collectors.toMap(MigrationProgressStatus::getStatus, Function.identity()));

  private final int status;

  MigrationProgressStatus(int status) {
    this.status = status;
  }

  public static Optional<MigrationProgressStatus> valueOfByStatus(int status) {
    return Optional.ofNullable(allStatus.get(status));
  }

  public int getStatus() {
    return this.status;
  }
}
