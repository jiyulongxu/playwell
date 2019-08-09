package playwell.route;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MessageRoute执行状态
 */
public enum MessageRouteStatus {

  INIT("init"),

  RUNNING("running"),

  STOPPED("stopped"),

  PAUSED("paused"),

  ;

  private static final Map<String, MessageRouteStatus> allStatus = Arrays.stream(values()).collect(
      Collectors.toMap(MessageRouteStatus::getStatus, Function.identity()));

  private final String status;

  MessageRouteStatus(String status) {
    this.status = status;
  }

  public static Optional<MessageRouteStatus> valueOfByStatus(String status) {
    return Optional.ofNullable(allStatus.get(status));
  }

  public String getStatus() {
    return this.status;
  }
}
