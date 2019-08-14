package playwell.message.bus.codec;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import playwell.activity.Activity;
import playwell.activity.ActivityManager;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.definition.ActivityDefinitionManager;
import playwell.activity.thread.ActivityThread;
import playwell.activity.thread.ActivityThreadStatus;
import playwell.activity.thread.message.MigrateActivityThreadMessage;
import playwell.activity.thread.message.RemoveActivityThreadMessage;
import playwell.clock.CachedTimestamp;
import playwell.clock.CleanTimeRangeMessage;
import playwell.clock.ClockMessage;
import playwell.common.EasyMap;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.message.Message;
import playwell.message.RoutedMessage;
import playwell.message.ServiceRequestMessage;
import playwell.message.ServiceResponseMessage;
import playwell.message.sys.ActivityThreadCtrlMessage;
import playwell.message.sys.SysMessage;
import playwell.route.migration.MigrateOutputFinishedMessage;

/**
 * <p>基于Map的MessageCodec实现</p>
 *
 * 业务事件消息：<br/>
 *
 * <pre>
 *  {
 *    "type": "message_type",
 *    "attr": {
 *        "a": 1,
 *        "b": 2
 *    },
 *    "time": 1549076084884
 * }
 * </pre>
 *
 * 服务请求消息：<br/>
 *
 * <pre>
 *   {
 *     "type": "req",
 *     "sender": "activity_runner",
 *     "receiver": "xxx_service",
 *     "attr": {
 *        "activity": 1,
 *        "domain": "100",
 *        "action": "xxxx",
 *        "args": {
 *          "a": 1,
 *          "b": 2
 *        }
 *     },
 *     "time": 1549076084884
 *   }
 * </pre>
 *
 * 服务响应消息：<br/>
 *
 * <pre>
 *   {
 *     "type": "res",
 *     "sender": "xxx_service",
 *     "receiver": "activity_runner",
 *     "attr": {
 *        "activity": 1,
 *        "domain": "100",
 *        "action": "xxxx",
 *        "status": "ok",
 *        "error_code": "service_error",
 *        "message": "Service error",
 *        "data": {
 *          "sum": 3
 *        }
 *     },
 *     "time": 1549076084884
 *   }
 * </pre>
 *
 * 系统消息：<br/>
 *
 * <pre>
 *   {
 *     "type": "SYSTEM",
 *     "attr": {
 *        "activity": 1,
 *        "domain": "100",
 *        "command": "continue"
 *     },
 *     "time": 1549076084884
 *   }
 * </pre>
 *
 * @author chihongze
 */
public class MapMessageCodec implements MessageCodec {

  private static final Map<String, Function<EasyMap, Message>> ALL_CODECS = new HashMap<>();

  // 处理普通用户事件
  private static Function<EasyMap, Message> USER_MESSAGE_HANDLER = data -> {
    final EasyMap attr = data.getSubArguments(Message.Fields.ATTRIBUTES);
    final String type = data.getString(Message.Fields.TYPE);

    if (attr.contains(RoutedMessage.DOMAIN_ID) && attr.contains(RoutedMessage.STRATEGY)) {
      return new RoutedMessage(
          attr.getString(RoutedMessage.STRATEGY),
          attr.getString(RoutedMessage.DOMAIN_ID),
          new Message(
              type,
              data.getString(Message.Fields.SENDER, ""),
              data.getString(Message.Fields.RECEIVER, ""),
              attr.toMap(),
              data.getLong(Message.Fields.TIMESTAMP, CachedTimestamp.nowMilliseconds())
          )
      );
    } else {
      return new Message(
          type,
          data.getString(Message.Fields.SENDER, ""),
          data.getString(Message.Fields.RECEIVER, ""),
          attr.toMap(),
          data.getLong(Message.Fields.TIMESTAMP, CachedTimestamp.nowMilliseconds())
      );
    }
  };

