ALTER TABLE usage_records
    ADD COLUMN operation_key VARCHAR(120);

CREATE UNIQUE INDEX uk_usage_records_counted_operation
    ON usage_records(user_id, operation_key)
    WHERE operation_key IS NOT NULL AND request_count > 0;

CREATE INDEX idx_usage_records_operation
    ON usage_records(user_id, operation_key, created_at DESC)
    WHERE operation_key IS NOT NULL;
