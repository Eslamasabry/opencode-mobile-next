# KitDiffView — API freeze (wave 0)

> **Copy (SEC-13, coordinator 2026-09-27):** this part copies verbatim: `KitCopy.copy(context, text, redact: false)`. Code, diffs and messages are the person's own content.

Unit: `kit-KitDiffView` (wave 1, tier 1d, kind `kit-part`). Write set (work-units.json): `lib/ui/kit/kit_diff_view.dart` (new), `lib/ui/widgets/diff_view.dart` (becomes a wrapper, C24; slice-P3.7a deletes it, C45), `test/kit/kit_diff_view_test.dart`, `test/goldens/kit/kit_diff_view_golden_test.dart`; its `tests` list: `test/diff_view_test.dart`, `test/reader_preferences_test.dart`, `test/demo_isolation_test.dart`. Spec: kit-v2.md §1.15, §8.2 ("diffs side by side from expanded"), map proposals for review-workspace (Fix: one KitDiffView with a sticky file header, one "Change 1 of N" navigator across files, hanging indents, `@@` as line ranges) and diff-view (a read-only mode of the one component); C15, C24, C25, C33, C36, C45. Rules: STATE-9, STATE-11, KIT-32, LAY-8, LOOK-6, COPY-30, A11Y-2, PERF-2, MOT-5, TEST-5, KIT-43, R11, R12.

## Purpose

The one diff renderer: unified on a phone, side by side from an expanded window, with one file header, one "Change 1 of N" navigator across files, a file list, and line selection for Comment, Add to prompt and Copy. It serves the review workspace, revert previews, run results and a request's "See the change".

## Replaces

- **Map elements (kit-v2.json `assignment` → `KitDiffView`): 6 elements on 2 pages.**
  - diff-view (diff-view-body: the second renderer, whose continuation lines lose their indentation);
  - review-workspace (review-workspace-diff: `_UnifiedDiffRow`/`_SplitDiffRow`; review-workspace-file-header: the file name shown three times; review-workspace-file-strip: chips repeating each name and `+/-`; review-workspace-hunk-nav: "1 ↑↓" plus a bottom "1 hunk" bar; review-workspace-selection-bar: clear, copy, add, Comment).
- **Merged proposal names:** `KitDiffView`.
- **Moved by this unit (C24, R12):** `lib/ui/widgets/diff_view.dart` (811 lines; G16 `AppBar` 1, `CloseButton` 1, `Container` 2, `Icon` 2, `IconButton` 1, `InkWell` 1, `Material` 2, `Scaffold` 1, `SingleChildScrollView` 1, `SliverPersistentHeader` 1, `SnackBar` 2, `Text` 13, `Tooltip` 1; G1 `SnackBar(` 2, `showSnackBar(` 2). Its parser (`_DiffModel`: unified patch and before/after pair, 3 context lines, 20-line expansion) moves into the kit as `KitDiffFile.fromPatch`/`fromTexts`. `DiffView`, `DiffView.single`, `DiffView.open`, `DiffRow`, `DiffRowKind` and `DiffGap` keep compiling as forwarding wrappers (KIT-43: `/// Retired by kit-KitDiffView: use KitDiffView`, and G2 ratchet patterns `DiffView(` and `DiffView.single(`). The wrapper's copy uses `KitIconButton.copy`, so its 2 `SnackBar`s go (G1 and G16 for the file drop).
- **Callers of the wrapper (unchanged by this unit):** `chat_screen.dart` (`DiffView(`, chat chain), `chat/permission_sheet.dart` and `staged_revert_screen.dart` (`DiffView.single(`).
- **Code reach, adopted by other units:**
  - `review_workspace.dart`'s own renderer: `_ReviewFileStrip`, `_ReviewFileList`, `_ReviewDiffToolbar`, `_ReviewPhoneDiffToolbar`, `_ReviewDiffCanvas`, `_UnifiedDiffRow`, `_SplitDiffRow`, `_ReviewSelectionBar`, `_ReviewHunkBar` (G16 in that file: `Container` 18, `Icon` 20, `IconButton` 12, `InkWell` 8, `TextButton` 9, `Scrollbar` 3, `RotatedBox` 2, `VerticalDivider` 2, `IntrinsicHeight` 2; G15 width literals 2) → screen-review-1 (C37);
  - `run_result_view.dart`, `merge_section.dart` and the request's "See the change" → shared-review-1, slice-P3.7a (C36, C45), kit-KitRequestSheet (C15).
