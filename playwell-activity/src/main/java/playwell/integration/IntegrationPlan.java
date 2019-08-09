package playwell.integration;


import java.util.Map;
import playwell.common.PlaywellComponent;

/**
 * 组件集成方案
 *
 * @author chihongze@gmail.com
 */
public interface IntegrationPlan {

  /**
   * 返回是否集成完毕
   */
  boolean isReady();

  /**
   * 执行集成操作
   *
   * @param config 配置信息
   */
  void intergrate(Map<String, Object> config);

  /**
   * 是否包含指定类型的组件
   *
   * @param topComponentType 组件类型
   * @return 是否包含
   */
  boolean contains(TopComponentType topComponentType);

  /**
   * 根据类型获取Top Component
   *
   * @param topComponentType Component类型
   * @return Component Object
   */
  PlaywellComponent getTopComponent(TopComponentType topComponentType);

  /**
   * 关闭所有组件 & 释放所有资源
   */
  void closeAll();
}
