# Native switch upgrade gate, 2026-11-07

**Verdict: FAIL** (5 of 19 checks passed)

Written by `native/tool/qa/upgrade_e2e.sh` (E00-S09). The released Flutter app was seeded (E2E-39 Part A), checked
(Part B), replaced with the native app by `adb install -r` (never uninstalled) and checked again (Part C).

## Builds

| | Flutter (installed first) | Native (installed over it) |
|---|---|---|
| File | `flutter-2.1.4+62-testkey.apk` | `native-switch-benchmark-ff625ceef-testkey.apk` |
| Package | `io.github.eslamasabry.opencode_mobile` | `io.github.eslamasabry.opencode_mobile` |
| Version | 2.1.4 (62) | 3.0.0-dev (10004) |
| APK SHA-256 | `037bd3e6d866d17894b260e39d2d9e3067d9a0a4b8bc5d6b5bd8a3b3da255f75` | `0a4bafe7311ed45bcf1d16efbfed2bc1b54bdaf52c1ddb08bbfe368ab2b97675` |
| Signer SHA-256 | `3796C2105095518AF1CC7300F69BA3825CA7EBF798AECFA60D93FEE041EE442C` | `3796C2105095518AF1CC7300F69BA3825CA7EBF798AECFA60D93FEE041EE442C` |

Expected signer: `3796C2105095518AF1CC7300F69BA3825CA7EBF798AECFA60D93FEE041EE442C`

## Device

- Emulator `emulator-5584`, AVD `OCN_U`, held under the emulator lock for the whole run.
- Seed: Part A ran on a wiped AVD.

## Checks

| Id | Check | Verdict | Detail |
|---|---|---|---|
| signer-flutter | The flutter APK is signed by the expected key only | PASS | flutter-2.1.4+62-testkey.apk signed by 3796C2105095518A... |
| signer-native | The native APK is signed by the expected key only | PASS | native-switch-benchmark-ff625ceef-testkey.apk signed by 3796C2105095518A... |
| same-signer | Both APKs carry the same signer | PASS |  |
| same-package | Both APKs are io.github.eslamasabry.opencode_mobile | PASS | io.github.eslamasabry.opencode_mobile / io.github.eslamasabry.opencode_mobile |
| version-order | The native versionCode is above the Flutter one | PASS | 62 -> 10004 |
| conversations | Conversations shown, no welcome screen and no first-run guide | **FAIL** | not reached: the flow stopped earlier |
| settings | Language, text size, style and notification choices kept | **FAIL** | not reached: the flow stopped earlier |
| servers | Servers listed and connect without re-typing passwords | **FAIL** | not reached: the flow stopped earlier |
| chat-messages | A seeded chat opens and shows its messages | **FAIL** | not reached: the flow stopped earlier |
| drafts | Draft, attachment, saved prompt, pins and always-allow rule kept; queued prompt sends once | **FAIL** | not reached: the flow stopped earlier |
| send-prompt | A new prompt sends and its reply arrives | **FAIL** | not reached: the flow stopped earlier |
| linux-ui | Built-in Linux, the project and its server there without reinstall | **FAIL** | not reached: the flow stopped earlier |
| terminal | `pwd && ls` runs in the seeded project's terminal | **FAIL** | not reached: the flow stopped earlier |
| voice-ready | Voice pack shows Ready | **FAIL** | not reached: the flow stopped earlier |
| schedule | Scheduled prompt listed and still fires | **FAIL** | not reached: the flow stopped earlier |
| old-files | Attachments from the old documents folder open | **FAIL** | not reached: the flow stopped earlier |
| no-secret-in-log | No password or token in the log during the update | **FAIL** | not reached: the flow stopped earlier |
| device-state | OS-held state read before and after the update | **FAIL** | missing before.json, after-migration.json: the flow stopped before it was read |
| flow | E2E-39 passed end to end | **FAIL** | E2E-39 FAILED |

## Screens

- `screens/failure-1-flutter-first-run.png`: where the flow stopped, Part A step 8 on the Flutter 2.1.4 app (copied by hand: the gate's
  collector does not recognise Maestro 2.12's failure screenshot name).
- `screens/smoke-native-after-kill-relaunch.png`: manual smoke (below), the native app after its first launch was killed and relaunched.

Run output (logs, Maestro report, device state JSON): kept in the run agent's scratch folder on the PC (not committed).

## Run notes (E00-S10, added by hand)

The gate ran end to end and wrote this verdict, but E2E-39 stopped at Part A step 8 on the Flutter app, so **no Part C
assertion was exercised**: every UI and device-state FAIL above is "not reached", not a migration defect.

Why it stopped: a clean 2.1.4 install opens the first-run chooser "Where should your agent run?"; Part A expects the
welcome screen's "Connect to my computer". Had it passed, the next step (`_fx/fx.js seed-sessions`) would have failed too:
nothing serves `FX_CTL` (127.0.0.1:8898; `native/e2e/fixtures/fx.py` does not exist) and no fixture implements
`seed-sessions` or `cut-host`.

Setup used (deviations from the gate's defaults, none in product code):
- Signer: a throwaway test key made for this run (SHA-256 above), not an owner lineage. Native APK =
  `:androidApp:assembleSwitchBenchmark` at ff625ceef re-signed with that key; `assembleSwitchRelease` does not compile at
  this commit (`IncomingShareNotice.kt` imports compose foundation, which only the benchmark build type has).
- Flutter APK: `flutter build apk --release` of tag v2.1.4+62 with the pinned Flutter 3.47.1, signed with the same key.
- AVD `OCN_U`: android-35 google_apis x86_64, 4 GB RAM, 8 GB data. The API 37 image aborts SurfaceFlinger on every
  screenshot (`hasReadColorBufferDma`) and takes the package service down mid `adb install`; 16 GB of data did not fit the
  free space.
- Fixtures started by hand (`fxctl.py --run-dir DIR start ...`) and passed with `--fixtures-env`: the gate's own call puts
  `--run-dir` after `start`, which `fxctl.py` rejects. FX-OC2/FX-OC2-B ran on the opencode2 beta (v0.0.0-beta-19242):
  with the pinned 2.1.1 the fixture never becomes ready (it polls `/api/health`; 2.1.1 answers `/api/info`).
- `--skip-resource-checks`: `run.py` asks for 7 GB free RAM after the gate has already booted the 4 GB AVD.
- Run from a sparse worktree of ff625ceef that also holds `lib/l10n`, `android/app/src/main/res` and `docs`
  (`gen_strings.py` and the flow catalog need them); the coordinator worktree holds only `native` and `docs`.

Manual smoke outside the gate (same AVD, same APKs, nothing seeded): Flutter 2.1.4 installed and launched; native
installed over it with `adb install -r` (versionCode 62 -> 10004, grants kept); its first launch killed after 2 s and
relaunched: the app runs, no crash in the crash buffer, and it shows its first-run screen (expected with no data).
This says nothing about migrating real data; it only shows the in-place install and a killed first launch do not break.