- **Widgets retired directly:** in `diff_view.dart`, the renderer's `Container`, `InkWell`, `Material`, `SliverPersistentHeader`, `SingleChildScrollView`, `Tooltip`, `IconButton` and most `Text` counts, and both `SnackBar`s (copy becomes `KitIconButton.copy`). The wrapper keeps its route frame (`Scaffold`, `AppBar`, `CloseButton`, the title `Text`) until slice-P3.7a deletes the file, so no counts rise.

## File

- `lib/ui/kit/kit_diff_view.dart`: `KitDiffLineKind`, `KitDiffLine`, `KitDiffGap`, `KitDiffFileStatus`, `KitDiffFile`, `KitDiffMode`, `KitDiffSide`, `KitDiffSelection`, `KitDiffView`, `showKitDiff`.
- `lib/ui/widgets/diff_view.dart`: forwarding wrapper. It converts `FileDiff` (`lib/api/models.dart`, which it already imports; the kit never imports `lib/api`, ARCH-1) to `KitDiffFile`, reads the reader wrap preference and passes it in, and keeps the `diff-view` key on its frame.
- Tests: `test/kit/kit_diff_view_test.dart`; `test/diff_view_test.dart` keeps passing against the wrapper.
- Gallery: `test/goldens/kit/kit_diff_view_golden_test.dart`.

## Public API

