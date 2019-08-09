package playwell.route.migration;


import com.alibaba.fastjson.JSONObject;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import playwell.storage.jdbc.JDBCHelper;

/**
 * MigrationPlan数据访问层
 */
class MigrationPlanDataAccess {

  private static final String QUERY_FIELDS = "`message_bus`, `input_message_bus_config`, "
      + "`output_message_bus_config`, `slots_distribution`, `comment`, `created_on`";

  private static final String ALL_FIELDS = "`pk`, " + QUERY_FIELDS;

  private static final int PK = 0;

  private final String dataSource;

  MigrationPlanDataAccess(String dataSource) {
    this.dataSource = dataSource;
  }

  int insert(MigrationPlan migrationPlan) {
    final String sql = String.format(
        "INSERT IGNORE INTO `migration_plan` (%s) VALUES (?, ?, ?, ?, ?, ?, ?)", ALL_FIELDS);
    return (int) JDBCHelper.execute(
        dataSource,
        sql,
        PK,
        migrationPlan.getMessageBus(),
        JSONObject.toJSONString(migrationPlan.getInputMessageBusConfig()),
        JSONObject.toJSONString(migrationPlan.getOutputMessageBusConfig()),
        JSONObject.toJSONString(migrationPlan.getSlotsDistribution()),
        migrationPlan.getComment(),
        new Date()
    );
  }

  Optional<MigrationPlan> get() {
    final String sql = String.format(
        "SELECT %s FROM `migration_plan` WHERE `pk` = ?",
        QUERY_FIELDS
    );
    return JDBCHelper.queryOne(
        dataSource,
        sql,
        resultSet -> new MigrationPlan(
            resultSet.getString("message_bus"),
            JSONObject.parseObject(resultSet.getString("input_message_bus_config")).getInnerMap(),
            JSONObject.parseObject(resultSet.getString("output_message_bus_config")).getInnerMap(),
            JSONObject.parseObject(resultSet.getString("slots_distribution"))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> (Integer) entry.getValue())),
            resultSet.getString("comment"),
            resultSet.getDate("created_on")
        ),
        PK
    );
  }

  int clean() {
    final String sql = "TRUNCATE `migration_plan`";
    return (int) JDBCHelper.execute(
        dataSource,
        sql
    );
  }
}
