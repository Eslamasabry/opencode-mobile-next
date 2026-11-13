# Removing the Flutter app

Status: prepared on branch `native/remove-flutter` (deletion commit `54d3ada905e3`), not merged. The owner decides
the day. The native app under `native/` is the product; the Flutter app at the repository root was kept only as a
reference.

The branch is built so that everything before the last two commits works with the Flutter folders still present,
and the last two commits do only this:

1. **Deletion commit**: `git rm -r` of every path marked DELETE below. Nothing else. Revert it alone to undo.
2. **Front matter commit**: the native-only `README.md`, `AGENTS.md` and `CONTRIBUTING.md` (drafted in
   `docs/native/removal-drafts/` and moved into place), `.gitignore` (Flutter leftovers stay ignored),
   `.gitattributes`, `CODEOWNERS`, `dependabot.yml`, the pull request template, and the privacy allowlist without the
   deleted `test/` paths (`PrivacyLeakGuardTest` fails on a stale entry), with its ceiling lowered to 14.

Headline: 9 551 tracked files and 1 388 235 text lines go (30 376 tracked files before, about 20 850 after). Nothing
the released native app contains changes: no native Gradle module compiles or packages a file from the Flutter
folders.

## (a) Root paths

| Path | Fate | Why / who uses it |
|---|---|---|
| `lib/`, `test/`, `integration_test/`, `android/`, `ios/`, `assets/`, `widgetbook/` | DELETE | The Flutter app, its tests, its Android/iOS shells and its widget catalogue. |
| `pubspec.yaml`, `pubspec.lock`, `l10n.yaml`, `shorebird.yaml`, `analysis_options.yaml`, `.metadata` | DELETE | Flutter, Dart and Shorebird configuration. |
| `third_party/` (only `xterm/`) | DELETE | A Dart package fork used by the Flutter terminal. The native app vendors its own in `native/third_party/`. |
| `tool/` except `host/`, `demo-video/`, `qa/test_site.py` | DELETE | Flutter QA, golden capture, l10n, release, F-Droid, branding, coverage, catalogue, notices, quota and UX-gate tools. |
| `tool/host/` | KEEP | The AI Team host front (`front.py`). The app's guide downloads it from a pinned commit; `HostScriptPinFilesTest` checks its bytes. |
| `tool/demo-video/` | KEEP | The Remotion product video (marketing). It reads no Flutter file. |
| `tool/qa/test_site.py` | KEEP (only file left in `tool/qa/`) | `pages.yml` runs it before publishing `docs/site`. |
| `tool/builtin_linux/fetch_proot.sh` | MOVED to `native/android/linux/tool/` | Refreshes the pinned proot libraries; now writes `:android:linux` jniLibs. |
| `scripts/release.sh`, `release_github.sh`, `cut-alpha.sh`, `verify_github_release.py` | DELETE | The Flutter/Shorebird release path. Native releases use `scripts/native-release.sh` and `native-release.yml`. |
| `scripts/host/` | DELETE | The frozen Flutter copy of the Ubuntu host script; the native copy is `native/scripts/host/`. |
| `scripts/native-release.sh` | KEEP, same path | Named by `native-release.yml`, `ReleaseShapeTest` and `native/tool/release/test/run_tests.sh`, so it is not moved. |
| `scripts/byo-host/` | KEEP | The host half of the BYO host feature the native app has (`ByoHostWiring`); built by `byo-host-bundle.yml`. |
| `scripts/termux/` | KEEP | AI Team engine build scripts (with `engine/`). |
| `engine/` | KEEP | The AI Team engine sources (Rust); the privacy guard pins some of its files. |
| `docs/` | KEEP | `docs/site` is the website; native tests read `docs/ai-team-host.md`, `docs/opencode2-protocol-notes.md`, `docs/verification/`, `docs/design/`, `docs/native/migration/`; the app links `docs/ai-team-host.md`. Flutter-only pages stay as history (follow-up). |
| `contracts/` | KEEP | OpenCode 2 OpenAPI snapshots, named as the source of native fixtures. |
| `fastlane/` | KEEP | Store listing text for the same package id. |
| `marketing/` | KEEP | Launch material. |
| `LICENSE`, `LICENSES/`, `NOTICE`, `THIRD_PARTY_NOTICES.md` | KEEP | The app's Settings links `THIRD_PARTY_NOTICES.md` on `master`. Its package table is Flutter's (follow-up). |
| `README.md`, `AGENTS.md`, `CONTRIBUTING.md` | REPLACED by the drafts | Front matter commit. |
| `SECURITY.md`, `SUPPORT.md`, `CODE_OF_CONDUCT.md`, `PRIVACY.md`, `CHANGELOG.md` | KEEP | Governance (the hygiene test requires them); `CHANGELOG.md` is the Flutter history. |
| `.gitignore`, `.gitattributes`, `.github/CODEOWNERS`, `.github/dependabot.yml`, PR template | CHANGED | Flutter entries removed in the front matter commit. |

