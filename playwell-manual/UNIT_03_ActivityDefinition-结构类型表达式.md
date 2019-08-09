## Unit 3  ActivityDefinition

ActivityDefinition使用专门的语法来定义工作流的触发条件和要执行的业务逻辑。目前语法分为两部分，一部分使用YAML固定了程序结构，另一部分使用表达式来动态计算执行时的参数。我们将在本章分别介绍这两部分的细节。

### 结构和表达式

#### 总体结构

```yaml
activity:
  name: <definition name>  # 名称
  domain_id_strategy: <domain id strategy name>  # 引用的Domain ID策略
  description: <description>  # 描述
  config:  # 配置常量
    item_1: value_1
    item_2:
      - a
      - b
      - c
      
  # 触发器
  trigger:
    type: event
    args:
      condition: <TRIGGER CONTEXT EXPRESSION>
    context_vars:
      var_a: <TRIGGER CONTEXT EXPRESSION>
      var_b: <TRIGGER CONTEXT EXPRESSION>
      var_c: <TRIGGER CONTEXT EXPRESSION>
      
   # 活动单元
   actions:
      - name: <action name>
        type: <action type name>
        args: <ACTION ARG CONTEXT EXPRESSION>
        ctrl:
          - when: <SCHEDULE CONTEXT EXPRESSION>
            then: <SCHEDULE CONTEXT EXPRESSION>
          - when: <SCHEDULE CONTEXT EXPRESSION>
            then: <SCHEDULE CONTEXT EXPRESSION>
          - default: <SCHEDULE CONTEXT EXPRESSION>
      
      - name: <action name>
        type: <action type name>
        args: <ACTION ARG CONTEXT EXPRESSION>
        ctrl: <SCHEDULE CONTEXT EXPRESSION>
```

以上就是ActivityDefinition的全貌，通过这样的固定结构，我们就能以一种"填空"的方式来编写代码。但需要注意的是，"填空"的内容分为两种，一种是YAML值，另一种是表达式。上面定义中，`<XXX EXPRESSION>`形式的都需要填写表达式，它们在创建和加载ActivityDefinition的时候会被编译，然后只有运行到它们的时候才会去根据上下文执行；而YAML值都是YAML中的基本类型：数字、字符串、布尔、列表、字典，这些值是固定的配置，加载之后就不会变动。

**特别需要注意的是字符串**

在需要YAML值的地方，我们可以直接使用字符串，比如像action的`name`和`type`属性：

```yaml
actions:
  - name: stdout
    type: stdout
    ...
```

但是，在需要表达式的地方，比如像是action的参数部分：

```yaml
actions:
  - name: stdout
    type: stdout
    args: "Hello, World"  # Error happened!
    ctrl: call("next")
```

这样写就会出错！因为表达式引擎会将`Hello, World`整体作为一个表达式进行编译，而这并不是一个正确的表达式。正确的写法应该是这样：

```yaml
actions:
  - name: stdout
    type: stdout
    args: str("Hello, World")
    ctrl: call("next")
```

`str`是一个构建字符串的内置函数，而当YAML解析器碰到引号的时候，引号并不在值的开头，此时会将引号也传递给表达式引擎，这样就可以编译为一个正确的表达式。本质上相当于：

```yaml
actions:
  - name: stdout
    type: stdout
    args: '"Hello, World"'
    ctrl: call("next")
```

而如果引号不位于开头，那就不需要这样转义：

```yaml
actions:
  - name: case
    type: case
    args:
      - when: var("gender") == "male"  # 不必非写成var("gender") == str("male")
        then: call("male_action")
      - when: var("gender") == "female"
        then: call("female_action")
      - default: call("unknown_action")
```

**某些内置函数只能在特定的上下文中才可以使用**

在上述的结构中，我们列出了几种类型的表达式，比如`<TRIGGER CONTEXT EXPRESSION>`、`<ACTION ARG CONTEXT EXPRESSION>`等等。之所以需要这样对其进行分类，是因为有些内置函数只有在特定的上下文中才可以使用。

* `<TRIGGER CONTEXT EXPRESSION>` 在trigger参数和上下文初始化部分使用该类型表达式。允许使用的函数类型：
  * 事件相关的函数，比如用`containsAttr(属性名)`来判断当前事件属性是否存在，用`eventAttr(属性名)`来获取当前事件属性的值
  * 活动配置相关的函数，比如用`config(配置项)`来获取配置项的值。
