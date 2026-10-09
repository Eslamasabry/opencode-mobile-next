# KitMarkdown — frozen API (wave 0, 2026-09-26)

> **Copy (SEC-13, coordinator 2026-09-27):** this part copies verbatim: `KitCopy.copy(context, text, redact: false)`. Code, diffs and messages are the person's own content.

Group: chat. Unit `kit-KitMarkdown` (wave 1, tier 1c; after kit-KitCodeBlock, C25, plus kit-KitText-v2, README.md). In wave 2c the file `lib/ui/kit/chat/kit_markdown.dart` joins the write set of chain link chat-1 (C03 as corrected, C38). Spec: kit-v2.md §9.2 (chat parts), §5 (chat transcript group), cut review C24 with its verdict correction (the kit takes a block builder and a highlighter, so it imports neither `agent_blocks.dart` nor `transcript_highlight.dart`), C42 correction (```` ```choices``` ```` is AgentChoicesBlock in shared-shell-1, not a kit concern). Rules: KIT-41, STATE-16, SEC-1, KIT-32, LOOK-16, COPY-2, COPY-30, PERF-2, KIT-43, R12.

## Purpose

Renders the Markdown an agent writes (headings, emphasis, inline code, fenced code, lists, quotes, rules, links, tables) as crisp, selectable kit text. It is the prose of a reply, a thought, a tool's note and a document preview, parsed once per change and cheap to rebuild while a reply streams.

## Replaces

- **Map** (kit-v2.json `assignment`, module:chat transcript): `embedded-markdown-text#embedded-markdown-text-link`, `embedded-markdown-text#embedded-markdown-text-table` (2 elements). The same page's `embedded-markdown-text-code` is KitCodeBlock's (rendered here through it); `embedded-markdown-text-agent-choice` is KitChoiceList inside KitRequestCard (shared-shell-1, C42), reached through `blockBuilder`. Map `statesMissing`: "very wide table" (handled below). Owner Fix: "table first-column sizing and code right-edge fade/wrap".
- **Code:** `lib/ui/widgets/markdown.dart` (1,422 lines) is in this unit's write set (C24, R12). `MarkdownText`'s parser, `_MarkdownTable`, `_Heading`, `_Quote`, `_List`, `_RichLines`, `_InlineParser`, `_PathCodeChip` and the inline `_codeSpan` move into `kit_markdown.dart`. What stays in `markdown.dart` is a thin forwarding layer (see Public API → Compatibility).
- **Ratchet** (`test/kit_ratchet_baseline.json`): `markdown.dart` G16 = AppBar 1, CheckedPopupMenuItem 1, ClipRRect 1, Container 3, DataTable 1, DecoratedBox 1, DefaultTextStyle 1, Directionality 1, Divider 1, ExcludeFocus 2, GestureDetector 1, Icon 4, IconButton 2, PopupMenuButton 1, PopupMenuItem 1, Scaffold 1, SelectableText 2, SingleChildScrollView 3, SnackBar 2, SnackBarAction 1, Text 15 (46); G1 = SnackBar 2, showSnackBar 2. This unit brings the file to zero: the parser moves into the kit, `CodeBlock` forwards to KitCodeBlock (its copy SnackBars become KitCopy), and the full-screen code reader in the same file becomes `KitScreen` + `KitCodeBlock.fill`.
- Callers that keep compiling unchanged through the wrapper: `message_view.dart`, `tool_card.dart`, `agent_blocks.dart`, `file_preview.dart`, `delimited_file_preview.dart`, `svg_file_preview.dart`, `team/gate_sheet.dart`, `team/work_sheet.dart`, `about_screen.dart`, `desktop/desktop_interaction.dart`.

## File

`lib/ui/kit/chat/kit_markdown.dart` (new). `lib/ui/widgets/markdown.dart` becomes the forwarding layer.

## Public API

