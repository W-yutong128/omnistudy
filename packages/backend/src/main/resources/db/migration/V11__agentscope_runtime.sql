CREATE TABLE agent_scope_states (
    user_key       VARCHAR(80) NOT NULL,
    session_id     VARCHAR(120) NOT NULL,
    state_key      VARCHAR(120) NOT NULL,
    state_value    JSONB NOT NULL,
    list_value     BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_key, session_id, state_key)
);
CREATE INDEX idx_agent_scope_states_session
    ON agent_scope_states(user_key, session_id, updated_at DESC);

ALTER TABLE agent_traces ADD COLUMN framework VARCHAR(40) NOT NULL DEFAULT 'custom-harness';
ALTER TABLE agent_traces ADD COLUMN input_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE agent_traces ADD COLUMN output_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE agent_traces ADD COLUMN cached_tokens INTEGER NOT NULL DEFAULT 0;
