package playwell.activity.definition;

import java.util.Date;
import playwell.common.PlaywellComponent;
import playwell.common.exception.BuildComponentException;

/**
 * 活动定义解码器，可以将各种格式的活动定义，转换成ActivityDefinition对象
 *
 * @author chihongze@gmail.com
 */
public interface ActivityDefinitionCodec extends PlaywellComponent {

  /**
   * codec名称
   */
  String name();

  /**
   * 将字符串的定义转换成ActivityDefinition对象
   *
   * @param version 定义版本
   * @param enable 是否启用该版本
   * @param definitionText 字符串定义
   * @return ActivityDefinition对象
   * @throws BuildComponentException 构建出错时，抛出此异常
   */
  ActivityDefinition decode(
      String version, boolean enable, String definitionText, Date createdOn, Date updatedOn)
      throws BuildComponentException;
}
