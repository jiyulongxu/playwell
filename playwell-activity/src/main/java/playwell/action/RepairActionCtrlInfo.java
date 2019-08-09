package playwell.action;

import java.util.Map;

/**
 * RepairActionCtrlInfo
 */
public class RepairActionCtrlInfo extends ActionCtrlInfo {

  private final String problem;

  public RepairActionCtrlInfo(String problem, Map<String, Object> contextVars) {
    super(ActionCtrlType.REPAIRING, "", "", contextVars);
    this.problem = problem;
  }

  public String getProblem() {
    return problem;
  }
}
