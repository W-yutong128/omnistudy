ALTER TABLE users ADD COLUMN email VARCHAR(254);
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN plan VARCHAR(20) NOT NULL DEFAULT 'FREE';
ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX uk_users_email_normalized
    ON users (LOWER(email)) WHERE email IS NOT NULL;

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at  TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id, expires_at DESC);

CREATE TABLE user_quotas (
    user_id              UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    daily_request_limit  INTEGER NOT NULL DEFAULT 20 CHECK (daily_request_limit >= 0),
    daily_token_limit    BIGINT NOT NULL DEFAULT 200000 CHECK (daily_token_limit >= 0),
    unlimited            BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO user_quotas(user_id)
SELECT id FROM users
ON CONFLICT (user_id) DO NOTHING;

CREATE TABLE usage_records (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trace_id               UUID REFERENCES agent_traces(id) ON DELETE SET NULL,
    request_count          INTEGER NOT NULL DEFAULT 1,
    input_tokens           INTEGER NOT NULL DEFAULT 0,
    output_tokens          INTEGER NOT NULL DEFAULT 0,
    cached_tokens          INTEGER NOT NULL DEFAULT 0,
    total_tokens           BIGINT NOT NULL DEFAULT 0,
    estimated_cost_micros  BIGINT NOT NULL DEFAULT 0,
    status                 VARCHAR(20) NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_usage_records_user_created ON usage_records(user_id, created_at DESC);
CREATE INDEX idx_usage_records_trace ON usage_records(trace_id) WHERE trace_id IS NOT NULL;
