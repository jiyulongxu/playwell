package playwell.message.bus;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import playwell.common.PlaywellComponent;
import playwell.common.Result;

/**
 * 消息总线管理器
 *
 * @author chihongze@gmail.com
 */
public interface MessageBusManager extends PlaywellComponent {

  /**
   * 创建新的临时MessageBus对象，由调用者自行管理，不纳入统一管理周期中
   *
   * @param clazz MessageBus class
   * @param config MessageBus config
   * @return new MessageBus object
   */
  MessageBus newMessageBus(String clazz, Map<String, Object> config);

  /**
   * 注册新的MessageBus对象
   *
   * @param clazz 使用的类
   * @param config 配置信息
   * @return 注册结果
   */
  Result registerMessageBus(String clazz, Map<String, Object> config);

  /**
   * 打开MessageBus
   *
   * @param name MessageBus名称
   * @return 操作结果
   */
  Result openMessageBus(String name);

  /**
   * 关闭MessageBus
   *
   * @param name MessageBus名称
   * @return 操作结果
   */
  Result closeMessageBus(String name);

  /**
   * 获取所有已经注册的MessageBus
   *
   * @return MessageBus Collection
   */
  Collection<MessageBus> getAllMessageBus();

  /**
   * 根据名称获取MessageBus对象
   *
   * @param name MessageBus name
   * @return MessageBus
   */
  Optional<MessageBus> getMessageBusByName(String name);

  /**
   * 删除MessageBus
   *
   * @param name MessageBus名称
   * @return 删除操作结果
   */
  Result deleteMessageBus(String name);

  /**
   * 错误代码
   */
  interface ErrorCodes {

    String EXISTED = "existed";  // 当MessageBus已经存在的时候返回该错误码

    String LOAD_ERROR = "load_error"; // 加载MessageBus实例的时候出现了错误

    String NOT_FOUND = "not_found";  // MessageBus不存在

    String ALREADY_OPENED = "already_opened";

    String ALREADY_CLOSED = "already_closed";
  }
}
