CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  role VARCHAR(16) NOT NULL DEFAULT 'USER',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  membership_expires_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE songs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  artist VARCHAR(200) NOT NULL,
  album_name VARCHAR(200) NOT NULL DEFAULT '',
  language_code VARCHAR(12) NOT NULL DEFAULT 'other',
  genre VARCHAR(80) NOT NULL DEFAULT '未分类',
  description TEXT NULL,
  audio_key VARCHAR(500) NOT NULL,
  preview_key VARCHAR(500) NULL,
  cover_key VARCHAR(500) NULL,
  source_path VARCHAR(500) NULL UNIQUE,
  duration_seconds INT NOT NULL DEFAULT 0,
  vip BOOLEAN NOT NULL DEFAULT FALSE,
  published BOOLEAN NOT NULL DEFAULT TRUE,
  play_count BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_songs_public (published, id),
  INDEX idx_songs_artist (artist),
  INDEX idx_songs_title (title)
);

CREATE TABLE song_tags (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(60) NOT NULL UNIQUE
);
CREATE TABLE song_tag_links (
  song_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY (song_id, tag_id),
  FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE,
  FOREIGN KEY (tag_id) REFERENCES song_tags(id) ON DELETE CASCADE
);
CREATE TABLE favorites (
  user_id BIGINT NOT NULL,
  song_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, song_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE
);
CREATE TABLE playlists (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_playlists_user (user_id)
);
CREATE TABLE playlist_songs (
  playlist_id BIGINT NOT NULL,
  song_id BIGINT NOT NULL,
  added_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (playlist_id, song_id),
  FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
  FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE
);
CREATE TABLE comments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  song_id BIGINT NOT NULL,
  content VARCHAR(1000) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE,
  INDEX idx_comments_song (song_id, status, created_at)
);
CREATE TABLE play_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  song_id BIGINT NOT NULL,
  seconds_played INT NOT NULL,
  completed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE,
  INDEX idx_play_events_user (user_id, created_at),
  INDEX idx_play_events_song (song_id, created_at)
);
CREATE TABLE recommendations (
  user_id BIGINT NOT NULL,
  song_id BIGINT NOT NULL,
  algorithm VARCHAR(20) NOT NULL,
  score DOUBLE NOT NULL,
  reason VARCHAR(200) NOT NULL,
  generated_at DATETIME NOT NULL,
  PRIMARY KEY (user_id, song_id, algorithm),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE,
  INDEX idx_recommendations_user (user_id, algorithm, score)
);
CREATE TABLE ai_conversations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_conversations_user (user_id, updated_at)
);
CREATE TABLE ai_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  role VARCHAR(16) NOT NULL,
  content TEXT NOT NULL,
  parsed_conditions JSON NULL,
  result_song_ids JSON NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OK',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE,
  INDEX idx_ai_messages_conversation (conversation_id, id)
);
CREATE TABLE recommendation_experiments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  algorithm VARCHAR(20) NOT NULL,
  dataset VARCHAR(100) NOT NULL,
  precision_at_10 DOUBLE NOT NULL,
  recall_at_10 DOUBLE NOT NULL,
  coverage DOUBLE NOT NULL,
  evaluated_users INT NOT NULL,
  details JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
