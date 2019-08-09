## Getting Start

### 单机版调度节点安装

依赖：

* Oracle JDK 1.8
* MySQL
* Maven 3
* 操作系统：Linux/Windows/Mac OS/Unix

**编译**

获取源码：

```shell
git clone git@github.com:playwell-framework/playwell.git
```

进入项目目录：

```powershell
cd playwell/playwell-activity
```

编译打包：

```shell
mvn clean compile assembly:single
```

此时在target目录下会生成编译完毕的jar包，可将此jar包复制到期望的安装目录，比如`/opt/playwell`

**初始化元数据**

Playwell默认在MySQL中记录工作所需要的元数据，比如活动定义以及服务信息。因此我们需要初始化表结构。

在您的MySQL中创建一个新的库，比如`playwell`，然后将以下SQL脚本直接导入即可：https://github.com/playwell-framework/playwell/tree/master/playwell-activity/docs/schema

**配置文件**

playwell的配置文件结构分为"资源"和"组件"两部分。

资源是系统运行所依赖的一些公共支持，比如数据库连接池、HttpServer、各种连接客户端等等。

组件是Playwell系统的各个组成部分：

* `activity_definition_manager` 管理活动定义
* `message_domain_id_strategy_manager` 管理消息路由策略
* `activity_manager` 管理具体活动
* `clock`时钟
* `activity_thread_pool` 管理ActivityThread
* `message_bus_manager`管理MessageBus
* `service_meta_manager`管理ServiceMeta
* `activity_thread_scheduler` ActivityThread调度器
* `activity_runner` 总消息循环路由

以上组件都是在配置文件中独立配置，彼此之间都是通过抽象接口(interface)进行交互，从而组成完整系统。

下面是完整的配置文件：playwell.yml

```yaml
playwell:
  tz: "Asia/Shanghai"  # 时区

	# 声明资源
  resources:
    # 时间戳缓存，因为系统中有大量的地方需要时间戳，为了避免
    # 频繁系统调用，可通过公共的时间戳缓存来获取
    # period为刷新时间
    - class: playwell.clock.CachedTimestamp
      period: 100

		# RocksDB相关配置，我们会在系统优化章节来详细介绍参数
		# 这里只需要修改数据目录即可
    - class: playwell.storage.rocksdb.RocksDBHelper
      path: /data/test_data/test_rocksdb_rpa
      db_log_dir: /data/test_data/test_rocksdb_rpa
      keep_log_file_num: 10
      max_log_file_size: 1048576
      block_caches:
        # - name: common
        #   type: LRU
        #   capacity: 2147483648
      column_families:
        - name: default
        - name: clock
          write_buffer_size: 131072
          max_write_buffer_number: 5
          min_write_buffer_number_to_merge: 2
          level0_file_num_compaction_trigger: 5
        - name: thread
          write_buffer_size: 67108864
          max_write_buffer_number: 3
          min_write_buffer_number_to_merge: 2
          iterator_readahead_size: 2097152
		
		# 数据库连接池，这里要配置刚才创建的库
    - class: playwell.storage.jdbc.DataSourceManager
      datasource:
        - name: playwell
          driver: com.mysql.cj.jdbc.Driver
          url: 'jdbc:mysql://localhost:3306/playwell_test?useSSL=false&user=playwell&password=123456&useUnicode=true&characterEncoding=utf8&useLegacyDatetimeCode=false&serverTimezone=Asia/Shanghai'
          max_active: 100
          initial_size: 10
          max_idle: 20
          max_wait: 5000
          remove_abandoned: true

		# 基于Netty的Http客户端
    - class: playwell.http.NettyHttpClientHolder
      event_loop: nio
      n_threads: 2
      connect_timeout: 10000
      request_timeout: 10000
      read_timeout: 10000

		# 内置的Http Service，用于API以及HttpMessageBus
    - class: playwell.http.HttpServiceManager
      services:
        - host: 127.0.0.1
          port: 1922
          min_threads: 2
          max_threads: 8

    # 在该节点上启用API
    - class: playwell.api.PlaywellAPIServer
      host: 127.0.0.1
      port: 1922

  message_domain_id_strategy_manager:
    class: playwell.message.domainid.MySQLMessageDomainIDStrategyManager
    datasource: playwell

  activity_definition_manager:
    class: playwell.activity.definition.MySQLActivityDefinitionManager
    codecs:
      - class: playwell.activity.definition.YAMLActivityDefinitionCodec
    datasource: playwell

  activity_manager:
    class: playwell.activity.MySQLActivityManager
    datasource: playwell
    listeners:

  clock:
    class: playwell.clock.RocksDBClock
    column_family:
      name: clock
    direct: false

  activity_thread_pool:
    class: playwell.activity.thread.RocksDBActivityThreadPool
    column_family:
      name: thread
    direct: false

  message_bus_manager:
    class: playwell.message.bus.MySQLMessageBusManager
    datasource: playwell
    buses:
      - name: activity_bus_rpa
        class: playwell.message.bus.HttpMessageBus
        url: "http://127.0.0.1:1922/bus"

  service_meta_manager:
    class: playwell.service.MySQLServiceMetaManager
    datasource: playwell
    local_services:

  activity_thread_scheduler:
    listeners:

  activity_runner:
    service_name: playwell_rpa
    input_message_bus: activity_bus_rpa
    sleep_time: 1000
    max_fetch_num: 10000
    max_error_num: 10
    listeners:
      - message_domain_id_strategy_manager
      - activity_definition_manager
      - activity_manager
      - clock
      - activity_thread_pool
      - message_bus_manager
      - service_meta_manager
```

