package playwell;


import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.Assert;
import playwell.action.ActionManager;
import playwell.activity.Activity;
import playwell.activity.ActivityManager;
import playwell.activity.ActivityManager.ResultFields;
import playwell.activity.ActivityRunner;
import playwell.activity.MySQLActivityManager;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.definition.ActivityDefinitionManager;
import playwell.activity.definition.MySQLActivityDefinitionManager;
import playwell.activity.definition.YAMLActivityDefinitionCodec;
import playwell.activity.thread.ActivityThreadPool;
import playwell.activity.thread.ActivityThreadScheduler;
import playwell.clock.Clock;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.integration.ActivityRunnerIntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.TopComponentType;
import playwell.message.Message;
import playwell.message.MessageDispatcherListener;
import playwell.message.bus.MessageBus;
import playwell.message.bus.MessageBusManager;
import playwell.message.bus.MessageBusNotAvailableException;
import playwell.message.bus.MySQLMessageBusManager;
import playwell.message.domainid.MessageDomainIDStrategyManager;
import playwell.message.domainid.MySQLMessageDomainIDStrategyManager;
import playwell.route.MySQLSlotsManager;
import playwell.route.SlotsManager;
import playwell.service.MySQLServiceMetaManager;
import playwell.service.ServiceMeta;
import playwell.service.ServiceMetaManager;
import playwell.service.ServiceRunner;
import playwell.storage.rocksdb.RocksDBHelper;
import playwell.trigger.TriggerManager;
import playwell.util.PerfLog;

/**
 * Base TestCase 为其他的测试用例提供辅助方法支撑，比如基础组件的初始化
 *
 * @author chihongze@gmail.com
 */
public class PlaywellBaseTestCase {

  protected ActivityRunnerIntegrationPlan activityRunnerIntergrationPlan = null;

  protected ActivityDefinitionManager activityDefinitionManager = null;

  protected ActivityManager activityManager = null;

  protected ActivityThreadPool activityThreadPool = null;

  protected Clock clock = null;

  protected ActivityThreadScheduler activityThreadScheduler = null;

  protected MessageBusManager messageBusManager = null;

  protected ServiceMetaManager serviceMetaManager = null;

  protected TriggerManager triggerManager = null;

  protected ActionManager actionManager = null;

  protected ActivityRunner activityRunner = null;

  protected ServiceRunner serviceRunner = null;

  protected SlotsManager slotsManager = null;

  protected MessageDomainIDStrategyManager messageDomainIDStrategyManager = null;

  protected void initStandardActivityRunnerIntergrationPlan(boolean startRunner) {
    initStandardActivityRunnerIntergrationPlan(
        "config/playwell_test.yml", startRunner);
  }

  protected void initStandardActivityRunnerIntergrationPlan(String configFile,
      boolean startRunner) {

    PerfLog.setEnable(false);

    final EasyMap rocksDBConfig = new EasyMap(ImmutableMap.builder()
        .put(RocksDBHelper.ConfigItems.PATH,
            "/data/test_data/test_rocksdb_" + DateFormatUtils.format(new Date(), "yyyyMMddHHmmss"))
        .put(RocksDBHelper.ConfigItems.COLUMN_FAMILIES, Arrays.asList(
            ImmutableMap.of(RocksDBHelper.CfConfigItems.NAME, "default"),
            ImmutableMap.of(RocksDBHelper.CfConfigItems.NAME, "test"),
            ImmutableMap.of(RocksDBHelper.CfConfigItems.NAME, "clock"),
            ImmutableMap.of(RocksDBHelper.CfConfigItems.NAME, "test_clock"),
            ImmutableMap.of(RocksDBHelper.CfConfigItems.NAME, "thread")
        ))
        .build());
    RocksDBHelper.init(rocksDBConfig);

    final IntegrationPlanFactory integrationPlanFactory = IntegrationPlanFactory.getInstance();
    integrationPlanFactory.clean();
    integrationPlanFactory.intergrateWithYamlConfigFile(
        "playwell.integration.StandardActivityRunnerIntegrationPlan",
        configFile
    );

    activityRunnerIntergrationPlan = IntegrationPlanFactory.currentPlan();
    activityDefinitionManager = activityRunnerIntergrationPlan.getActivityDefinitionManager();
    activityManager = activityRunnerIntergrationPlan.getActivityManager();
    activityThreadPool = activityRunnerIntergrationPlan.getActivityThreadPool();
    clock = activityRunnerIntergrationPlan.getClock();
    activityThreadScheduler = activityRunnerIntergrationPlan.getActivityThreadScheduler();
    messageBusManager = activityRunnerIntergrationPlan.getMessageBusManager();
    serviceMetaManager = activityRunnerIntergrationPlan.getServiceMetaManager();
    triggerManager = activityRunnerIntergrationPlan.getTriggerManager();
    actionManager = activityRunnerIntergrationPlan.getActionManager();
    activityRunner = activityRunnerIntergrationPlan.getActivityRunner();
    serviceRunner = activityRunnerIntergrationPlan.getServiceRunner();
    messageDomainIDStrategyManager = activityRunnerIntergrationPlan
        .getMessageDomainIDStrategyManager();

    messageDomainIDStrategyManager.addMessageDomainIDStrategy(
        "user_id",
        "containsAttr('user_id')",
        "eventAttr('user_id')"
    );
    ((MySQLMessageDomainIDStrategyManager) messageDomainIDStrategyManager).beforeLoop();

    if (activityRunnerIntergrationPlan.contains(TopComponentType.SLOTS_MANAGER)) {
      this.slotsManager = this.activityRunnerIntergrationPlan.getSlotsManager();
    }

    clock.clean(Long.MAX_VALUE);

    if (startRunner) {
      new Thread(() -> activityRunner.dispatch()).start();
      new Thread(() -> serviceRunner.dispatch()).start();
    }
  }

