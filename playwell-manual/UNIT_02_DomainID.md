## Unit 2 Domain ID

在进行面向对象程序设计的时候，有一件很重要的事情，就是设计领域对象(Domain Model)。

领域对象用属性和方法描述了真实世界的实体所具备的属性和业务逻辑。其中最重要的一个属性，就是对象的唯一标识。例如，用户对象会有一个`user_id`，订单对象会有`order_id`。这些ID是引用对象的关键，用户通过这些ID来标识自己所请求的资源，应用程序再通过这些ID到数据库操作相应的记录。

同面向对象系统中会存在大量的Domain Model一样，Playwell中也会存在着大量的ActivityThread。这就产生了两个问题：

1. 如何把相关事件路由到具体的ActivityThread，因为每个ActivityThread都是顺序同步的处理事件，我们不可能把所有事件分别路由到每个ActivityThread进行匹配。
2. 使用什么样的方式来对系统中具体某个ActivityThread进行唯一标识。

Domain ID Strategy解决了这些问题，我们前面的都是通过一个名称，比如`user_id`来引用它，但它其实是由两个表达式组成的：第一个表达式是`cond_expression`，返回布尔值，对事件进行过滤；第二个表达式是`domain_id_expression`，用于从事件中提取Domain ID。

路由的过程，事件会先经过`cond_expression`，如果返回true，那么会通过`domain_id_expression`从事件属性中计算出Domain ID。若是该Domain ID对应的ActivityThread存在，那么会将事件加入到该ActivityThread的邮箱，该ActivityThread稍后也会被调度，处理自己邮箱中的事件；如果不存在，则会尝试去计算Trigger的触发条件，满足条件，会创建一个新的ActivityThread。

如果所有条件都不满足，则事件就会被忽略。

在Playwell中，Domain ID Strategy独立于ActivityDefinition，多个ActivityDefinition可以通过名称来引用同一个Domain ID Strategy。

使用客户端创建Domain ID Strategy：

```shell
playwell domain add --name 'user_id' \
  --cond_expression 'containsAttr("user_id")' \
  --domain_id_expression 'eventAttr("user_id")'
```

`containsAttr`函数可以用来判断事件中是否包含指定的属性，`eventAttr`函数可以获取事件中的某个属性值。

创建之后，我们就可以在ActivityDefinition中通过名称引用需要的`domain_id_strategy`了。

**Domain ID是字符串类型，如果`domain_id_expression`的返回值不是字符串，那么系统会自动进行toString处理，这一点需要注意**

可以构建基于多个事件属性的Domain ID Strategy

```shell
playwell domain add --name 'user_id_goods_id' \
  --cond_expression 'containsAttr("user_id") AND containsAttr("goods_id")' \
  --domain_id_expression 'str("ug_%d_%d", eventAttr("user_id"), eventAttr("goods_id"))'
```

上述示例中，事件要包含`user_id`和`goods_id`两个属性，然后会把这两个属性拼接成一个Domain ID，假设用户ID是10001，商品ID是123，那么拼接成的DomainID就是`ug_10001_123`。这样，同一个用户在同一件商品下的所有事件，都会被路由到Domain ID为`ug_10001_123`的ActivityThread。

**ActivityThread的定位**

同一个Activity下不会存在重复的Domain ID。

不同的Activity下可以存在重复的Domain ID。

比如，一个Activity是要在用户注册后发送一封欢迎邮件，另一个Activity是要增加注册计数，它们可以共用`user_id`这个Domain  ID Strategy，然后在同一个用户注册后，各自拥有独立执行的ActivityThread完成各自的业务逻辑，互不干扰。

**过滤条件应该根据业务尽可能的细化**

