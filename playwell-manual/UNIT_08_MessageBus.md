## MessageBus

Playwell中所有的通信都是基于消息传递。而消息传递需要通过某些渠道，这些渠道可能是某种网络协议，也可能是一些消息中间件。Playwell统一将这些渠道抽象成了MessageBus，它拥有一个写消息的接口，一个读消息的接口，一些用于初始化的配置信息以及一个可以用来引用它的名称。

Playwell目前内置了基于HTTP和Apache Kafka的MessageBus。HTTP实现的MessageBus通常只是用于测试或者对消息传输稳定性不那么严格的场景，正式场景更推荐使用基于Kafka的MessageBus。

当然，后续我们也会提供更多类型的MessageBus，以支持更多的网络协议和消息中间件。

### 批量传递

针对IO的性能优化，向来是有两大法宝：异步和批量聚合。在Playwell中，无论哪种MessageBus，都是以一种异步批量的方式传递消息，通常会在本地维护一个缓冲，当缓冲大小或者时长超出了阈值，再将消息写入到具体的通信渠道当中。

而对于消息接收端，一次接收的也是批量的消息，然后再执行具体的分发。比如，可以多个服务共用一个MessageBus，批量收到消息后，再将消息分发到具体的服务。

而对于服务，也是建议通过处理批量消息的方式进行编写，这在很多时候对性能优化至关重要，尤其是一些偏IO的场景。比如说，我们一个服务主要功能是基于Redis进行计数，那么在操作Redis之前，我们可以把相同的计数项汇总起来，再去更新Redis，这样一是降低了与Redis的通信次数，二是也降低了执行计数指令的次数。

### 完全分离的请求/响应

像我们熟悉的基于HTTP协议的API或者其它的RPC框架，一次通信会通常包含请求和响应两个环节。

Playwell中的消息通信也是先向服务发送请求消息，然后等服务处理完毕，再返回响应消息。但这却不是通过一次通信完成的。而是通过两次完全独立的通信：请求是一次，响应是另一次。并且这两次通信通常不是通过同一个MessageBus，非常可能会使用不同类型的MessageBus，比如请求是通过基于HTTP的MessageBus传递，而响应却是通过基于Kafka的MessageBus。

每个服务会绑定一个用于输入请求消息的MessageBus。假设我们有两个服务：

- 服务A，绑定的MessageBus为InputA
- 服务B，绑定的MessageBus为InputB

服务A想调用服务B，那么就向InputB发送请求消息，同时在请求消息中加入自己的标识；服务B处理了服务A的请求后，会根据请求消息的标识，获取到服务A的MessageBus，InputA，然后将响应消息写入到InputA，这样，服务A就获得了响应结果。

在这其中，每个服务可以使用不同类型的MessageBus，比如服务A可以绑定HTTP MessageBus，而服务B可以绑定Kafka MessageBus。

为什么要将请求和响应分离？这样做的目的，一是最大程度的解耦，让Playwell可以与各种异构的组件集成，并且各组件可以根据自己的场景选择适合自己的MessageBus；二是更为彻底的异步，无论是请求还是响应，发送完毕消息后，组件可以继续处理其它的事情，而完全不必顾及另一端的状态如何。

**这样做会不会造成网络通信次数增加？**

并不会，就像上文说的，Playwell并不是每消息每请求的通信模式，消息在发送之前会进行聚合，一次网络请求通常就可以携带好多消息。

**对业务的实时性会不会造成影响**

Playwell是一个软实时的系统，目前无法保证完全实时，但用户可以根据自身需要来定义实时的范围。比如可以定义缓冲区刷新周期、获取响应的超时时间以及对处理节点进行扩展和优化提升处理效率。这些时间的估值，需要去通过仔细研究业务场景并不断测量来获得，最终控制在一个让业务可以接受的范围之内。

### 基本操作

#### 注册

要在系统中使用MessageBus，首先要进行注册。注册的方式有两种，一种是在playwell配置文件中声明，在启动服务的时候会自动注册；另一种方式是通过API或客户端进行注册。

**通过配置文件注册**

```yaml
message_bus_manager:
    class: playwell.message.bus.MySQLMessageBusManager
    datasource: playwell
    buses:
      - name: activity_bus
        class: playwell.message.bus.HttpMessageBus
        url: "http://127.0.0.1:1923/input"
```

上述示例就会在每次服务启动时注册一个名为`activity_bus`的HttpMessageBus，各个注册项：

