# Codex audit3 — 2026-09-29

Adversarial security and correctness audit of the recent `feat/phone-setup-v2` merges, fixed in the existing `codex/audit3` worktree. Baseline: `bcf45eb75ed579c1ec553366941da38450925be9`; both branches pointed to that revision at audit start. [Merge inventory](merge-window.txt) is the output of `git log --since='36 hours ago' --merges feat/phone-setup-v2` captured at the start.

Finish line: confirmed backend/native findings fixed with regression evidence, remaining UI-owned/publication work explicitly handed off, requested gates and analyzer recorded, logical local commits. Non-goals: UI redesign or implementation, owner-phone access, live-server mutation, pushes, PRs, CI, signing, deployment or release.

## Port onto the integration line (2026-11-01)

The nine commits below were written against `bcf45eb7` (2026-09-29). The line has since moved 1,800 commits: the OpenCode 1 client and `lib/api/product_repository.dart` are gone, the native wake-lease and recovery code was rewritten, and migration offers, project export and profile deletion moved. `merge: int/combined-2026-11-01` brings the branch up to that line, and each fix was checked against the code that now owns it. Dev is frozen, so only these fixes were ported. Analyzer only: no suite, APK, emulator or phone ran for this port, and the 09-29 suite and analyzer numbers below describe the old tree, not the merged one. The raw logs linked below were never committed.

| ID | Outcome on the integration line |
| --- | --- |
| PR-1, PR-2 | Dropped. The OpenCode 1 client is gone and the OpenCode 2 `refreshProviderRuntime` refuses ("unavailable on this server"); nothing is left to harden. The reload test reverts to the integration version. |
| IN-1 | Ported unchanged: the installer probe accepts only an authenticated 200 from a live candidate. |
| LS-1 | Ported and widened to the keys the line added. `oc.genui.<covered|enabled|journal>.<id>` and the agent backend's `<id>.agents` keys are swept with their phone; a saved profile that is itself named `<id>.agents` keeps its own keys; app-wide keys that look like an owner (`oc.phraseChips.enabled`, `oc.usageWidget.v1`, `oc.replySpeed.inApp`, ...) are never matched. |
| LS-2 | Ported unchanged. |
| ME-8 | Ported onto `TermuxMigrationService.dismissOffer` (the offer owner no longer writes the flag itself) and onto the deletion transaction in `lib/state/connection/deletion.dart`. |
| NL-1 | Reworked. The line already rechecks owner, policy and native intent after the password probe. The remaining window (Stop while the owner is claimed or the password is saved) is closed by `startServerForLaunch`: the launch reads the native intent generation after the claim and the native start refuses a changed generation. It keeps the normal start's replacement of a stale server, restore recipe and budget reset; it does not spend the crash budget. A refusal ends as a withdrawn start, not an error card. |
| NL-2 | Dart half already present (the watch clips each lease and rechecks demand). Native half: `setChatWorkLease` now reads the server and admits the lease under the runtime monitor that Stop uses. |
| NL-3 | Ported unchanged (`stopTree` returns for an exited Process). |
| NL-4 | Dart half unchanged. Native half moved to `TermuxActivityPermissions.kt`; an empty grant array answers "denied". |
| ME-1, ME-5, ME-6 | Ported onto the walker that also exports proot Git links; privacy is checked on the full relative path, `.git/config*` and `.gitconfig` are always private, `.oc-migration-exports` joins the private export, and unreadable or conflicting roots refuse the export. |
| ME-2 | Ported. The Git link files are reported by resolved path (`/data/data/...`), so the native opener accepts either spelling of the app folder. Without that the new descriptor walk would refuse every repository exported from Ubuntu. |
| ME-3, ME-4, ME-7 | Ported unchanged. |
| PIN-1 | Still open, unchanged. |

Not verified: Kotlin compile, the Kotlin race harness (`test/native/reply_work_hold_harness.kt`, adapted to the new method names), and every Dart test named in this README. Run them at the next suite gate.

## Findings

Line references identify the fixed tree unless marked historical. Exploit/failure detail and failing-first evidence are in the linked slice reports.

