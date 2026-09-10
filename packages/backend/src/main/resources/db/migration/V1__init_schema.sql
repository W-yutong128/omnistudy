-- OmniStudy V1：初始数据库结构

-- ==================== 用户 ====================
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       VARCHAR(255) UNIQUE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    settings        JSONB DEFAULT '{}'
);
CREATE INDEX idx_users_device_id ON users(device_id);

-- ==================== 课程 ====================
CREATE TABLE courses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform        VARCHAR(50) NOT NULL,
    url             TEXT NOT NULL,
    title           VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_courses_user_id ON courses(user_id);

-- ==================== 学习会话 ====================
CREATE TABLE sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id       UUID REFERENCES courses(id) ON DELETE SET NULL,
    started_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    ended_at        TIMESTAMP WITH TIME ZONE,
    video_url       TEXT,
    video_title     VARCHAR(500)
);
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_course_id ON sessions(course_id);

-- ==================== 字幕片段 ====================
CREATE TABLE subtitle_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    t_start         FLOAT NOT NULL,
    t_end           FLOAT,
    text            TEXT NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_subtitle_chunks_session_t ON subtitle_chunks(session_id, t_start);

-- ==================== 截图 ====================
CREATE TABLE screenshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    t               FLOAT NOT NULL,
    image_data      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_screenshots_session_id ON screenshots(session_id);

-- ==================== 题目 ====================
CREATE TABLE questions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    t               FLOAT NOT NULL,
    prompt_json     JSONB NOT NULL,
    difficulty      INTEGER NOT NULL DEFAULT 2,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_questions_session_id ON questions(session_id);

-- ==================== 作答记录 ====================
CREATE TABLE attempts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id     UUID NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    attempt_num     INTEGER NOT NULL DEFAULT 1,
    answer_text     TEXT NOT NULL,
    evaluation_json JSONB,
    decided         VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_attempts_question_id ON attempts(question_id);

-- ==================== 笔记 ====================
CREATE TABLE notes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    content_json    JSONB,
    status          VARCHAR(20) NOT NULL DEFAULT 'generating',
    generated_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_notes_session_id ON notes(session_id);

-- ==================== 知识点 ====================
CREATE TABLE knowledge_points (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(500) NOT NULL,
    normalized_name VARCHAR(500) NOT NULL,
    mastery         VARCHAR(20) NOT NULL DEFAULT '陌生的',
    first_seen      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_reviewed   TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    sources         UUID[] DEFAULT '{}'
);
CREATE INDEX idx_kp_user_id ON knowledge_points(user_id);
CREATE INDEX idx_kp_normalized ON knowledge_points(user_id, normalized_name);

-- ==================== 知识点关系 ====================
CREATE TABLE knowledge_relations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_point_id   UUID NOT NULL REFERENCES knowledge_points(id) ON DELETE CASCADE,
    to_point_id     UUID NOT NULL REFERENCES knowledge_points(id) ON DELETE CASCADE,
    relation_type   VARCHAR(50) NOT NULL,
    weight          FLOAT NOT NULL DEFAULT 0.5
);
CREATE INDEX idx_kr_from ON knowledge_relations(from_point_id);
CREATE INDEX idx_kr_to ON knowledge_relations(to_point_id);
