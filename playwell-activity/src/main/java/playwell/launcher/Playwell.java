package playwell.launcher;

import java.util.Arrays;
import java.util.Optional;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Playwell launcher
 *
 * <pre>
 * java playwell.jar activity -config config.yml -log4j log4j2.xml
 * java playwell.jar service -config config.yml -log4j log4j2.xml
 * java playwell.jar route -config config.yml -log4j log4j2.xml
 * java playwell.jar clock -config config.yml -log4j log4j2.xml
 * java playwell.jar activity_replication -config config.yml -log4j log4j2.xml
 * java playwell.jar clock_replication -config config.yml -log4j log4j2.xml
 * </pre>
 */
public final class Playwell {

  private static final LauncherModule[] MODULES = new LauncherModule[]{
      new ActivityRunnerLauncher(),
      new RouteLauncher(),
      new ServiceRunnerLauncher(),
      new ClockRunnerLauncher(),
      new ActivityReplicationRunnerLauncher(),
      new ClockReplicationRunnerLauncher()
  };

  public static void main(String[] args) throws Exception {
    if (ArrayUtils.isEmpty(args)) {
      System.out.println("Invalid command args, for example:\n" + getCmdExample());
      System.exit(1);
      return;
    }

    final String moduleName = StringUtils.strip(args[0]);
    final Optional<LauncherModule> launcherModuleOptional = Arrays.stream(MODULES)
        .filter(m -> m.moduleName().equals(moduleName)).findFirst();
    if (!launcherModuleOptional.isPresent()) {
      System.out.println(String.format("Unknown module: %s", moduleName));
      System.exit(1);
      return;
    }

    final LauncherModule launcherModule = launcherModuleOptional.get();
    Runtime.getRuntime().addShutdownHook(new Thread(launcherModule::shutdown));

    final CommandLineParser parser = new DefaultParser();
    launcherModule.run(parser.parse(
        launcherModule.getOptions(),
        ArrayUtils.subarray(args, 1, args.length))
    );
  }

  private static String getCmdExample() {
    return "java playwell.jar activity -config config.yml -log4j log4j2.xml\n"
        + "java playwell.jar service -config config.yml -log4j log4j2.xml\n"
        + "java playwell.jar route -config config.yml -log4j log4j2.xml\n"
        + "java playwell.jar clock -config config.yml -log4j log4j2.xml\n"
        + "java playwell.jar activity_replication -config config.yml -log4j log4j2.xml\n"
        + "java playwell.jar clock_replication -config config.yml -log4j log4j2.xml\n";
  }
}