## (b) Native files that read Flutter files, and what replaces them

All of these were changed before the deletion commit, with the Flutter folders still present.

| Native file | Read | Now |
|---|---|---|
| `native/tool/design/gen_tokens.py` | Dart kit sources (git) | Retired. `FlutterKit.kt` is hand-owned; header says where its numbers came from. |
| `native/engine/agents/pins/generate_pins.py` | Dart agent catalog | Retired. `agent-pins.json` is the source; `AgentPinsParityTest` still checks the Kotlin copy against it. |
| `native/android/agenthost/tool/extract_auth_scripts.py` | Dart `_authProbe` | Retired. `AuthProbeBody.kt` is the source (text and digest unchanged); the fixtures stay committed. |
| `:tools:strings` (`Drift`, `DriftTest` B-L10N-010, `RealArbTest`, `cut_fixture.py`) | `lib/l10n/*.arb`, `android/app/src/main/res` | Frozen byte-for-byte copies in `native/tools/strings/frozen/`. `RealArbTest` routes every key to one feature (no Dart lib) and lost its two Dart-routing CLI tests. |
| `native/e2e/runner/gen_strings.py`, `upgrade.py` | ARB and Android strings | The same frozen copies. |
| `native/androidApp/build.gradle.kts`, `scripts/native-release.sh` | `android/key.properties` | Key lookup moved to build-logic `ReleaseKey` (see (d)). |
| `ReleaseShapeTest` (signing) | Flutter `android/app/build.gradle.kts` | Reads `ReleaseKey.kt` instead. |
| `ReleaseScriptContractTest` | `scripts/release.sh`, root `test/*.sh` | DELETED (Flutter release path). |
| `AgentAuthScriptsTest`, `AuthProjectionFixtureTest` | `test/fixtures/agent_auth/*.json`, `pubspec.yaml` as a marker | Copies in `engine/agents` test resources; root found by `native/settings.gradle.kts`. |
| `DartSource.kt` (`:engine:agents` tests) | `lib/domain/agent_catalog.dart` as a marker, live Dart | Root found by `native/`; the live-Dart reader is gone; the frozen twins stay. |
| `AgentPhoneScriptsTest` (2), `AgentRemovalScriptsTest` (1), `HistoryCopyRunTest` (1), `LinuxVoiceWiringTest` (1), `CapabilitySnapshotTest` (1), `ManifestHardeningTest` (1), `Oc2FixtureManifestTest` (1), `NativeShipGateTest` (1) | live Dart, Flutter res, Flutter jniLibs | DELETED: each only proved likeness to Flutter. The other tests in those classes stay. |
| `ClaudeInstallScriptTest` | live `claude_scripts.dart` pins | The live-pin lines dropped; the frozen-twin text check stays. |
| `AgentPinsParityTest` | Dart marker, live `paseo_scripts.dart` | Root found by `native/`; Node pins compared with literals. |
| `AgentCertificationFileTest` | Dart snapshot | Dart compare dropped; JSON vs Kotlin vs `docs/verification` stays. |
| `TestedClaudeVersionsTest` | `lib/paseo/tested_versions.dart` | Compares with `agent-pins.json` only. |
| `LiveModeTest` B-ANDR-014 | Flutter `BackgroundConnectionService.kt` | Literal wire values. |
| `HostScriptPinFilesTest` (pipe scan) | `lib/**/*.dart`, `app_en.arb` | Scans docs, scripts, `tool/host`, native only. |
| `BundledScriptsShellTest` | `scripts/host` as a marker | Root found by `native/`. |
| `NativeQuestionFixtureTest` | `test/fixtures/paseo/…` | DELETED (the fixture is inlined in the common tests). |
| `tool/parity/file_ledger.py`, `tool/glyphs/gen_icons.py`, `fixtures/oc2/tool/dart_variants.*`, `catalog/seed/topup_inputs.py` | Flutter sources | DELETED (one-off generators; outputs stay committed). |

