package playwell.message.bus;

/**
 * 当MessageBus不可用的时候(比如用户手动关闭，或者系统自行检测到不可用)，会抛出该异常
 *
 * @author chihongze@gmail.com
 */
public class MessageBusNotAvailableException extends Exception {

  public MessageBusNotAvailableException(String name) {
    super(String.format(
        "The message bus '%s' is not available, may be closed or not alive", name));
  }
}
