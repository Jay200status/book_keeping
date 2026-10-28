-- 记账小程序数据库（MySQL 8.0.16+）
-- 约定：金额使用 DECIMAL；时间由服务端统一写入，建议连接时区固定为 Asia/Shanghai。
CREATE DATABASE IF NOT EXISTS book_keeping
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE book_keeping;

CREATE TABLE app_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  union_id VARCHAR(64) COLLATE utf8mb4_bin NULL COMMENT '同一微信开放平台下的 UnionID；无法获取时为空',
  display_name VARCHAR(100) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_user_union_id (union_id),
  CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED', 'CANCELLED'))
) ENGINE=InnoDB COMMENT='小程序用户';

CREATE TABLE wechat_identity (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  app_type VARCHAR(20) NOT NULL COMMENT 'MINI_PROGRAM 或 OFFICIAL_ACCOUNT',
  app_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  open_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_wechat_app_open (app_id, open_id),
  KEY idx_wechat_identity_user (user_id),
  CONSTRAINT fk_wechat_identity_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT ck_wechat_identity_type CHECK (app_type IN ('MINI_PROGRAM', 'OFFICIAL_ACCOUNT'))
) ENGINE=InnoDB COMMENT='公众号和小程序的微信身份';

CREATE TABLE account_book (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  owner_user_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(100) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_account_book_owner (owner_user_id),
  CONSTRAINT fk_account_book_owner FOREIGN KEY (owner_user_id) REFERENCES app_user (id)
) ENGINE=InnoDB COMMENT='账本；首版每位用户可使用默认账本';

CREATE TABLE category (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  book_id BIGINT UNSIGNED NOT NULL,
  type VARCHAR(8) NOT NULL COMMENT 'INCOME 或 EXPENSE',
  name VARCHAR(50) NOT NULL,
  icon VARCHAR(32) NOT NULL DEFAULT '•',
  sort_order INT NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_category_book_type_name (book_id, type, name),
  UNIQUE KEY uk_category_book_type_id (book_id, type, id),
  CONSTRAINT fk_category_book FOREIGN KEY (book_id) REFERENCES account_book (id),
  CONSTRAINT ck_category_type CHECK (type IN ('INCOME', 'EXPENSE'))
) ENGINE=InnoDB COMMENT='收支分类；停用后保留历史记录';

CREATE TABLE finance_account (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  book_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(50) NOT NULL COMMENT '例如现金、微信钱包、银行卡',
  kind VARCHAR(10) NOT NULL DEFAULT 'ASSET' COMMENT 'ASSET 或 LIABILITY',
  opening_balance DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_finance_account_book_name (book_id, name),
  UNIQUE KEY uk_finance_account_book_id (book_id, id),
  CONSTRAINT fk_finance_account_book FOREIGN KEY (book_id) REFERENCES account_book (id),
  CONSTRAINT ck_finance_account_kind CHECK (kind IN ('ASSET','LIABILITY'))
) ENGINE=InnoDB COMMENT='资金账户；余额由期初余额和收支记录计算';

