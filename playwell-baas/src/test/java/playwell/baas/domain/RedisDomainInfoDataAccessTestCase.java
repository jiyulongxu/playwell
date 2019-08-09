package playwell.baas.domain;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.baas.BaseBaasTestCase;
import playwell.baas.domain.RedisDomainInfoDataAccess.ConfigItems;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.common.expression.spel.SpELPlaywellExpression;
import playwell.message.BasicActivityThreadMessage;
import playwell.message.ServiceResponseMessage;
import playwell.service.LocalServiceMeta;
import playwell.util.Sleeper;

/**
 * TestCase for RedisDomainInfoDataAccess
 */
public class RedisDomainInfoDataAccessTestCase extends BaseBaasTestCase {

  private static final String SERVICE_NAME = "redis_domain";

  private DomainService domainService;

  @Before
  public void setUp() {
    this.init(true);
    LocalServiceMeta localServiceMeta = getLocalServiceMeta(SERVICE_NAME);
    this.domainService = (DomainService) localServiceMeta.getPlaywellService();
  }

  @Test
  public void testDataAccessOperations() {
    // 写入用户属性
    sendRequests(SERVICE_NAME, Arrays.asList(
        ImmutableMap.of(
            "opt", "upsert",
            "type", "user",
            "id", "1",
            "properties", ImmutableMap.of(
                "name", "Sam",
                "gender", "male",
                "grade", "A"
            )
        ),
        ImmutableMap.of(
            "opt", "upsert",
            "type", "user",
            "id", "2",
            "properties", ImmutableMap.of(
                "name", "Jack",
                "gender", "male",
                "grade", "A",
                "score", 89
            )
        ),
        ImmutableMap.of(
            "opt", "upsert",
            "type", "user",
            "id", "3",
            "properties", ImmutableMap.of(
                "name", "James",
                "gender", "male",
                "grade", "A",
                "scores", Arrays.asList(1, 2, 3, 4, 5),
                "subjects", ImmutableMap.of(
                    "Chinese", 90,
                    "Maths", 30
                )
            )
        )
    ));

    Sleeper.sleepInSeconds(1);

    // 获取响应
    System.out.println("=== Get write response ===");
    Collection<ServiceResponseMessage> responseMessages = getResponse(100);
    Assert.assertEquals(3, responseMessages.size());
    responseMessages.forEach(responseMessage -> {
      System.out.println("Response: " + responseMessage);
      Assert.assertEquals(Result.STATUS_OK, responseMessage.getStatus());
    });

    // 读取用户属性
    sendRequests(SERVICE_NAME, Arrays.asList(
        ImmutableMap.of(
            "opt", "query",
            "type", "user",
            "id", "1"
        ),
        ImmutableMap.of(
            "opt", "query",
            "type", "user",
            "id", "2",
            "properties", Arrays.asList("name", "score")
        ),
        ImmutableMap.of(
            "opt", "query",
            "type", "user",
            "id", "3",
            "properties", Arrays.asList("name", "scores", "subjects")
        )
    ));

    Sleeper.sleepInSeconds(1);

    // 获取响应
    System.out.println("=== Get read response ===");
    responseMessages = getResponse(100);
    Assert.assertEquals(3, responseMessages.size());
    responseMessages.forEach(responseMessage -> {
      System.out.println("Response: " + responseMessage);
      Assert.assertEquals(Result.STATUS_OK, responseMessage.getStatus());
      EasyMap responseData = new EasyMap(responseMessage.getData());
      String name = responseData.getString("name");
      if ("Sam".equals(name)) {
        Assert.assertEquals("male", responseData.getString("gender"));
        Assert.assertEquals("A", responseData.getString("grade"));
      } else if ("Jack".equals(name)) {
        Assert.assertEquals(89, responseData.getInt("score"));
      } else if ("James".equals(name)) {
        Assert.assertEquals(
            Arrays.asList(1, 2, 3, 4, 5),
            responseData.getIntegerList("scores")
        );
        Assert.assertEquals(new HashMap<String, Object>(ImmutableMap.of(
            "Chinese", 90,
            "Maths", 30
        )), responseData.getSubArguments("subjects").toMap());
      }
    });

    // 增加计数
    sendRequests(SERVICE_NAME, Collections.singletonList(ImmutableMap.of(
        "opt", "upsert",
        "type", "user",
        "id", "2",
        "properties", ImmutableMap.of(
            "score", "$incr 2"
        )
    )));
    Sleeper.sleepInSeconds(1);
    getResponse(1);
    sendRequests(SERVICE_NAME, Collections.singletonList(ImmutableMap.of(
        "opt", "query",
        "type", "user",
        "id", "2",
        "properties", Arrays.asList("name", "score")
    )));
    Sleeper.sleepInSeconds(1);
    responseMessages = getResponse(1);
    responseMessages.forEach(responseMessage -> {
      EasyMap data = new EasyMap(responseMessage.getData());
      Assert.assertEquals(91, data.getInt("score"));
    });

    // 删除属性
    sendRequests(SERVICE_NAME, Collections.singletonList(ImmutableMap.of(
        "opt", "upsert",
        "type", "user",
        "id", "1",
        "properties", ImmutableMap.of(
            "gender", "$delete"
        )
    )));
    Sleeper.sleepInSeconds(1);
    getResponse(1);
    sendRequests(SERVICE_NAME, Collections.singletonList(ImmutableMap.of(
        "opt", "query",
        "type", "user",
        "id", "1",
        "properties", Arrays.asList("name", "gender")
    )));
    Sleeper.sleepInSeconds(1);
    responseMessages = getResponse(1);
    responseMessages.forEach(responseMessage -> {
      EasyMap data = new EasyMap(responseMessage.getData());
      Assert.assertNull(data.getString("gender", null));
    });

    // 查询不存在的用户
    sendRequests(SERVICE_NAME, Collections.singletonList(ImmutableMap.of(
        "opt", "query",
        "type", "user",
        "id", "100",
        "properties", Arrays.asList("name", "gender")
    )));
    Sleeper.sleepInSeconds(1);
    responseMessages = getResponse(1);
    responseMessages.forEach(responseMessage -> {
      EasyMap data = new EasyMap(responseMessage.getData());
      Assert.assertTrue(data.isEmpty());
    });

    sendRequests(SERVICE_NAME, Collections.singletonList(ImmutableMap.of(
        "opt", "query",
        "type", "user",
        "id", "100"
    )));
    Sleeper.sleepInSeconds(1);
    responseMessages = getResponse(1);
    responseMessages.forEach(responseMessage -> {
      EasyMap data = new EasyMap(responseMessage.getData());
      Assert.assertTrue(data.isEmpty());
    });
  }

