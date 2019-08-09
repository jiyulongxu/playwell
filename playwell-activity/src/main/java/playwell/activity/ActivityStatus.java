package playwell.activity;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 活动状态枚举
 *
 * @author chihongze@gmail.com
 */
public enum ActivityStatus {

  /**
   * 当处于Common状态下的时候，ActivityThread可以被正常触发执行， 正在运行的ActivityThread也可以按照正常逻辑进行调度
   */
  COMMON(0, "common"),

  /**
   * 当处于Paused状态下的时候，新的ActivityThread将不会被触发执行， 旧的Activity是否依旧允许需要根据活动的ON_PAUSE_STOP_ALL_THREADS配置选项来确定
   */
  PAUSED(1, "paused"),

  /**
   * 当处于Killed状态下的时候，新的ActivityThread将不会被触发执行， 并且正在运行的ActivityThread将会被强制停止调度。
   */
  KILLED(2, "killed"),

  ;

  private static final Map<Integer, ActivityStatus> allByCode = Arrays.stream(values()).collect(
      Collectors.toMap(ActivityStatus::getCode, Function.identity()));

  private static final Map<String, ActivityStatus> allByStatus = Arrays.stream(values()).collect(
      Collectors.toMap(ActivityStatus::getStatus, Function.identity()));

  private final int code;

  private final String status;

  ActivityStatus(int code, String status) {
    this.code = code;
    this.status = status;
  }

  public static Optional<ActivityStatus> valueOfByCode(int code) {
    return Optional.ofNullable(allByCode.get(code));
  }

  public static Optional<ActivityStatus> valueOfByStatus(String status) {
    return Optional.ofNullable(allByStatus.get(status));
  }

  public int getCode() {
    return this.code;
  }

  public String getStatus() {
    return this.status;
  }
}
