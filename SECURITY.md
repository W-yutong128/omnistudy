# Security Policy

## Supported versions

Security fixes are provided for the latest release on the default branch. Pre-release builds and old tags may not receive fixes.

## Reporting a vulnerability

Please report vulnerabilities privately through GitHub Security Advisories (`Security` → `Advisories` → `Report a vulnerability`) after this repository is published. Do not disclose credentials, personal data, exploit details, or an unpatched vulnerability in a public issue.

Include the affected version, reproduction steps, expected impact, and any suggested mitigation. Maintainers should acknowledge a valid report within seven days and coordinate disclosure after a fix is available.

## Deployment responsibilities

- Never commit `.env.local`, database backups, API keys, JWT secrets, TLS keys, or Kubernetes Secrets.
- Local backups contain the database and the key used to decrypt user-provided model credentials. Treat them like passwords.
- Public deployments must use HTTPS, exact extension origins, unique 32+ character secrets, restricted database networking, and regular backups.
- Users should create a dedicated model API Key with the minimum necessary permissions and rotate it if exposure is suspected.
