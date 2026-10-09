# BD3 read-only connector search — 2026-10-09

Owner addition to `docs/design/BD3-mcp-chat-contract.md`. Contract-first commit:
`35488eb81`. Implementation: `e3c72c90bfe6e427f30f75945d929a4faab7796f`. Existing commits were not amended.

## Implemented

`oc-ui.find_connectors` searches the same saved profile registry cache used by
Tools > MCP. The generated MCP helper round-trips to a bounded app-owned
loopback HTTP bridge. It cannot fetch the public registry, install, connect,
start OAuth, or obtain a credential. Output is at most ten whitelist-projected
catalog matches. Description text is single-line, redacted, and URL-free.
Current registry auth metadata is insufficient: sign-in is always `unknown`.
Connection status comes only from an exact active source inventory; unknown
project/workspace or unavailable inventory produces null rather than a guess.

The bridge has an independent random bearer, request/reply/concurrency/deadline
bounds, and source checks before and after reads. The app writes the descriptor
directly into its existing private runtime directory. No bearer goes through a
shell script, process argv, diagnostics, or preferences. Empty temporary files
are chmod 0600 before contents are written and atomically renamed; missing or
symlinked runtime paths are rejected. No new native API/dependency is needed.
Existing runtime tool permissions remain: only show has automatic preallow.
The install verifier now accepts the exact two-tool helper advertisement.

## Verification

Pinned Flutter 3.47.1; every Flutter test/analyzer invocation used
`tool/qa/machine_lock.sh`, with one file set at a time and concurrency=1.

- `red.txt`: initial missing domain/bridge APIs and absent helper tool failures.
- `connection-red.txt`: missing ConnectionController search integration.
- `adapter-red.txt`: missing adapter identity API before implementation.
- `bounds-mutation.txt`: deleting the 100-row scan limit fails its regression.
- `retry-mutation.txt`: deleting failed-publication key reset fails retry coverage.
  Mutations were restored in finally blocks.
- `focused-tests.txt`: 142 passing tests across catalogue, bridge, helper,
  publisher, real helper-to-bridge roundtrip, ConnectionController, adapter,
  installer, existing permission security and file-size ratchet suites.
- `final-affected-tests.txt`: final 25 catalogue/ConnectionController tests pass
  after source-identity/retry hardening. The other 117 tests' source files did not
  change after the 142-test run. The manifest captures final Dart hashes after behavior-neutral analyzer cleanup
  (braces, an unused abstract getter and a wildcard parameter).
- `analyze.txt`: repository analyzer clean, no issues (21.5 seconds).

The roundtrip executes the generated Node MCP helper against the real Dart HTTP
bridge and pure catalogue search. Controller tests exercise real cache reads,
exact inventory, project invalidation, disabling cards, cache replacement,
missing cache and failed-publication retry. Publisher tests exercise real
private file writes, atomic replacement, runtime path selection, permissions,
symlink refusal and failure paths. No real account, public registry request,
model request, production server, emulator, Gradle/APK build, or native changes.
The existing serial full-suite gate remains coordinator-owned.

## Runtime limits and handoff

Search is callable only on the same installed, qualified Agent-cards helper.
Current OC1 and Claude/Paseo card qualification are reused; OC2 and other ACP
agents do not gain qualification. Paseo search can return cached metadata but
cannot confirm connected MCP servers through its current API. Existing chat
connect/OAuth/tool-refresh flags are unchanged. OC1 next-step tools-ready,
OC2 readiness-unknown, and Paseo runtime mutation unavailable remain as in the
parent contract. No phone search or provider OAuth qualification is claimed.

An already-running old helper needs its normal reconnect/reload to advertise
the new tool. There is no forced restart. The UI owner can render the trusted
search call as “Searched connectors ›” and use returned catalogId in a reviewed
connector card. MCP install/connect remains a separate explicit user action.
