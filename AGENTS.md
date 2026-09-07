# AGENTS.md

Working notes for coding agents in this repo. The human-facing equivalents are
[CONTRIBUTING.md](CONTRIBUTING.md) (gates, boundaries) and
[docs/technical-overview.md](docs/technical-overview.md) (architecture depth).

## Toolchain — pinned, not advisory

- Flutter **3.47.2** from Shorebird's cache:
  `~/.shorebird/bin/cache/flutter/<rev>/bin/flutter`. A different local Flutter
  may fail to resolve packages or produce different analyzer results.
- JDK 17 (temurin), Android SDK API 37, Shorebird CLI 1.6.x (release/patch only).

## Commands and order

```bash
flutter pub get
flutter analyze                 # must be clean; no new ignores
flutter test --concurrency=1    # serial — parallel runs are flaky or killed
```

- Single file: `flutter test --concurrency=1 test/offline_queue_test.dart`.
- If the shell kills long runs, split the suite — do not parallelize:
  `ls test/*.dart | split -n l/6 - /tmp/chunk_`, then run each chunk.
- Generated SDK package has its own checks: `dart analyze` and `dart test`
  inside `packages/opencode_sdk/`.
- Optional live-server checks (no emulator):
  `opencode serve --port 4123 &` then
  `dart run tool/smoke_test.dart http://127.0.0.1:4123 /server/project`;
  `tool/prompt_test.dart` additionally needs model auth.
- Android compile check: `flutter build apk --release`. Debug APKs **do not
  work** against the Shorebird-pinned engine (release engine jars only).
  Without `android/key.properties` the build fails at `validateSigningRelease` —
  that still proves the Kotlin and Dart sides compile.

## Testing traps

- Any test touching `ProfileStore.load`/`upsert` must mock the
  `plugins.it_nomads.com/flutter_secure_storage` method channel or it hangs
  forever. Copy the pattern from `test/offline_queue_test.dart`.
- Widget keys exist only where a test needs a handle; find by user-visible
  text otherwise. Tests assert behavior, not implementation.
- `flutter_animate` is banned (pending timers fail widget tests). Use the
  framework's animation APIs.

## Architecture boundaries

- `lib/api/` OpenCode 1 client (generated SDK + SSE `/event`);
  `lib/api2/` handwritten OpenCode 2 client (Basic auth, `/api` prefix,
  91-event union, durable session log);
  `lib/domain/` protocol-neutral gateway (`ServerGateway`,
  `ServerOperationsGateway`, `ServerCapabilities`);
  `lib/state/` profiles/Keystore, `ConnectionController`, offline queue;
  `lib/termux/`, `lib/background/`, `lib/voice/`, `lib/platform/` native
  bridges; `lib/ui/` screens and widgets.
- UI talks to the domain gateway only — never `api/` or `api2/` directly.
  Gate features on `ServerCapabilities` flags, never on the flavor enum.
- Treat as single-owner units (one editor at a time): `lib/state/connection.dart`,
  `lib/ui/screens/chat_screen.dart` **plus every `chat/*.dart` part file as one
  library**, `lib/domain/server_gateway.dart`, `lib/api/product_repository.dart`,
  `lib/main.dart`, `lib/l10n/` output, each protocol cluster, and both halves of
  any MethodChannel (`oc/termux`, `oc/voice`, `oc/camera`, `oc/background`,
  `oc/share`).
- OpenCode 2 event stream is volatile: after reconnect, reconcile by refetch,
  not replay (the beta session log replays only durable events; deltas and
  `tool.progress` never replay). Wire truth: `docs/opencode2-protocol-notes.md`.

## Security invariants — breaking these is a security regression

- Every URL the app did not author goes through `openExternalLink`
  (`lib/ui/widgets/external_link.dart`). Never `launchUrl` a value from a
  server, form field, or network response.
- Per-profile storage keys must be named `oc.<what>.<profileId>` so the
  deletion sweep in `ProfileStore.profileScopedPreferenceKeys` finds them;
  extend `ConnectionController.deleteProfileAndLocalData` for shared blobs.
- Never echo provider credentials: `/config/providers` returns API keys — they
  must not reach logs, diagnostics, notification copy, or test output.

## Repo-specific facts

- `packages/opencode_sdk/` is generated from `contracts/` via
  `tool/sdk/generate.sh`. Never hand-edit; CI fails on drift.
- Nothing in `lib/background/` may assume unbounded lifetime: Android 15+
  caps `dataSync` foreground services at 6 background hours per rolling 24h,
  and the battery-optimization exemption does not lift that.
- App is English-only; `l10n.yaml`/`app_en.arb` are wired but most strings are
  still hardcoded (`docs/localization-todo.md`).
- Verifying against a live OpenCode 2 beta: `opencode2 serve --port 4097
  --hostname 127.0.0.1`, HTTP Basic user `opencode` with the per-run password
  it prints — never commit or echo that password.
- `.claude/skills/` is gitignored third-party content. Do not commit it.
- The Termux-hosted dev container shares the phone with the live session
  server: never kill processes by pattern — `pkill -f "opencode serve"`
  matches the session's own server and drops the connection. Capture and
  kill test servers by exact PID, and check listeners by port or
  `ps -p <pid>`, not by matching command lines.

## Workflow

- Replacement APKs for the maintainer must keep the installed stable CI signer: `2D010C2103CB2F78ABAACA690EAD4D45F8003A6C0A02082CD2A2AE62FD18D0EC`. Use the Android quality workflow for these updates, verify the APK certificate before delivery, and never substitute or rotate the signer.

- Work lands on `dev` through PRs; `master` is fast-forwarded only at approved
  milestones — see `docs/verification/` for the branch ledger.
- Releases/signing go through `scripts/release.sh` / `scripts/cut-alpha.sh`
  (master-only, clean tree, dry-run by default). Never publish, tag, or use
  signing secrets without explicit maintainer approval.
- PRs need: tests + local serial `flutter test` result, screenshots for UI
  changes, accessibility notes, privacy/security notes for credential/data
  changes, migration notes for stored-format changes.