* `<ACTION ARG CONTEXT EXPRESSION>` 在Action参数中使用，允许使用的函数类型：
  * 事件相关函数，但通常只有个别类型的Action需要使用事件函数，比如等待事件发生的receive单元。
  * 活动配置相关函数
  * 上下文变量相关函数，比如可以通过`var(变量名称)`来获取上下文变量的值。
  * 与当前ActivityThread相关的内置变量
    * `activityId` 当前Activity的ID
    * `domainId` 当前ActivityThread的Domain ID
    * `activityThread` 当前ActivityThread的相关元数据
      * `status` - ActivityThread状态
      * `currentAction` - 当前执行到的单元
      * `createdOn` - 创建时间(字符串形式：yyyy-MM-dd HH:mm:ss)
* `<SCHEDULE CONTEXT EXPRESSION>` 在Action的控制条件中使用，允许使用的函数类型：
  * 活动配置相关函数
  * 上下文变量相关函数。
  * 服务结果相关函数，比如可以使用`resultOk()`来判断服务响应是否正常，使用`resultVar(变量名称)`来获取服务返回的某项结果值。
  * 与当前ActivityThread相关的内置变量。

#### 各部分结构解释

* `activity` 顶级结构，必须，声明这是一个ActivityDefinition

  * `name` 定义名称，需要在系统内唯一。**必须，接受YAML值**

  * `domain_id_strategy` 引用的Domain ID策略。**必须，接受YAML值**

  * `description` 定义的相关文字描述，相当于一段全局注释。**可选，接受YAML字符串**

  * `config` 默认配置项，可被Activity的配置覆盖。**可选，接受YAML字典，配置项均为静态的YAML值**

  * `trigger` 触发器，**必须**

    * `type` 触发器类型，目前只支持event类型
    * `args` 触发器参数
      * `condition` 触发条件 **必须，接受事件上下文的表达式**
    * `context_vars` 上下文变量初始化，**可选**，接受YAML字典形式的一系列KV，Value必须是**事件上下文表达式**

  * `actions` 工作单元，**YAML列表，必须**

    * `name` 单元名称，需要在ActivityDefinition中唯一。**必须，接受YAML值**

    * `type` 单元类型，描述了工作单元所执行的逻辑。**可选，如果不指定，则以name的值来查找类型，接受YAML值**

    * `args` 单元参数，不同的单元会要求不同的参数。可以指定参数上下文表达式或者YAML列表、字典。其中，YAML列表或字典的元素也需要是参数上下文表达式。可以理解为，所有参数需要的值，必须都得是表达式。

    * `ctrl` 控制条件，接受由YAML列表描述的控制条件。when和default都是可选的。当没有default的时候，when需要覆盖所有的结果条件，否则可能会在执行时因为无法覆盖的条件出现错误；而当没有when的时候，无论执行结果是什么，都是同样处理，并且这时候default是可省略的。

      ```yaml
      - name: stdout
        args: str("Hello, World")
        ctrl: call("next")
      ```

      相当于：

      ```yaml
      - name: stdout
        args: str("Hello, World")
        ctrl:
          - default: call("next")
      ```

#### 表达式

表达式方面，Playwell并没有去自己造轮子，而是默认集成了Spring EL，然后加入了自己的内置函数。因此，语法可以直接参考Spring EL的文档：https://docs.spring.io/spring/docs/4.3.10.RELEASE/spring-framework-reference/html/expressions.html

需要关心的是，Playwell在Spring EL的基础上，构建了许多内置函数。下面，我们将分类来介绍它们。

Playwell中的函数，分为通用函数和"基于某些上下文的函数"，通用函数就是在所有允许使用表达式的地方都可以使用的函数，而"基于某些上下文的函数"正如我们上文所列举的情况，只有在某些语义下才可以使用。

比如，我们不能在触发器中使用`var`函数来读取变量，因为触发阶段还没有任何变量；但对于字符串操作函数，却是通用的，无论在哪里都需要。

##### 数据类型

