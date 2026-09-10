# Privacy

OmniStudy is Local-first software. In the default Docker Compose installation, application services and persistent data remain on the user's computer.

## Data stored locally

- Account name, optional email, password hash, refresh tokens, and role;
- Courses, learning sessions, subtitles, generated questions, answers, notes, review state, Agent traces, and token usage;
- A user-provided DashScope API Key encrypted with AES-GCM before it is stored in PostgreSQL;
- Redis coordination/cache data and local Prometheus/Grafana metrics.

Passwords are stored as hashes. Model API Keys are never returned in plaintext by the settings API. `.env.local` contains the encryption key and must remain private.

## Data sent to third parties

When an AI feature is invoked, the configured model provider receives the prompt and the context required for that operation. Depending on the feature, this can include subtitle text, note text, the user's question, and a temporary video-frame image. Users should review the provider's privacy terms before adding a Key.

The browser extension runs on supported Bilibili video pages and reads page/video context needed for learning features. OmniStudy does not add analytics or advertising telemetry. Locally provisioned Prometheus and Grafana stay on the user's machine unless the user exposes them.

## Screenshots and backups

Video frames are currently passed in memory/Base64 for a model request and are not intentionally persisted by the application. Local backup archives contain user data and runtime encryption secrets; users control where those archives are stored and deleted.

## Self-hosted deployments

An organization or individual operating a shared deployment becomes responsible for access control, retention, notices, backups, network security, and compliance applicable to their users. The project maintainers do not receive data from independent self-hosted installations.
