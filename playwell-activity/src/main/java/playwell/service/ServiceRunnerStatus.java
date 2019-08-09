package playwell.service;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ServiceRunner的状态
 */
public enum ServiceRunnerStatus {

  INIT("init"),

  RUNNING("running"),

  PAUSED("paused"),

  STOPPED("stopped"),
  ;

  private static final Map<String, ServiceRunnerStatus> allStatus = Arrays.stream(values())
      .collect(Collectors.toMap(ServiceRunnerStatus::getStatus, Function.identity()));

  private final String status;

  ServiceRunnerStatus(String status) {
    this.status = status;
  }

  public static Optional<ServiceRunnerStatus> valueOfByStatus(String status) {
    return Optional.ofNullable(allStatus.get(status));
  }

  public String getStatus() {
    return this.status;
  }
}
