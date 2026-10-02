# KitCodeBlock — API freeze (wave 0)

> **Copy (SEC-13, coordinator 2026-09-27; corrected by kit-polish 2026-09-27 to match the code and G12):** this part redacts. Copy goes through `KitIconButton.copy` / `KitAction.copy` with the default `redact: true`, so what reaches the clipboard is the same masked text the block shows (`KitRedact.text(copyText ?? text)`). A block is often tool output or a command, where a pasted key would leak; the person's own prose (messages, markdown) copies verbatim through its own part.

Unit: `kit-KitCodeBlock` (wave 1, tier 1b, kind `kit-part`). Write set (work-units.json): `lib/ui/kit/kit_code_block.dart` (new), `lib/ui/widgets/code_highlight.dart` (moves into the kit, C24), `test/kit/kit_code_block_test.dart`, `test/goldens/kit/kit_code_block_golden_test.dart`. Spec: kit-v2.md §1.9, §4.10, §8.2 (`KitCodeBlock` row); VL §5 ("code blocks have a file header with `+n −n` and a copy button"), §3 (code roles); C24, C25. Rules: KIT-23, KIT-32, LOOK-16, LOOK-26, COPY-11, COPY-30, SEC-2, SEC-4, PERF-2, MOT-5, LAY-8, A11Y-8.

## Purpose

A block of code, a command the person copies to run elsewhere, or tool output: mono, left-to-right, capped so it never floods a scrolling list, with one header that carries its name, its `+n −n` and the one Copy. `KitCodeBlock.fill` is the same block filling its host, virtualised, for readers and viewers.

## Replaces

- **Map elements (kit-v2.json `assignment` → `KitCodeBlock`): 14 elements on 14 pages.**
  - connection-help (connection-help-examples);
  - continue-on-computer-sheet (continue-on-computer-command);
  - embedded-markdown-text (embedded-markdown-text-code);
  - embedded-tool-card (embedded-tool-card-output: uncapped tool output on the chat list, a perf finding);
  - guide (guide-pair-command);
  - host-management (host-management-command);
  - markdown-code-reader (markdown-code-reader-body: a bordered card instead of edge-to-edge code);
  - permission-sheet (permission-sheet-command-preview);
  - profile-editor (profile-editor-command: a Codex command cut at the edge);
  - server-settings-restart-dialog;
  - session-handoff-dialog;
  - team-host-guide-sheet (guide-body);
  - team-phone-tips-sheet (tips-commands);
  - tools-detail-sheet (schema).
- **Merged proposal names:** `KitCodeBlock`, `KitCommandBlock`, `KitCopyValue`.
- **Moved by this unit (C24, R12):** `lib/ui/widgets/code_highlight.dart` (157 lines, not in the G16 baseline). Its grammar table, size limit and parse cache move to `KitCodeHighlight` in `kit_code_block.dart`. The old file keeps `CodeHighlightTheme` and `highlightedCode` as forwarding wrappers (KIT-43: no `@Deprecated`; `/// Retired by kit-KitCodeBlock: use KitCodeHighlight`, plus a G2 ratchet pattern `highlightedCode(`). Callers: `widgets/markdown.dart` (1 call each; moves with kit-KitMarkdown) and `screens/chat/permission_sheet.dart` (2 each; chat-5).
- **Code reach, adopted by other units:**
  - `CodeBlock` in `widgets/markdown.dart:1079` (originalSource, language, highlightEnabled, initialWrap, canExpand; its full-screen reader at `:1186` is the markdown-code-reader page) → kit-KitMarkdown and kit-KitViewer;
  - `AgentCommandBlock` (`widgets/agent_blocks.dart:234`; 3 calls; its own `Clipboard.setData` + `SnackBar`) → a wrapper over `KitCodeBlock(kind: command)` in shared-shell-1;
  - tool card output (`widgets/tool_card.dart`, `SelectableText` 3) → kit-KitToolRow;
  - the command cards in `session_handoff_sheets.dart`, `host_management_screen.dart` (`SelectableText` 2), `servers_screen.dart`, `team_host_form.dart`, `team_phone_section.dart`, `tools_screen.dart` (`SelectableText` 2), `guide_screen.dart`, `settings/server_settings_screen.dart` → their wave-2 units.
