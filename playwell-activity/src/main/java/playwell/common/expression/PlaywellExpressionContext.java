package playwell.common.expression;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/**
 * Playwell表达式上下文抽象
 *
 * @author chihongze@gmail.com
 */
public interface PlaywellExpressionContext {

  /**
   * 获取Global Object
   *
   * @return 获取设置的Nests全局对象
   */
  Optional<Object> getRootObject();

  /**
   * 设置Nests表达式的全局对象
   *
   * @param rootObject 全局对象
   */
  void setRootObject(Object rootObject);

  /**
   * 向Nests表达式中添加函数
   *
   * @param name 函数名称
   * @param mtd 函数对应的Java方法对象
   */
  void setFunction(String name, Method mtd);

  /**
   * 获取指定的函数名称
   *
   * @param name 函数名称
   * @return 函数名称对应的Java方法对象
   */
  Optional<Method> getFunction(String name);

  /**
   * 获取所有已经注册过的函数
   */
  Map<String, Method> getAllRegisteredFunctions();

  /**
   * 向Nests表达式中添加变量
   *
   * @param name 变量名称
   * @param value 变量值
   */
  void setVariable(String name, Object value);

  /**
   * 获取指定的变量值
   *
   * @param name 变量名称
   * @return 变量值
   */
  Optional<Object> getVariable(String name);

  /**
   * 获取已经注册过的所有变量
   */
  Map<String, Object> getAllRegisteredVariables();
}
