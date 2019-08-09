package playwell.storage.rocksdb;

import com.alibaba.fastjson.JSONObject;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.BackupEngine;
import org.rocksdb.BackupInfo;
import org.rocksdb.BackupableDBOptions;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.Cache;
import org.rocksdb.Checkpoint;
import org.rocksdb.ClockCache;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompressionType;
import org.rocksdb.DBOptions;
import org.rocksdb.Env;
import org.rocksdb.InfoLogLevel;
import org.rocksdb.LRUCache;
import org.rocksdb.Options;
import org.rocksdb.RateLimiter;
import org.rocksdb.RateLimiterMode;
import org.rocksdb.RestoreOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WALRecoveryMode;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.storage.rocksdb.RocksDBHelper.ConfigItems.BackupConfigItems;
import playwell.storage.rocksdb.RocksDBHelper.ConfigItems.RateLimiterConfigItems;

/**
 * RocksDBHelper负责初始化RocksDB实例，以及封装了RocksDB常见的操作
 */
public class RocksDBHelper implements Closeable {

  private static final Logger logger = LogManager.getLogger(RocksDBHelper.class);

  private static final String RESTORE_ROCKS = "RESTORE_ROCKS";

  private static final RocksDBHelper INSTANCE = new RocksDBHelper();

  private static final Lock operationLock = new ReentrantLock();

  private static Map<String, EasyMap> columnFamilyConfigs;

  private static volatile BackupEngine backupEngine = null;

  private static volatile RocksDBBackupProcess backupProcess = null;

  private static Lock backupLock = new ReentrantLock();

  private static ExecutorService backupExecutor = null;

  static {
    RocksDB.loadLibrary();
  }

  private volatile boolean inited = false;

  private RocksDB rocksDBInstance;

  private Map<String, ColumnFamilyHandle> columnFamilyHandles = new HashMap<>();

  private RocksDBHelper() {

  }

  public static RocksDBHelper getInstance() {
    return INSTANCE;
  }

