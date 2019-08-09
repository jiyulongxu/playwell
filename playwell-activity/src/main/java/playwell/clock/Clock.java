package playwell.clock;

import java.util.Collection;
import java.util.function.Consumer;
import playwell.common.PlaywellComponent;
import playwell.message.Message;

/**
 * 时钟通知服务，传入时间点，获取截止到当前时间点的所有ClockMessage
 *
 * @author chihongze@gmail.com
 */
public interface Clock extends PlaywellComponent {

  /**
   * 注册时钟通知事件
   *
   * @param clockMessage 时钟通知事件
   */
  void registerClockMessage(ClockMessage clockMessage);

  /**
   * 获取截止到指定时间点的时钟通知
   *
   * @param untilTimePoint 截止到指定的时间点
   * @return 时钟通知事件
   */
  Collection<ClockMessage> fetchClockMessages(long untilTimePoint);

  /**
   * 对指定时间点的时钟通知进行回调消费
   *
   * @param untilTimePoint 截止到指定的时间点
   * @param consumer 时钟通知消费者
   */
  void consumeClockMessage(long untilTimePoint, Consumer<ClockMessage> consumer);

  /**
   * 清理截止到指定时间点的所有事件，防止重复获取
   *
   * @param untilTimePoint 指定的时间点
   */
  void clean(long untilTimePoint);

  /**
   * 扫描当前所有的ClockMessage，并执行回调逻辑
   *
   * @param consumer 回调逻辑
   */
  void scanAll(ClockMessageScanConsumer consumer);

  /**
   * 终止扫描
   */
  void stopScan();

  /**
   * 应用Replication消息到本地
   *
   * @param messages Replication messages
   */
  void applyReplicationMessages(Collection<Message> messages);
}
