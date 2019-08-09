package playwell.storage.rocksdb;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.BackupEngine;
import org.rocksdb.RocksDB;
import playwell.util.Sleeper;

/**
 * RocksDBBackupProcess
 *
 * 周期性对RocksDB进行备份并删除多余的旧备份
 */
class RocksDBBackupProcess implements Runnable {

  private static final Logger logger = LogManager.getLogger(RocksDBBackupProcess.class);

  private final RocksDB rocksDBInstance;

  private final BackupEngine backupEngine;

  private final int backupPeriod;

  private final boolean flushBeforeBackup;

  private final Lock backupLock;

  private volatile boolean stopMark = false;

  private volatile boolean stopped = false;

  private long lastBackupTime = System.currentTimeMillis();

  RocksDBBackupProcess(
      RocksDB rocksDBInstance,
      BackupEngine backupEngine,
      int backupPeriod,
      boolean flushBeforeBackup,
      Lock backupLock) {
    this.rocksDBInstance = rocksDBInstance;
    this.backupEngine = backupEngine;
    this.backupPeriod = backupPeriod;
    this.flushBeforeBackup = flushBeforeBackup;
    this.backupLock = backupLock;
  }

  @Override
  public void run() {
    while (true) {

      if (stopMark) {
        backupEngine.close();
        this.stopped = true;
        logger.info("The RocksDBBackupProcess stopped! The BackupEngine closed!");
        return;
      }

      if (System.currentTimeMillis() - lastBackupTime >= TimeUnit.SECONDS.toMillis(backupPeriod)) {
        if (!backupLock.tryLock()) {
          logger.info("Could not backup RocksDB, There is an another operation using BackupEngine");
          continue;
        }

        try {
          // Backup RocksDB
          long beginTime = System.currentTimeMillis();
          logger.info("Begin to backup RocksDB...");
          backupEngine.createNewBackup(rocksDBInstance, flushBeforeBackup);
          logger.info(String.format(
              "Backup RocksDB success! Used time: %d", System.currentTimeMillis() - beginTime));

          this.lastBackupTime = System.currentTimeMillis();
        } catch (Exception e) {
          logger.error("Backup RocksDB error!", e);
        } finally {
          backupLock.unlock();
        }
      }

      Sleeper.sleepInSeconds(3);
    }
  }

  public void stop() {
    this.stopMark = true;
  }

  public boolean isStopped() {
    return this.stopped;
  }
}