* `name` MessageBus名称，必须。
* `class` MessageBus类型，必须。`playwell.message.bus.HttpMessageBus`即为一个HTTP类型的MessageBus。
* `url` 具体的MessageBus配置信息，可选。配置信息的项目和格式，根据MessageBus的类型不同而不同。对于HttpMessageBus需要一个URL地址进行消息传递。

**通过客户端或API注册**

假设我们自己编写了一个基于HTTP协议的服务，我们想立即将该服务注册到Playwell中，然后马上可以创建基于该服务的ActivityDefinition，而不必更新Playwell的配置。那么我们就可以直接通过客户端或者API来注册MessageBus信息：

```shell
playwell message_bus register --config ./message_bus_config.json
```

`config`参数接受一个json格式的配置文件，里面的配置项与我们上文中的例子其实是一样的。

`message_bus_config.json`

```json
{
  "name": "test_service_bus",
  "class": "playwell.message.bus.HttpMessageBus",
  "url": "http://127.0.0.1:1923/input"
}
```

<b>需要注意的是，通过客户端或API注册的MessageBus，默认是处于关闭状态的，需要我们手动打开，而基于配置文件注册的会自动打开。</b>

执行打开操作：

```shell
playwell message_bus open --name test_service_bus
```

注册并打开之后，该MessageBus的信息会应用到整个Playwell集群，并在各个节点进行初始化，接下来，它就可以用于为服务传递消息。

#### 打开关闭

Playwell为MessageBus提供了逻辑上的开关。如果我们关闭了一个MessageBus，那么就不允许再往里面写数据，同时，ActivityThread也会因为异常而执行失败。通常情况下，我们并不需要手工操作这些开关，只有在紧急限流的情况下才需要如此。

**打开**

```shell
playwell message_bus open --name test_service_bus
```

**关闭**

```shell
playwell message_bus close --name test_service_bus
```

#### 查看

**查看指定的MessageBus**

```shell
playwell message_bus get --name <Message bus name>
```

**查看所有的MessageBus**

```shell
playwell message_bus get_all
```

#### 写入消息

Playwell允许通过客户端和API直接向某个MessageBus写入消息，这对一些业务场景非常有用。Playwell是基于事件触发，但事件并不仅仅是由用户、设备等外界环境去主动产生，我们也可以把事件保存到数据库之类的存储中，成为记录，而当有需要的时候，再把这些记录回放成事件去触发自动化流程。

最常见的需求就是我们会将用户行为记录到数据库中，然后筛选出具有某些特征的用户，接下来对这些用户运用某个自动化流程，比如群发消息。

**使用客户端写入**

```shell
playwell message_bus write --message_bus activity_message_bus --messages ./messages.json 
```

参数说明

* `message_bus` 指定要写入消息的message\_bus名称。
* `messages` 指定包含消息json数组的文件路径。

messages.json

```json
[
  {
    "type": "behavior",
    "attr": {
      "behavior": "提交订单",
      "user_id": 10001,
      "order_id": "xxxxxxxxxxx"
    }
  },
  {
    "type": "behavior",
    "attr": {
      "behavior": "提交订单",
      "user_id": 10002,
      "order_id": "xxxxxxxxxxx"
    }
  },
  {
    "type": "behavior",
    "attr": {
      "behavior": "提交订单",
      "user_id": 10003,
      "order_id": "xxxxxxxxxxx"
    }
  }
]
```

**使用API写入**

使用客户端工具往往只是用于测试。如果要将大量的记录转换为事件，写入到MessageBus，从而触发自动化流程，那么最好使用API的方式。客户端也是调用了如下的API：

```
[POST] /v1/message_bus/write
```

接受JSON格式的请求体：

```json
{
  "message_bus": "test_message_bus",
  "messages": [
    {
    "type": "behavior",
    "attr": {
      "behavior": "提交订单",
      "user_id": 10001,
      "order_id": "xxxxxxxxxxx"
    }
  },
  {
    "type": "behavior",
    "attr": {
      "behavior": "提交订单",
      "user_id": 10002,
      "order_id": "xxxxxxxxxxx"
    }
  },
  {
    "type": "behavior",
    "attr": {
      "behavior": "提交订单",
      "user_id": 10003,
      "order_id": "xxxxxxxxxxx"
    }
  ]
}
```

### 基于HTTP的MessageBus

Playwell内置了基于HTTP协议的MessageBus `playwell.message.bus.HttpMessageBus`。

使用HttpMessageBus之前，要先在Playwell节点配置相关资源。如果有写消息的需求，就需要配置HTTP客户端，因为所谓的写消息，本质其实就是向HTTP服务发送请求；如果有读消息的需求，那么需要配置内嵌的HTTP Server，因为所谓的读消息，就是接收HTTP客户端发来的请求。

