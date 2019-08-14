## Unit 4 Action

很多时候，编程就像玩那种收集道具才能进入下一关的游戏。如果某个问题可以直接使用某个库的某个函数去解决，并且你已经集齐了这个函数需要的所有参数，那么就会感觉一阵轻松。

使用Playwell编程也是如此，工作单元越丰富，我们的开发效率就会越高。Playwell内置了一些工作单元，主要涵盖了各种控制结构，但目前看来，这还远远不够。未来，一方面，Playwell会基于社区按照不同的业务方向扩展工作单元；另一方面，大家需要根据自己的具体业务场景去构建企业内部或个人的工作单元体系，以达到最大程度的业务逻辑复用。

### 同步和异步

在Playwell中，工作单元用Action来表示。而Action又分为了同步Action(SyncAction)和异步Action(AsyncAction)。这两种类型的主要区别在于其调度执行的方式，当调度器执行到一个SyncAction，会直接执行，同步获取结果，再根据结果来判断下一步流程的走向；而当调度器执行到一个AsyncAction，执行就分为了两部分，系统首先会向某个服务发出请求消息，然后将ActivityThread设置为WAITING状态，接下来就不再执行任何逻辑了，调度器也会将资源腾出来，调度其它的ActivityThread。直到服务返回了响应消息，ActivityThread才会进入RUNNING状态，重新被调度执行。

SyncAction通常是一些简短的控制和运算逻辑，不会引起阻塞，可以非常快的返回结果。比如像`case`单元，就是一个SyncAction，它只是简单执行一些判断条件，可以很快的完成。

而AsyncAction往往包含了那些会阻塞调度器的逻辑，比如Sleep或者请求外部服务。而为了避免调度器被阻塞，AsyncAction就分了发出请求和处理响应两个步骤来执行，通过异步消息，将阻塞的操作转移到外部服务，确保调度器保持一个高的吞吐量。

### 内置单元

下面，我们将分别来介绍Playwell一系列的内置单元。

#### case

`case`单元是一个SyncAction。类似于很多编程语言中的`if ... else if .. else ...结构`它的参数是一系列的分支判断，它会按顺序执行这些判断，直到满足条件为止。如果所有条件都不满足，若是有`default`分支，会执行`default`的逻辑，若是没有`default`，会出现错误，ActivityThread会终止。

```yaml
- name: case
  type: case
  args:
    - when: var("a") == 1
      then: call("action_a")
    - when: var("b") == 2
      then: call("action_b")
      context_vars:  # 可以在此为上下文变量赋值，context_vars是可选的
        c: 3
        d: 4
    - default: call("action_c")  # 当所有条件不满足的时候，执行default
      context_vars:
        e: 4
        f: 5
```

#### foreach

`foreach` 单元是一个SyncAction，可以用于顺序迭代一个List类型的集合，这在需要重复驱动某部分业务逻辑的场景非常有用。比如我们要追踪用户一系列的行为转化漏斗：

```yaml
- name: foreach_behavior
  type: foreach
  args:
    items: list("注册成功", "完善资料", "提交订单")
    continue: call("receive")
    eof: finish()
    
- name: receive
  type: receive
  args:
    - when: eventAttr("behavior") == var("$foreach_behavior.ele")
      then: call("add_score")
      
- name: add_score
  type: score
  args:
    request:
      user_id: domainId
      behavior: var("$foreach_behavior.ele")
  ctrl:
    - when: resultOk()
      then: call("foreach_behavior")
    - default: fail()
```

上述示例实现了一个用户引导的过程，用户每按顺序完成一个操作，就会增加相应的积分。在`foreach`单元中，`items`参数指定了要遍历的列表，`continue`参数指定了每次迭代新元素后执行的动作，`eof`参数指定了迭代完成后的操作，这三个参数都是必须的。除此之外，`foreach`单元在迭代期间还会自动创建两个上下文变量，分别是`$单元名称.ele`和`$单元名称.idx`，其中`$单元名称.ele`为当前迭代的元素，而`$单元名称.idx`为当前迭代到的索引，从0开始计数。

