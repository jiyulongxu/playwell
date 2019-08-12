## Service

如果我们要为Playwell赋予一个宏大的愿景，那就是编排万物。而万物能够被编排的先决条件，就是要按照一定的规范，适配成为Service，并注册到Playwell当中。

Service的规范非常简单，只需要满足以下几个条件：

* 拥有一个可以被唯一引用的名称
* 拥有一个可以输入请求消息的MessageBus
* 能够处理请求消息，并计算结果
* 根据请求消息中的发送者标识，定位到请求者的MessageBus，然后向请求者返回包含计算结果的响应消息。

满足这些条件，就构成了一个服务。另外，不仅仅外接的组件被抽象为Service，Playwell调度节点也会将其自身注册成为一个Service。在Playwell中，凡是可以彼此相互通信的，必然都是Service。通过Service，可以定位到其绑定的MessageBus，进而通过MessageBus以请求和响应的方式进行通信。

下面，我们通过举例子，来描述Service的整个工作过程。

假设我们有一个Playwell调度节点，其注册的Service名称为`playwell_activity`，绑定的MessageBus为`activity_message_bus` ；另有一个邮件服务的节点，其注册的Service名称为`mail`，绑定的MessageBus为`mail_message_bus`。

它们的Service和MessageBus信息都是声明在配置文件当中的，在进程启动的时候，会将这些信息注册到元数据管理组件，接下来，它们就可以被整个集群引用了。

当调度节点执行到`mail`工作单元的时候，就会去元数据管理组件查找`mail`服务的相关信息，发现它所使用的MessageBus是`mail_message_bus`，于是将一个请求写入到该MessageBus；而`mail`服务的进程会不断从`mail_message_bus`读取请求，根据请求参数，发出邮件，然后再根据请求消息中的发送方信息，定位到调度节点的MessageBus `activity_message_bus` 返回发送结果。

### 请求 / 响应

请求 / 响应消息是Service之间通信的基石，它们都拥有统一规范的格式。

**请求消息**

```json
{
  "type": "req",
  "sender": "playwell_activity",
  "receiver": "mail",
  "attr": {
    "activity": 1,
    "domain": "10010",
    "action": "send_mail",
    "ignore_result": false,
    "args": {
      "receiver": "chihongze@gmail.com",
      "subject": "下雨天我怎么办，我好想你",
      "content": "不敢打给你，我怕你被雷劈。为什么打雷的声音，变得好熟悉。"
    }
  },
  "time": 1563937177517
}
```

请求消息字段说明：

* `type` 所有请求消息固定都是`req`类型，必须
* `sender` 发起请求的服务名称，通常是Playwell调度节点，必须
* `receiver` 接收请求的服务名称，必须
* `attr` 请求参数
  * `activity` 发起请求ActivityThread的Activity ID，必须
  * `domain` 发起请求ActivityThread的Domain ID，必须
  * `action` 发起请求ActivityThread的Action名称，必须
  * `ignore_result` 如果为true，那么可以忽略返回结果，服务可以不必传输响应结果。
  * `args` 服务请求参数，由服务自身决定，比如，对于邮件，就是收件人、标题、正文等等。可选
* `time` 消息产生时间，必须

通常，请求消息是由Playwell调度器发出的，外接Service只需要关注`attr`中的`args`参数，以及通过`sender`查询请求者的相关信息，返回响应消息。

而像`activity`、`domain`、`action`等参数，主要是起一个定位的作用，可以让调度器知道是哪个ActivityThread哪个Action的消息，这样才可以将消息路由到正确的位置。因此，服务在返回响应消息的时候，要原封不动的把这些捎带上。

**响应消息**

```json
{
  "type": "res",
  "sender": "mail",
  "receiver": "playwell_activity",
  "attr": {
    "activity": 1,
    "domain": "10010",
    "action": "send_mail",
    "status": "ok",
    "error_code": "",
    "message": "",
    "data": {
      
    }
  },
  "time": 1563937177518
}
```

响应消息字段说明：

* `type` 所有响应消息固定都是`res`类型，必须
* `sender` 返回响应消息的服务名称，必须
* `receiver` 接收响应消息的服务名称，必须
* `attr `响应参数
  * `activity` 发起请求ActivityThread的Activity ID，必须
  * `domain` 发起请求ActivityThread的Domain ID，必须
  * `action` 发起请求ActivityThread的Action名称，必须
  * `status` 结果状态码，必须
    * `ok` 处理请求成功
    * `fail` 处理请求失败
    * `timeout` 处理请求超时
  * `error_code` 错误状态码，当`status`为`fail`的时候，可通过错误状态码来描述具体的错误原因。具体的错误码由服务自行定义。在`status`为`fail`时必须
  * `message` 提示消息，用于详细描述结果，可选
  * `data`返回数据。查询和计算类型的服务需要返回一些结果，在此字段中携带这些结果。可选

**响应消息和结果函数**

ActivityThread的执行要依据Service所返回的响应消息进行决策，而这一切都要通过内置的结果函数进行。这里，我们将介绍结果函数是如何与响应消息对应起来的。

* `resultOk()`  返回`status`的值是否为`ok`
* `resultFailure()` 返回`status`的值是否为`fail`
* `resultTimeout() `返回`status`的值是否为`timeout`
* `errorCode()` 返回`error_code`字段的值
* `allResultVars()`以`Map<String, Object>`的形式返回整个`data`字段的值
* `resultVar(String key)` 返回`data`中指定key的值
* `resultVar(String key, Object defaultValue)` 返回`data`中指定key的值，如果key不存在，则返回默认值。

### 开发一个Service