  public static void init(EasyMap configuration) {
    final String dbPath = configuration.getString(ConfigItems.PATH);

    doRestore(configuration, dbPath);

    final Map<String, Cache> allBlockCaches = configuration
        .getSubArgumentsList(ConfigItems.BLOCK_CACHES)
        .stream()
        .collect(Collectors.toMap(
            cacheConfig -> cacheConfig.getString(BlockCacheConfigItems.NAME),
            cacheConfig -> {
              final String name = cacheConfig.getString(BlockCacheConfigItems.NAME);
              final String type = cacheConfig.getString(BlockCacheConfigItems.TYPE);
              final long capacity = cacheConfig.getLong(BlockCacheConfigItems.CAPACITY);
              final int numShardBits = cacheConfig.getInt(
                  BlockCacheConfigItems.NUM_SHARD_BITS,
                  BlockCacheConfigItems.DEFAULT_NUM_SHARD_BITS
              );
              final boolean strictCapacityLimit = cacheConfig.getBoolean(
                  BlockCacheConfigItems.STRICT_CAPACITY_LIMIT,
                  BlockCacheConfigItems.DEFAULT_STRICT_CAPACITY_LIMIT
              );

              if (BlockCacheTypes.LRU.equalsIgnoreCase(type)) {
                final double highPriPoolRatio = cacheConfig.getDouble(
                    BlockCacheConfigItems.HIGH_PRI_POOL_RATIO,
                    BlockCacheConfigItems.DEFAULT_HIGH_PRI_POOL_RATIO
                );
                return new LRUCache(
                    capacity,
                    numShardBits,
                    strictCapacityLimit,
                    highPriPoolRatio
                );
              } else if (BlockCacheTypes.CLOCK.equalsIgnoreCase(type)) {
                return new ClockCache(
                    capacity,
                    numShardBits,
                    strictCapacityLimit
                );
              } else {
                throw new RuntimeException(String.format(
                    "Invalid RocksDB config, unknown type %s for block cache %s",
                    type,
                    name
                ));
              }
            }
        ));

    final List<EasyMap> cfConfigList = configuration
        .getSubArgumentsList(ConfigItems.COLUMN_FAMILIES);
    final Map<String, ColumnFamilyOptions> columnFamilies = cfConfigList.stream()
        .collect(Collectors.toMap(
            cfConfig -> cfConfig.getString(CfConfigItems.NAME),
            cfConfig -> {
              final String name = cfConfig.getString(CfConfigItems.NAME);

              final ColumnFamilyOptions cfOptions = new ColumnFamilyOptions();
              cfOptions.setMergeOperatorName("stringappend");

              // 处理压缩选项，默认使用LZ4压缩算法
              final CompressionType compressionType = CompressionType.getCompressionType(
                  cfConfig.getString(
                      CfConfigItems.COMPRESSION_TYPE,
                      CfConfigItems.DEFAULT_COMPRESSION_TYPE
                  )
              );
              cfOptions.setCompressionType(compressionType);
              if (cfConfig.contains(CfConfigItems.COMPRESSION_PER_LEVEL)) {
                final List<CompressionType> compressionTypes = cfConfig
                    .getStringList(CfConfigItems.COMPRESSION_PER_LEVEL)
                    .stream()
                    .map(CompressionType::getCompressionType)
                    .collect(Collectors.toList());
                cfOptions.setCompressionPerLevel(compressionTypes);
              }

              if (cfConfig.contains(CfConfigItems.WRITE_BUFFER_SIZE)) {
                cfOptions.setWriteBufferSize(cfConfig.getLong(CfConfigItems.WRITE_BUFFER_SIZE));
              }

              if (cfConfig.contains(CfConfigItems.MAX_WRITE_BUFFER_NUMBER)) {
                cfOptions.setMaxWriteBufferNumber(
                    cfConfig.getInt(CfConfigItems.MAX_WRITE_BUFFER_NUMBER));
              }

              if (cfConfig.contains(CfConfigItems.MIN_WRITE_BUFFER_NUMBER_TO_MERGE)) {
                cfOptions.setMinWriteBufferNumberToMerge(
                    cfConfig.getInt(CfConfigItems.MIN_WRITE_BUFFER_NUMBER_TO_MERGE));
              }

              // Level compaction
              if (cfConfig.contains(CfConfigItems.DISABLE_AUTO_COMPACTION)) {
                cfOptions.setDisableAutoCompactions(
                    cfConfig.getBoolean(CfConfigItems.DISABLE_AUTO_COMPACTION));
              }

              if (cfConfig.contains(CfConfigItems.LEVEL0_FILE_NUM_COMPACTION_TRIGGER)) {
                cfOptions.setLevel0FileNumCompactionTrigger(
                    cfConfig.getInt(CfConfigItems.LEVEL0_FILE_NUM_COMPACTION_TRIGGER));
              }

              if (cfConfig.contains(CfConfigItems.MAX_BYTES_FOR_LEVEL_BASE)) {
                cfOptions.setMaxBytesForLevelBase(
                    cfConfig.getLong(CfConfigItems.MAX_BYTES_FOR_LEVEL_BASE));
              }

              if (cfConfig.contains(CfConfigItems.MAX_BYTES_FOR_LEVEL_BASE_MULTIPLIER)) {
                cfOptions.setMaxBytesForLevelMultiplier(
                    cfConfig.getInt(CfConfigItems.MAX_BYTES_FOR_LEVEL_BASE_MULTIPLIER));
              }

              if (cfConfig.contains(CfConfigItems.TARGET_FILE_SIZE_BASE)) {
                cfOptions.setTargetFileSizeBase(
                    cfConfig.getLong(CfConfigItems.TARGET_FILE_SIZE_BASE));
              }

              if (cfConfig.contains(CfConfigItems.TARGET_FILE_SIZE_MULTIPLIER)) {
                cfOptions.setTargetFileSizeMultiplier(
                    cfConfig.getInt(CfConfigItems.TARGET_FILE_SIZE_MULTIPLIER));
              }

              // Table config
              final BlockBasedTableConfig tableConfig = new BlockBasedTableConfig();

              // Block相关
              if (cfConfig.contains(CfConfigItems.BLOCK_SIZE)) {
                tableConfig.setBlockSize(cfConfig.getLong(CfConfigItems.BLOCK_SIZE));
              }

              if (cfConfig.contains(CfConfigItems.BLOCK_CACHE)) {
                final String cacheName = cfConfig.getString(CfConfigItems.BLOCK_CACHE);
                final Cache blockCache = allBlockCaches.get(cacheName);
                if (blockCache == null) {
                  throw new RuntimeException(String.format(
                      "Invalid RocksDB config, unknown block cache %s with column family %s",
                      cacheName,
                      name
                  ));
                }
                tableConfig.setBlockCache(blockCache);
              }

              if (cfConfig.contains(CfConfigItems.CACHE_INDEX_AND_FILTER_BLOCKS)) {
                tableConfig.setCacheIndexAndFilterBlocks(cfConfig
                    .getBoolean(CfConfigItems.CACHE_INDEX_AND_FILTER_BLOCKS));
              }

              if (cfConfig.contains(CfConfigItems.PIN_L0_FILTER_AND_INDEX_BLOCKS_IN_CACHE)) {
                tableConfig.setPinL0FilterAndIndexBlocksInCache(cfConfig
                    .getBoolean(CfConfigItems.PIN_L0_FILTER_AND_INDEX_BLOCKS_IN_CACHE));
              }

              // Filter相关
              if (cfConfig.contains(CfConfigItems.FILTER_BITS)) {
                final int filterBits = cfConfig.getInt(CfConfigItems.FILTER_BITS);
                final boolean blockBased = cfConfig.getBoolean(
                    CfConfigItems.BLOCK_BASED_FILTER, CfConfigItems.DEFAULT_BLOCK_BASED_FILTER);
                tableConfig.setFilterPolicy(new BloomFilter(filterBits, blockBased));
              }

              cfOptions.setTableFormatConfig(tableConfig);

              return cfOptions;
            }
        ));
    columnFamilyConfigs = cfConfigList.stream().collect(Collectors.toMap(
        cfg -> cfg.getString(CfConfigItems.NAME), Function.identity()));

    final DBOptions dbOptions = new DBOptions();

    // 处理RateLimiter
    if (configuration.contains(ConfigItems.RATE_LIMITER)) {
      final EasyMap rateLimiterConfig = configuration.getSubArguments(ConfigItems.RATE_LIMITER);
      final long rateBytesPerSec = rateLimiterConfig
          .getLong(RateLimiterConfigItems.RATE_BYTES_PER_SEC);
      final long refillPeriodMicros = rateLimiterConfig.getLong(
          RateLimiterConfigItems.REFILL_PERIOD_MICROS,
          RateLimiterConfigItems.DEFAULT_REFILL_PERIOD_MICROS);
      final int fairness = rateLimiterConfig.getInt(
          RateLimiterConfigItems.FAIRNESS, RateLimiterConfigItems.DEFAULT_FAIRNESS);
      final RateLimiterMode rateLimiterMode = RateLimiterMode.getRateLimiterMode(
          (byte) rateLimiterConfig
              .getInt(RateLimiterConfigItems.MODE, RateLimiterConfigItems.DEFAULT_MODE));
      dbOptions.setRateLimiter(new RateLimiter(
          rateBytesPerSec,
          refillPeriodMicros,
          fairness,
          rateLimiterMode
      ));
    }

    // Parallel
    dbOptions.setMaxBackgroundFlushes(configuration.getInt(
        ConfigItems.MAX_BACKGROUND_FLUSHES, ConfigItems.DEFAULT_MAX_BACKGROUND_FLUSHES));
    dbOptions.setMaxBackgroundCompactions(configuration.getInt(
        ConfigItems.MAX_BACKGROUND_COMPACTIONS, ConfigItems.DEFAULT_MAX_BACKGROUND_COMPACTIONS));

    // IO
    if (configuration.contains(ConfigItems.BYTES_PER_SYNC)) {
      dbOptions.setBytesPerSync(configuration.getLong(ConfigItems.BYTES_PER_SYNC));
    }

    if (configuration.contains(ConfigItems.WAL_BYTES_PER_SYNC)) {
      dbOptions.setWalBytesPerSync(configuration.getLong(ConfigItems.WAL_BYTES_PER_SYNC));
    }

    if (configuration.contains(ConfigItems.ADVISE_RANDOM_ON_OPEN)) {
      dbOptions.setAdviseRandomOnOpen(configuration.getBoolean(ConfigItems.ADVISE_RANDOM_ON_OPEN));
    }

    dbOptions.setUseDirectReads(configuration.getBoolean(
        ConfigItems.USE_DIRECT_READS, ConfigItems.DEFAULT_USE_DIRECT_READS));
    dbOptions.setUseDirectIoForFlushAndCompaction(configuration.getBoolean(
        ConfigItems.USE_DIRECT_IO_FOR_FLUSH_AND_COMPACTION,
        ConfigItems.DEFAULT_USE_DIRECT_IO_FOR_FLUSH_AND_COMPACTION));
    dbOptions.setCompactionReadaheadSize(configuration.getLong(
        ConfigItems.COMPACTION_READAHEAD_SIZE,
        ConfigItems.DEFAULT_COMPACTION_READAHEAD_SIZE));

    // Memtable
    if (configuration.contains(ConfigItems.DB_WRITE_BUFFER_SIZE)) {
      dbOptions.setDbWriteBufferSize(configuration.getLong(ConfigItems.DB_WRITE_BUFFER_SIZE));
    }

    // Table Cache
    if (configuration.contains(ConfigItems.MAX_OPEN_FILES)) {
      dbOptions.setMaxOpenFiles(configuration.getInt(ConfigItems.MAX_OPEN_FILES));
    }

    if (configuration.contains(ConfigItems.TABLE_CACHE_NUMSHARDBITS)) {
      dbOptions
          .setTableCacheNumshardbits(configuration.getInt(ConfigItems.TABLE_CACHE_NUMSHARDBITS));
    }

    // WAL
    if (configuration.contains(ConfigItems.WAL_DIR)) {
      dbOptions.setWalDir(configuration.getString(ConfigItems.WAL_DIR));
    }

    if (configuration.contains(ConfigItems.WAL_TTL_SECONDS)) {
      dbOptions.setWalTtlSeconds(configuration.getLong(ConfigItems.WAL_TTL_SECONDS));
    }

    if (configuration.contains(ConfigItems.WAL_SIZE_LIMIT_MB)) {
      dbOptions.setWalSizeLimitMB(configuration.getLong(ConfigItems.WAL_SIZE_LIMIT_MB));
    }

    if (configuration.contains(ConfigItems.MAX_TOTAL_WAL_SIZE)) {
      dbOptions.setMaxTotalWalSize(configuration.getLong(ConfigItems.MAX_TOTAL_WAL_SIZE));
    }

    dbOptions.setWalRecoveryMode(WALRecoveryMode.getWALRecoveryMode(
        (byte) configuration.getInt(ConfigItems.WAL_RECOVERY_MODE,
            ConfigItems.DEFAULT_WAL_RECOVERY_MODE))
    );

    if (configuration.contains(ConfigItems.RECYCLE_LOG_FILE_NUM)) {
      dbOptions.setRecycleLogFileNum(configuration.getLong(ConfigItems.RECYCLE_LOG_FILE_NUM));
    }

    // Log相关
    if (configuration.contains(ConfigItems.DB_LOG_DIR)) {
      dbOptions.setDbLogDir(configuration.getString(ConfigItems.DB_LOG_DIR));
    }

    if (configuration.contains(ConfigItems.MAX_LOG_FILE_SIZE)) {
      dbOptions.setMaxLogFileSize(configuration.getInt(ConfigItems.MAX_LOG_FILE_SIZE));
    }

    if (configuration.contains(ConfigItems.KEEP_LOG_FILE_NUM)) {
      dbOptions.setKeepLogFileNum(configuration.getInt(ConfigItems.KEEP_LOG_FILE_NUM));
    }

    if (configuration.contains(ConfigItems.INFO_LOG_LEVEL)) {
      dbOptions.setInfoLogLevel(InfoLogLevel.getInfoLogLevel(
          (byte) configuration.getInt(ConfigItems.INFO_LOG_LEVEL)));
    }

    INSTANCE.initDB(dbPath, dbOptions, columnFamilies);

    // 备份相关
    if (configuration.contains(ConfigItems.BACKUP)) {
      createBackupEngineAndStartBackupProcess(configuration);
    }
  }