  @Test
  public void benchmark() {
    // 写100w条
    for (int i = 0; i < 1000000; i++) {
      sendRequests(SERVICE_NAME, Collections.singletonList(ImmutableMap.of(
          "opt", "upsert",
          "type", "user",
          "id", Integer.toString(i),
          "properties", ImmutableMap.of(
              "name", "Sam",
              "gender", "male",
              "grade", "A"
          )
      )));
    }
    int readedNum = 0;
    while (readedNum < 1000000) {
      Collection<ServiceResponseMessage> responseMessages = getResponse(100000);
      readedNum += responseMessages.size();
      Sleeper.sleep(1);
    }

    // 读100w条
    for (int i = 0; i < 1000000; i++) {
      sendRequests(SERVICE_NAME, Collections.singletonList(ImmutableMap.of(
          "opt", "query",
          "type", "user",
          "id", Integer.toString(i)
      )));
    }
    readedNum = 0;
    while (readedNum < 1000000) {
      Collection<ServiceResponseMessage> responseMessages = getResponse(100000);
      responseMessages.forEach(responseMessage -> {
        Assert.assertEquals(Result.STATUS_OK, responseMessage.getStatus());
        EasyMap data = new EasyMap(responseMessage.getData());
        Assert.assertEquals("Sam", data.getString("name"));
      });
      readedNum += responseMessages.size();
      Sleeper.sleep(1);
    }

    System.out.println("Start scan...");
    DomainInfoDataAccess dataAccess = new RedisDomainInfoDataAccess();
    dataAccess.init(new EasyMap(
        ImmutableMap.of(
            ConfigItems.REDIS_RESOURCE, "default",
            ConfigItems.NAMESPACE, "p:u"
        )
    ));
    Set<Integer> scanned = new HashSet<>();
    Optional<String> cursorOptional = Optional.of("0");
    do {
      cursorOptional = dataAccess.scan(
          cursorOptional.get(),
          "user",
          Collections.emptyList(),
          Collections.emptyList(),
          100,
          context -> {
            DomainInfo domainInfo = context.currentDomainInfo();
            int userId = Integer.parseInt(domainInfo.getDomainId());
            scanned.add(userId);
            if (userId % 10000 == 0) {
              System.out.println("Hello user: " + userId);
            }
          }
      );
    } while (cursorOptional.isPresent());
    Assert.assertEquals(1000000, scanned.size());

    System.out.println("Scan finished!");
  }

