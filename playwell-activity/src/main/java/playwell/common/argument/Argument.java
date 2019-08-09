package playwell.common.argument;

import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import playwell.common.expression.PlaywellExpression;
import playwell.common.expression.PlaywellExpressionContext;


/**
 * 动态表达式参数定义，通常用在诸如Trigger和Action中 当ActivityThread真正执行时，会按照上下文数据渲染成真正的值
 *
 * @author chihongze@gmail.com
 */
public abstract class Argument {

  public static boolean isExpressionArgument(Argument arguments) {
    return arguments.getClass() == ExpressionArgument.class;
  }

  public static boolean isListArgument(Argument arguments) {
    return arguments.getClass() == ListArgument.class;
  }

  public static boolean isMapArgument(Argument arguments) {
    return arguments.getClass() == MapArgument.class;
  }

  @SuppressWarnings({"unchecked"})
  public static Argument parse(Object rawArgument, String compiler) {
    if (rawArgument == null) {
      rawArgument = "";
    }

    if (rawArgument instanceof List) {
      List rawArgumentList = (List) rawArgument;
      final List<Argument> argumentList = new ArrayList<>(rawArgumentList.size());
      for (Object element : rawArgumentList) {
        argumentList.add(parse(element, compiler));
      }
      return new ListArgument(argumentList);
    } else if (rawArgument instanceof Map) {
      Map<String, Object> rawArgumentMap = (Map<String, Object>) rawArgument;
      final Map<String, Argument> argumentMap = Maps
          .newHashMapWithExpectedSize(rawArgumentMap.size());
      for (Map.Entry<String, Object> entry : rawArgumentMap.entrySet()) {
        argumentMap.put(entry.getKey(), parse(entry.getValue(), compiler));
      }
      return new MapArgument(argumentMap);
    } else {
      return new ExpressionArgument(PlaywellExpression.compile(compiler, rawArgument.toString()));
    }
  }

  public static Object getArgumentRepr(Argument argument) {
    if (argument instanceof ExpressionArgument) {
      return ((ExpressionArgument) argument).getExpression();
    } else if (argument instanceof ListArgument) {
      return ((ListArgument) argument).toList();
    } else if (argument instanceof MapArgument) {
      return ((MapArgument) argument).toMap();
    } else {
      throw new IllegalArgumentException(
          String.format("Unknown Argument Class: %s", argument.getClass().getName()));
    }
  }

  Object getVal(Argument arg, PlaywellExpressionContext context) {
    if (arg instanceof ExpressionArgument) {
      return ((ExpressionArgument) arg).getValue(context);
    } else if (arg instanceof ListArgument) {
      return ((ListArgument) arg).getValueList(context);
    } else if (arg instanceof MapArgument) {
      return ((MapArgument) arg).getValueMap(context);
    } else {
      throw new IllegalArgumentException(
          String.format("Unknown Argument Class: %s", arg.getClass().getName()));
    }
  }
}
