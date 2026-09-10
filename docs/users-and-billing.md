# Users, administrators, BYOK and usage safety

OmniStudy is an open-source BYOK application and does not sell plans. It uses PostgreSQL for accounts, BCrypt password hashes, signed JWT access tokens and rotating refresh tokens. Only SHA-256 hashes of refresh tokens are stored.

## Bootstrap an administrator

Set these variables before starting Spring Boot:

```bash
export BOOTSTRAP_ADMIN_USERNAME="your-name"
export BOOTSTRAP_ADMIN_PASSWORD="a-strong-password"
export BOOTSTRAP_ADMIN_EMAIL="you@example.com"
export DEFAULT_USER_ENABLED=false
export JWT_SECRET="at-least-32-random-characters"
export AI_CREDENTIAL_ENCRYPTION_KEY="another-independent-random-secret-at-least-32-characters"
```

The bootstrap process creates the account only when it does not exist. An existing account with the same normalized username is promoted to administrator; its password is not overwritten. The historical plan column is retained only for rolling-upgrade compatibility; plans are no longer part of the public API or UI.

Never commit these values. Production deployments should inject them through a secret manager. The credential encryption key must remain stable: changing it makes existing user API keys unreadable, after which users must save them again.

## Authentication API

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`

## User-owned model credentials

- `GET /api/settings/ai-provider` returns only provider metadata and a masked key.
- `PUT /api/settings/ai-provider` encrypts and replaces the current user's key.
- `DELETE /api/settings/ai-provider` deletes the current user's key.
- `POST /api/settings/ai-provider/test` performs an explicit minimal text-model request and reports the model and latency. The real Token usage is recorded as `CONNECTION_TEST`.
- Connection verification state and the last successful verification time are persisted. Replacing a Key resets it to `UNVERIFIED`; a successful or failed test changes it to `VERIFIED` or `FAILED` without ever returning the plaintext Key.
- `GET /api/usage/today` returns only the signed-in user's daily input/output/cached Token, estimated cost, success/failure counts and per-feature breakdown. The extension combines it with owned knowledge points and Agent traces in the “概览” tab.

Keys are encrypted using AES-256-GCM with a fresh random IV for every update. Plaintext keys are never returned to the extension and are not stored in browser storage, logs, metrics or AI jobs. OmniStudy is strict BYOK: the server has no shared model key and AI features remain unavailable until the signed-in user saves a personal key.

The current provider is DashScope's OpenAI-compatible API. Users may select their fast vision and strong text model names. AgentScope, video interception, evaluation, note generation and background AI jobs all resolve credentials by the task owner's user ID.

## Safety limits and usage observability

New accounts retain conservative daily request and Token limits as abuse and runaway-cost protection, not paid plan entitlements. Administrators can change role, status and safety limits. There is no upgrade or purchase endpoint.

- `GET /api/admin/overview`
- `GET /api/admin/users`
- `PUT /api/admin/users/{userId}`

`usage_records` reserves a safety budget before an AI call and records provider-reported Token usage and estimated cost afterward. Incremental note generation uses `NOTE_SESSION:<sessionId>` as an idempotent operation: the first call counts as one user operation, while later summaries, finalization and retries add real Token usage without counting another operation.

Periodic note refreshes skip the model when no new teaching subtitle exists. The admin overview groups requests, tokens and estimated cost by feature so self-hosting operators can spot accidental loops and expensive routes.
When a persistent note job fails, the session status response includes the latest bounded, single-line failure reason so the extension can explain the problem without requiring access to server logs.
