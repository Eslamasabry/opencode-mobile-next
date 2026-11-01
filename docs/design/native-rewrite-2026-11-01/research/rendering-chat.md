# Research: rendering a coding-agent chat in Compose (Android first, KMP-ready)

Date: 2026-11-01. Topic owner: native rewrite plan (`docs/design/native-rewrite-2026-11-01/`).
Scope: Markdown, syntax highlighting, diffs, very long transcripts in `LazyColumn`, text selection, the cost of incremental text append, and smooth streaming reveal at 120 Hz.
How claims are marked:
- **[V]** I read the primary source today: official docs, the androidx `androidx-main` source, a library's own source or README, or a Maven Central listing.
- **[S]** Secondary source: a blog, a third-party PR, Slack, or an aggregator.
- **[I]** My inference or design proposal. It needs a spike or test before we rely on it.

Read-only inputs used:
- `/home/eslam/Storage/Code/wt/oc_app-batch1/lib/ui/kit/chat/` (KitMarkdown and its parts, KitTurn), `lib/ui/kit/kit_code_block.dart`, `lib/ui/kit/kit_diff_view.dart` + `lib/ui/kit/diff/`, `lib/ui/screens/chat/chat_transcript.dart`, `chat_scroll.dart`
- `/home/eslam/Storage/Code/wt/native-plan/map/chat-genui.md` (features 09 and 16-21)
- the demo `/home/eslam/Storage/Code/kmp-demo/shared/src/commonMain/kotlin/io/github/eslamasabry/kmpdemo/ui/{Markdown.kt,ChatScreen.kt}`
- the owner memory files on motion, native feel, text scale, max width, the turn model and chat kit decisions

---

## 0. Decisions in one screen

| Area | Recommendation | Confidence |
|---|---|---|
| Markdown parser | **`org.jetbrains:markdown` 0.7.18** (JetBrains, KMP, 2026-10-15). Its new **`StreamingMarkdownFile`** (added 2026-05-23) re-parses only the unstable tail on each append [V]. | High |
| Markdown renderer | **Our own kit block renderers**, a port of KitMarkdown's ~3.2k lines. Do **not** adopt mikepenz/multiplatform-markdown-renderer 0.46.0 as the renderer. Use it as a reference: it uses the same parser, and its lazy and streaming code shows the key and contentType scheme [V]. | High |
| Transcript list | One `LazyColumn(reverseLayout = true)`, **flattened to block-level items**: prompt, work line, each Markdown block, code, table, picture group, agent block, notice, footer and live line. Stable string keys, a `contentType` per kind, and a `placementSpec = null` default. | Medium-high; needs the 5,000-part spike |
| Following the stream | Pinned to the live edge, reverse layout keeps the bottom glued for free. A *new* block inserted at index 0 is **not** shown automatically, because the list keeps its anchor on the first visible item's key [V]. While following, call `requestScrollToItem(0)`. | Medium; spike |
| Smooth growth | The live block grows on a critically damped spring (port of `KitMotion.replyGrow`: stiffness 700, damping ratio ≈1.0). Start with `animateContentSize(spring(1f, 700f))` on the live block only. | Medium |
| Fresh words fade in | Fade **in the draw phase only**: a `DstOut` mask over the fresh ranges' paths in an offscreen layer. Never use per-frame `AnnotatedString` colour spans: those re-lay out the paragraph every frame [V]. Move to `DrawAnnotation` once it ships; it is in androidx-main now but not in a release yet [V]. | Medium-high |
| Line breaking | Keep **`LineBreak.Simple`**, which is the Compose default on Android [V]. `Paragraph`/HighQuality makes earlier lines "dance" while text grows [V]. | High |
| Frame rate | Use `Modifier.preferredFrameRate(FrameRateCategory.High)` on the live block **only while it reveals or grows**. On devices with adaptive refresh rate, views default to "Normal" (about 60 Hz) [V]. | Medium |
| Selection | **One `SelectionContainer` around the transcript list**. Selected lazy items are pinned while scrolled off [V]. Prompts and chrome go under `DisableSelection`. Target Compose 1.13, which fixes selection crashes while text updates [V]; on 1.12, disable selection on the live block. | Medium |
| Syntax highlighting | A `CodeTokenizer` interface. First implementation: **SyntaxMP 0.3.0** (Apache-2.0, pure common Kotlin, 39 languages, matches our alias list) [V]. Benchmark it against Highlights 1.1.0 [V] and keep kotlin-textmate 0.3.0 (JVM/Android only) as the high-fidelity fallback. Never use a WebView highlighter. | Medium; the libraries are young |
| Diff | **Port our own** unified-patch parser, fold model and rows. Optionally take Myers from `io.github.petertrr:kotlin-multiplatform-diff` 1.4.0, which has no unified-diff support [V]. Rows are `Text` lines with one shared horizontal scroll, and the width is computed from the monospace advance. | High |
| Links | Wrap all Markdown links in `LinkAnnotation.Url` and provide our own `LocalUriHandler` that routes into `openExternalLink` [V]. Decide what to do about system smart selection, which is on by default [V]. | High |
| Measuring | Port the Flutter perf fixtures (5,000 parts, ten tail updates, rebuild budget) as Macrobenchmark and unit counters. Add a Baseline Profile for "open chat + fling + stream", worth about 30% faster code on first run [V]. | High |

---

## 1. What we must match: today's Flutter chat rendering

From the map (`chat-genui.md` §09, §16-§21) and the kit source:

- **Markdown** (`lib/ui/kit/chat/kit_markdown.dart` 1,363 lines + `_table` 358 + `_image` 935 + `_fresh` 501 + `kit_stream_caret.dart` 97). It is a custom parser with no package.
  - Block types: headings h1-h6, paragraphs, blockquote with a rail, ordered lists (starting at the first item's number, nested by 2 or 4 spaces), bullets, GFM tables (inline-code cells never break), rule, fences (``` and ~~~), and HTML comment blocks (hidden).
  - Inline: bold, italic, both, strike, code and links.
  - Pictures: a scanner plus tiles, image-only line groups, and never a raw attachment id shown as a name.
  - **Path chips**: inline code that looks like a path becomes a link only once a lookup confirms the file exists. A miss is remembered for 20 s.
  - **Agent blocks**: ```` ```choices ````, ```` ```checklist ```` and ```` ```command ````, built only for closed fences.
  - **Find marks**: they decorate spans without changing the plain text.
  - **Streaming caret**, **per-block parse cache** (`debugParseCount`), and an **open fence stays plain until it closes**.
  - Each block is its own semantics node under one semantics boundary. Without that, a 5,000-paragraph reply cost seconds per frame.
  - `proseForSpeech` feeds read-aloud.
- **Streaming polish** (owner, 2026-10-27):
  - Words fade in over 250 ms. Only the block being written animates, and only its colour (paint only).
  - The reply grows smoothly on the `KitMotion.replyGrow` spring: mass 1, stiffness 700, damping 53, which is about critical, about 210 ms per line (`lib/ui/kit/kit_motion.dart:108`).
  - Steps fold in and out. The sent prompt lifts from the composer.
  - The live status line sits in the turn under the growing reply (chat kit decision 01B). The 2026-10-30 decision moved Stop to the composer chip row.
- **Code blocks** (`kit_code_block.dart` 1,386 + parts 351):
  - ~45 language aliases mapped to the Dart `highlight` package (a highlight.js port).
  - **Sources over 20,000 chars stay plain.** Parse cache of **48 entries**.
  - Capped at 12 lines in the transcript with "Open full output". A `$` prompt is drawn for commands.
  - Wrap only breaks at spaces and punctuation. Copy copies the source.
- **Diff** (`kit_diff_view.dart` 813 + `diff/` ~2,700):
  - A unified patch parser, plus an LCS differ for before/after that falls back to one changed block above a cell limit.
  - 3 context lines, then gap bars. "Lines 12-18" hunk headers. Line selection with an action bar.
  - Syntax colour per line, lazily, adjusted to reach 4.5:1 on add and remove rows. Always LTR. Split view on wide screens.
- **Scroll** (`chat_scroll.dart` 417, `chat_transcript.dart`):
  - `ScrollablePositionedList(reverse: true)`. "Away from latest" is a 480 px threshold.
  - Pinned rendered count while away. Follow after send.
  - Live-end watch when the bottom padding grows. `_jumpToMessage` opens the step's fold and flashes it.
  - Stream flushes are coalesced to **≤1 per 50 ms**. Only the newest turn is re-derived per flush.

Demo lessons relevant here (`kmp-demo/.../ui/Markdown.kt`, `ChatScreen.kt`):

- **Whole-reply re-parse on every frame.** `RevealLoop` advances `p.shown` on every `withFrameNanos`. `Markdown(visible, …)` does `remember(text) { parseMarkdown(text) }`, so the whole visible text is re-parsed on each frame. That is O(reply length) per frame [V, read in source].
- **Re-layout on every frame.** `fadeTail` builds new `SpanStyle` colour spans each frame. Span styles count as layout-affecting annotations, so the tail paragraph re-measures every frame (see §6.1) [V].
- **Placement animation on growth.** `animateItem(placementSpec = Motion.spatial())` sits on every row. A row above the growing reply moves because the reply grows, and that move is animated too (§4.6) [I].
- **What the demo got right**:
  - `reverseLayout = true`
  - stable keys
  - `contentType`
  - `SelectionContainer` per reply
  - one growing `Text` for a streaming code block ("no per-line layout churn")

  Its smoothness on short replies is real. The costs above only show on long replies.

---

## 2. Platform baseline (as of 2026-11-01)

| Thing | Stable | Pre-release | Source |
|---|---|---|---|
| Jetpack Compose foundation/ui | **1.12.1** (2026-09-09; 1.12.0 on 2026-08-12) | 1.13.0-beta02 (2026-10-21) | compose-foundation release notes [V] |
| Compose BOM | 2026.09.00 | — | Google Maven metadata [V] |
| Jetpack Material3 | 1.4.0 | 1.5.0-beta02 | Google Maven metadata [V] |
| Compose Multiplatform | **1.12.2** (2026-10-15, on Jetpack 1.12.1) | 1.13.0-beta01 (2026-10-23, on Jetpack 1.13.0-beta01) | CMP GitHub releases [V] |
| Kotlin | 2.4.0 in the demo; strong skipping has been the default since Kotlin 2.0.20 | — | strong skipping doc (updated 2026-10-20) [V] |

These are the Compose text, list and selection changes that matter here (release notes [V]):

| Version and date | Change |
|---|---|
| 1.9 (2025) | `Modifier.preferredFrameRate(Float \| FrameRateCategory)`. It was renamed from `requestedFrameRate` in 1.9.0-beta01. It is in `commonMain` in androidx-main [V]. |
| 1.9 | `LazyLayoutCacheWindow` (ahead and behind prefetch windows). It became stable in **1.13.0-alpha01**. In 1.13.0-alpha03 the old `LazyListPrefetchStrategy` was deprecated, and the window moved from the state to a `cacheWindow` parameter on `LazyColumn`. |
| 1.9 | Copy from several `Text`s in one `SelectionContainer` puts `\n` between them. Smart selection (TextClassifier "smart items") arrived, controlled by `ComposeFoundationFlags.isSmartSelectionEnabled`, which **defaults to true** (source) [V]. |
| 1.10.0-alpha05 (2025-10-08) | Pausable composition in lazy prefetch on by default. |
| 1.10.6 (2026-03-25) | Pausable composition in prefetch **turned off** "to address stability concerns". |
| 1.13.0-alpha03 (2026-09-09) | The pausable-composition flag was removed. |

  The notes do not say plainly whether pausable composition ends up on or off. Treat it as unknown until we check on 1.13 (§10).

| Version and date | Change |
|---|---|
| 1.10.0-alpha01 | Double-tap selects a word in `SelectionContainer`. |
| 1.10.1, 1.10.2 | Fixes for selection crashes. |
| 1.12.0-alpha01 (2026-04-22) | Compose compileSdk 37, which **needs AGP ≥ 9.2.0**. This matches the demo lesson. |
| 1.12.0-alpha03 (2026-05-19) | `SelectionContainer` auto-scrolls when a selection drag goes past the viewport. |
| 1.13.0-alpha01 (2026-08-12) | New **`SelectionState`** API: observe or set the selection, select all, clear. New fix: "**Text selection is now safely constrained to the text length during concurrent text updates to prevent crashes**". This is directly relevant to selecting text that is still streaming. |
| 1.13.0-alpha03 | `LineHeightStyle.Mode.PerLine` (mixed font sizes in one paragraph without overlap). |
| Runtime 1.13 | `computedStateOf`, which the performance guide (updated 2026-10-22) now prefers over `derivedStateOf` for cheap conditions on scroll state [V]. |

**Implication [I]:** start the chat on **CMP 1.13 (beta now)**, the same call the sibling `kmp-cmp-status.md` makes. Until 1.13 is stable, keep the live block under `DisableSelection` (§5).

---

## 3. Markdown

### 3.1 Options

| Library | Latest (date) | KMP targets | Streaming | Fit for us |
|---|---|---|---|---|
| **JetBrains `org.jetbrains:markdown`** (parser only) | **0.7.18 (2026-10-15)** [V] | JVM, JS, wasm-js, iOS (arm64, sim, x64), macOS, Linux, mingw [V Maven dirs] | **Yes.** `StreamingMarkdownFile`: "When `append` is called, only the `unstableTail` part will be reparsed"; stable children are "append-only, … will not be reparsed or replaced". It has had a `CancellationToken` variant since 2026-08-01. Added in PR #185 on 2026-05-23 [V source + git history]. | **Use as the parser.** Official JetBrains project; GFM flavour (tables, strikethrough, autolinks); gives AST offsets we can key on. |
| mikepenz/multiplatform-markdown-renderer | **0.46.0 (2026-10-13)** [V Maven]. Modules: core, m2, m3, coil2/3, `-code` (Highlights 1.1.0) | Android, iOS, desktop, web | `rememberStreamingMarkdownState()` / `collectAsStreamingMarkdownState()` since 0.42.0 (2026-06-20), built on the JetBrains `StreamingMarkdownFile` [V source]. `LazyMarkdownSuccess` renders blocks as `LazyColumn` items with `key = node.startOffset` and `contentType = node.type` [V source]. | Mature and active, but Material-styled, and 0.46.0 was a **breaking** release. Our needs (path chips, agent blocks, find marks, fade-in, picture groups, 700 dp caps, kit-only) mean overriding nearly every component. **Use it as a reference implementation, not a dependency.** |
| compose-richtext (halilozercan) | 1.0.0-alpha05 (2026-06-07) [V Maven] | Android and JVM; no iOS per secondary listings [S] | None found [S] | No. Alpha, no iOS, commonmark-java parser. |
| huarangmeng/Markdown | 1.6.2 (2026-10-23) [V Maven] | Android, iOS, desktop, web; CMP 1.11.1 badge [V README] | `isStreaming` with a 16-char batching threshold and per-frame coalescing; claims 652/652 CommonMark examples; LaTeX, Mermaid and cross-block selection [V README] | Interesting but single-author (53 stars) and bundles a lot (LaTeX, diagram engines). Possible source of ideas; not a dependency. |
| adamglin0/compose-markdown | 2.1.0 per README; CMP 1.12.1; needs compileSdk 37 and JVM 21; "developing by AI"; 4 stars [V README] | Android, JVM, iOS, JS, wasm | Append-only incremental, with an optional `holdUnsettledLines` that holds partial lines for 500 ms | Too young. |
| Others seen [S] | llm-typewriter, compose-agent-render, zly2006/Markdown, the `dev.hossain:compose-highlight` WebView approach | — | — | Not considered: unproven or WebView-based. |

### 3.2 Recommended design [I]

```
SSE deltas ──► transcript reducer (Dispatchers.Default, single-writer)
                 │  appends to StringBuilder per text part; emits immutable snapshot ≤ 1 per frame
                 ▼
        MarkdownStream (one per live text part, confined to a single-thread dispatcher)
           • normalises CR/LF across chunk edges (README rule [V])
           • StreamingMarkdownFile.append(chunk)  (coalesced: ≤1 append per frame / 50 ms)
           • maps stableChildren → immutable Block (cached forever), unstableTail → fresh Blocks
           • open fence: Block.Code(open = true), no highlighting until the closing fence
                 ▼
        TranscriptRows (pure function, cached per message identity like chat_page_cache.dart)
                 ▼
        LazyColumn items: key = "b:$partId:$startOffset", contentType = BlockKind
```

- **Why our own renderer.** The kit-only rule says every component comes from `ui:kit`. Our extras also live *inside* inline span building: path chips, find marks, bidi isolation of inline code, the fade-in, and link policy. The JetBrains AST gives us `startOffset`/`endOffset` and node types. Walking it into our own `Block` model is a few hundred lines, and those lines are testable without UI.
- **Cost trap.** `StreamingMarkdownFile` re-parses the *whole unstable tail* on every append. Its own KDoc says this "matters most when the `unstableTail` is large" [V]. A 400-line code fence that is still open is the worst case.
  - Coalesce appends: at most one per frame, the same as Flutter's 50 ms rule.
  - Special-case an open fence: while the tail is one open fence, append to a code buffer and skip re-parsing until a line starts with the closing marker. This is a wrapper over the parser [I].
- **Non-append edits.** Examples: OC2 `session.text.ended` carrying a different full text, a history reload, or an edited earlier block (Flutter test `markdown_streaming_test.dart` "an edited earlier block still re-renders").
  - Fall back to a full parse on `Dispatchers.Default` (`MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString`).
  - Keep the keys of unchanged-prefix blocks so they don't recompose.
  - Cache finished replies by `(partId, text hash)`.
- **Line endings.** The parser requires LF. For streams, buffer a trailing CR across chunks. That is the README's explicit rule [V].
- **Inline building.** Build each block's `AnnotatedString` **off the main thread**: bold/italic/strike/code spans, `LinkAnnotation.Url` for links, path-chip placeholders via `InlineTextContent` or a `LinkAnnotation.Clickable` tag once the file is confirmed, and FSI…PDI bidi isolation around inline code in RTL prose.
  - `AnnotatedString` is pure data. Only the click handling needs the UI, through `LocalUriHandler` (see §3.3).
- **Agent blocks.** These are ```` ```choices ````, ```` ```checklist ```` and ```` ```command ````. Map them only for **closed** fences, the same rule as today. The handler comes from a CompositionLocal, so a new lambda never forces re-parsing (AgentChoiceScope parity).
- **Tables.** A kit `KitMarkdownTable`:
  - one horizontally scrollable `Row` of measured columns
  - inline-code cells use `softWrap = false`
  - a hard max width, with the overflow rule "scroll" (component-max-width rule)
- **Pictures.** Coil 3 is already in mikepenz's dependency set at 3.6.3 [V release notes]. Keep the Flutter picture scanner's rules:
  - stored attachment ids never shown as names
  - 8 MiB read cap
  - web addresses never fetched

### 3.3 Link safety (security invariant) [V]

`LinkAnnotation.Url(url)` with no listener opens through `LocalUriHandler`: "the app will try to open the url using UriHandler" (KDoc in androidx-main). Provide one handler at the app root:

```kotlin
class ExternalLinkUriHandler(private val open: (String) -> Unit) : UriHandler {
    override fun openUri(uri: String) = open(uri) // → the app's openExternalLink policy, never Intent directly
}
// root:
CompositionLocalProvider(LocalUriHandler provides remember { ExternalLinkUriHandler(links::openExternalLink) }) { App() }
```

Then a stray `LinkAnnotation.Url` anywhere still goes through the policy.

**Smart selection.** It is on by default (`isSmartSelectionEnabled = true`). The selection toolbar may offer a system "Open" action for a *selected* URL, and that action does not go through `UriHandler` [V for the flag, I for the toolbar behaviour on our phone]. Decide in the security review:
- set the flag to false in `Application.onCreate` (a global, mutable `@JvmField`), or
- accept it as a user-initiated system action.

---

## 4. Very long transcripts in `LazyColumn`

### 4.1 Orientation: reverse layout

Google's Jetchat sample uses `LazyColumn(reverseLayout = true)` [V source]. With reverse layout:
- index 0 sits at the bottom,
- the default arrangement is `Arrangement.Bottom`,
- `firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0` means "at the live edge".

Short conversations hug the composer for free, which matches today's `ScrollablePositionedList(reverse: true)`.

### 4.2 Anchoring rules we must design around [V]

- `LazyListState` "maintain[s] the index based on the first visible item key" when the data set changes (KDoc for `requestScrollToItem` in androidx-main). `requestScrollToItem(index, offset)` overrides that "*only* for the next remeasure", with no animation and no extra frame.
- Consequences [I, reasoned from the rule above; confirm in the spike]:
  1. **The bottom item grows while you are at the live edge.** Its bottom stays glued to the viewport bottom, and everything above moves up. This is the "follow" we want, and it costs nothing.
  2. **A new item is inserted at index 0** (the next Markdown block, a tool row, the live line). The list keeps its anchor on the *old* first item, so the new item lands **below the viewport, invisible**.
     - Fix: while `following == true`, call `listState.requestScrollToItem(0)` in the same snapshot as the insert.
     - Jetchat does the equivalent with `scrollToItem(0)` after sending.
  3. **You are reading inside the live reply,** scrolled up but still within the bottom item. Growth at the item's bottom shifts what you are reading.
     - Block-level flattening (4.3) makes this rare: you are almost always inside a *finished* block.
     - When it does happen, compensate with `scrollBy` for the size delta of the first visible item, or stop growth until the reader returns.
  4. **History is prepended** (older pages). They land at high indices, at the far end of the reverse list. The key anchor keeps the view still. This is the opposite of the Flutter workaround work (`_pinnedMessageCount`).
- "Away from latest" (Flutter: 480 px): `computedStateOf` (runtime 1.13) or `derivedStateOf { idx > 0 || offset > awayPx }`. Read it only where it is needed, such as the Jump pill, so scrolling does not recompose the screen.

### 4.3 Item granularity: flatten to blocks [I, recommended]

Two shapes:

- **Turn as one item** (the demo; close to Flutter).
  - Simple, and selection per turn is trivial.
  - But a huge reply composes and measures entirely even when only a sliver shows. The Flutter perf test exists exactly because a 5,000-part case cost seconds per frame.
- **Blocks as items.** Each prompt, work line, Markdown block, code block, table, picture group, agent block, notice or marker, turn footer and live line is its own lazy item.
  - Virtualisation then works inside long replies, and only the tail block re-lays out.
  - `contentType` reuse is real: a paragraph item reuses a paragraph composition.
  - mikepenz's `LazyMarkdownSuccess` takes the same approach [V].

Recommended keys and types:

```kotlin
sealed interface Row { val key: String; val kind: RowKind }       // @Immutable data classes
// keys are Strings (Bundle-saveable, per the lists guide [V]); never indexes or hashCode() [V best practices]
"prompt:$messageId"        "work:$turnId"          "b:$partId:$startOffset"
"code:$partId:$startOffset" "notice:$id"           "footer:$turnId"   "live:$turnId"   "ask:$requestId"
enum class RowKind { Prompt, WorkLine, Prose, Heading, Quote, ListItem, Code, Table, Pictures, AgentBlock, Notice, Marker, Footer, Live, Request }
```

- Turn-level visuals that span blocks (the 16 dp gutter, the 720 dp reading cap, one "more" control per turn) become **per-item padding plus a `TurnPosition` (first/middle/last)** carried in the row. There are no frames or fills to join, by the 2026-09-20 turn model rule, so this is easy.
- Fold state (work line open, reasoning open) is **hoisted** into an `ExpansionStore` keyed by id, the same as Flutter's `_ExpansionStore`. Item recycling then cannot lose it, and `jumpToMessage` can open a fold before scrolling.
- Jump to message: keep `id → index` in the derived rows. Use `scrollToItem(index, offset)`, then animate only the last stretch, the same as Flutter's measured reveal.

### 4.4 Stability and recomposition [V docs, I application]

- Strong skipping is on by default (Kotlin ≥ 2.0.20). Unstable params are compared with `===` and stable ones with `equals` [V].
  - Make rows `@Immutable` data classes holding prebuilt `AnnotatedString`s and primitives.
  - For lists inside rows, use `kotlinx.collections.immutable` types, or rely on identity: the reducer reuses unchanged row instances. That is Flutter's `identities` trick.
- The guide warns that annotating models that hold large collections makes `equals` slow in lazy layouts [V]. Keep rows small; large data such as code text is compared by identity or a version field.
- Do all derived passes (settle → turns → rows) in the presenter on `Dispatchers.Default`, and recompute only the newest turn on a flush (parity with `debugChatDisplayPassMessages`).
- Never `list.indexOf` inside `items` (O(N²) [V]). Use `itemsIndexed` or precomputed indices.

### 4.5 Prefetch, cache window, pausable composition [V facts, I values]

- 1.12: `rememberLazyListState(cacheWindow = LazyLayoutCacheWindow(aheadFraction = …, behindFraction = …))`.
- 1.13: pass `cacheWindow =` on `LazyColumn` instead; the state route is deprecated. 1.13.0-alpha03 also has an opt-in dynamic default: ahead window = average visible item size, clamped to 10-50% of the viewport.
- Proposed start point [I]:
  - `aheadFraction = 1.0f`. "Ahead" is older history when scrolling up in a reverse list.
  - `behindFraction = 0.5f`. This also keeps recently passed items composed, which helps selection (§5).
  - Tune with the fling benchmark.
- Heavy items must be cheap to *compose*. Highlighting, inline building and image decoding happen off the main thread and are cached, so a prefetched code block is only `Text(prebuiltAnnotatedString)`.
- Avoid 0-pixel items. Pictures get placeholder sizes from known dimensions, as the lists guide asks [V].

### 4.6 Item animations [V docs, I conclusion]

`Modifier.animateItem(fadeInSpec, placementSpec, fadeOutSpec)` animates appearance, disappearance **and placement changes**; "other position changes caused by events like arrangement or alignment changes will also be animated" [V via API reference summaries]. When the live block grows, every row above it changes position each frame. A placement spring on those rows makes them lag behind the text [I].

So:
- **The default for transcript items is `placementSpec = null`.**
- Keep `fadeInSpec = tween(200)` and `fadeOutSpec = tween(150)`, the 150-250 ms rule.
- Turn placement on only for user-initiated structural changes, such as opening or closing a work fold, by switching the spec while that change runs.

### 4.7 Accessibility order with reverse layout [S]

Stream Chat's Android SDK (PR #6536, merged 2026-07-03, shipped in v7.6.0) found that TalkBack reads a `reverseLayout` list in composition order, newest first. Their fix:
- explicit `traversalIndex` for the header, list and composer regions,
- `isTraversalGroup` on groups,
- "each lazy item gets a zIndex that mirrors its traversal index".

Plan for the same fix and test it with TalkBack on the phone.

Other accessibility rules [I]:
- Never put `liveRegion` on streaming text; that would announce every delta.
- Announce the end of a reply once, from the live line.
- Check parity with today's semantics in the widget-test catalogue later.

### 4.8 Skeleton [I]

```kotlin
val listState = rememberLazyListState(cacheWindow = LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = .5f)) // 1.12 form
val selection = rememberSelectionState()                                   // 1.13+
SelectionContainer(state = selection) {
    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(top = headerClearance, bottom = composerClearance),
        modifier = Modifier.fillMaxSize().semantics { isTraversalGroup = true },
    ) {
        items(rows /* newest-first view: rows.asReversed() is O(1) */, key = { it.key }, contentType = { it.kind }) { row ->
            TranscriptRow(row, Modifier.animateItem(fadeInSpec = tween(200), placementSpec = null, fadeOutSpec = tween(150)))
        }
    }
}
// following the live edge
LaunchedEffect(listState) {
    snapshotFlow { presenter.newestKey }.collect { if (presenter.following) listState.requestScrollToItem(0) }
}
```

`following` turns off when the person drags (`listState.interactionSource` drag interactions). It turns back on when they reach the live edge or tap the Jump pill, the same as `_followAfterSend`.

---

## 5. Text selection across messages

Facts [V, `SelectionContainer.kt` KDoc in androidx-main; the text was updated 2024-04-26 after the 2023-08-25 fix "selection not working with multiple texts in lazy layouts"]:

> "Use of a lazy layout … within a SelectionContainer has undefined behavior on text items that aren't composed. For example, texts that aren't composed will not be included in copy operations and select all will not expand the selection to include them. However, selected lazy layout items will be pinned so that they don't leave composition when scrolled off-screen."

Release-note facts [V]:
- Copy from several `Text`s inserts `\n` between them (1.9).
- Drag auto-scroll past the viewport (1.12).
- Double-tap selects a word (1.10).
- `SelectionState` to observe or drive selection (1.13).
- The concurrent-update crash fix (1.13).

The docs page "user interactions" (updated 2026-10-28) shows cross-`Text` selection with `DisableSelection` islands, but says nothing about lazy lists [V].

Recommendation [I]:
- **One `SelectionContainer` around the transcript `LazyColumn`**, so a selection can run from one block to the next and across turns.
  - With block items, a per-turn container is not possible.
  - A behind cache window of about half a viewport keeps the neighbourhood composed.
- `DisableSelection` around:
  - prompt bubbles, whose long-press is the prompt menu (owner rule, the same as Flutter `selectable:false`)
  - work lines and tool rows
  - chips, buttons, the live line
  - and on Compose 1.12 the **live block while it streams**. On 1.13 the live block can stay selectable once the crash fix is confirmed.
- Use the `SelectionState` (1.13) to offer selection-aware actions (copy is built in), for example "Add to message", if the owner wants it. It is a visible action in the toolbar, not a hidden gesture.
- "Select all" covers only composed items. For "copy the whole reply", keep the turn footer's **Copy**, which copies verbatim (today's parity). Do not rely on select-all.
- iOS/desktop (later): CMP 1.12 restyled iOS selection handles, and 1.13.0-beta01 fixed an RTL selection crash on Mac [V CMP releases]. Mouse selection already works on desktop.

