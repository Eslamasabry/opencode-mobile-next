# Migration 47 — Flutter platform, Linux and protocol (part 2)

Counts: **already there 0 · ported now 0 · not needed 10 · blocked 0** (10/78 reviewed).

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

## Compile-only checkpoints

Batch 1: documentation only; Gradle skipped because this commit changes no Kotlin or Gradle file. No tests written or executed. No APK, push or release.
