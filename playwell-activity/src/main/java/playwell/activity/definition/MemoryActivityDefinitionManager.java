package playwell.activity.definition;


import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import playwell.common.EasyMap;

/**
 * 基于内存缓存的ActivityDefinition管理器
 *
 * @author chihongze@gmail.com
 */
public abstract class MemoryActivityDefinitionManager extends BaseActivityDefinitionManager {

  protected final Map<String, List<ActivityDefinition>> allDefinitions;

  protected final Map<String, ActivityDefinition> latestEnableDefinitions;

  // 读写锁
  protected final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  // 通用比较器
  protected final Comparator<ActivityDefinition> comparator = Comparator.comparing(
      ActivityDefinition::getCreatedOn).reversed();

  public MemoryActivityDefinitionManager() {
    this.allDefinitions = new HashMap<>();
    this.latestEnableDefinitions = new HashMap<>();
  }

  @Override
  protected void initActivityDefinitionManager(EasyMap config) {
    // Do nothing
  }

  @Override
  public Collection<ActivityDefinition> getAllLatestDefinitions() {
    try {
      rwLock.readLock().lock();
      return allDefinitions.values().stream().map(list -> list.get(0)).collect(Collectors.toList());
    } finally {
      rwLock.readLock().unlock();
    }
  }

  @Override
  public Collection<ActivityDefinition> getActivityDefinitionsByName(String name) {
    try {
      rwLock.readLock().lock();
      Collection<ActivityDefinition> definitions = allDefinitions.get(name);
      if (CollectionUtils.isEmpty(definitions)) {
        return Collections.emptyList();
      }
      return definitions;
    } finally {
      rwLock.readLock().unlock();
    }
  }

  @Override
  public Optional<ActivityDefinition> getLatestEnableActivityDefinition(String name) {
    try {
      rwLock.readLock().lock();
      return Optional.ofNullable(latestEnableDefinitions.get(name));
    } finally {
      rwLock.readLock().unlock();
    }
  }

  @Override
  public Optional<ActivityDefinition> getActivityDefinition(String name, String version) {
    try {
      rwLock.readLock().lock();
      return allDefinitions.getOrDefault(name, Collections.emptyList()).stream()
          .filter(def -> def.getName().equals(name) && def.getVersion().equals(version))
          .findFirst();
    } finally {
      rwLock.readLock().unlock();
    }
  }
}