上面的示例即为，当`foreach_behavior`迭代到"注册成功"时，会执行`receive`单元，当`receive`单元等待到了"注册成功"事件后，会执行`add_score`单元增加积分，如果积分服务返回正常，又会再跳转到`foreach_behavior`，此时会迭代第二个元素，"完善资料"。如此，直到整个列表迭代完成为止。

你可能会纳闷，为什么有foreach结构，却没有类似于其它编程语言中的for和while结构？

因为for和while的逻辑，完全可以使用控制函数来实现，无需专门的工作单元，比如，我们从0加到100：

```yaml
- name: compute
  args:
    - sum: var("sum", 0) + var("i", 0)
    - i: var("i", 0) + 1
  ctrl:
    - when: var("i") > 100
      then: call("stdout")
    - default: call("compute")

- name: stdout
  args: str("Sum: %d", var("sum"))
  ctrl: call("compute")
```

如果你有C这类编程语言的开发经验，会发现这种工作方式跟goto关键字类似。我们经常会说goto有害，在C语言这种抽象层级，如果不加以规范，goto确实是有害的，滥用会使代码变得不清晰。但在Playwell这种更高的抽象层级，goto指向的都是工作单元，不会涉及到繁杂的逻辑细节，goto的会带来很多简便，甚至可以省去很多专门的控制结构。

#### stdout

stdout会将对象输出到标准输出设备。该单元通常只是用于调试，输出是发生在调度节点。因此，虽然它存在IO操作，但它是一个SyncAction。

```yaml
- name: echo
  type: stdout
  args: str("Are you OK?")
  ctrl: call("next_action")
```

如果参数接收的不是String类型，那么会进行toString处理：

```yaml
- name: echo
  type: stdout
  args: list("苟利国家生死以", "期因祸福避趋之")
```

#### compute

compute单元会对一系列的表达式按顺序进行计算，并将结果赋值给上下文变量。前一个表达式的结果可以用在后一个表达式中，compute单元也是一个SyncAction。

```yaml
- name: compute_insure
  type: compute
  args:
    - 合计工资: var("基本工资") - var("缺勤") + var("PRP")
    - 养老保险金: var("合计工资") * 0.08
    - 医疗保险金: var("合计工资") * 0.02
    - 失业保险金: var("合计工资") * 0.002
    - 住房公积金: var("合计工资") * 0.12
    - 五险一金总计: var("养老保险金") + var("医疗保险金") + var("失业保险金") + var("住房公积金")
    - 税前工资: var("合计工资") - var("五险一金总计")
  ctrl: call("compute_tax")
```

比如上述计算工资的示例，第一个表达式的结果，可以被用在后续的表达式当中。

当然，除了计算表达式，compute也可以单纯只是用于上下文变量的赋值。

#### delete_var

`delete_var`单元是一个SyncAction，可以删除指定的上下文变量。默认情况下，上下文变量会在ActivityThread结束时被自动销毁，不需要显式的去删除变量，这个单元通常也很少被使用。我觉着只有两个场景可能会用到：

* 需要以变量是否存在为逻辑判断依据
* 尺寸比较大的临时变量，用完之后希望及早删除释放

```yaml
- name: delete_var
  type: delete_var
  args:
    - var_a  # 要删除变量的列表
    - var_b
    - var_c
  ctrl: call("next_action")
```

#### sleep

`sleep`单元可以让ActivityThread休眠一段时间。它是一个AsyncAction。当ActivityThread执行到sleep单元的时候，它会向时钟服务发送一个注册时间点的消息，然后就不再做什么了，时钟服务会不断的检查这些已经注册了的时间点，如果某个注册时间点跟当前时间符合，那么时钟服务会向相关ActivityThread发送一个提醒消息，那么，ActivityThread就知道时间到了，会再次"醒来"。 

