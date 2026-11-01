**Verdict: BLOCKER — the range contains incompatible tests, credential-handling defects, and modal/action behavior that violates the claimed contracts.**

Reviewed `882f0fd4..7db496b2`; all line numbers below refer to `7db496b2`. Static review only; no files changed or Gradle tasks run.

1. **BLOCKER — New tests reference removed core contracts.**  
   **Files:** `PartBRulesTest.kt:194`, `PartBRulesTest.kt:199`, `PartBScenes.kt:243`.  
   **Problem/evidence:** These use `Failure.Net.Offline`, `Failure.Storage`, and `StorageProblem.Full`. At the reviewed tip, `native/core/common/.../Failure.kt:14` explicitly documents their replacement; the available types include `Failure.Network` and `Failure.StorageRefused`. Common tests and shared screenshot scenes therefore cannot compile against this core revision.  
   **Fix:** Update the fixtures to the flat hierarchy, then rerun desktop and Android host tests against the exact integrated revision.

2. **MAJOR — Secret registration happens after exposure to consumers.**  
   **File:** `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitField.kt:287`.  
   **Problem/evidence:** Registration observes already-published text through `LaunchedEffect` and `snapshotFlow`. A consumer can read or submit the changed state before registration; intermediate values can also be conflated. The default sink at line 98 silently does nothing, with no production provider found at this revision. This contradicts plan 06 §7.5 and SI-4’s registration-before-host requirement.  
   **Fix:** Register synchronously before publishing accepted input, including explicit Paste edits, and require a production sink. The test at `PartBDesktopTest.kt:304` waits for idle before checking the final value, so it cannot detect this ordering defect.

3. **MAJOR — Secret input dialogs persist credentials in saved state.**  
   **File:** `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitDialog.kt:230`.  
   **Problem/evidence:** `kind` accepts `Secret`, but every kind uses `rememberTextFieldState(initial)`. The pinned Compose implementation calls `rememberSaveable` with `TextFieldState.Saver`. Typed credentials consequently enter saved state, and a nonempty `initial` prefills them. Both violate plan 16 SI-4 and B-SEC-187.  
   **Fix:** Use empty, nonsaveable `remember { TextFieldState() }` for secrets; reject secret initial values and use Saved/Replace for stored credentials. Verify recreation leaves no credential in either the field or saved-state payload.

4. **MAJOR — Closing any dialog permanently disables sheet Back handling.**  
   **File:** `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitSheet.kt:262`.  
   **Problem/evidence:** Sheet Back handling requires `state.dialog == null`, but `hideDialog()` at line 141 only changes the animation target; it never clears `dialog`. Open a dirty sheet, dismiss its discard question with **Keep editing**, then press Back: the sheet handler is disabled and navigation underneath can receive Back, bypassing the discard guard.  
   **Fix:** Base ownership on active dialog visibility and clear the matching retained entry after exit. Add a regression covering Back after Keep editing.

5. **MAJOR — Nondismissible modals pass Back to the underlying navigation.**  
   **Files:** `KitSheet.kt:262`, `KitDialog.kt:116` in the same modal directory.  
   **Problem/evidence:** Both unregister their Back handler when `dismissible` is false. Back can then pop the underlying screen, removing the supposedly nondismissible operation or question. This contradicts `KitSheet`’s documented “keeps it until answered” behavior.  
   **Fix:** Let the visible topmost modal always consume Back; conditionally suppress dismissal inside its handler.

6. **MAJOR — Moving a working action into More makes it executable again.**  
   **File:** `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/action/KitActionBlock.kt:125`.  
   **Problem/evidence:** `MoreActions` copies `enabled` and `onClick` into `KitMenuItem` but drops `working`. A third tertiary action with `working = true` remains enabled in More and invokes its callback again, permitting duplicate mutations. Direct buttons suppress that callback at `KitButton.kt:119`.  
   **Fix:** Preserve working-state suppression in overflow items. Test the same action as both a visible button and an overflow item.

7. **MAJOR — Empty input bypasses validation and can be submitted.**  
   **File:** `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitDialog.kt:236`.  
   **Problem/evidence:** Empty text forces `error = null` without calling `validate`. Clearing a required name therefore enables both confirmation and IME submission, even when the supplied validator rejects blank values.  
   **Fix:** Validate every submitted value, including empty strings. If errors should remain hidden before interaction, track that presentation state separately from submission validity.

8. **MAJOR — Reopening an input dialog reuses the previous operation’s text.**  
   **File:** `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitDialog.kt:230`.  
   **Problem/evidence:** The remembered field survives `visible = false`; reopening only selects its existing text. Changing `initial` does not reset remembered text. Rename A, close, then open the same dialog for B: A’s edited value remains and can be submitted for B.  
   **Fix:** Give each dialog operation an identity and initialize state per operation, preserving nonsecret text across rotation within that operation. Test reopening after cancellation and reopening for a different target.

9. **MAJOR — A disabled risky switch can still be enabled through its open confirmation.**  
   **File:** `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/row/KitSwitchRow.kt:151`.  
   **Problem/evidence:** After the risk fold opens, changing `enabled` to Disabled leaves **Turn on** enabled. Its callback unconditionally calls `onCheckedChange(true)`, bypassing the row’s current disabled state.  
   **Fix:** Apply current enabled/locked/checked rules to the confirmation button and recheck them before dispatch. Test disabling the row while its risk fold is open.

10. **MINOR — The menu “runs once” test does not exercise menu dispatch or count calls.**  
    **File:** `native/ui/kit/src/desktopTest/kotlin/io/github/eslamasabry/oc/ui/kit/PartBDesktopTest.kt:350`.  
    **Problem/evidence:** `MenuScene` renders `KitMenuPanel` directly, and the test assigns the chosen label to one variable. Duplicate callbacks produce the same value; deleting `item.onSelect()` from `KitMenu` also leaves this test unaffected.  
    **Fix:** Exercise `KitMenu` through its anchor, record callback counts, and assert dismissal plus exactly one item callback.

Three additional tests were checked for mutation sensitivity by static inspection:

- `PartBDesktopTest.kt:127`: duplicating a choice callback breaks the exact callback-list assertion.
- `PartBDesktopTest.kt:140`: dispatching a selected segment breaks the empty-list assertion.
- `PartBDesktopTest.kt:198`: removing typed-name gating breaks the disabled-state assertions.

These are counterfactual checks, not executed mutation tests; finding 1 prevents accepting the reported test results as evidence for the reviewed tip.