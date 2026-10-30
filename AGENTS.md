# AGENTS.md

Working notes for coding agents in this repo. The human-facing equivalents are
[CONTRIBUTING.md](CONTRIBUTING.md) (gates, boundaries) and
[docs/technical-overview.md](docs/technical-overview.md) (architecture depth).

## Toolchain — pinned, not advisory

- Flutter **3.47.1** from Shorebird's cache:
  `~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`.
  A different local Flutter may fail to resolve packages or produce different
  analyzer results.
- JDK 17 (temurin), Android SDK API 37, Shorebird CLI 1.6.x (release/patch only).

## Commands and order

These are the **stable integration-candidate gates**, not a loop to repeat after
every edit. During development, follow the focused-check ladder below.

```bash
flutter pub get
flutter analyze                 # must be clean; no new ignores
flutter test --concurrency=1    # serial in the phone-hosted container; a workstation may use -j 3
```

- Before every hand-back and merge: `tool/qa/preflight.sh <base>` (about 40 s:
  commit rules, format, l10n output, QA tool tests and the repo-scanning
  ledger/ratchet tests that caused most repeated full runs).
- Single file: `flutter test --concurrency=1 test/offline_queue_test.dart`.
- Full run with the build server: `tool/qa/run_tests_split.sh` (about 13 min;
  commit first). Goldens run on both machines (per-image limits in
  `test/goldens/tolerances.json`), small files run in aggregates, and any
  failure is re-run alone before it counts. Files that cannot share a process
  go in `tool/qa/agg_exclude.txt` with a reason.
- Fast full run on this machine only: `tool/qa/run_tests_fast.sh` (4 timing-balanced shards × `--concurrency 2`, one machine_lock slot each; failing files in `build/traycer/fast-*/summary.txt`). CI runs the same runner as 6 shards.
- If the shell kills long runs, split the suite and run the chunks one after
  another: `find test -name '*_test.dart' | sort | split -n l/6 - "${TMPDIR:-/tmp}/chunk_"`
  (this includes nested directories and goldens).
- Optional live-server checks (no emulator): use the OpenCode 2 scratch probes in
  `tool/qa/oc2_live_proof_test.dart` with an isolated data root and Basic auth.
  Never probe the owner's live servers or print credentials.
- Release APK off this PC: `tool/qa/vps_build_apk.sh <build-number>` builds on the
  build server with a throwaway key, then re-signs here with the real key and fails
  unless the signer is the local release key (output in `build/vps-apk/`).
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

- `lib/api2/` handwritten OpenCode 2 client (Basic auth, `/api` prefix,
  91-event union, durable session log);
  `lib/domain/` protocol-neutral gateway (`ServerGateway`,
  `ServerOperationsGateway`, `ServerCapabilities`);
  `lib/state/` profiles/Keystore, `ConnectionController`, offline queue;
  `lib/termux/`, `lib/background/`, `lib/voice/`, `lib/platform/` native
  bridges; `lib/ui/` screens and widgets.
- **Kit only (owner rule, 2026-09-26):** every UI component comes from `lib/ui/kit/`.
  Outside the kit, `lib/ui/` only arranges kit parts with layout, scrolling, builder,
  semantics and focus widgets (the allowlist is in `docs/ux-system/kit-v2.md` §9).
  A missing part is added to the kit, even if only one screen uses it; never
  hand-build it on the screen. `test/kit_ratchet_test.dart` (G16) keeps the count
  per file going down only.
- **Teach what is hidden (owner rule, 2026-10-21):** any gesture or feature a person
  can't see on screen (long-press, swipe, hold, a power-user path, a new capability)
  ships with a built-in tip from the teaching system (`docs/ux-system/teaching.md`):
  a short contextual tip shown at the moment it becomes useful, at most twice, one
  at a time, never blocking. A feature bigger than one gesture gets a mini tutorial
  (2–4 steps). Every tip and tutorial is listed in Help › Tips so it can be replayed,
  and "Show tips again" resets them. A hidden path is never the only path: there is
  always a visible way too.
- UI talks to the domain gateway only — never `api/` or `api2/` directly.
  Gate features on `ServerCapabilities` flags, never on the flavor enum.
- Treat as single-owner units (one editor at a time): `lib/state/connection.dart` **plus every `connection/*.dart` part file**,
  `lib/ui/screens/chat_screen.dart` **plus every `chat/*.dart` part file as one
  library**, `lib/domain/server_gateway.dart`, `lib/domain/product_repository.dart`,
  `lib/main.dart`, `lib/l10n/` output, each protocol cluster, and both halves of
  any MethodChannel (`oc/termux`, `oc/voice`, `oc/camera`, `oc/background`,
  `oc/share`, `oc/byo_host_signer`).
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

- Nothing in `lib/background/` may assume unbounded lifetime: Android 15+
  caps `dataSync` foreground services at 6 background hours per rolling 24h,
  and the battery-optimization exemption does not lift that.