  static {
    // 处理系统消息
    ALL_CODECS.put(SysMessage.TYPE, data -> {
      final EasyMap attr = data.getSubArguments(Message.Fields.ATTRIBUTES);
      return new ActivityThreadCtrlMessage(
          data.getLong(Message.Fields.TIMESTAMP, CachedTimestamp.nowMilliseconds()),
          data.getString(Message.Fields.SENDER, ""),
          data.getString(Message.Fields.RECEIVER, ""),
          attr.getInt(SysMessage.Attributes.ACTIVITY_ID),
          attr.getString(SysMessage.Attributes.DOMAIN_ID),
          attr.getString(ActivityThreadCtrlMessage.Attributes.COMMAND)
      );
    });

    // 处理服务请求消息
    ALL_CODECS.put(ServiceRequestMessage.TYPE, data -> {
      final EasyMap attr = data.getSubArguments(Message.Fields.ATTRIBUTES);
      return new ServiceRequestMessage(
          data.getLong(Message.Fields.TIMESTAMP, CachedTimestamp.nowMilliseconds()),
          attr.getInt(ServiceRequestMessage.Attributes.ACTIVITY_ID),
          attr.getString(ServiceRequestMessage.Attributes.DOMAIN_ID),
          attr.getString(ServiceRequestMessage.Attributes.ACTION),
          data.getString(Message.Fields.SENDER),
          data.getString(Message.Fields.RECEIVER),
          attr.get(ServiceRequestMessage.Attributes.ARGS, null),
          attr.getBoolean(ServiceRequestMessage.Attributes.IGNORE_RESULT)
      );
    });

    // 处理服务响应消息
    ALL_CODECS.put(ServiceResponseMessage.TYPE, data -> {
      final EasyMap attr = data.getSubArguments(Message.Fields.ATTRIBUTES);
      return new ServiceResponseMessage(
          data.getLong(Message.Fields.TIMESTAMP, CachedTimestamp.nowMilliseconds()),
          attr.getInt(ServiceResponseMessage.Attributes.ACTIVITY_ID),
          attr.getString(ServiceResponseMessage.Attributes.DOMAIN_ID),
          attr.getString(ServiceResponseMessage.Attributes.ACTION),
          data.getString(Message.Fields.SENDER),
          data.getString(Message.Fields.RECEIVER),
          attr.getString(ServiceResponseMessage.Attributes.STATUS),
          attr.getString(ServiceResponseMessage.Attributes.ERROR_CODE, ""),
          attr.getString(ServiceResponseMessage.Attributes.MESSAGE, ""),
          attr.getSubArguments(ServiceResponseMessage.Attributes.DATA).toMap()
      );
    });

    // 处理时钟消息
    ALL_CODECS.put(ClockMessage.TYPE, data -> {
      final EasyMap attr = data.getSubArguments(Message.Fields.ATTRIBUTES);
      return new ClockMessage(
          data.getString(Message.Fields.SENDER),
          data.getString(Message.Fields.RECEIVER),
          attr.getLong(ClockMessage.Attributes.TIME_POINT),
          attr.getInt(ClockMessage.Attributes.ACTIVITY),
          attr.getString(ClockMessage.Attributes.DOMAIN_ID),
          attr.getString(ClockMessage.Attributes.ACTION),
          attr.getSubArguments(ClockMessage.Attributes.EXTRA).toMap(),
          data.getLong(Message.Fields.TIMESTAMP)
      );
    });

    // 处理时钟清理消息
    ALL_CODECS.put(CleanTimeRangeMessage.TYPE, data -> {
      final EasyMap attr = data.getSubArguments(Message.Fields.ATTRIBUTES);
      final long timePoint = attr.getLong(CleanTimeRangeMessage.Attributes.TIME_POINT);
      return new CleanTimeRangeMessage(timePoint);
    });

    // 处理ActivityThread迁移消息
    ALL_CODECS.put(MigrateActivityThreadMessage.TYPE, data -> {
      final EasyMap attr = data.getSubArguments(Message.Fields.ATTRIBUTES);
      return new MigrateActivityThreadMessage(
          data.getString(Message.Fields.SENDER, ""),
          data.getString(Message.Fields.RECEIVER, ""),
          buildActivityThreadFromAttr(attr)
      );
    });

    // 处理ActivityThread删除消息
    ALL_CODECS.put(RemoveActivityThreadMessage.TYPE, data -> {
      final EasyMap attr = data.getSubArguments(Message.Fields.ATTRIBUTES);
      return new RemoveActivityThreadMessage(
          data.getString(Message.Fields.SENDER, ""),
          data.getString(Message.Fields.RECEIVER, ""),
          attr.getInt(RemoveActivityThreadMessage.Attributes.ACTIVITY_ID),
          attr.getString(RemoveActivityThreadMessage.Attributes.DOMAIN_ID)
      );
    });

    // 处理迁移完毕通知
    ALL_CODECS.put(MigrateOutputFinishedMessage.TYPE, data -> new MigrateOutputFinishedMessage(
        data.getString(Message.Fields.SENDER),
        data.getString(Message.Fields.RECEIVER)
    ));
  }

