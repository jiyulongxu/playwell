## Activity

如果说ActivityDefinition是程序，那么Activity就是进程。一个程序可以起多个拥有不同配置的进程，一个ActivityDefinition也可以起多个拥有不同配置的Activity。

Activity的主要作用在于允许使用不同的配置来创建活动，并且这些活动之间是彼此隔离的。举个例子，我们要对不同性别的新用户发送不同的欢迎消息，那么我们就可以使用基于同一份ActivityDefinition来分别创建不同的Activty。

```yaml
activity:
  name: welcome_mail
  domain_id_strategy: register_user_id
  
  trigger:
    type: event
    args:
      condition: eventAttr("behavior") == "注册成功" AND eventAttr("gender") == config("gender")
    
   actions:
     - name: email
       args:
         request:
           subject: config("subject")
           content: config("content")
       await: false
       ctrl: finish()
```

可以看到，上面定义中，很多变量都是引用的`config(...)`配置项，我们只要在创建Activity的时候，指定不同的配置项，就可以实现不同的触发条件和发送不同的内容。当然，上述逻辑也可以在一个Activity中去做，只不过需要在定义中用case或者三目运算符之类去判断一下，但有时候使用不同配置的Activity可以让逻辑更简单清晰，ActivityDefinition的复用程度也会变得更高。另外，每个Activity都是可以被单独控制的，可以单独暂停、恢复、下线一个Activity，而其它的不受影响。比如就像在网络游戏运营的场景，你可以单独停止某个服的活动而不影响其它的。

### 基本操作

下面我们以客户端命令行的方式来介绍Activity的各种基本操作。

#### 创建

```shell
playwell activity create \
    --definition welcome_mail
    --display_name 'Welcome mail'
    --config '{"gender": "maile", "subject": "xxxxxxx", "content": "xxxxxxx"}'
```

* `definition` 必须，ActivityDefinition的名称
* `display_name` 可选，Activity用于展示的名称，如果有可视化的管理界面，这个会非常有用，让我们知道这个Activity是做什么的。
* `config` 可选，以json的形式指定配置常量，接受一个json字符串，或者包含json字符串文件的路径。

在playwell中，每个activity均通过一个唯一ID进行标识，创建成功后，会返回该ID。

#### 暂停

执行暂停操作后，Activity下就不会有新的ActivityThread被触发，而对于已经存在的ActivityThread，其行为决定于配置，如果Activity的配置项`$schedule.pause_continue_old`为true，那么已经存在的ActivityThread会继续正常运行，如果为false，那么已经存在的ActivityThread也不会执行，相关的事件也会丢弃。

`$schedule.pause_continue_old`默认为false，大家可以根据场景的需要，假如不想放弃尚未执行完毕的ActivityThread，就可以设置为true。

```shell
playwell activity pause --id <activity id>
```

#### 恢复

被暂停的Activity可以恢复，继续执行。

```shell
playwell activity continue --id <activity id>
```

#### Kill

我们可以像杀死一个进程一样杀死一个Activity。

```shell
playwell activity kill --id <activity id>
```

但是需要注意，Activity被kill掉之后，其下没有执行完的ActivityThread相关数据还是会被保留。如果要彻底清理一个Activity，建议按照如下顺序：先将其暂停，然后扫描清理其下的ActivityThread(具体方法将在ActivityThread相关章节介绍)，最后将Activity kill掉。

#### 修改配置

为Activity指定一套全新的配置项：

```yaml
playwell activity modify_config --id <activity id> --config <config>
```

其中config选项可以指定配置的JSON字符串，或者包含JSON格式配置的文件路径。

#### 添加配置项

通常我们只是操作单个配置项，`put_config_item`可以添加或者更新指定的选项。

```shell
playwell activity put_config_item --id <activity id> --key <config item> --type <config type> --value <value text>
```

* id 活动类型
* key 配置项名称
* type 配置项类型，支持的类型：
  * int
  * bool
  * long
  * double
  * map
  * list
  * str
* value 配置项的值

特别需要注意的是类型，因为从客户端工具传递的原始值都是字符串，playwell需要知道具体的类型以便于解析：

```shell
playwell activity put_config_item --id 1 --key a --type int --value 100
playwell activity put_config_item --id 1 --key b --type str --value 鸡你太美
playwell activity put_config_item --id 1 --key c --type bool --value true
playwell activity put_config_item --id 1 --key d --type double --value "3.14"
playwell activity put_config_item --id 1 --key f --type list --value '[1, 2, 3, 4]'
playwell activity put_config_item --id 1 --key g --type json --value '{"a": 1, "b": 2}'
```

#### 删除配置项

允许删除单个配置项，删除的时候需要注意，ActivityThread在运行的时候是否需要该配置项，或者在调用`config`函数的时候指定了默认值，否则会出现错误，导致ActivityThread终止。

```shell
playwell activity remove_config_item --id 1 --key a
```

#### 查看

Playwell支持多种查看Activity详情的接口。

##### 查看所有

```shell
playwell activity get_all
```

##### 根据指定的ID查看

```shell
playwell activity get --id <activity id>
```

##### 根据活动定义名称查看

```shell
playwell activity get_by_definition --name <activity definition name>
```

##### 根据状态查看

```shell
playwell activity get_by_status --status common
```

`status`参数可以指定：

* `common` 正常执行
* `paused` 暂停执行

已经被kill掉的Activity记录会被销毁，无法查看。

### 系统配置项

