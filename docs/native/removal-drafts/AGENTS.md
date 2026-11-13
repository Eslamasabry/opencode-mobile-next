# AGENTS.md

Working notes for coding agents in this repository. The app is native Kotlin (Kotlin Multiplatform, Jetpack Compose)
under `native/`. Read [native/AGENTS.md](native/AGENTS.md) first: its toolchain, module rules and tasks bind you. The
human-facing equivalent is [CONTRIBUTING.md](CONTRIBUTING.md).

## Toolchain and commands

- Gradle runs on **JDK 21**; **JDK 17** must be installed too (module bytecode targets 17). Android SDK
  `platforms;android-37.1`, `build-tools;37.0.0`.
- Run target tasks only, from `native/`: `:<module>:desktopTest` (KMP), `:<module>:test` (JVM), `:quality:test`,
  `:tools:strings:test` after copy changes, `-p build-logic test` after build-logic changes,
  `:androidApp:assembleDebug` for an APK. Never `./gradlew build`. Name test classes with `--tests`.
- On a machine without the capacity for Gradle, push the branch and dispatch `native-ci.yml` on it
  (`-f tasks="..."` for named tasks, `-f lane=fast -f base=<branch>` for the changed modules, `-f apk_only=true` for
  an APK). One `tasks=` run per ref at a time: a second one cancels the first.

## Testing traps

- Picture-gate scenes (`*GateTest`, `*.rb.*`) are red on purpose while the look is rebuilt; a broad filter buries
  your result. `native/tool/ci/known_failures.txt` lists what CI absorbs.
- Tests assert behaviour, not implementation; find by visible text; add a test tag only where a test needs a handle.
- Name every test after its catalog row: `B-AREA-NNN what it proves`, red first.

## Architecture boundaries

- Layers: `core`, `protocol` (OpenCode 2, Codex, Paseo clients), `engine`, `ui` (kit, words, routes, shell),
  `feature`, `android` (platform bridges), `tools`. The build enforces which may depend on which.
- **Kit only:** every UI component comes from `native/ui/kit*`. Features only arrange kit parts. A missing part is
  added to the kit by its owner, never drawn in a feature. The `:quality` guards enforce this; never add exceptions.
- UI gates on `ServerCapabilities` / `PlatformCapabilities`, never on the kind of backend.
- Single-owner units: shared modules (kit, words, routes, shell, core, connection, transcript); propose, don't edit.
- OpenCode 2's event stream is volatile: after a reconnect, reconcile by refetch, not replay.

## Security invariants: breaking these is a security regression

- Every URL the app did not author goes through `ExternalLinkPolicy` → `ExternalLinkOpener`.
- Per-profile keys are `oc.<what>.<profileId>` and registered with `ProfileScope`, so deleting a profile deletes them.
- Never echo provider credentials: `/config/providers` returns API keys; they must not reach logs, diagnostics,
  notification copy or test output. Log only through `AppLog`.
- The repository is public: no home-folder paths, private addresses, e-mails, device serials or token-like strings in
  committed files or commit messages. `native/tool/scan_push.sh` checks a push.

## Repository facts

- `native/engine/agents/pins/agent-pins.json` is the source of the agent pins (a pin moves only with an app release);
  `AgentPinsJson.kt` is its byte-for-byte copy.
- `native/tools/strings/frozen/` holds the frozen string sources the drift check compares with; new copy goes into
  the `:ui:words` XML through the strings pipeline (`translate.py plan/apply`).
- `engine/` is the AI Team engine (Rust); `scripts/byo-host/` the BYO host half; `tool/host/` the AI Team host front;
  `docs/site/` the website (`pages.yml`).
- `.claude/skills/` is gitignored third-party content. Do not commit it.
- Nothing in background code may assume unbounded lifetime: Android 15+ caps `dataSync` foreground services at six
  background hours per rolling 24 hours.
- Verifying against a live OpenCode 2 server: a scratch `opencode2 serve` on an unused port, HTTP Basic user
  `opencode` with the per-run password it prints; never commit or echo that password. Kill test servers by exact PID,
  never by pattern.

## Workflow

- Each feature gets its own branch and Git worktree, owned by one agent: the complete user journey, implementation,
  strings, tests, screenshots and local commits. Never edit another agent's checkout. The coordinator reviews the
  whole branch, resolves conflicts and merges after validation.
- CI budget is limited: every commit carries `[skip ci]`; runs are dispatched on purpose. Pushes, PRs, signing and
  releases each need a fresh explicit request.
- A replacement APK must be signed with the same certificate as the build on the maintainer's phone, or Android
  refuses the update and only an uninstall (which loses local data) gets past it. Compare
  `apksigner verify --print-certs` with the installed signer before delivery. Known signers: Public CI
  `842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C`, local release key
  `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`. Never substitute or rotate a signer.
- The release key file is `native/key.properties` (git-ignored) or the file `OC_KEY_PROPERTIES` names. Never read,
  copy or print a key or keystore.
- Releases go through `scripts/native-release.sh` (master only, clean tree, dry run by default) and the
  `native-release.yml` draft. Never publish, tag or use signing secrets without explicit maintainer approval.
- PRs need: tests and the CI run that ran them, screenshots for UI changes, accessibility notes, privacy/security
  notes for credential/data changes, migration notes for stored-format changes.

## Productivity rules: finish usable slices

1. **One active product slice.** Write a one-sentence finish line and an explicit non-goal before editing. Finish
   engine → UI → persistence/deletion before opening another feature. A scaffold is groundwork, not a feature.
2. **Check feasibility before building adapters.** Confirm the current callable contract, supported authentication
   and permitted credential use first. If the prerequisite fails, keep the feature unavailable.
3. **Timebox discovery.** About ten minutes for a bounded question; then implement, name a blocker, or explain why
   more research is needed.
4. **Parallelize execution, not repeated audits.** Freeze method names, arguments, results and errors before
   parallel editing. Shared modules stay single-owner. Reviewers never launch test processes.
5. **Use a check ladder.** Format changed files only; run the affected test classes once the change is ready; after
   a failure rerun that class or test, not the repository; run `:quality:test` at an integration checkpoint.
6. **Budget the full run.** Run the full lane at an agreed batch boundary and record the revision, the run and its
   failures. Later relevant edits invalidate it; never call partial coverage a pass.
7. **Keep an evidence budget.** Say what now works, what remains and the next concrete action.
8. **Separate shipping states.** Report implemented / enabled / verified / committed / deployed / released
   distinctly. "Get shipping" is not permission to touch live servers, credentials, tags, signing keys or releases.