CREATE TABLE binding_code (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  code_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '服务端密钥参与计算的摘要，不保存明文绑定码',
  expires_at DATETIME(3) NOT NULL,
  consumed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_binding_code_digest (code_digest),
  KEY idx_binding_code_user (user_id),
  KEY idx_binding_code_expires (expires_at),
  CONSTRAINT fk_binding_code_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB COMMENT='无法取得 UnionID 时的一次性公众号绑定码';

CREATE TABLE api_session (
  token_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '随机令牌的 SHA-256 摘要，不保存明文',
  user_id BIGINT UNSIGNED NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (token_digest),
  KEY idx_api_session_user (user_id),
  KEY idx_api_session_expires (expires_at),
  CONSTRAINT fk_api_session_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB COMMENT='小程序服务端会话';

CREATE TABLE official_message (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  official_app_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  wechat_msg_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  sender_open_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  user_id BIGINT UNSIGNED NULL COMMENT '未绑定时为空，消息留待处理',
  content TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED',
  parse_result JSON NULL COMMENT '解析候选结果，供管理端确认或修正',
  error_message VARCHAR(500) NULL,
  received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  processed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_official_message_dedup (official_app_id, wechat_msg_id),
  KEY idx_official_message_sender (official_app_id, sender_open_id, received_at),
  KEY idx_official_message_status (status, received_at),
  KEY idx_official_message_user (user_id, received_at),
  CONSTRAINT fk_official_message_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT ck_official_message_status CHECK
    (status IN ('RECEIVED', 'POSTED', 'NEEDS_BINDING', 'NEEDS_REVIEW', 'FAILED', 'IGNORED', 'BOUND'))
) ENGINE=InnoDB COMMENT='公众号收到的文字消息；消息 ID 用于防止重复入账';

CREATE TABLE bookkeeping_record (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  book_id BIGINT UNSIGNED NOT NULL,
  type VARCHAR(8) NOT NULL,
  amount DECIMAL(18, 2) NOT NULL COMMENT '正数；收支方向由 type 表示',
  purpose VARCHAR(40) NOT NULL COMMENT '用户填写或公众号消息解析出的用途',
  currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY',
  category_id BIGINT UNSIGNED NOT NULL,
  account_id BIGINT UNSIGNED NULL COMMENT '首版允许不指定资金账户',
  occurred_at DATETIME(3) NOT NULL COMMENT '实际发生时间',
  note VARCHAR(500) NULL,
  source VARCHAR(16) NOT NULL COMMENT 'MINI_PROGRAM、OFFICIAL_ACCOUNT 或 ADMIN',
  created_by_user_id BIGINT UNSIGNED NULL,
  source_message_id BIGINT UNSIGNED NULL,
  deleted_at DATETIME(3) NULL COMMENT '软删除，避免误删后无法恢复',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_record_source_message (source_message_id),
  KEY idx_record_book_time (book_id, occurred_at, id),
  KEY idx_record_book_type_time (book_id, type, occurred_at),
  KEY idx_record_book_account_time (book_id, account_id, occurred_at),
  CONSTRAINT fk_record_book FOREIGN KEY (book_id) REFERENCES account_book (id),
  CONSTRAINT fk_record_category FOREIGN KEY (book_id, type, category_id)
    REFERENCES category (book_id, type, id),
  CONSTRAINT fk_record_account FOREIGN KEY (book_id, account_id)
    REFERENCES finance_account (book_id, id),
  CONSTRAINT fk_record_source_message FOREIGN KEY (source_message_id)
    REFERENCES official_message (id),
  CONSTRAINT fk_record_creator FOREIGN KEY (created_by_user_id) REFERENCES app_user(id),
  CONSTRAINT ck_record_type CHECK (type IN ('INCOME', 'EXPENSE')),
  CONSTRAINT ck_record_amount CHECK (amount > 0),
  CONSTRAINT ck_record_source CHECK (source IN ('MINI_PROGRAM', 'OFFICIAL_ACCOUNT', 'ADMIN'))
) ENGINE=InnoDB COMMENT='收入和支出记录；一条公众号消息最多生成一条记录';

CREATE TABLE admin_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL COMMENT '由服务端密码哈希算法生成，不能保存明文密码',
  role VARCHAR(16) NOT NULL DEFAULT 'ADMIN',
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_user_username (username),
  CONSTRAINT ck_admin_user_role CHECK (role IN ('ADMIN', 'OPERATOR'))
) ENGINE=InnoDB COMMENT='Vue 管理端登录账号';

CREATE TABLE admin_session (
  token_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  admin_user_id BIGINT UNSIGNED NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (token_digest),
  KEY idx_admin_session_user (admin_user_id),
  KEY idx_admin_session_expires (expires_at),
  CONSTRAINT fk_admin_session_user FOREIGN KEY (admin_user_id) REFERENCES admin_user (id)
) ENGINE=InnoDB COMMENT='管理端会话；只存令牌摘要';

CREATE TABLE budget_rule (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  book_id BIGINT UNSIGNED NOT NULL,
  month DATE NOT NULL COMMENT '每月一号',
  category_id BIGINT UNSIGNED NULL COMMENT '为空表示月度总预算',
  category_key BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(category_id, 0)) STORED,
  amount DECIMAL(18,2) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_budget_scope (book_id, month, category_key),
  CONSTRAINT fk_budget_book FOREIGN KEY (book_id) REFERENCES account_book(id),
  CONSTRAINT fk_budget_category FOREIGN KEY (book_id, category_id) REFERENCES category(book_id, id),
  CONSTRAINT ck_budget_amount CHECK (amount > 0)
) ENGINE=InnoDB COMMENT='月度总预算与分类预算';

CREATE TABLE reminder_rule (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  kind VARCHAR(16) NOT NULL COMMENT 'BOOKKEEPING 或 DUE',
  title VARCHAR(80) NOT NULL,
  due_at DATETIME(3) NOT NULL,
  recurrence VARCHAR(8) NOT NULL COMMENT 'ONCE、DAILY、MONTHLY',
  lead_days SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_reminder_user (user_id, is_active),
  CONSTRAINT fk_reminder_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT ck_reminder_kind CHECK (kind IN ('BOOKKEEPING','DUE')),
  CONSTRAINT ck_reminder_recurrence CHECK (recurrence IN ('ONCE','DAILY','MONTHLY'))
) ENGINE=InnoDB COMMENT='记账及账单到期提醒规则';

