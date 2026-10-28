USE book_keeping;

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
) ENGINE=InnoDB;

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
) ENGINE=InnoDB;

INSERT INTO system_category_template(type,name,icon,sort_order) VALUES
  ('EXPENSE','餐饮','🍜',10),('EXPENSE','交通','🚇',20),('EXPENSE','购物','🛍️',30),
  ('EXPENSE','居住','🏠',40),('EXPENSE','其他','•',99),
  ('INCOME','工资','💼',10),('INCOME','奖金','✨',20),('INCOME','其他','•',99);

INSERT IGNORE INTO category(book_id,type,name,icon,sort_order)
SELECT b.id,t.type,t.name,t.icon,t.sort_order FROM account_book b CROSS JOIN system_category_template t WHERE t.is_active=TRUE;
