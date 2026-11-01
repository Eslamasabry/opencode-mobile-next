**Verdict: BLOCKED — one compile blocker and seven behavioral findings.**

Static, read-only review of `96b8f66f..65030c61`. Files were inspected at `65030c61`; no builds, test processes, or edits were performed.

1. **BLOCKER — Error classifier references a nonexistent core contract.**  
   **File:** [KitNotice.kt:49](/home/eslam/Storage/Code/wt/native-reviews/src-kita1/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitNotice.kt:49)  
   **Problem/evidence:** `Failure.Net.Offline`, `Unreachable`, `TrustRefused`, and `DeliveryUnknown` do not exist in this revision. [Failure.kt:36](/home/eslam/Storage/Code/wt/native-reviews/src-kita1/native/core/common/src/commonMain/kotlin/io/github/eslamasabry/oc/core/common/Failure.kt:36) defines the flat hierarchy, including `Network`, `TimedOut`, and `Certificate`. The stale references also occur in `PartAScenes.kt:180` and `PartADesktopTest.kt:466–472`, blocking production and test compilation. This contradicts plan 03 §10.1.  
   **Concrete fix:** Reconcile the classifier, fixtures, and tests with the flat hierarchy. Handle local timeouts and `Refused(DispatchUncertain)` deliberately rather than mechanically treating every timeout or refusal as connectivity trouble.

2. **MAJOR — Frame insets protect the bar and bottom slot, but leave the body exposed.**  
   **File:** [KitScreen.kt:97](/home/eslam/Storage/Code/wt/native-reviews/src-kita1/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitScreen.kt:97)  
   **Problem/evidence:** Safe top/horizontal padding applies only around `topBar`. IME/navigation padding applies only inside `bottom?.let` at line 107. Consequently, `KitScreen(page = true)` without a bar places content under the status bar/cutout; a page without a bottom slot does not reserve IME space. `MainActivity` enables edge-to-edge. The previous compatibility frame applied `safeDrawingPadding()` around its content. B-KIT-029’s desktop frame-order test supplies neither Android insets nor these missing-slot cases.  
   **Concrete fix:** Apply and consume frame-owned safe-area and IME insets around the page content independently of optional slots. Preserve the ground behind system bars. Add inset cases with no bar and no bottom slot.

3. **MAJOR — A long trailing value can consume the title’s entire width.**  
   **File:** [KitRow.kt:111](/home/eslam/Storage/Code/wt/native-reviews/src-kita1/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitRow.kt:111)  
   **Problem/evidence:** `valueBelow` depends only on large text. At normal scale, `RowValue` is an unweighted sibling of the weighted title column, with no width budget. A long value in a 320 dp row can consume the available width before the title is measured. Plan 06 §7.5 requires the value to move below when it cannot share the line. The B-KIT-027 test at `PartADesktopTest.kt:436` uses only the short value “Graphite” at 412 dp.  
   **Concrete fix:** Make placement depend on available width and measured content; bound the inline value so the title retains usable space. Test long values at normal scale, narrow widths, and RTL.

4. **MAJOR — Disabling a row does not disable its trailing action.**  
   **File:** [KitRow.kt:163](/home/eslam/Storage/Code/wt/native-reviews/src-kita1/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitRow.kt:163)  
   **Problem/evidence:** The row passes `enabled` to its own tappable, but passes the trailing `KitLineAction` unchanged to `KitInlineAction` at lines 163/167. That child creates an independently enabled tappable. Thus a disabled row can still execute its advertised action; disabling a parent click handler does not disable descendant handlers. B-KIT-051 tests only a row without a trailing action.  
   **Concrete fix:** Propagate the row’s disabled state and reason to its action. Model any intentionally available “Enable” action separately through the unavailable-row contract. Verify pointer and semantics activation cannot invoke disabled callbacks.

5. **MAJOR — Pane siblings create independent status slots and can display conflicting lines.**  
   **File:** [KitScreen.kt:213](/home/eslam/Storage/Code/wt/native-reviews/src-kita1/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitScreen.kt:213)  
   **Problem/evidence:** `KitPaneScreen` does not establish a status slot around the pane row. The list’s `KitScreen` owns a slot scoped only to the list; a detail `KitScreen` creates another. For example, list status `Update` plus detail status `Work` renders both instead of selecting `Work`. App-wide conditions can likewise appear twice. This violates plan 06 §7.3/B-KIT-035’s one-slot-per-window contract. The existing test covers nested screens, not sibling panes.  
   **Concrete fix:** Establish the shared slot above the panes and make pane screens contribute to it. Test competing list/detail statuses, app-wide override, and contribution removal.

6. **MAJOR — List-pane sheets are confined to 296 dp and leave sibling panes interactive.**  
   **File:** [KitScreen.kt:215](/home/eslam/Storage/Code/wt/native-reviews/src-kita1/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitScreen.kt:215)  
   **Problem/evidence:** In expanded mode, the list is a framed `KitScreen`, which installs another `KitSheetHost` inside the outer host at line 226. `KitSheet` resolves the nearest host, so a sheet opened from the list renders its panel and scrim inside the 296 dp list box. The detail and side panes remain outside the scrim and receive input. Plan 06 §7.4 requires window-level modal presentation.  
   **Concrete fix:** Let pane frames reuse the encompassing sheet host. Test a sheet opened from the list at 1280×800, including scrim coverage and blocked interaction with sibling panes.

7. **MINOR — Disabled reasons are exposed twice; the “once” test accepts the duplication.**  
   **File:** [KitRow.kt:153](/home/eslam/Storage/Code/wt/native-reviews/src-kita1/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitRow.kt:153)  
   **Problem/evidence:** The disabled reason becomes visible supporting text and is merged into the row’s text semantics. `kitTappable` also exposes the same reason as `stateDescription`. TalkBack therefore receives both copies. `PartADesktopTest.kt:417` checks that both representations exist, but never checks the “once” requirement in its name. The Flutter implementation explicitly avoids a second semantic reason.  
   **Concrete fix:** Keep one semantic representation while retaining visible supporting text. Assert the merged text/state payload contains the reason exactly once.

8. **MINOR — Time-only status updates change the live region despite the announcement-key test.**  
   **File:** [KitStatusLine.kt:72](/home/eslam/Storage/Code/wt/native-reviews/src-kita1/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/status/KitStatusLine.kt:72)  
   **Problem/evidence:** The live region merges `message`, `supporting`, and `next`. Updating a duration therefore changes the announced node. `announcementKey` is never consumed by the renderer. The B-KIT-035 assertion at `PartADesktopTest.kt:265` only compares two computed keys; it still passes with this incorrect live-region structure. The claimed status contract excludes time-only updates from announcements.  
   **Concrete fix:** Separate supporting/Now text from the announcement region and use the announcement key for announcement identity. Test rendered semantics while changing only supporting time.

Test sensitivity was also traced in three existing tests: unconditional first-status selection breaks the assertions at `PartADesktopTest.kt:257–258`; removing the width cap breaks the 720 dp/RTL geometry assertions at lines 175–181; fixing supporting text to one line breaks the rendered line-count assertions at lines 407–413. These were static counterfactual checks, not executed mutation tests.