为了方便持久化和数据传输，Playwell目前版本仅支持整形、浮点、布尔、字符串、列表(java.util.List)和字典(java.util.Map)这几种简单的基本类型。但有时候会在业务中涉及到一些复杂的数据类型，比如时间和Decimal，当需要把这些类型的对象存储到上下文时，要注意先将其转化为字符串等基本类型，比如一个joda的`DateTime`对象，就需要转化成`"2012-12-01 23:00:00"` 这样的字符串行存储，当需要读取并进行一些复杂的时间计算操作的时候，再通过`dateTime(var("varName"), "yyyy-MM-dd HH:mm:ss")`转化成`DateTime`对象。

##### 通用函数

###### 字符串

* `isEmpty(String str)` 判断字符串是否为空，返回布尔值，空的定义包括了null和`""`的情况
* `isNotEmpty(String str)` isEmpty取反
* `str(String str)` 创建字符串对象，该函数通常只是用于避免YAML语法造成的歧义，将参数中的字符串原样返回，不会每次在堆中真的去new一个全新的String对象。
* `str(String format, Object arg1, Object args2, ...)` 格式化字符串。接受一个字符串描述的格式，以及填充位置的一系列参数，比如`str("你好，我叫%s，今年%d岁", "野原新之助", 5)` 就会返回"你好，我叫野原新之助，今年5岁" 。这里format的格式，是与Java中String.format一致，可以参考相关文档。
* `toStr(Object obj)` 将非字符串对象转化为字符串对象，比如`toStr(5)`会将5转化成为"5"。
* `split(String str, String delimiter)` 将字符串按照delimiter进行分割，返回一个`List<String>`类型的结果。

另外，除了以上的函数，我们还可以使用Java String自身的各种方法，比如：

```java
str("Wubba lubba dub dub!").toUpperCase()
```

就变成了大写的"WUBBA LUBBA DUB DUB!"

更多方法参见Java String API文档：https://docs.oracle.com/javase/8/docs/api/java/lang/String.html

###### 正则表达式

正则表达式函数都是以`regex`作为namespace前缀

* `regex.match(String regex, String text) ` 判断text是否满足正则表达式，返回布尔值。使用该函数，系统会自动缓存正则表达式的编译结果以提升性能，最多缓存500个，会通过LRU算法对使用率低的编译结果进行淘汰。所有正则相关的函数都会使用该缓存。
* `regex.group(String regex, String text, int groupIndex)` 当正则达成匹配的时候，提取某个group的值。比如`regex.group("^([a-zA-Z0-9]+)@gmail.com$", "chihongze@gmail.com", 1)` 会返回"chihongze"。而当groupIndex为0的时候，会返回整个字符串。需要注意，使用该函数，如果正则无法达成匹配，会抛出异常。
* `regex.groupAll(String regex, String text) ` 当正则达成匹配的时候，返回所有group组成的`List<String>`。比如`regex.groupAll("^([a-zA-Z0-9]+)@([a-z]+).com$", "chihongze@gmail.com")` 会返回`["chihongze@gmail.com", "chihongze", "gmail"]`。如果正则无法达成匹配，会返回空的List
* `regex.replaceFirst(String regex, String text, String replaceText)`  对text中满足正则的第一处文本进行替换。比如`regex.replaceFirst("chz", "chz is a good man, chz will be a cool man", "chihongze")`，执行结果为"chihongze is a good man, chz will be a coll man"。如果text中没有满足正则的部分，那么会被原样返回。
* `regex.replaceAll(String regex, String text, String replaceText)`对text中满足正则的所有部分进行替换。

###### 时间

Playwell封装了Joda Time进行时间相关的处理，可将用字符串表示的时间直接转化为Joda Time中相关对象，然后进一步执行相关操作。

