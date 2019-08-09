package playwell.action;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * ActionCtrlType枚举包含了针对Action调度的控制类型
 *
 * <ul>
 * <li>FINISH: 表示Activity实例运行已经顺利结束</li>
 * <li>FAIL: 表示Activity实例运行失败已经终止</li>
 * <li>CALL: 跳转到指定的Action开始执行</li>
 * <li>WAITING: 表示Activity实例进入等待执行状态</li>
 * <li>RETRY: 执行重试</li>
 * <li>REPAIRING: 执行修复</li>
 * </ul>
 *
 * @author chihongze@gmail.com
 */
public enum ActionCtrlType {

  FINISH("FINISH"),

  FAIL("FAIL"),

  CALL("CALL"),

  WAITING("WAITING"),

  RETRY("RETRY"),

  REPAIRING("repairing"),
  ;

  private static final Map<String, ActionCtrlType> actionCtrlTypes = Arrays
      .stream(values()).collect(Collectors.toMap(ActionCtrlType::getType, Function.identity()));

  private final String type;

  ActionCtrlType(String type) {
    this.type = type;
  }

  public static Optional<ActionCtrlType> valueOfByType(String type) {
    return Optional.ofNullable(actionCtrlTypes.get(type));
  }

  public String getType() {
    return this.type;
  }
}
