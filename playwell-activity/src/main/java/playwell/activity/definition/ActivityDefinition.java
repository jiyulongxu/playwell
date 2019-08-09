package playwell.activity.definition;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import playwell.action.ActionDefinition;
import playwell.common.AbstractComponentBuilder;
import playwell.common.Mappable;
import playwell.common.exception.BuildComponentException;
import playwell.trigger.TriggerDefinition;
import playwell.util.DateUtils;
import playwell.util.Regexpr;
import playwell.util.validate.Field;

/**
 * Activity定义，描述了一个Activity定义所需要具备的各种属性
 *
 * @author chihongze@gmail.com
 */
public class ActivityDefinition implements Mappable {

  // 定义名称
  private final String name;

  // 版本，每次修改，会生成一个新的版本，name和version是在系统中引用一个唯一ActivityDefinition的依据
  private final String version;

  // 使用编码
  private final String codec;

  // DomainID获取策略
  private final String domainIdStrategy;

  // 用于显示的FlowDefinition名称
  private final String displayName;

  // 定义描述
  private final String description;

  // 触发器
  private final TriggerDefinition triggerDefinition;

  // 行为动作编排
  private final List<ActionDefinition> actionDefinitions;

  private final Map<String, ActionDefinition> actionDefinitionsMap;

  // 该版本是否可用
  private final boolean enable;

  // Flow定义的其它用户自定义配置
  private final Map<String, Object> config;

  // Flow定义字符串
  private final String activityDefinitionString;

  // 创建时间
  private final Date createdOn;

  // 上次更新时间
  private final Date updatedOn;

  public ActivityDefinition(
      String name, String version, String codec, String domainIdStrategy, String displayName,
      String description, TriggerDefinition triggerDefinition,
      List<ActionDefinition> actionDefinitions,
      boolean enable, Map<String, Object> config, String activityDefinitionString, Date createdOn,
      Date updatedOn) {
    this.name = name;
    this.version = version;
    this.codec = codec;
    this.domainIdStrategy = domainIdStrategy;
    this.displayName = displayName;
    this.description = description;
    this.triggerDefinition = triggerDefinition;
    this.actionDefinitions = actionDefinitions;
    this.actionDefinitionsMap =
        CollectionUtils.isEmpty(actionDefinitions) ? Collections.emptyMap() :
            actionDefinitions.stream()
                .collect(Collectors.toMap(ActionDefinition::getName, Function.identity()));
    this.enable = enable;
    this.config = config;
    this.activityDefinitionString = activityDefinitionString;
    this.createdOn = createdOn;
    this.updatedOn = updatedOn;
  }

  /**
   * 使用Builder来构建ActivityDefinition对象
   *
   * @param name ActivityDefinition名称
   * @param triggerDefinition 触发器定义
   * @param activityDefinitionString ActivityDefinition字符串
   * @return FlowDefinitionBuilder对象
   */
  public static ActivityDefinitionBuilder builder(
      String name, String version, String codec, String domainIdStrategy,
      TriggerDefinition triggerDefinition, boolean enable,
      String activityDefinitionString, Date createdOn, Date updatedOn) {
    return new ActivityDefinitionBuilder(
        name,
        version,
        codec,
        domainIdStrategy,
        triggerDefinition,
        enable,
        activityDefinitionString,
        createdOn,
        updatedOn
    );
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }

  public String getCodec() {
    return codec;
  }

