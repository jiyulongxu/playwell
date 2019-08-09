package playwell.clock;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Clock runner status
 */
public enum ClockRunnerStatus {

  INIT("init"),

  RUNNING("running"),

  STOPPED("stopped"),

  PAUSED("paused"),

  ;

  private static final Map<String, ClockRunnerStatus> allStatus = Arrays.stream(values()).collect(
      Collectors.toMap(ClockRunnerStatus::getStatus, Function.identity()));

  private final String status;

  ClockRunnerStatus(String status) {
    this.status = status;
  }

  public static Optional<ClockRunnerStatus> valueOfByStatus(String status) {
    return Optional.ofNullable(allStatus.get(status));
  }

  public String getStatus() {
    return this.status;
  }
}