**配置HTTP客户端**

Playwell默认的HTTP客户端是基于Netty的。

```yaml
resources:
  - class: playwell.http.NettyHttpClientHolder
    event_loop: nio
    n_threads: 2
    connect_timeout: 10000
    request_timeout: 10000
    read_timeout: 10000
```

配置项说明：

* `event_loop` Netty所采用的事件循环类型，支持`nio`和`epoll`两种选择。如果是运行在Windows或者Mac系统，那么可以选择`nio`；如果是运行在Linux系统，那么可以选择`epoll`。
* `n_threads` Netty 事件循环线程的数目，即使用多少线程来处理IO事件。
* `connect_timeout` 建立连接超时时间，单位毫秒
* `request_timeout` 发送请求的超时时间，单位毫秒
* `read_timeout` 获取响应的超时时间，单位毫秒

**配置HTTP服务端**

```yaml
resources:
  - class: playwell.http.HttpServiceManager
    services:
      - host: 127.0.0.1
        port: 1922
        min_threads: 2
        max_threads: 8
```

配置项说明：

* `host` Http Server监听地址
* `port` Http Server监听端口
* `min_threads` 处理请求的线程池最少线程数目
* `max_threads`处理请求的线程池最大线程数目
* `idle_time` 线程最大闲置时间，超过该时间，线程会被回收，直至线程池只保留`min_threads`个线程。

**无论是客户端还是服务端，都不是必须配置的资源，需要结合当前节点的情况，如果当前节点需要通过HTTP请求来调用外部服务，那么就需要配置客户端；如果当前节点要通过HTTP接收事件和其它服务的请求/响应消息，那么就需要配置服务端。对于Playwell调度节点，如果不是通过HTTP接收消息，那么只需要配置HTTP客户端即可**

**HttpMessageBus配置项**

```yaml
message_bus_manager:
    class: playwell.message.bus.MySQLMessageBusManager
    datasource: playwell
    buses:
      - name: activity_bus
        class: playwell.message.bus.HttpMessageBus
        direct: false
        max_buffer_size: 1000
        refresh_buffer_period: 1000
        url: "http://127.0.0.1:1923/input"
```

* `direct` 是否直接传输而不经过缓冲区，如果是true，则采用直接传输，如果是false，则通过缓冲区聚集后进行传输，建议选择false。
* `max_buffer_size` 最大缓冲消息数目，达到了该数目，所有消息会被合并，并发出HTTP请求。
* `refresh_buffer_period`最大缓存刷新周期，接受一个毫秒单位的时间戳，当达到了时间点，缓冲中的消息就会被合并，然后发出HTTP请求。`max_buffer_size`和`refresh_buffer_period`，这两个条件只要达到其中一个，消息缓冲就会被刷新。
* `url` 最终传递消息的URL地址。HttpMessageBus会去自动检查当前节点是否有相关HttpServer的配置，如果有，会自动注册一个该地址的Handler，那么该节点就相当于可以读消息了。比如我们配置的url地址是`http://127.0.0.1:1923/input`，在初始化时会检查当前节点有无配置监听信息为`127.0.0.1:1923`的HTTP Server资源，如果有，会在其上自动注册一个`[POST] /input`的Handler用来接收请求消息。

**消息格式**

HttpMessageBus接受ContentType为`application/json` 的POST请求，请求体格式为JSON数组，数组中每个元素都是一个消息，由接收端根据消息类型，自行解码为对应的消息对象。

请求体：

```json
[
  {
    "type": "behavior",
    "attr": {
      "behavior": "用户登录",
      "user_id": 10010
    },
    "time": 1563937177517
  },
  {
    "type": "behavior",
    "attr": {
      "behavior": "用户注册",
      "user_id": 10011,
      "email": "chihongze@gmail.com",
      "mobile": 17600817832
    }
  },
  ...
]
```

**HttpMessageBus的问题**

HttpMessageBus的优点是使用简单，尤其是在集成第三方服务的时候。我们只需要用自己喜欢的语言和web框架，编写一个接收上述json数组格式的handler，接下来用它的URL就可以注册一个MessageBus。

但HttpMessageBus的最大问题，在于无法保障消息总是会被传递到目的地。比如，我们的消息当前位于缓冲中尚未处理，这时候出现了异常或者节点宕掉了，那么这些消息就丢失了。

要提高消息传输的可靠性，最好的办法，就是使用专门的消息中间件，比如Apache Kafka。

