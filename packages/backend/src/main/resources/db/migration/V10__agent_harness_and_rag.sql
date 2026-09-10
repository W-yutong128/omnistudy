CREATE TABLE agent_traces (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id      UUID REFERENCES sessions(id) ON DELETE SET NULL,
    state           VARCHAR(40) NOT NULL,
    user_message    TEXT NOT NULL,
    tool_name       VARCHAR(80),
    tool_args       JSONB NOT NULL DEFAULT '{}',
    observation     TEXT,
    response_text   TEXT,
    model_name      VARCHAR(120),
    prompt_version  VARCHAR(40) NOT NULL,
    skill_name      VARCHAR(80),
    skill_version   VARCHAR(20),
    step_count      INTEGER NOT NULL DEFAULT 0,
    latency_ms      BIGINT NOT NULL DEFAULT 0,
    success         BOOLEAN NOT NULL DEFAULT FALSE,
    error_message   TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_agent_traces_user_created ON agent_traces(user_id, created_at DESC);
CREATE INDEX idx_agent_traces_session_created ON agent_traces(session_id, created_at DESC);

CREATE TABLE retrieval_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id      UUID REFERENCES sessions(id) ON DELETE CASCADE,
    note_id         UUID REFERENCES notes(id) ON DELETE CASCADE,
    part            INTEGER,
    start_time      DOUBLE PRECISION,
    end_time        DOUBLE PRECISION,
    content_type    VARCHAR(40) NOT NULL,
    title           TEXT NOT NULL,
    content         TEXT NOT NULL,
    content_hash    VARCHAR(64) NOT NULL,
    embedding       REAL[] NOT NULL DEFAULT '{}',
    search_vector   TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(content, ''))
    ) STORED,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, content_hash)
);
CREATE INDEX idx_retrieval_chunks_user ON retrieval_chunks(user_id, created_at DESC);
CREATE INDEX idx_retrieval_chunks_session ON retrieval_chunks(session_id, part, start_time);
CREATE INDEX idx_retrieval_chunks_search ON retrieval_chunks USING GIN(search_vector);

-- Local development may not have pgvector. Enable its native column when the extension
-- is available; the application keeps a REAL[] fallback so startup never depends on it.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        CREATE EXTENSION IF NOT EXISTS vector;
        EXECUTE 'ALTER TABLE retrieval_chunks ADD COLUMN embedding_vector vector(384)';
        EXECUTE 'CREATE INDEX idx_retrieval_chunks_vector ON retrieval_chunks USING hnsw (embedding_vector vector_cosine_ops)';
    END IF;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pgvector unavailable, using portable embedding fallback: %', SQLERRM;
END $$;
