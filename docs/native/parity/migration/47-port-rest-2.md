# Migration 47 — Flutter platform, Linux and protocol (part 2)

Counts: **already there 7 · ported now 0 · not needed 25 · blocked 0** (32/78 reviewed).

Base: `b6c30a59983a145a4d9cf0bcc3d1ec0295c0d6bc`, branch `p12x/47-port-rest-2`.
Implementation review only; no tests or device qualification. Binding design: `docs/design/native-rewrite-2026-11-01/plan/01-architecture-decision.md` and `03-architecture-modules.md`.

- `lib/network/browser_bindings.dart` — not needed: DOM fetch/WebSocket JS interop; the binding native design targets Android and compiles desktop, with no browser product (01 §1, §7).
- `lib/network/platform_dio.dart` — not needed: Dart conditional adapter selection and Dio stream detection; native Ktor streaming is configured directly in `native/core/net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/Http.kt:160`.
- `lib/network/platform_dio_io.dart` — not needed: an empty Dio configurator; native owns its OkHttp/Ktor client directly.
- `lib/network/platform_dio_stub.dart` — not needed: an empty fallback for Dart targets with neither IO nor JS; there is no corresponding native target.
- `lib/network/platform_dio_web.dart` — not needed: browser Fetch streaming, CORS and AbortController adapter; native transports use sockets, not DOM APIs.
- `lib/network/platform_websocket_stub.dart` — not needed: unsupported-Dart-target fallback; the native Android/JVM socket implementation replaces conditional Dart selection.
- `lib/network/platform_websocket_web.dart` — not needed: DOM WebSocket adapter and browser-origin error explanations; no browser product is in the native design.
- `lib/network/transport_failure_stub.dart` — not needed: returns false because a non-IO Dart target has no resolver details; native JVM has typed resolver failures.
- `lib/network/transport_failure_web.dart` — not needed: browser-only resolver opacity fallback; native JVM has typed resolver failures.
- `lib/orchestration/adapters/fixture/project_fixture/advance.dart` — not needed: simulated task ticks, fabricated usage/diffs and simulated merge/promotion receipts belong to the AI Team demo; AI Team follows the switch under binding 01 §12 D6, and no fake runtime is ported.

- `lib/termux/git_shell.dart` — not needed: External-Termux Git execution is deliberately retired with ADR 01 D7 migrate-out-only; the replacement phone Git shell runs in the built-in Ubuntu with real profile fencing at `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/BuiltinGitShell.kt:27`, selected at `GitWiring.kt:174` (no Termux host management).
- `lib/termux/scripts/aiteam_script.dart` — not needed: AI Team script installation, detached verbs, logs and projects belong to the deferred AI Team and retired Termux host paths (ADR 01 D6/D7); migrate-out never starts or installs a team.
- `lib/termux/scripts/install_and_serve_script.dart` — not needed: Starts/repairs a managed Termux server and stores its credential there; ADR 01 D7 and plan 07 X1 retire that host-management journey while retaining migration only.
- `lib/termux/scripts/local_agents_script.dart` — not needed: Installs, starts, signs into and removes Claude/Paseo inside external Termux Ubuntu; ADR 01 D7 retires Termux as runtime host, while phone agents use the built-in Linux host.
- `lib/termux/scripts/project_scripts.dart` — not needed: Creates folders and arms recovery for a managed Termux server; ADR 01 D7/plan 07 X1 retire Termux host management, and migrate-out only reads existing folders.
- `lib/termux/scripts/restart_script.dart` — not needed: Restarts/stops/reclaims ownership of a managed Termux server; ADR 01 D7/plan 07 X1 deliberately remove these management actions.
- `lib/termux/scripts/script_support.dart` — not needed: Constants and shell quoting for the retired Termux host scripts; migration owns its separate transport and does not need the server-manager wrapper.
- `lib/termux/scripts/setup_base_script.dart` — not needed: Bootstraps a new Termux Ubuntu container; ADR 01 D7/plan 07 X1 exclude new Termux installations, keeping read-only source inspection and migrate-out instead.
- `lib/termux/scripts/status_scripts.dart` — not needed: Reports managed Termux installation/setup/storage/diagnostics state; ADR 01 D7 removes its setup and running pages, with migrate-out using its own inspection contract.
- `lib/termux/scripts/tools_script.dart` — not needed: Storage scan/cache cleanup and process management target external Termux; those pages are explicitly dropped by plan 07 F17/X1 under ADR 01 D7, while built-in Linux tools remain separate.

- `lib/orchestration/adapters/fixture/project_fixture/apply.dart` — not needed: simulated AI Team commands, fabricated commits/checks and demo task transitions; AI Team is deferred by binding 01 §12 D6.
- `lib/orchestration/adapters/fixture/project_fixture/helpers.dart` — not needed: demo plan generation, synthetic findings, simulated review/merge requests and fixture bookkeeping for deferred AI Team (01 §12 D6).
- `lib/orchestration/adapters/fixture/project_fixture/seed.dart` — not needed: canned servers, roles, projects, spend and timeline for a simulated AI Team workspace; no real native runtime may be seeded with this data (01 §12 D6).
- `lib/platform/browser_context_io.dart` — not needed: the constant secure-context answer is Dart conditional-import plumbing; native Android storage has no browser-origin security context (ADR §1, §7.3).
- `lib/platform/browser_context_web.dart` — not needed: window.isSecureContext serves Flutter web, a platform outside the native rewrite's Android/desktop/iOS design (ADR §1).

## Compile-only checkpoints

Batch 1: documentation only; Gradle skipped because this commit changes no Kotlin or Gradle file. No tests written or executed. No APK, push or release.

Batch 2: ten retired Termux-path dispositions; documentation only, so Gradle skipped.

Batch 3: seven existing update/voice implementations and five fixture/browser dispositions; documentation only, so Gradle skipped.
