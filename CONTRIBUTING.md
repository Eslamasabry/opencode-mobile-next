# Contributing

Thanks for looking at OpenCode mobile. This file is the short, factual version
of how the project is built and what a change has to satisfy.

## Toolchain

| Tool | Pinned version |
|---|---|
| Flutter | 3.47.2 — Shorebird's cached copy, `~/.shorebird/bin/cache/flutter/<rev>/bin/flutter` |
| JDK | 17 (temurin) |
| Android SDK | API 37 |
| Shorebird CLI | 1.6.x, only for release/patch work |

The Flutter pin is not advisory. Release artifacts and the test suite are
validated against 3.47.2; other local Flutters may fail to resolve packages or
produce different analyzer results. Use the pinned binary for anything you
intend to submit.

## The gates a change must pass

```bash
flutter pub get
flutter analyze                      # must be clean, no new ignores
flutter test --concurrency=1         # serial; see "Tests" below
```

CI ([android-quality.yml](.github/workflows/android-quality.yml)) runs the same
two gates plus generated-SDK verification, Android lint, and a test-signed
release compile whose artifact is never distributed. It splits the suite into
thirds so one hung invocation cannot eat the job timeout.

Two optional integration checks run against a live server, no emulator needed:

```bash
opencode serve --port 4123 &
dart run tool/smoke_test.dart http://127.0.0.1:4123 /server/project
dart run tool/prompt_test.dart http://127.0.0.1:4123   # needs model auth
```

## Tests

- **Run them serially.** `--concurrency=1` is the repo default; parallel runs
  are flaky and, on some machines, get killed outright.
- If your shell or harness kills long-running commands, split the suite rather
  than parallelizing it:
  `ls test/*.dart | split -n l/6 - /tmp/chunk_`, then
  `flutter test --concurrency=1 $(cat /tmp/chunk_aa)` per chunk.
- **Any test that touches `ProfileStore.load`/`upsert` must mock the
  `plugins.it_nomads.com/flutter_secure_storage` channel**, or it hangs
  forever waiting for a platform reply that never comes. See
  `test/offline_queue_test.dart` for the pattern.
- Tests assert behavior, not implementation. Widget keys exist for the ones
  that need a handle; prefer finding by user-visible text otherwise.

## Architecture boundaries

```
lib/api/          v1 client: DTOs, Dio HTTP client, /event SSE with reconnect
lib/api2/         OpenCode 2 client: Basic-auth transport, typed models, SSE
lib/domain/       protocol-neutral gateway over v1 and v2
lib/state/        profiles (Keystore secrets), ConnectionController, offline queue
lib/termux/       Dart side of the Termux RUN_COMMAND bridge
lib/background/   foreground service, live-session notifications, home widget
lib/voice/        sherpa-onnx Whisper: model manager, downloader, recognizer
lib/ui/           screens and widgets
```

UI talks to the gateway, never to `api/` or `api2/` directly. Capability flags
decide what a server generation supports — do not branch on the flavor enum in
UI code.

Two rules worth stating explicitly, because breaking them is a security
regression rather than a style issue:

- **Every URL the app did not author goes through `openExternalLink`**
  (`lib/ui/widgets/external_link.dart`). Never call `launchUrl` on a value that
  came from a server, a form field, or a network response.
- **Anything keyed to a server profile must be deleted with it.** If you add
  per-profile storage, name the key `oc.<what>.<profileId>` so the deletion
  sweep in `ProfileStore.profileScopedPreferenceKeys` finds it, and extend
  `ConnectionController.deleteProfileAndLocalData` if it lives in a shared
  blob.

## Generated code

`packages/opencode_sdk/` is generated from the checked-in OpenAPI contracts in
`contracts/`. Do not hand-edit it. CI re-verifies the generated tree, its
manifest, and its coverage matrix against the contract, and fails on drift.

## Pull requests

Include, where they apply:

- what changed and why, in the commit message body — this repo's history is
  written to be read;
- tests covering the new behavior, and the local `flutter test` result;
- screenshots for visible UI changes;
- accessibility notes (labels, tap targets, text scale) for new surfaces;
- privacy/security notes when a change touches credentials, stored data,
  external links, or notifications;
- migration notes when a stored format changes.

## Triage

New reports arrive with `bug` + `needs triage` from the in-app **Report a bug**
link and the issue template. Triage them like this:

1. **Redaction check first.** If the report contains a server password, a
   provider API key, or a real hostname the reporter probably did not mean to
   share, edit it out before anything else and say so in a comment.
2. **Is it ours?** Server or model misbehaviour goes upstream
   (see [SUPPORT.md](SUPPORT.md)); close with a link. Keep it here if the app
   renders, stores, or sends something wrong.
3. **Can you reproduce it?**
   - Yes: drop `needs triage`, add a priority (`P0` breaks connect/send/approve
     or loses data; `P1` a feature does not work; `P2` everything else) and a
     platform label (`device:android`, `device:linux`, `device:windows`).
     Add `ux` or `accessibility` when that is the nature of the defect.
   - No: add `needs-repro` and ask for exactly one thing — usually the
     redacted diagnostics from Settings → App diagnostics, or the server
     version and flavor (OpenCode 1 or 2). Close after 30 days without reply.
4. **Old preview?** If the app version is behind the current release, ask the
   reporter to retest on the current APK before spending time on it.
5. **Duplicate?** Link the original, label `duplicate`, close.

Anything a newcomer could finish in an evening gets `good first issue` and a
one-paragraph pointer to the files involved.

## Security issues

Do not open a public issue for a vulnerability. Report it privately through
GitHub's private vulnerability reporting on this repository.

## Developer agent skills

Prompt packs under `.claude/skills/` are **not** part of this repository. They
are third-party content installed locally by whoever wants them, and their
licensing is theirs, not this project's. `.claude/skills/` is gitignored;
install what you need locally and do not commit it. What was previously
committed there, and what a future pack would have to record before it could
be, is in
[docs/internal/developer-skills.md](docs/internal/developer-skills.md).

## Things that will waste your afternoon

Hard-won facts that are not discoverable from the code:

- **Debug APK builds do not work against the Shorebird-pinned engine.** The
  Shorebird artifact mirror carries release engine jars only. Use
  `flutter build apk --release` for compile checks; without
  `android/key.properties` it fails at `validateSigningRelease`, which still
  proves the Kotlin and Dart sides compile.
- **`flutter_animate` is not allowed in this repo.** It leaves pending timers
  that fail widget tests. Use the framework's own animation APIs.
- **Android 15+ caps `dataSync` foreground services** at six background hours
  per rolling 24-hour period. The battery-optimization exemption the app can
  request does not lift that platform limit, so nothing in
  `lib/background/` may assume an unbounded live session.
- **Never echo provider credentials.** `/config/providers` returns API keys;
  they must not reach logs, diagnostics, notification copy, or test output.
- **Verifying against an OpenCode 2 beta server:**
  `opencode2 serve --port 4097 --hostname 127.0.0.1`. It prints a per-run
  password on its "server password" line (HTTP Basic user `opencode`) — never
  commit or echo it. Known beta quirk: the experimental session-log endpoint
  replays nothing, so transcripts reconcile by refetch rather than replay.

## Project history

This repository used to carry an append-only engineering working log at
`docs/internal/handoff.md`. It was internal scratch — machine-specific paths,
device names, and release claims that went stale the week they were written —
and it has been removed from the tree. Git history still has it if you need
archaeology. Current facts live in the README, this file, PRIVACY.md,
[SECURITY.md](SECURITY.md), and the GitHub releases page.
