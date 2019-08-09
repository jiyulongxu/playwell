package playwell.message.bus;

import java.util.Collection;
import java.util.function.Consumer;
import playwell.common.PlaywellComponent;
import playwell.message.Message;

/**
 * 消息总线抽象，消息总线是介于Action和具体服务之间的传播媒介 Action可以通过消息总线来向服务发出异步调用。 消息总线有时也不仅仅只是异步传播消息，它还可以对消息进行过滤和归并后再发送到具体服务
 *
 * @author chihongze@gmail.com
 */
public interface MessageBus extends PlaywellComponent {

  /**
   * 获取消息总线名称，名称通常在组件初始化后被注入
   */
  String name();

  /**
   * 向消息总线传递一个消息
   *
   * @param message 被传递的消息
   */
  void write(Message message) throws MessageBusNotAvailableException;

  /**
   * 向消息总线写入一批消息
   *
   * @param messages 被传递的事件集合
   */
  void write(Collection<Message> messages) throws MessageBusNotAvailableException;

  /**
   * 从消息总线获取指定数目的未消费消息
   *
   * @return 获取到的消息
   */
  Collection<Message> read(int maxFetchNum) throws MessageBusNotAvailableException;

  /**
   * 该方法允许边从消息总线中获取消息边进行消费
   */
  int readWithConsumer(int maxFetchNum, Consumer<Message> eventConsumer)
      throws MessageBusNotAvailableException;

  /**
   * 对读取的消息进行确认，通常用于消息队列类型的MessageBus，诸如Kafka和RabbitMQ
   */
  void ackMessages();

  /**
   * 是否处于打开状态
   *
   * @return 打开状态
   */
  boolean isOpen();

  /**
   * 打开MessageBus
   */
  void open();

  /**
   * 关闭MessageBus
   */
  void close();
}
