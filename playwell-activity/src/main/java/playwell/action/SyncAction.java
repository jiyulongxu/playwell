package playwell.action;

import playwell.activity.thread.ActivityThread;
import playwell.common.Result;

/**
 * SyncAction会在ActivityThread调度器中被同步执行 通常只包含一些非常简单的逻辑，比如修改上下文变量值、更改活动走向、产生随机数等等
 * 不应该在SyncAction中引入太复杂的逻辑，尤其是与IO相关的，防止阻塞事件循环
 *
 * @author chihongze@gmail.com
 */
public abstract class SyncAction extends Action {

  public SyncAction(ActivityThread activityThread) {
    super(activityThread);
  }

  public abstract Result execute();
}
