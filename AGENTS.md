# OmniStudy Agent Guide

## Architecture

- `packages/extension`: Manifest V3 Chrome extension. The content script senses and controls Bilibili; the service worker is the authenticated API gateway; the React side panel owns UI state.
- `packages/backend`: Java 21 Spring Boot layered monolith. Controllers call services; services call repositories or the AI adapter. Only Flyway may change the schema.
- `packages/shared`: TypeScript contracts used by extension packages. Keep matching Java DTOs synchronized.
- Dependency direction is controller -> service -> repository/AI adapter. Do not call repositories from controllers.

## Invariants

- Never commit credentials. Read secrets from environment variables and update `.env.example` with placeholders only.
- Scope every user-owned database query by the authenticated user or verify ownership before returning data.
- Agent tools must be registered, typed, bounded, traced, and explicit about side effects. Models never access repositories directly.
- Require confirmation for destructive or local-machine actions. Read-only retrieval and video seeking may run directly.
- Database changes require a new forward-only Flyway migration. Never edit an applied migration.
- Treat extension messages and model JSON as untrusted boundary input; validate before use.

## Required validation

- Backend change: run `mvn test` in `packages/backend`, restart `mvn spring-boot:run`, then require `GET /health` to return HTTP 200.
- Extension/shared change: run TypeScript checking, the Vite production build, and `scripts/fix-sw.js`.
- Agent behavior change: run `./scripts/run-agent-evals` in addition to unit tests.
- Cross-package change: run `./scripts/verify`.

## Completion

Report checks executed, failures or skipped checks, backend restart status, and any environment requirement. Do not claim a behavior is verified solely because compilation passed.