```dart
/// Builds a fenced block whose info string the host claims (```choices```,
/// ```checklist```, ```command```: AgentBlockKinds). Returns null to let
/// KitMarkdown render the fence as a KitCodeBlock. Called once per parse
/// of a closed fence, never per streamed token.
typedef KitMarkdownBlockBuilder =
    Widget? Function(BuildContext context, String info, String body);

/// Decorates a finished inline span (the find-in-conversation highlight).
/// [source] is the raw Markdown the span was parsed from. It must return a
/// span with the same plain text, so selection, links and copy are
/// unchanged.
typedef KitMarkdownHighlighter =
    TextSpan Function(BuildContext context, TextSpan span, {String? source});

/// How inline code that looks like a server path becomes a link. The host
/// memoises [validate]; a path renders as plain mono until it resolves true.
@immutable
class KitMarkdownFileLinks {
  const KitMarkdownFileLinks({required this.validate, required this.open});
  final Future<bool> Function(String path) validate;
  final void Function(String path) open;
}

/// Agent Markdown in kit text. Chat parts follow the transcript turn model
/// (STANDARDS STATE-16, KIT-41): this part is the prose of a reply or a
/// thought; it never draws a turn's controls.
///
/// States: default, streaming (an unclosed fence stays unhighlighted),
/// non-interactive, empty (renders nothing). No loading, error or disabled:
/// the text is given (KIT-12).
class KitMarkdown extends StatefulWidget {
  const KitMarkdown(
    this.data, {
    super.key,
    this.role = KitTextRole.body,   // body: replies, documents; secondary: a tool's note, a thought
    this.tone,                      // null: the role's own tone (KitText.defaultTone)
    this.selectable = true,         // false where long-press belongs to a menu (a prompt bubble)
    this.interactive = true,        // false: links and path chips draw as text (demo, isolated previews)
    this.blockBuilder,
    this.highlighter,
    this.fileLinks,
    this.codeWrap,                  // null: KitCodeBlock.defaultWrap for the window
    this.onCodeWrapChanged,         // the reader preference; null: each block keeps its own
    this.onOpenCode,                // non-null: capped code says "Open full output" and calls this
    this.codeLanguage,              // non-null: all of [data] is one code block in this language
  }) : assert(role == KitTextRole.body || role == KitTextRole.secondary,
           'KitMarkdown runs in body or secondary only');

  final String data;
  final KitTextRole role;
  final KitTextTone? tone;
  final bool selectable;
  final bool interactive;
  final KitMarkdownBlockBuilder? blockBuilder;
  final KitMarkdownHighlighter? highlighter;
  final KitMarkdownFileLinks? fileLinks;
  final bool? codeWrap;
  final ValueChanged<bool>? onCodeWrapChanged;
  final void Function(String code, String? language)? onOpenCode;
  final String? codeLanguage;

  /// Conservative path test, moved from markdown.dart: multi-segment, no
  /// spaces, no scheme, anchored (/, ~/, ./) or ending in an extension.
  static bool looksLikeFilePath(String code);

  /// `lib/a.dart:120` -> `lib/a.dart`.
  static String stripPathLineSuffix(String code);

  /// The prose a reader would speak: fences, tables and link targets
  /// dropped (read-aloud and voice replies). Moved from markdown.dart.
  static String proseForSpeech(String source);

  /// Counts full block re-parses (tests assert streaming does not re-parse
  /// unchanged Markdown).
  @visibleForTesting
  static int debugParseCount = 0;
}
```

**Frozen behaviour.**