* `dateTime()` 返回当前时间点的`org.joda.time.DateTime`对象。
* `dateTime(long timestamp)` 接受一个单位为毫秒的长整型时间戳，返回对应的`org.joda.time.DateTime`对象。
* `dateTime(String text)` 将字符串text转化为`org.joda.time.DateTime`对象，text需要满足格式："yyyy-MM-dd HH:mm:ss"
* `dateTime(String text, String format)` 将字符串text按照format指定的格式转换为`org.joda.time.DateTime`对象。
* `localDate()` 返回当前时间点的`org.joda.time.LocalDate`对象
* `localDate(String text) ` 将字符串text转化为`org.joda.time.LocalDate`对象，text需要满足格式"yyyy-MM-dd"
* `localDate(String text, String format) ` 将字符串text按照format所指定的格式，转化为`org.joda.time.LocalDate`对象。
* `localTime()` 返回当前时间点的`org.joda.time.LocalTime`对象
* `localTime(String text)` 将字符串text转化为`org.joda.time.LocalTime`对象，text需要满足格式"HH:mm:ss"
* `localTime(String text, String format)` 将字符串text按照format所指定的格式，转化为`org.joda.time.LocalTime`对象。
* `timestamp()`返回当前以毫秒为单位的时间戳
* `year()` 返回当前时间点的年份，相当于`dateTime().year()`
* `month()`返回当前时间点的月份，相当于`dateTime().getMonthOfYear()`
* `day()`返回当前时间点的日期，相当于`dateTime().getDayOfMonth()`
* `hour()`返回当前时间点的小时数，相当于`dateTime().getHourOfDay()`
* `minute()` 返回当前时间点的分钟数，相当于`dateTime().getMinuteOfHour()`
* `second()`返回当前时间点的秒数，相当于`dateTime().getSecondOfMinute()`

只要将时间转化为Joda Time对象，各种操作就非常简单灵活了。

对时间按照单位加减：

```java
dateTime("1989-12-04 12:00:00").plusMonths(1)
```

```java
dateTime("1989-12-04 12:00:00").minusMonths(1)
```

获取更加精细的时间属性：

```java
dateTime("1989-12-04 12:00:00").year()
```

```
dateTime("1989-12-04 12:00:00").getMonthOfYear()
```

比较时间：

```
dateTime(var("dt")).isAfter(dateTime(config("begin_time"))) AND 
dateTime(var("dt")).isBefore(dateTime(config("end_time")))
```

更多操作请参见Joda API文档：https://www.joda.org/joda-time/index.html

**再次重申，特别注意，因为Playwell上下文只能存储基本类型，所以，如果要将Joda操作的结果写入到上下文，必须要将其转化成字符串；读取的时候，再通过dateTime等函数转换成DateTime对象**

```yaml
# 存储
- name: action_a
  args:
   ...
  ctrl:
    - when: resultOk()
      context_vars:
        # 存入上下文的时候，要使用toString(String format)转化为字符串
        my_dt: dateTime(resultVar("datetime")).toString("yyyy-MM-dd HH:mm:ss")
      then: call("case")
  
- name: case
  args:
    # 从上下文读出的时候要重新将字符串解析为Joda对象
    - when: dateTime(var("my_dt")).isAfterNow()
      then: call("next")
```

###### math

数学函数的命名空间是`math`，通过该命名空间，可以直接使用`java.lang.Math`下的各种工具方法，比如：

```
math.abs(var("a"))
math.floor(3.14)
```

详情参见：https://docs.oracle.com/javase/8/docs/api/java/lang/Math.html

###### list

Playwell中使用的list结构都是基于`java.util.List`，并在此基础之上额外封装了一些便于操作的函数。除了构建函数`list(Object arg1, Object arg2, …)`之外，其它函数都是基于`list ` namespace。