  public String getDomainIdStrategy() {
    return domainIdStrategy;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getDescription() {
    return description;
  }

  public TriggerDefinition getTriggerDefinition() {
    return triggerDefinition;
  }

  public List<ActionDefinition> getActionDefinitions() {
    return actionDefinitions;
  }

  public ActionDefinition getActionDefinitionByName(String name) {
    return actionDefinitionsMap.get(name);
  }

  public boolean isEnable() {
    return enable;
  }

  public Map<String, Object> getConfig() {
    return config;
  }

  public String getActivityDefinitionString() {
    return activityDefinitionString;
  }

  public Date getCreatedOn() {
    return createdOn;
  }

  public Date getUpdatedOn() {
    return updatedOn;
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.<String, Object>builder()
        .put(Fields.NAME, this.name)
        .put(Fields.VERSION, this.version)
        .put(Fields.CODEC, this.codec)
        .put(Fields.DOMAIN_ID_STRATEGY, this.domainIdStrategy)
        .put(Fields.DISPLAY_NAME, this.displayName)
        .put(Fields.DESCRIPTION, this.description)
        .put(Fields.ENABLE, enable)
        .put(Fields.CONFIG, this.config)
        .put(Fields.CREATED_ON, DateUtils.format(this.createdOn))
        .put(Fields.UPDATED_ON, DateUtils.format(this.updatedOn))
        .put(Fields.DEFINITION_STRING, activityDefinitionString)
        .build();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ActivityDefinition)) {
      return false;
    }
    ActivityDefinition that = (ActivityDefinition) o;
    return getName().equals(that.getName()) &&
        getVersion().equals(that.getVersion());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getVersion());
  }

  @Override
  public String toString() {
    final String dataText = JSON.toJSONString(toMap());
    return String.format("ActivityDefinition@%d%s", System.identityHashCode(this), dataText);
  }

  public interface Fields {

    String NAME = "name";
    Field NAME_RULE = Field.builder(NAME)
        .required(true).regex(Regexpr.NESTS_INDENTIFIER_PATTERN).build();

    String VERSION = "version";
    Field VERSION_RULE = Field.builder(VERSION)
        .regex(Pattern.compile("^[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)*$")).required(true).build();

    String CODEC = "codec";
    Field CODEC_RULE = Field.builder(CODEC).required(true).build();

    String DOMAIN_ID_STRATEGY = "domain_id_strategy";
    Field DOMAIN_ID_STRATEGY_RULE = Field.builder(VERSION)
        .regex(Pattern.compile("^[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)*$")).required(true).build();

    String DISPLAY_NAME = "display_name";
    Field DISPLAY_NAME_RULE = Field.builder(DISPLAY_NAME).required(false).defaultValue("")
        .build();

    String DESCRIPTION = "description";
    Field DESCRIPTION_RULE = Field.builder(DESCRIPTION).required(false).defaultValue("").build();

    String ENABLE = "enable";

    String CONFIG = "config";

    String CREATED_ON = "created_on";

    String UPDATED_ON = "updated_on";

    String DEFINITION_STRING = "definition_string";
  }

  /**
   * Builder for the activity definition Not thread safe
   */
  public static class ActivityDefinitionBuilder extends
      AbstractComponentBuilder<ActivityDefinition> {

    private final String name;

    private final String version;

    private final String domainIdStrategy;

    private final String codec;

    private final String activityDefinitionString;

    private final TriggerDefinition triggerDefinition;

    private final List<ActionDefinition> actionDefinitions = new ArrayList<>();

    private final boolean enable;

    private final Map<String, Object> config = new HashMap<>();

    private final Date createdOn;

    private final Date updatedOn;

    private String displayName;

    private String description;

    public ActivityDefinitionBuilder(
        String name, String version, String domainIdStrategy, String codec,
        TriggerDefinition triggerDefinition, boolean enable, String activityDefinitionString,
        Date createdOn, Date updatedOn) {
      this.name = checkField(
          name,
          Fields.NAME_RULE,
          e -> String.format("构建活动定义出错，名称不合法。 %s", e.getMessage()));
      this.version = checkField(
          version,
          Fields.VERSION_RULE,
          e -> String.format("构建活动定义出错，版本不合法。 %s", e.getMessage()));
      this.domainIdStrategy = checkField(
          domainIdStrategy,
          Fields.DOMAIN_ID_STRATEGY_RULE,
          e -> String.format("构建活动定义出错，DomainID策略不合法。%s", e.getMessage())
      );
      this.codec = checkField(
          codec,
          Fields.CODEC_RULE,
          e -> String.format("构建活动定义出错，编码不合法。%s", e.getMessage()));
      this.enable = enable;
      this.triggerDefinition = triggerDefinition;
      this.activityDefinitionString = activityDefinitionString;
      this.createdOn = createdOn;
      this.updatedOn = updatedOn;
    }

    public ActivityDefinitionBuilder displayName(String displayName) {
      this.displayName = checkField(
          displayName,
          Fields.DISPLAY_NAME_RULE,
          e -> String.format("构建名为 '%s' 的活动定义出错。 %s", name, e.getMessage()));
      return this;
    }

    public ActivityDefinitionBuilder description(String description) {
      this.description = checkField(
          description,
          Fields.DESCRIPTION_RULE,
          e -> String.format("构建名为 '%s' 的活动定义出错。 %s", name, e.getMessage()));
      return this;
    }

    public ActivityDefinitionBuilder addAction(ActionDefinition actionDefinition) {
      final String actionName = actionDefinition.getName();
      final boolean alreadyExists = actionDefinitions.stream().anyMatch(
          a -> a.getName().equals(actionName));
      if (alreadyExists) {
        throw new BuildComponentException(
            String.format("构建名为 '%s' 的活动定义出错。Action '%s' 已经存在。",
                name, actionName));
      }

      actionDefinitions.add(actionDefinition);
      return this;
    }

    public ActivityDefinitionBuilder addConfigItem(String itemKey, Object itemValue) {
      this.config.put(itemKey, itemValue);
      return this;
    }

    public ActivityDefinitionBuilder addConfigItems(Map<String, Object> configItems) {
      if (MapUtils.isNotEmpty(configItems)) {
        config.putAll(configItems);
      }
      return this;
    }

    public ActivityDefinition build() {
      return new ActivityDefinition(
          name,
          version,
          domainIdStrategy,
          codec,
          StringUtils.isEmpty(displayName) ? name : displayName,
          StringUtils.isEmpty(description) ? "" : description,
          triggerDefinition,
          actionDefinitions,
          enable,
          config,
          activityDefinitionString,
          createdOn,
          updatedOn
      );
    }
  }
}