- **Widgets retired directly:** none outside the kit.

## File

- `lib/ui/kit/kit_code_block.dart`: `KitCodeKind`, `KitCodeBlock`, `KitCodeHighlight`.
- `lib/ui/widgets/code_highlight.dart`: forwarding wrapper.
- Tests: `test/kit/kit_code_block_test.dart`; the existing `test/code_highlight_test.dart` keeps passing against the wrapper.
- Gallery: `test/goldens/kit/kit_code_block_golden_test.dart`.

## Public API

```dart
enum KitCodeKind {
  /// Source code: syntax colour from [KitCodeBlock.language].
  code,

  /// A command the person runs elsewhere. A `$` prompt is drawn before each
  /// line, outside the copied text. Never wraps (it scrolls sideways with
  /// an edge fade), so a copied command is never broken by the display.
  command,

  /// Output of a tool or process. No syntax colour.
  output,
}

/// A block of code, a command or output (K2 §1.9). Always LTR, mono,
/// selectable, redacted.
///
/// States: default, capped ("Show all N lines"), wrapped, scrolling,
/// copied, empty.
class KitCodeBlock extends StatefulWidget {
  const KitCodeBlock({
    super.key,
    required this.text,
    this.kind = KitCodeKind.code,
    this.language,          // syntax hint only ("dart", "sh"); unknown = plain
    this.caption,           // header words: "Run this on your computer"
    this.fileName,          // header: "lib/main.dart" (LTR, middle ellipsis)
    this.added,             // header "+n" (success)
    this.removed,           // header "−n" (codeRemoved)
    this.maxLines = 12,     // null = no cap
    this.onOpenFull,        // with a cap: "Open full output" calls this (a KitViewer)
                            //   instead of unfolding in place
    this.wrap,              // null = the kind's default (below)
    this.onWrapChanged,     // null = the block keeps its own wrap state
    this.showWrapToggle = true, // the header's Wrap toggle (never for command)
    this.copyText,          // what Copy copies when it differs from [text]
                            //   (the fence's exact source); never the "$" prompt
    this.copyLabel,         // null = by kind: "Copy code" / "Copy command" / "Copy output"
    this.copyable = true,   // false only inside a host whose header has Copy (KitViewer)
    this.highlight = true,  // false while the text still streams in
    this.lineNumbers = false,
    this.blockKey,
    this.copyKey,
    this.showAllKey,
  }) : fill = false, marks = const [], activeMark = null, initialLine = null;

  /// The same block filling its host: no cap, no outer radius, virtualised
  /// by line (ListView.builder), for KitViewer and full readers. [marks]
  /// highlight find matches; [activeMark] is scrolled into view;
  /// [initialLine] (1-based) is scrolled to and marked on first build.
  const KitCodeBlock.fill({
    super.key,
    required this.text,
    this.kind = KitCodeKind.code,
    this.language,
    this.wrap,
    this.onWrapChanged,
    this.copyText,
    this.highlight = true,
    this.lineNumbers = true,
    this.marks = const [],        // List<TextRange> into [text]
    this.activeMark,              // index into [marks]
    this.initialLine,
    this.blockKey,
  }) : fill = true, caption = null, fileName = null, added = null,
       removed = null, maxLines = null, onOpenFull = null,
       showWrapToggle = false, copyLabel = null, copyable = false,
       copyKey = null, showAllKey = null;

  final String text;
  final KitCodeKind kind;
  final String? language, caption, fileName, copyText, copyLabel;
  final int? added, removed, maxLines, activeMark, initialLine;
  final VoidCallback? onOpenFull;
  final bool? wrap;
  final ValueChanged<bool>? onWrapChanged;
  final bool showWrapToggle, copyable, highlight, lineNumbers, fill;
  final List<TextRange> marks;
  final Key? blockKey, copyKey, showAllKey;

  /// The wrap a block uses when [wrap] is null: command and code scroll
  /// sideways on every window (slice-polish2: a long line is never broken
  /// mid-identifier); output wraps on a compact window and scrolls sideways
  /// from medium up. The Wrap toggle wraps any of them.
  static bool defaultWrap(BuildContext context, KitCodeKind kind);
}

/// Syntax colour, moved from widgets/code_highlight.dart. Colours come from
/// ThemeRoles' code roles; parse results are cached (48 entries) and a
/// source over [sizeLimit] characters is left plain.
abstract final class KitCodeHighlight {
  static const int sizeLimit = 20000;
  static bool supports(String? language);
  static TextSpan spans(String source, String? language, ThemeRoles roles);
}
```