这就像是只要订上了闹铃，我们就可以安心睡觉，直到闹铃响起，而不必中途亲自去检查是否睡过了头。而在Playwell里面，我们可以让ActivityThread休眠任意长的时间，不必担心阻塞底层的调度线程。

```yaml
- name: sleep
  type: sleep
  args:
    time: days(5)  ## 休眠5天
  ctrl: call("next_action")
```

`sleep`单元只接受一个`time`参数，该参数接受一个毫秒单位的时间，用于指定休眠时常。使用诸如`days(int)`、`hours(int)`、`minutes(int)`、`seconds(int)`会很清晰的将天、小时、分钟、秒转化为毫秒。

#### clock

`clock`单元是一个AsyncAction，可以让ActivityThread休眠到指定的时间点，sleep指定的是时长，而clock指定的是非常具体的时间点，比如明天上午9点10分。

```yaml
- name: clock
  type: clock
  args:
    time: dateTime()
    	.plusDays(1)
    	.withHourOfDay(9)
    	.withMinuteOfHour(10)  # 一直休眠到明天上午9:10
  ctrl: call("next_action")
```

`clock`单元也只接受一个`time`参数，但该参数接受的是一个描述具体时间点的DateTime对象。

#### send

`send` 单元可以向指定的服务发送自定义消息。在Playwell的设计里，服务之间可以自由的通过消息进行交互。`send`单元是一个SyncAction，这就意味着它在发出消息之后，就什么都不管了。用户可以选择通过`receive`单元来等待接收对方的响应消息，也可以选择不接收。

```yaml
- name: send
  type: send
  args:
    service: str("target_service_name")
    messages:
      - type: str("message_type")
        attributes:
          a: 1
          b: 2
          c: str("Hello, World")
      - type: str("message_type")
        attributes:
          d: 3
          e: 4
          f: str("Good morning!")
  ctrl: call("next_action")
```

以下是各个参数的含义：

* `service` 接收消息的目标服务
* `messages` 要发送的消息列表
  * `type` 消息类型
  * `attributes` 消息参数

`send`单元和`receive`单元配合，可以编排出一些复杂的协作模式，比如类似父子进程的关系：

```yaml
## 父进程

# 发送事件，触发subprocess
- name: send
  type: send
  args:
    service: str("playwell_activity")
    messages:
      - type: str("fork_subprocess")
        attributes:
          factivity: activityId
          fdid: domainId
          a: 1
          b: 2
  ctrl: call("wait_subprocress")
  
# 等待subprocress的计算结果
- name: wait_subprocess
  type: receive
  args:
    - when: eventTypeIs("subprocess_result")
      context_vars:
        subprocess_result: eventAttr("result")
```

```yaml
## 子进程

activity:
  name: add
  domain_id_strategy: subprocess
  
  trigger:
    type: event
    args:
      condition: eventTypeIs("for_subprocess")
    context_vars:
      factivity: eventAttr("factivity")
      fdid: eventAttr("fdid")
      a: eventAttr("a")
      b: eventAttr("b")
      
   actions:
     - name: send
       args:
         service: str("playwell_activity")
         messages:
           - type: str("subprocess_result")
             attributes:
               $ACTIVITY: var("factivity")  # 消息中包含$ACTIVITY和$DID就可以被路由到指定的ActivityThread
               $DID: var("fdid")
               result: var("a") + var("b")
       ctrl: finish()
```

Playwell调度器会把自身注册为一个service，比如上面示例中的`playwell_activity`，父进程向调度器服务发送一个消息，满足子进程的触发器条件，然后就会创建子ActivityThread实例，完成计算后，再将结果返回给父进程。

除了父子进程，像是栅栏之类的协作模式，也可以基于消息收发去实现。

#### receive

`receive`单元用于等待目标事件，它是一个AsyncAction，这会是一个使用频率非常高的工作单元类型。因为我们的系统是事件驱动的，ActivityThread总是会停下来去等待一些事情发生。