Activity拥有很多保留的系统配置项，这些配置项通常会影响到其下ActivityThread的调度行为。

#### $schedule.max_continue_periods

该配置项为最大持续调度单元。Playwell调度器的工作默认依赖于消息等待，当ActivityThread运行到需要等待消息的单元(AsyncAction)时，会将底层线程"让出"，供其它有需要的ActivityThread执行。

但假设我们写了一个死循环，并且这个循环中都是SyncAction，没有需要等待消息的操作，那么这个ActivityThread就会一直占用底层执行线程，而如果这样的ActivityThread不止一个，那么真正执行调度的底层线程很快就会被占用光，其它的ActivityThread就没有了执行的机会，甚至新的ActivityThread也不会触发。这种现象在并发编程中俗称"饥饿"问题。

通过配置`$schedule.max_continue_periods`可以解决这个问题。该配置项指定了连续执行单元的最大个数，超过了该数目，系统就会把该ActivityThread设置为挂起(SUSPENDING)状态，并向时钟注册一个时间点，然后该ActivityThread就不再执行，底层调度线程就被让给了其它的ActivityThread，直到时间点到来，该ActivityThread才会被继续唤醒执行，并重新开始计数，当再次达到`$schedule.max_continue_periods`的值，会被重新挂起。这种工作机制很像我们熟悉的操作系统时间片，只不过Playwell的抽象层次更高，协调的都是服务，不需要精确到CPU指令级的计数，在目前情况下，也不需要考虑优先级算法。

该配置项默认值为-1，也就是不进行计数，默认依赖于消息等待进行调度，这对大多数的活动是没有问题的，但如果你的活动中包含了纯粹由SyncAction组成的循环，那么请务必考虑设置该项，否则极有可能会造成大面积的"饥荒"。

#### $schedule.suspend_time

即上文中设置的时间点长度，单位是毫秒，默认为1000，也就是ActivityThread被挂起1秒之后会被再次唤醒执行。

#### $schedule.pause_continue_old

Activity被暂停之后，已经存在的ActivityThread是否继续执行。如果为false，那么就不会执行，相关的消息也会被丢弃；如果为true，那么暂停只是新的ActivityThread不会被触发，而旧的ActivityThread会照常执行。默认为false。

但需要注意，当该项被设置为false的时候，ActivityThread可能会错过相关消息，导致今后再也无法执行。

#### $schedule.monitor

该选项用于指定一个服务，该服务用于监控需要修复的ActivityThread。当控制函数`repair`被调用时，会向该服务发送一个消息。该服务由开发者自定义了修复问题的手段，自动定位修复，或者引发报警人工修复皆可。

我们在错误处理相关章节会详细介绍该参数的具体使用。

#### $thread_log

我们在线上排查问题的时候，最痛苦的，恐怕就是日志信息不足了。此时我们要么使用一些外挂工具，比如Java程序员会使用BTrace向线上运行环境注入代码；要么完善日志，重新编译打包部署。这两种方式，操作起来都比较痛苦，另外，如果输出了过多的日志，有时对性能也会有影响。通过`$thread_log`配置项，可以控制在具体的ActivityThread生命周期打印日志，日志内容包括了当前ActivityThread的状态、上下文变量、错误消息等一切信息，并且可以随时在线开关，日志都会输出到名为`activity_thread`的logger当中。`$thread_log`的具体值：

* `all` 输出所有生命周期的信息
* `error` 只输出系统错误相关的信息。
* `none`不输出任何信息

如果你想开启某个活动的日志，那么直接使用客户端工具修改Activity的配置项即可：

```shell
playwell activity put_config_item --id <activity id> --key '$thread_log' --type str --value all
```

如果要停止输出：

```shell
playwell activity put_config_item --id <activity id> --key '$thread_log' --type str --value none
```

或者直接删掉该配置项：

```shell
playwell activity remove_config_item --id <activity id> --key '$thread_log'
```

ActivityThread log的输出格式：

```
Operation - ActivityDefinitionName - ActivityID - DomainID - ActivityThreadStatusCode - CurrentAction - Context
```

* `operation` 操作类型，描述当前ActivityThread生命周期的执行步骤，以下是所有的operation:
  * `spawn_success` 触发新ActivityThread成功
  * `spawn_error` 触发新ActivityThread发生错误
  * `suspending`进入挂起状态
  * `running` 恢复执行状态
  * `sync_exec` 执行SyncAction
  * `async_req` 执行AsyncAction的消息请求操作
  * `async_res` 执行AsyncAction的消息响应操作
  * `async_no_wait` nowait的AsyncAction执行
  * `schedule_error` 调度出现系统错误
  * `kill` ActivityThread被执行了kill指令
  * `paused` ActivityThread被执行了pause指令
  * `continue_success` ActivityThread从暂停中成功恢复执行
  * `continue_fail` ActivityThread从暂停中恢复执行失败
  * `retry`执行`retry`控制函数
  * `repair` 执行`repair`控制函数
* `ActivityDefinitionName` ActivityDefinition名称
* `ActivityID` Activity ID
* `DomainID` Domain ID
* `ActivityThreadStatusCode` ActivityThread当前状态
  * 0 - suspending
  * 1 - running
  * 2 - waiting
  * 3 - finished
  * 4 - fail
  * 5 - paused
  * 6 - killed
* `CurrentAction` 当前执行的工作单元
* `Context` 当前所有的上下文变量值

通过以上日志，我们就可以很清晰的了解当前ActivityThread的执行状态。