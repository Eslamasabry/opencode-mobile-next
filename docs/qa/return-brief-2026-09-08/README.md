# Return brief verification — 2026-09-08

The implemented Workspace card surfaces unreviewed sessions and pending requests with Review results, Answer and Continue. Dismissal is local, exact and persistent; it neither marks sessions viewed nor answers requests. See the [scope and queued Portfolio C slices](../../product/mobile-experience.md).

## Candidate and checks

Branch `feature/return-brief`, based on F5 `7af2107`. F5 correctness fixes are separately committed as `ed2118f`: original route scope is immutable, timestamp ties retain server order (including older-page prepending), and only the newest terminal step supplies the finish timestamp. Reviewer `2e0ed0f8` gave those fixes a bounded source-only PASS. The return-brief changes await coordinator integration review.

Pinned Flutter 3.47.2 and worktree-local package configuration were used. `flutter pub get` resolved dependencies and generated localization, then exited 1 at Windows desktop plugin symlink creation. Developer Mode/global Flutter settings were not changed. Subsequent checks used `--no-pub`; this is not a claim that the Windows desktop plugin setup or a native build passed.

| Command | Result |
| --- | --- |
| `flutter analyze --no-pub` | Final source: no issues. Initial four brace-style infos corrected. |
| `flutter test --no-pub --concurrency=1 test/run_result_test.dart test/run_result_screen_test.dart test/return_brief_test.dart test/return_brief_state_test.dart test/return_brief_widget_test.dart` | 54 passed: 27 F5, 27 brief. |
| `flutter test --no-pub --concurrency=1 test/workspace_hierarchy_test.dart test/profile_deletion_test.dart test/session_read_state_test.dart test/chat_form_test.dart test/form_renderer_test.dart test/l10n_coverage_test.dart` | 79 passed. |
| `flutter test --no-pub --concurrency=1 test/return_brief_widget_test.dart build/traycer/return_brief_capture_test.dart` | After the final title-wrap correction: 15 widget checks and 6 capture cases passed. |
| Capture helper, then its `--plain-name 'capture actual Workspace brief before'` case | Final 6 cases passed; the before image also rerendered alone after capture-layer/scroll isolation corrections. 7 PNGs inspected. |
| Pinned `dart format` and `git diff --check` | Passed for changed source and staged changes. |

This is focused coverage (133 distinct behavioral checks), not the full serial repository gate. No emulator/device, provider calls, CI, signing, publication or model spend was used. Traycer's background-shell wrapper exited without running commands on this host; the actual checks ran through native exec with `powershell.exe -NoProfile -ExecutionPolicy Bypass -File build/traycer/check.ps1` and the pinned environment.

## Visual evidence

All data is synthetic. The test fixture deliberately uses tiny epoch values (hence 1970 in existing session rows); they are not captured user activity. The screenshot is the production Workspace in a minimal test scaffold, without the app shell's outer navigation bar. Capture source remains locally available in ignored `build/traycer/return_brief_capture_test.dart`, with the tracked fixture in `test/support/return_brief_fixture.dart`.

| Capture | What it establishes |
| --- | --- |
| [Before](before.png) / [after, light](light.png) | Same current Workspace and fixture, with the new card locally dismissed for the reconstructed before layout. The before image is not a deployed historical binary. The after is implemented UI. |
| [Dark](dark.png) | Hierarchy, outlined surface, contrast and readable actions. |
| [320 px / 2x / RTL, top](narrow-rtl.png) / [lower](narrow-rtl-lower.png) | Icon wraps above the title so “Unreviewed” remains one word. Answer, Review results, Continue and Dismiss shown items remain readable; content scrolls to reach all actions. |
| [Offline](offline.png) | Cached work and blockers explicitly say last observed state; actions reuse guarded flows. |
| [Unknown and partial](unknown.png) | Unsupported read state and incomplete inventory are separate disclosures, not an empty-success claim. |

The final real-font heading assertion checks that “Unreviewed” occupies one line box, in addition to the widget tests' clipping checks. Existing Workspace session rows and quick-composer text ellipsize at 2x/RTL; those pre-existing compact rows are visible in the lower capture and were not redesigned in this slice. The brief's own labels wrap without shrinking.

## Persistence, accessibility and remaining limits

- Exact `(sessionID, idle)` result pairs and `[sessionID, request kind, requestID]` request identities; displayed rows only. Equal/older timestamps from newly loaded sessions, new requests and save-time arrivals stay unacknowledged. Overflow/evicted identities can reappear.
- `oc.returnBrief.<profileId>` is bounded and location-scoped. Restart, malformed blobs, write refusal/retry, queued writes and profile deletion draining are covered. No transcript/request body is stored. Existing read-state privacy behavior is preserved.
- Standard semantic Material buttons, directional alignment, wrapping labels, inline live-region save errors and no automatic focus/modal presentation. The brief adds no polling, animation or haptic dependency.
- Form guard hardening is intentionally conservative: replacing the pending form object retires its open presenter, even if a refresh returned equivalent contents. The user can reopen the current request. Controller reply/cancel recheck ownership after transport wake and never resolve a replacement form.
- The brief shows loaded root sessions. Global forms and requests whose sessions are not loaded remain in Activity; there is no second inbox. Unsupported read state leaves results unknown. Integration/full-suite and native verification remain with the coordinator.
