# Architecture

OmniStudy is a browser-native learning assistant. The Chrome extension provides the environment boundary; Spring Boot owns authenticated business state; PostgreSQL owns durable learning memory; model adapters provide probabilistic reasoning behind typed boundaries.

## Runtime boundaries

1. The content script reads subtitles, captures frames, and controls playback.
2. The service worker attaches JWT credentials and forwards requests.
3. Controllers validate transport input and delegate to services.
4. The Agent harness selects only registered tools, validates arguments, records traces, and enforces step limits. Conversation state and learning memory are PostgreSQL-backed; AgentScope file memory/session persistence is disabled so replicas remain stateless.
5. Long-running note generation is persisted in PostgreSQL `ai_jobs`; workers claim rows with `FOR UPDATE SKIP LOCKED`, merge duplicate/finalize requests, retry failures and recover stale leases after a process restart.
5. Services verify ownership before repositories return user data.

The learning-memory retriever chunks notes and session summaries, combines PostgreSQL full-text ranking with a portable 384-dimensional dense similarity score, and fuses both rankings with reciprocal-rank fusion. The migration enables a pgvector/HNSW column when the extension exists; local PostgreSQL installations without pgvector keep the array-backed fallback.

## Rules

- Deterministic scheduling, grading, review intervals, authorization, and persistence remain ordinary code.
- Models may explain, classify, plan, summarize, and generate learning material.
- Retrieval responses carry source identifiers and video timestamps so answers remain inspectable.
- Browser actions originate from structured response actions; the model cannot execute Chrome APIs directly.
- Native IDEA handoff is a proposed action. The user must confirm it in the extension, and the host enforces an explicit filesystem allowlist.
- Extension builds derive both the backend origin and Chrome host permission from `VITE_API_BASE_URL`; production backends expose separate liveness/readiness probes.
