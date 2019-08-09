package playwell.message.sys;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import playwell.common.EasyMap;
import playwell.common.Mappable;

/**
 * Repair command arguments
 */
public class RepairArguments implements Mappable {

  // 修复后要执行的命令
  private final RepairCtrl ctrl;

  // 修复上下文变量
  private final Map<String, Object> contextVars;

  // Goto action
  private final String gotoAction;

  public RepairArguments(
      RepairCtrl ctrl, Map<String, Object> contextVars, String gotoAction) {
    this.ctrl = ctrl;
    this.contextVars = contextVars;
    this.gotoAction = gotoAction;
  }

  /**
   * 从参数构建
   *
   * @param args 命令参数
   * @return RepairArguments
   */
  public static RepairArguments fromArgs(Map<String, Object> args) {
    final EasyMap arguments = new EasyMap(args);
    return new RepairArguments(
        RepairCtrl.valueOfByCtrl(arguments.getString(ArgNames.CTRL)),
        arguments.getSubArguments(ArgNames.CONTEXT_VARS).toMap(),
        arguments.getString(ArgNames.GOTO, "")
    );
  }

  public RepairCtrl getCtrl() {
    return ctrl;
  }

  public Map<String, Object> getContextVars() {
    return contextVars;
  }

  public String getGotoAction() {
    return gotoAction;
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.of(
        ArgNames.CTRL, this.ctrl.getCtrl(),
        ArgNames.CONTEXT_VARS, this.contextVars,
        ArgNames.GOTO, this.gotoAction
    );
  }

  @Override
  public String toString() {
    return new JSONObject(toMap()).toJSONString();
  }

  public enum RepairCtrl {

    // 重新调度执行Action请求
    RETRY("retry"),

    // 不请求，继续等待结果
    WAITING("waiting"),

    // 跳转到指定的Action
    GOTO("goto"),
    ;

    private static final Map<String, RepairCtrl> valuesByCtrl = new HashMap<>(values().length);

    static {
      for (RepairCtrl ctrl : values()) {
        valuesByCtrl.put(ctrl.getCtrl(), ctrl);
      }
    }

    private final String ctrl;

    RepairCtrl(String ctrl) {
      this.ctrl = ctrl;
    }

    public static RepairCtrl valueOfByCtrl(String ctrl) {
      final RepairCtrl repairCtrl = valuesByCtrl.get(ctrl);
      if (repairCtrl == null) {
        throw new RuntimeException(
            String.format("Unknown repair ctrl: %s", ctrl));
      }
      return repairCtrl;
    }

    public String getCtrl() {
      return ctrl;
    }
  }

  public interface ArgNames {

    String CTRL = "ctrl";

    String CONTEXT_VARS = "context_vars";

    String GOTO = "goto";
  }
}
