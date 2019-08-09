package playwell.storage.rocksdb;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableMap;
import java.util.Date;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.rocksdb.BackupInfo;
import playwell.common.Mappable;
import playwell.util.DateUtils;

/**
 * Delegate for RocksDB BackupInfo
 */
public class BackupInfoModel implements Mappable {

  private final BackupInfo backupInfo;

  public BackupInfoModel(BackupInfo backupInfo) {
    this.backupInfo = backupInfo;
  }

  public int getBackupId() {
    return backupInfo.backupId();
  }

  public Date getTime() {
    return new Date(backupInfo.timestamp() * 1000);
  }

  public long getSize() {
    return backupInfo.size();
  }

  public int getNumberFiles() {
    return backupInfo.numberFiles();
  }

  public String getMetaData() {
    final String metaData = backupInfo.appMetadata();
    return StringUtils.isEmpty(metaData) ? "" : metaData;
  }

  @Override
  public Map<String, Object> toMap() {
    return ImmutableMap.of(
        Attributes.BACKUP_ID, getBackupId(),
        Attributes.TIME, DateUtils.format(getTime()),
        Attributes.SIZE, getSize(),
        Attributes.NUMBER_FILES, getNumberFiles(),
        Attributes.META_DATA, getMetaData()
    );
  }

  @Override
  public String toString() {
    return new JSONObject(toMap()).toJSONString();
  }

  public interface Attributes {

    String BACKUP_ID = "backup_id";

    String TIME = "time";

    String SIZE = "size";

    String NUMBER_FILES = "number_files";

    String META_DATA = "meta_data";
  }
}