- **Header.** Shown when there is a `caption`, a `fileName`, counts, a wrap toggle or Copy. Start: `fileName` (mono, LTR, middle ellipsis on one line, full value in semantics and tooltip, A11Y-8) or `caption` (`secondary`). Then `+added` in `success` and `−removed` in `codeRemoved` (words in semantics: "4 added, 1 removed"). End: the Wrap toggle (`KitIconButton(selected: wrap)`, not for `command`) and one `KitIconButton.copy`. There is never a second copy or a ⋯ inside the block (K2 §1.9).
- **Cap.** With `maxLines` and more lines than that, the block shows `maxLines` lines and a tertiary action "Show all {count} lines" (unfolds in place) or, when `onOpenFull` is set, "Open full output" (PERF-2). Lines are counted after trailing-newline trim.
- **Compatibility.** A new part (no existing public API). `CodeHighlightTheme.of(context)` and `highlightedCode(source, language, theme)` keep their signatures in the wrapper; their colours now come from `ThemeRoles` (a look change, not a behaviour change).
- **Internal keys (TEST-5):** `kit-code-block`, `kit-code-copy`, `kit-code-wrap`, `kit-code-show-all`, `kit-code-open-full`, `kit-code-horizontal` (the sideways scroller), `kit-code-line-<n>` (fill only).
- **Kit copy (ARB, `kit` prefix, en + ar):** `kitCodeCopyCode` "Copy code", `kitCodeCopyCommand` "Copy command", `kitCodeCopyOutput` "Copy output", `kitCodeShowAll` "{count, plural, other{Show all {count} lines}}", `kitCodeOpenFull` "Open full output", `kitWrapLines` "Wrap lines" (shared with KitLogPanel, KitViewer, KitDiffView), `kitCodeChanges` "{added} added, {removed} removed", `kitCodeEmpty` "Empty".

## States

| State | Look |
|---|---|
| default | the header (if any), then the lines on the code surface |
| capped | `maxLines` lines, a 1 px hairline, "Show all N lines" or "Open full output" |
| wrapped | lines wrap at spaces and, inside a long token, after punctuation (`kitCodeBreakable`: after `. , ; : ( [ { = & \| /`, never inside a run such as `::` or before a closing bracket); only a token with no such mark breaks where it must. The break chances are display-only zero-width spaces: Copy uses the source and a selection copies without them. Continuation lines are indented by the line-number gutter |
| scrolling | one horizontal scroller for the whole block (not per line), a visible scrollbar on a fine pointer, and a `space6` edge fade at the end edge while more is off-screen; nothing is clipped |
| copied | the copy glyph is a check for `KitMotion.copiedHold` (KitIconButton.copy) |
| empty | a single `text3` line "Empty" (`kitCodeEmpty`); Copy is disabled |

Loading, error and disabled are the host's (a code block shows text it was given). KIT-12 doc comment: "States: default, capped, wrapped, scrolling, copied, empty".

## Tokens

