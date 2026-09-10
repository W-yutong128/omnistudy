# Agent runtime

The primary runtime is AgentScope Java 2.0.1 `HarnessAgent`, exposed through `/api/agent-v2/chat`. It persists conversation state in PostgreSQL by `(userId, sessionId)`, compacts long conversations, loads versioned Skills and reports provider token usage. See `docs/agentscope.md`.

The original runtime below remains available at `/api/agent/chat` and is also the safe fallback when AgentScope or its model provider fails.

The Agent runtime uses a bounded state machine:

`RECEIVED -> PLANNING -> TOOL_VALIDATION -> TOOL_EXECUTION -> OBSERVATION -> FINAL_RESPONSE`

Each request has a trace ID. A plan may select one registered tool. Tool input is parsed into a typed argument object and validated before execution. Read-only tools run immediately. Side-effecting tools declare their confirmation policy. Failures produce a trace and a safe response instead of recursive retries.

The first production tools are current-video context, learning-memory search, practice generation, and video seeking. Maximum steps, prompt versions, latency, status, and sanitized observations are persisted for evaluation.
