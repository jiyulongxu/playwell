package playwell.service;

import java.util.Collection;
import java.util.Optional;
import playwell.common.PlaywellComponent;
import playwell.common.Result;

/**
 * 服务管理器，包含所有的服务元信息
 *
 * @author chihongze@gmail.com
 */
public interface ServiceMetaManager extends PlaywellComponent {

  /**
   * 向ServiceMetaManager注册新的ServiceMeta
   *
   * @param serviceMeta ServiceMeta Object
   * @return 操作结果
   */
  Result registerServiceMeta(ServiceMeta serviceMeta);

  /**
   * 移除注册的ServiceMeta，服务在Playwell中将不再可用
   *
   * @param name 服务名称
   * @return 移除结果
   */
  Result removeServiceMeta(String name);

  /**
   * 获取所有的ServiceMeta信息
   */
  Collection<ServiceMeta> getAllServiceMeta();

  /**
   * 根据服务名称获取ServiceMeta
   *
   * @param name 服务名称
   * @return ServiceMeta Optional
   */
  Optional<ServiceMeta> getServiceMetaByName(String name);

  interface ErrorCodes {

    String ALREADY_EXISTED = "already_existed";

    String NOT_FOUND = "not_found";
  }

  interface ResultFields {

    String SERVICE_META = "service_meta";
  }
}
