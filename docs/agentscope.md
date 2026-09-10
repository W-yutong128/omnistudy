# AgentScope Java 2.0 integration

OmniStudy embeds AgentScope Java 2.0.1 inside Spring Boot. The existing `/api/agent/chat` remains as a deterministic fallback; the extension uses `/api/agent-v2/chat`.

## Responsibilities

- `HarnessAgent`: ReAct loop, PostgreSQL-backed per-session context, context compaction, Skills and token usage.
- `PostgresAgentStateStore`: durable state keyed by `(userId, sessionId)` in `agent_scope_states`.
- `OmniStudyAgentScopeTools`: adapters over the existing RAG and learning services.
- `AgentScopeContextBuilder`: bounds browser-provided subtitles and estimates the pre-call input budget.
- `ScreenshotContextMiddleware`: injects the current browser frame into each multimodal reasoning call without persisting Base64 image data in the conversation state.
- `AgentTrace`: stores framework version and provider-reported input/output/cached tokens.

Compaction triggers after 20 messages or an estimated 8,000 conversation tokens and keeps the latest 8 messages by default. Its model call consumes Token quota without consuming another user operation and is recorded as `CONTEXT_COMPACTION`. Agent execution is capped at 3 iterations, while workspace context is capped at 12,000 tokens. All values are configurable through the `AGENTSCOPE_*` environment variables in `.env.example`.

AgentScope file-backed memory hooks, memory tools and session JSONL persistence are disabled. Durable conversation state lives in PostgreSQL `agent_scope_states`; durable learning memory lives in PostgreSQL RAG chunks. This keeps API replicas stateless and prevents different Pods from observing different local `MEMORY.md` or session files.

The default model is `qwen3-vl-flash`, so subtitle text and the current video frame can be reasoned over together. Oversized or invalid image payloads are ignored, and screenshots remain call-scoped rather than becoming long-term memory.

## Safety boundary

Shell, filesystem tools, MCP, Plan Mode and subagents are disabled. All exposed domain tools are read-only. `request_open_idea` only creates a proposal; the native application is opened exclusively after the browser UI receives explicit confirmation and the Native Host validates its path allowlist.

Set `AGENTSCOPE_ENABLED=false` to disable the AgentScope bean. If an AgentScope call fails, the v2 facade records a failure trace and invokes the previous bounded Agent implementation.