| ID | Severity | Failure / exploit scenario | Location | Outcome | Fix commit |
| --- | --- | --- | --- | --- | --- |
| PR-1 | Medium | Malformed successful session-status entries were treated as idle, permitting a provider reset to abort work. | `lib/api/product_repository.dart:2066` | Fixed: require readable type; only explicit idle is idle. | `e5ea24bd` |
| PR-2 | High | Project changed while status requests awaited; reset disposed the new, unchecked project. | `lib/api/product_repository.dart:2039` | Fixed: snapshot selected directory/workspace for checks and disposal. | `e5ea24bd` |
| IN-1 | Medium | Auth-rejecting listener passed the new binary's startup probe and permitted replacement of the working install. | `lib/builtin/setup/components.dart:592` | Fixed: authenticated HTTP 200 and a live candidate required. | `b1ab6b52` |
| PIN-1 | Medium | The app pins an old wrapper whose downstream installer/self-upgrade remains unpinned; checkout hardening does not reach users. | `lib/ui/setup_commands.dart:112`; historical `scripts/host/ubuntu-opencode.sh:75,184` at `c62f159a` | **Open UI/publication hand-off**; no UI edit or push authorized. | `open hand-off` |
| LS-1 | Medium | Profile deletion matched another profile's session/draft/location text or a global namespace; dotted profile IDs also collided. | `lib/state/profiles.dart:965` | Fixed: match owner position, resolve legacy nested owners against saved IDs. | `32d66f64` |
| LS-2 | Low | Canonicalized or newly registered UUID-shaped credential entered plaintext session binding preferences. | `lib/state/session_link_bindings.dart:85,165` | Fixed: validate raw/canonical ID and revalidate immediately before write; production address capability remains off. | `67ba0b0f` |
| NL-1 | Medium | Launch recovery overwrote Stop during an asynchronous health probe or password write. | `lib/builtin/phone_server_healing.dart:194`; `lib/builtin/builtin_server.dart:221`; `BuiltinLinux.kt:340` | Fixed: native foreground/generation admission, readable selected owner, current policy, fresh stop intent. | `04fd30b5` |
| NL-2 | Low | Concurrent Stop could be followed by stale wake acquisition; final lease could overrun the six-hour watch ceiling. | `BuiltinLinux.kt:561`; `lib/builtin/reply_watch.dart:186` | Fixed: synchronized native admission, clipped leases, elapsed timer ticks and stale-demand checks. | `04fd30b5` |
| NL-3 | Medium | Already-exited tracked Process exposed a reusable PID to descendant enumeration/signaling. | `BuiltinLinux.kt:935` | Fixed: return before PID lookup for exited Process; no name-based killing. | `04fd30b5` |
| NL-4 | Low | Older permission bridge fallback leaked an exception; cancelled native result was mislabeled permanently denied. | `lib/termux/termux_reach.dart:108`; `MainActivity.kt:829` | Fixed: controlled denied result for fallback failures and cancellation. | `04fd30b5` |
| ME-1 | High | Plain exports leaked top-level credential folders and Git credentials not caught by the old content regex. | `lib/builtin/project_export.dart:143,253` | Fixed: full relative-path privacy checks and unconditional private treatment of Git config. | `819fe7b9` |
| ME-2 | High | File/parent symlink swap between path check and open could export credentials; a FIFO could hang the worker. | `ProjectExportBridge.kt:211` | Fixed: held-directory traversal, no-follow opens, descriptor type checks, nonblocking leaf and hardlink refusal. | `819fe7b9` |
| ME-3 | Medium | Explicit caller intents supplied cached Flutter engine/route/entrypoint parameters to the exported Manage space activity. | `ManageSpaceActivity.kt:26` | Fixed: replace caller intent with empty app-authored intent before Flutter reads it. | `819fe7b9` |
| ME-4 | Medium | Cancellation/disposal during preparation still dispatched export; double taps opened multiple pickers. | `lib/builtin/project_export_controller.dart:112` | Fixed: reserve operation before picker and recheck cancellation after each preparation await. | `819fe7b9` |
| ME-5 | Medium | Incomplete archives reported success after duplicate roots, unframeable names, unreadable or vanished planned files. | `lib/builtin/project_export.dart:292,324,544`; `ProjectExportBridge.kt:140` | Fixed: refuse ambiguous or incomplete exports. | `819fe7b9` |
| ME-6 | Medium | Private backup omitted migrated Termux config/session exports before Clear storage. | `lib/builtin/project_export.dart:169` | Fixed: include `.oc-migration-exports` in private opt-in only. | `819fe7b9` |
| ME-7 | Medium | Unauthenticated duplicate authorization headers threw outside the loopback migration receiver catch. | `lib/builtin/migration/termux_migration_transport.dart:373,558` | Fixed: nonthrowing exact-one-header lookup; reject attacker, accept subsequent legitimate transfer. | `4f843b36` |
| ME-8 | Low | In-flight migration-offer preference writes could outlive the profile deletion sweep. | `lib/state/termux_migration_offer.dart:14`; `lib/state/connection.dart:6804` | Fixed: serialized admission, deletion close/drain and durable abort recovery. | `32d66f64` |

