package playwell.activity.thread;


import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.action.AsyncAction;
import playwell.action.SyncAction;
import playwell.activity.Activity;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.message.Message;

/**
 * 用于跟踪ActivityThread状态扭转情况的日志
 *
 * @author chihongze@gmail.com
 */
public final class ActivityThreadLogger {

  private static final Logger logger = LogManager.getLogger("activity_thread");

  private static final String THREAD_LOG = "$thread_log";

  private ActivityThreadLogger() {

  }

  /**
   * 记录ActivityThread spawn成功
   *
   * @param activityThread spawn的ActivityThread
   */
  public static void logSpawnSuccess(ActivityThread activityThread) {
    final String operation = "spawn_success";
    if (allowOutputLog(activityThread, operation)) {
      logger.info(getActivityThreadDesc(operation, activityThread));
    }
  }

  /**
   * 记录ActivityThread spawn失败
   *
   * @param activityThread spawn的ActivityThread
   * @param exception 发生的异常
   */
  public static void logSpawnError(ActivityThread activityThread, Exception exception) {
    final String operation = "spawn_error";
    if (allowOutputLog(activityThread, operation)) {
      logger.error(getActivityThreadDesc(operation, activityThread), exception);
    }
  }

  /**
   * 记录ActivityThread从running变成了suspending状态
   *
   * @param activityThread 目标ActivityThread
   */
  public static void logBecomeSuspending(ActivityThread activityThread) {
    final String operation = "suspending";
    if (allowOutputLog(activityThread, operation)) {
      logger.info(getActivityThreadDesc(operation, activityThread));
    }
  }

  /**
   * 记录ActivityThread从suspending状态变成了running状态
   *
   * @param activityThread 目标ActivityThread
   */
  public static void logBecomeRunning(ActivityThread activityThread) {
    final String operation = "running";
    if (allowOutputLog(activityThread, operation)) {
      logger.info(getActivityThreadDesc(operation, activityThread));
    }
  }

  /**
   * 记录同步Action的执行结果
   *
   * @param activityThread 目标ActivityThread
   * @param syncAction 目标ActivityThread
   * @param result 执行结果
   */
  public static void logSyncActionResult(ActivityThread activityThread, SyncAction syncAction,
      Result result) {
    final String operation = "sync_exec";
    if (allowOutputLog(activityThread, operation)) {
      logger.info(String.format(
          "%s - %s - %s",
          getActivityThreadDesc(operation, activityThread),
          syncAction.getName(),
          result.toMap()
      ));
    }
  }

  /**
   * 记录AsyncAction发出消息
   *
   * @param activityThread 目标ActivityThread
   */
  public static void logMakeAsyncRequest(ActivityThread activityThread) {
    final String operation = "async_req";
    if (allowOutputLog(activityThread, operation)) {
      logger.info(getActivityThreadDesc(operation, activityThread));
    }
  }

  /**
   * 记录AsyncAction响应异步消息
   *
   * @param activityThread 目标ActivityThread
   * @param asyncAction 目标AsyncAction
   * @param result 处理结果
   * @param message 异步消息
   */
  public static void logReceiveAsyncResponse(
      ActivityThread activityThread, AsyncAction asyncAction, Message message, Result result) {
    final String operation = "async_res";
    if (allowOutputLog(activityThread, operation)) {
      logger.info(String.format(
          "%s - %s - %s",
          getActivityThreadDesc(operation, activityThread),
          asyncAction.getName(),
          message.toMap()
      ));
    }
  }

  /**
   * 记录No await AsyncAction状态转移
   *
   * @param activityThread 目标ActivityThread
   * @param asyncAction 目标AsyncAction
   */
  public static void logAsyncActionNoAwait(
      ActivityThread activityThread, AsyncAction asyncAction) {
    final String operation = "async_no_await";
    if (allowOutputLog(activityThread, operation)) {
      logger.info(String.format(
          "%s - %s",
          getActivityThreadDesc(operation, activityThread),
          asyncAction.getName()
      ));
    }
  }