---

## 6. Performance of incremental text append

### 6.1 What a change to a `Text` costs [V, androidx-main source]

`TextAnnotatedStringNode.updateText`:

```kotlin
val charDiff = this.text.text != text.text
val layoutTextDiff = charDiff || !this.text.hasSameLayoutAffectingAnnotations(text)
... return layoutTextDiff   // → invalidateMeasurement() + invalidateDraw()
```

`hasSameLayoutAffectingAnnotations` skips only `DrawAnnotation`s. **Every `SpanStyle` is "layout-affecting", including colour-only spans.** So:

- Appending characters → re-measures that paragraph. This is unavoidable, so keep paragraphs small. Block items do that naturally.
- Changing span colours each frame (the demo's `fadeTail`) → **re-measures every frame**.
- Animating colour through the `BasicText(color = ColorProducer)` lambda or `TextStyle` colour → draw only. A style-level colour change does not re-layout [S]. This is fine for whole-block colour, not for per-word fades.
- **`DrawAnnotation`** (androidx-main, copyright 2026): "updating or animating state read inside `drawBehind` or `drawOver` only invalidates drawing without triggering text measurement or relayout". It gives `textLayoutResult`, `start` and `end`, plus `getBoundingBoxesForRange` / `fillPathForRange`. I found **no release note naming it yet** in the compose-ui notes up to 1.13.0-beta02. Treat it as "coming soon"; it is the ideal tool for both fresh-word fades and find marks.

### 6.2 Line breaking: keep Simple [V]

- The Compose default break strategy is **`BREAK_STRATEGY_SIMPLE`**. `toLayoutBreakStrategy` maps an unspecified strategy to `LayoutCompat.DEFAULT_BREAK_STRATEGY = BREAK_STRATEGY_SIMPLE`.
- Material3's typography tokens do not set `lineBreak`.
- Android's own docs, on why `EditText` uses SIMPLE: "the default value for EditText is Layout.BREAK_STRATEGY_SIMPLE, **the latter to avoid the text "dancing" when being edited**". HighQuality and Balanced, especially with hyphenation, "require more time to do the text layout" (`TextView.setBreakStrategy`).
- Our streaming text is the same case as a field being typed into. **Do not set `LineBreak.Paragraph` on reply text,** and do not switch strategy when a block settles, because that reflows once at the end. Hyphenation stays off, which is the default [V style-paragraph doc, updated 2026-10-28].

### 6.3 Budget rules [I]

1. The reducer appends into `StringBuilder`s off the main thread and publishes immutable snapshots at most once per frame (or every 50 ms, which is Flutter parity).
2. Only the **tail block** gets a new `AnnotatedString`. Settled blocks keep their instance, so `===` skips them.
3. Inline building for the tail is O(tail). Re-parse cost is O(unstable tail), bounded by the open-fence special case (§3.2).
4. Streaming code in the transcript is capped at 12 visible lines. While a fence streams, update only the first 12 lines plus the line count. Lines past 12 cost no layout (parity with "Open full output").
5. Highlighting never runs per token. A closed fence is highlighted once off the main thread and cached in the LRU (48 entries, theme-independent tokens).
6. **No layout work in the per-frame path:** the reveal clock (§7) changes layout only when a *word* is revealed, about 8-20 times a second. Every other frame is draw-only.

---

## 7. Smooth 120 Hz streaming reveal

### 7.1 Frame rate on adaptive-refresh devices [V, ARR guide updated 2026-10-23]

- On Android 15 QPR1+ devices with adaptive refresh rate (`hasArrSupport()`), "most Views default to a 'Normal' frame rate, which is often set to 60 Hz".
- The final rate is "determined by the highest vote". "Animations that involve movement or size changes automatically receive a higher refresh rate". A touch boosts to High for a while after release.
- Compose 1.9+ votes with `Modifier.preferredFrameRate(…)` / `FrameRateCategory.High` [V].

Rule [I]: put `Modifier.preferredFrameRate(FrameRateCategory.High)` on the **live block only while it is revealing, fading or growing**, and remove it when the reply settles. Fade-in is alpha-only and may otherwise run at "Normal" 60 Hz. Whether the owner's NX721J (Android 16) supports ARR is unknown; log `Display.hasArrSupport()` in the diagnostics.

### 7.2 Pipeline per live text part [I]

```
network snapshot (text, ≤1/frame) ─► MarkdownStream ─► tail Block (AnnotatedString, full text so far)
                                                         │
RevealState.shown (word-snapped, layout)  ◄── withFrameNanos clock (only while behind or fading)
RevealState.runs  [start,end,bornNanos]   ── read in draw phase only ──► DstOut mask over fresh glyphs
SmoothGrow(height spring, layout of the item only) ── list moves rows above like a scroll
preferredFrameRate(High) while any of the above is active
```

**Reveal clock** (port of the demo's `RevealLoop`, fixed):

```kotlin
const val BASE_CPS = 45f; const val DRAIN_S = 0.25f; const val FADE_NS = 250_000_000L
suspend fun RevealState.drive(text: () -> CharSequence, reduced: () -> Boolean) {
    snapshotFlow { text().length > shown || runs.isNotEmpty() }.collectLatest { busy ->
        if (!busy) return@collectLatest                 // park: no frames scheduled while idle (battery)
        var last = 0L; var carry = 0f
        while (true) {
            val keepGoing = withFrameNanos { now ->
                val t = text()
                if (reduced()) { reveal(t.length, now, fade = false); runs.clear(); return@withFrameNanos false }
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f); last = now
                val backlog = t.length - shown
                carry += maxOf(BASE_CPS, backlog / DRAIN_S) * dt  // never lags more than ~250 ms
                val want = shown + carry.toInt(); carry -= carry.toInt()
                val snapped = t.wordEndAtOrAfter(want).coerceAtMost(t.length)   // reveal whole words
                if (snapped > shown) reveal(snapped, now, fade = true)          // → layout (8–20×/s)
                frameNanos = now                                                // → draw-only invalidation
                runs.removeAll { now - it.born > FADE_NS }
                t.length > shown || runs.isNotEmpty()
            }
            if (!keepGoing) break
        }
    }
}
```

`shown` drives which prefix of the tail block is laid out. Use `buildAnnotatedString` with `append(full, 0, shown)`, or keep the full text and clip the rest with a transparent `SpanStyle` that only changes on word boundaries. Both re-measure only when a word lands.

**Fresh-word fade, draw phase only** (until `DrawAnnotation` ships):

```kotlin
fun Modifier.freshFade(layout: () -> TextLayoutResult?, state: RevealState) =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }   // live block only
        .drawWithCache {
            val path = Path()
            onDrawWithContent {
                drawContent()
                val tl = layout() ?: return@onDrawWithContent
                val now = state.frameNanos                                     // state read here = draw-only
                for (r in state.runs) {
                    val hide = 1f - FastOutSlowInEasing.transform(((now - r.born) / FADE_NS.toFloat()).coerceIn(0f, 1f))
                    if (hide <= 0f) continue
                    path.reset(); path.addPath(tl.getPathForRange(r.start, r.end))
                    drawPath(path, Color.Black.copy(alpha = hide), blendMode = BlendMode.DstOut)
                }
            }
        }
```

`getPathForRange` returns per-line glyph boxes. `DstOut` lowers the destination alpha there, so the fade works on any background. The live background feature is not a solid colour, which rules out a painted "veil" [I]. Remove the offscreen layer when the block settles.

**Smooth growth.** The Flutter spec: mass 1, stiffness 700, damping 53, so ζ = 53 / (2·√700) ≈ 1.00. Words are laid out at full size and clipped at the box's bottom edge. A shrink is taken at once. The Compose equivalent [I]:

```kotlin
val ReplyGrow = spring<IntSize>(dampingRatio = 1f, stiffness = 700f)
Modifier.animateContentSize(animationSpec = ReplyGrow, alignment = Alignment.TopStart)   // live block only
```

- `animateContentSize` retargets from the current value and velocity and clips to the animated bounds. The demo already used it on the turn column.
- If shrink-on-rewrap animating, or clipped descenders, bother the owner, replace it with a small `LayoutModifierNode`. It would animate only growth with an `Animatable` and leave a descender allowance, the same as Flutter's `_KitMdGrow`.
- A newly inserted tail block grows from 0, which smooths the `requestScrollToItem(0)` hop from §4.2.

**Glyph shimmer.** Rows move by whole pixels through lazy placement, so `TextMotion.Static` (hinting on) is right. Use `TextMotion.Animated` only on text that is translated or scaled through `graphicsLayer`, such as the sent-prompt "lift". The release-note guidance: "Use TextMotion.Animated if Text is going to be scaled, translated, or rotated via animation" [V].

**Reduced motion.**
- Honour `Settings.Global.ANIMATOR_DURATION_SCALE == 0` and the app's "Animations: Calm".
- Words appear at once, with no fade and no glide. Keep the 50 ms coalescing.
- The stream caret stops breathing.

No haptics during streaming (owner: never on scroll or plain taps).

### 7.3 What "smooth" will be measured against [I]

| Scenario | Metric (Macrobenchmark `FrameTimingMetric`) | Target |
|---|---|---|
| Stream a 6,000-word reply with 3 code fences, at the live edge | `frameOverrunMs` P95 / P99 | ≤ 0 / ≤ 4 ms on the phone at 120 Hz |
| Fling 1,000 turns (the 5,000-part fixture) | same | same |
| Open a chat with 300 turns | time to first transcript (Flutter mark `chat.open_to_first_transcript`) | ≤ 300 ms warm |
| A tail update on a 200-block reply | blocks re-laid out per update (unit counter) | 1 |

The emulator is fine for relative regressions. Absolute numbers only count on the NX721J (owner-tests rule).

---

## 8. Syntax highlighting

### 8.1 Options

| Library | Latest (date) | Targets | How it works | Languages | Notes |
|---|---|---|---|---|---|
| **SyntaxMP** `com.gallatinapps.syntaxmp:syntaxmp-tokenizer` / `:syntaxmp` | **0.3.0 (2026-06-04)** [V Maven] | Android, JVM, iOS arm64 and sim, wasm [V] | "purpose-built lexical tokenizers… single-pass… no regex or grammar runtime". Scanners are heuristic. `rememberSyntaxAnnotatedString`, tokens with dotted `SyntaxRole` [V README] | **39**: Bash, C, C#, C++, CSS, CSV, Dart, Diff, Dockerfile, Dotenv, Go, GraphQL, HTML, INI, Java, JS, JSON, JSX, Kotlin, Makefile, Markdown, PHP, PostgreSQL, PowerShell, Properties, Protobuf, Python, Ruby, Rust, Shell, SQL, SQLite, Swift, TOML, TSX, TS, XML, YAML, Zsh | Best match for our ~45 aliases (missing: scss → css, gradle, svg → xml, console → bash, patch → diff). Very young: 4 stars, about 20 commits. Apache-2.0, so we can vendor it. |
| **Highlights** `dev.snipme:highlights` | **1.1.0 (2025-09-02)** [V Maven] | JVM, Native, JS, wasm [S klibs] | Caches results, "incremental changes", sync or async [V README] | **17**: C, C++, Dart, Java, Kotlin, Rust, C#, CoffeeScript, JS, Perl, Python, Ruby, Shell, Swift, TS, Go, PHP | **No JSON, YAML, TOML, HTML, XML, CSS, SQL or diff.** That is most of what agents print. No release in 14 months. Used by mikepenz `-code` and by adamglin. |
| **kotlin-textmate** `io.github.ivan-magda:kotlin-textmate-core` (+`-compose`) | **0.3.0 (2026-10-06)** [V Maven] | **JVM/Android only.** iOS would need a native Oniguruma [V README] | A port of vscode-textmate. Joni regex. `tokenizeLine(prevState)` allows line-incremental highlighting; the bundled highlighter re-tokenises everything [V README] | Any VS Code `.tmLanguage` | Highest fidelity. JVM bench: Kotlin 79k lines/s, JSON 458k, JS 10k [V README]. Joni 2.2.1 (230 KB jar) plus **jcodings 1.0.58 (1.74 MB jar)** [V Maven]. MIT, 49 stars. |
| tree-sitter `io.github.tree-sitter:ktreesitter` | 0.25.1 (2026-07-01) [S] | JVM, Android, Native; not JS or wasm [S docs] | Real parsers through JNI | One native `.so` per grammar | Needs the NDK, APK size grows per grammar, AGP 9's KMP library plugin has no `externalNativeBuild` [S]. **Not worth it for read-only chat code.** |
| sora-editor `language-textmate` (tm4e engine) | — | Android | Editor-bound | — | An editor, not a highlighter. No. |
| `dev.hossain:compose-highlight` | 0.40.0 [S] | Android | **highlight.js in a hidden WebView**; 120-180 ms cold start | 190+ | **No.** WebView, and it runs foreign JS on transcript data. |

### 8.2 Recommendation [I]

- **`CodeTokenizer` interface in `ui:kit` common:**
  - `tokenize(code, langAlias): List<Token(start, end, role)>`, with roles such as keyword, string, comment, number, type, function, punctuation, meta, addition and deletion.
  - Kit code roles map roles to colours (the `ThemeRoles` code roles today). Tokens are theme-independent, so a theme change does not re-tokenise. Keep the 48-entry LRU keyed by `(alias, source hash)` and the **20,000-char plain limit**.
- **Implementation 1: SyntaxMP**, vendored or pinned. Accept a slightly different palette, as the map's stance already does.
- **Benchmark gate before committing** (one spike, about half a day):
  - Use a corpus of 45 aliases × 3 real agent outputs taken from the owner's transcripts.
  - Measure correctness by eye on the kit's code block, and tokenise time per 20k chars on the phone.
  - Run SyntaxMP, Highlights and kotlin-textmate side by side.
  - If SyntaxMP's heuristics are visibly wrong on common outputs (shell, JSON, TS, Kotlin, diff), use **kotlin-textmate on Android/JVM behind the same interface**, plus SyntaxMP or plain text for iOS later. Grammars ship as assets, and APK cost is measured.
- **Streaming:** an open fence stays plain (parity). Optional upgrade with TextMate: line-incremental colouring of *completed* lines using `prevState`, leaving the last line plain.
- **Diff rows:** tokenise the **whole old and new file text once**, not each line. Each line on its own breaks multi-line strings and comments. Then slice tokens per line. Keep the 4.5:1 contrast adjustment on add and remove rows (§9).

---

## 9. Diff view

Facts [V]:
- `io.github.petertrr:kotlin-multiplatform-diff` 1.4.0 (2026-09-20, Apache-2.0) is a port of java-diff-utils 4.15 for JVM, JS, wasm and Native, **without unified-diff support**, fuzzy patches or JGit.
- Other KMP diff libraries exist (`dev.andrewbailey.difference`, `baole/diff-kotlin`) [S].
- I found **no ready Compose diff viewer** with unified and split views [S search].

Recommendation [I]:

- **Port** `kit_diff_model.dart` (653 lines): the unified-patch parser with its `\ No newline` note rule and dropped preambles, gap folding with 3 context lines, hunk ranges, and per-file derived facts cached per immutable `DiffFile`.
- For before/after text, use Myers from petertrr (O(ND), with no need for the cell-limit fallback), or port the existing LCS with its fallback. Both are pure functions with ported unit tests (`test/kit/kit_diff_view_test.dart` has 31 tests; `diff_page_test.dart` 8).
- **Inline in a tool step:** at most 12 lines, a plain `Column` of `Text` rows (not lazy). Wrapping is a choice on the long-press menu (parity).
- **Full page ("Changes in this reply"):**
  - `LazyColumn` rows in **one** horizontal scroll container, with the content width computed once: `gutter + monoAdvance × maxLineLength`, measured with `TextMeasurer` on "0".
  - `requiredWidth(contentWidth)` on the list, because a lazy list given infinite width would otherwise size by its visible rows and jitter.
  - The line-number gutter stays put by countering the scroll in the layout phase: `Modifier.offset { IntOffset(hScroll.value, 0) }`.
  - Split view from the expanded width class: two such columns sharing one vertical state.
- Always LTR: `CompositionLocalProvider(LocalLayoutDirection provides Ltr)` around code and diff, so horizontal scroll and gutters don't flip in Arabic (parity with `kit_bidi.dart`).
- **Line selection** (Comment / Add to prompt / Copy lines) is our own row-range selection with the action bar, not `SelectionContainer`, the same as today.
- **Word-level change emphasis** inside changed lines is a future option (a char diff on the paired -/+ lines). It is not parity; propose it to the owner rather than build it.

---

## 10. Unknowns and spikes (each one a small, bounded task)

1. **The 5,000-part fixture, turn-items vs block-items.**
   - Port `test/perf_chat_test.dart` (2 tests) and `chat_rebuild_budget_test.dart` (2).
   - Measure fling and stream frame overrun on the emulator, then the NX721J.
   - This decides §4.3.
2. **Reverse-layout insert at index 0.** Confirm that a new block at index 0 stays hidden without `requestScrollToItem(0)`, and that the hop plus a grow-from-0 looks smooth (§4.2).
3. **Placement animation lag** on rows above a growing item with `animateItem(placementSpec = spring)` vs `null` (§4.6).
4. **Selection.**
   - Cross-block selection across 3 blocks and across a scroll of one screen.
   - The order of copied text in a reverse layout.
   - Selecting the live block on 1.12 vs 1.13.
   - Whether smart selection's "Open" bypasses our link policy.
5. **Pausable composition in prefetch on 1.13.** It was re-enabled, then disabled in 1.10.6, and the flag was removed in 1.13.0-alpha03. Find out what the final default is, and whether `LazyLayoutCacheWindow` composition spreads across frames on our phone.
6. **`DrawAnnotation`**: which release ships it. Watch the compose-ui notes, then move the fade and the find marks to it.
7. **The JetBrains streaming parser** on real agent output:
   - Does it keep a growing paragraph in the unstable tail correctly (setext headings, lazy list continuation, tables whose header arrives before the delimiter row)?
   - What is the re-parse cost with a 400-line open fence, before and after our open-fence wrapper?
8. **Highlighter choice** (benchmark in §8.2), and the R8-shrunk APK delta for kotlin-textmate plus jcodings.
9. **ARR on NX721J** (`hasArrSupport()`), and whether `preferredFrameRate(High)` changes the measured refresh during alpha-only fades.
10. **`preferredFrameRate` in CMP common.** It is in androidx `commonMain` [V]. Check that the JetBrains fork exports it for iOS and desktop; otherwise use expect/actual.
11. **CMP iOS / desktop text.** LineBreak strategies, `getPathForRange` and offscreen `DstOut` on Skia need a check when iOS starts. This is not an Android blocker.
12. **TalkBack order** with the Stream-style `zIndex` / `traversalIndex` fix on a reverse list (§4.7).

---

## 11. Recommendations for THIS app (build order and ownership)

These are sized for one agent per worktree. Shared kit parts are single-owner.

1. **`core:markdown`** (pure Kotlin, common; one owner).
   - A wrapper over `org.jetbrains:markdown:0.7.18` streaming and full parse.
   - The `Block` model, inline `AnnotatedString` builder, path-chip and picture scanners, agent-block detection, `proseForSpeech`, and CRLF stream normalisation.
   - **Tests first:** turn the Flutter Markdown tests into input → expected-`Block` fixtures. They are pure logic, so they belong in the logic batch even though they live in widget test files: `kit_markdown_test` (23), `kit_markdown_image_test` (25), `markdown_path_link_test` (9), `markdown_reading_test` (16), `markdown_agent_blocks_test` (5), `markdown_streaming_test` (3).
2. **`ui:kit` chat text parts** (one owner): `KitProse`, `KitHeading`, `KitQuote`, `KitListItem`, `KitMarkdownTable`, `KitPictureGroup`, `KitAgentBlock`, `KitStreamCaret`, `RevealState` + `freshFade` + `ReplyGrow`.
   - Each part declares a max width (reading cap 720 dp, as `KitLayout.readingWidth`) and an overflow rule.
   - Reading text scales fully; frame text is capped at 1.3×.
3. **`ui:kit` code and diff** (one owner): `CodeTokenizer` + SyntaxMP adapter, `KitCodeBlock` (12-line cap, `$` prompts, wrap rules, copy source), `DiffModel` port + `KitDiffView`. Run the highlighter spike first (§8.2).
4. **`feature:chat` transcript list** (single owner, like `chat_screen.dart` today):
   - the rows model, the `LazyColumn` per §4.8, follow logic, jump-to-message, Jump pill, `SelectionContainer` + `DisableSelection` map, TalkBack order, `ExternalLinkUriHandler`, and `preferredFrameRate` on the live block.
5. **Perf harness** (one owner, in parallel): Macrobenchmark module (stream, fling, open), Baseline Profile generator for those journeys, the unit counters (parses per update, blocks re-laid out per update), and JankStats in Diagnostics.
6. **Order:** (1) and the fixtures → spikes 1-3 on a throwaway branch → (2) and (3) → (4) → the emulator pass → an APK to the owner. Do not port the fade or grow visuals until spike 1 is green; they are polish on a list that must already scroll smoothly.

What this removes compared with today (owner's "judge by what it removes"):
- the `scrollable_positioned_list` dependency and the pinned-count workaround (key anchoring does it)
- the custom grow `RenderBox`
- the paint-only span copying in `_KitMdFresh` (a draw mask does it)
- the Dart `highlight` package

---

## 12. Sources (accessed 2026-11-01)

Primary: docs and source
- Compose foundation release notes (1.9 → 1.13.0-beta02, dates as listed): https://developer.android.com/jetpack/androidx/releases/compose-foundation
- Compose UI release notes (frame-rate rename in 1.9.0-beta01, TextMotion, LinkAnnotation): https://developer.android.com/jetpack/androidx/releases/compose-ui
- Compose runtime release notes (`computedStateOf`, pausable composition fixes): https://developer.android.com/jetpack/androidx/releases/compose-runtime
- Lists and grids guide (keys, contentType, animateItem, 0-px items; updated 2026-10-28): https://developer.android.com/develop/ui/compose/lists
- Performance best practices (keys, defer reads, backwards writes, `computedStateOf`; updated 2026-10-22): https://developer.android.com/develop/ui/compose/performance/bestpractices
- Compose phases (updated 2026-06-13): https://developer.android.com/develop/ui/compose/performance/phases
- Strong skipping (updated 2026-10-20): https://developer.android.com/develop/ui/compose/performance/stability/strongskipping
- Style paragraph / LineBreak (updated 2026-10-28): https://developer.android.com/develop/ui/compose/text/style-paragraph
- Text user interactions / SelectionContainer (updated 2026-10-28): https://developer.android.com/develop/ui/compose/text/user-interactions
- `TextView.setBreakStrategy` ("dancing" rationale): https://developer.android.com/reference/android/widget/TextView
- Adaptive refresh rate guide (updated 2026-10-23): https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate
- `preferredFrameRate` reference: https://developer.android.com/reference/kotlin/androidx/compose/ui/preferredFrameRate.modifier
- Baseline Profiles overview (about 30% faster code; updated 2026-10-28): https://developer.android.com/topic/performance/baselineprofiles/overview
- androidx-main source, read raw on 2026-11-01:
  - `compose/ui/ui-text/.../AndroidParagraph.android.kt` and `LayoutCompat.android.kt` (default SIMPLE)
  - `material3/tokens/TypographyTokens.kt` (no lineBreak)
  - `foundation/text/modifiers/TextAnnotatedStringNode.kt` (invalidation)
  - `ui-text/AnnotatedString.kt` and `DrawAnnotation.kt`
  - `ui-text/LinkAnnotation.kt`
  - `foundation/lazy/LazyListState.kt` (`requestScrollToItem` KDoc)
  - `foundation/lazy/layout/LazyLayoutCacheWindow.kt`, `foundation/lazy/LazyDsl.kt` (`cacheWindow` param)
  - `foundation/text/selection/SelectionContainer.kt` (lazy pinning KDoc; commit history)
  - `foundation/ComposeFoundationFlags.kt` (smart selection default)
  - `ui/FrameRate.kt` (commonMain)
  - Mirror: https://github.com/androidx/androidx
- Jetchat sample `Conversation.kt` (`reverseLayout = true`): https://github.com/android/compose-samples
- Compose Multiplatform releases (1.12.2 on 2026-10-15; 1.13.0-beta01 on 2026-10-23): https://github.com/JetBrains/compose-multiplatform/releases
- Maven metadata for Compose BOM, Material3, foundation and CMP: https://dl.google.com/android/maven2/ and https://repo1.maven.org/maven2/org/jetbrains/compose/

Markdown
- JetBrains markdown (versions; 0.7.18 on 2026-10-15): https://repo1.maven.org/maven2/org/jetbrains/markdown/
  - README with the CRLF streaming rule: https://github.com/JetBrains/markdown
  - `StreamingMarkdownFile.kt` (history from 2026-05-23): https://github.com/JetBrains/markdown/blob/master/src/commonMain/kotlin/org/intellij/markdown/parser/StreamingMarkdownFile.kt
- mikepenz multiplatform-markdown-renderer:
  - README: https://github.com/mikepenz/multiplatform-markdown-renderer
  - releases 0.40.2 → 0.46.0: https://github.com/mikepenz/multiplatform-markdown-renderer/releases
  - Maven versions: https://repo1.maven.org/maven2/com/mikepenz/multiplatform-markdown-renderer/
  - source of `StreamingMarkdownState.kt` and `LazyMarkdown.kt` (develop branch)
- compose-richtext (1.0.0-alpha05, 2026-06-07): https://repo1.maven.org/maven2/com/halilibo/compose-richtext/richtext-commonmark/ ; status [S]: https://klibs.io/project/halilozercan/compose-richtext
- huarangmeng/Markdown (1.6.2, 2026-10-23): https://github.com/huarangmeng/Markdown , https://repo1.maven.org/maven2/io/github/huarangmeng/markdown-renderer/
- adamglin0/compose-markdown: https://github.com/adamglin0/compose-markdown (Maven coordinates not verified)

Highlighting and diff
- SyntaxMP: https://github.com/GallatinApps/SyntaxMP ; Maven (0.3.0, 2026-06-04): https://repo1.maven.org/maven2/com/gallatinapps/syntaxmp/
- Highlights: https://github.com/SnipMeDev/Highlights ; Maven (1.1.0, 2025-09-02): https://repo1.maven.org/maven2/dev/snipme/highlights/
- kotlin-textmate: https://github.com/ivan-magda/kotlin-textmate ; Maven (0.3.0, 2026-10-06): https://repo1.maven.org/maven2/io/github/ivan-magda/kotlin-textmate-core/
  - jcodings and joni jar sizes: https://repo1.maven.org/maven2/org/jruby/
- KTreeSitter [S]: https://tree-sitter.github.io/kotlin-tree-sitter , https://github.com/Enaium/tree-sitter-languages-kmp
- compose-highlight (WebView) [S]: https://github.com/hossain-khan/android-compose-highlight
- sora-editor TextMate [S]: https://project-sora.github.io/sora-editor-docs/guide/using-language
- kotlin-multiplatform-diff: https://github.com/petertrr/kotlin-multiplatform-diff ; Maven (1.4.0, 2026-09-20): https://repo1.maven.org/maven2/io/github/petertrr/kotlin-multiplatform-diff/

Secondary
- Stream Chat Android PR #6536 (TalkBack order with reverse layout; merged 2026-07-03): https://github.com/GetStream/stream-chat-android/pull/6536
- Kotlin Slack on `BasicText(color = ColorProducer)`: https://slack-chats.kotlinlang.org/t/13086071/basictext-now-has-a-colorproducer-parameter-i-think-i-get-th
- Kotlin Slack on selection lost in lazy lists (historical; superseded by the pinning KDoc): https://slack-chats.kotlinlang.org/t/5048699/i-ve-got-a-lazy-column-of-text-lines-wrapped-with-a-selectio
- Hackernoon on animateItem and streaming size changes: https://hackernoon.com/why-jetpack-compose-needs-a-better-tool-for-small-animated-lists
