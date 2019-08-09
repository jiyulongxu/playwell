SET NAMES utf8;
SET FOREIGN_KEY_CHECKS = 0;

-- Activity Definition
create TABLE IF NOT EXISTS `activity_definition` (
  id INT(11) PRIMARY KEY AUTO_INCREMENT COMMENT "ID",
  name VARCHAR(255) NOT NULL COMMENT "引用名称",
  version VARCHAR(255) NOT NULL COMMENT "版本号",
  codec VARCHAR(255) NOT NULL COMMENT "编码",
  display_name VARCHAR(1000) DEFAULT "" COMMENT "展示名称",
  enable INT(2) DEFAULT 1 COMMENT "版本是否启用 0-不启用 1-启用",
  definition TEXT NOT NULL COMMENT "活动定义",
  created_on DATETIME NOT NULL COMMENT "创建时间",
  updated_on DATETIME NOT NULL COMMENT "最近更新时间",
  UNIQUE KEY (name, version)
) ENGINE=InnoDB, DEFAULT CHARSET=utf8;

-- Activity
create TABLE IF NOT EXISTS `activity` (
  id INT(11) PRIMARY KEY AUTO_INCREMENT COMMENT "ID",
  display_name VARCHAR(255) DEFAULT "" COMMENT "展示名称",
  definition_name VARCHAR(255) NOT NULL COMMENT "关联的活动定义",
  status INT NOT NULL COMMENT "0-正常 1-暂停 2-Killed",
  config TEXT NOT NULL COMMENT "活动配置信息",
  created_on DATETIME NOT NULL COMMENT "创建时间",
  updated_on DATETIME NOT NULL COMMENT "最近更新时间"
) ENGINE=InnoDB, DEFAULT CHARSET=utf8;

-- Compare and callback
create TABLE IF NOT EXISTS `compare_and_callback` (
  id INT(11) PRIMARY KEY AUTO_INCREMENT COMMENT "ID",
  item varchar(255) NOT NULL UNIQUE KEY COMMENT "项目标记",
  version int(20) NOT NULL COMMENT "递增版本号",
  updated_on DATETIME NOT NULL COMMENT "最近更新时间"
) ENGINE=InnoDB, DEFAULT CHARSET=utf8;

-- Domain ID Strategy
create TABLE IF NOT EXISTS `domain_id_strategy` (
  name VARCHAR(255) PRIMARY KEY COMMENT "策略名称",
  cond_expr TEXT NOT NULL COMMENT "判断条件",
  domain_id_expr TEXT NOT NULL COMMENT "DomainID表达式",
  updated_on DATETIME NOT NULL COMMENT "更新时间",
  created_on DATETIME NOT NULL COMMENT "创建时间"
) ENGINE=InnoDB, DEFAULT CHARSET=utf8;

-- Message Bus
create TABLE IF NOT EXISTS `message_bus` (
  name VARCHAR(255) PRIMARY KEY COMMENT "名称",
  clazz TEXT NOT NULL COMMENT "MessageBus class",
  opened INT(2) NOT NULL COMMENT "0 - 关闭 1 - 开启",
  config TEXT NOT NULL COMMENT "配置信息"
) ENGINE=InnoDB, DEFAULT CHARSET=utf8;

-- Service Meta
create TABLE IF NOT EXISTS `service_meta` (
  name VARCHAR(255) PRIMARY KEY COMMENT "服务名称",
  message_bus VARCHAR(255) NOT NULL COMMENT "使用的Message Bus",
  config TEXT NOT NULL COMMENT "配置"
) ENGINE=InnoDB, DEFAULT CHARSET=utf8;

-- Slots
create TABLE IF NOT EXISTS `slots` (
  slot INT(11) PRIMARY KEY COMMENT "slot",
  service VARCHAR(255) NOT NULL COMMENT "slot对应的ActivityRunner Service"
) ENGINE=InnoDB, DEFAULT CHARSET=utf8;

-- Migration Plan
create TABLE IF NOT EXISTS `migration_plan` (
  pk INT PRIMARY KEY COMMENT "PK",
  message_bus VARCHAR(1000) NOT NULL COMMENT "迁移需要使用的Message Bus class",
  input_message_bus_config TEXT NOT NULL COMMENT "迁移需要使用的输入MessageBus配置",
  output_message_bus_config TEXT NOT NULL COMMENT "迁移需要使用的输出MessageBus配置",
  slots_distribution TEXT NOT NULL COMMENT "本次迁移的目标slots分布",
  comment TEXT NOT NULL COMMENT "迁移描述",
  created_on DATETIME NOT NULL COMMENT "迁移开始时间"
) ENGINE=InnoDB, DEFAULT CHARSET=utf8;

-- Migration Progress
create TABLE IF NOT EXISTS migration_progress (
  status INT NOT NULL COMMENT "0 - 尚未开始 1 - 正在迁移 2 - 迁移完毕",
  slots TEXT NOT NULL COMMENT "涉及到的slots列表",
  output_node VARCHAR(255) NOT NULL COMMENT "输出节点",
  input_node VARCHAR(255) NOT NULL COMMENT "输入节点",
  output_latest_key TEXT NOT NULL COMMENT "输出节点最后输出的Key",
  input_latest_key TEXT NOT NULL COMMENT "输入节点最后输入的Key",
  output_finished BOOL NOT NULL COMMENT "输出节点执行完毕",
  input_finished BOOL NOT NULL COMMENT "输入节点执行完毕",
  begin_time DATETIME DEFAULT NULL COMMENT "开始执行时间",
  end_time DATETIME DEFAULT NULL COMMENT "结束执行时间",
  UNIQUE KEY(output_node, input_node)
) ENGINE=InnoDB, DEFAULT CHARSET=UTF8;