```yaml
- name: receive
  type: receive
  args:
    - when: eventAttr("a") == 1
      then: call("action_1")
      context_vars:
        a: eventAttr("a")
    - when: eventAttr("b") == 2
      then: call("action_b")
    - after: hours(1)
      then: fail()
```

上面的参数与`case`单元类似，这也是一系列的`when` … `then` …条件，只不过`default`分支被换成了`after`，没有默认条件，只有发生或者没发生。`receive`相当于在逻辑判断上增加了一个时间的维度。

`after`接收的是毫秒单位的时间长度，如果不指定`after`，且等待的消息一直不能满足，那么ActivityThread就会一直处于等待状态。若是大规模的ActivityThread处于这种状态不能释放，就会造成资源泄露，因此建议尽可能为`receive`指定`after`条件。

如果你对Erlang或者Scala比较熟悉，那么会发现`receive`单元跟这些语言中接收消息然后模式匹配的语法结构非常相似。它们确实都做了同样的事情。但不一样的是，像Erlang，会将不匹配的消息堆积在邮箱，供以后匹配，要仔细避免消息大量堆积。而Playwell不是，如果消息在该单元没有满足匹配条件，会直接被丢弃，然后系统会继续等待，直到满足条件的消息到来或者是超时。

receive单元总是会按照事件到达的顺序来进行匹配。

#### 服务单元

任何外部服务只要注册到Playwell，就可以作为一个工作单元被引用。服务类型的单元也将会是Playwell数量最大的工作单元。服务单元都是AsyncAction。

```yaml
- name: action_name
  type: service_name
  args:
    request: request_args
    timeout: request_timeout
  ctrl:
    - when: resultOk()
      then: call("next_action")
```

服务单元的type都是服务的注册名称，而参数包含了两大部分，一部分是`request`，即向服务发送的请求参数，具体的参数由服务自身来决定；而`timeout`是服务的响应超时时间，可以不指定。

`ctrl`会解析服务的返回结果，执行下一步的调度工作。

#### concurrent

`concurrent`单元允许我们并发执行多个AsyncAction，并自定义结果处理条件。比如你可以定义必须当所有Action都返回正确结果才算执行成功，也可以定义只有一半Action返回正确结果就算成功。

```yaml
- name: concurrent
  type: concurrent
  args:
    actions:
      - name: str("action_a")
        result_handle:
          - when: resultOk()
            context_vars:
              ok_num: var("ok_num", 0) + 1
      
      - name: str("action_b")
      - name: str("action_c")
      
    default_result_handle:
      - when: resultOk()
        context_vars:
          ok_num: var("ok_num", 0) + 1
    
    ctrl:
      - when: var("ok_num", 0) == 3
        then: call("next_action")
      - after: seconds(10)
        then: failBecause("timeout")
        
- name: action_a
  ...
  
- name: action_b
  ...
  
- name: action_c
  ...
```

可以看到`concurrent`单元的参数分为了三大部分：

* `actions` 这里编排了要并发执行的工作单元，这些单元都在定义中声明，这里只是引用它们的名称，以及通过`result_handle`来声明结果处理。在`concurrent`单元中编排的action，其`ctrl`中的处理逻辑会被忽略，只会通过这里的`result_handle`来处理结果，将结果写入到上下文变量中。`result_handle`中的when没有then后的流程控制函数，只能更改上下文变量，因为流程控制是由`concurrent`单元来整体处理的。
* `default_result_handle`该参数是可选的，如果我们每个单元的结果处理逻辑都相同，那么可以指定一个默认的处理逻辑，避免编写重复的代码。示例中`action_b`和`action_c`就是使用了`default_result_handle`。
* `ctrl` 控制条件，包含最终的结果判定和超时处理。

`concurrent`会先同时请求列表中所有的AsyncAction。然后等待它们的结果消息。每收到一个结果消息，就会通过`result_handle`进行处理，并且判断是否满足当前某个ctrl分支。`concurrent`总是会按接收顺序同步处理结果，不必担心并发安全问题。

