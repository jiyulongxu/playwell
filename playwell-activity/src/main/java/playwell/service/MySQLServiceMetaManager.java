package playwell.service;

import com.alibaba.fastjson.JSONObject;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.action.ActionManager;
import playwell.common.CompareAndCallback;
import playwell.common.EasyMap;
import playwell.common.MySQLCompareAndCallback;
import playwell.common.Result;
import playwell.integration.IntegrationPlan;
import playwell.integration.IntegrationPlanFactory;
import playwell.integration.IntergrationUtils;
import playwell.integration.TopComponentType;
import playwell.message.MessageDispatcherListener;
import playwell.storage.jdbc.DBField;
import playwell.storage.jdbc.JDBCHelper;
import playwell.storage.jdbc.RowMapper;

/**
 * 基于MySQL存储的ServiceMetaManager
 *
 * @author chihongze@gmail.com
 */
public class MySQLServiceMetaManager implements ServiceMetaManager, MessageDispatcherListener {

  private static final Logger logger = LogManager.getLogger(MySQLServiceMetaManager.class);

  private final Map<String, ServiceMeta> allServiceMeta = new ConcurrentHashMap<>();

  private DataAccess dataAccess;

  private CompareAndCallback updator;

  private int expectedVersion;

  public MySQLServiceMetaManager() {

  }

  @Override
  public void init(Object config) {
    EasyMap configuration = (EasyMap) config;
    final String dataSource = configuration.getString(ConfigItems.DATASOURCE);
    this.dataAccess = new DataAccess(dataSource);
    this.updator = new MySQLCompareAndCallback(dataSource, "service_meta");

    // 加载Local service
    final List<EasyMap> localServiceConfigList = configuration.getSubArgumentsList(
        ConfigItems.LOCAL_SERVICE);
    if (CollectionUtils.isNotEmpty(localServiceConfigList)) {
      localServiceConfigList.forEach(serviceConfig -> {
        final String name = serviceConfig.getString(ConfigItems.SERVICE_NAME);
        final String messageBus = serviceConfig.getString(ConfigItems.MESSAGE_BUS);
        final PlaywellService playwellService = (PlaywellService) IntergrationUtils
            .buildAndInitComponent(serviceConfig);
        final ServiceMeta serviceMeta = new LocalServiceMeta(
            name,
            messageBus,
            serviceConfig.toMap(),
            playwellService
        );
        allServiceMeta.put(serviceMeta.getName(), serviceMeta);
        dataAccess.insert(serviceMeta);
      });
      this.updator.updateVersion();
    }

    beforeLoop();
  }

  @Override
  public Result registerServiceMeta(ServiceMeta serviceMeta) {
    dataAccess.insert(serviceMeta);
    updator.updateVersion();
    return Result.okWithData(Collections.singletonMap(
        ResultFields.SERVICE_META, serviceMeta));
  }

  @Override
  public Result removeServiceMeta(String name) {
    final Optional<ServiceMeta> serviceMetaOptional = dataAccess.getByName(name);
    if (!serviceMetaOptional.isPresent()) {
      return Result.failWithCodeAndMessage(
          ErrorCodes.NOT_FOUND,
          String.format("The service '%s' not found.", name)
      );
    }
    final ServiceMeta serviceMeta = serviceMetaOptional.get();
    dataAccess.remove(name);
    updator.updateVersion();
    return Result.okWithData(Collections.singletonMap(
        ResultFields.SERVICE_META, serviceMeta));
  }

  @Override
  public Collection<ServiceMeta> getAllServiceMeta() {
    return allServiceMeta.values();
  }

  @Override
  public Optional<ServiceMeta> getServiceMetaByName(String name) {
    return Optional.ofNullable(allServiceMeta.get(name));
  }

  @Override
  public void beforeLoop() {
    expectedVersion = updator.compareAndCallback(expectedVersion, this::refresh);
  }

  public void removeAll() {
    dataAccess.truncate();
  }

