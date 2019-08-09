package playwell.message.sys;


import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import playwell.clock.CachedTimestamp;

/**
 * 用于控制活动中单个ActivityThread运行的消息命令
 *
 * @author chihongze@gmail.com
 */
public class ActivityThreadCtrlMessage extends SysMessage {

  private final String command;

  private final Map<String, Object> args;

  public ActivityThreadCtrlMessage(
      long timestamp, String sender, String receiver, int activityId, String domainId,
      String command) {
    this(timestamp, sender, receiver, activityId, domainId, command, Collections.emptyMap());
  }

  public ActivityThreadCtrlMessage(
      long timestamp, String sender, String receiver, int activityId, String domainId,
      String command, Map<String, Object> args) {
    super(timestamp, sender, receiver, activityId, domainId,
        ImmutableMap.of(Attributes.COMMAND, command, Attributes.ARGS, args));
    this.command = command;
    this.args = args;
  }

  public static ActivityThreadCtrlMessage pauseCommand(String sender, String receiver,
      int activityId,
      String domainId) {
    return new ActivityThreadCtrlMessage(
        CachedTimestamp.nowMilliseconds(), sender, receiver, activityId, domainId, Commands.PAUSE);
  }

  public static ActivityThreadCtrlMessage continueCommand(String sender, String receiver,
      int activityId, String domainId) {
    return new ActivityThreadCtrlMessage(
        CachedTimestamp.nowMilliseconds(), sender, receiver, activityId, domainId,
        Commands.CONTINUE);
  }

  public static ActivityThreadCtrlMessage killCommand(String sender, String receiver,
      int activityId, String domainId) {
    return new ActivityThreadCtrlMessage(
        CachedTimestamp.nowMilliseconds(), sender, receiver, activityId, domainId, Commands.KILL);
  }

  public static ActivityThreadCtrlMessage repairCommand(
      String sender, String receiver, int activityId, String domainId, RepairArguments arguments) {
    return new ActivityThreadCtrlMessage(
        CachedTimestamp.nowMilliseconds(), sender, receiver, activityId, domainId, Commands.REPAIR,
        arguments.toMap());
  }

  public String getCommand() {
    return this.command;
  }

  public Map<String, Object> getArgs() {
    return this.args;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ActivityThreadCtrlMessage)) {
      return false;
    }
    ActivityThreadCtrlMessage that = (ActivityThreadCtrlMessage) o;
    return getActivityId() == that.getActivityId() &&
        Objects.equals(getDomainId(), that.getDomainId()) &&
        Objects.equals(getCommand(), that.getCommand());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getActivityId(), getDomainId(), getCommand());
  }

  public interface Attributes {

    String COMMAND = "command";

    String ARGS = "args";
  }

  public interface Commands {

    String PAUSE = "pause";

    String CONTINUE = "continue";

    String KILL = "kill";

    String REPAIR = "repair";
  }
}
