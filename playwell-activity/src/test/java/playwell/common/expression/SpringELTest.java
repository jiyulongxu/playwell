package playwell.common.expression;

import com.google.common.collect.ImmutableMap;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.expression.AccessException;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.EvaluationContext;
import playwell.common.expression.spel.SpELPlaywellExpression;
import playwell.common.expression.spel.SpELPlaywellExpressionContext;

/**
 * spring-el expression test case
 *
 * @author chihongze@gmail.com
 */
public class SpringELTest {

  @Test
  @SuppressWarnings({"unchecked"})
  public void testCommonEL() throws Exception {
    // basic operations and call method
    PlaywellExpression exp = new SpELPlaywellExpression("1 + 1 + 'abc'.length()");
    exp.compile();
    long beginTime = System.currentTimeMillis();
    for (int i = 0; i < 1000000; i++) {
      exp.getResult();
    }
    System.out.println(System.currentTimeMillis() - beginTime);

    // with standard context
    Student student = new Student(1, "Sam", 1);
    exp = new SpELPlaywellExpression("(id + 1) + name + grade");
    exp.compile();
    beginTime = System.currentTimeMillis();
    for (int i = 0; i < 1000000; i++) {
      PlaywellExpressionContext ctx = new SpELPlaywellExpressionContext();
      ctx.setRootObject(student);
      exp.getResult(ctx);
    }
    System.out.println(System.currentTimeMillis() - beginTime);

    // with map root context
    Map<String, Student> data = ImmutableMap.of(
        "Sam", new Student(1, "Sam", 1),
        "Jack", new Student(2, "Jack", 2)
    );
    exp = new SpELPlaywellExpression("['Sam'].id");
    exp.compile();
    beginTime = System.currentTimeMillis();
    for (int i = 0; i < 1000000; i++) {
      PlaywellExpressionContext ctx = new SpELPlaywellExpressionContext();
      ctx.setRootObject(data);
      exp.getResult(ctx);
    }
    System.out.println(System.currentTimeMillis() - beginTime);

    // with variables
    exp = new SpELPlaywellExpression("#Sam.id + #Jack.id");
    exp.compile();
    beginTime = System.currentTimeMillis();
    for (int i = 0; i < 1000000; i++) {
      PlaywellExpressionContext ctx = new SpELPlaywellExpressionContext();
      ctx.setVariable("Sam", new Student(1, "Sam", 1));
      ctx.setVariable("Jack", new Student(2, "Jack", 2));
      exp.getResult(ctx);
    }
    System.out.println(System.currentTimeMillis() - beginTime);

    // with functions
    exp = new SpELPlaywellExpression("#assertEq(1, 1)");
    exp.compile();
    Method eqMethod = Assertions.class.getMethod("assertEq", Integer.class, Integer.class);
    beginTime = System.currentTimeMillis();
    for (int i = 0; i < 1000000; i++) {
      PlaywellExpressionContext ctx = new SpELPlaywellExpressionContext();
      ctx.setFunction("assertEq", eqMethod);
      exp.getResult(ctx);
    }
    System.out.println(System.currentTimeMillis() - beginTime);

    // Inline lists
    exp = new SpELPlaywellExpression("{1, 2, 3, 4}");
    exp.compile();
    List numbers = exp.getResultWithType(List.class);
    System.out.println(numbers);

    // Operators
    Assert.assertTrue(new SpELPlaywellExpression("${2 == 2}")
        .compile().getResultWithType(Boolean.class));
    Assert.assertFalse(new SpELPlaywellExpression("${2 < -5.0}")
        .compile().getResultWithType(Boolean.class));
    Assert.assertFalse(new SpELPlaywellExpression("${true and false}")
        .compile().getResultWithType(Boolean.class));

    // Types
    exp = new SpELPlaywellExpression("${T(java.lang.Math).abs(-1)}").compile();
    Assert.assertEquals(1, exp.getResult());

    // Constructors
    exp = new SpELPlaywellExpression(
        "${(new playwell.common.expression.SpringELTest.Student(1, 'Sam', 1)).id}")
        .compile();
    int id = exp.getResultWithType(Integer.class);
    Assert.assertEquals(1, id);

    // #this variable
    List<Integer> primes = Arrays.asList(2, 3, 5, 7, 11, 13, 17);
    PlaywellExpressionContext ctx = new SpELPlaywellExpressionContext();
    ctx.setVariable("primes", primes);
    List<Integer> primesGreaterThanTen = (List<Integer>)
        new SpELPlaywellExpression("${#primes.?[#this > 10]}")
            .compile()
            .getResultWithType(ctx, List.class);
    System.out.println(primesGreaterThanTen);

    // If-Then-Else
    String falseString = new SpELPlaywellExpression("${false ? 'trueExp' : 'falseExp'}")
        .compile().getResultWithType(String.class);
    Assert.assertEquals("falseExp", falseString);

    // Elvis Operator name != null ? name : "Unknown"
    Assert.assertEquals("Unknown",
        new SpELPlaywellExpression("${null?:'Unknown'}").compile().getResultWithType(String.class));

    // Collection Selection
    Map<String, Integer> dataMap = ImmutableMap.of(
        "Sam", 1,
        "Jack", 10,
        "James", 50
    );
    ctx = new SpELPlaywellExpressionContext();
    ctx.setVariable("map", dataMap);
    Map<String, Integer> subDataMap = (Map<String, Integer>)
        new SpELPlaywellExpression("${#map.?[value >= 10]}").compile().getResult(ctx);
    System.out.println(subDataMap);

    exp = new SpELPlaywellExpression("{'a': 1, 'b': 2}");
    exp.compile();
    System.out.println(exp.getResult());
  }

  @Test
  public void testExtendsContext() {
    PlaywellExpressionContext ctx = new SpELPlaywellExpressionContext();
    ctx.setVariable("a", 1);
    ctx.setVariable("b", 2);
    PlaywellExpression exp = new SpELPlaywellExpression("${#a + #b}").compile(ctx);
    Assert.assertEquals(3, exp.getResult());
    ctx = new SpELPlaywellExpressionContext();
    ctx.setVariable("b", 100);
    Assert.assertEquals(101, exp.getResult(ctx, true));
  }

  public static class Student {

    private final int id;

    private final String name;

    private final int grade;

    public Student(int id, String name, int grade) {
      this.id = id;
      this.name = name;
      this.grade = grade;
    }

    public int getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    public int getGrade() {
      return grade;
    }
  }

  public static class Assertions {

    public static boolean assertEq(Integer a, Integer b) {
      return a == b;
    }
  }

  public static class MyBeanResolver implements BeanResolver {

    @Override
    public Object resolve(EvaluationContext context, String beanName) throws AccessException {
      if ("student".equals(beanName)) {
        return new Student(1998, "Sam", 1);
      }
      throw new AccessException("Unknown bean name: " + beanName);
    }
  }
}
