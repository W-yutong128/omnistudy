# Public release checklist

Before every public release:

1. Run `./scripts/audit-release` and inspect every reported file.
2. Run `./scripts/verify` and require all backend, extension, Native Messaging, E2E, and Agent eval checks to pass.
3. Run `./scripts/local-backup`, then verify `GET http://localhost:8082/health` after rebuilding.
4. Confirm that all bundled datasets have redistribution permission. The repository currently includes only project-owned sample questions.
5. Confirm that `LICENSE`, `SECURITY.md`, `PRIVACY.md`, and `CONTRIBUTING.md` match the intended release.
6. Update the versions in `package.json` and `packages/extension/package.json`.
7. Generate and inspect `artifacts/omnistudy-extension-v*.zip`; it must not contain `.env` files or user data.
8. Push a matching tag such as `v0.1.0`. GitHub Actions will test, package, record the UI walkthrough, and create or update the Release.

Never upload `backups/`, `.env.local`, Docker volumes, Chrome profiles, `.idea/`, `node_modules/`, or `target/` manually.
