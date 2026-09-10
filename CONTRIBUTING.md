# Contributing

Contributions are welcome after the repository license is selected and published.

## Development setup

1. Install JDK 21, Maven 3.9+, Node.js 20+, pnpm 9+, Docker Desktop, and Chromium.
2. Run `pnpm install --frozen-lockfile`.
3. Start the local stack with `./scripts/local-up` or use `./scripts/dev-up` for a directly launched Spring Boot process.
4. Build the extension with `./scripts/package-extension`.

## Required checks

Run:

```bash
./scripts/verify
./scripts/audit-release
```

Backend changes also require a backend restart and a successful `GET /health`. Agent behavior changes require `./scripts/run-agent-evals`.

## Safety and data rules

- Never commit credentials, `.env.local`, backups, browser profiles, database volumes, personal data, or generated Agent memory.
- Do not contribute exam questions, books, subtitles, images, or other datasets unless their redistribution license is documented.
- Database changes must use a new forward-only Flyway migration.
- Every user-owned query must be scoped by the authenticated user or explicitly verify ownership.
- Treat extension messages and model-generated JSON as untrusted input.
- Destructive Agent and local-machine actions require explicit user confirmation.
