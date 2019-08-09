package playwell.activity;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.activity.ActivityManager.ErrorCodes;
import playwell.activity.ActivityManager.ResultFields;
import playwell.activity.definition.ActivityDefinition;
import playwell.activity.thread.ScheduleConfigItems;
import playwell.common.Result;

/**
 * TestCase for MySQLActivityManager
 *
 * @author chihongze@gmail.com
 */
public class MySQLActivityManagerTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    this.initStandardActivityRunnerIntergrationPlan(false);
  }

  @Test
  public void testCreateAndGet() throws Exception {
    ActivityDefinition activityDefinition = this.createActivityDefinition(
        "docs/sample/activity_demo.yml");

    ActivityManager manager = this.activityManager;
    Result result = manager.createNewActivity(
        activityDefinition.getName(),
        "Test Activity",
        ImmutableMap.of(
            "a", 1,
            "b", 2
        )
    );
    Assert.assertTrue(result.isOk());
    Activity activity = result.getFromResultData(ResultFields.ACTIVITY);
    Assert.assertTrue(activity.getId() > 0);
    Map<String, Object> config = activity.getConfig();
    Assert.assertEquals(1, config.get("a"));
    Assert.assertEquals(2, config.get("b"));
    System.out.println(activityDefinition);
    System.out.println(activity);

    // Test get by id
    Optional<Activity> activityOptional = manager.getActivityById(activity.getId());
    Assert.assertFalse(activityOptional.isPresent());

    Collection<Activity> activities = manager.getActivitiesByDefinitionName(
        activityDefinition.getName());
    Assert.assertTrue(CollectionUtils.isEmpty(activities));

    ((MySQLActivityManager) manager).beforeLoop();
    activities = manager.getActivitiesByDefinitionName(activityDefinition.getName());
    Assert.assertEquals(1, activities.size());
    for (Activity a : activities) {
      Assert.assertEquals(ActivityStatus.COMMON, a.getStatus());
      Assert.assertEquals(activityDefinition.getName(), a.getDefinitionName());
      System.out.println(a);
    }
    activityOptional = manager.getActivityById(activity.getId());
    Assert.assertTrue(activityOptional.isPresent());
    activity = activityOptional.get();
    System.out.println(activity);
  }

  @Test
  public void testChangeStatus() {
    ActivityDefinition activityDefinition = this.createActivityDefinition(
        "docs/sample/activity_demo.yml");

    ActivityManager manager = this.activityManager;
    Result result = manager.createNewActivity(
        activityDefinition.getName(),
        "Test Activity",
        ImmutableMap.of(
            "c", 3,
            "d", 4,
            ScheduleConfigItems.PAUSE_CONTINUE_OLD.getKey(), false
        )
    );
    Assert.assertTrue(result.isOk());
    Activity activity = result.getFromResultData(ResultFields.ACTIVITY);

    ((MySQLActivityManager) manager).beforeLoop();
    Optional<Activity> activityOptional = manager.getActivityById(activity.getId());
    Assert.assertTrue(activityOptional.isPresent());
    activity = activityOptional.get();
    Assert.assertEquals(ActivityStatus.COMMON, activity.getStatus());

    Collection<Activity> activities = manager.getSchedulableActivities();
    Assert.assertEquals(1, activities.size());

    // 暂停
    result = manager.pauseActivity(activity.getId());
    System.out.println(result);
    Assert.assertTrue(result.isOk());
    ((MySQLActivityManager) manager).beforeLoop();
    activityOptional = manager.getActivityById(activity.getId());
    Assert.assertTrue(activityOptional.isPresent());
    activity = activityOptional.get();
    Assert.assertEquals(ActivityStatus.PAUSED, activity.getStatus());
    // 重复暂停
    result = manager.pauseActivity(activity.getId());
    Assert.assertTrue(result.isFail());
    Assert.assertEquals(ErrorCodes.INVALID_STATUS, result.getErrorCode());
    activities = manager.getSchedulableActivities();
    Assert.assertEquals(0, activities.size());
    activities = manager.getActivitiesByStatus(ActivityStatus.PAUSED);
    Assert.assertEquals(1, activities.size());

    // 恢复执行
    result = manager.continueActivity(activity.getId());
    Assert.assertTrue(result.isOk());
    ((MySQLActivityManager) manager).beforeLoop();
    activityOptional = manager.getActivityById(activity.getId());
    Assert.assertTrue(activityOptional.isPresent());
    activity = activityOptional.get();
    Assert.assertEquals(ActivityStatus.COMMON, activity.getStatus());
    activities = manager.getSchedulableActivities();
    Assert.assertEquals(1, activities.size());

    result = manager.killActivity(activity.getId());
    Assert.assertTrue(result.isOk());
    ((MySQLActivityManager) manager).beforeLoop();
    activityOptional = manager.getActivityById(activity.getId());
    Assert.assertFalse(activityOptional.isPresent());
  }

  @Test
  public void testPauseContinueOld() {
    ActivityDefinition activityDefinition = this.createActivityDefinition(
        "docs/sample/activity_demo.yml");

    ActivityManager manager = this.activityManager;
    List<Activity> activities = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      Result result = manager.createNewActivity(
          activityDefinition.getName(),
          "Test Activity",
          ImmutableMap.of(
              "c", i,
              "d", i + 1,
              ScheduleConfigItems.PAUSE_CONTINUE_OLD.getKey(), i % 2 == 0
          )
      );
      Assert.assertTrue(result.isOk());
      activities.add(result.getFromResultData(ResultFields.ACTIVITY));
    }

    for (Activity activity : activities) {
      Result result = manager.pauseActivity(activity.getId());
      Assert.assertTrue(result.isOk());
    }

    ((MySQLActivityManager) manager).beforeLoop();

    activities = (List<Activity>) manager.getSchedulableActivities();
    Assert.assertEquals(5, activities.size());
  }

  @After
  public void tearDown() {
    this.cleanStandardActivityRunnerIntergrationPlan(false);
  }
}