- **Parse once.** Blocks are cached by source; while `data` only grows at its tail, every earlier block is reused as the same widget instance (today's `_blocksBySource`), so a delta rebuilds only the last block. `debugParseCount` counts full parses exactly as today.
- **Links.** A Markdown link or bare URL is `accent` text with an underline (so it is never colour alone) and opens only through `openExternalLink` (`lib/ui/widgets/external_link.dart`, SEC-1). There is no link-callback parameter, so no caller can route a link around it. With `interactive: false` links are plain text.
- **Path chips.** Inline code that `looksLikeFilePath` stays plain `mono` until `fileLinks.validate` resolves true, then becomes an `accent` underlined mono link that calls `fileLinks.open`. The path is isolated LTR (KIT-32).
- **Fences.** A closed fence whose info string `blockBuilder` claims renders the builder's widget; anything else is a `KitCodeBlock(kind: code, language: info, maxLines: 12, highlight: closed, copyText: <exact fence source>, wrap: codeWrap, onWrapChanged: onCodeWrapChanged, onOpenFull: onOpenCode == null ? null : () => onOpenCode!(body, info))`. An unclosed fence (streaming) is a KitCodeBlock with `highlight: false`.
- **Tables.** A table that fits is laid out at its natural column widths with the first column never narrower than its longest word; one that does not fit scrolls sideways inside its own box (never the page), with a visible scrollbar on a fine pointer (`KitScrollbar` once kit-KitScrollbar merges, KIT-6). Cells wrap at word boundaries only; mono cells never break mid-token. Rows are separated by hairlines.
- **Type.** `#`/`##` → `headline`; `###`–`######` → `rowTitle`; paragraphs, lists and quotes → `role`; inline code and fences → `mono`. No other size (LOOK-12). Headings carry header semantics.
- **Quotes.** A one-physical-pixel `hairline` stroke at the start edge, text in `text2`.
- **Search.** `highlighter` is applied to every inline span after parsing, with no re-parse.

**Compatibility (KIT-43, R11, R12).** `lib/ui/widgets/markdown.dart` keeps every public name with its signature. No `@Deprecated` (KIT-43 overrides the work-units acceptance line "a @Deprecated wrapper"). Each old name gets a `/// Retired by kit-KitMarkdown: use KitMarkdown` note and a G2 pattern:

- `MarkdownText(data, {baseStyle, codeBlockLanguage, selectable, onChoice})` builds a `KitMarkdown`. `baseStyle` non-null maps to `role: secondary, tone: secondary`. `codeBlockLanguage` maps to `codeLanguage`. `onChoice` and the three agent blocks are passed as a `blockBuilder` that builds `AgentChoicesBlock`/`AgentChecklistBlock`/`AgentCommandBlock` as today (the wrapper, not the kit, imports `agent_blocks.dart`). `TranscriptHighlight.decorate` is passed as the `highlighter`. `MarkdownInteractionScope` and `MarkdownFileLinks` stay in `markdown.dart` as they are; the wrapper reads them and passes `interactive` and `fileLinks`. `ReaderPreferencesScope`'s `wrapCode` is passed as `codeWrap`/`onCodeWrapChanged`. The wrapper passes an `onOpenCode` that pushes the reader page.
- `MarkdownText.debugParseCount` becomes a static getter/setter over `KitMarkdown.debugParseCount`.
- `looksLikeFilePath`, `stripPathLineSuffix` and `markdownProseForSpeech` forward to the statics.
- `CodeBlock({code, originalSource, language, highlightEnabled, initialWrap, canExpand})` builds `KitCodeBlock(text: code, copyText: originalSource, language, highlight: highlightEnabled, wrap: initialWrap, maxLines: canExpand ? 12 : null)`.

**Kit copy** (ARB, `kit` prefix, en + ar in the same change, COPY-1, COPY-3): `kitMarkdownOpenLink` "Open link" (link semantics hint), `kitMarkdownOpenFile` "Open file" (path chip hint), `kitMarkdownTable` "Table, {rows} rows" (ICU plural; table semantics). Code block words are KitCodeBlock's.

## Pictures

`![alt](target)` never shows as syntax (`lib/ui/kit/chat/kit_markdown_image.dart`, a part of the same library). The scanner takes each picture out of its line (nested brackets in the description, `<angle>` and titled destinations, `[![alt](src)](href)`; inline code is left alone) and each one is drawn as a kit part by what it points at:

| Target | Look | Tap |
| --- | --- | --- |
| A path or `file:` URI on the server or phone, confirmed by `fileLinks.validate` | A 96 dp thumbnail (`KitImage`, cover, tile corners) with its name under it, when `fileLinks.readImage` returns the bytes and Flutter can draw the type. Otherwise a `KitChip` (picture glyph, "Image · name") | `fileLinks.open(path)`: the file viewer the path links use |
| An http(s) address | `KitChip`: "Image · host". Never loaded: no `Image.network`, no fetch | `openExternalLink` (SEC-1): it names the host and asks first |
| `data:`, another scheme, or a path the host cannot confirm | `KitChip`, plain, with the description | none (no dead affordance) |

- The description is the alt text; empty or the bare word "Image" gets the app's own word (`kitMarkdownImage`). A file's name or a host follows it, isolated left to right (COPY-30).
- A line of only pictures, with the picture-only lines after it (blank lines between them too), is one wrapping group, not one per line. A picture inside a sentence is a link-like run (glyph, words, accent and underlined when it opens) that wraps with the words; it has no thumbnail.
- The last line of a streaming reply holds back a picture that is still being written, so syntax never flashes.
- Selection reads the words on the tiles. `proseForSpeech` speaks the alt text (nothing for an empty one), never the target. The reply's own Copy stays verbatim Markdown (a reply is the person's to paste).
- `KitMarkdownFileLinks.readImage` (host-supplied, null: chips only) is the same file transport FC4's sent-photo thumbnails use; the chat caps it at 8 MiB.
- Copy (ARB, en + ar): `kitMarkdownImage` "Image"; a thumbnail's semantics reuse `kitAttachmentOpen` ("Preview {label}").

