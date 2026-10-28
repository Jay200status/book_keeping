-- 仅用于已经执行过旧版 db/schema.sql 的数据库。
-- 新数据库直接运行 db/schema.sql，不再执行本迁移。
USE book_keeping;

-- 微信身份标识区分大小写，不能沿用数据库默认的不区分大小写排序规则。
ALTER TABLE app_user MODIFY COLUMN union_id VARCHAR(64) COLLATE utf8mb4_bin NULL;
ALTER TABLE wechat_identity
  MODIFY COLUMN app_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  MODIFY COLUMN open_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL;
ALTER TABLE official_message
  MODIFY COLUMN official_app_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  MODIFY COLUMN wechat_msg_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  MODIFY COLUMN sender_open_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL;

ALTER TABLE bookkeeping_record
  ADD COLUMN purpose VARCHAR(40) NOT NULL DEFAULT '未分类' AFTER amount;

UPDATE bookkeeping_record r
JOIN category c ON c.id = r.category_id
SET r.purpose = LEFT(c.name, 40);

ALTER TABLE bookkeeping_record
  ALTER COLUMN purpose DROP DEFAULT;

ALTER TABLE official_message
  DROP CHECK ck_official_message_status;

ALTER TABLE official_message
  ADD CONSTRAINT ck_official_message_status CHECK
    (status IN ('RECEIVED', 'POSTED', 'NEEDS_BINDING', 'NEEDS_REVIEW', 'FAILED', 'IGNORED', 'BOUND'));

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
