-- OmniStudy V2：把 deviceId 登录改为 username + password
-- 策略：清空旧数据（drop-old），从零开始

-- 直接 DROP + CASCADE：级联删除所有依赖 users 的子表
DROP TABLE IF EXISTS users CASCADE;

-- 重建 users 表（username + password_hash）
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    settings        JSONB DEFAULT '{}'
);

CREATE INDEX idx_users_username ON users(username);