  /**
   * 记录调度异常
   *
   * @param activityThread 目标ActivityThread
   * @param e 异常对象
   */
  public static void logScheduleError(ActivityThread activityThread, Exception e) {
    final String operation = "schedule_error";
    if (allowOutputLog(activityThread, operation)) {
      logger.error(getActivityThreadDesc(operation, activityThread), e);
    }
  }

  public static void logScheduleError(
      ActivityThread activityThread, String errorCode, String message) {
    final String operation = "schedule_error";
    if (allowOutputLog(activityThread, operation)) {
      logger.error(String.format("%s - %s - %s",
          getActivityThreadDesc(operation, activityThread), errorCode, message));
    }
  }

  /**
   * 记录ActivityThread被kill掉
   *
   * @param activityThread 目标ActivityThread
   */
  public static void logActivityThreadKilled(ActivityThread activityThread) {
    final String operation = "killed";
    if (allowOutputLog(activityThread, operation)) {
      logger.info(getActivityThreadDesc(operation, activityThread));
    }
  }

  /**
   * 记录ActivityThread被暂停
   *
   * @param activityThread 目标ActivityThread
   */
  public static void logActivityThreadPaused(ActivityThread activityThread) {
    final String operation = "paused";
    if (allowOutputLog(activityThread, operation)) {
      logger.info(getActivityThreadDesc(operation, activityThread));
    }
  }

  /**
   * 记录ActivityThread从暂停当中恢复成功
   *
   * @param activityThread 目标ActivityThread
   */
  public static void logActivityThreadContinueSuccess(ActivityThread activityThread) {
    final String operation = "continue_success";
    if (allowOutputLog(activityThread, operation)) {
      logger.info(getActivityThreadDesc(operation, activityThread));
    }
  }

  /**
   * 记录ActivityThread从暂停中恢复失败
   */
  public static void logActivityThreadContinueFailure(
      ActivityThread activityThread, String errorCode, String message) {
    final String operation = "continue_fail";
    if (allowOutputLog(activityThread, operation)) {
      logger.error(String.format(
          "%s - %s - %s",
          getActivityThreadDesc(operation, activityThread),
          errorCode,
          message
      ));
    }
  }

  /**
   * 记录重试操作
   */
  public static void logRetry(ActivityThread activityThread) {
    final String operation = "retry";
    if (allowOutputLog(activityThread, operation)) {
      logger.info(getActivityThreadDesc(operation, activityThread));
    }
  }

  /**
   * 记录修复操作
   *
   * @param activityThread ActivityThread
   */
  public static void logRepair(ActivityThread activityThread) {
    logger.info(getActivityThreadDesc("repair", activityThread));
  }

  private static String getActivityThreadDesc(String operation, ActivityThread activityThread) {
    return String.format(
        "%s - %s - %s - %d - %s - %d - %s - %s",
        operation,
        activityThread.getActivityDefinition().getName(),
        activityThread.getActivityDefinition().getVersion(),
        activityThread.getActivity().getId(),
        activityThread.getDomainId(),
        activityThread.getStatus().getCode(),
        activityThread.getCurrentAction(),
        activityThread.getContext()
    );
  }

  @SuppressWarnings({"unchecked"})
  private static boolean allowOutputLog(ActivityThread thread, String operation) {
    final Activity activity = thread.getActivity();
    final EasyMap configuration = new EasyMap(activity.getConfig());
    final Object logItemObj = configuration.get(THREAD_LOG, "error");

    if (LogContent.NONE.equals(logItemObj)) {
      return false;
    }

    if (logItemObj instanceof String) {
      if (LogContent.ALL.equals(logItemObj)) {
        return true;
      } else if (LogContent.ERROR.equals(logItemObj)) {
        return operation.endsWith(LogContent.ERROR);
      }
    } else if (logItemObj instanceof List) {
      final List<String> logItems = (List<String>) logItemObj;
      return logItems.stream().anyMatch(item -> item.equals(operation));
    }
    return false;
  }

  interface LogContent {

    String ALL = "all";

    String ERROR = "error";

    String NONE = "none";
  }
}
