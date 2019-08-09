package playwell.service;


import java.util.Collection;
import playwell.common.PlaywellComponent;
import playwell.message.ServiceRequestMessage;
import playwell.message.ServiceResponseMessage;

/**
 * PlaywellService用来描述一个Playwell服务，接受消息，返回结果 服务不一定非要实现PlaywellService，甚至可以跨语言，
 * 但基于该接口构建服务可以方便的使用很多脚手架
 *
 * @author chihongze@gmail.com
 */
public interface PlaywellService extends PlaywellComponent {

  /**
   * 基于配置对服务进行初始化，通常在系统集成或者服务启动的时候会被回调
   *
   * @param config 配置数据
   */
  void init(Object config);

  /**
   * 处理服务请求消息
   *
   * @param messages 服务请求消息，playwell鼓励以小批量的方式来处理消息以提升性能，因此这里传递的是集合
   * @return 处理结果
   */
  Collection<ServiceResponseMessage> handle(Collection<ServiceRequestMessage> messages);
}
