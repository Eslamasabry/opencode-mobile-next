# Markdown engine evaluation (native `core/markdown`)

Date 2026-11-06. Scratch: `/home/eslam/Storage2/wt/md-eval/` (harness `harness/Harness.kt`, `compile.sh`, `compare.py`, `cases/`, `probes/`, `scan.py`). Compiled on the VPS with kotlin-compiler-embeddable 2.4.21 straight from the Gradle cache (no Gradle). App code untouched.

## 0. What `markdown = "0.7.18"` is
`gradle/libs.versions.toml:36` is a version only. It is the JetBrains `org.jetbrains:markdown` parser version. There is no library alias that uses it, and nothing in the repo references it. `core/markdown/build.gradle.kts` says so: the engine is a pure-Kotlin port of Flutter's `kit_markdown.dart`, and its only dependencies are coroutines and collections-immutable. **The catalog entry is dead and can be deleted** (or kept for a future spike).

## 1. Conformance (CommonMark 0.31.2 spec.json, 652 cases; cmark-gfm extension examples, 24 cases)
Our block tree was rendered to HTML-ish output. Two metrics:
- **strict**: normalised HTML equal. Entity decoding and raw HTML pass-through are expected to fail by design.
- **skeleton**: the block-level tags (h1-6, blockquote, ul/ol/li, pre, hr, table) match.

Totals: CommonMark strict 183/652 (28%), skeleton 524/652 (80%, inflated because inline-only sections trivially pass). GFM strict 6/24, skeleton 21/24.
Raw 28% is expected and **not a useful quality number**: the engine is a line-based chat-reply renderer, not a CommonMark implementation. Judge it by the failure classes in section 1.2 and the chat probes in 1.3.

### 1.1 Pass counts per section
| Source | Section | Cases | HTML-equal (strict) | Skeleton (block shape) |
|---|---|---|---|---|
| cm | Tabs | 11 | 1 | 2 |
| cm | Backslash escapes | 13 | 2 | 12 |
| cm | Entity and numeric character references | 17 | 4 | 16 |
| cm | Precedence | 1 | 1 | 1 |
| cm | Thematic breaks | 19 | 10 | 10 |
| cm | ATX headings | 18 | 8 | 14 |
| cm | Setext headings | 27 | 7 | 9 |
| cm | Indented code blocks | 12 | 1 | 2 |
| cm | Fenced code blocks | 29 | 23 | 26 |
| cm | HTML blocks | 44 | 0 | 35 |
| cm | Link reference definitions | 27 | 5 | 25 |
| cm | Paragraphs | 8 | 7 | 7 |
| cm | Blank lines | 1 | 1 | 1 |
| cm | Block quotes | 25 | 8 | 12 |
| cm | List items | 48 | 14 | 19 |
| cm | Lists | 26 | 3 | 7 |
| cm | Inlines | 1 | 1 | 1 |
| cm | Code spans | 22 | 8 | 22 |
| cm | Emphasis and strong emphasis | 132 | 43 | 132 |
| cm | Links | 90 | 14 | 90 |
| cm | Images | 22 | 1 | 22 |
| cm | Autolinks | 19 | 4 | 19 |
| cm | Raw HTML | 20 | 6 | 20 |
| cm | Hard line breaks | 15 | 6 | 15 |
| cm | Soft line breaks | 2 | 2 | 2 |
| cm | Textual content | 3 | 3 | 3 |
| gfm | Tables | 8 | 4 | 6 |
| gfm | Task list items | 2 | 0 | 2 |
| gfm | Strikethrough | 2 | 2 | 2 |
| gfm | Autolinks | 11 | 0 | 11 |
| gfm | Disallowed Raw HTML | 1 | 0 | 0 |