  @Test
  public void testScan() throws Exception {
    // 测试记录扫描

    // 制造5k条记录
    for (int i = 0; i < 5000; i++) {
      sendRequests(SERVICE_NAME, Collections.singletonList(ImmutableMap.of(
          "opt", "upsert",
          "type", "user",
          "id", Integer.toString(i),
          "properties", ImmutableMap.of(
              "name", "Sam" + i,
              "gender", "male",
              "grade", "A"
          )
      )));
    }
    Sleeper.sleepInSeconds(1);
    Assert.assertEquals(5000, getResponse(5000).size());
    Sleeper.sleepInSeconds(1);

    // 正常扫描
    Result result = domainService.scanAll("user", new DomainInfoScanConfiguration(
        Collections.emptyList(),
        Collections.emptyList(),
        false,
        null,
        "test",
        100,
        0,
        1,
        0
    ));
    Assert.assertTrue(result.isOk());
    Sleeper.sleepInSeconds(5);

    // 带间隔时间的扫描
    result = domainService.scanAll("user", new DomainInfoScanConfiguration(
        Collections.emptyList(),
        Arrays.asList("name", "gender"),
        false,
        null,
        "test",
        1,
        1000,
        1,
        5
    ));
    Assert.assertTrue(result.isOk());
    Sleeper.sleepInSeconds(6);

    // 停止扫描
    System.out.println("Start scanning...");
    result = domainService.scanAll("user", new DomainInfoScanConfiguration(
        Collections.emptyList(),
        Collections.emptyList(),
        false,
        null,
        "test",
        100,
        1000,
        1,
        0
    ));
    Assert.assertTrue(result.isOk());
    Sleeper.sleepInSeconds(3);
    domainService.stopScan("test");
    Sleeper.sleepInSeconds(1);

    // 事件转化
    System.out.println("Test map to event");
    result = domainService.scanAll("user", new DomainInfoScanConfiguration(
        Collections.emptyList(),
        Collections.emptyList(),
        false,
        new EventMappingConfiguration(
            "batch_user",
            250,
            "activity",
            Collections.emptyMap()
        ),
        "test",
        100,
        0,
        0,
        0
    ));
    Assert.assertTrue(result.isOk());
    Sleeper.sleepInSeconds(2);
    this.activityBus
        .read(5000)
        .stream()
        .map(message -> (BasicActivityThreadMessage) message)
        .forEach(message -> {
          System.out.println(message);
          Assert.assertEquals("batch_user", message.getType());
          Assert.assertEquals(250, message.getActivityId());
        });

    // 加入条件筛选和额外属性的事件转化
    result = domainService.scanAll("user", new DomainInfoScanConfiguration(
        Collections.singletonList(
            new SpELPlaywellExpression("toInt(domainId) % 2 == 0")
                .compile()),
        Arrays.asList("name", "gender"),
        false,
        new EventMappingConfiguration(
            "batch_user",
            250,
            "activity",
            ImmutableMap.of("a", 1, "b", "2")
        ),
        "test",
        100,
        0,
        0,
        0
    ));
    Assert.assertTrue(result.isOk());
    Sleeper.sleepInSeconds(2);
    this.activityBus
        .read(5000)
        .stream()
        .map(message -> (BasicActivityThreadMessage) message)
        .forEach(message -> {
          System.out.println(message);
          int userId = Integer.parseInt(message.getDomainId());
          Assert.assertEquals(0, userId % 2);
          Assert.assertEquals("batch_user", message.getType());
          Assert.assertEquals(250, message.getActivityId());
          EasyMap attributes = new EasyMap(message.getAttributes());
          Assert.assertEquals(1, attributes.getInt("a"));
          Assert.assertEquals("2", attributes.getString("b"));
        });

    // 遍历筛选删除
    result = domainService.scanAll("user", new DomainInfoScanConfiguration(
        Collections.singletonList(
            new SpELPlaywellExpression("toInt(domainId) % 2 == 0")
                .compile()),
        Collections.emptyList(),
        true,
        null,
        "test",
        100,
        0,
        0,
        0
    ));
    Assert.assertTrue(result.isOk());
    Sleeper.sleepInSeconds(2);
    result = domainService.scanAll("user", new DomainInfoScanConfiguration(
        Collections.emptyList(),
        Collections.emptyList(),
        false,
        new EventMappingConfiguration(
            "batch_user",
            250,
            "activity",
            Collections.emptyMap()
        ),
        "test",
        100,
        0,
        0,
        0
    ));
    Assert.assertTrue(result.isOk());
    Sleeper.sleepInSeconds(2);
    Collection<BasicActivityThreadMessage> messages = this.activityBus
        .read(5000)
        .stream()
        .map(message -> (BasicActivityThreadMessage) message).collect(Collectors.toList());
    Assert.assertEquals(2500, messages.size());
    messages.forEach(message -> {
      int userId = Integer.parseInt(message.getDomainId());
      Assert.assertNotEquals(0L, userId % 2);
    });
  }

  @After
  public void tearDown() {
    this.cleanAll(true);
  }
}
