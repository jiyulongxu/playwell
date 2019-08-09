package playwell.activity.thread;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Optional;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import playwell.PlaywellBaseTestCase;
import playwell.activity.Activity;
import playwell.activity.thread.PlaywellActivityThreadScheduler.ScheduleContextVars;
import playwell.clock.CachedTimestamp;
import playwell.common.Result;
import playwell.message.ServiceResponseMessage;
import playwell.message.TestUserBehaviorEvent;
import playwell.message.sys.ActivityThreadCtrlMessage;
import playwell.message.sys.RepairArguments;
import playwell.message.sys.RepairArguments.RepairCtrl;
import playwell.util.Sleeper;

/**
 * Test activity thread repair function
 */
public class ActivityThreadRepairingTestCase extends PlaywellBaseTestCase {

  @Before
  public void setUp() {
    this.initStandardActivityRunnerIntergrationPlan(true);
  }

  @Test
  public void testSyncRepair() {
    // 测试正常操作
    final Activity activity = this.spawn(
        "./docs/sample/test_definitions/repair/sync_repair.yml",
        new TestUserBehaviorEvent(
            "1",
            "test",
            ImmutableMap.of("num", 1),
            CachedTimestamp.nowMilliseconds()
        ));
    Sleeper.sleepInSeconds(1);
    ActivityThreadStatus status = TestActivityThreadStatusListener.getStatus(
        activity.getId(), "1");
    Assert.assertEquals(ActivityThreadStatus.FINISHED, status);

    // 触发需要repair的事件
    sendMessage(new TestUserBehaviorEvent(
        "2",
        "test",
        ImmutableMap.of("num", 2),
        CachedTimestamp.nowMilliseconds()
    ));
    Sleeper.sleepInSeconds(1);
    status = TestActivityThreadStatusListener.getStatus(
        activity.getId(), "2");
    Assert.assertEquals(ActivityThreadStatus.WAITING, status);
    Assert.assertTrue((boolean) TestActivityThreadStatusListener.getFromCtx(
        activity.getId(), "2", ScheduleContextVars.IN_REPAIRING));

    // 执行修复指令
    // 首先，在SyncAction上执行WAITING操作应该不会有什么效果的
    sendMessage(ActivityThreadCtrlMessage.repairCommand(
        "",
        "",
        activity.getId(),
        "2",
        new RepairArguments(
            RepairCtrl.WAITING,
            ImmutableMap.of("num", 3),
            ""
        )
    ));
    Sleeper.sleepInSeconds(1);
    Assert.assertEquals(2, TestActivityThreadStatusListener.getFromCtx(
        activity.getId(), "2", "num"));

    // 执行GOTO指令，更新上下文，并跳转到指定的Action
    sendMessage(ActivityThreadCtrlMessage.repairCommand(
        "",
        "",
        activity.getId(),
        "2",
        new RepairArguments(
            RepairCtrl.GOTO,
            ImmutableMap.of("num", 7),
            "receive"
        )
    ));
    Sleeper.sleepInSeconds(1);
    Optional<ActivityThread> activityThreadOptional = TestActivityThreadStatusListener.getThread(
        activity.getId(), "2");
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals("receive", activityThread.getCurrentAction());
    Assert.assertEquals(7, activityThread.getContext().get("num"));
    sendMessage(new TestUserBehaviorEvent(
        "2",
        "添加购物车",
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    ));
    Sleeper.sleepInSeconds(1);
    Assert.assertEquals(
        ActivityThreadStatus.WAITING,
        TestActivityThreadStatusListener.getStatus(
            activity.getId(),
            "2"
        )
    );
    Assert.assertEquals(
        14,
        TestActivityThreadStatusListener.getFromCtx(
            activity.getId(),
            "2",
            "num"
        )
    );

    // 执行RETRY指令
    sendMessage(ActivityThreadCtrlMessage.repairCommand(
        "",
        "",
        activity.getId(),
        "2",
        new RepairArguments(
            RepairCtrl.RETRY,
            ImmutableMap.of("num", 3),
            ""
        )
    ));
    Sleeper.sleepInSeconds(1);
    Assert.assertEquals(ActivityThreadStatus.FINISHED, TestActivityThreadStatusListener.getStatus(
        activity.getId(),
        "2"
    ));
    Assert.assertEquals(3, TestActivityThreadStatusListener.getFromCtx(
        activity.getId(),
        "2",
        "num"
    ));
  }

