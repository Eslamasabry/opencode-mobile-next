RESULT: APPROVED WITH FIXES

# Approval: job 43-port-kit (Flutter kit parts)

Branch `native/p12x/job-43-port-kit`. Reviewed: the job's non-merge commits over `origin/native/p12-head`
(`d2aa243b`, `cd5c1054`, `ed7e1ea0`, `0ecc8b02` report only; `e7ee1a54` foundations/Signal, `6f1e54fb` chat,
`14870d63` requests/logs, `e2452284` viewer/diff). Review by reading only: Gradle cannot resolve Google Maven in
this environment, so nothing was compiled; the build VPS compiles after this. No tests were written or run and no
APK was built (owner: coding first, testing afterwards).

## Merge

`git merge --no-ff origin/native/p12-head` was a no-op ("Already up to date"). The branch already contains the
current P12 head, so there was no merge commit and no conflict.

## Review

All 76 rows are accounted for (9 already there / 29 ported now / 34 not needed / 4 blocked). The "not needed" rows
match the binding plan: live backgrounds and scenes are D11, AI Team is D6, and old depth/edge keys are unread per
plan 06. Spot-checked "already there" citations hold: UnfinishedTail used by StreamingMarkdown, FreshFade, KitMicPress
(350 ms / 18 dp / 80 dp), ComposerGlow, DiffModel, KitRowGroup. The four blocked rows are honest: there is no Inbox
entry in ChatsEntries, ChatHeaderPort has no branch/origin/live-link facts, there is no current-server switcher
contract, and form ingress and durable request drafts are missing.

The ported work is real and reachable: composer tray, glow choice, prompt lift, picture thumbnails through the
identity-fenced read, table fitting, step nodes, rail and Signal work line, frost behind overlaid chrome, Signal slip
and answer keys, question sheet through the engine's refusable answer lane, request targets, searchable log panel,
file viewer Find/Wrap/formatted bodies, and the diff controls.

- **Rules:** UI stays kit-only (drawing only in `:ui:kit*`), and no `Dispatchers` or `Clock.System` is added.
- **Links:** file Markdown links go policy, then confirm, then a second policy check, then the opener.
- **SVG and diffs:** SVG is shown only after `StaticSvg.parse`, and Coil has no network fetcher. Diff copy, comment
  and add-to-prompt all use redacted text.
- **Strings:** all 156 referenced string keys exist in English and Arabic, and there is no hardcoded user-visible
  English.

## Findings and fixes

Commits `9ebf33cf` and `4d58b1e5`:

1. **File viewer crashed on Markdown with a code fence.** `KitCodeBlock` needs `LocalKitRedactor`, and
   `FilesEntries` never provided it. Fixed: `FilesEntries` takes `KitChatEnvironment` and wraps `FileScreen`, as Git
   and Review do. This also gives `KitCodeLine` its work context.
2. **The composer's field and Stop/Send row overlapped while a reply ran.** The new glow holds its content in a Box.
   Fixed: the two now stack in a Column inside the glow, as before.
3. **Overlaid chrome rebuilt the composer, bar and transcript.** This happened on every status line, Find, or
   Loading→Live change (two different layout trees), which dropped focus, keyboard and scroll. Fixed: `KitScreen`
   moves them with `movableContentOf` when `overlayChrome` is on.
4. **Prompt lift never matched prompts with surrounding whitespace.** The composer sends trimmed words. Fixed: the
   offer and the take both compare trimmed text.
5. **Full diffs: every row had a 48 dp minimum height.** The gutter used `KitTappable`; Flutter uses a plain gesture
   detector. Fixed with a compact clickable that keeps its accessibility label.
6. **Full diffs: unwrapped side-by-side cells were 0 wide.** Weights under an unbounded `LazyColumn` measure to zero.
   Fixed: the list gets a width measured from the longest line, capped at 4000 characters.
7. **KitConfirm marked the first plain consequence "lost"** for every non-neutral caller, including kept facts such
   as Linux storage's "… stay". Fixed: plain lines keep the neutral bullet; only `consequenceItems` callers get marks.
8. **A failed staged Keep / Put back closed its question.** Undo all already kept it open. Fixed: it stays open with
   `working`/`failed` for a retry, and closes only when the review went stale.
9. **The chat header showed the fixed word "Git" as a branch name**, duplicating the bar's Git action. That
   contradicts the row's own "not invented". Fixed: removed, along with the `onBranch` plumbing.
10. **Sheet bodies lost their item spacing** (`spacedBy(labelGap)`), and **request cards lost the attention
    surface.** Both restored.
11. **Refused file links showed the "Open with failed" words.** Fixed with a new `FileNotice.LinkRefused` and the
    existing link-refused copy. `FileViewModelTest` and `FilesScreensTest` were updated so they still compile with
    the new `links` collaborator and actions.
12. **The report preview tailed 2000 lines and opened at the end** (a live-log behaviour). Fixed: `KitLogPanel`
    gained `live = false`, which keeps every line, opens at the top and has no Pause/Follow.
13. **Smaller fixes:**
    - The step rail follows RTL.
    - The frost style check compares the enum. Solid-only frost is correct per plan 06.
    - The code viewer decides on highlighting once per file, not once per line.
    - CSV/TSV tables cut at 200 rows show the partial-file notice.
14. **Report corrected:**
    - Request waiting age is not shown (live chat passes `nowMs = null`, as before this job).
    - The header scope shows no Git target.
    - Full diffs scroll inside a 560 dp window, and only Copy lines is reachable.
    - The consequence marks and the log preview mode are described as they now work.

## Left for the owner's later phases (not blocking)

- **Tests that will fail when run, to update in the testing phase:**
  - `ReviewPresenterTest` B-CHAT-105: a failed undo-all now keeps `confirming`.
  - `ChatHeaderUiTest`: the subtitle is replaced by the scope when a project is known.
  - `ComposerModelsFilesTest` B-CHAT-009: the Add sheet became the inline tray.
  - The `KitToolRow` step goldens: the node now has a tile.
- **Performance:**
  - Picture thumbnails have no cache, and base64 decoding happens on the caller.
  - Viewer Find walks every match for each visible line.
  - Diff syntax colours are re-derived per token.
- **Behaviour gaps:**
  - Frost covers the composer but not the reader strip and request slot around it.
  - Deny and Always allow are not drawn as Signal keys.
  - A refused question-sheet send is silent.
  - The consequence marks have no spoken label.
  - The rail does not follow the glyph's text scale.
- **Compile:** the build VPS must confirm it, since nothing could be compiled here.
