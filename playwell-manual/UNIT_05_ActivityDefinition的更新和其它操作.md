## Unit 5 ActivityDefinition的更新和其它操作

无论用什么语言和框架开发程序，在部署更新方面我们几乎都有着一致的愿景：

* 一键发布，不需要手工上传安装包，更不需要登录单台机器操作。
* 无缝更新，不会对线上的业务逻辑产生中断。
* 版本清晰，甚至可以多个版本在线上共存，用于平滑过渡或者对比测试。
* 出现问题可快速回滚
* 根据用户规模，可以迅速的扩容/缩容

为了达到这些目标，我们也做了很多的努力，比如持续集成、持续交付、容器化、更灵活的网关、各种DevOps方法论和工具，直到现如今的Serverless架构。

而作为一款更高抽象层次的编程框架，Playwell试图从自身的设计去达成这些目标，而无需任何外界工具。

### 更新版本

在Playwell中，ActivityDefinition在同一个名称下是可以存在多个版本的。系统会自动基于最新可用版本来触发ActivityThread。 每个ActivityThread都会跟一个具体的版本号绑定，整个生命周期都只执行这一个版本的逻辑，而不管后续有多少新版本上线。

这也就意味着，我们每次在进行版本更新的时候，正在运行的ActivityThread会不受干扰的继续执行过去的逻辑，而新触发的ActivityThread会执行最新版本的逻辑。

这种基于版本的更新，通常用于关键流程的变更，比如添加或者减少了工作单元，如果不进行隔离，新旧ActivityThread都去同时执行最新的流程，那么势必会导致一些既有的ActivityThread执行出错，比如会找不到指定的单元，或者莫名其妙漏掉某部分没执行。通过绑定具体的版本号，可以使我们放心的对流程进行"大改"，而不必担心影响正在运行的业务逻辑。

通过客户端工具，我们可以很容易的创建一个新版本的ActivityDefinition：

```shell
playwell definition create --codec yaml --definition ./def_file.yml --version 0.1
```

总共三个参数：

* `codec` ActivityDefinition使用的编码方式，目前只支持yaml
* `definition` ActivityDefinition的定义文件路径
* `version` 版本号

这三个参数都是必须的。如果要创建不同的版本，只需要指定不同的版本号即可。版本号的格式目前并没有采取严格的限定，可以按照自己的喜好进行设置。**Playwell判断最新版本的依据并非是版本号的大小，而是创建时间**。

使用客户端工具创建之后，新定义就会同步到playwell集群，就相当于部署了。

### 更新参数

很多时候，我们的修改并不需要涉及到流程的改动，只是修改一些参数，比如改写一下推送文案。那么此时就不需要添加一个新版本的定义，只需要通过客户端在既有版本上稍加修改即可：

```shell
playwell definition modify --codec yaml --definition ./def_file.yml --version 0.1
```

执行之后，就会在既有的版本上直接进行修改，并且无论新的还是旧的ActivityDefinition都会应用修改后的逻辑。

**需要注意**

Playwell目前并不能自己去判断修改是涉及到流程的执行还是只涉及简单参数调整。使用modify也是可以修改大的流程变更的（比如添加删除工作单元，或者修改ctrl控制条件），但是非常不推荐。

用户需要自行去判断每次修改用那种方式更合适。

### 限流

有些时候，对于新上的服务或者活动，我们不确定全放开是否可以顶得住压力，这时候就可以采取一种循序渐进的方式，比如一开始只允许20%的请求，然后观察各项指标，再允许50%、80%直到100%。

过去，要达到这种目的，你可能需要在网关和消息队列上做一些手脚。而现如今，如果你的服务是通过Playwell编排进行访问，那只需修改一下触发器条件就可以了：

```yaml
trigger:
  type: event
  args:
    condition: randomBoolean(0.2) AND eventAttr("behavior") == "提交订单"
```

这里用到了一个函数是`randomBoolean(double rate)` ，该函数会以一定的概率返回布尔值，上述概率即为，以20%的概率返回true。这样就会限制只有20%的请求会触发流程。我们可以不断的调整这个值。

### AB测试

Playwell对于AB测试也是非常的友好，下面分别介绍几种实现AB测试的方式。

#### 在参数中实现

这是最简单的AB测试实现，即按照条件或者随机去选择服务的参数，这种方式通常适应于类似文案级别的AB测试。比如：

```yaml
activity:
  name: ab_test
  domain_id_strategy: user_id
  config:
    content_plan:
      plan_a: "游泳、健身了解一下"
      plan_b: "健身、游泳了解一下"
    
  trigger:
    type: event
    args:
      condition: ...
    context_vars:
      plan: list.randomChoice(list("plan_a", "plan_b"))
  
  actions:
    - name: push
      args:
        request:
          content: config("content_plan")[var("plan")]
```

在ActivityThread触发之后，我们通过`list.randomChoice`函数随机选择一个文案，并将选择计入到上下文中，然后发送的时候，获取对应的文案。如果在配合数据同步，我们就能看到每个方案的效果。

#### 在流程中实现

比如我们要测试活动提醒是用邮件的方式好，还是用短信的方式好，就可以随机为用户选择执行不同的工作单元：

```yaml
- name: case
  args:
    - when: randInt(10) % 2 == 0
      then: call("email")
    - default: call("sms")

- name: email
  args: ...
  
- name: sms
  args: ...
```

#### 在不同的定义中实现

如果对比涉及到的逻辑比较复杂，那么最好定义两个不同的ActivityDefinition，需要注意触发条件不能覆盖。比如像是`randomBoolean`这种函数是不能用在触发条件的，因为这种触发几率仅仅作用于当前定义。我们可以用一些其它方法，比如对用户ID取余或者判断奇偶性等等。

后续，Playwell有会允许在Activity中配置多个版本可以按照概率触发的更新计划，这样就不需要建立多个ActivityDefinition。

### 其它操作

#### 获取所有定义的最新版本

```shell
playwell definition get_all_latest
```

#### 根据定义名称，获取其下所有版本

```shell
playwell definition get_by_name --name <definition_name>
```

#### 根据名称和版本，获取定义详情

```shell
playwell definition get --name <definition_name> --version <version> --format
```

其中`--format`是可选的，加入format参数之后，就会按照标准YAML格式输出定义内容。

#### 删除某个具体版本的定义

```shell
playwell definition delete --name <definition_name> --version <version>
```

要确保这个版本没有其它组件(Activity、ActivityThread)在使用的时候才可以删除，否则会出现找不到定义的错误。

