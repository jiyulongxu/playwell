package playwell.common.expression;

import playwell.common.expression.spel.SpELPlaywellExpression;

/**
 * Playwell表达式抽象，为各种内嵌表达式的具体实现提供一个统一的抽象接口
 *
 * @author chihongze@gmail.com
 */
public interface PlaywellExpression {

  static PlaywellExpression compile(String compiler, String expression) {
    if (Compilers.SPRING_EL.equals(compiler)) {
      return new SpELPlaywellExpression(expression).compile();
    } else {
      throw new IllegalArgumentException(
          String.format("Unknown NESTS expression compiler: '%s'", compiler));
    }
  }

  /**
   * 编译表达式
   */
  PlaywellExpression compile();

  /**
   * 编译表达式，并为该表达式指定一个默认上下文，如果调用getResult的时候不指定上下文，那么将会使用此处指定的默认上下文
   *
   * @param defaultContext 默认上下文
   */
  PlaywellExpression compile(PlaywellExpressionContext defaultContext);

  /**
   * 获取表达式的原始字符串表示
   *
   * @return 表达式字符串
   */
  String getExpressionString();

  /**
   * 基于Default context，以Object对象的形式获取表达式计算结果
   *
   * @return 表达式计算结果
   */
  Object getResult();

  /**
   * 基于指定的Context，以Object对象的形式获取表达式计算结果
   *
   * @param context 上下文对象
   * @return 表达式计算结果
   */
  Object getResult(PlaywellExpressionContext context);

  /**
   * 基于指定的Context，以Object对象的形式获取表达式计算结果，如果extendsDefaultContext为false，那么 Default
   * context将会被忽略，如果extendsDefaultContext为true，那么当前context会继承Default Context，即Default中有的会被覆盖
   * 而没有的会被加入
   *
   * @param context 上下文对象
   * @param extendsDefaultContext 是否继承Default Context
   * @return 表达式计算结果
   */
  Object getResult(PlaywellExpressionContext context, boolean extendsDefaultContext);

  /**
   * 基于Default context和指定类型，获取表达式计算结果
   *
   * @param clazz 指定类型的Class对象
   * @return 表达式计算结果
   */
  <T> T getResultWithType(Class<T> clazz);

  /**
   * 基于Context和指定类型，获取表达式计算结果，如果之前在compile中指定了Default Context，那么Default Context将会被忽略
   *
   * @param context 上下文
   * @param clazz 指定类型的Class对象
   * @return 表达式计算结果
   */
  <T> T getResultWithType(PlaywellExpressionContext context, Class<T> clazz);

  /**
   * 基于Context和指定类型，获取表达式计算结果，如果之前在compile中指定了Default Context，并且extendsDefaultContext为false，那么
   * Default context将会被忽略，如果extendsDefaultContext为true，那么当前context会继承Default Context，即Default中有的会被覆盖
   * 而没有的会被加入
   *
   * @param context 上下文
   * @param clazz 指定类型的Class对象
   * @param extendsDefaultContext 是否继承Default Context
   * @return 表达式计算结果
   */
  <T> T getResultWithType(PlaywellExpressionContext context, Class<T> clazz,
      boolean extendsDefaultContext);

  interface Compilers {

    String SPRING_EL = "spring_el";
  }
}
