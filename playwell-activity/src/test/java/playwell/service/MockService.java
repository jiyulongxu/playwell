package playwell.service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import playwell.clock.CachedTimestamp;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.message.ServiceRequestMessage;
import playwell.message.ServiceResponseMessage;

/**
 * <p>Mock Service，用于单元测试ServiceAction以及服务集成</p>
 *
 * @author chihongze@gmail.com
 */
public class MockService implements PlaywellService {

  @Override
  public void init(Object config) {

  }

  @Override
  public Collection<ServiceResponseMessage> handle(Collection<ServiceRequestMessage> messages) {
    if (CollectionUtils.isEmpty(messages)) {
      return Collections.emptyList();
    }
    return messages.stream().map(this::handleRequest).collect(Collectors.toList());
  }

  @SuppressWarnings({"unchecked"})
  private ServiceResponseMessage handleRequest(ServiceRequestMessage requestMessage) {
    final EasyMap args = new EasyMap((Map<String, Object>) requestMessage.getArgs());
    final String action = args.getString(RequestArgs.ACTION, "echo");
    if ("timeout".equals(action)) {
      try {
        long time = args.getLong(RequestArgs.TIME);
        TimeUnit.MILLISECONDS.sleep(time);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    final String echoString = args.getString(RequestArgs.ECHO);
    return new ServiceResponseMessage(
        CachedTimestamp.nowMilliseconds(),
        requestMessage,
        Result.okWithData(Collections.singletonMap("echo", echoString))
    );
  }

  // 请求参数名称
  interface RequestArgs {

    String ACTION = "action";

    String TIME = "time";

    String ECHO = "echo";
  }
}
