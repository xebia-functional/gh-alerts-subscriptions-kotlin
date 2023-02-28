CREATE TABLE IF NOT EXISTS users (
  user_id BIGSERIAL PRIMARY KEY,
  slack_user_id VARCHAR UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS repositories (
  repository_id BIGSERIAL PRIMARY KEY,
  owner VARCHAR NOT NULL,
  repository VARCHAR NOT NULL
);

CREATE TABLE IF NOT EXISTS subscriptions (
  user_id BIGSERIAL NOT NULL,
  repository_id BIGSERIAL NOT NULL,
  subscribed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, repository_id),
  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
  FOREIGN KEY (repository_id) REFERENCES repositories(repository_id) ON DELETE CASCADE
);
