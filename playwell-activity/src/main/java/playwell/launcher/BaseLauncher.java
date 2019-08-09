package playwell.launcher;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;

/**
 * Abstract base launcher
 */
public abstract class BaseLauncher implements LauncherModule {

  private final String integrationPlanClass;

  protected BaseLauncher(String integrationPlanClass) {
    this.integrationPlanClass = integrationPlanClass;
  }

  @Override
  public Options getOptions() {
    final Options options = new Options();
    options.addOption(Option.builder("config")
        .hasArg()
        .argName("configFile")
        .required()
        .desc("The configuration file for playwell")
        .build());
    options.addOption(Option.builder("log4j")
        .hasArg()
        .argName("Log4j2ConfigFile")
        .required()
        .desc("The configuration file for log4j2")
        .build());
    return options;
  }

  @Override
  public void run(CommandLine commandLine) {
    final String configFile = commandLine.getOptionValue("config");
    final String log4jConfigFile = commandLine.getOptionValue("log4j");
    System.setProperty("log4j.configurationFile", log4jConfigFile);

    final Logger logger = LogManager.getLogger(BaseLauncher.class);
    try {
      IntegrationPlanFactory.getInstance().intergrateWithYamlConfigFile(
          this.integrationPlanClass,
          configFile
      );
      startRunner(IntegrationPlanFactory.currentPlan());
    } catch (Throwable t) {
      logger.error(t.getMessage(), t);
      System.exit(1);
    }
  }

  protected abstract void startRunner(IntegrationPlan integrationPlan);

  @Override
  public void shutdown() {
    IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    if (integrationPlan != null) {
      integrationPlan.closeAll();
    }
  }
}
