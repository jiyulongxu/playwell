package playwell.action.builtin;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import playwell.action.ActionInstanceBuilder;
import playwell.action.ActionRuntimeException;
import playwell.action.SyncAction;
import playwell.activity.thread.ActivityThread;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.common.argument.Argument;
import playwell.common.argument.MapArgument;
import playwell.common.exception.BuildComponentException;


/**
 * <p>RandomChoiceAction可以用于从一组给定的参数中随机选择一个，也可以按照权重进行选择。</p>
 *
 * eg.<br/>
 *
 * <pre>
 *   - name: random_choice
 *     type: random_choice
 *     args:
 *       items:
 *         - a
 *         - b
 *         - c
 *         - d
 *       weights:
 *         - 20
 *         - 30
 *         - 20
 *         - 30
 *       var: random_choice_value
 *     ctrl:
 *       - default: CALL("xxxx")
 * </pre>
 *
 * <p>
 * 以上声明会按照权重来从items选择，a - 20% b - 30% c - 20% d - 20%， 然后被选中的项会保存在上下文的random_choice_value变量中。
 * </p>
 *
 * <p>
 * weights参数可以忽略，如果忽略，则为随机选择。
 * </p>
 *
 * <p>可以配合ctrl表达式来随机选择执行步骤：</p>
 *
 *
 * <pre>
 *   - name: random_choice
 *     type: random_choice
 *     args:
 *       items:
 *         - a
 *         - b
 *         - c
 *         - d
 *       var: act
 *     ctrl:
 *       - default: call(var("act"))
 * </pre>
 *
 * @author chihongze@gmail.com
 */
public class RandomChoiceAction extends SyncAction {

  public static final String TYPE = "random_choice";

  public static final ActionInstanceBuilder BUILDER = RandomChoiceAction::new;

  public static final Consumer<Argument> ARG_SPEC = argument -> {
    if (!Argument.isMapArgument(argument)) {
      throw new BuildComponentException(
          "Invalid argument, the random_choice action only accpet map argument.");
    }

    Map<String, Argument> mapArgumentsDef = ((MapArgument) argument).getArgs();
    if (!mapArgumentsDef.containsKey(ArgNames.ITEMS)) {
      throw new BuildComponentException(
          "Invalid argument, the random_choice action required items argument");
    }
    if (!mapArgumentsDef.containsKey(ArgNames.VAR)) {
      throw new BuildComponentException(
          "Invalid argument, the random_choice action required var argument");
    }
  };

  public RandomChoiceAction(ActivityThread activityThread) {
    super(activityThread);
  }

  @Override
  public Result execute() {
    Argument argumentsDef = this.getActionDefinition().getArguments();
    if (!Argument.isMapArgument(argumentsDef)) {
      throw new ActionRuntimeException(
          CommonRuntimeErrorCodes.INVALID_ARGUMENT,
          "Invalid argument, only allow map."
      );
    }

    EasyMap arguments = new EasyMap(getMapArguments());

    final List<Object> items = arguments.getObjectList(ArgNames.ITEMS);
    final String varName = arguments.getString(ArgNames.VAR);

    if (arguments.contains(ArgNames.WEIGHTS)) {
      // 带权重
      final List<Double> weights = arguments.getDoubleList(ArgNames.WEIGHTS);

      // 权重数目和item数目不匹配
      if (weights.size() != items.size()) {
        throw new ActionRuntimeException(
            CommonRuntimeErrorCodes.INVALID_ARGUMENT,
            "The number of items is not match with weights"
        );
      }

      final Random random = ThreadLocalRandom.current();
      final TreeMap<Double, Object> map = new TreeMap<>();
      double total = 0;
      for (int i = 0; i < items.size(); i++) {
        double weight = weights.get(i);
        if (weight <= 0) {
          continue;
        }
        total += weight;
        Object item = items.get(i);
        map.put(total, item);
      }
      Object targetValue = map.higherEntry(random.nextDouble() * total).getValue();
      getActivityThread().getContext().put(varName, targetValue);
    } else {
      // 不带权重，真.随机选择
      int targetIndex = ThreadLocalRandom.current().nextInt(items.size());
      Object targetValue = items.get(targetIndex);
      getActivityThread().getContext().put(varName, targetValue);
    }

    return Result.ok();
  }

  interface ArgNames {

    String ITEMS = "items";

    String WEIGHTS = "weights";

    String VAR = "var";
  }
}
