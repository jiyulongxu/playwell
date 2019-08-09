package playwell.activity.definition;

import java.util.Collection;
import java.util.Optional;
import playwell.common.PlaywellComponent;
import playwell.common.Result;

/**
 * 活动定义管理器，可以用于验证、创建以及获取活动定义
 *
 * @author chihongze@gmail.com
 */
public interface ActivityDefinitionManager extends PlaywellComponent {

  /**
   * 验证活动定义是否正确
   *
   * @param codec 编码方式
   * @param definitionString 活动定义字符串
   * @return 工作流定义验证结果
   */
  Result validateActivityDefinition(String codec, String definitionString);

  /**
   * 创建新版本的活动定义
   *
   * @param codec 编码方式
   * @param version 版本号
   * @param definitionString 活动定义字符串
   * @param enable 是否可用
   * @return 工作流定义验证结果
   */
  Result newActivityDefinition(
      String codec, String version, String definitionString, boolean enable);

  /**
   * 开启版本定义的enable开关
   *
   * @param name 定义名称
   * @param version 定义版本
   * @return 操作结果
   */
  Result enableActivityDefinition(String name, String version);

  /**
   * 关闭版本定义的enable开关
   *
   * @param name 定义名称
   * @param version 定义版本
   * @return 操作结果
   */
  Result disableActivityDefinition(String name, String version);

  /**
   * 修改指定版本的活动定义，只有在确定不会影响活动执行流程的时候才允许修改
   *
   * @param codec 使用的编码
   * @param version 版本号
   * @param definitionString 活动定义字符串
   * @param enable 是否可用
   * @return 修改结果
   */
  Result modifyActivityDefinition(
      String codec, String version, String definitionString, boolean enable);

  /**
   * 获取所有最新版本的ActivityDefinition
   *
   * @return All latest ActivityDefinition
   */
  Collection<ActivityDefinition> getAllLatestDefinitions();

  /**
   * 根据定义名称获取所有版本的ActivityDefinition
   *
   * @param name 活动定义名称
   * @return 各个版本的ActivityDefinition
   */
  Collection<ActivityDefinition> getActivityDefinitionsByName(String name);

  /**
   * 根据定义名称获取一个最新可用版本的ActivityDefinition
   *
   * @param name 活动定义名称
   * @return ActivityDefinition Optional
   */
  Optional<ActivityDefinition> getLatestEnableActivityDefinition(String name);

  /**
   * 根据名称和版本来获取一个确切的ActivityDefinition对象
   *
   * @param name 名称
   * @param version 版本
   * @return ActivityDefinition Optional
   */
  Optional<ActivityDefinition> getActivityDefinition(String name, String version);

  /**
   * 删除某个版本的活动定义，当活动定义被删除后，所有基于该版本的执行的ActivityThread也会终止。
   *
   * @param name 定义名称
   * @param version 版本
   * @return 删除结果
   */
  Result deleteActivityDefinition(String name, String version);


  interface ResultFields {

    String DEFINITION = "definition";

  }

  interface ErrorCodes {

    String ALREADY_EXIST = "already_exist";

    String PARSE_ERROR = "parse_error";

    String NOT_FOUND = "not_found";

    String INVALID_STATUS = "invalid_status";
  }
}