  protected void cleanStandardActivityRunnerIntergrationPlan(boolean stopRunner) {
    if (stopRunner) {
      activityRunner.stop();
      while (!activityRunner.isStopped()) {
        try {
          TimeUnit.MILLISECONDS.sleep(10L);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      serviceRunner.stop();
      while (!serviceRunner.isStopped()) {
        try {
          TimeUnit.MILLISECONDS.sleep(10L);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
    IntegrationPlanFactory.getInstance().clean();
    ((MySQLActivityDefinitionManager) activityDefinitionManager).removeAll();
    ((MySQLActivityManager) activityManager).removeAll();
    ((MySQLMessageDomainIDStrategyManager) messageDomainIDStrategyManager).removeAll();
    ((MySQLServiceMetaManager) serviceMetaManager).removeAll();
    ((MySQLMessageBusManager) messageBusManager).removeAll();
    if (slotsManager != null) {
      ((MySQLSlotsManager) slotsManager).removeAll();
    }
    clock.clean(Long.MAX_VALUE);
    Optional<ServiceMeta> addServiceMetaOptional = serviceMetaManager.getServiceMetaByName("add");
    Assert.assertTrue(addServiceMetaOptional.isPresent());
  }

  protected ActivityDefinition createActivityDefinition(String yamlDefinitionFile) {
    try {
      Result result = activityDefinitionManager.newActivityDefinition(
          YAMLActivityDefinitionCodec.NAME,
          "1.0",
          FileUtils.readFileToString(new File(yamlDefinitionFile), Charset.forName("UTF-8")),
          true
      );
      System.out.println(result);
      Assert.assertTrue(result.isOk());
      ((MessageDispatcherListener) activityDefinitionManager).beforeLoop();
      return result.getFromResultData(ActivityDefinitionManager.ResultFields.DEFINITION);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected Activity createActivity(String definitionName, String displayName,
      Map<String, Object> config) {
    final Result result = activityManager.createNewActivity(definitionName, displayName, config);
    System.out.println(result);
    Assert.assertTrue(result.isOk());
    return result.getFromResultData(ActivityManager.ResultFields.ACTIVITY);
  }

  protected Activity spawn(String definitionPath, Message triggerEvent) {
    ActivityDefinition activityDefinition = this.createActivityDefinition(definitionPath);
    Result result = activityManager.createNewActivity(
        activityDefinition.getName(), "Test Activity", Collections.emptyMap());
    Assert.assertTrue(result.isOk());

    Optional<MessageBus> messageBusOptional = this.messageBusManager
        .getMessageBusByName("activity_bus");
    if (!messageBusOptional.isPresent()) {
      throw new RuntimeException("No message bus activity_bus");
    }
    MessageBus messageBus = messageBusOptional.get();

    try {
      messageBus.write(triggerEvent);
    } catch (MessageBusNotAvailableException e) {
      throw new RuntimeException(e);
    }

    return result.getFromResultData(ResultFields.ACTIVITY);
  }

  protected void sendMessage(Message message) {
    Optional<MessageBus> messageBusOptional = this.messageBusManager
        .getMessageBusByName("activity_bus");
    if (!messageBusOptional.isPresent()) {
      throw new RuntimeException("No message bus activity_bus");
    }
    MessageBus messageBus = messageBusOptional.get();

    try {
      messageBus.write(message);
    } catch (MessageBusNotAvailableException e) {
      throw new RuntimeException(e);
    }
  }

  protected MessageBus getMessageBus(String messageBusName) {
    return messageBusManager.getMessageBusByName(messageBusName).orElseThrow(
        () -> new RuntimeException(String.format("Unknown message bus: %s", messageBusName)));
  }
}
