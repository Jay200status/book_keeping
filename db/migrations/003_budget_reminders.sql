USE book_keeping;

CREATE TABLE budget_rule (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  book_id BIGINT UNSIGNED NOT NULL,
  month DATE NOT NULL,
  category_id BIGINT UNSIGNED NULL,
  category_key BIGINT UNSIGNED GENERATED ALWAYS AS (COALESCE(category_id, 0)) STORED,
  amount DECIMAL(18,2) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_budget_scope (book_id, month, category_key),
  CONSTRAINT fk_budget_book FOREIGN KEY (book_id) REFERENCES account_book(id),
  CONSTRAINT fk_budget_category FOREIGN KEY (book_id, category_id) REFERENCES category(book_id, id),
  CONSTRAINT ck_budget_amount CHECK (amount > 0)
) ENGINE=InnoDB;

CREATE TABLE reminder_rule (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  kind VARCHAR(16) NOT NULL,
  title VARCHAR(80) NOT NULL,
  due_at DATETIME(3) NOT NULL,
  recurrence VARCHAR(8) NOT NULL,
  lead_days SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_reminder_user (user_id, is_active),
  CONSTRAINT fk_reminder_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT ck_reminder_kind CHECK (kind IN ('BOOKKEEPING','DUE')),
  CONSTRAINT ck_reminder_recurrence CHECK (recurrence IN ('ONCE','DAILY','MONTHLY'))
) ENGINE=InnoDB;

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
) ENGINE=InnoDB;

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
) ENGINE=InnoDB;

CREATE TABLE subscription_permission (
  user_id BIGINT UNSIGNED NOT NULL,
  template_id VARCHAR(128) NOT NULL,
  credits INT UNSIGNED NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, template_id),
  CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB;

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
) ENGINE=InnoDB;
