package playwell.action;

import playwell.activity.thread.ActivityThread;
import playwell.common.Result;
import playwell.message.Message;

/**
 * AsyncAction用于调用一系列的外部服务或实现需要阻塞等待的逻辑 sendRequest方法会通过消息总线来向外部服务发出请求
 * handleResponse方法会用来处理外部服务的响应消息
 *
 * @author chihongze@gmail.com
 */
public abstract class AsyncAction extends Action {

  public AsyncAction(ActivityThread activityThread) {
    super(activityThread);
  }

  public abstract void sendRequest();

  public abstract Result handleResponse(Message message);

  public boolean isAwait() {
    return this.actionDefinition.isAwait();
  }
}