### 基于Kafka的MessageBus

上文我们介绍过，对于基于HTTP的MessageBus，写操作就是通过HTTP客户端来发送请求，而读操作就是通过HTTP服务来读请求。

基于Kafka的MessageBus也同样如此，写操作就是用Producer API来发送消息，而读操作就是用Consumer来接收消息。

Kafka有两种消费模式：

* 一种是订阅Topic，然后Consumer具体消费哪些Partition是由仲裁节点决定的，当Consumer的数目发生变更的时候，仲裁节点会进行再均衡。这种方式非常适合无状态的Consumer，因为有了再均衡的特性，**在理论上**，我们可以很方便的调整Consumer进程的数目以达到调整服务规模的目的。
* 另外一种是消费者明确订阅Topic中的哪个Partition，不需要仲裁节点分配，也没有再均衡一说。这种通常适应于有状态的Consumer。首先，消息是按照某种规则路由到Partition中的，而为了维持状态的一致性，我们要确保满足同样规则的消息，总是由相同的Consumer进行处理，否则状态可能就会发生分裂，同样的事物，在不同的Consumer中就出现了不同的状态。

Playwell针对这两种模式提供了不同的MessageBus：

* 针对订阅Topic `playwell.message.bus.KafkaTopicMessageBus`
* 针对订阅Partition `playwell.message.bus.KafkaPartitionMessageBus`

可以按照业务场景需要来灵活的选择不同类型的MessageBus。

Kafka MessageBus同样也是需要配置资源的，需要配置KafkaProducer和KafkaConsumer。

**配置KafkaProducer**

```yaml
- class: playwell.kafka.KafkaProducerManager
  producers:
    - name: playwell
      bootstrap.servers: "localhost:9092"
      acks: 1
      retries: 3
      key.serializer: "org.apache.kafka.common.serialization.StringSerializer"
      value.serializer: "org.apache.kafka.common.serialization.StringSerializer"
```

Producer可以被全局共享，对于同一个Kafka集群，只需要配置一个Producer即可，每个Producer都有一个名称，供MessageBus进行引用。这里的参数，也都是KafkaProducer的参数。但需要注意的是，serializer类型目前版本只支持`org.apache.kafka.common.serialization.StringSerializer`，通过JSON格式传递消息，后续版本会添加更多格式支持。

**配置KafkaConsumer**

```yaml
- class: playwell.kafka.KafkaConsumerManager
  consumers:
    - name: activity_bus_0
      bootstrap.servers: "localhost:9092"
      group.id: playwell_activity
      key.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      value.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      enable.auto.commit: true
      max.poll.records: 5000
```

Consumer的配置也是直接可以使用KafkaConsumer的原生配置项，但需要注意的是，只有确定当前节点需要从KafkaMessageBus中读取消息的时候，才需要配置Consumer。另外Consumer的名称需要跟MessageBus的名称保持一致，在MessageBus初始化的时候，会检查当前节点有无跟自己同名的Consumer的配置，如果有，就会打开该Consumer从中获取数据，如果没有，那么就只能使用Producer写消息。

**配置KafkaTopicMessageBus**

```yaml
  message_bus_manager:
    class: playwell.message.bus.MySQLMessageBusManager
    datasource: playwell
    buses:
      - name: baas_service_bus
        class: playwell.message.bus.KafkaTopicMessageBus
        producer: playwell
        topic: baas_service
        commit_sync: true
```

* `producer` 使用的KafkaProducer
* `topic` 操作的Topic
* `commit_sync` 是否采用同步提交，默认为true。如果Producer已经配置了`enable.auto.commit`，那么该配置项会被忽略。如果不采用自动提交的方式，那么提交的时机是由应用自身来决定的，对于Playwell调度节点，是在每次事件循环中按批次进行提交，可以配置在读取消息完毕之后就提交，也可以配置处理消息完毕之后提交。通常，更建议在处理消息完毕之后再提交，这样可能会出现消息重复处理的情况，但Playwell会通过一些方式来避免消息被重复处理。

**配置KafkaPartitionMessageBus**

```yaml
  message_bus_manager:
    class: playwell.message.bus.MySQLMessageBusManager
    datasource: playwell
    buses:
      - name: activity_bus_0
        class: playwell.message.bus.KafkaPartitionMessageBus
        producer: playwell
        topic: playwell_activity
        partition: 0
```

相对于KafkaTopicMessageBus，KafkaPartitionMessageBus只多了一个配置项，即`partition`，指定要处理的分区。

