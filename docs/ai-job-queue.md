# Persistent AI job queue

Long-running note generation is stored in PostgreSQL instead of the JVM executor. This keeps accepted work durable across Spring Boot restarts and allows multiple backend replicas to share the same queue safely.

## Lifecycle

1. `NoteGenerationRequestService` writes the `ai_jobs` row and the note's `generating` state in one database transaction.
2. `AiJobWorker` polls eligible `PENDING` rows.
3. `AiJobQueueService.claimNext` uses `FOR UPDATE SKIP LOCKED`, changes one row to `RUNNING`, increments `attempts`, and records a worker lease.
4. A successful job becomes `SUCCEEDED`. A failure returns to `PENDING` with exponential backoff until `max_attempts`, then becomes `FAILED` and the note is marked failed.
5. A scheduled recovery moves expired `RUNNING` leases back to `PENDING`. The default lease timeout is five minutes, which is longer than the model HTTP timeout.

Delivery is at-least-once. Note generation is safe to retry because summaries are content-hash deduplicated, note writes replace the session's single note, and usage uses the stable `NOTE_SESSION:<sessionId>` idempotency key.

## Coalescing updates

There is at most one active `NOTE_GENERATION` job per session. A periodic sync received while a job is pending is merged into that row. A sync received while it is running sets `rerun_requested`; the worker returns the same row to `PENDING` after the current run. `finalize_requested` is monotonic while active, so an end-of-video finalization cannot be lost behind an incremental refresh.

## Configuration

- `AI_JOBS_ENABLED` enables workers while still allowing API-only instances.
- `AI_JOBS_POLL_DELAY_MS` controls empty-queue polling delay.
- `AI_JOBS_RECOVERY_DELAY_MS` controls stale-lease scans.
- `AI_JOBS_STALE_AFTER_SECONDS` is the worker lease timeout.
- `AI_JOBS_CLAIM_BATCH_SIZE` limits jobs handled by one scheduled poll.
- `AI_JOBS_MAX_ATTEMPTS` sets the retry limit for newly created jobs.

For multi-instance deployment, every replica may run the worker because row locking prevents simultaneous claims. Alternatively, set `AI_JOBS_ENABLED=false` on API replicas and enable it only on dedicated worker replicas.