  @Test
  public void testAsyncRepair() {
    // 正常执行
    final Activity activity = spawn(
        "./docs/sample/test_definitions/repair/async_repair.yml",
        new TestUserBehaviorEvent(
            "1",
            "test",
            ImmutableMap.of("name", "Sam"),
            CachedTimestamp.nowMilliseconds()
        )
    );
    Sleeper.sleepInSeconds(1);
    Assert.assertEquals(
        ActivityThreadStatus.FINISHED,
        TestActivityThreadStatusListener.getStatus(activity.getId(), "1")
    );

    // 进入repair状态
    sendMessage(new TestUserBehaviorEvent(
        "2",
        "test",
        ImmutableMap.of("name", "Jack"),
        CachedTimestamp.nowMilliseconds()
    ));
    Sleeper.sleepInSeconds(1);
    Assert.assertEquals(
        ActivityThreadStatus.WAITING,
        TestActivityThreadStatusListener.getStatus(activity.getId(), "2")
    );
    Assert.assertTrue((boolean) TestActivityThreadStatusListener.getFromCtx(
        activity.getId(),
        "2",
        ScheduleContextVars.IN_REPAIRING
    ));

    // 命令继续等待
    sendMessage(ActivityThreadCtrlMessage.repairCommand(
        "",
        "",
        activity.getId(),
        "2",
        new RepairArguments(
            RepairCtrl.WAITING,
            ImmutableMap.of("name", "Tom"),
            ""
        )
    ));
    Sleeper.sleepInSeconds(1);
    Optional<ActivityThread> activityThreadOptional = activityThreadPool.getActivityThread(
        activity.getId(),
        "2"
    );
    Assert.assertTrue(activityThreadOptional.isPresent());
    ActivityThread activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.WAITING, activityThread.getStatus());
    Assert.assertEquals("Tom", activityThread.getContext().get("name"));
    // 送你一个Response
    sendMessage(new ServiceResponseMessage(
        CachedTimestamp.nowMilliseconds(),
        activity.getId(),
        "2",
        "mock",
        "",
        "",
        Result.STATUS_OK,
        "",
        "",
        ImmutableMap.of("echo", "James")
    ));
    Sleeper.sleepInSeconds(1);
    Assert.assertEquals(
        ActivityThreadStatus.WAITING,
        TestActivityThreadStatusListener.getStatus(activity.getId(), "2")
    );
    Assert.assertTrue((boolean) TestActivityThreadStatusListener.getFromCtx(
        activity.getId(),
        "2",
        ScheduleContextVars.IN_REPAIRING
    ));

    // GOTO指令
    sendMessage(ActivityThreadCtrlMessage.repairCommand(
        "",
        "",
        activity.getId(),
        "2",
        new RepairArguments(
            RepairCtrl.GOTO,
            Collections.emptyMap(),
            "receive"
        )
    ));
    Sleeper.sleepInSeconds(1);
    activityThreadOptional = activityThreadPool.getActivityThread(
        activity.getId(),
        "2"
    );
    Assert.assertTrue(activityThreadOptional.isPresent());
    activityThread = activityThreadOptional.get();
    Assert.assertEquals(ActivityThreadStatus.WAITING, activityThread.getStatus());
    Assert.assertEquals("receive", activityThread.getCurrentAction());
    sendMessage(new TestUserBehaviorEvent(
        "2",
        "test",
        ImmutableMap.of("name", "Betty"),
        CachedTimestamp.nowMilliseconds()
    ));
    Sleeper.sleepInSeconds(1);
    Assert.assertEquals(
        ActivityThreadStatus.WAITING,
        TestActivityThreadStatusListener.getStatus(activity.getId(), "2")
    );
    Assert.assertTrue((boolean) TestActivityThreadStatusListener.getFromCtx(
        activity.getId(),
        "2",
        ScheduleContextVars.IN_REPAIRING
    ));

    // RETRY指令
    sendMessage(ActivityThreadCtrlMessage.repairCommand(
        "",
        "",
        activity.getId(),
        "2",
        new RepairArguments(
            RepairCtrl.RETRY,
            ImmutableMap.of("name", "Sam"),
            ""
        )
    ));
    Sleeper.sleepInSeconds(1);
    Assert.assertEquals(
        ActivityThreadStatus.FINISHED,
        TestActivityThreadStatusListener.getStatus(activity.getId(), "2")
    );
  }

  @After
  public void tearDown() {
    this.cleanStandardActivityRunnerIntergrationPlan(true);
  }
}
