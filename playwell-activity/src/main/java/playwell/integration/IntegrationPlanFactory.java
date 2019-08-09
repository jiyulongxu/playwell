package playwell.integration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.yaml.snakeyaml.Yaml;

public class IntegrationPlanFactory {

  private static final IntegrationPlanFactory INSTANCE = new IntegrationPlanFactory();

  private volatile IntegrationPlan integrationPlan = null;

  private IntegrationPlanFactory() {

  }

  public static IntegrationPlanFactory getInstance() {
    return INSTANCE;
  }

  @SuppressWarnings({"unchecked"})
  public static <T extends IntegrationPlan> T currentPlan() {
    return (T) INSTANCE.getIntegrationPlan();
  }

  @SuppressWarnings({"unchecked"})
  public synchronized void intergrateWithYamlConfigFile(String className,
      String yamlConfigFilePath) {
    if (integrationPlan != null) {
      throw new RuntimeException("The IntegrationPlan has already been intergrated!");
    }

    try {
      final Class planClass = Class.forName(className);

      if (!IntegrationPlan.class.isAssignableFrom(planClass)) {
        throw new RuntimeException(String.format(
            "Invalid IntegrationPlan class type: '%s',  must be the subclass of '%s'",
            className,
            IntegrationPlan.class.getCanonicalName()
        ));
      }

      this.integrationPlan = (IntegrationPlan) planClass.getDeclaredConstructor().newInstance();

      final String yamlContent = FileUtils.readFileToString(
          new File(yamlConfigFilePath), Charset.forName("UTF-8"));
      final Yaml yaml = new Yaml();
      final Map<String, Object> data = yaml.load(yamlContent);

      integrationPlan.intergrate(data);
    } catch (
        ClassNotFoundException
            | InstantiationException
            | IllegalAccessException
            | NoSuchMethodException
            | InvocationTargetException
            | IOException e) {
      throw new RuntimeException("Load IntegrationPlan from YAML config file failed", e);
    }
  }

  public IntegrationPlan getIntegrationPlan() {
    if (integrationPlan == null) {
      throw new RuntimeException("The IntegrationPlan has not been intergrated!");
    }

    return integrationPlan;
  }

  /**
   * 清理已经初始化的IntergrationPlan，仅用于测试
   */
  public void clean() {
    integrationPlan = null;
  }
}
