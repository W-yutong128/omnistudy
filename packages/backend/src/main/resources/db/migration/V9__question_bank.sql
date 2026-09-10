-- 外部题库与用户实际作答题目分离。题库记录可被多个用户检索，投放后才创建 questions。
CREATE TABLE question_bank (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type         VARCHAR(30) NOT NULL,
    source_document     VARCHAR(500) NOT NULL,
    source_year         INTEGER,
    source_exam         VARCHAR(500),
    source_question_no  INTEGER NOT NULL,
    source_page         INTEGER,
    subject             VARCHAR(50) NOT NULL,
    question_type       VARCHAR(30) NOT NULL DEFAULT 'single_choice',
    question_text       TEXT NOT NULL,
    options_json        JSONB NOT NULL,
    correct_option_id   VARCHAR(10) NOT NULL,
    explanation         TEXT,
    knowledge_tags      JSONB NOT NULL DEFAULT '[]',
    asset_paths         JSONB NOT NULL DEFAULT '[]',
    difficulty          INTEGER NOT NULL DEFAULT 2,
    raw_text            TEXT,
    license_status      VARCHAR(30) NOT NULL DEFAULT 'unverified',
    review_status       VARCHAR(30) NOT NULL DEFAULT 'ready',
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(source_document, source_question_no)
);

CREATE INDEX idx_question_bank_subject ON question_bank(subject);
CREATE INDEX idx_question_bank_review_status ON question_bank(review_status);
CREATE INDEX idx_question_bank_tags ON question_bank USING GIN(knowledge_tags);
CREATE INDEX idx_question_bank_search ON question_bank USING GIN (
    to_tsvector('simple',
        COALESCE(question_text, '') || ' ' ||
        COALESCE(explanation, '') || ' ' ||
        COALESCE(subject, '') || ' ' ||
        COALESCE(knowledge_tags::text, ''))
);

ALTER TABLE questions
    ADD COLUMN bank_question_id UUID REFERENCES question_bank(id) ON DELETE SET NULL;
CREATE INDEX idx_questions_bank_question ON questions(bank_question_id);