**17 findings fixed; one previously documented host-pin publication gap remains open.**

Native paths in the table are under `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/`.

## Evidence and ownership

- [Provider reload, native installer and host pin](provider-install.md): root owned `product_repository.dart`, setup components and their tests. [Red](provider-install-red.log), [green](provider-install-green.log): six intentional failures, then 47 passing tests and one explicit real-download smoke skip.
- [Links, storage ownership and deletion](links-storage.md): worker owned session-link backend/store plus ProfileStore and connection deletion integration. [Green](links-storage-green.log): 117 passing focused tests. The report distinguishes the dependency-order compile attempt from the final complete-tree run.
- [Native lifecycle and Termux permission](native-lifecycle.md): worker owned both native/Dart bridge halves, server startup/recovery and reply watch. Executable Kotlin harness exercises production method bodies; original-method replay demonstrates the race.
- [Migration/export findings and evidence](migration-export.md): **69 focused tests passed**, including an executable Kotlin activity harness and real loopback duplicate-header attack. [Green log](migration-export-green.log). Worker owned migration files, project exporter/controller, ManageSpaceActivity and ProjectExportBridge. Shared ProfileStore/connection integration remained with the links worker. All workers used this designated audit worktree with disjoint write ownership; the coordinator reviewed the combined diffs and owns commits/checkpoints.

## Integration validation

Pinned Flutter/Dart: `~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/`. `flutter pub get` completed at the initial test invocation. Every changed Dart file is formatted with `dart format --language-version=3.10`; no new ignores were introduced.

- Requested five gates: `tool/qa/machine_lock.sh test -- <pinned-flutter> test --no-pub --concurrency=1 test/kit_ratchet_test.dart test/redaction_test.dart test/ui_glossary_test.dart test/no_raw_error_text_test.dart test/architecture_boundaries_test.dart --reporter expanded` — **92 passed**. [Log](gates.log).
- Whole-project analyzer: `tool/qa/machine_lock.sh analyze -- <pinned-flutter> analyze --no-pub` — **clean, no issues** on final candidate `8dece940`, [analyze.log](analyze.log). Initial missing-braces lint in `project_export.dart` was corrected; [first result](analyze-first.log) is superseded.
- Complete test suite: **934** test files, including nested goldens, in six sequential chunks. Chunk 1 uses one test worker; chunks 2–6 use the explicitly permitted workstation setting of three workers. Chunk 1 is retained only after a matching input fingerprint; the incomplete first attempt at chunk 2 is discarded. [Manifest](suite-manifest.txt), [runner](run_serial_suite.py), [summary](suite-summary.json), [progress](suite-run.log). Initial command: `tool/qa/machine_lock.sh test -- python3 docs/qa/codex-audit3-2026-09-29/run_serial_suite.py`; continuation: the same command plus `--resume --concurrency 3`. Both run with the verified Temurin 17 directory as `JAVA_HOME` and its `bin` first in `PATH`. Before/after source/test input fingerprints prevent combining changed snapshots. The first partial chunk was stopped when a late worker edit and integration corrections invalidated it; `suite-interrupted-before-freeze.log` is excluded from final coverage. A second partial run found a pre-existing unbaselined `avoid_print` suppression in the installer test; it was removed in `37e6716f`, and both suppression and installer tests passed (10 pass, one opt-in download skip; [log](suppression-green.log)). `suite-interrupted-suppression.log` is also excluded. A third partial run exposed four launch tests whose fake lacked the new guarded wire; the fake was fixed without changing assertions and all 10 autostart tests passed ([log](native-autostart-fixture.log)). `suite-interrupted-autostart-fixture.log` is excluded too. Final suite started from `8dece940` after every source/test edit was committed. Result pending; partial coverage is not a completed gate.
- Native compile: API 37, Gradle 9.5.0, Temurin `17.0.20.1+1`, `:app:compileReleaseKotlin` under `machine_lock.sh build`, two Gradle workers, 3 GB heap. [Log](native-compile.log). No signing task, key access or APK delivery. This checkout lacks `gradlew`, so the exact cached Gradle version from `android/gradle/wrapper/gradle-wrapper.properties` was invoked. System Java was OpenJDK; a temporary Temurin archive was obtained from the official Adoptium GitHub release and verified against its published SHA-256 `3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e` before extraction. **Passed** in 2m 14s; no APK signing/packaging. The first compile exposed unavailable Android `O_DIRECTORY`; the final implementation uses `fstat` on the opened directory descriptor instead. [Initial compiler evidence](native-compile-first.log).