CREATE TABLE recurring_bill_template (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  book_id BIGINT UNSIGNED NOT NULL,
  type VARCHAR(8) NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  purpose VARCHAR(40) NOT NULL,
  category_id BIGINT UNSIGNED NOT NULL,
  note VARCHAR(500) NULL,
  day_of_month TINYINT UNSIGNED NOT NULL,
  start_month DATE NOT NULL,
  end_month DATE NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_recurring_book (book_id, is_active),
  CONSTRAINT fk_recurring_book FOREIGN KEY (book_id) REFERENCES account_book(id),
  CONSTRAINT fk_recurring_category FOREIGN KEY (book_id, type, category_id) REFERENCES category(book_id, type, id),
  CONSTRAINT ck_recurring_type CHECK (type IN ('INCOME','EXPENSE')),
  CONSTRAINT ck_recurring_amount CHECK (amount > 0),
  CONSTRAINT ck_recurring_day CHECK (day_of_month BETWEEN 1 AND 31)
) ENGINE=InnoDB COMMENT='每月自动生成待确认账单的模板';

CREATE TABLE recurring_bill_occurrence (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  template_id BIGINT UNSIGNED NOT NULL,
  month DATE NOT NULL,
  scheduled_on DATE NOT NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
  record_id BIGINT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_recurring_month (template_id, month),
  CONSTRAINT fk_occurrence_template FOREIGN KEY (template_id) REFERENCES recurring_bill_template(id),
  CONSTRAINT fk_occurrence_record FOREIGN KEY (record_id) REFERENCES bookkeeping_record(id),
  CONSTRAINT ck_occurrence_status CHECK (status IN ('PENDING','POSTED','DISMISSED'))
) ENGINE=InnoDB COMMENT='周期账单的待确认实例';

CREATE TABLE subscription_permission (
  user_id BIGINT UNSIGNED NOT NULL,
  template_id VARCHAR(128) NOT NULL,
  credits INT UNSIGNED NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, template_id),
  CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB COMMENT='小程序一次性订阅授权次数';

CREATE TABLE notification_event (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  event_key VARCHAR(100) NOT NULL,
  title VARCHAR(80) NOT NULL,
  content VARCHAR(200) NOT NULL,
  trigger_at DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
  sent_at DATETIME(3) NULL,
  error_message VARCHAR(200) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_event (event_key),
  KEY idx_notification_pending (status, trigger_at, expires_at),
  CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT ck_notification_status CHECK (status IN ('PENDING','SENDING','SENT','NO_PERMISSION','FAILED'))
) ENGINE=InnoDB COMMENT='超支及到期通知的去重与发送状态';

CREATE TABLE book_member (
  book_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  role VARCHAR(8) NOT NULL,
  joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (book_id,user_id),
  KEY idx_book_member_user (user_id),
  CONSTRAINT fk_book_member_book FOREIGN KEY (book_id) REFERENCES account_book(id),
  CONSTRAINT fk_book_member_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT ck_book_member_role CHECK (role IN ('EDITOR','VIEWER'))
) ENGINE=InnoDB COMMENT='家庭或团队账本成员';

CREATE TABLE book_invitation (
  code_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  book_id BIGINT UNSIGNED NOT NULL,
  role VARCHAR(8) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  created_by_user_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (code_digest),
  CONSTRAINT fk_book_invitation_book FOREIGN KEY (book_id) REFERENCES account_book(id),
  CONSTRAINT fk_book_invitation_creator FOREIGN KEY (created_by_user_id) REFERENCES app_user(id),
  CONSTRAINT ck_book_invitation_role CHECK (role IN ('EDITOR','VIEWER'))
) ENGINE=InnoDB COMMENT='短期有效的共享账本邀请码';

CREATE TABLE account_transfer (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  book_id BIGINT UNSIGNED NOT NULL,
  from_account_id BIGINT UNSIGNED NOT NULL,
  to_account_id BIGINT UNSIGNED NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  note VARCHAR(500) NULL,
  created_by_user_id BIGINT UNSIGNED NULL,
  deleted_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_transfer_book_time (book_id,occurred_at),
  CONSTRAINT fk_transfer_book FOREIGN KEY (book_id) REFERENCES account_book(id),
  CONSTRAINT fk_transfer_from FOREIGN KEY (book_id,from_account_id) REFERENCES finance_account(book_id,id),
  CONSTRAINT fk_transfer_to FOREIGN KEY (book_id,to_account_id) REFERENCES finance_account(book_id,id),
  CONSTRAINT fk_transfer_creator FOREIGN KEY (created_by_user_id) REFERENCES app_user(id),
  CONSTRAINT ck_transfer_accounts CHECK (from_account_id <> to_account_id),
  CONSTRAINT ck_transfer_amount CHECK (amount > 0)
) ENGINE=InnoDB COMMENT='同一账本账户间转账';

