-- Users provide their own model credentials, so OmniStudy must not impose a
-- practical per-day request cap. Keep the column for API compatibility and
-- retain the independent token safety limit.
ALTER TABLE user_quotas
    ALTER COLUMN daily_request_limit SET DEFAULT 2147483647;

UPDATE user_quotas
SET daily_request_limit = 2147483647,
    updated_at = NOW();