  public static RocksDB db() {
    return INSTANCE.getDBInstance();
  }

  public static RocksDBOperation useColumnFamily(String columnFamily) {
    return INSTANCE.getRocksDBOperation(columnFamily);
  }

  private static void createBackupEngineAndStartBackupProcess(EasyMap configuration) {
    final EasyMap backupConfig = configuration.getSubArguments(ConfigItems.BACKUP);

    final String backupDir = backupConfig.getString(BackupConfigItems.BACKUP_DIR);
    final int backupPeriod = backupConfig.getInt(
        BackupConfigItems.BACKUP_PERIOD, BackupConfigItems.DEFAULT_BACKUP_PERIOD);
    final boolean shareTableFiles = backupConfig.getBoolean(
        BackupConfigItems.SHARED_TABLE_FILES, BackupConfigItems.DEFAULT_SHARED_TABLE_FILES);
    final boolean shareTableFilesWithChecksum = backupConfig.getBoolean(
        BackupConfigItems.SHARE_TABLE_FILES_WITH_CHECKSUN,
        BackupConfigItems.DEFAULT_SHARE_TABLE_FILES_WITH_CHECKSUM);
    final boolean sync = backupConfig.getBoolean(
        BackupConfigItems.SYNC, BackupConfigItems.DEFAULT_SYNC);
    final boolean destroyOldData = backupConfig.getBoolean(
        BackupConfigItems.DESTROY_OLD_DATA, BackupConfigItems.DEFAULT_DESTROY_OLD_DATA);
    final int maxBackgroundOperations = backupConfig.getInt(
        BackupConfigItems.MAX_BACKGROUND_OPERATIONS,
        BackupConfigItems.DEFAULT_MAX_BACKGROUND_OPERATIONS);
    final boolean flushBeforeBackup = backupConfig.getBoolean(
        BackupConfigItems.FLUSH_BEFORE_BACKUP,
        BackupConfigItems.DEFAULT_FLUSH_BEFORE_BACKUP
    );

    final BackupableDBOptions backupOptions = new BackupableDBOptions(backupDir);
    backupOptions.setShareTableFiles(shareTableFiles);
    backupOptions.setShareFilesWithChecksum(shareTableFilesWithChecksum);
    backupOptions.setSync(sync);
    backupOptions.setDestroyOldData(destroyOldData);
    backupOptions.setMaxBackgroundOperations(maxBackgroundOperations);

    try {
      backupEngine = BackupEngine.open(Env.getDefault(), backupOptions);
      if (backupPeriod > 0) {
        backupProcess = new RocksDBBackupProcess(
            db(),
            backupEngine,
            backupPeriod,
            flushBeforeBackup,
            backupLock
        );
        backupExecutor = Executors.newSingleThreadExecutor();
        backupExecutor.execute(backupProcess);
      }
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  private static void doRestore(EasyMap configuration, String dbPath) {
    // 有恢复选项指定，首先执行恢复操作
    final String restoreRocksDBOptionsFilePath = System.getProperty(RESTORE_ROCKS, "");
    if (StringUtils.isNotEmpty(restoreRocksDBOptionsFilePath)) {
      final EasyMap restoreOptions;
      try {
        final JSONObject restoreOptionsJson = JSONObject.parseObject(FileUtils.readFileToString(
            new File(restoreRocksDBOptionsFilePath),
            Charset.forName("UTF-8")
        ));
        restoreOptions = new EasyMap(restoreOptionsJson);

        logger.info(String.format("Starting restore RocksDB from backup, the restore options: %s",
            restoreOptionsJson.toJSONString()));

        final String walDir;
        if (configuration.contains(ConfigItems.WAL_DIR)) {
          walDir = configuration.getString(ConfigItems.WAL_DIR);
        } else {
          walDir = dbPath;
        }

        final int backupId = restoreOptions.getInt(RestoreSettings.BACKUP_ID, -1);
        final String backupDir = restoreOptions.getString(RestoreSettings.BACKUP_DIR);
        final boolean keepLogFile = restoreOptions.getBoolean(RestoreSettings.KEEP_LOG_FILE);

        if (backupId == -1) {
          restoreFromBackupWithLatestBackup(backupDir, dbPath, walDir, keepLogFile);
        } else {
          restoreFromBackupWithID(backupId, backupDir, dbPath, walDir, keepLogFile);
        }

        logger.info("Restore RocksDB from backup successful!");

      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  // 基于Backup ID来恢复RocksDB
  private static void restoreFromBackupWithID(
      int backupId, String backupPath, String dbPath, String walPath, boolean keepLogFile) {
    try (BackupEngine backupEngine = BackupEngine.open(
        Env.getDefault(), new BackupableDBOptions(backupPath))) {
      backupEngine.restoreDbFromBackup(backupId, dbPath, walPath, new RestoreOptions(keepLogFile));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // 基于最近的一次备份来恢复RocksDB
  private static void restoreFromBackupWithLatestBackup(
      String backupPath, String dbPath, String walPath, boolean keepLogFile) {
    try (BackupEngine backupEngine = BackupEngine.open(
        Env.getDefault(), new BackupableDBOptions(backupPath))) {
      backupEngine.restoreDbFromLatestBackup(dbPath, walPath, new RestoreOptions(keepLogFile));
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 对RocksDB进行初始化
   */
  public synchronized void initDB(
      String dbPath, DBOptions dbOptions, Map<String, ColumnFamilyOptions> existedCfOptions) {
    if (this.inited) {
      return;
    }

    dbOptions.setCreateIfMissing(true);
    dbOptions.setErrorIfExists(false);

    try {
      // 获取已经存在的列族
      List<ColumnFamilyDescriptor> existedColumnFamilyDescriptors = RocksDB
          .listColumnFamilies(new Options(), dbPath).stream()
          .map(bytes -> new ColumnFamilyDescriptor(
              bytes, existedCfOptions.get(new String(bytes))))
          .collect(Collectors.toList());

      List<ColumnFamilyDescriptor> allColumnFamilyDescriptors = new ArrayList<>();
      if (CollectionUtils.isEmpty(existedColumnFamilyDescriptors)) {
        // 当前没有任何列族，那么初始化default列族
        allColumnFamilyDescriptors.add(
            new ColumnFamilyDescriptor("default".getBytes(), new ColumnFamilyOptions()));
      } else {
        // 添加所有已存在列族
        allColumnFamilyDescriptors.addAll(existedColumnFamilyDescriptors);
      }

      final List<ColumnFamilyHandle> cfHandleList = new ArrayList<>();

      this.rocksDBInstance = RocksDB.open(
          dbOptions,
          dbPath,
          allColumnFamilyDescriptors,
          cfHandleList
      );

      for (int i = 0; i < allColumnFamilyDescriptors.size(); i++) {
        final ColumnFamilyHandle handle = cfHandleList.get(i);
        final ColumnFamilyDescriptor descriptor = allColumnFamilyDescriptors.get(i);
        columnFamilyHandles.put(new String(descriptor.getName()), handle);
      }

      // 只有default列族，需要按照配置来创建列族
      if (columnFamilyHandles.size() == 1) {
        for (Map.Entry<String, ColumnFamilyOptions> entry : existedCfOptions.entrySet()) {
          final String cfName = entry.getKey();
          if (cfName.equals("default")) {
            continue;  // 无需创建default列族
          }
          final ColumnFamilyOptions cfOptions = entry.getValue();
          final ColumnFamilyHandle cfHandle = rocksDBInstance.createColumnFamily(
              new ColumnFamilyDescriptor(cfName.getBytes(), cfOptions));
          columnFamilyHandles.put(cfName, cfHandle);
        }
      }

      this.inited = true;

    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean isInited() {
    return this.inited;
  }

  /**
   * 获取RocksDB实例
   *
   * @return RocksDB instance
   */
  public RocksDB getDBInstance() {
    if (!inited) {
      throw new IllegalStateException("The RocksDBHelper has not been initialized!");
    }
    return this.rocksDBInstance;
  }

  /**
   * 使用指定的列族
   *
   * @param columnFamilyName 列族名称
   * @return RocksDBOperation instance
   */
  public RocksDBOperation getRocksDBOperation(String columnFamilyName) {
    if (!inited) {
      throw new IllegalStateException("The RocksDBHelper has not been initialized!");
    }
    if (!columnFamilyHandles.containsKey(columnFamilyName)) {
      throw new RuntimeException(
          String.format("Could not found column family: %s", columnFamilyName));
    }

    return new RocksDBOperation(
        rocksDBInstance,
        columnFamilyHandles.get(columnFamilyName),
        columnFamilyConfigs.get(columnFamilyName)
    );
  }

  /**
   * Create checkpoint of RocksDB instance
   *
   * @param checkpointDir checkpoint dir
   * @return Operation result
   */
  public Result createCheckpoint(String checkpointDir) {
    if (rocksDBInstance == null) {
      logger.error("There is no RocksDB instance");
      return Result.failWithCodeAndMessage(
          "sys_error", "There is no RocksDB instance");
    } else {
      if (!operationLock.tryLock()) {
        logger.error("There is an another operation on RocksDB instance!");
        return Result.failWithCodeAndMessage(
            "sys_error",
            "There is an another operation on RocksDB instance!"
        );
      }
      try {
        final Checkpoint checkpoint = Checkpoint.create(rocksDBInstance);
        checkpoint.createCheckpoint(checkpointDir);
        return Result.ok();
      } catch (RocksDBException e) {
        logger.error("Create RocksDB checkpoint error!", e);
        return Result.failWithCodeAndMessage("sys_error", e.getMessage());
      } finally {
        operationLock.unlock();
      }
    }
  }

  public List<BackupInfoModel> getBackupInfo() {
    if (backupEngine == null) {
      return Collections.emptyList();
    }

    final List<BackupInfo> backupInfoList = backupEngine.getBackupInfo();
    if (CollectionUtils.isEmpty(backupInfoList)) {
      return Collections.emptyList();
    }

    return backupInfoList.stream().map(BackupInfoModel::new).collect(Collectors.toList());
  }

  public Result createNewBackup(boolean flushBeforeBackup) {
    if (backupEngine == null) {
      return Result.failWithCodeAndMessage(
          "sys_error",
          "There is no available RocksDB backup engine!"
      );
    }

    if (!backupLock.tryLock()) {
      return Result.failWithCodeAndMessage(
          "sys_error",
          "Could not backup the RocksDB instance, there is an another backup operation."
      );
    }

    try {
      backupEngine.createNewBackup(db(), flushBeforeBackup);
      return Result.ok();
    } catch (Exception e) {
      logger.error("Backup RocksDB error", e);
      return Result.failWithCodeAndMessage("sys_error", e.getMessage());
    } finally {
      backupLock.unlock();
    }
  }

  public Result purgeOldBackups(int num) {
    if (backupEngine == null) {
      return Result.failWithCodeAndMessage(
          "sys_error",
          "There is no available RocksDB backup engine!"
      );
    }

    if (!backupLock.tryLock()) {
      return Result.failWithCodeAndMessage(
          "sys_error",
          "Could not purge old backups, there is an another backup operation."
      );
    }

    try {
      backupEngine.purgeOldBackups(num);
      return Result.ok();
    } catch (Exception e) {
      logger.error("Purge old backups error", e);
      return Result.failWithCodeAndMessage("sys_error", e.getMessage());
    } finally {
      backupLock.unlock();
    }
  }

  @Override
  public void close() {
    if (backupExecutor != null && backupProcess != null) {
      backupProcess.stop();
      backupExecutor.shutdown();
    }
    if (rocksDBInstance != null) {
      rocksDBInstance.close();
    }
  }

  // 备份恢复相关设置项
  public interface RestoreSettings {

    String BACKUP_ID = "backup_id";

    String BACKUP_DIR = "backup_dir";

    String KEEP_LOG_FILE = "keep_log_file";
  }

  // RocksDB配置项
  public interface ConfigItems {

    String PATH = "path";

    String BLOCK_CACHES = "block_caches";

    String COLUMN_FAMILIES = "column_families";

    // 并发相关
    String MAX_BACKGROUND_FLUSHES = "max_background_flushes";

    int DEFAULT_MAX_BACKGROUND_FLUSHES = 1;

    String MAX_BACKGROUND_COMPACTIONS = "max_background_compactions";

    int DEFAULT_MAX_BACKGROUND_COMPACTIONS = 4;

    // IO
    String BYTES_PER_SYNC = "bytes_per_sync";

    String WAL_BYTES_PER_SYNC = "wal_bytes_per_sync";

    String ADVISE_RANDOM_ON_OPEN = "advise_random_on_open";

    String USE_DIRECT_READS = "use_direct_reads";

    boolean DEFAULT_USE_DIRECT_READS = false;

    String USE_DIRECT_IO_FOR_FLUSH_AND_COMPACTION = "use_direct_io_for_flush_and_compaction";

    boolean DEFAULT_USE_DIRECT_IO_FOR_FLUSH_AND_COMPACTION = false;

    String COMPACTION_READAHEAD_SIZE = "compaction_readahead_size";

    long DEFAULT_COMPACTION_READAHEAD_SIZE = 2 * 1024 * 1024; // 2MB

    // RateLimiter相关
    String RATE_LIMITER = "rate_limiter";

    // 对Memtable使用内存的总数进行限制
    String DB_WRITE_BUFFER_SIZE = "db_write_buffer_size";

    // Table cache size
    String MAX_OPEN_FILES = "max_open_files";

    // Table cache num shard bits
    String TABLE_CACHE_NUMSHARDBITS = "table_cache_numshardbits";

    // WAL
    String WAL_DIR = "wal_dir";

    String WAL_TTL_SECONDS = "wal_ttl_seconds";

    String WAL_SIZE_LIMIT_MB = "wal_size_limit_mb";

    String MAX_TOTAL_WAL_SIZE = "max_total_wal_size";

    String WAL_RECOVERY_MODE = "wal_recovery_mode";

    byte DEFAULT_WAL_RECOVERY_MODE = 0;

    String RECYCLE_LOG_FILE_NUM = "recycle_log_file_num";

    String DB_LOG_DIR = "db_log_dir";

    String MAX_LOG_FILE_SIZE = "max_log_file_size";

    String KEEP_LOG_FILE_NUM = "keep_log_file_num";

    String INFO_LOG_LEVEL = "info_log_level";

    String BACKUP = "backup";

    interface BackupConfigItems {

      String BACKUP_DIR = "backup_dir";

      String BACKUP_PERIOD = "backup_period";

      int DEFAULT_BACKUP_PERIOD = -1;

      String SHARED_TABLE_FILES = "shared_table_files";

      boolean DEFAULT_SHARED_TABLE_FILES = true;

      String SHARE_TABLE_FILES_WITH_CHECKSUN = "shared_table_files_with_checksum";

      boolean DEFAULT_SHARE_TABLE_FILES_WITH_CHECKSUM = true;

      String SYNC = "sync";

      boolean DEFAULT_SYNC = false;

      String DESTROY_OLD_DATA = "destroy_old_data";

      boolean DEFAULT_DESTROY_OLD_DATA = false;

      String MAX_BACKGROUND_OPERATIONS = "max_background_operations";

      int DEFAULT_MAX_BACKGROUND_OPERATIONS = 1;

      String FLUSH_BEFORE_BACKUP = "flush_before_backup";

      boolean DEFAULT_FLUSH_BEFORE_BACKUP = false;
    }

    interface RateLimiterConfigItems {

      String RATE_BYTES_PER_SEC = "rate_bytes_per_sec";

      String REFILL_PERIOD_MICROS = "refill_period_micros";

      long DEFAULT_REFILL_PERIOD_MICROS = 100000L;

      String FAIRNESS = "fairness";

      int DEFAULT_FAIRNESS = 10;

      String MODE = "mode";

      byte DEFAULT_MODE = 1;
    }
  }

  // BlockCache配置项
  public interface BlockCacheConfigItems {

    String NAME = "name";

    String TYPE = "type";

    String CAPACITY = "capacity";

    String NUM_SHARD_BITS = "num_shard_bits";

    int DEFAULT_NUM_SHARD_BITS = 8;

    String STRICT_CAPACITY_LIMIT = "strict_capacity_limit";

    boolean DEFAULT_STRICT_CAPACITY_LIMIT = false;

    String HIGH_PRI_POOL_RATIO = "high_pri_pool_ratio";

    double DEFAULT_HIGH_PRI_POOL_RATIO = 0.5;
  }

  // Column family配置项
  public interface CfConfigItems {

    String NAME = "name";

    // Compression相关
    String COMPRESSION_TYPE = "compression_type";

    String DEFAULT_COMPRESSION_TYPE = "lz4";

    String COMPRESSION_PER_LEVEL = "compression_per_level";

    // 执行迭代时的readahead尺寸
    String ITERATOR_READAHEAD_SIZE = "iterator_readahead_size";

    // Block size
    String BLOCK_SIZE = "block_size";

    // Block Cache
    String BLOCK_CACHE = "block_cache";

    // Cache index and filter in the block cache
    String CACHE_INDEX_AND_FILTER_BLOCKS = "cache_index_and_filter_blocks";

    String PIN_L0_FILTER_AND_INDEX_BLOCKS_IN_CACHE = "pin_l0_filter_and_index_blocks_in_cache";

    // 单个Memtable的大小
    String WRITE_BUFFER_SIZE = "write_buffer_size";

    // 内存中保持memtable的最大数目
    String MAX_WRITE_BUFFER_NUMBER = "max_write_buffer_number";

    // Memtable在flush之前的最小合并数
    String MIN_WRITE_BUFFER_NUMBER_TO_MERGE = "min_write_buffer_number_to_merge";

    // Filter bit number
    String FILTER_BITS = "filter_bits";

    String BLOCK_BASED_FILTER = "block_based_filter";

    boolean DEFAULT_BLOCK_BASED_FILTER = true;

    // Compaction相关
    String DISABLE_AUTO_COMPACTION = "disable_auto_compaction";

    String LEVEL0_FILE_NUM_COMPACTION_TRIGGER = "level0_file_num_compaction_trigger";

    String MAX_BYTES_FOR_LEVEL_BASE = "max_bytes_for_level_base";

    String MAX_BYTES_FOR_LEVEL_BASE_MULTIPLIER = "max_bytes_for_level_base_multiplier";

    String TARGET_FILE_SIZE_BASE = "target_file_size_base";

    String TARGET_FILE_SIZE_MULTIPLIER = "target_file_size_multiplier";
  }

  // Block cache type
  public interface BlockCacheTypes {

    String LRU = "LRU";

    String CLOCK = "CLOCK";
  }
}
