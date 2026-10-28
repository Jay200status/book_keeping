USE book_keeping;

ALTER TABLE app_user DROP CHECK ck_app_user_status;
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE','DISABLED','CANCELLED'));
ALTER TABLE category ADD COLUMN icon VARCHAR(32) NOT NULL DEFAULT '•' AFTER name;
ALTER TABLE finance_account ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'ASSET' AFTER name;
ALTER TABLE finance_account ADD CONSTRAINT ck_finance_account_kind CHECK (kind IN ('ASSET','LIABILITY'));
ALTER TABLE bookkeeping_record ADD COLUMN created_by_user_id BIGINT UNSIGNED NULL AFTER source;
ALTER TABLE bookkeeping_record ADD CONSTRAINT fk_record_creator FOREIGN KEY (created_by_user_id) REFERENCES app_user(id);
UPDATE bookkeeping_record r JOIN account_book b ON b.id=r.book_id
SET r.created_by_user_id=b.owner_user_id WHERE r.created_by_user_id IS NULL;

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
) ENGINE=InnoDB;

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
) ENGINE=InnoDB;

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
) ENGINE=InnoDB;

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
) ENGINE=InnoDB;
