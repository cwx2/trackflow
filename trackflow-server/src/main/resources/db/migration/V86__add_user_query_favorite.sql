-- User-Query Favorite relation table
-- Implements personal saved query panel (YouTrack opt-in favorites model)
-- Each user sees only their own queries + explicitly favorited shared queries.

CREATE TABLE user_query_favorite (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    query_id   BIGINT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uk_user_query_favorite UNIQUE (user_id, query_id),
    CONSTRAINT fk_uqf_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_uqf_query FOREIGN KEY (query_id) REFERENCES saved_query(id) ON DELETE CASCADE
);

CREATE INDEX idx_uqf_user_id ON user_query_favorite(user_id);
CREATE INDEX idx_uqf_query_id ON user_query_favorite(query_id);

COMMENT ON TABLE user_query_favorite IS 'User-level saved query favorites for personalized panel display';
COMMENT ON COLUMN user_query_favorite.user_id IS 'The user who favorited this query';
COMMENT ON COLUMN user_query_favorite.query_id IS 'The favorited saved query';
COMMENT ON COLUMN user_query_favorite.sort_order IS 'User-specific display order in the panel';

-- Migrate existing data: auto-favorite pinned shared queries for all existing users
-- so the panel doesn't suddenly become empty after this migration.
-- Only favorite the two "universal" queries: "分配给我" (id=5) and "我报告的" (id=6)
-- and any query each user created themselves (those are shown regardless, but favoriting
-- ensures they show up in the favorite section too).
INSERT INTO user_query_favorite (user_id, query_id, sort_order)
SELECT u.id, sq.id, ROW_NUMBER() OVER (PARTITION BY u.id ORDER BY sq.sort_order, sq.id)
FROM sys_user u
CROSS JOIN saved_query sq
WHERE sq.id IN (5, 6)
  AND NOT EXISTS (
    SELECT 1 FROM user_query_favorite uqf
    WHERE uqf.user_id = u.id AND uqf.query_id = sq.id
  )
ON CONFLICT DO NOTHING;
