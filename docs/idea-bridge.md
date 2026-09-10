# IDEA Native Messaging bridge

The Agent cannot open IDEA by itself. It may return a `request_open_idea` handoff; the extension then shows a confirmation button. Only that click sends a Native Messaging request.

1. Load `packages/extension/dist` as an unpacked Chrome extension and copy its extension ID.
2. Copy `native-host/idea_bridge.config.example.json` to `native-host/idea_bridge.config.json`.
3. Configure `allowedRoots` with the narrowest parent directories that IDEA may open.
4. Run `scripts/install-idea-bridge <extension-id>` and reload Chrome.

The host resolves paths, rejects paths outside the allowlist, accepts only the `open` action, and never writes project files.