CREATE TABLE quick_template (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  book_id BIGINT UNSIGNED NOT NULL,
  type VARCHAR(8) NOT NULL,
  name VARCHAR(50) NOT NULL,
  amount DECIMAL(18,2) NULL,
  purpose VARCHAR(40) NOT NULL,
  category_id BIGINT UNSIGNED NOT NULL,
  account_id BIGINT UNSIGNED NULL,
  note VARCHAR(500) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_quick_template_book (book_id,sort_order),
  CONSTRAINT fk_quick_template_book FOREIGN KEY (book_id) REFERENCES account_book(id),
  CONSTRAINT fk_quick_template_category FOREIGN KEY (book_id,type,category_id) REFERENCES category(book_id,type,id),
  CONSTRAINT fk_quick_template_account FOREIGN KEY (book_id,account_id) REFERENCES finance_account(book_id,id),
  CONSTRAINT ck_quick_template_type CHECK (type IN ('INCOME','EXPENSE')),
  CONSTRAINT ck_quick_template_amount CHECK (amount IS NULL OR amount > 0)
) ENGINE=InnoDB COMMENT='快捷记账模板';

CREATE TABLE admin_login_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  admin_user_id BIGINT UNSIGNED NULL,
  username VARCHAR(64) NOT NULL,
  success BOOLEAN NOT NULL,
  ip_address VARCHAR(45) NOT NULL,
  user_agent VARCHAR(255) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_admin_login_limit (username,ip_address,created_at),
  CONSTRAINT fk_admin_login_user FOREIGN KEY (admin_user_id) REFERENCES admin_user(id)
) ENGINE=InnoDB COMMENT='管理端登录与限流记录';

CREATE TABLE admin_audit_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  admin_user_id BIGINT UNSIGNED NOT NULL,
  action VARCHAR(40) NOT NULL,
  target_type VARCHAR(40) NOT NULL,
  target_id BIGINT UNSIGNED NOT NULL,
  before_json JSON NULL,
  after_json JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_admin_audit_target (target_type,target_id,created_at),
  CONSTRAINT fk_admin_audit_user FOREIGN KEY (admin_user_id) REFERENCES admin_user(id)
) ENGINE=InnoDB COMMENT='管理端操作审计';

CREATE TABLE app_login_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  device_info VARCHAR(255) NULL,
  ip_address VARCHAR(45) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_app_login_user (user_id,created_at),
  CONSTRAINT fk_app_login_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB COMMENT='小程序登录设备记录';

CREATE TABLE user_feedback (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  subject VARCHAR(80) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  admin_response VARCHAR(1000) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_feedback_status (status,created_at),
  CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT ck_feedback_status CHECK (status IN ('OPEN','RESOLVED'))
) ENGINE=InnoDB COMMENT='用户反馈';

CREATE TABLE system_category_template (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  type VARCHAR(8) NOT NULL,
  name VARCHAR(50) NOT NULL,
  icon VARCHAR(32) NOT NULL DEFAULT '•',
  sort_order INT NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  UNIQUE KEY uk_system_category (type,name),
  CONSTRAINT ck_system_category_type CHECK (type IN ('INCOME','EXPENSE'))
) ENGINE=InnoDB COMMENT='新账本默认分类模板';

INSERT INTO system_category_template(type,name,icon,sort_order) VALUES
  ('EXPENSE','餐饮','🍜',10),('EXPENSE','交通','🚇',20),('EXPENSE','购物','🛍️',30),
  ('EXPENSE','居住','🏠',40),('EXPENSE','其他','•',99),
  ('INCOME','工资','💼',10),('INCOME','奖金','✨',20),('INCOME','其他','•',99);

CREATE TABLE content_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  kind VARCHAR(20) NOT NULL,
  title VARCHAR(100) NOT NULL,
  body TEXT NOT NULL,
  link_path VARCHAR(255) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_content_kind (kind,is_active,sort_order),
  CONSTRAINT ck_content_kind CHECK (kind IN ('BANNER','ANNOUNCEMENT','FAQ','VERSION','PRIVACY','TERMS'))
) ENGINE=InnoDB COMMENT='公告、帮助、版本提示和法律文本';

CREATE TABLE transaction_comment (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  book_id BIGINT UNSIGNED NOT NULL,
  record_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  body VARCHAR(500) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_comment_record (record_id,created_at),
  CONSTRAINT fk_comment_book FOREIGN KEY (book_id) REFERENCES account_book(id),
  CONSTRAINT fk_comment_record FOREIGN KEY (record_id) REFERENCES bookkeeping_record(id),
  CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB COMMENT='共享账本交易留言';
