Verdict: REQUEST CHANGES — 5 MAJOR, 2 MINOR in `49cc7fcc..08189c83`. Static, read-only review; no builds or tests launched.

1. **MAJOR — Same-key row updates can leave stale content and callbacks.** [KitAnimatedRows.kt:74](/home/eslam/Storage/Code/wt/native-reviews/src-kitb/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitAnimatedRows.kt:74)

   **Problem/evidence:** `existing.item = item` changes an ordinary mutable field while preserving the `RowEntry` identity. The composable lambdas capture that entry. With strong skipping, those lambdas can remain cached and the existing `AnimatedVisibility` subtree can skip recomposition. Replacing `(id=1, label="Connecting")` with `(id=1, label="Connected")` therefore need not update the displayed row or its action. The desktop test changes keys; it never updates a retained key’s data.

   **Fix:** Make `item` observable snapshot state, or capture the current item outside the cached content lambda so it becomes a changing composition input. Test same-key label and callback replacement after animations settle.

2. **MAJOR — Changing motion level destroys retained child state.** [KitSwap.kt:41](/home/eslam/Storage/Code/wt/native-reviews/src-kitb/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitSwap.kt:41)

   **Problem/evidence:** Off renders content under `Box`; Full/Calm render it under `AnimatedContent`. Switching levels disposes one subtree and creates another even when `target` is unchanged. `KitReveal`, `KitPresence`, `KitEntrance`, `KitSpin`, and `KitSheetStep` use similar branch changes. A field holding its draft in `rememberTextFieldState()` can lose that draft when Saver or system animation settings force Off. Focus and child effects also restart. B-KIT-023 requires motion to settle, not content to reset.

   **Fix:** Preserve the content’s composition identity while changing animation policy. Add a test that edits a child field, switches Full → Off → Full without changing its target, and verifies its draft, selection, and effect identity survive.

3. **MAJOR — A drag can cancel dismissal permanently and leave the sheet blocking the page.** [KitSideSheet.kt:139](/home/eslam/Storage/Code/wt/native-reviews/src-kitb/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitSideSheet.kt:139)

   **Problem/evidence:** Every drag delta launches an independent `offset.snapTo()`. Those mutations compete with the dismissal `offset.animateTo()` at line 119. An `Animatable` mutation cancels the interrupted animation’s coroutine, so a drag during closing can prevent line 120’s `present = false` from executing. Dragging remains enabled while `visible == false`; the unchanged effect keys do not restart dismissal. The sheet and full-screen clickable scrim can remain composed indefinitely.

   **Fix:** Serialize drag and settle mutations, stop accepting drag updates during dismissal, and ensure an interrupted close converges to the current visibility target. Test dismissal during an active drag and another touch during the exit spring.

4. **MAJOR — Pull-to-refresh still animates under in-app Off.** [KitRefresh.kt:109](/home/eslam/Storage/Code/wt/native-reviews/src-kitb/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitRefresh.kt:109)

   **Problem/evidence:** `rememberPullToRefreshState()` receives no kit motion policy. Inspection of the cached CMP M3 `1.13.0-beta01` implementation shows its `animateToHidden()` and `animateToThreshold()` call `Animatable.animateTo()` with the default animation spec. They do not consult `KitMotionScheme`. Consequently, releasing an unarmed pull under app Off still springs back, violating plan 08 §5.10 and B-KIT-054. Wiring the currently unused scheme alone will not fix this API.

   **Fix:** Supply a motion-aware `PullToRefreshState` whose settle methods snap under Off and use the required specs otherwise. Verify an actual pull/release with app Off and system animation scale 1.

5. **MAJOR — Three motion tests cannot detect the incorrect behavior named in their titles.** [KitbDesktopTest.kt:133](/home/eslam/Storage/Code/wt/native-reviews/src-kitb/native/ui/kit/src/desktopTest/kotlin/io/github/eslamasabry/oc/ui/kit/KitbDesktopTest.kt:133)

   **Problem/evidence:** Counterfactual inspection identifies these false positives:

   - Line 133’s count-roll test still passes if both slide transitions are deleted: it checks only which children are composed.
   - Line 222’s Off entrance test still passes with incorrect alpha or translation: `assertExists()` does not inspect rendered appearance.
   - Line 230’s “final values on the first frame” test still passes with incorrect chevron rotation, dim alpha, or progress fill: it checks three text nodes.

   This does not establish B-KIT-023’s rendered-state contract or plan 08 §9.3’s zero-running-animation requirement.

   **Fix:** Assert rendered frames or transform values, and instrument running animations. Include the system-scale-zero path. Separately, the tests at lines 116, 180, and 200 **would** reject early swap disposal, bypassed value easing, and immediate Full row removal, respectively; these were inspected statically, not mutation-tested.

6. **MINOR — Disabled refresh still intercepts pull gestures.** [KitRefresh.kt:111](/home/eslam/Storage/Code/wt/native-reviews/src-kitb/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitRefresh.kt:111)

   **Problem/evidence:** `enabled` suppresses the callback and indicator but is never passed to `PullToRefreshBox`. The pinned API exposes `enabled`, defaulting to true. A disabled wrapper therefore continues consuming downward nested scroll while displaying no indicator.

   **Fix:** Pass `enabled = enabled` to `PullToRefreshBox`. Test gesture consumption with refresh disabled.

7. **MINOR — Cancelled predictive back jumps instead of springing home.** [KitSideSheet.kt:134](/home/eslam/Storage/Code/wt/native-reviews/src-kitb/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitSideSheet.kt:134)

   **Problem/evidence:** Preview displacement exists only while the navigation transition is `InProgress`. Cancellation immediately returns displacement zero; nothing animates the last preview offset back. At full progress this jumps 10% of the sheet width, contradicting plan 08 §5.5’s explicit `Spatial` cancel spring. Existing tests cover completion, not cancellation.

   **Fix:** Retain the preview offset and spring it to zero on cancellation; snap under Off. Test an intermediate cancel frame and the settled frame.