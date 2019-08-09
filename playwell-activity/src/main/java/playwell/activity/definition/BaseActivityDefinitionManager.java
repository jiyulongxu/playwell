package playwell.activity.definition;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.common.exception.BuildComponentException;
import playwell.integration.IntergrationUtils;

/**
 * ActivityDefinitionManager基类
 *
 * @author chihongze@gmail.com
 */
public abstract class BaseActivityDefinitionManager implements ActivityDefinitionManager {

  private static final Logger logger = LogManager.getLogger(BaseActivityDefinitionManager.class);

  // 所有的ActivityDefinition编解码器
  private final Map<String, ActivityDefinitionCodec> allCodecs;

  protected BaseActivityDefinitionManager() {
    allCodecs = new HashMap<>();
  }

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    initActivityDefinitionManager(configuration);
    IntergrationUtils.loadAndInitSubComponents(configuration.getObjectList("codecs"))
        .forEach(codecObj -> {
          final ActivityDefinitionCodec codec = (ActivityDefinitionCodec) codecObj;
          allCodecs.put(codec.name(), codec);
        });
  }

  protected abstract void initActivityDefinitionManager(EasyMap config);

  @Override
  public Result validateActivityDefinition(String codec, String definitionString) {
    // 只对定义进行检查，不需要传递版本信息
    final Date now = new Date();
    return validateActivityDefinition(codec, "unknown", false, definitionString, now, now);
  }

  protected Result validateActivityDefinition(
      String codec, String version, boolean enable, String definitionString, Date createdOn,
      Date updatedOn) {

    try {
      final ActivityDefinitionCodec definitionCodec = this.getCodec(codec);
      final ActivityDefinition definition = definitionCodec.decode(
          version, enable, definitionString, createdOn, updatedOn);
      return Result.okWithData(Collections.singletonMap(ResultFields.DEFINITION, definition));
    } catch (BuildComponentException e) {
      logger.error("Build ActivityDefinition Error", e);
      return Result.failWithCodeAndMessage(ErrorCodes.PARSE_ERROR, e.getMessage());
    } catch (Exception e) {
      logger.error("Build ActivityDefinition Error", e);
      return Result.failWithCodeAndMessage(
          ErrorCodes.PARSE_ERROR, "Build ActivityDefinition Error: " + e.getMessage());
    }
  }

  protected ActivityDefinitionCodec getCodec(String codecName) {
    final ActivityDefinitionCodec codec = allCodecs.get(codecName);
    if (codec == null) {
      throw new BuildComponentException(
          String.format("Unknown ActivityDefinitionCodec: %s", codecName));
    }
    return codec;
  }
}
