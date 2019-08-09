package playwell.action.builtin;


import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.collections4.CollectionUtils;
import playwell.action.ActionInstanceBuilder;
import playwell.action.SyncAction;
import playwell.activity.thread.ActivityThread;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.common.argument.Argument;
import playwell.common.argument.ExpressionArgument;
import playwell.common.argument.ListArgument;
import playwell.common.argument.MapArgument;
import playwell.common.exception.BuildComponentException;

/**
 * <p>ForeachAction可以用于迭代指定的List</p>
 * eg.
 * <pre>
 *   - name: foreach
 *     type: foreach
 *     args:
 *       items: "{1, 2, 3, 4, 5}"
 *       eof: finish
 *       continue: call("xxx")
 * </pre>
 */
public class ForeachAction extends SyncAction {

  public static final String TYPE = "foreach";

  public static final ActionInstanceBuilder BUILDER = ForeachAction::new;

  public static final Consumer<Argument> ARG_SPEC = argument -> {
    if (!Argument.isMapArgument(argument)) {
      throw new BuildComponentException("The arguments of foreach action must be map");
    }

    Map<String, Argument> argumentsDef = ((MapArgument) argument).getArgs();
    if (!argumentsDef.containsKey(ArgNames.ITEMS)) {
      throw new BuildComponentException("The foreach action required items argument");
    }

    if (!argumentsDef.containsKey(ArgNames.NEXT)) {
      throw new BuildComponentException("The foreach action required next argument");
    }

    if (!argumentsDef.containsKey(ArgNames.FINISH)) {
      throw new BuildComponentException("The foreach action required finish argument");
    }
  };

  public ForeachAction(ActivityThread activityThread) {
    super(activityThread);
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public Result execute() {
    Map<String, Argument> argumentsDef = ((MapArgument) getActionDefinition().getArguments())
        .getArgs();
    final Argument itemsArgumentDef = argumentsDef.get(ArgNames.ITEMS);
    final List<Object> elements;
    if (Argument.isListArgument(itemsArgumentDef)) {
      elements = ((ListArgument) itemsArgumentDef)
          .getValueList(getArgExpressionContext());
    } else {
      elements = (List<Object>) ((ExpressionArgument) itemsArgumentDef)
          .getValue(getArgExpressionContext());
    }

    // 元素为空，默认迭代已经完成
    if (CollectionUtils.isEmpty(elements)) {
      activityThread.getContext().remove(indexVarName());
      activityThread.getContext().remove(elementVarName());
      final String finishCtrl = (String) ((ExpressionArgument) argumentsDef.get(ArgNames.FINISH))
          .getValue(getArgExpressionContext());
      return Result.okWithData(Collections.singletonMap("$ctrl", finishCtrl));
    }

    final EasyMap context = new EasyMap(activityThread.getContext());
    int index = context.getInt(indexVarName(), -1);
    if (++index < elements.size()) {
      Object element = elements.get(index);
      activityThread.getContext().put(indexVarName(), index);
      activityThread.getContext().put(elementVarName(), element);
      final String nextCtrl = (String) ((ExpressionArgument) argumentsDef
          .get(ArgNames.NEXT))
          .getValue(getArgExpressionContext());
      return Result.okWithData(Collections.singletonMap("$ctrl", nextCtrl));
    } else {
      activityThread.getContext().remove(indexVarName());
      activityThread.getContext().remove(elementVarName());
      final String finishCtrl = (String) ((ExpressionArgument) argumentsDef.get(ArgNames.FINISH))
          .getValue(getArgExpressionContext());
      return Result.okWithData(Collections.singletonMap("$ctrl", finishCtrl));
    }
  }

  private String indexVarName() {
    return String.format("$%s.%s", actionDefinition.getName(), ContextVars.INDEX);
  }

  private String elementVarName() {
    return String.format("$%s.%s", actionDefinition.getName(), ContextVars.ELEMENT);
  }

  interface ContextVars {

    String INDEX = "idx";

    String ELEMENT = "ele";
  }

  interface ArgNames {

    String ITEMS = "items";

    String FINISH = "finish";

    String NEXT = "next";
  }
}
