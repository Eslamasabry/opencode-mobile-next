# P0.7 / P1.4 backend removal (2026-09-27)

Finish line: expose dependency-checked component removal and durable AI Team turn-off/removal for the later UI unit; assess whole-runtime project preservation before implementation.

Non-goals: UI, localization, host-to-host project migration, Kotlin, protected single-owner files, deployment or release.

Read set: AGENTS.md; STANDARDS.md sections 2, 3, 13, 15 only; built-in Linux/setup/team contracts, native uninstall/install implementation (read only), existing tests, rootfs folder and export interfaces.
Write set: setup contract/registry, new component removal service, BuiltinTeam, dedicated behavior tests and this record. Dependency: existing BuiltinLinux channel and setup registry. Ownership: parent owns component service/registry/docs, team worker owns BuiltinTeam and its tests, test worker owns component behavior tests; feasibility worker was read-only. Checks were attempted serially by parent.

## Feasibility and contract problems

**P0.7 blocked; no replacement uninstall API is exposed.** Projects live at `/root/projects` inside `<filesDir>/linux/ubuntu`. Evidence:

- `lib/builtin/builtin_linux.dart:133`: project directory; `:213-214`: uninstall has no policy argument.
- `lib/builtin/builtin_folders.dart:63-68`: direct rootfs mapping.
- `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt:37-38`: rootfs; `:365-375`: uninstall stops services then recursively deletes it; `:78`: installation deletes it again before unpacking.
- The native byte count (`BuiltinLinux.kt:381-404`) is cached aggregate rootfs usage, not measured per-choice reclamation.

Required native/setup-owner change: add an explicit preservation policy; stop services; retain projects outside the disposable rootfs; restore or bind that retained directory during setup before server startup; expose measured retained/reclaimed sizes. Both uninstall and install must change. A Dart staging workaround is not built under the requested feasibility-stop rule.

`BuiltinRootfsFolders.locateRootfs()` can read project files. Existing `FilePicker.saveFile` can save bytes through Android's document picker, allowing the person to choose Downloads (`lib/ui/screens/session_export_screen.dart:13-17`). No project ZIP export service currently exists, and the picker requires the entire archive as bytes. Export is potentially feasible but unimplemented, not proven automatic Downloads export. Whole-runtime choice-specific sizes and typed-name deletion are also unimplemented. `phoneServerCardRemoveBody` remains unchanged and belongs to the UI unit.

**P1.4 callable contract verified from current source.** Existing `BuiltinLinux.run`, `stopService`, and registry `removeScript` values support Python and AI Team removal. Core required components have no removal scripts; they remain unavailable. Dependency reasons take priority so Node reports OpenCode and AI Team. No native contract was invented.

## UI hook-up

Use `lib/builtin/setup/component_removal.dart`:

```dart
final linux = BuiltinLinux();
final team = BuiltinTeam(linux: linux);
final removal = ComponentRemovalService(
  linux: linux,
  registry: setupComponents(l10n),
  team: team,
);
final entries = await removal.inventory();
// After the component-specific confirmation:
await removal.remove(componentId);
// Refresh inventory after success OR failure.
```

Share one service instance for the This phone removal flow. Each `ComponentRemovalEntry` exposes `component`, `presence`, `canRemove`, `block`, and `blockingDependents` (ids, including indirect dependents). Localize block enum values and resolve dependent names from the registry; do not show enum spellings as copy. `presence == unknown` is not an absent component. Job steps are excluded. Presence checks are separate from version/health checks, so an outdated or partial AI Team install still blocks Node removal. `ComponentRemovalException.code` contains only a stable reason, never script output.

Listen to `busy` to disable conflicting removal/setup/start actions. Removal re-reads inventory and setup status, rejects active or unreadable setup, checks dependencies, executes the registry script, then confirms absence. There is no native atomic lock shared with setup: caller coordination remains necessary if another route can start setup concurrently. Removal may partly change tools before failure; refresh rather than showing success or assuming rollback.

Survival copy for each supported choice:

