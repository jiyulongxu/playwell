package playwell.activity.thread;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ActivityThread实例运行状态
 *
 * @author chihongze@gmail.com
 */
public enum ActivityThreadStatus {

  /**
   * ActivityThread已经就绪，等待调度器择机执行
   */
  SUSPENDING(0, "suspending"),

  /**
   * 正在执行中
   */
  RUNNING(1, "running"),

  /**
   * 正在等待外部消息唤醒
   */
  WAITING(2, "waiting"),

  /**
   * ActivityThread已经顺利执行完毕
   */
  FINISHED(3, "finished"),

  /**
   * 因为某些错误而导致执行失败
   */
  FAIL(4, "fail"),

  /**
   * 被暂停，暂停后的ActivityThread不会再被调度器执行， 并且只接受kill和continue这两种系统消息， 当接受到kill消息时，ActivityThread将进入KILLED状态，当接受到continue消息时，将会被调度器重新恢复执行
   */
  PAUSED(5, "paused"),

  /**
   * 被强制终止，终止后的ActivityThread不会再被调度器执行，也不会再被任何消息唤醒
   */
  KILLED(6, "killed"),

  ;

  private static final Map<Integer, ActivityThreadStatus> valuesByCode = Arrays
      .stream(values())
      .collect(Collectors.toMap(ActivityThreadStatus::getCode, Function.identity()));

  private static final Map<String, ActivityThreadStatus> valuesByStatus = Arrays
      .stream(values())
      .collect(Collectors.toMap(ActivityThreadStatus::getStatus, Function.identity()));

  private final int code;

  private final String status;

  ActivityThreadStatus(int code, String status) {
    this.code = code;
    this.status = status;
  }

  public static Optional<ActivityThreadStatus> valueOfByCode(int code) {
    return Optional.ofNullable(valuesByCode.get(code));
  }

  public static Optional<ActivityThreadStatus> valueOfByStatus(String status) {
    return Optional.ofNullable(valuesByStatus.get(status));
  }

  public int getCode() {
    return code;
  }

  public String getStatus() {
    return status;
  }
}
