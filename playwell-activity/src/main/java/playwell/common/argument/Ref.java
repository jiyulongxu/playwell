package playwell.common.argument;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import playwell.common.Mappable;

/**
 * Ref用于在上下文中引用外部的数据
 */
public class Ref implements Mappable {

  // 引用类型
  private final String type;

  // 引用名称
  private final String name;

  // 引用元数据
  private final Map<String, Object> meta;

  // 引用的是否为临时资源
  private final boolean tmp;


  public Ref(String type, String name, Map<String, Object> meta, boolean tmp) {
    this.type = type;
    this.name = name;
    this.meta = meta;
    this.tmp = tmp;
  }

  public String getType() {
    return this.type;
  }

  public String getName() {
    return this.name;
  }

  public Map<String, Object> getMeta() {
    return meta;
  }

  public boolean isTmp() {
    return this.tmp;
  }

  public List<Object> toSequence() {
    return Arrays.asList(type, name, meta, tmp);
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.of(
        "type", this.type,
        "name", this.name,
        "meta", this.meta,
        "tmp", this.tmp
    );
  }

  public String toString() {
    return JSONObject.toJSONString(toMap());
  }
}