```dart
enum KitDiffLineKind { context, added, removed, hunk }

@immutable
class KitDiffLine {
  const KitDiffLine(this.text, this.kind, {this.oldNo, this.newNo});
  final String text;
  final KitDiffLineKind kind;
  final int? oldNo, newNo;
}

/// Unchanged lines that start folded. [lines] is null when the source (a
/// unified patch) does not carry them: the bar then only states the count.
@immutable
class KitDiffGap {
  const KitDiffGap({required this.count, this.lines});
  final int count;
  final List<KitDiffLine>? lines;
}

enum KitDiffFileStatus { modified, added, deleted, renamed }

/// One file's change, normalised into lines and gaps. Built by the caller
/// from its own model (the kit never sees lib/api types).
@immutable
class KitDiffFile {
  const KitDiffFile({
    required this.path,
    required this.segments,          // KitDiffLine and KitDiffGap in file order
    required this.added,
    required this.removed,
    this.status = KitDiffFileStatus.modified,
    this.oldPath,                    // renamed: "Renamed from {oldPath}"
    this.binary = false,             // "Binary file · not shown"
    this.fullText,                   // the whole new text, when known (Copy file)
    this.patch,                      // the unified patch, when known (Copy patch)
  });

  /// Parses a unified patch; `@@` headers become hunk lines read as line
  /// ranges ("Lines 12–18"), and 3 context lines stay around each change.
  factory KitDiffFile.fromPatch(String path, String patch, {KitDiffFileStatus? status, String? oldPath});

  /// Diffs a before/after pair (either may be null: added or deleted).
  factory KitDiffFile.fromTexts(String path, {String? before, String? after, KitDiffFileStatus? status});

  final String path;
  final List<Object> segments;
  final int added, removed;
  final KitDiffFileStatus status;
  final String? oldPath, fullText, patch;
  final bool binary;

  /// The number of changes (runs of added/removed lines) in this file.
  int get changeCount;
}

enum KitDiffMode {
  unified,
  /// Side by side. Used only where the diff's own box is expanded or wider
  /// (KitLayout.windowFor(width) >= KitWindow.expanded); narrower, it
  /// renders unified.
  split,
}

enum KitDiffSide { old, current }

/// Selected lines, in one file and one side.
@immutable
class KitDiffSelection {
  const KitDiffSelection({required this.file, required this.side, required this.startLine, required this.endLine, required this.text});
  final KitDiffFile file;
  final KitDiffSide side;
  final int startLine, endLine;   // that side's numbers
  final String text;              // the selected lines, redacted
}

/// The one diff renderer (K2 §1.15).
///
/// States: loading, empty, error, loaded, file-binary, file-renamed,
/// too-big, selecting.
class KitDiffView extends StatefulWidget {
  const KitDiffView({
    super.key,
    required this.files,
    this.mode,                     // null = auto: unified below expanded, split from expanded
    this.initialFile,              // index into [files]
    this.initialChange,            // 0-based, across all files
    this.readOnly = true,          // true: no selection, no Comment / Add to prompt
    this.onComment,                // ValueChanged<KitDiffSelection>: the selection bar's primary
    this.onAddToPrompt,            // ValueChanged<KitDiffSelection>: tertiary
    this.fileActions,              // List<KitMenuItem> Function(KitDiffFile): the file header's menu
    this.maxLines,                 // null = no cap (virtualised); a preview passes e.g. 400
    this.onOpenAll,                // with [maxLines]: "Open all"
    this.loading = false,
    this.error,                    // a failure in words; the part shows it with Try again
    this.onRetry,
    this.wrap,                     // null = wrap on compact, scroll sideways from medium
    this.onWrapChanged,
    this.keyPrefix = 'kit-diff',   // internal keys are '<prefix>-…' (the wrapper passes 'diff')
  }) : assert(readOnly || onComment != null || onAddToPrompt != null),
       assert(error == null || onRetry != null);

  final List<KitDiffFile> files;
  final KitDiffMode? mode;
  final int? initialFile, initialChange, maxLines;
  final bool readOnly, loading;
  final ValueChanged<KitDiffSelection>? onComment, onAddToPrompt;
  final List<KitMenuItem> Function(KitDiffFile file)? fileActions;
  final VoidCallback? onOpenAll, onRetry;
  final String? error;
  final bool? wrap;
  final ValueChanged<bool>? onWrapChanged;
  final String keyPrefix;

  /// Unchanged lines revealed per tap on a gap bar.
  static const int expandStep = 20;
}

/// Opens a read-only diff as a page (KitPageRoute, Close at the end),
/// titled by what it shows ("Changes in this reply"), never a sheet on a
/// sheet. Returns when it closes.
Future<void> showKitDiff(
  BuildContext context, {
  required String title,
  required List<KitDiffFile> files,
  int? initialFile,
  bool? wrap,
  ValueChanged<bool>? onWrapChanged,
});
```

- **Layout.**
  - **File header** (sticky at the top of the diff): "3 files · README.md +4 −1". With 2+ files it is a `KitPickerRow` that opens `showKitChoiceSheet` of the files (each: path, `+n −n`, the status word). With one file it is a plain header. It carries the file's `fileActions` in a `KitIconButton` "More" (Copy file, Copy patch, Ask about this file, …), and Wrap.
  - **File list** (from a large diff box, `KitLayout.windowFor(width) == large`, with 2+ files): a column of `paneListWidth` (296, LAY-5) at the start, one row per file with its counts and status, the current one selected. The picker row is then just the header.
  - **Navigator:** "Change 1 of 7" with previous and next `KitIconButton`s (48 dp), across files: next on the last change of a file opens the next file's first change.
  - **Lines:** a line-number gutter (both numbers in unified, one per side in split), a `+`/`−` glyph column, then the text. Continuation lines of a wrapped line are indented to the text column (a hanging indent). Hunk headers read as line ranges ("Lines 12–18").
  - **Gaps:** "Show 20 unchanged lines" bars that expand in place by `expandStep` and collapse again; a gap without lines states its count only.
  - **Selection** (`readOnly: false`): tap or drag on line numbers selects a range on one side; the selection bar is a `KitActionBlock` pinned at the bottom: Comment (primary), Add to prompt and Copy lines (tertiary), and Clear (a `KitIconButton`).