- **Python:** runs exactly `SetupScripts.pythonRemove`: removes the pip/venv packages and asks apt to autoremove unused dependencies. It does not delete project directories or explicitly uninstall the base Python package. Package-manager effects are not measured freed bytes.
- **AI Team turn off:** `await team.turnOff()` persists an empty runtime-global marker at `/root/.oc-builtin/aiteam.disabled`, then stops the service. Installed programs, team tasks/settings and projects remain. `await team.isTurnedOff()` reads that choice, throwing on unknown state. `stop()` is temporary; recovery may restart it. Explicit `start()`/`turnOn()` re-enable recovery.
- **AI Team remove:** use `removal.remove(SetupComponentIds.aiTeam)` for inventory/dependency/setup guards. It calls `team.remove()`, which disables recovery, stops the service, then runs exactly `AiTeamScripts.removeScript`. Projects under `/root/projects` remain. AI Team programs, downloads, tasks, settings and phone-side bare Git origins under `/root/aiteam` are deleted. Existing projects can retain an `origin` URL pointing into that removed directory. Confirmation must name that loss; do not promise all history survives or imply automatic migration. `team.remove()` alone is a low-level lifecycle operation without setup/dependency checks.

After successful turn-off/removal, clear orchestration config on every matching built-in AI Team profile with the existing `profile.orchestration = null; await connection.store.upsert(profile);`, then `connection.syncOrchestration()`. Select only built-in AI Team configs, preserving remote/Termux teams. This is the later UI/connection owner's hook-up, not an edit made here. The marker prevents stale saved config from automatically restarting the service even before cleanup. Removing Ubuntu deletes the marker. Setup does not clear the marker; explicit user team start does.

Do not wire the existing all-data uninstall to a new “keep my projects” default. P0.7 stays blocked until the preservation contract and export choice are implemented and verified.

## Behavior coverage

`test/setup_component_removal_test.dart`: exact Python registry dispatch; AI Team lifecycle dispatch; transitive dependency reasons; presence vs health; required/unsupported/absent/unknown cases; unknown dependents; fresh recheck; running/malformed/unavailable setup; duplicate removal/busy reset; script failures; post-removal verification; credential-safe errors; authored-script restriction; presence shell syntax.

`test/builtin_team_removal_test.dart`: durable off across instances; temporary stop recovery; exact removal script; stop/persistence/read/enable/remove failures; explicit re-enable; serialization with preparation and queued recovery; redacted native diagnostics; management shell syntax.

New APIs rather than an existing behavior fix; no failing-first baseline run claimed. The two tests must pass on the candidate before merge. Existing affected tests to rerun: `setup_engine_test.dart`, `setup_scripts_test.dart`, `builtin_team_test.dart`, `builtin_team_hot_test.dart`, `builtin_team_bring_in_test.dart`.

## Verification

Base candidate: `d97420b8aa70c0cf6ab17edde4b28670587f2f05`, branch `codex/p07-p14`, plus the scoped working-tree changes.

- Requested pinned `bin/dart format --language-version=3.10` is blocked during Flutter bootstrap by read-only `engine.stamp`/`engine.realm` writes.
- Formatting succeeds with the **same pinned SDK**, using `bin/cache/dart-sdk/bin/dart --suppress-analytics format --language-version=3.10` on changed Dart files. The lint include warning reflects absent `.dart_tool/package_config.json`; it is not an analyzer pass.
- Pinned `flutter test --concurrency=1 ...` cannot start: engine-cache writes are denied. Saved output: [tests.txt](tests.txt). No behavior test ran; no pass claim.
- Pinned `flutter analyze` cannot start for the same reason. Saved output: [analyze.txt](analyze.txt). Analyzer coverage is unverified.
- `git diff --check` passes. No full suite, build, device or rendered UI verification performed.

Verifier commands (with writable pinned SDK cache):

```sh
F="$HOME/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter"
"$F" pub get
"$F" test --concurrency=1 test/setup_component_removal_test.dart test/builtin_team_removal_test.dart test/setup_engine_test.dart test/setup_scripts_test.dart test/builtin_team_test.dart test/builtin_team_hot_test.dart test/builtin_team_bring_in_test.dart
"$F" analyze
```

## State

Implemented: P1.4 backend API; P0.7 blocked. Enabled: no UI wiring. Verified: formatting/diff only, behavior and analyzer blocked by environment. Committed: no. `git add` failed because `/home/owner/Storage/Code/oc_app/.git/worktrees/oc_app-codex-p07-p14/index.lock` is on a read-only filesystem. Changes remain in this working tree; the requested commit message and attribution trailers are in root `COMMIT_MSG.txt`. Deployed/released: no. No pushes.
