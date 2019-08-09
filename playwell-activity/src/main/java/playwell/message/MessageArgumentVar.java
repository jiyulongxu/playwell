package playwell.message;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import playwell.common.EasyMap;

/**
 * MessageArgumentVar用于渲染活动定义中的MESSAGE参数
 *
 * @author chihongze@gmail.com
 */
public class MessageArgumentVar {

  private final Message message;

  public MessageArgumentVar(Message message) {
    this.message = message;
  }

  public static MessageArgumentVar emptyMessage() {
    final String TYPE = "_EMPTY_MSG";
    return new MessageArgumentVar(new Message(TYPE, "", "", Collections.emptyMap(), 0L));
  }

  public String getType() {
    return message.getType();
  }

  public Map<String, Object> getAllAttributes() {
    return message.getAttributes();
  }

  public Object get(String propertyName) {
    return message.getAttributes().get(propertyName);
  }

  public Object get(String propertyName, Object defaultValue) {
    return message.getAttributes().getOrDefault(propertyName, defaultValue);
  }

  public int getInt(String propertyName) {
    return new EasyMap(message.getAttributes()).getInt(propertyName, 0);
  }

  public int getInt(String propertyName, int defaultValue) {
    return new EasyMap(message.getAttributes()).getInt(propertyName, defaultValue);
  }

  public List<Integer> getIntList(String propertyName) {
    return new EasyMap(message.getAttributes()).getIntegerList(propertyName);
  }

  public long getLong(String propertyName) {
    return new EasyMap(message.getAttributes()).getLong(propertyName, 0L);
  }

  public long getLong(String propertyName, long defaultValue) {
    return new EasyMap(message.getAttributes()).getLong(propertyName, defaultValue);
  }

  public List<Long> getLongList(String propertyName) {
    return new EasyMap(message.getAttributes()).getLongList(propertyName);
  }

  public boolean getBoolean(String propertyName) {
    return new EasyMap(message.getAttributes()).getBoolean(propertyName, false);
  }

  public boolean getBoolean(String propertyName, boolean defaultValue) {
    return new EasyMap(message.getAttributes()).getBoolean(propertyName, defaultValue);
  }

  public List<Boolean> getBooleanList(String propertyName) {
    return new EasyMap(message.getAttributes()).getBooleanList(propertyName);
  }

  public double getDouble(String propertyName) {
    return getDouble(propertyName, 0.0);
  }

  public double getDouble(String propertyName, double defaultValue) {
    return new EasyMap(message.getAttributes()).getDouble(propertyName, defaultValue);
  }

  public List<Double> getDoubleList(String propertyName) {
    return new EasyMap(message.getAttributes()).getDoubleList(propertyName);
  }

  public String getString(String propertyName) {
    return new EasyMap(message.getAttributes()).getString(propertyName, "");
  }

  public String getString(String propertyName, String defaultValue) {
    return new EasyMap(message.getAttributes()).getString(propertyName, defaultValue);
  }

  public List<String> getStringList(String propertyName) {
    return new EasyMap(message.getAttributes()).getStringList(propertyName);
  }

  public List<Object> getList(String propertyName) {
    return new EasyMap(message.getAttributes()).getObjectList(propertyName);
  }

  public EasyMap getSubArguments(String propertyName) {
    return new EasyMap(message.getAttributes()).getSubArguments(propertyName);
  }

  public List<EasyMap> getSubArgumentsList(String propertyName) {
    return new EasyMap(message.getAttributes()).getSubArgumentsList(propertyName);
  }

  public boolean contains(String attrName) {
    return message.getAttributes().containsKey(attrName);
  }
}
