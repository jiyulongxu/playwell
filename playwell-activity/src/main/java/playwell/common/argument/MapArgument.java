package playwell.common.argument;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.collections4.MapUtils;
import playwell.common.expression.PlaywellExpressionContext;

/**
 * 字典类型的参数
 *
 * @author chihongze@gmail.com
 */
public class MapArgument extends Argument {

  private final Map<String, Argument> args;

  public MapArgument(Map<String, Argument> args) {
    this.args = args;
  }

  public boolean containsArg(String argName) {
    return args.containsKey(argName);
  }

  public Map<String, Object> getValueMap(PlaywellExpressionContext context) {
    if (MapUtils.isEmpty(args)) {
      return Collections.emptyMap();
    }

    final Map<String, Object> renderedArgs = Maps.newHashMapWithExpectedSize(args.size());
    for (Map.Entry<String, Argument> entry : args.entrySet()) {
      String argName = entry.getKey();
      Argument arg = entry.getValue();
      Object value = getVal(arg, context);
      renderedArgs.put(argName, value);
    }
    return renderedArgs;
  }

  public Map<String, Argument> getArgs() {
    return args;
  }

  public Map<String, Object> toMap() {
    if (MapUtils.isEmpty(args)) {
      return Collections.emptyMap();
    }

    Map<String, Object> data = Maps.newHashMapWithExpectedSize(args.size());
    for (Map.Entry<String, Argument> entry : args.entrySet()) {
      String argName = entry.getKey();
      Argument arg = entry.getValue();
      if (arg instanceof ExpressionArgument) {
        data.put(argName, ((ExpressionArgument) arg).getExpression().getExpressionString());
      } else if (arg instanceof ListArgument) {
        data.put(argName, ((ListArgument) arg).toList());
      } else if (arg instanceof MapArgument) {
        data.put(argName, ((MapArgument) arg).toMap());
      }
    }
    return data;
  }

  @Override
  public String toString() {
    return JSONObject.toJSONString(toMap());
  }
}