- **ThemeRoles:** `text1` (plain code), `codeKeyword`, `codeString`, `codeType` (titles, numbers, meta), `text3` (comments, line numbers, the `$` prompt), `success` (`+n`, `addition` spans), `codeRemoved` (`−n`, `deletion` spans; VL §3 defines removed as `#FF7A7A`, the `danger` hue, as a code role), `text2` (caption), `hairline` (header rule, cap rule).
- **KitText:** `mono` (13/19) for code, numbers and the file name; `secondary` for the caption; `button` via `KitButton` for "Show all".
- **KitTokens (VL branch):** `detailsSurface` (the code surface: `surface1` in dark, `ground` in light, one step off a sheet's `surface2`), `codeRadius` (14; 0 for `.fill`), `space2`/`space3`/`space6` (padding and the edge fade), `minTarget`.
- **Pre-wave seams:** `KitTokens.hairlineWidth(context)`, `KitCopy.copy`, `KitBidi.ltr`, `KitRedact`, `KitMotion.copiedHold`.
- **New glyph (pre-wave, `_new-tokens.md`):** `AppIconography.wrapText` (Phosphor "text-align-justify"/wrap) with an `AppIcons.wrap` entry (a PROC-13 registry append). It replaces `Icons.wrap_text_rounded` in `ReaderWrapButton`, and KitLogPanel, KitViewer and KitDiffView reuse it.
- No other new token: the gutter width is measured from the mono style and the largest line number.

## Adaptive

| Window | Behaviour |
|---|---|
| compact | full width of its host's rails; output wraps by default; code and command scroll sideways (code with the Wrap toggle) |
| medium | code and output scroll sideways by default, with the Wrap toggle |
| expanded / large | the same, plus mouse text selection across lines and an always-visible horizontal scrollbar where the block scrolls (the desktop rule, `KitLayout.finePointer`) |

- **Keyboard:** the header's Wrap and Copy are in Tab order; "Show all" follows the lines. Shift+scroll and the arrow keys scroll the horizontal scroller once it has focus (it is focusable only when it can scroll).
- **Pointer:** hover shows the Wrap and Copy tooltips (the label again, LAY-11).
- **Short windows** (below 480 dp tall): the cap stays `maxLines`; the host's page scrolls.

## Accessibility

- The code is one selectable text node per block (per line in `.fill`), read as is. The kind is announced as the node's hint: "Code", "Command", "Output".
- Copy is labelled by kind ("Copy command", A11Y-1); Wrap is a toggle ("Wrap lines", `toggled`).
- The horizontal scroller has a visible scrollbar and semantic scroll actions (K2 §1.9).
- At 200 % text the mono size follows the text scale (no clamp); the header wraps to two lines; the file name keeps its full value in semantics.
- Contrast: every code role reads ≥ 4.5:1 on the code surface in each pack (LOOK-8); comments in `text3` meet it by the role's own floor.

## RTL

- The block is always an LTR box, aligned left in both directions (COPY-30, LAY-8), isolated from the surrounding text. The line-number gutter stays on the left.
- The header follows the locale for the caption; the file name is LTR. Copy and Wrap sit at the end (the right in LTR, the left in RTL).
- `$`, `+n` and `−n` do not mirror.

## Motion and haptics

- "Show all" unfolds with `KitReveal` (`KitMotion.standard`), never `AnimatedSize`, and never on a scrolling list item's layout (MOT-5): in a list, the host rebuilds the item at its new height.
- The Wrap toggle swaps layout at once (no animation). The copy check cross-fades through KitIconButton.
- Reduced motion: instant (MOT-7). Haptics: none (MOT-11).

## Data safety and honest state

- **Redaction (G12, SEC-2).** Displayed and copied text passes through `KitRedact.text` (`copyText ?? text`). A fake provider key in any kind never reaches the screen, the clipboard or a golden.
- **Commands (SEC-4).** A `command` block asserts in debug that `KitRedact.containsSecret(text)` is false: commands carry placeholders (`<your key>`), never a real token. kit-gates-manifest's G12 test feeds fake keys through every caller that builds command strings.
- **Exact copy.** Copy copies `copyText ?? text` exactly apart from redaction (no `$`, no line numbers, no display normalisation), so a pasted command runs as shown; a secret in it is masked in the copy as on screen (SEC-13, G12).
- **Honest cap.** A capped block always says how many lines exist; it never silently truncates.

## Depends on

- **kit-KitIconButton-v2** (tier 1a): `KitIconButton.copy` and the `selected` Wrap toggle (C25).
- **Pre-wave seams:** `KitCopy`, `KitBidi`, `KitRedact` (defined in KitDetailsFold.md), `KitTokens.hairlineWidth`, `KitMotion.copiedHold`.
- **Existing kit parts:** `KitReveal`, `KitButton` (tertiary "Show all"), `KitText`, `KitTokens`, `ThemeRoles`.
- **Package:** `highlight` (already a dependency).
- **Depended on by (C25):** kit-KitMarkdown, kit-KitViewer, kit-KitToolRow; in wave 2 shared-shell-1 (`AgentCommandBlock`), shared-phone-1 and the command-card screens.

## Tests required

In `test/kit/kit_code_block_test.dart`:

1. Copy: `KitCodeBlock(text: 'ls -la', kind: command)` copies `ls -la` (without `$`) through `KitCopy`, announces "Copied" once and shows no `SnackBar`. `copyText` wins over `text` when given.
2. Copy label by kind: "Copy code", "Copy command", "Copy output"; `copyLabel` overrides.
3. Cap: 40 lines with `maxLines: 12` show 12 lines and "Show all 40 lines"; tapping it shows 40 in place. With `onOpenFull`, the action is "Open full output" and calls it once, without unfolding.
4. Wrap: on a 360 dp window `code` wraps (no horizontal scroller); on 1280 dp it scrolls (`kit-code-horizontal` present). `command` never wraps. The toggle flips it; with `onWrapChanged` it calls back and waits for the host.
5. Header: `fileName: 'lib/main.dart', added: 4, removed: 1` shows the name and "+4 −1"; semantics read "4 added, 1 removed".
6. Highlight: `language: 'dart'` paints `codeKeyword` on `class`; an unknown language and text over `sizeLimit` render plain; `highlight: false` renders plain. `test/code_highlight_test.dart` passes against the wrapper.
7. Redaction (G12): `output` containing `sk-ant-FAKE…` renders and copies without it. A `command` containing a fake key trips the debug assert.
8. RTL: under `TextDirection.rtl` the block's `Directionality` is LTR and its text is left-aligned.
9. `.fill`: 5,000 lines build only the visible lines (virtualised); `initialLine: 2000` scrolls line 2000 into view; `marks` paint on the matched ranges and `activeMark` scrolls to its line.
10. Empty text shows "Empty" and a disabled Copy.
11. Reduced motion (G8): "Show all" settles after one `pump()`.
12. Semantics: the scroller exposes scroll actions when it can scroll; Wrap has `toggled`.
13. Overflow (G6): a 300-character command and a 200-character file name at 320 and 412 dp × text 1.0/1.3/2.0 × LTR/RTL: no overflow.

## Galleries required

`test/goldens/kit/kit_code_block_golden_test.dart`, DPR 3.0, Android, on `ground` and inside a sheet body, names per TEST-20.

- **Each state at 412×915, dark and light:** `code_header` (Dart, file name, +4 −1), `command` (a long curl scrolling, edge fade visible), `output_capped` (40 lines, "Show all"), `wrapped`, `copied`, `fill_find` (`.fill` with line numbers and two marks), `empty`. 7 × 2 = 14 PNGs.
- **`code_header` at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000, dark and light:** 10 PNGs.
- **`code_header` and `command` at text 2.0 and in Arabic RTL, at 412×915 and 1280×800, dark and light:** 16 PNGs.
- **Total:** 40 PNGs.

## Non-goals

- No editing, running or sending of the code.
- No language guessing: an unknown hint renders plain (today's rule).
- No diff rendering (that is KitDiffView; `+n −n` in the header is a count only).
- No markdown parsing (KitMarkdown dispatches fences to this part).
- No screen adoption; `AgentCommandBlock`, markdown and tool output move in their own units.

## Open questions

None.

## Light frame and control gap (2 Oct)

In light, `detailsSurface` is the page ground, so a block had no edge: it now draws a 1 px hairline (`text1` at 16 %) around its shape (dark unchanged). The Copy and Wrap column sits `space3` clear of the text, never over it.
