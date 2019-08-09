package playwell.launcher;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

/**
 * LauncherModule
 */
public interface LauncherModule {

  /**
   * Launcher模块名称
   *
   * @return 模块名称
   */
  String moduleName();

  /**
   * 获取命令行参数声明
   *
   * @return 命令行参数声明
   */
  Options getOptions();

  /**
   * 基于给定的运行参数，运行LauncherModule
   *
   * @param commandLine 运行参数
   */
  void run(CommandLine commandLine);

  /**
   * 关闭钩子回调
   */
  void shutdown();
}
