ALTER TABLE user_ai_credentials
    ADD COLUMN last_verified_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_test_error VARCHAR(500);
