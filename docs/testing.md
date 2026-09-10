# Testing

- `./scripts/verify`: backend tests, extension type check/build, real MV3 Playwright E2E, and offline Agent evals.
- `./scripts/run-agent-evals`: deterministic routing and safety cases; does not call a paid model by default.
- Backend unit tests must cover ownership checks and invalid tool output.
- `pnpm --filter @omnistudy/extension test:e2e`: loads the built unpacked extension in Playwright Chromium and verifies the side panel workspace.
- Set `SKIP_E2E=1` only when a browser runtime is intentionally unavailable.
- Live smoke testing requires PostgreSQL, a configured model key, a reloaded unpacked extension, and a Bilibili video with subtitles.
