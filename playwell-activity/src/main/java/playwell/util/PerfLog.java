package playwell.util;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.Mappable;

/**
 * Performance log util
 */
public class PerfLog {

  private static final Logger logger = LogManager.getLogger("performance");

  private static final LinkedList<PerfSpan> spanStack = new LinkedList<>();

  private static final LinkedList<PerfRecord> recordStack = new LinkedList<>();

  private static List<PerfRecord> recordsSnapshot = Collections.emptyList();

  private static long lastOutputTime = 0L;

  private static boolean enable = false;

  private static long outputPeriod = 1000;

  public static synchronized void setEnable(boolean enableIn) {
    enable = enableIn;
    if (!enable) {
      clear();
    }
  }

  public static synchronized void setOutputPeriod(long outputPeriodIn) {
    outputPeriod = outputPeriodIn;
  }

  public static synchronized void clear() {
    spanStack.clear();
    recordStack.clear();
  }

  public static synchronized void beginSpan(String name) {
    if (!enable) {
      return;
    }
    if (spanStack.size() == 0) {
      spanStack.push(new PerfSpan(name));
    } else {
      final PerfSpan baseSpan = spanStack.peek();
      final String fullSpanName = String.format("%s.%s", baseSpan.getName(), name);
      spanStack.push(new PerfSpan(fullSpanName));
    }
  }

  public static synchronized void endSpan() {
    endSpan("");
  }

  public static synchronized void endSpan(String desc) {
    if (!enable) {
      return;
    }
    if (spanStack.size() == 0) {
      return;
    }
    final PerfSpan currentSpan = spanStack.pop();
    final long usedTime = System.currentTimeMillis() - currentSpan.getBeginTime();
    recordStack.push(new PerfRecord(
        currentSpan.getName(),
        usedTime,
        desc
    ));
  }

  public static synchronized void endRootSpan() {
    endRootSpan("");
  }

  public static synchronized void endRootSpan(String desc) {
    if (!enable) {
      return;
    }

    if (spanStack.size() == 0) {
      return;
    }

    final PerfSpan rootSpan = spanStack.getLast();
    final long usedTime = System.currentTimeMillis() - rootSpan.getBeginTime();
    recordStack.push(new PerfRecord(
        rootSpan.getName(),
        usedTime,
        desc
    ));
  }

  public static synchronized void outputPerfLog() {
    if (!enable) {
      return;
    }

    final long now = System.currentTimeMillis();

    if (now - lastOutputTime < outputPeriod) {
      return;
    }

    recordsSnapshot = new LinkedList<>(recordStack);

    while (!recordStack.isEmpty()) {
      final PerfRecord record = recordStack.removeLast();
      if (StringUtils.isEmpty(record.getDesc())) {
        logger.info(String.format("%s used time: %d", record.getName(), record.getUsedTime()));
      } else {
        logger.info(String.format("%s used time: %d, %s", record.getName(), record.getUsedTime(),
            record.getDesc()));
      }
    }

    lastOutputTime = now;
  }

  public static synchronized List<PerfRecord> getPerfRecords() {
    return recordsSnapshot;
  }

  static class PerfSpan {

    private final String name;

    private final long beginTime;

    PerfSpan(String name) {
      this(name, System.currentTimeMillis());
    }

    PerfSpan(String name, long beginTime) {
      this.name = name;
      this.beginTime = beginTime;
    }

    String getName() {
      return name;
    }

    long getBeginTime() {
      return beginTime;
    }
  }

  public static class PerfRecord implements Mappable {

    private final String name;

    private final long usedTime;

    private final String desc;

    PerfRecord(String name, long usedTime, String desc) {
      this.name = name;
      this.usedTime = usedTime;
      this.desc = desc;
    }

    String getName() {
      return name;
    }

    long getUsedTime() {
      return usedTime;
    }

    String getDesc() {
      return desc;
    }

    @Override
    public Map<String, Object> toMap() {
      return ImmutableMap.of(
          Attributes.NAME, this.name,
          Attributes.TIME, this.usedTime,
          Attributes.DESC, this.desc
      );
    }

    @Override
    public String toString() {
      return new JSONObject(toMap()).toJSONString();
    }

    interface Attributes {

      String NAME = "name";

      String TIME = "time";

      String DESC = "desc";
    }
  }
}
