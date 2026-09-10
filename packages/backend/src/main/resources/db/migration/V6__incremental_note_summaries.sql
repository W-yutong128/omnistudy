-- 原始字幕仅作为短期可靠队列；长期只保留体积小得多的结构化摘要块。
ALTER TABLE subtitle_chunks ADD COLUMN part INTEGER NOT NULL DEFAULT 1;

CREATE TABLE note_summary_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    part            INTEGER NOT NULL DEFAULT 1,
    t_start         FLOAT NOT NULL,
    t_end           FLOAT,
    content_hash    VARCHAR(64) NOT NULL,
    summary_json    JSONB NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(session_id, content_hash)
);

CREATE INDEX idx_note_summary_session_time
    ON note_summary_chunks(session_id, part, t_start);