- **Compatibility (R11).** The wrapper keeps `DiffView({diffs, title, allowCopy})`, `DiffView.single`, `DiffView.open`, `DiffView.wrapBelow`, `DiffView.expandStep`, and the public `DiffRow`, `DiffRowKind`, `DiffGap` types. It passes `keyPrefix: 'diff'`, so `diff-file-header-<path>`, `diff-gap-<i>`, `diff-expand-down-<i>`, `diff-collapse-<i>` and `diff-view-horizontal` keep their meaning (TEST-5; used by `test/diff_view_test.dart`).
- **Internal keys (`<prefix>` = `keyPrefix`):** `<prefix>-file-header-<path>`, `<prefix>-file-picker`, `<prefix>-file-list`, `<prefix>-file-row-<index>`, `<prefix>-nav-previous`, `<prefix>-nav-next`, `<prefix>-nav-label`, `<prefix>-gap-<i>`, `<prefix>-expand-down-<i>`, `<prefix>-collapse-<i>`, `<prefix>-horizontal`, `<prefix>-line-<file>-<side>-<n>`, `<prefix>-selection-bar`, `<prefix>-open-all`.
- **Kit copy (ARB, `kit` prefix, en + ar):** `kitDiffFiles` "{count, plural, =1{1 file} other{{count} files}}", `kitDiffChangeOf` "Change {index} of {count}", `kitDiffPreviousChange` "Previous change", `kitDiffNextChange` "Next change", `kitDiffLines` "Lines {start}–{end}", `kitDiffShowUnchanged` "{count, plural, =1{Show 1 unchanged line} other{Show {count} unchanged lines}}", `kitDiffHideUnchanged` "Hide unchanged lines", `kitDiffUnchangedCount` "{count, plural, other{{count} unchanged lines}}", `kitDiffNoChanges` "No changes", `kitDiffBinary` "Binary file · not shown", `kitDiffRenamed` "Renamed from {path}", `kitDiffAddedFile` "New file", `kitDiffDeletedFile` "Deleted", `kitDiffTooBig` "Showing {shown} of {total} lines", `kitDiffOpenAll` "Open all", `kitDiffLineAdded` "Line {number} added", `kitDiffLineRemoved` "Line {number} removed", `kitDiffComment` "Comment", `kitDiffAddToPrompt` "Add to prompt", `kitDiffCopyLines` "Copy lines", `kitDiffClearSelection` "Clear selection", `kitDiffSelected` "{count, plural, =1{1 line selected} other{{count} lines selected}}", `kitDiffCounts` "{added} added, {removed} removed", `kitDiffLoadFailed` "Couldn't load the changes", plus the shared `kitWrapLines`, `kitMore` "More" (KitAction.md), `kitTryAgain`.

## States

| State | Look |
|---|---|
| loading | skeleton diff lines (`KitSkeletonRows`), the header already sized |
| empty (`files` empty) | inline `KitStateView` "No changes" |
| error | inline `KitStateView` with `error` as its body and Try again (`onRetry`) |
| loaded | header, navigator, lines |
| file binary | the file's header, then "Binary file · not shown" (and the counts, if known) |
| file renamed / added / deleted | the status word on the header's supporting line ("Renamed from lib/old.dart") |
| too big (`maxLines`) | the first `maxLines` lines, then "Showing 400 of 3,200 lines" with "Open all" (`onOpenAll`) |
| selecting | selected lines tinted, the selection bar with "3 lines selected" |

KIT-12 doc comment: "States: loading, empty, error, loaded, binary, renamed, too-big, selecting". Disabled: Comment is disabled with a reason when the selection spans both sides ("Select lines on one side").

## Tokens