* `list(Object arg1, Object arg2, ...)` 返回`List<Object>`，基于传递的参数构建一个列表。
* `range(int startInclusive, int endExclusive)` 返回一个连续的整数列表：`[startInclusive, endExclusive)`
* `range(int endExclusive) ` 返回一个连续的整数列表：`[0, endExclusive)`
* `list.add(List<Object> oldList, Object element)` 采用Copy On Write的方式基于oldList添加一个新元素，并返回添加后的新列表。**为什么要采用Copy On Write的方式？因为Playwell是面向抽象来操作集合，并不确定oldList的具体实现，有可能oldList是只读的(比如当集合没有元素的时候，有些函数会返回Collections.emptyList())，为了保证安全，以下，Playwell对所有集合的写操作函数(增、删、改)都是基于Copy On Write的，复制旧的集合到新集合，对新集合进行修改，返回新集合。**
* `list.add(List<Object> oldList, int index, Object element)` 在指定的索引位置添加一个新元素，如果索引不合法，会抛出异常。
* `list.addDistinct(List<Object> oldList, Object element)` 去重式添加，如果元素在列表中已经存在，则不再添加，如果不存在，则添加到列表的末尾。这种去重式添加在很多业务中非常有用，比如判断当用户完成了一系列行为后，触发某次操作，我们就可以用去重式添加，这样每次只要判断一下列表中元素的数目是否满足即可。
* `list.removeByIndex(List<Object> oldList, int index)` 根据索引删除指定的元素，如果索引不合法，会出现异常。
* `list.removeByElement(List<Object> oldList, Object element)` 根据元素的值来删除集合中的元素。如果找不到指定的值，则会将集合原样返回。
* `list.distinct(List<Object> oldList)` 对列表元素进行去重后返回
* `list.set(List<Object> oldList, int index, Object element)` 修改某处索引的值后返回。
* `list.shuffle(List<Object> oldList)` 将列表中的值打散后返回。
* `list.sort(List<Object> oldList)` 将列表中的值按照自小到大的顺序排序后返回。
* `list.reverse(List<Object> oldList)` 将列表中元素的顺序进行翻转。
* `list.get(List<Object> oldList, int index)` 获取指定索引处的值，如果索引不存在，那么直接抛出异常。
* `list.get(List<Object> oldList, int index, Object defaultValue)` 获取指定索引处的值，如果索引不存在，那么会返回defaultValue。如果你不清楚索引是否合法，又不想抛出异常导致ActivityThread终止，那么可以使用该方法来指定一个默认值。
* `list.count(List<Object> list, Object element)` 返回列表中，值为element的元素出现的次数，比如：`list.count(list(21, 22, 21, 22, 23, 25), 21)` 就会返回2，值21总共出现了两次。
* `list.groupCount(List<Object> list)` 返回列表中各个元素所出现的次数，返回类型为`Map<Object, Integer>` ，其中Key为元素的值，Value为次数。`list.groupCount(list("Sam", "Jack", "Betty", "Sam", "Sam", "Betty"))` 会返回`{"Sam": 3, "Betty": 2, "Jack": 1}`
* `list.min(List<Comparable> list)` 返回列表中的最小值。`list.min(list(4, 5, 1, 3, 2))` 返回最小值1
* `list.max(List<Comparable> list)` 返回列表中的最大值。`list.max(list(4, 5, 1, 3, 2))` 返回最大值5
* `list.sumInt(List<Integer> list)` 对整数列表进行求和。`list.sumInt(list(1, 2, 3)` 返回6
* `list.sumDouble(List<Double> list)` 对数字列表进行求和，返回双精度浮点数。
* `list.avg(List<Number> numbers)` 返回列表中的平均数，`list.avg(list(1, 2, 3))` 返回2
* `list.randomChoice(List<Object> list)` 从列表中随机选择一个元素。这在AB测试的时候非常有用，可以把几个方案放在列表中，每次随机选择一个方案。
* `list.join(List<Object> list, String delimiter)` 使用分隔符来拼接字符串，比如`list.join(list("苟利国家生死以", "岂因祸福避趋之"), "，")` 会返回"苟利国家生死以，岂因祸福避趋之"

###### map

playwell中的map结构都是基于`java.util.Map`。除了构建函数`map(Object key1, Object value1, Object key2, Object value2...)` 之外，其余的都是基于`map` namespace。

* `map(Object key1, Object value1, Object key2, Object value2, ...)` 构建一个map对象
* `map.put(Map<Object, Object> oldMap, Object key, Object value)` 通过Copy on write的方式来添加一对新的KV，并返回修改后的Map。以下涉及到修改操作的函数均通过Copy on write的方式进行。
* `map.removeByKey(Map<Object, Object> oldMap, Object key)` 删除指定的key，如果key不存在，原样返回
* `map.removeByValue(Map<Object, Object> oldMap, Object value)` 删除所有value与指定value相等的记录。
* `map.update(Map<Object, Object> oldMap, Map<Object, Object> newMap)` 用newMap的值覆盖oldMap的既有值，比如`map.update(map("a", 1, "b", 2, "c", 3), map("a", 5, "x", 19))` 会返回 {"a": 5, "b": 2, "c": 3, "x": 19} 

###### counter

在实际业务当中，我们经常需要将事物进行分类计数。Playwell提供了计数器及相关函数来应对这种场景，所谓的计数器，就是一个类型为`Map<Object, Integer>`的集合，其中key为计数项，value为发生次数。

