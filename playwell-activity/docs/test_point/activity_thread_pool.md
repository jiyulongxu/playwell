# Playwell-Activity 测试要点整理

## ActivityThreadPool

### 查询ActivityThread

* 查询已存在
* 查询不存在
* 查询remove status状态的thread

### 批量查询ActivityThread

* 批量上限和查询性能

### 扫描

* 正常以默认参数的形式进行扫描
* 重复发起扫描操作，一次只能有一个扫描请求被执行
* 停止扫描
  * 正常停止
  * 停止后再发起新的扫描
* 过滤条件
  * idle(string)
  * context vars
    * var
  * thread
    * activityId
    * domainId
    * status
  * Other inner functions
  * 过滤条件使用错误的语法或者运行时错误
* 使用sync message bus进行同步
  * 同步普通服务
  * 同步Replication节点
  * 同步所有 or 只同步筛选条件
  * `sync_batch_num`参数
    * 结束和停止扫描后如果不满足该数目，是否会丢失消息
* 删除符合条件的thread
  * 当ActivityThread可写的时候
* 其它参数
  * `limit`
  * `mark`
  * `log_per_records`

### Replication

* 主节点和Replication节点同时启动
* 各种状态的同步
  * SUSPENDING
  * RUNNING
  * WAITING
  * FINISHED
  * FAIL
  * PAUSED
  * KILLED
* 主节点运行一段时间后，通过同步的方式创建Replication节点
* 主节点运行一段时间后，通过Checkpoint的方式创建Replication节点
* Replication节点切换为主节点
* 主节点切换为Replication节点

