package playwell.trigger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import playwell.message.Message;


/**
 * TriggerMatchResult用于描述触发器处理结果
 *
 * @author chihongze@gmail.com
 */
public class TriggerMatchResult {

  // 是否匹配触发器条件
  private final boolean matched;

  // 匹配后的剩余事件
  private final List<Message> trailingMessages;

  // 初始化上下文变量
  private final Map<String, Object> initContextVars;

  public TriggerMatchResult(boolean matched, List<Message> trailingMessages,
      Map<String, Object> initContextVars) {
    this.matched = matched;
    this.trailingMessages = trailingMessages;
    this.initContextVars = initContextVars;
  }

  public static TriggerMatchResult unmatchedResult() {
    return new TriggerMatchResult(false, Collections.emptyList(), Collections.emptyMap());
  }

  public static TriggerMatchResult matchedResult() {
    return new TriggerMatchResult(true, Collections.emptyList(), Collections.emptyMap());
  }

  public static TriggerMatchResult matchResult(List<Message> trailingMessages,
      Map<String, Object> initContextVars) {
    return new TriggerMatchResult(true, trailingMessages, initContextVars);
  }

  public boolean isMatched() {
    return matched;
  }

  public List<Message> getTrailingMessages() {
    return trailingMessages;
  }

  public Map<String, Object> getInitContextVars() {
    return initContextVars;
  }
}
