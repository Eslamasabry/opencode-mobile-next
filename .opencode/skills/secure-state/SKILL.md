---
name: secure-state
description: Use for credentials, profiles, persistence, pairing, lifecycle, diagnostics, notifications, external URLs, or data deletion.
---

# Secure State Workflow

Treat a configured OpenCode connection as shell access.

1. Classify state scope: app, profile, location, session, message, or request.
2. Include every required identity in cache and persistence keys.
3. Guard async completion with lifecycle, generation, repository, profile, and
   location identity where relevant.
4. Keep credentials in secure storage and out of URLs, logs, errors,
   diagnostics, notifications, tests, and screenshots.
5. Permit cleartext HTTP only to loopback.
6. Route every non-app-authored URL through the shared external-link policy.
7. Name new profile preferences `oc.<what>.<profileId>` and prove deletion.
8. For shared blobs, extend profile deletion and test failed writes/removals.
9. Bound persisted data by count, bytes, or age and degrade safely on corruption.
10. Mock secure storage in every test touching `ProfileStore.load` or `upsert`.

Test restart behavior, stale completion, profile switching, failed persistence,
negative calls, redaction, and complete deletion. Never use real credentials.
