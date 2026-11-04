RESULT: APPROVED WITH FIXES

# Approval: job 44-port-ui (Flutter widgets and other UI, 61 files)

Branch `native/p12x/job-44-port-ui`. Reviewed: the job's non-merge commits over `origin/native/p12-head`
(`87945afa` cards/questions, `0c1a9e79` files/sharing/pause/consents, `34769fe8` model catalog/Git,
`232c784c` tool output + report). Fixes: `f3f5d71e`. Review was static (read and grep against the Flutter
sources in `lib/` and `test/`): no Gradle, no tests, no APK. The build VPS compiles the fixed branch.

## Merge

`git merge --no-ff origin/native/p12-head` (`de611627`) had no conflicts and changed no files: the branch
already contained the P12 head content (`job-29-build-ANDR-2` and `job-30-build-LINUX-3` merges).

## Review

The 61 rows are all recorded (7 already there / 10 ported now / 14 not needed / 30 blocked) and the counts
match. The not-needed rows follow plan/01 (D6 AI Team, D7 Termux migrate-out, D8 phone Paseo, desktop
deferral). The blocked rows are honest: each names the missing native contract, and partial ports are
labelled. Spot-checked already-there claims (grace timer `STATE_ESCALATE_AFTER` 8 s in OpeningGateScreen,
child-limit card variants in RunningScreen, link file share and secret-path refusal, transcript display
preferences without a transcript consumer) all hold.

The ported work is wired into the real app:
- the question sheet through ChatEntries → ChatScreen → ChatRequestSlot;
- card forms, photos and nodes through ChatCardSlot, still behind `OPEN_CODE_2_QUALIFIED=false`;
- the delimited preview and the share sheet in FileScreen;
- the pause line as an app-wide `ShellStatusSource`;
- the model catalog in the composer sheet, with favorites on `oc.modelLibrary.<profileId>` through the
  profile lane;
- the Git entry conveniences on GitScreen;
- tool step bodies from `TurnFactsRows`.

No hardcoded copy was found. New strings exist in English and Arabic. Features arrange kit parts only. No
`Dispatchers`, `Clock.System` or `runCatching` was added, and no provider keys reach the UI.

## Findings fixed (f3f5d71e)

Security/privacy:
1. Tool output was cut before redaction, so a token or a PEM block crossing the cut showed partly
   unmasked. This affected the live step subject (39 chars), output (200 lines / 20,000 chars), prose
   (12 lines / 4,000 chars), readable values (1,000) and connector names (120). Now the whole value is
   redacted first, then cut.
2. Write/Edit/Patch diffs now showed file content and patches that the kit redacts only line by line, so
   multi-line secrets showed. That text is now redacted whole before the diff is parsed.

Crashes / broken wiring:
3. `ModelCatalogPanel` used a `verticalScroll` Column inside `KitSheet`'s scrolling body. Two nested
   vertical scrolls throw "infinite height" on every open. The inner scroll is removed.
4. `BasicComposerViewModel` injected `ChatOutside?`/`Clock?` with null defaults. Metro keys nullable types
   apart from their non-null types, so the defaults always won: Copy ID always failed and New never showed.
   Both are now non-null parameters. Sentinel defaults keep hand-built test constructors compiling.
5. The first-start battery/maker questions were saved as offered on the first start but drawn only on This
   phone. A person who went from setup to chat never saw them. They are now asked above any screen through
   a new `ShellOverlay` multibinding in `:ui:shell`. NavShell wraps the routes in the root `KitSheetHost`
   that the kit docs already describe for app-level overlays; with no overlay, the tree is unchanged.
   `PhoneConsentOverlay` contributes `PhoneFirstStartConsentHost`. The This phone copy and its view-model
   state were removed, so the question is never shown twice.
6. A save that stayed refused trapped the person in the consent dialog. The first refusal now keeps the
   question with the failure line, and a second one stops asking that profile. The allow action dispatches
   explicitly on the kind (NeedsYouNotifications opens nothing).

Behaviour:
7. The Background screen hid its own pause notice whenever any app-wide condition was a pause. It now hides
   it only when the pause is the condition the slot actually shows.
8. The background status rebuilt its lambdas on each composition, so the app-wide condition list
   recomposed every screen while paused. The callbacks are now remembered.
9. Disabled models, now kept in `choices` for the catalog, leaked into the header's Continue with. They are
   filtered out there and in `continueWith`.
10. A session model missing from the catalog became a stand-in with `supportsAttachments = false`, which
    blocked Attach with "can't take files". The stand-in no longer refuses on a guess.
11. The question sheet closed even when the engine refused the answer (its duplicate-value check is
    stricter). It now closes only on `Outcome.Ok`. Stored drafts are resized to the prompt count, so a
    refetch cannot index past the end.
12. Card photo previews Base64-decoded up to 20 MB on every redraw. Each photo is now decoded once. The
    disabled reason while picking reads "Working", not "Choose photo".
13. The Required hint showed on toggle and select fields too. It is now on typed and date fields only, as
    in Flutter.
14. Step rows with input but nothing to draw (running Read, Card, cut-off steps) opened to an empty body.
    A failed subagent with no text showed nothing; it now says "Subagent failed.", and Task bodies show
    after a cut-off, as in Flutter.
15. Git Get latest and Send could be disabled without a reason, which a KitRow rule refuses. They now say
    why: not on the remote yet, not on a branch, or no commits yet.
16. Copy: "Show N more models" is now a plural in English and Arabic. The diff gap label reads
    "Hidden lines: N".

Report: the corrections above are recorded in `docs/native/parity/migration/44-port-ui.md`. That covers the
consent overlay and its residual, redaction order, the corrected TurnFactsRows line, and the stale tests.

## Open, not blocking (for the testing phase or follow-ups)

- **Stale tests.** This job left these unchanged, and they now assert old behaviour: `QaWave1ComposerTest`,
  `ComposerModelsFilesTest` (Apply flow, renamed tags and copy) and `WorldComposerFilesModelsTest`
  (disabled models are listed). `ModelPicker.kt` is now used only by `ModelPickerTest`.
- **Consent residual.** A question is claimed when queued, so a process death before it is answered leaves
  it unanswered, and there is no Settings revision journey yet (the row stays blocked).
- **Smaller gaps against Flutter:**
  - finer photo error words (too large / unsupported / storage);
  - formatting of line-chart values in `KitAgentCard`;
  - todo in-progress and cancelled marks;
  - Write "modified" status from `metadata.exists`;
  - directory, search and webfetch captions;
  - counted subjects drawn left-to-right in `KitToolRow`;
  - localized tool labels in Task "what it did";
  - the share sheet shows the sanitized file name.
- **Duplicate parser.** `engine/files/DelimitedText.kt` duplicates the tested `engine/media` parser.
- **Edits to single-owner modules.** The job edited `ui/kit`, `ui/kit-content` and `ui/shell` (owner
  rule 14); this approval adds `ShellOverlay` to `ui/shell`. The coordinator should note it for the module
  owners.
