const { DatabaseSync } = require('node:sqlite');
const { mkdirSync } = require('node:fs');
const { dirname } = require('node:path');

function openStore(file) {
  mkdirSync(dirname(file), { recursive: true });
  const db = new DatabaseSync(file);
  db.exec('PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON;');
  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY, union_id TEXT UNIQUE, display_name TEXT,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE TABLE IF NOT EXISTS identities (
      app_id TEXT NOT NULL, open_id TEXT NOT NULL, user_id INTEGER NOT NULL REFERENCES users(id),
      PRIMARY KEY(app_id, open_id)
    );
    CREATE TABLE IF NOT EXISTS sessions (
      token_hash TEXT PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id), expires_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS binding_codes (
      digest TEXT PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id),
      expires_at INTEGER NOT NULL, consumed_at INTEGER
    );
    CREATE TABLE IF NOT EXISTS official_messages (
      app_id TEXT NOT NULL, msg_id TEXT NOT NULL, open_id TEXT NOT NULL,
      content TEXT NOT NULL, status TEXT NOT NULL, user_id INTEGER REFERENCES users(id),
      received_at TEXT NOT NULL, PRIMARY KEY(app_id, msg_id)
    );
    CREATE TABLE IF NOT EXISTS records (
      id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id),
      amount_cents INTEGER NOT NULL CHECK(amount_cents > 0), purpose TEXT NOT NULL,
      note TEXT NOT NULL DEFAULT '', occurred_at TEXT NOT NULL,
      source TEXT NOT NULL, message_app_id TEXT, message_id TEXT,
      UNIQUE(message_app_id, message_id)
    );
    CREATE INDEX IF NOT EXISTS idx_records_user_date ON records(user_id, occurred_at DESC, id DESC);
  `);
  return db;
}

module.exports = { openStore };
