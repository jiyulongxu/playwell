package playwell.integration;

import java.io.File;
import java.nio.charset.Charset;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.definition.ActivityDefinitionManager;
import playwell.activity.definition.MySQLActivityDefinitionManager;
import playwell.activity.definition.YAMLActivityDefinitionCodec;
import playwell.common.Result;


/**
 * SimpleMemoryIntergrationPlan的测试用例
 *
 * @author chihongze@gmail.com
 */
public class StandardActivityRunnerIntegrationPlanTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    this.initStandardActivityRunnerIntergrationPlan(false);
  }

  @Test
  public void testIntegrateFromYaml() throws Exception {

    ActivityRunnerIntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();

    Assert.assertTrue(integrationPlan.isReady());

    ActivityDefinitionManager activityDefinitionManager = integrationPlan
        .getActivityDefinitionManager();
    Assert.assertNotNull(activityDefinitionManager);

    // 来，笑一个看看
    Result result = activityDefinitionManager.newActivityDefinition(
        YAMLActivityDefinitionCodec.NAME,
        "1.0",
        FileUtils.readFileToString(
            new File("docs/sample/activity_demo.yml"), Charset.forName("UTF-8")),
        true
    );
    Assert.assertTrue(result.isOk());
    ActivityDefinition definition = result.getFromResultData(
        ActivityDefinitionManager.ResultFields.DEFINITION);
    System.out.println(definition);

    ((MySQLActivityDefinitionManager) activityDefinitionManager).removeAll();
  }

  @After
  public void tearDown() {
    this.cleanStandardActivityRunnerIntergrationPlan(false);
  }
}
