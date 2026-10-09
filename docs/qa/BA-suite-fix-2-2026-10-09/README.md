# BA suite reconciliation

Finish line: classify and resolve all 45 assigned failures, then pass each affected file independently under the machine lock.
Non-goal: broad golden regeneration, unrelated suite repairs, or APK/device qualification.

Base: feat/genui-fe cd5b97c4d5b219cf944591ce70746fc9a88418c6.


## Triage of all 45 assigned failures

| Files | Assigned failures | Finding and resolution |
| --- | ---: | --- |
| `chat_live_events_test`, `chat_states_standard_test`, `home_navigation_test`, `release_blockers_test`, `goldens/chat_states_golden_test` | 7 | Intended BA14 timing: a persistent outage is visible after the 15-second quiet window. Keep immediate reachability false, assert quiet first, and then check the existing recovery/status line. Disconnected golden contents remain unchanged. |
| `chat_reference_send_test`, `e7_session_approvals_layout_test`, `pending_sends_strip_test`, `plugins_screen_test`, `revamp/chat_3_test`, `revamp/queued_prompt_move_test`, `revamp/slice_aisetup_review_test`, `server_switcher_test` | 9 | Behavior assertions already pass; controller quiet-window timers outlive the test body. Dispose inside the fake-clock test after unmounting (or advance the tested approval timeout past 15 seconds). Queueing, offline authorization, and refresh expectations remain intact. |
| `goldens/runtime_switch_golden_test` | 6 | Pixels already match. The server-only fixture now inadvertently starts a native phone-agent liveness probe; stub only that refresh in its fake controller, preserving phone-agent availability and the All connections filter. |
| `goldens/agents_ui_golden_test`, `goldens/chats_home_golden_test` | 22 | Real layout regression from `e16d47f89`: the chip-wrap repair moved a lone model chip from trailing to leading. The owner contract in `docs/ux-system/kit-api/KitComposerChips.md` still requires trailing placement. Restore only that placement, preserve wrapping, and verify both LTR and RTL with new failing-first tests. Retain the existing golden images. |
| `session_draft_test` | 1 | Real layout regression: at 320x640, 1.7x text and keyboard inset 260, the unsaved-draft row overflows by 29 pixels. Let the full recovery row scroll in a bounded, shrink-wrapped ListView within the remaining space. Render diagnostics traced the 250-pixel recovery row plus other composer content exceeding its 339-pixel constraint. Existing failing test is the regression evidence. |

The [three-line layout contract](../../design/BA-suite-fix-2-contract.md) records the two production corrections. No connection-state, queue, auth, native or persistence implementation was changed. No golden was replaced: intended post-grace state matches the existing image, and the model-chip placement is repaired in production.

## Validation protocol

Each file runs in a separate invocation of:

```text
OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test -- /home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter test --no-pub --concurrency 1 <file>
```

All 45 assigned failures reproduced individually on the base production source; `red-*.txt` records each file. Two extra trailing-edge cases failed before the layout fix (`red-kit_composer_status_strip_test.txt`). The two production corrections fail when independently reverted, then are restored byte-for-byte (`revert-model.txt`, `revert-draft.txt`). Final per-file results are in `results.json` and `green-*.txt`. No Gradle, APK, emulator, full-suite or physical-device claim.

The visual comparison of the failing new-chat and agent-sheet images showed the lone model chip moved from trailing to leading; no other pixels needed replacement. The existing runtime-switch images already matched. All golden baselines are deliberately retained.

The kit ratchet rejected an initial SingleChildScrollView wrapper (`red-layout-kit-ratchet.txt`); the final layout uses the existing permitted ListView primitive, without any ratchet or allowlist change. All final file checks are rerun on this correction.

## Final result

- All 45 assigned failures resolved: 23 production layout regressions, 7 intended quiet-window expectations, and 15 fixture lifecycle failures. See `triage.json` for every test.
- Final candidate: **464 tests pass across 22 independently run files** (the 17 assigned files plus 5 targeted regression/guard files), no skipped files. `results.json` contains every exit code and count.
- Scoped analyzer: **clean**, 17 affected source/test entry points (`analyzed-files.txt`, `analyzer.txt`). Pinned formatter reports no changes; diff and source-hash checks pass.
- Both layout changes have failing-first and independent production-revert evidence. No golden regeneration: all 30 assigned baseline images retain their original hashes (`golden-audit.json`).
- No build, emulator, owner-phone, full-suite, signing, release or push action. Coordinator owns merge and integration qualification. Terminal logs have trailing whitespace normalized.
