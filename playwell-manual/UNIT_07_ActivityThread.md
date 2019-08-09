## ActivityThread

ActivityThread是Playwell的核心所在。我们所有的业务逻辑最终都是以ActivityThread为载体执行的。

Playwell在JVM线程池之上实现了ActivityThread的轻量级调度，没有底层操作系统线程那种上下文切换的开销，并且因为严格区分了SyncAction和AsyncAction，整个调度是无阻塞的，这使得Playwell每秒可以调度上万个ActivityThread实例，同时配合RocksDB这类LSM存储，让大量非活跃的ActivityThread状态可以被持久化存储，供以后唤醒。这样综合起来就是系统可以容纳上千万的ActivityThread，并且允许上万个ActivityThread同时活跃。

**ActivityThread生命周期**

ActivityThread生命周期基本是创建(spawn) -> 挂起(suspending) -> 执行(running) -> 等待(waiting) -> 执行(running) -> 结束&销毁这么个流程。

当事件满足触发器的条件，ActivityThread就会被创建，然后开始从第一个Action执行，如果是SyncAction，直接执行返回结果，如果AsyncAction，那么会发出请求消息，然后等待，当响应消息到来时，继续执行。而如果当前ActivityThread可执行，但因为调度算法的原因，尚未安排其正式调度执行，此时会处于挂起状态。

而当ActivityThread遭遇到以下场景，就会结束，并且释放存储中的所有状态(意味着你无法再查找到它，除非它再次被触发，但这时算是另一个实例了)

* `finish`控制函数被调用，正常结束。
* `fail`控制函数被调用，因为业务逻辑错误而结束。
* 发生了系统异常，比如调度器自身出现了错误。

### 基本操作

在操作系统中，我们往往可以很方便的使用系统工具对进程进行操作，而如果要是对进程内的某个线程去执行具体的操作，就不会那么容易。Playwell允许我们直接通过API或者客户端工具来查看具体某个ActivityThread的执行状态，或对其进行一系列的操作。

通常，在Playwell中，我们以两个坐标来定位某个具体的ActivityThread，一个是Activity ID，一个是Domain ID。

#### 查看

以下命令会输出单独某个ActivityThread当前的状态和上下文变量。

```shell
playwell thread get --activity_id <Activity ID> --domain_id <Domain ID>
```

#### 暂停

暂停一个ActivityThread的执行，被暂停后的ActivityThread不会继续执行，也不会再响应外部的消息。

```shell
playwell thread pause --activity_id <Activity ID> --domain_id <Domain ID>
```

#### 从暂停中恢复

```shell
playwell thread continue --activity_id <Activity ID> --domain_id <Domain ID>
```

#### kill

kill会完全"杀掉"一个ActivityThread，不再执行，而且所有的状态也会从存储中销毁。

```shell
playwell thread kill --activity_id <Activity ID> --domain_id <Domain ID>
```

### 扫描

Playwell允许我们通过指定的条件来批量扫描筛选ActivityThread，并对其应用指定的操作。

```shell
playwell thread scan \
  --conditions conditions.json
  --limit 1000 
  --log_per_records 5
  --mark test
  --remove_thread
```

参数说明：

* `conditions` 筛选条件，接受一个包含条件的JSON数组字符串或者文件路径。如果不指定，则扫描当前节点下所有的ActivityThread。
* `limit` 筛选结果条数最大限制，如果不指定则没有限制
* `log_per_records` 系统会将筛选结果输出到scan.log的日志文件中，如果筛选的ActivityThread非常多，那么输出的日志会非常庞大，并且会大大影响扫描效率，该选项指定了每筛选多少条输出一条采样日志。
* `mark` 指定一个用于标识此次扫描的字符串，这个字符串会写入到scan.log中。用于分辨此次扫描结果，如果不指定，系统会随机生成一个标识。
* `remove_thread` 如果指定了该选项，那么筛选出来的ActivityThread会被删除。

需要注意的是，如果扫描涉及到写操作，比如删除，那么Playwell节点会终止执行，直到扫描结束。另外，一个节点在同一时刻，只允许有一个扫描操作存在，而多余的扫描操作请求会被拒绝。

**筛选条件**

筛选条件是一个包含表达式的JSON数组，这些表达式之间是或的关系：

```json
[
  "var('a') == 1", # 筛选出上下文变量包含a = 1的ActivityThread
  "activityThread.currentAction == 'push'", # 筛选出当前执行单元为push的ActivityThread
  "activityThread.activityId == 1", # 筛选出Activity为1的ActivityThread
  "activityThread.domainId.startsWith('a')" # 筛选出Domain ID以'a'开头的ActivityThread
]
```

**终止扫描**

如果扫描的数据量比较大，那么可能会花费一些时间。我们也可以随时在中途停止扫描操作：

```shell
playwell thread stop_scan
```

