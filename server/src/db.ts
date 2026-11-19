import { DatabaseSync } from 'node:sqlite'

export type Db = DatabaseSync

// Ordered, append-only migrations. `PRAGMA user_version` records how many have run.
const MIGRATIONS: string[] = [
  `
  CREATE TABLE users (
    id            TEXT PRIMARY KEY,
    phone         TEXT NOT NULL UNIQUE,
    display_name  TEXT NOT NULL DEFAULT '',
    avatar        TEXT NOT NULL DEFAULT 'bunny',
    public_key    TEXT,
    paused        INTEGER NOT NULL DEFAULT 0,
    paused_at     INTEGER,
    settings      TEXT NOT NULL DEFAULT '{}',
    created_at    INTEGER NOT NULL,
    last_seen_at  INTEGER
  );

  CREATE TABLE otp_codes (
    phone       TEXT PRIMARY KEY,
    code_hash   TEXT NOT NULL,
    expires_at  INTEGER NOT NULL,
    attempts    INTEGER NOT NULL DEFAULT 0,
    sent_at     INTEGER NOT NULL
  );

  CREATE TABLE sessions (
    id            TEXT PRIMARY KEY,
    user_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash    TEXT NOT NULL UNIQUE,
    device        TEXT NOT NULL DEFAULT '',
    created_at    INTEGER NOT NULL,
    last_used_at  INTEGER NOT NULL
  );
  CREATE INDEX sessions_user ON sessions(user_id);

  CREATE TABLE invites (
    id            TEXT PRIMARY KEY,
    from_user_id  TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    to_phone      TEXT NOT NULL,
    status        TEXT NOT NULL CHECK (status IN ('pending','accepted','declined','cancelled','expired')),
    created_at    INTEGER NOT NULL,
    expires_at    INTEGER NOT NULL,
    responded_at  INTEGER
  );
  CREATE INDEX invites_to ON invites(to_phone, status);
  CREATE INDEX invites_from ON invites(from_user_id, status);

  CREATE TABLE pairs (
    id          TEXT PRIMARY KEY,
    user_a      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  INTEGER NOT NULL,
    ended_at    INTEGER
  );
  CREATE INDEX pairs_a ON pairs(user_a) WHERE ended_at IS NULL;
  CREATE INDEX pairs_b ON pairs(user_b) WHERE ended_at IS NULL;

  CREATE TABLE events (
    id            TEXT PRIMARY KEY,
    pair_id       TEXT NOT NULL REFERENCES pairs(id) ON DELETE CASCADE,
    sender_id     TEXT NOT NULL,
    recipient_id  TEXT NOT NULL,
    type          TEXT NOT NULL,
    meta          TEXT NOT NULL DEFAULT '{}',
    enc           TEXT,
    reply_to      TEXT,
    status        TEXT NOT NULL CHECK (status IN ('sent','delivered','opened','answered','expired')),
    created_at    INTEGER NOT NULL,
    delivered_at  INTEGER,
    opened_at     INTEGER,
    expires_at    INTEGER
  );
  CREATE INDEX events_pair_time ON events(pair_id, created_at DESC);
  CREATE INDEX events_pending ON events(recipient_id, status);

  CREATE TABLE media (
    id           TEXT PRIMARY KEY,
    pair_id      TEXT NOT NULL REFERENCES pairs(id) ON DELETE CASCADE,
    owner_id     TEXT NOT NULL,
    kind         TEXT NOT NULL CHECK (kind IN ('voice','image')),
    size         INTEGER NOT NULL,
    enc_title    TEXT,
    duration_ms  INTEGER,
    created_at   INTEGER NOT NULL
  );
  CREATE INDEX media_pair ON media(pair_id, kind, created_at DESC);
  `,
]

export function openDatabase(file: string): Db {
  const db = new DatabaseSync(file)
  db.exec('PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON; PRAGMA busy_timeout = 5000;')
  migrate(db)
  return db
}

function migrate(db: Db): void {
  const row = db.prepare('PRAGMA user_version').get() as { user_version: number }
  for (let v = row.user_version; v < MIGRATIONS.length; v++) {
    transaction(db, () => {
      db.exec(MIGRATIONS[v]!)
      db.exec(`PRAGMA user_version = ${v + 1}`)
    })
  }
}

/** Runs `fn` inside BEGIN IMMEDIATE … COMMIT, rolling back if it throws. */
export function transaction<T>(db: Db, fn: () => T): T {
  db.exec('BEGIN IMMEDIATE')
  try {
    const result = fn()
    db.exec('COMMIT')
    return result
  } catch (err) {
    db.exec('ROLLBACK')
    throw err
  }
}
