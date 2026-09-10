CREATE TABLE user_ai_credentials (
    user_id             UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    provider            VARCHAR(32) NOT NULL DEFAULT 'dashscope',
    key_ciphertext      TEXT NOT NULL,
    key_iv              VARCHAR(64) NOT NULL,
    key_hint            VARCHAR(16) NOT NULL,
    fast_vision_model   VARCHAR(100),
    strong_text_model   VARCHAR(100),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

