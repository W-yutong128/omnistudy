-- 打通知识点、复习题、作答记录与间隔复习的最小闭环。

ALTER TABLE questions ALTER COLUMN session_id DROP NOT NULL;
ALTER TABLE questions ALTER COLUMN t DROP NOT NULL;
ALTER TABLE questions ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE questions ADD COLUMN note_id UUID REFERENCES notes(id) ON DELETE CASCADE;
ALTER TABLE questions ADD COLUMN knowledge_point_id UUID REFERENCES knowledge_points(id) ON DELETE SET NULL;
ALTER TABLE questions ADD COLUMN origin VARCHAR(30) NOT NULL DEFAULT 'course_intercept';

UPDATE questions q
SET user_id = s.user_id
FROM sessions s
WHERE q.session_id = s.id AND q.user_id IS NULL;

ALTER TABLE questions ALTER COLUMN user_id SET NOT NULL;
CREATE INDEX idx_questions_user_created ON questions(user_id, created_at DESC);
CREATE INDEX idx_questions_note ON questions(note_id);
CREATE INDEX idx_questions_knowledge_point ON questions(knowledge_point_id);

ALTER TABLE knowledge_points ADD COLUMN mastery_score DOUBLE PRECISION NOT NULL DEFAULT 0.20;
ALTER TABLE knowledge_points ADD COLUMN next_review_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
ALTER TABLE knowledge_points ADD COLUMN review_interval_days INTEGER NOT NULL DEFAULT 1;
ALTER TABLE knowledge_points ADD COLUMN correct_streak INTEGER NOT NULL DEFAULT 0;
CREATE INDEX idx_kp_user_review ON knowledge_points(user_id, next_review_at);