假设我们现在要创建一个跟踪新用户注册后是否完成引导的活动，这个活动使用的Domain ID Strategy为上文中的`user_id`，那么问题就来了，所有带有`user_id`的事件属性都会被路由到这个活动，比如像是"修改用户设置"、"提交订单"等等。而对于跟踪引导来说，我们并不关心这些事件，但因为Domain ID Strategy设置的宽泛，这些事件也会被路由到活动，这样每次都会查找对应的ActivityThread，并将事件写入邮箱，再引起ActivityThread被调度，虽然最后这些事件都会因为不满足trigger或receive单元的匹配条件被丢弃，但这样还是会造成不必要的性能损耗。

我们应该对Domain ID Strategy的过滤范围尽可能的细化，只让活动所需要的事件通过。有很多的方法，一种方法是通过事件名前缀，比如：

```shell
playwell domain add --name 'user_guide' \
  --cond_expression 'eventAttr("behavior", "").startsWith("引导_")' \
  --domain_id_expression 'eventAttr("user_id")'
```

凡是用户引导业务相关的事件，用户行为都会使用"引导_"这个前缀进行标识。

当然，也还可以专门添加一个事件属性来对业务标识：

```shell
playwell domain add --name 'user_guide' \
  --cond_expression 'eventAttr("biz", "") == "guide"'
  --domain_id_expression 'eventAttr("user_id")'
```

而有些需求可能是跨业务的，比如"用户完成了其中某个引导步骤，送他一个奖励，然后跟踪他有没有领取奖励"。这里面跨越了"引导"和"奖励"两个业务。这种情况就可以使用布尔条件，比如：

```shell
playwell domain add --name 'user_guide_award' \
  --cond_expression 'eventAttr("behavior") == "填写资料" OR eventAttr("behavior") == "使用奖励"' \
  --domain_id_expression 'eventAttr("user_id")'
```

或者，我们可以干脆把一个活动需要的事件都放入到一个集合当中：

```shell
playwell domain add --name 'user_guide_award' \
  --cond_expression 'list("填写资料", "使用奖励").contains(eventAttr("behavior"))' \
  --domain_id_expression 'eventAttr("user_id")'
```

**无论使用上述哪种方式，创建Domain ID Strategy时，都需要仔细检查四点：**

1. ActivityDefinition中所需要的事件，比如trigger和receive单元所监听的事件，是否全部能够被`cond_expression`覆盖，否则就会出现需要的事件尚未经过处理，就被Domain ID Strategy丢弃的情况。
2. 是否所有过滤后的事件，都可以被`domain_id_expression`提取出一致的Domain ID。比如我们要跟踪用户在某件商品下的行为，是否所有事件都拥有`user_id`和`goods_id`
3. Domain ID是否能提供业务所需要的唯一性。比如，我们要跟踪某个用户的引导行为，那么使用`user_id`即可。而如果要跟踪某个用户在某个商品下、某篇文章下的行为，那么唯一性就是两件事情之间的一种关系，此时就需要关联上其它的ID，比如`goods_id`、`blog_id`。
4. 筛选范围是否恰到好处，有没有过于宽泛。

通常，只有普通的事件，比如用户行为事件，才会被Domain ID Strategy路由，而其它系统类型的消息，比如服务请求和响应消息，是不需要路由的，它们自身的属性中带有了明确的Activity ID和Domain ID以及发起操作的Action信息，可以直接定位到目标。用户无需关心。

### 管理操作

#### 添加 / 修改

我们上文中已经使用了客户端添加了Domain ID，而如果要修改，只需要添加同名的Domain ID就可以了，系统会自动覆盖。

#### 查看

列出系统中所有的Domain ID Strategy

```shell
playwell domain get_all
```

#### 删除

通过名称进行删除：

```shell
playwell domain delete --name <name>
```

**不要随便进行删除操作，否则会有意想不到的错误产生。需要按照以下顺序删除Playwell中的元素：**

* 扫描并清理掉ActivityThread
* Kill掉Activity
* 删除掉ActivityDefinition
* 删除Domain ID Strategy

也就是当没有其它元素在引用的时候才进行删除。

