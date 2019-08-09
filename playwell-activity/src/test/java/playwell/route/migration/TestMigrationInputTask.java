package playwell.route.migration;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import playwell.activity.thread.ActivityThread;

/**
 * 用于测试的MigrationInputTask 将接收的ActivityThread保存到内存中供比较
 */
public class TestMigrationInputTask extends DefaultMigrationInputTask {

  private final Map<Pair<Integer, String>, ActivityThread> allThreads = new HashMap<>();

  public TestMigrationInputTask(String dataSource) {
    super(dataSource);
  }

  @Override
  protected void batchSave(Collection<ActivityThread> receivedActivityThreads) {
    receivedActivityThreads.forEach(thread -> allThreads.put(
        Pair.of(thread.getActivity().getId(), thread.getDomainId()), thread));
  }

  public Collection<ActivityThread> all() {
    return allThreads.values();
  }

  public Optional<ActivityThread> get(int activityId, String domainId) {
    return Optional.ofNullable(allThreads.get(Pair.of(activityId, domainId)));
  }
}