- **ThemeRoles:** `codeAddedSurface` / `codeRemovedSurface` (line tints), `success` (`+` glyph and `+n`), `codeRemoved` (`−` glyph and `−n`; VL §3's code role), `text1` (line text), `text3` (line numbers, context `+/−` column), `text2` (gap bars, hunk ranges), `hairline` (header rule, split divider), `accent` (the selection mark on selected line numbers and the current file row's mark, LOOK-6), `surface1` (sticky header over `ground`).
- **KitText:** `mono` (lines, numbers, paths), `rowTitle` (the header path), `secondary` (header supporting line, gap bars), `caption` (navigator label), `button` via `KitButton`.
- **KitTokens (VL branch):** `rowHeight` (header), `minTarget`, `space1`–`space4`, `smallIconSize`, `panelCornerRadius` (file list panel), `detailsSurface` (the diff surface).
- **Layout names:** `KitLayout.paneListWidth` (296 after the LAY-5 amendment), `KitLayout.windowFor` on the diff's own width.
- **Pre-wave seams:** `KitTokens.hairlineWidth` (the split divider and header rule), `KitBidi`, `KitCopy`, `KitRedact`.
- **No new tokens.** The line tint is the existing `codeAddedSurface`/`codeRemovedSurface` getters on `ThemeRoles`.

## Adaptive

