package playwell.clock;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ClockReplicationRunnerStatus
 */
public enum ClockReplicationRunnerStatus {

  INIT("init"),

  RUNNING("running"),

  STOPPED("stopped"),

  PAUSED("paused"),
  ;

  private static final Map<String, ClockReplicationRunnerStatus> allStatus = Arrays.stream(values())
      .collect(
          Collectors.toMap(ClockReplicationRunnerStatus::getStatus, Function.identity()));

  private final String status;

  ClockReplicationRunnerStatus(String status) {
    this.status = status;
  }

  public static Optional<ClockReplicationRunnerStatus> valueOfByStatus(String status) {
    return Optional.ofNullable(allStatus.get(status));
  }

  public String getStatus() {
    return this.status;
  }
}
