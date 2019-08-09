package playwell.message.bus;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import playwell.message.Message;

/**
 * 基于内存缓冲的MessageBus 在BufferedMessageBus当中，消息并不会立即被提交到具体的介质中， 而是会提交到内存队列，当队列中的消息数目达到了阈值或者到达了指定的时间点，那么才会被一并提交到介质
 *
 * @author chihongze@gmail.com
 */
public abstract class BufferedMessageBus extends BaseMessageBus {

  private static final Logger logger = LogManager.getLogger(BufferedMessageBus.class);

  // 内存事件缓冲区
  private final ConcurrentLinkedQueue<Message> buffer = new ConcurrentLinkedQueue<>();

  // 是否直接传输
  private boolean direct;

  // 定时器，用于每隔固定时间排空缓冲区
  private ScheduledExecutorService scheduledExecutorService;

  // 事件缓冲区最大尺寸
  private int maxBufferSize;

  // Clean buffer lock
  private Lock cleanBufferLock;

  protected BufferedMessageBus() {

  }

  /**
   * 初始化缓冲区大小以及时间间隔
   *
   * @param config 配置数据
   */
  @Override
  public void init(Object config) {
    super.init(config);
    final EasyMap configuration = (EasyMap) config;
    this.direct = configuration.getBoolean(ConfigItems.DIRECT, true);

    // 非直接传输，获取缓冲区的相关配置
    if (!direct) {
      this.cleanBufferLock = new ReentrantLock();
      this.maxBufferSize = configuration.getInt(ConfigItems.MAX_BUFFER_SIZE, 10);
      long period = configuration.getLong(ConfigItems.PERIOD, 5);

      this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
      Runtime.getRuntime().addShutdownHook(new Thread(scheduledExecutorService::shutdown));
      this.scheduledExecutorService
          .scheduleAtFixedRate(this::cleanBuffer, period, period, TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public void write(Message message) throws MessageBusNotAvailableException {
    checkAvailable();
    if (direct) {
      // 直接传输到broker中
      directWrite(Collections.singletonList(message));
    } else {
      // 添加新事件到缓冲区
      buffer.add(message);
      // 缓冲区达到了最大的长度，
      if (buffer.size() >= maxBufferSize) {
        cleanBuffer();
      }
    }
  }

  @Override
  public void write(Collection<Message> messages) throws MessageBusNotAvailableException {
    checkAvailable();
    if (direct) {
      // 直接传输到broker中
      directWrite(messages);
    } else {
      buffer.addAll(messages);
      // 缓冲区达到了最大长度
      if (buffer.size() >= maxBufferSize) {
        cleanBuffer();
      }
    }
  }

  @Override
  protected Map<String, Object> getStatus() {
    return ImmutableMap.of(
        StatusItems.BUFFER_SIZE, buffer.size()
    );
  }

  private void cleanBuffer() {
    if (cleanBufferLock.tryLock()) {
      try {
        final Collection<Message> messages = new LinkedList<>();
        while (!buffer.isEmpty()) {
          final Message message = buffer.poll();
          messages.add(message);
        }
        if (messages.size() > 0) {
          directWrite(messages);
        }
      } catch (Exception e) {
        logger.error("Error happened when clean message bus buffer.", e);
      } finally {
        cleanBufferLock.unlock();
      }
    }
  }

  /**
   * 将缓冲区的消息批量发送到broker
   *
   * @param messages 缓冲区消息
   */
  protected abstract void directWrite(Collection<Message> messages);

  // 配置项目
  interface ConfigItems {

    // 是否直接传输而不经过缓冲区
    String DIRECT = "direct";

    // 最大Buffer尺寸
    String MAX_BUFFER_SIZE = "max_buffer_size";

    // Buffer刷新周期，单位秒
    String PERIOD = "refresh_buffer_period";
  }

  // 状态条目
  interface StatusItems {

    String BUFFER_SIZE = "buffer_size";
  }
}
