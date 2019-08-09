package playwell.route.migration;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import playwell.storage.jdbc.DBField;
import playwell.storage.jdbc.JDBCHelper;
import playwell.storage.jdbc.RowMapper;

class MigrationProgressDataAccess {

  // Row Mapper
  private static final RowMapper<MigrationProgress> rowMapper = resultSet -> {
    final List<Integer> slots = Arrays
        .stream(StringUtils.split(
            resultSet.getString(DBFields.SLOTS.getName()), ','))
        .map(Integer::parseInt)
        .collect(Collectors.toList());
    return new MigrationProgress(
        MigrationProgressStatus.valueOfByStatus(
            resultSet.getInt(DBFields.STATUS.getName())).orElseThrow(
            () -> new RuntimeException("Invalid migration progress status")),
        slots,
        resultSet.getString(DBFields.OUTPUT_NODE.getName()),
        resultSet.getString(DBFields.INPUT_NODE.getName()),
        resultSet.getString(DBFields.OUTPUT_LATEST_KEY.getName()),
        resultSet.getString(DBFields.INPUT_LATEST_KEY.getName()),
        resultSet.getBoolean(DBFields.OUTPUT_FINISHED.getName()),
        resultSet.getBoolean(DBFields.INPUT_FINISHED.getName()),
        resultSet.getDate(DBFields.BEGIN_TIME.getName()),
        resultSet.getDate(DBFields.END_TIME.getName())
    );
  };

  private final String dataSource;

  MigrationProgressDataAccess(String dataSource) {
    this.dataSource = dataSource;
  }

  int insert(MigrationProgress migrationProgress) {
    final String sql = String.format(
        "INSERT INTO `migration_progress` (%s) VALUES (%s)",
        DBField.joinAllFields(DBFields.values()),
        DBField.joinPlaceHolders(DBFields.values().length)
    );
    return (int) JDBCHelper.execute(
        dataSource,
        sql,
        migrationProgress.getStatus().getStatus(),
        StringUtils.join(migrationProgress.getSlots(), ","),
        migrationProgress.getOutputNode(),
        migrationProgress.getInputNode(),
        migrationProgress.getOutputLatestKey(),
        migrationProgress.getInputLatestKey(),
        migrationProgress.isOutputFinished(),
        migrationProgress.isInputFinished(),
        migrationProgress.getBeginTime(),
        migrationProgress.getEndTime()
    );
  }

  void insert(Collection<MigrationProgress> migrationProgressCollection) {
    final String sql = String.format(
        "INSERT INTO `migration_progress` (%s) VALUES (%s)",
        DBField.joinAllFields(DBFields.values()),
        DBField.joinPlaceHolders(DBFields.values().length)
    );

    final Object[][] params = new Object[migrationProgressCollection.size()][];
    int index = 0;
    for (MigrationProgress migrationProgress : migrationProgressCollection) {
      params[index++] = new Object[]{
          migrationProgress.getStatus().getStatus(),
          StringUtils.join(migrationProgress.getSlots(), ","),
          migrationProgress.getOutputNode(),
          migrationProgress.getInputNode(),
          migrationProgress.getOutputLatestKey(),
          migrationProgress.getInputLatestKey(),
          migrationProgress.isOutputFinished(),
          migrationProgress.isInputFinished(),
          migrationProgress.getBeginTime(),
          migrationProgress.getEndTime()
      };
    }

    JDBCHelper.batchExecute(
        dataSource,
        sql,
        params
    );
  }

  Collection<MigrationProgress> getAllProgress() {
    final String sql = String.format(
        "SELECT %s FROM `migration_progress`",
        DBField.joinAllFields(DBFields.values())
    );
    return JDBCHelper.queryList(
        dataSource,
        sql,
        rowMapper
    );
  }

  void updateStatus(String outputNode, String inputNode, MigrationProgressStatus status) {
    final String sql = "UPDATE `migration_progress` SET `status` = ?, `begin_time` = ?"
        + "WHERE `output_node` = ? AND `input_node` = ?";
    JDBCHelper.execute(
        dataSource,
        sql,
        status.getStatus(),
        new Date(),
        outputNode,
        inputNode
    );
  }

  Optional<MigrationProgress> getProgressByOutputServiceName(String serviceName) {
    final String sql = String.format(
        "SELECT %s FROM `migration_progress` WHERE "
            + "`output_node` = ? AND `status` = ? AND `output_finished` = ?",
        DBField.joinAllFields(DBFields.values())
    );
    return JDBCHelper.queryOne(
        dataSource,
        sql,
        rowMapper,
        serviceName,
        MigrationProgressStatus.MIGRATING.getStatus(),
        false
    );
  }

  Optional<MigrationProgress> getProgressByInputServiceName(String serviceName) {
    final String sql = String.format(
        "SELECT %s FROM `migration_progress` WHERE "
            + "`input_node` = ? AND `status` = ? AND `input_finished` = ?",
        DBField.joinAllFields(DBFields.values())
    );
    return JDBCHelper.queryOne(
        dataSource,
        sql,
        rowMapper,
        serviceName,
        MigrationProgressStatus.MIGRATING.getStatus(),
        false
    );
  }

  long updateOutputFinished(String outputServiceName) {
    return JDBCHelper.execute(
        dataSource,
        "UPDATE `migration_progress` SET `output_finished` = true "
            + "WHERE `output_node` = ? AND `status` = ?",
        outputServiceName,
        MigrationProgressStatus.MIGRATING.getStatus()
    );
  }

  long updateInputFinished(String inputServiceName) {
    return JDBCHelper.execute(
        dataSource,
        "UPDATE `migration_progress` SET `input_finished` = true "
            + "WHERE `input_node` = ? AND `status` = ?",
        inputServiceName,
        MigrationProgressStatus.MIGRATING.getStatus()
    );
  }

  void clean() {
    JDBCHelper.execute(
        dataSource,
        "TRUNCATE `migration_progress`"
    );
  }

  // All db fields
  enum DBFields implements DBField {

    STATUS("status"),

    SLOTS("slots"),

    OUTPUT_NODE("output_node"),

    INPUT_NODE("input_node"),

    OUTPUT_LATEST_KEY("output_latest_key"),

    INPUT_LATEST_KEY("input_latest_key"),

    OUTPUT_FINISHED("output_finished"),

    INPUT_FINISHED("input_finished"),

    BEGIN_TIME("begin_time"),

    END_TIME("end_time"),
    ;

    private final String name;

    DBFields(String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return this.name;
    }

    @Override
    public boolean isPrimaryKey() {
      return false;
    }
  }
}