下面，我们将使用Python的Bottle框架(一款非常简单的web框架 )，来介绍开发一个Service的过程，并将其应用在工作单元当中。

```python
import time
import logging
from collections import defaultdict
from urllib.parse import urljoin
from concurrent.futures import ThreadPoolExecutor

import requests
from bottle import (
    run,
    post,
    request
)


PLAYWELL_API = "http://localhost:1922"
executor = ThreadPoolExecutor(max_workers=4)

@post("/add")
def add():
    """从Http Request中，接收请求消息数组，然后将消息转发给worker处理
    """
    request_messages = request.json
    executor.submit(_add, request_messages)
    return {"status": "ok"}


def _add(request_messages):
    # 计算结果并包装响应消息
    all_response_messages = defaultdict(list)
    for req in request_messages:
        res = {
            "type": "res",
            "sender": req["receiver"],
            "receiver": req["sender"],
            "attr": {
                "activity": req["attr"]["activity"],
                "domain": req["attr"]["domain"],
                "action": req["attr"]["action"],
                "status": "ok",
                "data": {
                    "result": req["attr"]["args"]["a"] + req["attr"]["args"]["b"]
                }
            },
            "time": int(time.time() * 1000)
        }
        all_response_messages[req["sender"]].append(res)
    
    # 返回响应消息
    for service, messages in all_response_messages.items():
        try:
            # 获取请求服务的MessageBus路径
            message_bus_url = _get_message_bus_url(service)
            rs = requests.post(message_bus_url, json=messages)
            if rs.status_code != 200:
                logging.error(
                    "Write message bus error! The message bus url: %s, the status code: %d" % (
                        message_bus_url,
                        rs.status_code
                    )
                )
        except Exception as e:
            logging.exception(e)


def _get_message_bus_url(service_name):
    rs = requests.get(urljoin(PLAYWELL_API, "/v1/service_meta/%s" % service_name))
    if rs.status_code != 200:
        raise Exception(
            "Get service info error, API status code: %d" % rs.status_code)
    message_bus_name = rs.json()["data"]["message_bus"]
    rs = requests.get(
        urljoin(PLAYWELL_API, "/v1/message_bus/%s" % message_bus_name)
    )
    if rs.status_code != 200:
        raise Exception(
            "Get message bus info error, API status code: %d" % rs.status_code)
    result = rs.json()
    message_bus_class = result["data"]["class"]
    if message_bus_class != "playwell.message.bus.HttpMessageBus":
        raise Exception("Unsupport message bus type: '%s'" % message_bus_class)
    return result["data"]["config"]["url"]

def _register_self():
    rs = requests.post(
        urljoin(PLAYWELL_API, "/v1/message_bus/register"),
        json={
            "config": {
                "name": "test_add_bus",
                "class": "playwell.message.bus.HttpMessageBus",
                "url": "http://localhost:12345/add"
            }
        }
    )
    if rs.status_code != 200:
        raise Exception(
            "Register message bus error, the API status code %d" % rs.status_code)

    rs = requests.post(
        urljoin(PLAYWELL_API, "/v1/message_bus/open"),
        json={
            "name": "test_add_bus"
        }
    )
    if rs.status_code != 200 and rs.json()["error_code"] != "already_opened":
        raise Exception(
            "Open message bus error, the API status code: %d" % rs.status_code)

    rs = requests.post(
        urljoin(PLAYWELL_API, "/v1/service_meta/register"),
        json={
            "name": "test.add",
            "message_bus": "test_add_bus"
        }
    )
    if rs.status_code != 200:
        raise Exception(
            "Register service meta error, the API status code: %d" % rs.status_code)


if __name__ == "__main__":
    _register_self()
    run(host="localhost", port=12345, quiet=True)
```

启动该程序之后，首先`_register_self()`函数会将自身监听的web地址注册成一个MessageBus `test_add_bus`，并确保该MessageBus处于打开状态，然后注册Service信息。这样，Playwell就知道该怎样调用`test.add`这个服务。

接下来，会调用Bottle的API来启动web server，当Playwell要调用`test.add`服务的时候，会将请求消息通过`/add`这个Url path传递过来，add handler再将消息传递给处理线程，就立即返回。处理线程计算结果，构建响应消息，并将响应消息按照发送者归类。

最后，我们通过`_get_message_bus_url`函数，查找到发送者的MessageBus信息，然后也是批量传递响应消息。这里为了简化，只支持HTTP类型的MessageBus。

以上，就是一个Service的工作流程。

在ActivityDefinition中引用该服务：

```yaml
activity:
  name: test_add
  domain_id_strategy: cmd

  trigger:
    type: event
    args:
      condition: eventAttr("cmd") == "add"
    context_vars:
      a: eventAttr("a")
      b: eventAttr("b")

  actions:
    - name: test.add
      args:
        request:
          a: var("a")
          b: var("b")
        timeout: minutes(1)
      ctrl:
        - when: resultOk()
          context_vars:
            result: resultVar("result")
          then: call("stdout")
        - default: failBecause("call_add_error")

    - name: stdout
      args: var("result")
      ctrl: finish()
```

### Service Container

可以看到，上述示例中，绝大多数的代码都是样板代码，跟Service真正要完成的业务逻辑无关。为了减少这些重复逻辑和复杂性，我们可以构建框架，让Service只专注完成自己的业务就好了。

这样的框架，我们称其为Service Container。它能够自动完成MessageBus和服务的注册、构建以及返回响应消息等工作，只暴露出一个非常简单的Hook需要用户自行实现，即接收请求 -> 返回结果。

### 基本操作

#### 注册

