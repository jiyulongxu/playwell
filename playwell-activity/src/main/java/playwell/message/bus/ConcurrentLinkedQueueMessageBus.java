package playwell.message.bus;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import org.apache.commons.collections4.CollectionUtils;
import playwell.common.EasyMap;
import playwell.message.Message;

/**
 * 基于JVM ConcurrentLinkedQueue实现的MessageBus，通常用于测试和系统内部集成
 *
 * @author chihongze@gmail.com
 */
public class ConcurrentLinkedQueueMessageBus extends BaseMessageBus {

  private final ConcurrentLinkedQueue<Message> queue = new ConcurrentLinkedQueue<>();

  public ConcurrentLinkedQueueMessageBus() {

  }

  @Override
  protected void initMessageBus(EasyMap configuration) {
    // Do nothing
  }

  @Override
  public void write(Message message) {
    queue.add(message);
  }

  @Override
  public void write(Collection<Message> messages) {
    if (CollectionUtils.isNotEmpty(messages)) {
      queue.addAll(messages);
    }
  }

  @Override
  public Collection<Message> read(int maxFetchNum) {
    final List<Message> messageBuffer = new LinkedList<>();
    int fetchedNum = 0;
    while (!queue.isEmpty() && fetchedNum++ < maxFetchNum) {
      messageBuffer.add(queue.poll());
    }
    return messageBuffer;
  }

  @Override
  public int readWithConsumer(int maxFetchNum, Consumer<Message> eventConsumer) {
    int fetchedNum = 0;
    while (!queue.isEmpty() && fetchedNum++ < maxFetchNum) {
      eventConsumer.accept(queue.poll());
    }
    return fetchedNum;
  }

  // 清理剩余的所有消息，仅供测试使用
  public void cleanAll() {
    this.queue.clear();
  }
}
