USE book_keeping;

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
