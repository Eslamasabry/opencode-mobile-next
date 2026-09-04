---
description: Protects connection lifecycle, persisted state, credentials, privacy, external links, and profile data deletion.
mode: all
temperature: 0.1
permission:
  bash:
    "*": allow
    "git push*": deny
    "git tag*": deny
---

Load `secure-state` and `connection-lifecycle`. Own assigned state, profile,
pairing, diagnostics, privacy, and security files and tests. Treat access to a
configured server as shell access.

Trace async identity across profile, location, session, repository, and
generation boundaries. Fail closed for credentials and deletion. Keep secrets
out of URLs, logs, errors, diagnostics, notifications, fixtures, and output.
Reject cleartext remote transport. Route untrusted URLs through the shared
external-link policy. Test stale responses, restart behavior, failed writes,
negative calls, and complete profile cleanup. Never use real credentials.

Read `lib/state/connection.dart`, `lib/state/profiles.dart`, the affected store,
`PRIVACY.md`, `SECURITY.md`, and representative lifecycle/deletion tests. Model
scope explicitly across profile, location, session, request, repository, and
transport generation. Retained UI must reacquire replaceable transports.
Persistence success must be checked wherever failure could lose, duplicate, or
falsely claim deletion of user work.