  private void refresh() {
    logger.info("Refreshing MySQLServiceMetaManager...");

    final Collection<ServiceMeta> serviceMetas = dataAccess.getAll();
    if (CollectionUtils.isEmpty(serviceMetas)) {
      allServiceMeta.clear();
      return;
    }

    final Set<String> newServiceNames = new HashSet<>();

    serviceMetas.forEach(serviceMeta -> {
      // LocalServiceMeta的信息只能在重启服务的时候修改
      newServiceNames.add(serviceMeta.getName());
      final ServiceMeta existedServiceMeta = allServiceMeta.get(serviceMeta.getName());
      if (!(existedServiceMeta instanceof LocalServiceMeta)) {
        allServiceMeta.put(serviceMeta.getName(), serviceMeta);
      }
    });

    allServiceMeta.entrySet().removeIf(
        entry -> !newServiceNames.contains(entry.getKey()));

    final IntegrationPlan integrationPlan = IntegrationPlanFactory.currentPlan();
    if (integrationPlan.contains(TopComponentType.ACTION_MANAGER)) {
      final ActionManager actionManager = (ActionManager) integrationPlan.getTopComponent(
          TopComponentType.ACTION_MANAGER);
      getAllServiceMeta()
          .forEach(serviceMeta -> actionManager.registerServiceAction(serviceMeta.getName()));
    }

    logger.info("MySQLServiceMetaManager refreshed, now services: "
        + allServiceMeta.values().stream()
        .map(ServiceMeta::getName).collect(Collectors.toList()));
  }

  public interface ConfigItems {

    String DATASOURCE = "datasource";

    String LOCAL_SERVICE = "local_services";

    String SERVICE_NAME = "name";

    String MESSAGE_BUS = "message_bus";
  }

  public static class DataAccess {

    private static final RowMapper<ServiceMeta> rowMapper = rs -> new ServiceMeta(
        rs.getString(DBFields.NAME.name),
        rs.getString(DBFields.MESSAGE_BUS.name),
        JSONObject.parseObject(rs.getString(DBFields.CONFIG.name)).getInnerMap()
    );

    private final String dataSource;

    DataAccess(String dataSource) {
      this.dataSource = dataSource;
    }

    public void insert(ServiceMeta serviceMeta) {
      final String SQL = "INSERT INTO `service_meta` (" +
          DBField.joinAllFields(DBFields.values()) + ") VALUES (?, ?, ?) " +
          "ON DUPLICATE KEY UPDATE `message_bus` = ?, `config` = ?";
      final String config = MapUtils.isEmpty(serviceMeta.getConfig()) ? "{}" :
          new JSONObject(serviceMeta.getConfig()).toJSONString();
      JDBCHelper.execute(
          dataSource,
          SQL,
          serviceMeta.getName(),
          serviceMeta.getMessageBus(),
          config,
          serviceMeta.getMessageBus(),
          config
      );
    }

    public long remove(String name) {
      final String SQL = "DELETE FROM `service_meta` WHERE `name` = ?";
      return JDBCHelper.execute(
          dataSource,
          SQL,
          name
      );
    }

    public Collection<ServiceMeta> getAll() {
      final String SQL =
          "SELECT " + DBField.joinAllFields(DBFields.values()) + " FROM `service_meta`";
      return JDBCHelper.queryList(
          dataSource,
          SQL,
          rowMapper
      );
    }

    public Optional<ServiceMeta> getByName(String name) {
      final String SQL = "SELECT " + DBField.joinAllFields(DBFields.values())
          + " FROM `service_meta` WHERE `name` = ?";
      return JDBCHelper.queryOne(
          dataSource,
          SQL,
          rowMapper,
          name
      );
    }

    public void truncate() {
      final String SQL = "TRUNCATE `service_meta`";
      JDBCHelper.execute(
          dataSource,
          SQL
      );
    }

    enum DBFields implements DBField {

      NAME("name", true),

      MESSAGE_BUS("message_bus", false),

      CONFIG("config", false),
      ;

      private final String name;

      private final boolean primaryKey;

      DBFields(String name, boolean primaryKey) {
        this.name = name;
        this.primaryKey = primaryKey;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public boolean isPrimaryKey() {
        return primaryKey;
      }
    }
  }
}