## States

Declared (KIT-12): **default**; **streaming** (the last fence is unclosed: plain mono, no highlight; nothing flickers as it closes); **non-interactive** (`interactive: false`); **empty** (`data` blank: renders `SizedBox.shrink`, no semantics node). No loading or error (the text is given), no disabled, no working.

## Tokens

- ThemeRoles: `text1`, `text2`, `text3` (via KitText tones), `accent` (links, path links; LOOK-6 "link text"), `hairline` (quote stroke, table rules, horizontal rule), `surface3` (inline code background), `surface1` (table header band). Code colours are KitCodeBlock's (`codeKeyword`, `codeString`, `codeType`).
- KitText roles: `headline`, `rowTitle`, `body`, `secondary`, `mono`.
- KitTokens: `space1` (inline code padding), `space2` (list item gap, table cell vertical padding), `space3` (block gap, table cell horizontal padding), `space4` (list indent), `hairlineWidth(context)` (§0.5 step 2 seam, `_new-tokens.md`).
- No new token. No radius, shadow, glass or blur.

## Adaptive

KitMarkdown lays out at the width its host gives it. The chat host caps the conversation at `KitLayout.paneDetailMaxWidth` (700, LAY-5; §0.5 step 2 seam).

- **compact:** code blocks wrap (`KitCodeBlock.defaultWrap`); wide tables scroll sideways in their box.
- **medium / expanded / large:** code blocks scroll sideways with an edge fade unless the reader preference wraps; the same table rule.
- **Fine pointer:** links and path chips show `SystemMouseCursors.click` and a hover underline; text selection by drag. In the transcript, the host passes `selectable: false` and wraps the conversation in `KitSelectable(mode: finePointer)`, so drag-selection works with a mouse and long-press on touch stays the turn's menu.
- **Keyboard:** links and path chips are focusable in reading order, Enter opens them, and the focus ring is `accent` at `focusRingWidth`. A horizontally scrolling table or code block is reachable by Tab and scrolls with the arrow keys.

## Accessibility

- Headings expose header semantics; links expose link semantics with the `kitMarkdownOpenLink` hint; path chips have the `kitMarkdownOpenFile` hint.
- Lists read their markers ("1.", "•") before each item; a table announces `kitMarkdownTable` and reads row by row.
- Not a live region: a streaming reply is never announced token by token (A11Y-3). The host's status line announces the turn's state.
- Link and chip targets are at least 48 dp tall (the tappable area extends beyond the glyphs; LAY-9).
- At 200 % text everything wraps; mono follows the text scale; tables scroll instead of overflowing; no clipped text at the LAY-4 widths.

## RTL

- Paragraphs follow the reading direction; each paragraph's direction is taken from its first strong character, so an English reply inside the Arabic app still reads LTR.
- Inline code, path chips and fenced blocks are isolated LTR (KitText.mono, KitCodeBlock) and aligned to the paragraph's start.
- List markers and quote strokes sit at the start edge (directional insets only, G7). Tables lay out columns in the reading direction; a table whose cells are all mono is forced LTR.

## Motion and haptics

