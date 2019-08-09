package playwell.activity;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ActivityRunner的执行状态
 */
public enum ActivityRunnerStatus {

  // 初始化中
  INIT("init"),

  // 正在执行当中
  RUNNING("running"),

  // 已经停止执行
  STOPPED("stopped"),

  // 已经暂停执行
  PAUSED("paused"),

  // 正在执行需要Stop world的对象扫描
  SCANNING("scanning"),

  // 正在迁入ActivityThread
  MIGRATING_IN("migrating_in"),

  ;

  private static final Map<String, ActivityRunnerStatus> allStatus = Arrays.stream(values())
      .collect(
          Collectors.toMap(ActivityRunnerStatus::getStatus, Function.identity()));

  private final String status;

  ActivityRunnerStatus(String status) {
    this.status = status;
  }

  public static Optional<ActivityRunnerStatus> valueOfByStatus(String status) {
    return Optional.ofNullable(allStatus.get(status));
  }

  public String getStatus() {
    return this.status;
  }
}