Still reading Flutter **git history** (not the working tree), so unaffected: the picture gate (`FlutterGoldenGate`,
`fetch_all_goldens.sh`, `rb_gate.py`), `twin_check.sh`, `Sh.releasedText`. The Flutter commits stay in history.

## (c) Workflows

| Workflow | Fate |
|---|---|
| `android-quality.yml`, `android-release.yml`, `android-device.yml`, `android-apk-comment.yml`, `ios-quality.yml` | DELETE (Flutter build, test, Shorebird release). Their secrets (`SHOREBIRD_TOKEN`, `CI_KEYSTORE_*`) become unused; nothing else changes. |
| `native-ci.yml`, `native-device.yml`, `native-release.yml` | KEEP unchanged. |
| `byo-host-bundle.yml` | KEEP unchanged (`scripts/byo-host` stays). |
| `pages.yml` | KEEP unchanged: `tool/qa/test_site.py` and `docs/site` both stay. |

## (d) What had to be done by hand, and its state (2026-11-13)

The owner asked the coordinator to do these.

1. **Release key file.** DONE. A copy of the key properties file now lives outside every checkout and release runs
   name it with `OC_KEY_PROPERTIES`; the original is left where it was as a second copy. Its `storeFile` is an
   absolute path to a keystore that was never inside the repository, so nothing else moves. Order the build uses:
   `OC_KEY_PROPERTIES`, then `native/key.properties`, then the old `android/key.properties` (with a notice). Never
   delete a checkout's `android/` folder by hand while it still holds an untracked key file.
2. **Tag the last commit that has Flutter.** Created when the removal lands on the build line: `flutter-final` on the
   commit named below. It matches no workflow trigger (`v*` only).
3. **F-Droid.** Nothing to do: neither package id has a recipe in fdroiddata (checked 2026-11-13). `fastlane/metadata`
   stays. A future recipe builds `native/`.
4. **Promoting a release.** Unchanged for native: the Native release workflow attaches the APKs to a DRAFT with
   `make_latest=false`; a person publishes the draft by hand and marks it latest only on the owner's go
   (`docs/native/release.md`, step 6). The Flutter script's `promote` goes with the Flutter release path.
5. **Default branch protection.** OPEN, to do the day `master` moves: `master` requires the status check `gate`,
   which only the Flutter workflow `android-quality.yml` produced. After the removal no workflow reports it, so the
   required check must change to a native one (Native CI's `summary`) at the same time `master` is advanced.
6. **Secrets.** Left alone on purpose. `SHOREBIRD_TOKEN` is unused after the removal; `CI_KEYSTORE_*` hold a signing
   identity and are not deleted without the owner's own decision.

**Last commit with Flutter: `4c1ffbda35fc0c08d8667d0aff580b4b4d6189d8`** (the parent of the deletion commit). Suggested tag:
`git tag -a flutter-final 4c1ffbda35fc0c08d8667d0aff580b4b4d6189d8 -m "Last commit with the Flutter app"`. The last released Flutter app is
`v2.1.5+63`.

## (e) Lost on purpose

- No Flutter fix can ship any more: no Shorebird patch, no Flutter release. Users of Flutter 2.1.x upgrade to native.
- The Flutter→native upgrade is still tested: the in-app migration (`:android:migration`) and its fixtures are native;
  `native/e2e/runner/upgrade.py` installs a released Flutter APK (an artifact, not the tree) and reads the frozen
  strings. Only a fresh Flutter APK can no longer be built from this repository.
- "Same as Flutter" tests: native sources are now the truth; no test compares against Dart any more.

## (f) Undo

`git revert <deletion commit>` brings every Flutter file back; the front matter commit can stay or be reverted too.
Nothing before those two commits depends on the Flutter folders being absent.

## Follow-ups (not done here)

- Rename `FlutterKit*`, `flutter-2.1.4`/`flutter-freeze-tip` fixtures and other Flutter-named symbols.
- `KitIcons.kt` header (in `native/ui/kit`, owned elsewhere) still names the retired `gen_icons.py`; `Tokens.kt`,
  `TypeScale.kt`, `BuiltinLinux.kt` comments name retired tools.
- Rewrite `THIRD_PARTY_NOTICES.md` and prune `LICENSES/` for the native dependencies.
- `native/TWINS.md` and `native/tool/twin_check.sh`: the twin rule ends with Flutter.
- Prune Flutter-only pages from `docs/`; delete the Flutter-likeness tests that still read frozen twins.
- The e2e flow lint (`test_real_tree_is_clean_and_complete`) fails today for 46 native-only keys, with or without
  this change.
