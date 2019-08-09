package playwell.common;

import java.util.Map;

/**
 * 可以将自身对象属性序列化成Map的对象可以实现该接口 对输出Map的要求，Key必须是字符串类型，Object必须是基本类型，字符串以及嵌套的List和Map，
 * 其中，List和Map中包含的元素也必须是这些类型
 *
 * @author chihongze@gmail.com
 */
public interface Mappable {

  Map<String, Object> toMap();
}
