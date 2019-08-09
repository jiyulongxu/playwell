package playwell.storage.rocksdb;

import org.rocksdb.ColumnFamilyOptions;
import playwell.common.EasyMap;

/**
 * 依据配置，构建ColumnFamilyOptions
 *
 * @author chihongze@gmail.com
 */
public class ColumnFamilyOptionsBuilder {

  private ColumnFamilyOptionsBuilder() {

  }

  public static ColumnFamilyOptions build(EasyMap config) {
    return new ColumnFamilyOptions();
  }
}
