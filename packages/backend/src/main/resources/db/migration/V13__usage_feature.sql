ALTER TABLE usage_records
    ADD COLUMN feature VARCHAR(40) NOT NULL DEFAULT 'AGENT';

CREATE INDEX idx_usage_records_user_feature_created
    ON usage_records(user_id, feature, created_at DESC);
