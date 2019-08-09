# Playwell - 测试要点整理

## MessageBus

### 注册新的MessageBus

* 普通注册
* 类不存在
* 名称不合法
* 配置信息不合法
* 同名重复注册

### 删除MessageBus

* 正常删除
* 删除不存在
* 对已经删除的MessageBus进行打开、关闭、写消息操作

### 打开MessageBus

* 打开已关闭
* 重复打开

### 关闭MessageBus

* 正常关闭
* 重复关闭
* 向关闭的MessageBus写入消息

### HttpMessageBus

* 注册
* 写入消息
  * direct写入
  * buffer写入
    * 最大尺寸
    * 最大周期
    * 消息合并
* 读取消息
* HttpMessageBus位于同一个节点读写消息 direct / buffer

### KafkaTopicMessageBus

### KafkaPartitionMessageBus

