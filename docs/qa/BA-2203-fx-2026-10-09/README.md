# APK 2203 fx driver repair

Finish line: identify the fx picker timeout, repair both driver observations, and prove install/picker/removal on approved 2203 under the emulator lock.
Non-goal: enabling uncertified chat capabilities, sending prompts, signing in, or qualifying other final-pass rows.

## Diagnosis and changes

The [original final pass](../final-pass-2026-10-09/README.md) timed out in driver navigation, before observing fx. APK 2203 exposes the selected agent chip as `Claude Code` on the new-conversation screen; the driver accepted OpenCode and obsolete accessibility labels only. The inspection tap now accepts one unique selected-agent chip, only while the new-conversation prompt is visible. It never selects Claude in the picker or sends a prompt.

Android also repeats the unavailable row's reason as a hint, e.g. `Not certified on this version yet, Not certified on this version yet`. Only exact repetitions of known public blocker strings are normalized, scoped to the target's own row. Other agents' status and arbitrary screen copy cannot qualify it.

Removal had two observer races: physical deletion could precede the UI completion frame, and the completion sentence can share a multiline semantics node with its title. The driver now waits within its existing deadline, captures the exact target's freed-size sentence from that same frame, taps its unique Done action, and then requires the fresh Not installed row. It still refuses ambiguous/foreign confirmation actions and has no shell-deletion fallback.

## Verification

All device operations use only emulator-5554, under one `flock -w 3600 /home/eslam/Storage/tmp/oc-emulator.lock` reservation through final APK identity verification. The approved [artifact receipt](artifact.json) records normal 2203, SHA-256 `82877732e8bb362722f49e3193b8ffe2a91aacbaf37954769e244a1de37462c7`, source `ec778efa3de1730402f096fc126e16fd2e83264e`, and no QA defines. APK signer and installed hash/build are checked by the existing driver. No build, app uninstall/data clear, credential action, owner-phone operation or capability/matrix change.

The device exercised `device_install.run_case('fx', ...)` under the held lock, using the production `make_ports`, `install_if_absent`, `run_launch`, `run_uninstall`, retention, restoration and continuation checks. The first observation is retained in [initial-picker-observation](initial-picker-observation/fx-device.json); it proves install/removal but correctly remains an unclassified picker observation. Final driver hashes are in [driver-sources.json](driver-sources.json); the branch base is [base.txt](base.txt).

Offline regression checks are run serially through `OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test -- python3 ...`. New tests cover the current selected-Claude inspection chip, exact repeated Android hint, merged removal completion, delayed row refresh, and rejection of foreign completion. Red, green and production-revert logs distinguish the tests from device evidence. Flutter/native code is unchanged; no Flutter suite or APK build is needed for this driver-only patch.

## Final result

- Final [device receipt](fx-device.json): fresh app install, 5,482,652 downloaded bytes, exact pinned version/link, 12,591,104 allocated bytes.
- [Picker screenshot](fx-launch-rejection.jpg): the app's Sign in with fx sheet is reached in **20.752 seconds**; `app_rejection_with_way_forward / auth_required`, no hang, navigation unwound. Sign-in was not started and no prompt was sent. This proves the app entrypoint/rejection, **not** authenticated daemon launch. The original timeout was a driver defect.
- [Removed row](fx-removed.jpg): app removal passes; **“fx removed. Freed 13 MB.”** was captured before Done, then the Not installed row was verified. Target allocation falls from **12,591,104 to 0 bytes**; no target process, launcher/payload/staging/lock remains. The separate free-space delta was 12,546,048 bytes.
- All 27 Claude chat IDs, phone-gate digest, two Claude homes, other target-home counts, Node and Paseo match before/after. This is retained-state proof, not a new authentication qualification.
- [Normal restoration](normal-restore.json): 2203 hash/build and approved local signer verified again under the same reservation. Continuation confirms normal identity, idle setup, all targets absent and sufficient storage. No APK replacement was needed.
- **96 focused Python tests pass**: 83 driver tests and 13 final-pass adapter tests. Four new regressions fail with the driver changes reverted (`revert.txt`); final files restored byte-for-byte and driver tests rerun. Ruff F/E9 and diff checks pass. No UI/native source change, Flutter run, build, push or matrix promotion.
