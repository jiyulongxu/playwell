# 控制结构

## Trigger

### 完整表达形式

```yaml
trigger:
	type: <Trigger Type>
	args:
		condition: ${...}
	context_vars:
		var_a: ${...}
		var_b: ${...}
		var_c: ${...}
```

#### 声明

* type 触发器类型
* args 触发器参数表达式，允许字典和列表两种形式，可以使用的内置变量为ACTIVITY、DEFINITION、EVENT
* context\_vars: 初始化上下文变量表达式，只允许字典形式，k是变量名，v是变量值表达式，可以使用的内置变量为ACTIVITY、DEFINITION、EVENT/EVENTS

Trigger接收的数据形式是`Map<DomainID, Collection<Event>>`，即为每个DomainID所对应的事件列表。

Trigger完成两件事情：

1. 获取DomainID在该活动中已经存在的ActivityThread，并将该ActivityThread以及事件邮箱转发给调度器执行。
2. 如果DomainID在活动中没有对应的ActivityThread，那么判断当前事件是否满足匹配条件，并spawn一个新的ActivityThread，然后将剩余的未处理的事件转发给调度器执行。

### 内置Trigger - SimpleEventTrigger

```yaml
trigger:
	type: event
	args:
		condition: ${EVENT.getString("行为") == "注册成功"}
	context_vars:
		user_id: ${EVENT.getString("user_id")}
```

用于判断单个事件的匹配，以及多个事件之间的或者关系

args和`context_vars`可以使用的内置变量都是EVENT