* `counter(Object argA, Object argB, ...)` 基于参数返回一个计数器，比如`counter("Sam", "Jack", "Sam")`会返回`{"Sam": 2, "Jack": 1}`
* `counter.incr(Map<Object, Integer> counter, Object item, int num)` 对counter的item项进行递增后返回。比如`counter.incr(map("Sam", 2, "Jack", 1), "Jack", 2)` 就会返回`{"Sam": 2, "Jack": 3}`
* `counter.decr(Map<Object, Integer> counter, Object item, int num)` 对counter的item项进行递减后返回。
* `counter.get(Map<Object, Integer> counter, Object item)` 返回counter item项的值，如果不存在，则返回0。
* `counter.mostCommon(Map<Object, Long> counter, int n)` 返回计数最大的前N项，返回值为`List<Map.Entry<Object, Long>>` 。

###### random

* `random()` 返回一个`[0, 1)`之间的随机浮点数。
* `randInt(int origin, int bound)` 返回一个`[origin, bound)` 之间的随机整数。
* `randInt(int bound)` 返回一个`[0, bound)` 之间的随机整数。
* `randomBoolean(double rate)` 按照一定的概率来返回布尔值，例如`randomBoolean(0.8)`就有80%的概率返回true值。 

###### bool

* `all(boolean value1, boolean value2, boolean value3, ...)` 只有当所有参数为true的时候，才会返回true
* `any(boolean value1, boolean value2, boolean value3, ...)` 只要其中一个参数的值为true，就会返回true。

###### Decimal

* `toDecimal(Object value)`  可将字符串、整形、浮点转化为`java.util.BigDecimal`对象进行精确计算，比如`toDecimal("3.14")` 相当于`new BigDecimal("3.14")`
* `toDecimal(Object value, int scale, String roundingMode)` 将字符串、整形、浮点转化为`java.util.BigDecimal`对象，并指定保留小数位数。比如`toDecimal(3.141592654, 3, "FLOOR")`的结果就是`3.14` 。

##### 配置函数

配置函数用于读取Activity的配置常量。在触发器参数、Action参数以及ctrl控制逻辑均可使用。

* `config(String itemName)` 获取某个配置项的值，如果不存在，则会抛出异常。
* `config(String itemName, Object defaultValue)` 获取某个配置项的值，如果不存在，则返回defaultValue，如果不确定某个配置项是否存在，则最好使用该函数获取。

##### 事件函数

事件函数用于获取当前处理事件的相关属性。在触发器参数、与事件相关的Action参数(比如receive)可以使用。

* `eventType()` 获取当前事件类型
* `allEventAttributes()` 以`Map<String, Object>`的形式返回所有事件属性
* `eventTypeIs(String targetType)` 判断当前事件类型是否是targetType，返回布尔值。
* `eventAttr(String attrName)` 获取事件属性，如果不存在，则会抛出异常
* `eventAttr(String attrName, Object defaultValue)` 获取事件属性，如果不存在，则返回默认值。在不确定属性是否存在的情况下，应该尽可能考虑使用该函数。
* `containsAttr(String attrName)` 判断事件属性是否存在，返回布尔值
* `isAttrEmpty(String attrName)` 判断事件属性是否为空，空的定义为不存在或null或空字符串。
* `isAttrNotEmpty(String attrName)` 对`isAttrEmpty`取反 

##### 上下文变量函数

用于读取上下文变量的值。可应用在Action参数以及ctrl控制逻辑。

* `allVars()` 以`Map<String, Object>`的形式返回所有的上下文变量。
* `var(String varName)`  通过名称来获取上下文变量的值，如果变量名不存在，则抛出异常。
* `var(String varName, Object defaultValue)` 通过名称来获取上下文变量，如果变量名不存在，则返回默认值。如果不确定变量是否存在，应该尽可能使用该函数。

##### 返回结果函数

返回结果函数用于读取服务的返回结果。可应用在ctrl控制逻辑中。

* `allResultVars()` 以`Map<String, Object>`的形式返回所有的结果变量。

* `resultOk()` 返回结果状态是否为成功
* `resultFailure()` 返回结果状态是否为失败
* `resultTimeout()` 返回结果状态是否为超时
* `resultVar(String varName)` 获取指定的结果变量，如果变量不存在，抛出异常
* `resultVar(String varName, Object defaultValue)` 获取指定的结果变量，如果变量不存在，返回默认值。 

