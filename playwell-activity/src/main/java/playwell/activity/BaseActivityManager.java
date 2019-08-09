package playwell.activity;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import playwell.integration.IntergrationUtils;

/**
 * Base ActivityManager
 *
 * @author chihongze@gmail.com
 */
public abstract class BaseActivityManager implements ActivityManager {

  private static final Logger logger = LogManager.getLogger(BaseActivityManager.class);

  // 活动状态监听器
  private final List<ActivityStatusListener> listeners = new LinkedList<>();

  protected BaseActivityManager() {

  }

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    initActivityManager(configuration);
    IntergrationUtils.loadAndInitSubComponents(configuration.getObjectList("listeners"))
        .forEach(listenerObj -> listeners.add((ActivityStatusListener) listenerObj));
  }

  protected abstract void initActivityManager(EasyMap configuration);

  protected void callbackListeners(Consumer<ActivityStatusListener> callback) {
    if (CollectionUtils.isNotEmpty(listeners)) {
      try {
        listeners.parallelStream().forEach(callback);
      } catch (Exception e) {
        logger.error(
            "Error happened when callback ActivityStatusListener", e);
      }
    }
  }
}
