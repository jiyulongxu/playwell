package playwell.baas.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import playwell.common.PlaywellComponent;
import playwell.common.expression.PlaywellExpression;

/**
 * Domain信息数据访问模块抽象
 */
public interface DomainInfoDataAccess extends PlaywellComponent {

  /**
   * 查询单个DomainInfo信息
   *
   * @param type DomainInfo类型
   * @param domainId Domain ID
   * @param properties 属性
   * @return DomainInfo Optional
   */
  Optional<DomainInfo> query(String type, String domainId, List<String> properties);

  /**
   * Upsert DomainInfo属性
   *
   * @param type DomainInfo类型
   * @param domainId DomainInfo ID
   * @param properties 属性
   */
  void upsert(String type, String domainId, Map<String, Object> properties);

  /**
   * 批量查询Domain信息
   *
   * @param queryCommands 查询命令
   * @return 查询结果集
   */
  Collection<DomainInfo> executeQueryCommands(Collection<DomainInfoQueryCommand> queryCommands);

  /**
   * 批量更新Domain信息
   *
   * @param upsertCommands 更新命令
   */
  void executeUpsertCommands(Collection<DomainInfoUpsertCommand> upsertCommands);

  /**
   * 对Domain记录进行扫描
   *
   * @param cursor Cursor
   * @param type Domain type
   * @param properties Domain properties
   * @param count 扫描数目
   * @param scanConsumer 扫描记录回调
   * @return 返回用于下次迭代的cursor，如果没有了可迭代对象，则返回Optional.empty()
   */
  Optional<String> scan(
      String cursor,
      String type,
      List<String> properties,
      List<PlaywellExpression> conditions,
      int count,
      Consumer<ScanDomainInfoContext> scanConsumer
  );

  /**
   * 批量移除
   *
   * @param domainInfoCollection Domain info collection
   */
  void batchRemove(Collection<DomainInfo> domainInfoCollection);
}
