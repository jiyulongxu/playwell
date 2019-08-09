package playwell.message.domainid;


import java.util.Collection;
import playwell.common.PlaywellComponent;
import playwell.common.Result;

/**
 * MessageDomainIDStrategy管理器
 *
 * @author chihongze@gmail.com
 */
public interface MessageDomainIDStrategyManager extends PlaywellComponent {

  /**
   * 添加新的DomainID策略
   *
   * @param name 策略名称
   * @param condExpr 条件表达式
   * @param domainIdExpr DomainID表达式
   * @return 操作结果
   */
  Result addMessageDomainIDStrategy(String name, String condExpr, String domainIdExpr);

  /**
   * 根据索引移除一个MessageDomainIDStrategy
   *
   * @param name 策略名称
   * @return 移除结果
   */
  Result removeMessageDomainIDStrategy(String name);

  /**
   * 获取所有的MessageDomainIDStrategy
   *
   * @return 所有的MessageDomainIDStrategy集合
   */
  Collection<MessageDomainIDStrategy> getAllMessageDomainIDStrategies();

  interface ErrorCodes {

    String ALREADY_EXISTED = "already_existed";

    String NOT_FOUND = "not_found";

    String SERVICE_ERROR = "service_error";
  }
}