  public MapMessageCodec() {

  }

  private static ActivityThread buildActivityThreadFromAttr(EasyMap attr) {
    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    final ActivityDefinitionManager activityDefinitionManager = (ActivityDefinitionManager) integrationPlan
        .getTopComponent(TopComponentType.ACTIVITY_DEFINITION_MANAGER);
    final ActivityManager activityManager = (ActivityManager) integrationPlan
        .getTopComponent(TopComponentType.ACTIVITY_MANAGER);

    final int activityId = attr.getInt(ActivityThread.Fields.ACTIVITY_ID);
    final Optional<Activity> activityOptional = activityManager.getActivityById(activityId);
    if (!activityOptional.isPresent()) {
      throw new RuntimeException(String.format("Unknown activity: %d", activityId));
    }
    final Activity activity = activityOptional.get();

    final String definitionName = attr.getString(ActivityThread.Fields.DEFINITION_NAME);
    final String version = attr.getString(ActivityThread.Fields.VERSION);
    final Optional<ActivityDefinition> definitionOptional = activityDefinitionManager
        .getActivityDefinition(definitionName, version);
    if (!definitionOptional.isPresent()) {
      throw new RuntimeException(String.format(
          "Unknown activity definition: name = %s, version = %s",
          definitionName,
          version
      ));
    }
    final ActivityDefinition activityDefinition = definitionOptional.get();

    final int statusCode = attr.getInt(ActivityThread.Fields.STATUS);
    final Optional<ActivityThreadStatus> statusOptional = ActivityThreadStatus
        .valueOfByCode(statusCode);
    if (!statusOptional.isPresent()) {
      throw new RuntimeException(String.format("Unknown activity thread status: %d", statusCode));
    }
    final ActivityThreadStatus status = statusOptional.get();

    return new ActivityThread(
        activity,
        activityDefinition,
        attr.getString(ActivityThread.Fields.DOMAIN_ID),
        status,
        attr.getString(ActivityThread.Fields.CURRENT_ACTION),
        attr.getLong(ActivityThread.Fields.UPDATED_ON),
        attr.getLong(ActivityThread.Fields.CREATED_ON),
        attr.getSubArguments(ActivityThread.Fields.CONTEXT).toMap()
    );
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public Message decode(Object object) {
    final EasyMap data = new EasyMap((Map<String, Object>) object);
    final String type = data.getString(Message.Fields.TYPE);

    if (ALL_CODECS.containsKey(type)) {
      return ALL_CODECS.get(type).apply(data);
    } else {
      return USER_MESSAGE_HANDLER.apply(data);
    }
  }

  @Override
  public Object encode(Message message) {
    return message.toMap();
  }
}