- Copy lives in `lib/l10n/app_en.arb`, with an Arabic `app_ar.arb`; strings not
  yet moved out of the code are listed in `docs/localization-todo.md`.
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

- Each feature gets its own branch and Git worktree, owned by one agent: the
  complete user journey, implementation, localization, tests, screenshots and
  local commits. Keep a worktree to one feature and never edit another agent's
  checkout; shared-library single ownership applies within each worktree. The
  coordinator reviews the whole branch, resolves conflicts and merges after
  validation.
- A new or changed screen in `lib/ui/` needs its catalog use-case in
  `widgetbook/` (the real screen with fake data, in its main states): see
  `widgetbook/README.md`. `dart run tool/coverage_check.dart` inside
  `widgetbook/` fails on a new uncatalogued screen; its baseline only shrinks.
  Every use-case is also checked against the screen standard
  (`docs/ux-system/screen-standard.md`) by `widgetbook/test/standard_check_test.dart`.
- CI budget is limited: every commit carries `[skip ci]`, checks run locally,
  and pushes, PRs, CI runs, signing and releases each need a fresh explicit
  request. Serialize machine-heavy Flutter/native checks across worktrees.
- A replacement APK must be signed with the same certificate as the build on
  the maintainer's phone, or Android refuses the update and only an uninstall
  (which loses local data) gets past it. Ask for the installed certificate's
  SHA-256 and compare it with `apksigner verify --print-certs` before delivery.
  Known signers: stable CI `2D010C2103CB2F78ABAACA690EAD4D45F8003A6C0A02082CD2A2AE62FD18D0EC`,
  local release key `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`.
  Never substitute or rotate a signer.
- `master` is fast-forwarded only at approved milestones — see
  `docs/verification/` for the branch ledger.
- Releases are Shorebird releases (CI tag build or `scripts/release.sh sideload`); Dart-only fixes ship as `scripts/release.sh patch` against the exact released version; native/asset/pubspec changes need a new release.
- Releases/signing go through `scripts/release.sh` / `scripts/cut-alpha.sh`
  (master-only, clean tree, dry-run by default). Never publish, tag, or use
  signing secrets without explicit maintainer approval.
- PRs need: tests + local serial `flutter test` result, screenshots for UI
  changes, accessibility notes, privacy/security notes for credential/data
  changes, migration notes for stored-format changes.

## Productivity rules — finish usable slices

1. **One active product slice.** Write a one-sentence finish line and an explicit
   non-goal before editing. Finish controller → UI → persistence/deletion before
   opening another feature. A scaffold or opt-in backend is groundwork, not a
   completed user feature. Example: stash is done when save → restart → restore
   → profile deletion works, not when the vault class exists.
2. **Check feasibility before building adapters.** Confirm the current callable
   contract, supported authentication and permitted credential use first. A
   historical schema/internal endpoint is not enough. If the prerequisite fails,
   keep the feature unavailable and move to runnable product work; do not build
   a workaround first and withdraw it later.
3. **Timebox discovery.** Start with current source and existing evidence. Target
   ten minutes for a bounded question; then implement, name a blocker, or explain
   why more research is necessary. Reopen research only for a new contradiction,
   changed contract or unresolved implementation decision—not another summary.
4. **Parallelize execution, not repeated audits.** For substantial work, use at
   least three independent slices when ownership permits. Freeze method names,
   arguments, results and errors before parallel editing. Record read/write
   sets, dependency, acceptance and focused checks. Shared libraries remain
   single-owner. Reviewers never launch test processes. If a worker is blocked
   by quota or permissions, take over that slice yourself.
5. **Use a check ladder.** Format changed files; run the new/affected test file
   once the logical change is ready. After a failure, rerun that file or exact
   test (`--plain-name`) until fixed—not the repository. Run the analyzer at an
   integration checkpoint. Generate localization once after copy settles. Docs-
   only changes need a diff/link check, not Flutter tests.
6. **Budget the final gate before starting it.** This environment's serial suite
   has taken 27–40 minutes. At the agreed stable batch boundary, run the required
   full suite in bounded serial chunks from a complete test-file manifest,
   including nested goldens. Record candidate revision/diff, commands, completed
   files, skips and failures. If interrupted, resume only uncompleted files for
   the unchanged candidate. Subsequent relevant edits invalidate that coverage;
   never combine incompatible snapshots into a claimed pass, and never call
   partial coverage a completed gate.
7. **Keep an evidence budget.** Progress updates should say what now works, what
   remains, and the next concrete action. Append verification results at the
   slice boundary. Record unexpected blockers once and return to code.
8. **Separate shipping states.** Report implemented / enabled / verified /
   committed / deployed / released distinctly. Lines changed and test counts
   are not user value. Native CI, signing and publication cannot substitute for
   unfinished product integration. “Get shipping” is not permission to touch
   live servers, credentials, tags, signing keys or public releases; obtain the
   required explicit approval first.