- Nothing animates: blocks appear as parsed. A path chip turning into a link swaps colour without a transition.
- Code blocks' "Show all" is KitCodeBlock's (KitReveal, not in a scrolling list's layout).
- Reduced motion: nothing to reduce; one `pump()` settles (G8x).
- Haptics: none (MOT-11).

## Data safety and honest state

- Links open only through `openExternalLink` (SEC-1); a path opens only after `validate` confirmed the server can read it.
- Code, commands and output go through KitCodeBlock, which redacts (SEC-4, G12). Prose is shown as the agent wrote it (COPY-2); it is never rewritten or translated.
- Copy of a fence copies its exact source (`copyText`), never the display normalisation.
- An unclosed fence is shown as unhighlighted text, never as a finished block.

## Depends on

- **kit-KitCodeBlock** (C25): fences, code reader page (`KitCodeBlock.fill`).
- **kit-KitText-v2** (tier 1a; edge added, README.md): `KitText.selectableRich`, `KitText.mono`, `KitSelectable`. No tier change.
- Existing: `KitScreen` (the reader page in the wrapper), `KitMotion`, `ThemeRoles`, `KitTokens`, `openExternalLink`.
- Pre-wave seams (§0.5 step 2): `KitTokens.hairlineWidth`, `KitLayout.paneDetailMaxWidth`.

## Tests required

`test/kit/kit_markdown_test.dart`, plus the existing Markdown tests kept green through the wrapper (TEST-19; this unit's `tests` list):

1. Headings, emphasis, inline code, lists, quotes, rules and links render with the role table above (finders by text and by `KitText` role).
2. Streaming: growing `data` by appending keeps `debugParseCount` at one full parse per change, and earlier blocks are the same widget instances.
3. An unclosed fence renders a `KitCodeBlock` with `highlight: false`; closing it switches to `highlight: true`.
4. `blockBuilder` returning a widget replaces the fence; returning null renders a KitCodeBlock; the builder is called once per closed fence, not per token.
5. `highlighter` decorates spans, and the plain text (selection and copy) is unchanged.
6. A link tap calls `openExternalLink` (mocked launcher), never `launchUrl` directly; `interactive: false` makes it plain text.
7. A path chip renders plain until `validate` completes true, then taps call `open` once; a false result stays plain.
8. A 12-column table at 320 dp scrolls sideways inside its box with no overflow exception; the first column is never narrower than its longest word.
9. `codeLanguage` renders the whole data as one code block; `onOpenCode` makes capped blocks offer "Open full output".
10. Wrapper: `MarkdownText(..., onChoice:)` still renders AgentChoicesBlock and calls `onChoice`; `MarkdownText.debugParseCount` reads the kit counter; `CodeBlock(...)` builds a KitCodeBlock with the mapped arguments.
11. Semantics: header nodes for headings, link nodes for links; no live region anywhere.
12. RTL: an English paragraph under `TextDirection.rtl` lays out LTR; inline code is LTR-isolated inside an Arabic paragraph.
13. 200 % text at 320 dp and 412 dp, LTR and RTL: no overflow (G6).
14. Pictures (`test/kit/kit_markdown_image_test.dart`, `test/reply_picture_tiles_test.dart`): the scanner (picture vs link, `!` before text, nested brackets, several per line, alt text, path vs http vs `data:`); file thumbnail and chip taps reach `open`; a web picture is never read and its tap shows the `openExternalLink` confirmation; consecutive pictures are one group; no raw syntax anywhere; speech and selection read the alt text.

## Galleries required

`test/goldens/kit/kit_markdown_golden_test.dart`, DPR 3, Android, Geist and Noto Sans Arabic loaded (TEST-8, TEST-9, TEST-20):

- States at 412×915, dark and light: `kit_markdown_default` (a reply with a heading, a list, inline code, a link, a quote and a short fence), `kit_markdown_table` (a wide table scrolling), `kit_markdown_streaming` (an unclosed fence), `kit_markdown_secondary` (the thought/note role), `kit_markdown_path_link` (a validated path chip).
- Default at 360×800, 915×412, 800×1280, 1280×800 (in a 700 dp pane) and 1600×1000, dark and light.
- Default at text 2.0 and in Arabic RTL (an Arabic reply with English inline code) at 412×915 and 1280×800, dark.
- G5 and G6 cover the rest. About 26 PNGs.
- Pictures, `test/goldens/kit/kit_markdown_images_golden_test.dart`, 412×915, dark and light: `kit_markdown_images_reply` (three thumbnails, a web chip, a `data:` chip), `_reply_ar` (the same in Arabic, right to left), `_inline` (pictures inside a sentence), `_unread` (a host with no file reader: named chips).

## Non-goals

- The agent blocks (choices, checklist, command): they stay in `agent_blocks.dart` (shared-shell-1) and reach the kit through `blockBuilder`.
- The search engine and match counting (`transcript_highlight.dart` stays the host's; chat-2 owns it).
- Syntax colouring (KitCodeBlock's `KitCodeHighlight`).
- A full Markdown spec: no HTML, footnotes or nested tables. They render as their source text. Pictures are tiles (see Pictures), not a gallery: no editing, no download, no loading of web pictures.
- Changing any caller: callers keep `MarkdownText` until their chain link or screen unit moves them.

## Open questions

None.
