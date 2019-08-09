package playwell.activity.definition;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.activity.definition.ActivityDefinitionManager.ErrorCodes;
import playwell.activity.thread.TestActivityThreadStatusListener;
import playwell.clock.CachedTimestamp;
import playwell.common.Result;
import playwell.message.TestUserBehaviorEvent;

public class MySQLActivityDefinitionManagerTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    super.initStandardActivityRunnerIntergrationPlan(true);
  }

  @Test
  public void testCreateAndGet() throws Exception {
    ActivityDefinitionManager manager = this.activityDefinitionManager;
    final String defName = "test_flow";

    // 什么都没有
    Optional<ActivityDefinition> activityDefinitionOptional = manager
        .getLatestEnableActivityDefinition(defName);
    Assert.assertFalse(activityDefinitionOptional.isPresent());

    // 创建一个
    String definitionString = FileUtils.readFileToString(
        new File("docs/sample/activity_demo.yml"), Charset.defaultCharset());
    Result result = manager.newActivityDefinition(
        YAMLActivityDefinitionCodec.NAME, "0.1", definitionString, true);
    Assert.assertTrue(result.isOk());

    // 还是不会有
    activityDefinitionOptional = manager
        .getLatestEnableActivityDefinition(defName);
    Assert.assertFalse(activityDefinitionOptional.isPresent());

    // 刷新一下
    ((MySQLActivityDefinitionManager) manager).beforeLoop();
    // 应该会有了！
    activityDefinitionOptional = manager
        .getLatestEnableActivityDefinition(defName);
    Assert.assertTrue(activityDefinitionOptional.isPresent());
    System.out.println(activityDefinitionOptional.get());

    // 创建重复的版本
    result = manager.newActivityDefinition(
        YAMLActivityDefinitionCodec.NAME, "0.1", definitionString, true);
    Assert.assertTrue(result.isFail());
    Assert.assertEquals(ErrorCodes.ALREADY_EXIST, result.getErrorCode());

    TimeUnit.SECONDS.sleep(1L);

    // 创建一个不可用的版本
    result = manager.newActivityDefinition(
        YAMLActivityDefinitionCodec.NAME, "0.2", definitionString, false);
    Assert.assertTrue(result.isOk());

    ((MySQLActivityDefinitionManager) manager).beforeLoop();
    Collection<ActivityDefinition> activityDefinitions = manager
        .getActivityDefinitionsByName(defName);
    Assert.assertEquals(2, activityDefinitions.size());
    int i = 2;
    for (ActivityDefinition activityDefinition : activityDefinitions) {
      if (i == 2) {
        Assert.assertFalse(activityDefinition.isEnable());
      }
      Assert.assertEquals(String.format("0.%d", i--), activityDefinition.getVersion());
    }

    activityDefinitionOptional = manager.getLatestEnableActivityDefinition(defName);
    Assert.assertTrue(activityDefinitionOptional.isPresent());
    ActivityDefinition activityDefinition = activityDefinitionOptional.get();
    Assert.assertEquals("0.1", activityDefinition.getVersion());
    Assert.assertTrue(activityDefinition.isEnable());

    // TODO Test getAllLatest
  }

  @Test
  public void testModifyAndGet() throws Exception {
    final ActivityDefinitionManager manager = this.activityDefinitionManager;
    final String defName = "test_flow";

    String definitionString = FileUtils.readFileToString(
        new File("docs/sample/activity_demo.yml"), Charset.defaultCharset());
    Result result = manager.newActivityDefinition(
        YAMLActivityDefinitionCodec.NAME, "0.1", definitionString, true);
    Assert.assertTrue(result.isOk());

    ((MySQLActivityDefinitionManager) manager).beforeLoop();
    Optional<ActivityDefinition> activityDefinitionOptional = manager
        .getLatestEnableActivityDefinition(defName);
    Assert.assertTrue(activityDefinitionOptional.isPresent());

    TimeUnit.SECONDS.sleep(1L);

    // update to not enable
    result = manager.modifyActivityDefinition(
        YAMLActivityDefinitionCodec.NAME, "0.1", definitionString, false);
    Assert.assertTrue(result.isOk());
    activityDefinitionOptional = manager.getLatestEnableActivityDefinition(defName);
    Assert.assertTrue(activityDefinitionOptional.isPresent());
    ActivityDefinition activityDefinition = activityDefinitionOptional.get();
    Assert.assertTrue(activityDefinition.isEnable());

    ((MySQLActivityDefinitionManager) manager).beforeLoop();
    activityDefinitionOptional = manager.getLatestEnableActivityDefinition(defName);
    Assert.assertFalse(activityDefinitionOptional.isPresent());
    activityDefinitionOptional = manager.getActivityDefinition(defName, "0.1");
    Assert.assertTrue(activityDefinitionOptional.isPresent());
    activityDefinition = activityDefinitionOptional.get();
    Assert.assertFalse(activityDefinition.isEnable());
    System.out.println(activityDefinition);
  }

  @Test
  public void testChangeVersion() throws Exception {
    final ActivityDefinitionManager manager = this.activityDefinitionManager;

    final String defName = "change_version";

    String definitionString = FileUtils.readFileToString(
        new File("docs/sample/test_definitions/versions/version1.yml"),
        Charset.defaultCharset()
    );

    Result result = manager.newActivityDefinition(
        YAMLActivityDefinitionCodec.NAME, "1", definitionString, true);
    Assert.assertTrue(result.isOk());

    TimeUnit.SECONDS.sleep(1L);

    result = activityManager.createNewActivity(
        defName, "Change version", Collections.emptyMap());
    Assert.assertTrue(result.isOk());

    sendMessage(new TestUserBehaviorEvent(
        "1",
        "test",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(1L);

    // 换一个版本

    definitionString = FileUtils.readFileToString(
        new File("docs/sample/test_definitions/versions/version2.yml"),
        Charset.defaultCharset()
    );

    result = manager.newActivityDefinition(
        YAMLActivityDefinitionCodec.NAME, "2", definitionString, true);
    Assert.assertTrue(result.isOk());

    TimeUnit.SECONDS.sleep(1L);

    sendMessage(new TestUserBehaviorEvent(
        "1",
        "注册成功",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(1L);

    Assert.assertEquals(
        "version1",
        TestActivityThreadStatusListener.getFromCtx(1, "1", "v")
    );

    sendMessage(new TestUserBehaviorEvent(
        "2",
        "test",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(1L);
    sendMessage(new TestUserBehaviorEvent(
        "2",
        "注册成功",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    TimeUnit.SECONDS.sleep(1L);

    Assert.assertEquals(
        "version2",
        TestActivityThreadStatusListener.getFromCtx(1, "2", "v")
    );
  }

  @After
  public void tearDown() {
    super.cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
