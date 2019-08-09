package playwell.common.argument;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import playwell.common.expression.PlaywellExpression;
import playwell.common.expression.spel.SpELPlaywellExpressionContext;

/**
 * TestCase of ExpressionArgument、MapArgument、ListArgument
 *
 * @author chihongze@gmail.com
 */
public class ArgumentTestCase {

  @Test
  public void testArgument() {
    final MapArgument mapArgument = (MapArgument) Argument.parse(
        ImmutableMap.of(
            "a", "${1 + 1}",
            "b", Arrays.asList(
                "${3 + 4}",
                ImmutableMap.of(
                    "a", "${'hello'}",
                    "b", "Hello${5 + 10}"
                )
            ),
            "c", ImmutableMap.of("a", "${2 + 2}")
        ),
        PlaywellExpression.Compilers.SPRING_EL
    );
    Map<String, Object> data = mapArgument.getValueMap(new SpELPlaywellExpressionContext());
    System.out.println(JSON.toJSONString(data));
    System.out.println(mapArgument.toMap());

    final ListArgument listArgument = (ListArgument) Argument.parse(
        Arrays.asList(
            "${1 + 2}",
            "${2 + 3}",
            ImmutableMap.of(
                "a", "${'hello'}",
                "b", "Hello${5 + 10}"
            ),
            Arrays.asList(
                "${3 + 4}",
                ImmutableMap.of(
                    "a", "${'hello'}",
                    "b", "Hello${5 + 10}"
                )
            )
        ),
        PlaywellExpression.Compilers.SPRING_EL);
    List<Object> dataList = listArgument.getValueList(new SpELPlaywellExpressionContext());
    System.out.println(JSONArray.toJSONString(dataList));
    System.out.println(listArgument.toList());
  }
}
