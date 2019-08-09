package playwell.activity.thread;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.tuple.Pair;

public class TestActivityThreadStatusListener implements ActivityThreadStatusListener {

  public static final Map<Pair<Integer, String>, ActivityThread> ALL_THREADS = new ConcurrentHashMap<>();

  public static Optional<ActivityThread> getThread(int activityId, String domainId) {
    return Optional.ofNullable(ALL_THREADS.get(Pair.of(activityId, domainId)));
  }

  public static Object getFromCtx(int activityId, String domainId, String varName) {
    Optional<ActivityThread> activityThreadOptional = getThread(activityId, domainId);
    if (!activityThreadOptional.isPresent()) {
      throw new RuntimeException(String.format(
          "Could not get the activity thread: %d - %s", activityId, domainId));
    }

    ActivityThread activityThread = activityThreadOptional.get();
    return activityThread.getContext().get(varName);
  }

  public static ActivityThreadStatus getStatus(int activityId, String domainId) {
    Optional<ActivityThread> activityThreadOptional = getThread(activityId, domainId);
    if (!activityThreadOptional.isPresent()) {
      throw new RuntimeException(String.format(
          "Could not get the activity thread: %d - %s", activityId, domainId));
    }

    ActivityThread activityThread = activityThreadOptional.get();
    return activityThread.getStatus();
  }

  @Override
  public void init(Object config) {

  }

  @Override
  public void onSpawn(ActivityThread newActivityThread) {
    ALL_THREADS.put(
        Pair.of(newActivityThread.getActivity().getId(), newActivityThread.getDomainId()),
        newActivityThread
    );
  }

  @Override
  public void onStatusChange(ActivityThreadStatus oldStatus, ActivityThread targetThread) {
    ALL_THREADS.put(
        Pair.of(targetThread.getActivity().getId(), targetThread.getDomainId()),
        targetThread
    );
  }

  @Override
  public void onScheduleError(ActivityThread targetThread, Throwable exception) {
    ALL_THREADS.put(
        Pair.of(targetThread.getActivity().getId(), targetThread.getDomainId()),
        targetThread
    );
  }

  @Override
  public void onRepair(ActivityThread activityThread, String problem) {
    ALL_THREADS.put(
        Pair.of(activityThread.getActivity().getId(), activityThread.getDomainId()),
        activityThread
    );
  }
}
