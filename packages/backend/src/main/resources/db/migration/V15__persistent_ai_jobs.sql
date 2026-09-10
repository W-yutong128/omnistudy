CREATE TABLE ai_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_type            VARCHAR(40) NOT NULL,
    operation_key       VARCHAR(160) NOT NULL,
    payload_json        JSONB NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts            INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts        INTEGER NOT NULL DEFAULT 3 CHECK (max_attempts > 0),
    finalize_requested  BOOLEAN NOT NULL DEFAULT FALSE,
    rerun_requested     BOOLEAN NOT NULL DEFAULT FALSE,
    available_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    locked_at           TIMESTAMP WITH TIME ZONE,
    locked_by           VARCHAR(120),
    last_error          TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_ai_jobs_active_operation
    ON ai_jobs(job_type, operation_key)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX idx_ai_jobs_claim
    ON ai_jobs(status, available_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_ai_jobs_user_created
    ON ai_jobs(user_id, created_at DESC);