`resources`项中的都是公共资源配置，而下面的就是具体的组件。`class`是具体实现，然后各个组件有自己不同的参数。

**日志**

playwell的日志基于log4j2，可以按需进行配置。系统内置了几个log，也可以只配置一个log，将所有输出到一个日志文件中。

log4j2.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
  <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
    </Console>
    <File fileName="playwell.log" name="File">
      <PatternLayout>
        <pattern>%d %p %C{1.} [%t] %m%n</pattern>
      </PatternLayout>
    </File>
    <File fileName="performance.log" name="Performance">
      <PatternLayout>
        <pattern>%d %p %C{1.} [%t] %m%n</pattern>
      </PatternLayout>
    </File>
    <File fileName="scan.log" name="Scan">
      <PatternLayout>
        <pattern>%d %p %C{1.} [%t] %m%n</pattern>
      </PatternLayout>
    </File>
    <File fileName="activity_thread.log" name="ActivityThread">
      <PatternLayout>
        <pattern>%d %p %C{1.} [%t] %m%n</pattern>
      </PatternLayout>
    </File>
  </Appenders>
  <Loggers>
    <Logger additivity="false" level="INFO" name="scan">
      <AppenderRef ref="Scan"/>
    </Logger>
    <Logger additivity="false" level="INFO" name="performance">
      <AppenderRef ref="Performance"/>
    </Logger>
    <Logger additivity="false" level="INFO" name="activity_thread">
      <AppenderRef ref="ActivityThread"/>
    </Logger>
    <Root level="INFO">
      <AppenderRef ref="File"/>
    </Root>
  </Loggers>
</Configuration>
```

**启动**

现在，一切就绪，就可以启动playwell调度进程了。

```shell
nohup java -jar ./playwell-activity.jar -config ./playwell.yml --log4j ./log4j2.xml > ./playwell.out 2>&1 &
```

可以通过API检查一下运行状态：

```shell
curl http://localhost:1922/v1/activity_runner/status
```

### 安装客户端

依赖：

* python >= 3.7
* 操作系统：Linux/Windows/Mac OS/Unix

进入项目目录：

```shell
cd playwell/playwell-client
```

执行安装：

```shell
python ./setup.py install
```

客户端依赖于调度节点的API进行工作，需要将API地址加入到环境变量：

```shell
export PLAYWELL_API=http://localhost:1922
```

接下来就可以通过客户端来更方便的访问API了。

检查运行状态：

```shell
playwell activity_runner status
```

客户端指令是与调度节点各个组件的API一一对应的：

```
playwell 组件 操作 --参数A 值 --参数B 值
```

直接执行playwell，可以列出支持的所有组件：

```
$ playwell
Invalid command arguments, eg.
  playwell definition validate --codec yaml --file ./definition.yml
  playwell activity create --definition test --display_name 'Test activity' --config '{}'
  playwell activity pause --id 1

All modules:
   definition  -  Playwell activity definition API
   activity  -  Playwell activity API
   activity_runner  -  Playwell activity runner API
   thread  -  Playwell Activity thread API
   clock  -  Playwell clock API
   domain  -  Playwell Domain API
   message_bus  -  Playwell message bus API
   service  -  Playwell service API
   service_runner  -  Playwell service runner API
   slots  -  Playwell slots API
   system  -  Playwell system API
```

通过help命令列出组件下的所有操作和对应的API

```
$ playwell domain help
add: [POST] http://localhost:1922/v1/domain_id/add
  --name {'required': True, 'help': 'The domain id strategy unique name'}
  --cond_expression {'required': True, 'help': 'The condition expression of the domain id strategy'}
  --domain_id_expression {'required': True, 'help': 'The domain id extract expression'}

delete: [DELETE] http://localhost:1922/v1/domain_id
  --name {'required': True, 'help': 'The target domain id strategy name'}

get_all: [GET] http://localhost:1922/v1/domain_id/all
  No need arguments
```

### Hello World

安装完调度节点和客户端，就可以使用Playwell来开发程序了。

**创建Domain ID Strategy**

首先，我们来创建一个Domain ID Strategy，用来路由事件：

```shell
playwell domain add --name test --cond_expression 'eventTypeIs("test")' --domain_id_expression 'eventAttr("cmd_id")'
```

该策略接收test类型的事件，然后从事件属性中提取`cmd_id`作为Domain ID

**创建ActivityDefinition**

接下来，使用yaml编写定义，test.yml：

```yaml
activity:
  name: test
  domain_id_strategy: test
  
  trigger: eventAttr("cmd") == "test"
  
  actions:
    - name: stdout
      args: str("Hello, World")
      ctrl: finish()
```

收到事件时，就会在标准输出输出Hello World

通过客户端创建定义：

```shell
playwell definition create --definition test.yml --version 0.1
```

**创建Activity**

ActivityDefinition是代码，而Activity是进程，只有创建了Activity，才可以触发可执行实例。

```shell
playwell activity create --definition test
```

**触发事件**

我们本次配置，调度节点是通过HTTP服务接收事件：

```shell
curl http://localhost:1922/bus -H "Content-Type:application/json" -X POST --data '[{"type": "test", "attr": {"cmd_id": 1, "cmd": "test"}}]'
```

然后就可以看一下调度节点重定向的输出文件`playwell.out`中是否有hello world输出。

