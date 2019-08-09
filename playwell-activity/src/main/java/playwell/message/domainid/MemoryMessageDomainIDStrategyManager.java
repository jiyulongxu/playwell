package playwell.message.domainid;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import playwell.common.EasyMap;
import playwell.common.Result;

/**
 * 基于内存的MessageDomainIDStrategyManager， 从配置文件初始化，并可以从内存动态添加/删除新的策略
 *
 * @author chihongze@gmail.com
 */
public class MemoryMessageDomainIDStrategyManager implements MessageDomainIDStrategyManager {

  private final List<MessageDomainIDStrategy> allStrategies = new ArrayList<>();

  public MemoryMessageDomainIDStrategyManager() {

  }

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    configuration.getSubArgumentsList(ConfigItems.STRATEGIES).forEach(strategyConf -> {
      final MessageDomainIDStrategy domainIDStrategy = new ExpressionMessageDomainIDStrategy();
      domainIDStrategy.init(strategyConf);
      allStrategies.add(domainIDStrategy);
    });
  }

  @Override
  public synchronized Result addMessageDomainIDStrategy(
      String name, String condExpr, String domainIdExpr) {
    MessageDomainIDStrategy messageDomainIDStrategy = new ExpressionMessageDomainIDStrategy();
    messageDomainIDStrategy.init(new EasyMap(ImmutableMap.of(
        ExpressionMessageDomainIDStrategy.ConfigItems.NAME, name,
        ExpressionMessageDomainIDStrategy.ConfigItems.COND, condExpr,
        ExpressionMessageDomainIDStrategy.ConfigItems.DOMAIN_ID, domainIdExpr
    )));
    allStrategies.add(messageDomainIDStrategy);
    return Result.ok();
  }

  @Override
  public synchronized Result removeMessageDomainIDStrategy(String name) {
    final boolean removed = allStrategies.removeIf(strategy -> strategy.name().equals(name));
    if (removed) {
      return Result.ok();
    } else {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND,
          String.format("The strategy name '%s' not found.", name)
      );
    }
  }

  @Override
  public synchronized Collection<MessageDomainIDStrategy> getAllMessageDomainIDStrategies() {
    return allStrategies;
  }

  // 配置项
  interface ConfigItems {

    String STRATEGIES = "strategies";
  }
}
