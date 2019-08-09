package playwell.message;

/**
 * ActivityThreadMessage针对明确的ActivityThread进行投放的消息
 *
 * @author chihongze@gmail.com
 */
public interface ActivityThreadMessage extends DomainMessage {

  int getActivityId();
}