### 1.2 Failure classes (hand-judged), most relevant to chat first
1. **Nested bullets under a numbered list break the list.** `1. Step` followed by `   - sub a` ends the ordered list; the sub-bullets render at depth 0 (a separate list), and `2.` restarts as its own list at the right number. Probe p05. Very common in agent replies.
2. **Fenced code inside a list item** is pulled out to top level. The numbering survives (`start=2`), but the code is not indented under its step. Probe p06 and spec cm-0318/0324.
3. **Blockquote content is flat text.** Lists, headings and fences inside `>` print literally (`- a`, `# Foo`, ```` ```js ````). Probe p15.
4. **Multi-paragraph and loose list items.** Continuation paragraphs after a blank line lose their list. A loose list becomes N one-item lists, but numbering is kept. Empty items (`-`, `2.`) become paragraphs.
5. **Emphasis flanking rules are missing.** `2 * 3 * 4 = 24` and `a*b*c` turn into italics (p01). `__init__.py` renders **init**.py (p02). `_italic_` single-underscore is not supported at all. Mixed nesting `*a **b** c*` drops the bold (p31). Of the 132 emphasis cases 43 pass.
6. **Code spans with double backticks** are wrong (p03).
7. **Bare URLs and link targets with parentheses are truncated.** `https://en.wikipedia.org/wiki/Foo_(bar)` stops at `Foo`, and `[x](...Foo_(bar))` is cut and leaves a stray `)` (p04). Also GFM `www.` autolinks are not linked, and `<https://x>` shows the angle brackets.
8. **Tables.**
   - A header whose delimiter row uses 1-2 dashes (`|-|--|`) is not a table. The spec allows 1+ dashes; we require 3+.
   - A body row with fewer than 2 cells ends the table; ragged rows are not padded.
   - A table with no leading pipe whose delimiter is `:-:` fails (same dash rule).
   - A pipe inside code in a cell (`\|`) works. `<br>` in a cell is shown literally.
9. **Setext headings** (`Title\n=====`) are not headings. `text\n---` gives paragraph + rule; this is arguably what the author meant.
10. **Indented code blocks** (4 spaces) render as a paragraph with leading spaces.
11. **Backslash escapes** (`\*`) are not honoured; the backslash stays and the `*` still triggers emphasis (p12). **Entities** (`&amp;`) show as the literal text, not decoded (p13). **Hard line breaks** (two spaces or `\`) are not breaks.
12. **Task lists** `- [ ]` / `- [x]` show literally as `[ ] todo`. (Flutter does the same; the checklist fence is the supported form.)
13. **Reference links** `[t][1]`, link titles, `<details>`, inline HTML (`<kbd>`, `<sub>`, `<br>`) are shown as literal text. This is the intended safe default for HTML.
14. Passing well: fenced code (23/29, with fails only for exotic cases), thematic breaks, ATX headings (8/18, fails are mostly edge cases), paragraphs, strikethrough, bare http(s) autolinks, RTL/Arabic inline text (p35), 4-backtick fences, images (p24).
No input threw an exception (676 of 676 ran).

### 1.3 Probe results (35 realistic chat snippets in `probes/*.md`, outputs in `probes/*.ours`)
OK: p23 fences, p24 images, p27 bold/code/link in a list item, p30 `*` bullets with nesting, p35 Arabic, p28 numbered list after a paragraph, p11 pipe-in-code table cell.
Wrong or unsupported: p01-p08, p10, p12-p22, p25, p29-p33 (details above).

## 2. Flutter parity
Flutter (`flutter-ref/lib/ui/kit/chat/kit_markdown*.dart`, 3,157 lines) has **no markdown package** in `pubspec.yaml`. It is the same hand-rolled parser (`_splitBlocks`, the `_spansOf` regexes, a pipe-table scanner, pictures, path chips, agent fences). Its only related dependency is `highlight ^0.7.0` for code colouring.
A grep of `lib/ui/kit` for mermaid, latex, katex, math, footnote, admonition, `[!`, `<details>` and task-list handling found nothing in the renderer.

| Feature | Flutter | Native |
|---|---|---|
| LaTeX/math | no, shown as text | same |
| Mermaid/diagrams | no, shown as a code block | same |
| Footnotes | no | same |
| Admonitions `> [!NOTE]` | no, plain quote with the `[!NOTE]` text | same |
| Task lists | no (checklist fence only) | same |
| HTML | no; only `<!--` comment blocks are hidden | same (`blankHtmlComments`) |
| Syntax highlighting | `highlight` package | `CodeTokenizer` / `SyntaxMpCodeTokenizer` (compare the language list separately) |

**There is no parity gap to close.** The native parser was ported 1:1 and carries the same limitations. If a parity test is wanted, the cases in `cases/` and `probes/` can run through both engines.

## 3. What agents actually send
Source: `~/.local/share/opencode/opencode.db`, 19,683 assistant text parts (17.3 M characters), mostly one project's coding chats. Regex counts are an upper bound (some hits are inside code fences).

| Construct | Parts | Share |
|---|---|---|
| GFM table | 879 | 4.47% |
| Bare URL | 323 | 1.64% |
| Hard break (two spaces) | 195 | 0.99% |
| List item followed by a fenced block | 97 | 0.49% |
| Nested ordered list (indented) | 70 | 0.36% |
| `**a *b* c**` nested emphasis | 30 | 0.15% |
| Blockquote | 56 | 0.28% |
| HTML tag | 55 | 0.28% |
| Strikethrough | 3 | 0.02% |
| Task list `- [ ]` | 6 | 0.03% |
| `<details>` | 12 | 0.06% (all in prose about HTML, spot-checked) |
| `$$ ... $$` | 0 | 0% |
| `\( \)` / `\[ \]` | 1 / 1 | both false hits inside shell code |
| ```` ```mermaid ```` | 0 | 0% |
| `[^n]` footnote definition | 0 | 0% (ref hits are in code) |
| `> [!NOTE]` admonition | 0 | 0% |
| `![img](...)` | 0 | 0% (images arrive as file parts) |
| `$x$` inline | 32 | 0.16% (false positives: shell and regex text, no maths seen) |

Caveats: one user and one set of coding projects; maths or diagrams may show up for other users. Even so, tables, lists, links and code carry almost everything.

## 4. Libraries and recommendation per gap
Facts were read from the READMEs on 2026-11-06.

| Library | License | KMP | Notes |
|---|---|---|---|
| mikepenz multiplatform-markdown-renderer | Apache-2.0 | Android, iOS, Desktop, Wasm/JS | JetBrains `markdown` AST; `MarkdownComponents` maps each node to a composable; has a streaming state that re-parses only the tail; GFM tables, tasks, alerts. No maths. |
| huarangmeng/Markdown | MIT | Android, iOS, Desktop, Wasm/JS | Claims 652/652 CommonMark, LaTeX (`$`, `$$`), Mermaid/PlantUML/DOT, streaming flag, admonitions, directives. Mainline 1.7.0 pins Kotlin 2.4.21, Compose MP 1.12.2, compileSdk 37 (our Kotlin is 2.4.21). Bundles Coil3 and Ktor3; the app has Coil 3.6.3 already. Conformance claim is self-reported; size not documented. |
| Enriched Markdown, Markwon | - | Android Views / non-KMP | Both draw through Android `TextView`/Spans. They cannot go through the kit-only rule. Skip. |

The kit-only rule says every visible part comes from `lib/ui/kit` (here `ui/kit-chat`). So a library may only supply **parsing or layout data**. It may not draw by itself. Neither mikepenz nor huarangmeng can be used as an off-the-shelf renderer without breaking the rule.

### Per gap
1. **Nested lists, code and quotes inside list items and blockquotes, loose lists (classes 1-4).** Keep our engine and fix it ourselves. The fix is to make `BlockParser.list` and the quote branch recursive (a container pass that strips the indent or `>` and re-runs the block parser on the inner lines). It must keep the `[start, end)` offsets, per-line re-parse and instance reuse that `StreamingMarkdown` relies on, which no library gives us. Needs new `Block` shapes plus kit rendering (indent rail, item content).
   Do this first. It is the most visible chat defect.
2. **Emphasis, escapes, entities, double-backtick code, URL parentheses, `_x_` (classes 5-7, 11).** Keep our engine and replace the one big alternation regex in `InlineParser` with a small delimiter-run scanner (CommonMark algorithm 6.2: flanking rules, `*` and `_` rules, backslash escapes, entity decode, balanced parentheses in URLs and bare URLs). About 200 lines, pure Kotlin, testable with the spec cases in `cases/`. A library parser would be the alternative, but it would replace the block layer too.
3. **Tables (class 8).** Keep it. Accept 1-dash delimiters, pad ragged rows, and let a one-cell row continue the table. Small change in `TableScanner` and `BlockParser.table`.
4. **Setext headings, indented code, hard breaks (9, 10, 11).** Add ourselves. Setext and indented code are small block rules. Hard breaks need an inline `Break` span.
5. **Task lists (12).** Add ourselves. Recognise `[ ]` / `[x]` at the start of a list item and add `ListItem.checked: Boolean?`; draw with the existing kit task mark (`KitTaskMark` exists in Flutter's kit). 0.03% of replies, so low priority, but it is cheap and users expect it.
6. **Maths (LaTeX).** Zero use today. **Do nothing now.** If it becomes wanted: huarangmeng's LaTeX engine is the only KMP option listed. Use it for the one block type only, behind a kit part (`KitMathBlock`) that takes the formula string. Pull in just its LaTeX artifact if it ships as a separate module, not the whole renderer. Check size, the KaTeX fonts and Android API level first. Do not adopt it for the rest of the markdown.
7. **Mermaid/diagrams.** Zero use. **Do nothing.** Today a ```` ```mermaid ```` fence is shown as a code block, as in Flutter, and that is acceptable. A Mermaid engine would pull in Ktor and a large renderer; a future option is a "Show diagram" button that opens the source in a browser-based viewer via `openExternalLink`.
8. **Footnotes, admonitions (`> [!NOTE]`).** Zero use. Admonitions are cheap: once quotes are recursive, a first line `[!NOTE]` could pick a kit callout style. Do that only if it appears. Footnotes: skip.
9. **HTML (`<details>`, `<kbd>`, `<br>`).** Keep showing it as text (safe). If wanted later, support `<br>` in table cells and `<details>` as a collapsible kit part; both are small.
10. **Full swap to a library.** Not recommended. Reasons:
    - The engine's value is the streaming tail re-parse with stable instances and offsets, the unfinished-syntax closing, path chips, pictures and agent fences, none of which a library has.
    - mikepenz is the closest fit (its streaming state is similar). Its parser gives full CommonMark nesting. Adopting only its parser would cost us the line/offset model and an Apache dependency (JetBrains markdown 0.7.18 is already in the catalog and could be used as a reference or as a test oracle).
    - A cheaper use of that catalog entry: use JetBrains `markdown` (JVM tests only) as an **oracle** in a conformance test, instead of shipping it.
    - huarangmeng is too large a bet: it is a full renderer with its own theming, so it cannot meet kit-only.

### Suggested order
1. Inline scanner rewrite (class 5-7, 11): fixes wrong output on ordinary text such as `a*b*c` and `__init__.py`.
2. Recursive lists and quotes (1-4).
3. Table leniency (8), task lists (12), setext and indented code (9, 10).
4. Add a conformance ratchet test that runs `cases/` (a checked-in subset) so the pass count only goes up.
5. Remove or document the dead `markdown = "0.7.18"` catalog entry.
