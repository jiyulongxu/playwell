# Playwell-Activity 测试要点整理

## Activity

### 创建新活动

* 正常创建活动
  * 覆盖ActivityDefinition的配置
  * 展示名称指定和不指定
* 指定的活动定义不存在
* 指定的活动定义存在，但是无可用版本
  * 所有版本enable都为false

### 暂停活动

* 正常暂停活动
  * 在暂停状态下触发新ActivityThread
  * 旧有的ActivityThread行为 `$schedule.pause_continue_old`
* 重复执行暂停操作

### 恢复活动

* 正常恢复已经暂停的活动
  * 恢复后的ActivityThread触发
* 恢复本来已经正常的活动

### 杀掉活动

* 正常杀掉活动
  * 杀掉之后新Thread不会再被触发
  * 旧Thread无法被触发
* 杀掉不存在的活动

### 修改活动配置

* 修改正常配置后Thread的执行
* 修改调度配置后Thread的执行