## Security invariants and limits

Repository URL-launch inventory found only the central `openExternalLink` implementation directly invoking `launchUrl`; no new bypass. Provider ingress registers credentials before decoding, and redaction/credential-ingress tests pass without real credential logging. New migration-offer persistence uses `oc.termuxMigrationOffer.<profileId>` with deletion admission/drain. Storage matching covers the inventoried legacy versioned and nested owner formats.

Session-address parsing, anonymous tailnet transport restrictions, consent, binding, instance recheck and deletion cancellation were inspected and covered by existing tests. Their production capability remains disabled; this audit does not enable it. Termux import retains bounded manifest/hash checks, path validation and non-destructive conflict handling. No emulator or owner phone was used.

Provider status and disposal remain separate server requests: another client can start work between them. The stale Process fix prevents enumeration of an already-exited tracked process; it is not a kernel-level atomic process-identity primitive. Host/native tests and compilation do not claim real-device battery or filesystem-race validation.

## UI hook-up and publication contract

PIN-1 remains with the UI/publication owner: only after an explicitly authorized publication, pin `HostScripts.commit` and its Ubuntu SHA-256 to the published hardened script, update both host guides and release description, remove `_pendingPinUpdate`, and test the **pinned Git blob**, not just checkout bytes, for unchecked downstream execution. Full steps are in [provider-install.md](provider-install.md). A read-only raw GitHub check of the known hardened script commit `3c7163b2e0b583fa490adae30920da79140eb8e1` returned 404 during this audit ([result](host-publication-check.json)); that revision is not currently usable as the public download pin. The current old pin returned HTTP 200 and exactly its configured SHA-256 ([result](host-current-pin-check.json)), confirming that users still receive the legacy wrapper.

Keep address links disabled pending independent host authorization/private-ingress proof. Continue using typed/localized failure categories and existing private-export consent. No direct `lib/ui/**` edits were made; the new guarded native launch wire ships with its Dart half and fails closed on older runners. Other fixes preserve existing UI APIs; any additional export presentation note is recorded in the migration/export slice report.

## Local commits

All commits include `[skip ci]` and the requested Claude co-author/session trailers. Nothing was pushed.

| Commit | Change |
| --- | --- |
| `e5ea24bd` | Fail-closed provider status and location snapshot |
| `b1ab6b52` | Authenticated live installer probe |
| `67ba0b0f` | Session-binding credential validation |
| `32d66f64` | Exact profile ownership and drained migration dismissal writes |
| `04fd30b5` | Stop/generation admission, wake leases, tracked process and permission fixes |
| `4f843b36` | Duplicate migration header rejection |
| `819fe7b9` | Project backup privacy, safe native opens, exported activity and cancellation |
| `37e6716f` | Remove pre-existing unbaselined installer-test suppression |
| `8dece940` | Model guarded launch in autostart fixture; existing 10 assertions pass |

Stable code/test candidate: `8dece9403ceb7cfbea1627f9f4878c92c30e3d5d`. Final evidence commit will be recorded at hand-off.
