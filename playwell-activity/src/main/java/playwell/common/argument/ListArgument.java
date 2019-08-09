package playwell.common.argument;

import com.alibaba.fastjson.JSONArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import playwell.common.expression.PlaywellExpressionContext;

/**
 * 列表类型的参数
 *
 * @author chihongze@gmail.com
 */
public class ListArgument extends Argument {

  private final List<Argument> args;

  public ListArgument(List<Argument> args) {
    this.args = args;
  }

  public List<Object> getValueList(PlaywellExpressionContext context) {
    if (CollectionUtils.isEmpty(args)) {
      return Collections.emptyList();
    }

    final List<Object> valueList = new ArrayList<>(args.size());
    for (Argument arg : args) {
      Object value = getVal(arg, context);
      valueList.add(value);
    }
    return valueList;
  }

  public List<Argument> getArgs() {
    return args;
  }

  public List<Object> toList() {
    if (CollectionUtils.isEmpty(args)) {
      return Collections.emptyList();
    }

    List<Object> data = new ArrayList<>(args.size());
    for (Argument arg : args) {
      if (arg instanceof ExpressionArgument) {
        data.add(((ExpressionArgument) arg).getExpression().getExpressionString());
      } else if (arg instanceof ListArgument) {
        data.add(((ListArgument) arg).toList());
      } else if (arg instanceof MapArgument) {
        data.add(((MapArgument) arg).toMap());
      }
    }
    return data;
  }

  @Override
  public String toString() {
    return JSONArray.toJSONString(toList());
  }
}
