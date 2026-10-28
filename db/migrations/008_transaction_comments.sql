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
