package playwell.route;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import playwell.common.CompareAndCallback;
import playwell.common.EasyMap;
import playwell.common.MySQLCompareAndCallback;
import playwell.common.Result;
import playwell.message.MessageDispatcherListener;
import playwell.route.migration.DefaultMigrationCoordinator;
import playwell.route.migration.DefaultMigrationInputTask;
import playwell.route.migration.DefaultMigrationOutputTask;
import playwell.route.migration.MigrationCoordinator;
import playwell.route.migration.MigrationInputTask;
import playwell.route.migration.MigrationOutputTask;
import playwell.storage.jdbc.JDBCHelper;

/**
 * 基于MySQL存储的SlotsManager
 */
public class MySQLSlotsManager implements SlotsManager, MessageDispatcherListener {

  private static final String COMPARE_AND_CALLBACK_ITEM = "slots";

  // Read write lock
  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  private String[] allSlots = new String[0];

  private DataAccess dataAccess;

  private CompareAndCallback updator;

  private int expectedVersion = 0;

  private MigrationCoordinator migrationCoordinator;

  private MigrationOutputTask migrationOutputTask;

  private MigrationInputTask migrationInputTask;

  public MySQLSlotsManager() {

  }

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    this.allSlots = new String[0];
    final String dataSource = configuration.getString(ConfigItems.DATASOURCE);
    this.dataAccess = new DataAccess(dataSource);
    this.updator = new MySQLCompareAndCallback(dataSource, COMPARE_AND_CALLBACK_ITEM);
    this.migrationCoordinator = new DefaultMigrationCoordinator(dataSource);
    this.migrationOutputTask = new DefaultMigrationOutputTask(dataSource);
    this.migrationInputTask = new DefaultMigrationInputTask(dataSource);
  }

  @Override
  public Result allocSlots(int slotsNum, Map<String, Integer> slotsNumPerNode) {
    if (MapUtils.isEmpty(slotsNumPerNode)) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.INVALID_NODE_SLOTS_NUM,
          "The slots num of per node is invalid"
      );
    }

    final int sumSlots = slotsNumPerNode.values().stream().reduce((a, b) -> a += b).orElse(0);
    if (sumSlots != slotsNum) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.INVALID_NODE_SLOTS_NUM,
          String.format(
              "The target slots num is %d, but the sum of all the node is %d",
              slotsNum,
              sumSlots
          )
      );
    }

    final int currentSlotsCount = dataAccess.count();
    if (currentSlotsCount > 0) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.ALREADY_ALLOCATED,
          "The slots has been already allocated"
      );
    }

    final AtomicInteger slotsIndex = new AtomicInteger(0);

    slotsNumPerNode.forEach((service, nodeSlotsNum) -> {
      for (int i = 0; i < nodeSlotsNum; i++) {
        dataAccess.insert(slotsIndex.getAndIncrement(), service);
      }
    });

    updator.updateVersion();

    return Result.ok();
  }

  @Override
  public Collection<Integer> getSlotsByServiceName(String serviceName) {
    try {
      rwLock.readLock().lock();
      final List<Integer> slots = new LinkedList<>();
      for (int i = 0; i < allSlots.length; i++) {
        String service = allSlots[i];
        if (service.equals(serviceName)) {
          slots.add(i);
        }
      }
      return slots;
    } finally {
      rwLock.readLock().unlock();
    }
  }

  @Override
  public Optional<String> getServiceNameBySlot(int slot) {
    try {
      rwLock.readLock().lock();
      if (slot >= allSlots.length) {
        return Optional.empty();
      }

      return Optional.of(allSlots[slot]);
    } finally {
      rwLock.readLock().unlock();
    }
  }

  @Override
  public Result getSlotsDistribution() {
    try {
      rwLock.readLock().lock();
      final Map<String, Long> groupingCount = Arrays.stream(allSlots).collect(
          Collectors.groupingBy(Function.identity(), Collectors.counting()));

      return Result.okWithData(ImmutableMap.of(
          ResultFields.SLOTS, allSlots.length,
          ResultFields.DISTRIBUTION, groupingCount
      ));
    } finally {
      rwLock.readLock().unlock();
    }
  }

  @Override
  public String getServiceByHash(long hashCode) {
    int slot = Math.abs((int) (hashCode % allSlots.length));
    return allSlots[slot];
  }

  @Override
  public String getServiceByKey(String string) {
    return getServiceByHash(fnv1Hash(string));
  }

  @Override
  public int getSlotByKey(String string) {
    final long hashCode = fnv1Hash(string);
    return Math.abs((int) (hashCode % allSlots.length));
  }

  @Override
  public Collection<String> getAllServices() {
    return new HashSet<>(Arrays.asList(allSlots));
  }

  @Override
  public MigrationCoordinator getMigrationCoordinator() {
    return this.migrationCoordinator;
  }

  @Override
  public MigrationOutputTask getMigrationOutputTask() {
    return this.migrationOutputTask;
  }

  @Override
  public MigrationInputTask getMigrationInputTask() {
    return this.migrationInputTask;
  }

  @Override
  public void modifyService(Collection<Integer> slots, String service) {
    if (CollectionUtils.isEmpty(slots)) {
      return;
    }

    dataAccess.updateService(slots, service);
    updator.updateVersion();
  }

  @Override
  public void beforeLoop() {
    this.expectedVersion = updator.compareAndCallback(expectedVersion, this::refreshAll);
  }

  public void refreshAll() {
    try {
      rwLock.writeLock().lock();
      this.allSlots = dataAccess.getAll();
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  // 清除所有的slots记录，只用于测试
  public void removeAll() {
    dataAccess.truncate();
  }

  private long fnv1Hash(String key) {
    final int p = 16777619;
    int hash = (int) 2166136261L;
    for (int i = 0; i < key.length(); i++) {
      hash = (hash ^ key.charAt(i)) * p;
    }
    hash += hash << 13;
    hash ^= hash >> 7;
    hash += hash << 3;
    hash ^= hash >> 17;
    hash += hash << 5;
    return hash;
  }

  interface ConfigItems {

    String DATASOURCE = "datasource";
  }

  static class DataAccess {

    private String dataSource;

    DataAccess(String dataSource) {
      this.dataSource = dataSource;
    }

    void insert(int slot, String service) {
      JDBCHelper.execute(
          dataSource,
          "INSERT INTO `slots` (slot, service) VALUES (?, ?)",
          slot,
          service
      );
    }

    int count() {
      return JDBCHelper.queryOneField(
          dataSource,
          "SELECT count(*) AS c FROM `slots`",
          "c",
          Integer.class
      ).orElse(0);
    }

    String[] getAll() {
      final List<Pair<Integer, String>> tmpSlots = JDBCHelper.queryList(
          dataSource,
          "SELECT slot, service FROM `slots`",
          rs -> Pair.of(
              rs.getInt("slot"),
              rs.getString("service")
          )
      );

      if (CollectionUtils.isEmpty(tmpSlots)) {
        return new String[0];
      }

      final String[] slots = new String[tmpSlots.size()];
      for (Pair<Integer, String> pair : tmpSlots) {
        slots[pair.getKey()] = pair.getValue();
      }
      return slots;
    }

    long updateService(Collection<Integer> slots, String serviceName) {
      final String inCondition = Strings.repeat("?, ", slots.size() - 1) + "?";
      return JDBCHelper.execute(
          dataSource,
          String.format("UPDATE `slots` SET `service` = ? WHERE `slot` IN (%s)", inCondition),
          JDBCHelper.flattenSQLArgs(serviceName, slots)
      );
    }

    void truncate() {
      JDBCHelper.execute(
          dataSource,
          "TRUNCATE `slots`"
      );
    }

  }
}
