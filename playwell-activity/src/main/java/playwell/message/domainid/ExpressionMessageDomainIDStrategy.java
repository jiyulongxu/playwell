package playwell.message.domainid;

import com.alibaba.fastjson.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import playwell.common.EasyMap;
import playwell.common.Mappable;
import playwell.common.argument.BaseArgumentRootContext;
import playwell.common.argument.EventVarAccessMixin;
import playwell.common.expression.PlaywellExpression;
import playwell.common.expression.PlaywellExpressionContext;
import playwell.common.expression.spel.SpELPlaywellExpression;
import playwell.common.expression.spel.SpELPlaywellExpressionContext;
import playwell.message.Message;
import playwell.message.MessageArgumentVar;

/**
 * 基于playwell内建表达式的DomainID提取策略
 *
 * @author chihongze
 */
public class ExpressionMessageDomainIDStrategy implements MessageDomainIDStrategy, Mappable {

  private String name;

  private PlaywellExpression condExpr;

  private PlaywellExpression domainIdExpr;

  public ExpressionMessageDomainIDStrategy() {

  }

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    this.name = configuration.getString(ConfigItems.NAME);
    this.condExpr = new SpELPlaywellExpression(
        configuration.get(ConfigItems.COND).toString());
    this.condExpr.compile();
    this.domainIdExpr = new SpELPlaywellExpression(
        configuration.get(ConfigItems.DOMAIN_ID).toString());
    this.domainIdExpr.compile();
  }

  @Override
  public String name() {
    return name;
  }

  public PlaywellExpression getCondExpr() {
    return condExpr;
  }

  public PlaywellExpression getDomainIdExpr() {
    return domainIdExpr;
  }

  @Override
  public Optional<String> domainId(Message message) {
    final PlaywellExpressionContext context = new SpELPlaywellExpressionContext();
    context.setRootObject(new MessageDomainIDStrategyExpressionContextRoot(message));
    final boolean matched = (boolean) condExpr.getResult(context);
    if (matched) {
      return Optional.of(domainIdExpr.getResult(context).toString());
    }
    return Optional.empty();
  }

  @Override
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>(3);
    map.put(ConfigItems.NAME, this.name);
    if (condExpr != null) {
      map.put(ConfigItems.COND, condExpr.getExpressionString());
    } else {
      map.put(ConfigItems.COND, "");
    }
    if (domainIdExpr != null) {
      map.put(ConfigItems.DOMAIN_ID, domainIdExpr.getExpressionString());
    } else {
      map.put(ConfigItems.DOMAIN_ID, "");
    }
    return map;
  }

  @Override
  public String toString() {
    return new JSONObject(toMap()).toString();
  }

  // 配置项
  interface ConfigItems {

    String NAME = "name";

    String COND = "cond";

    String DOMAIN_ID = "domain_id";
  }

  public static class MessageDomainIDStrategyExpressionContextRoot extends BaseArgumentRootContext
      implements EventVarAccessMixin {

    public final MessageArgumentVar event;

    public MessageDomainIDStrategyExpressionContextRoot(Message event) {
      this.event = new MessageArgumentVar(event);
    }

    @Override
    public MessageArgumentVar getEvent() {
      return event;
    }
  }
}
