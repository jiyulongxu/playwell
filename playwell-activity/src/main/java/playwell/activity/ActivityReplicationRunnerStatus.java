package playwell.activity;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ActivityReplicationRunnerStatus
 */
public enum ActivityReplicationRunnerStatus {

  INIT("init"),

  RUNNING("running"),

  STOPPED("stopped"),

  PAUSED("paused"),

  ;

  private static final Map<String, ActivityReplicationRunnerStatus> allStatus = Arrays
      .stream(values())
      .collect(Collectors.toMap(ActivityReplicationRunnerStatus::getStatus, Function.identity()));

  private final String status;

  ActivityReplicationRunnerStatus(String status) {
    this.status = status;
  }

  public static Optional<ActivityReplicationRunnerStatus> valueOfByStatus(String status) {
    return Optional.ofNullable(allStatus.get(status));
  }

  public String getStatus() {
    return this.status;
  }
}
