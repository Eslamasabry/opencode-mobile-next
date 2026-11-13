# Contributing

Thanks for looking at OpenCode Mobile. This file is the short, factual version of how the project is built and what a
change has to satisfy. The app is native Kotlin (Kotlin Multiplatform, Jetpack Compose) and lives under `native/`.

## Toolchain

| Tool | Version |
|---|---|
| JDK | 21 to run Gradle (Metro's plugin needs it); 17 installed too (module bytecode targets 17) |
| Android SDK | `platforms;android-37.1`, `build-tools;37.0.0`; `native/local.properties` holds `sdk.dir` |
| Gradle | the wrapper in `native/` |

Details and the module rules: [native/AGENTS.md](native/AGENTS.md).

## The checks a change must pass

Run the tasks for what you touched, never `./gradlew build`:

```bash
cd native
./gradlew :<module>:desktopTest          # the module's tests (KMP modules; JVM modules use :test)
./gradlew :quality:test                  # architecture, kit-only, privacy and release guards
./gradlew :tools:strings:test            # when user-visible copy changed (the drift check)
./gradlew -p build-logic test            # when build-logic changed
```

CI ([native-ci.yml](.github/workflows/native-ci.yml)) compiles the app, runs the desktop tests of the changed modules
in shards (or every module on the full lane) and builds a preview-signed APK. It runs on dispatch, not on every push.

New or changed copy goes through the strings pipeline (`native/tools/strings`, `translate.py plan/apply`); a missing
translation fails `:tools:strings:test`.

Optional live checks target a scratch OpenCode 2 server on an unused port. Supply its password in
`OC2_LIVE_PASSWORD`, never as a command-line argument or in committed output, and stop only the process you started.

## Tests

- Name every test after its catalog row: `B-AREA-NNN what it proves`. Write it red first.
- Tests assert behaviour, not implementation. Find by visible text; add a test tag only where a test needs a handle.
- Name test classes with `--tests` when you run them; picture-gate scenes that are red on purpose bury a broad run.

## Boundaries

The module layers (`core`, `protocol`, `engine`, `ui`, `feature`, `android`, `tools`) and what may depend on what are
enforced by the build (`./gradlew -p build-logic test`, `:quality:test`). In short:

- Features only arrange kit parts (`native/ui/kit*`). A missing part is added to the kit, never drawn in a feature.
- UI gates on `ServerCapabilities` / `PlatformCapabilities`, never on the kind of backend.
- Engines own the truth: `StateFlow` out, suspend commands returning `Outcome` in.

Two rules worth stating explicitly, because breaking them is a security regression rather than a style issue:

- **Every URL the app did not author goes through `ExternalLinkPolicy` → `ExternalLinkOpener`.** Never open a value
  that came from a server, a form field or a network response any other way.
- **Anything keyed to a server profile must be deleted with it.** Per-profile keys are `oc.<what>.<profileId>` and are
  registered with `ProfileScope`; per-profile files and tables too.

## Pull requests

Include, where they apply:

- what changed and why, in the commit message body; this repository's history is written to be read;
- tests covering the new behaviour, and the CI run that ran them;
- screenshots for visible UI changes;
- accessibility notes (labels, tap targets, text scale) for new surfaces;
- privacy/security notes when a change touches credentials, stored data, external links or notifications;
- migration notes when a stored format changes.

## Triage

New reports arrive with `bug` + `needs triage` from the in-app **Report a bug** link and the issue template:

1. **Redaction check first.** Edit out a server password, a provider API key or a real host name the reporter
   probably did not mean to share, and say so in a comment.
2. **Is it ours?** Server or model misbehaviour goes upstream (see [SUPPORT.md](SUPPORT.md)); close with a link.
3. **Can you reproduce it?** Yes: drop `needs triage`, add a priority (`P0` breaks connect/send/approve or loses data;
   `P1` a feature does not work; `P2` everything else). No: add `needs-repro`, ask for exactly one thing (usually the
   report code from Settings → Help → Send a problem report), close after 30 days without reply.
4. **Old version?** Ask for a retest on the current release first.
5. **Duplicate?** Link the original, label `duplicate`, close.

## Security issues

Do not open a public issue for a vulnerability. Report it privately through GitHub's private vulnerability reporting
on this repository.

## Developer agent skills

Prompt packs under `.claude/skills/` are **not** part of this repository: third-party content installed locally,
gitignored, never committed. See [docs/internal/developer-skills.md](docs/internal/developer-skills.md).

## Things that will waste your afternoon

- **Android 15+ caps `dataSync` foreground services** at six background hours per rolling 24 hours. The
  battery-optimization exemption does not lift it, so no background code may assume an unbounded live session.
- **Never echo provider credentials.** `/config/providers` returns API keys; they must not reach logs, diagnostics,
  notification copy or test output.
- **An APK installs over another only with the same signer.** Check with `apksigner verify --print-certs` before
  handing one over; see [docs/native/release-identity.md](docs/native/release-identity.md).
- **OpenCode 2's event stream is volatile:** after a reconnect, reconcile by refetch, not replay.

## Project history

The app started in Flutter. That code is in the history up to the tag `flutter-final`; the removal is described in
[docs/native/flutter-removal.md](docs/native/flutter-removal.md).
