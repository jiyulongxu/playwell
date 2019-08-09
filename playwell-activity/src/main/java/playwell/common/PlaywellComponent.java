package playwell.common;

/**
 * 该接口用于修饰Playwell标准集成组件，为组件约定一些统一的行为以便于集成
 *
 * @author chihongze
 */
public interface PlaywellComponent {

  /**
   * 执行初始化操作
   *
   * @param config 配置信息
   */
  void init(Object config);
}