| Diff box width (`KitLayout.windowFor(width)`) | Behaviour |
|---|---|
| compact | unified; lines wrap by default (today's `DiffView.wrapBelow` 600 rule); file picker row |
| medium | unified; lines scroll sideways by default (Wrap toggles); file picker row |
| expanded | split side by side (`mode: null` or `split`), each side scrolling together; file picker row |
| large | split, plus the file list column at the start |

The box width, not only the window, decides: a diff in the 340 dp changes pane of a large window renders unified (LAY-5 panes). `mode: split` in a narrow box renders unified, and the host's mode control shows the reason ("Side by side needs a wider window") through its own `disabledReason`.

- **Keyboard (LAY-10):** Tab reaches the file picker or list, the navigator, Wrap, More, then the lines. `n` / `p` (and F7 / Shift+F7) move to the next and previous change when the diff has focus; Up/Down move by line; Shift+Up/Down extend a selection; Enter on a selection runs Comment; Esc clears the selection; Ctrl+C copies it.
- **Pointer:** mouse drag over line numbers selects; text is mouse-selectable for copying; hover shows the navigator and More tooltips; right-click on a line opens a menu with Comment, Add to prompt and Copy lines (the KIT-28 twin of the selection bar).

## Accessibility

- Added and removed lines carry a `+` or `−` glyph and semantics ("Line 12 added: …", "Line 9 removed: …"), never only a tint (STATE-9).
- Moving to a change moves focus to its first line and announces "Change 2 of 7" once (the navigator label is a polite live region).
- The file header reads "README.md, 4 added, 1 removed, 1 of 3 files"; the picker row has button semantics and opens the file sheet.
- Line numbers are selectable 48 dp-tall targets when selection is on (the row is at least 48 dp tall only in selection mode; read-only rows keep the mono line height).
- 200 % text: lines wrap with hanging indents, the gutter grows with the numbers, and the header wraps to two lines.

## RTL

- The diff is always LTR: gutter on the left, old side on the left in split, text left-aligned (LAY-8, COPY-30).
- The header, navigator and selection bar follow the locale; the path is isolated with `KitBidi.ltr`; the previous/next arrows are up/down and do not mirror.

## Motion and haptics

- Gap bars unfold with `KitReveal` inside the virtualised list's item (the item rebuilds at its new height; no `AnimatedSize`, MOT-5).
- The navigator scrolls to a change on `KitMotion.standard` with `emphasized`, or jumps under `KitMotion.reduced(context)`.
- No layout animation while scrolling; rows are virtualised (`ListView.builder`/`SliverList`).
- Haptics: none (MOT-11: nothing on local choices or selection).

## Data safety and honest state

- **Read-only is the default.** Selection, Comment and Add to prompt appear only when the host passes a handler.
- **Redaction.** Copy lines, Copy file and Copy patch go through `KitCopy` and `KitRedact` (G12). The displayed diff is not masked (a change the agent made is shown as it is; SEC-2 covers logs, reports and the clipboard, and redacting a diff line would hide what is being reviewed). **Flag:** if the coordinator reads G12's "rendered text" as covering diffs, the builder adds display redaction; see Open questions.
- **Honest counts.** The header's counts come from `KitDiffFile.added`/`removed`, and a partial patch says "Showing 400 of 3,200 lines". A gap without its lines says it cannot expand.
- **Nothing is applied here.** Reverting a file or hunk is the host's act (DATA-11: confirm first), not a kit button.

## Depends on

- **From C25:** kit-KitChoiceList (tier 1c: `KitPickerRow`, `showKitChoiceSheet` for the file picker), kit-KitIconButton-v2 (tier 1a: navigator, Wrap, More, Clear), kit-KitAction-v2 (tier 1a: the selection bar's `KitActionBlock` with `disabledReason`).
- **Edges added** (README.md), both tier 1a, so the unit stays in tier 1d: kit-KitMenu (`fileActions` More menu and the right-click menu) and kit-KitPageRoute (`showKitDiff` and the wrapper's `DiffView.open`).
- **Existing kit parts:** `KitStateView` (inline empty and error; v1 API suffices), `KitSkeletonRows`, `KitReveal`, `KitButton`, `KitLayout`.
- **Pre-wave seams:** `KitTokens.hairlineWidth`, `KitBidi`, `KitCopy`, `KitRedact`.
- **Depended on by:** kit-KitRequestSheet and kit-KitToolRow (C25), and in waves 2–3 screen-review-1, shared-review-1, slice-P3.7a.

## Tests required

In `test/kit/kit_diff_view_test.dart` (and the wrapper's own `test/diff_view_test.dart`, unchanged):

1. Parser: `KitDiffFile.fromPatch` on a two-hunk patch gives the right added/removed counts, numbers and hunk lines; `fromTexts` folds unchanged runs into gaps with 3 context lines around each change; `fromTexts(after: …)` alone is status added.
2. Gaps: tapping a gap bar reveals 20 lines per tap and collapse hides them again; a patch gap (no lines) shows its count and no expand action.
3. Mode: `mode: null` renders unified in a 412 dp box and split in a 1280 dp box; `mode: split` in a 412 dp box renders unified; a KitDiffView in a 340 dp box on a 1600 dp window renders unified.
4. File list: 3 files in a 1600 dp box show the file list column; in 412 dp the picker row opens a sheet listing the 3 files with counts, and choosing one scrolls to it.
5. Navigator: with 2 files × 3 changes, "Change 1 of 6"; next five times reaches the second file's last change; `n`/`p` do the same with desktop capabilities; focus moves to the change and "Change 2 of 6" is announced once.
6. Not colour alone: an added line's semantics read "Line 12 added: …" and it shows a `+` glyph; a removed line "Line 9 removed: …" and `−`.
7. Selection (`readOnly: false`, `onComment` set): dragging line numbers 12–14 on the new side shows "3 lines selected" and the bar; Comment calls `onComment` once with `startLine: 12`, `endLine: 14`, side current and the redacted text; Esc clears. With `readOnly: true` no selection is possible.
8. Copy lines copies the selection through `KitCopy`, announces "Copied" once and shows no `SnackBar`; a fake `sk-ant-FAKE…` in the copied lines is redacted (G12).
9. States: `loading` shows skeleton lines; empty files show "No changes"; `error` with `onRetry` shows the error and Try again, which calls `onRetry` once; a binary file shows "Binary file · not shown"; a renamed file shows "Renamed from lib/old.dart".
10. Too big: `maxLines: 400` on a 3,200-line diff shows "Showing 400 of 3,200 lines" and "Open all" calls `onOpenAll`.
11. Wrap: a long line wraps with a hanging indent at 412 dp and scrolls sideways at 1280 dp; `onWrapChanged` receives the toggle.
12. Wrapper compatibility: `DiffView.single(FileDiff(...))` still shows `diff-file-header-<path>`, `diff-gap-0` and `diff-view-horizontal`, and its copy no longer shows a `SnackBar`.
13. RTL: under Arabic the lines are LTR and the old side is on the left in split.
14. Virtualisation: a 10,000-line file builds only visible rows.
15. Reduced motion (G8): gap expansion and change navigation settle after one `pump()`.
16. Overflow (G6): a 150-character path and a 400-character line at 320, 412, 600, 840 and 1280 dp × text 1.0/1.3/2.0 × LTR/RTL: no overflow.

## Galleries required

`test/goldens/kit/kit_diff_view_golden_test.dart`, DPR 3.0, Android, deterministic fixture files, names per TEST-20.

- **Each state at 412×915, dark and light:** `unified` (2 files, a gap, 3 changes), `selecting` (the selection bar), `binary_renamed` (both file kinds), `too_big`, `loading`, `empty`, `error`. 7 × 2 = 14 PNGs.
- **Default at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000, dark and light:** 10 PNGs (unified up to 800, split at 1280, split with the file list at 1600).
- **Default at text 2.0 and in Arabic RTL, at 412×915 and 1280×800, dark and light:** 8 PNGs.
- **Total:** 32 PNGs.

## Non-goals

- No syntax colour inside diff lines (no KitCodeBlock edge; a later part may add it).
- No word-level (intra-line) highlighting in v2.
- No reverting, staging or applying changes; no inline review comments (the review workspace hands comments to the prompt).
- No mode control inside the part (the host's `KitSegmented` sets `mode`).
- No screen adoption; review_workspace, run results and request previews move in their own units.

## Open questions

1. **Does G12 ("never appear in rendered text") cover diff lines? (owner: a privacy decision)** This freeze redacts the diff's clipboard output but shows diff lines unmasked, because masking a changed line hides what the person is reviewing and could make them approve a change they cannot see. SEC-2 lists the clipboard but not the screen. If the owner or coordinator decides G12 covers diffs, display redaction is a one-line change in the line builder, and the gallery adds a masked-line case. Until they answer, the rule above applies.

## Inline and the page header (2026-10-14)

- `inline: true` (the conversation's step, which already says "Edit checkout_test.dart +3 −1"): one file shows **no file header**; the lines and the fold rows have **no fill** (transparent over the page). Several files keep a header so each is named. The wrap toggle is the host's: it passes `wrap` and puts "Wrap lines" on the step's long-press menu. Diff line colours are unchanged.
- The pinned file header (the full diff page, the review screen) is the page's ground with a hairline under it, not a `surface1` card.

## Markdown drawn as Markdown (2026-10-27)

A file ending in `.md`, `.markdown` or `.mdx` shows its changes formatted by
default, everywhere the part is used (a chat step's preview and its "Open
all" page, the permission sheet, the review workspace). Each run of added,
removed or unchanged lines is cut into blocks (a heading, a paragraph, one
list item with its wrapped lines, a quote, a table, a blank line) and each
block is drawn with `KitMarkdown` (secondary size, primary tone, links only
through `openExternalLink`). The gutter keeps the block's first line
numbers and its `+` / `−`, the row keeps its tint, and a screen reader hears
"Lines 6–8 added: …". Code fences (and a fence cut by the change) stay
written lines. Drawn Markdown is one column and always wraps; "Source" (in
the header, or beside "Showing N of M lines" when inline) shows the written
lines with the usual split and wrap, "Formatted" draws them again, the new
form fading in over `KitMotion.quick`. The preview's cap still counts
written lines (a block across the cut keeps the lines before it), and
selecting a block's number selects all its lines, so Copy lines copies what
is written. Other files are unchanged.
