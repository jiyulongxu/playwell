package playwell.message.bus.codec;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import playwell.clock.CachedTimestamp;
import playwell.message.Message;
import playwell.message.ServiceRequestMessage;
import playwell.message.ServiceResponseMessage;
import playwell.message.sys.ActivityThreadCtrlMessage;

/**
 * 针对MapMessageCodec的测试用例
 *
 * @author chihongze@gmail.com
 */
public class MapMessageCodecTestCase {

  @Test
  @SuppressWarnings({"unchecked"})
  public void test() {
    final MessageCodec messageCodec = new MapMessageCodec();

    // Test System message
    ActivityThreadCtrlMessage ctrlMessage = (ActivityThreadCtrlMessage) messageCodec
        .decode(ImmutableMap.builder()
            .put("type", "SYSTEM")
            .put("attr", ImmutableMap.of(
                "activity", 1,
                "domain", "10001",
                "command", "continue"
            ))
            .put("time", CachedTimestamp.nowMilliseconds())
            .build());
    checkCtrlMessage(ctrlMessage);
    Map<String, Object> data = (Map<String, Object>) messageCodec.encode(ctrlMessage);
    ctrlMessage = (ActivityThreadCtrlMessage) messageCodec.decode(data);
    checkCtrlMessage(ctrlMessage);

    // Test Service request message
    ServiceRequestMessage requestMessage = (ServiceRequestMessage) messageCodec.decode(
        ImmutableMap.builder()
            .put("type", "req")
            .put("sender", "activity_runner")
            .put("receiver", "xxx_service")
            .put("attr", ImmutableMap.of(
                "activity", 1,
                "domain", "10001",
                "action", "xxx",
                "args", ImmutableMap.of("a", 1, "b", 2),
                "ignore_result", false
            ))
            .put("time", CachedTimestamp.nowMilliseconds())
            .build());
    checkReqMessage(requestMessage);
    data = (Map<String, Object>) messageCodec.encode(requestMessage);
    requestMessage = (ServiceRequestMessage) messageCodec.decode(data);
    checkReqMessage(requestMessage);

    // Test Service postResponse message
    ServiceResponseMessage responseMessage = (ServiceResponseMessage) messageCodec.decode(
        ImmutableMap.builder()
            .put("type", "res")
            .put("sender", "xxx_service")
            .put("receiver", "activity_runner")
            .put("attr", ImmutableMap
                .builder()
                .put("activity", 1)
                .put("domain", "10001")
                .put("action", "xxx")
                .put("status", "ok")
                .put("error_code", "service_error")
                .put("message", "Service error")
                .put("data", Collections.singletonMap("sum", 3))
                .build()
            )
            .put("time", CachedTimestamp.nowMilliseconds())
            .build()
    );
    checkResMessage(responseMessage);
    data = (Map<String, Object>) messageCodec.encode(responseMessage);
    responseMessage = (ServiceResponseMessage) messageCodec.decode(data);
    checkResMessage(responseMessage);

    // Test User event
    Message message = messageCodec.decode(
        ImmutableMap.of(
            "type", "user_behavior",
            "attr", ImmutableMap.of(
                "a", 1,
                "b", 2
            )
        )
    );
    checkUserEvent(message);
    data = (Map<String, Object>) messageCodec.encode(message);
    message = messageCodec.decode(data);
    checkUserEvent(message);
  }

  private void checkCtrlMessage(ActivityThreadCtrlMessage ctrlMessage) {
    Assert.assertEquals("SYSTEM", ctrlMessage.getType());
    Assert.assertEquals(1, ctrlMessage.getActivityId());
    Assert.assertEquals("10001", ctrlMessage.getDomainId());
    Assert.assertEquals("continue", ctrlMessage.getCommand());
  }

  @SuppressWarnings({"unchecked"})
  private void checkReqMessage(ServiceRequestMessage requestMessage) {
    Assert.assertEquals("req", requestMessage.getType());
    Assert.assertEquals("activity_runner", requestMessage.getSender());
    Assert.assertEquals("xxx_service", requestMessage.getReceiver());
    Assert.assertEquals(1, requestMessage.getActivityId());
    Assert.assertEquals("10001", requestMessage.getDomainId());
    Assert.assertEquals("xxx", requestMessage.getAction());
    Assert.assertEquals(1, ((Map<String, Object>) requestMessage.getArgs()).get("a"));
  }

  private void checkResMessage(ServiceResponseMessage responseMessage) {
    Assert.assertEquals("res", responseMessage.getType());
    Assert.assertEquals("xxx_service", responseMessage.getSender());
    Assert.assertEquals("activity_runner", responseMessage.getReceiver());
    Assert.assertEquals(1, responseMessage.getActivityId());
    Assert.assertEquals("10001", responseMessage.getDomainId());
    Assert.assertEquals("xxx", responseMessage.getAction());
    Assert.assertEquals("ok", responseMessage.getStatus());
    Assert.assertEquals("service_error", responseMessage.getErrorCode());
    Assert.assertEquals("Service error", responseMessage.getMessage());
    Assert.assertEquals(3, responseMessage.getData().get("sum"));
  }

  private void checkUserEvent(Message userEvent) {
    Assert.assertEquals("user_behavior", userEvent.getType());
    Assert.assertEquals(1, userEvent.getAttributes().get("a"));
    Assert.assertEquals(2, userEvent.getAttributes().get("b"));
  }
}
