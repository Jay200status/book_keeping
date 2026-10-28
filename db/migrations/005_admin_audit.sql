USE book_keeping;

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
) ENGINE=InnoDB;

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
) ENGINE=InnoDB;

CREATE TABLE app_login_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  device_info VARCHAR(255) NULL,
  ip_address VARCHAR(45) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_app_login_user (user_id,created_at),
  CONSTRAINT fk_app_login_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB;
