# Unit S2-KCB: kit-chat renderers (report)

Branch `native/kitchat-2026-11-01`, created from `claude/kitchat-markdown-diff-rendering-562hui` at `6b9a2b21` (the
M0 tip). Date: 2026-11-01. Write set: `native/ui/kit-chat/**` (renderers) plus this report.

Plan sections read: `plan/01` (binding), `plan/15` (roadmap rows S2-KCA, S2-KCB, S2-MD), `plan/09` §0.3–§1, §3.8,
§7.8, §8, §9.1–§9.7, `plan/08` §5.1, `native/AGENTS.md`, `docs/native/slice0/P3-transcript.md`, `P7-kit.md`.

**Finish line (this brief):** our own Markdown and diff drawing, code colours via SyntaxMP behind a swap seam, block keys
stable while streaming, text selection across blocks; goldens plus streaming-append tests from the P3 fixtures.
**Non-goal:** the parser itself (`:core:markdown`, S2-MD), pictures and agent blocks, the composer, the request engine.

**Status:** implemented; verified locally on the desktop JVM and under Robolectric; compiled for Android and desktop
with warnings as errors; committed and pushed to `native/kitchat-2026-11-01`. There is no PR, CI run, device run or
release. The S2-MD parser is not wired: renderers take a kit-local block model that mirrors its planned API.

---

## 1. What was built

Package root `io.github.eslamasabry.oc.ui.kitchat` (the module's existing namespace; plan 09 §0.3 writes
`…ui.kit.chat.*`, see D6). The P3 transcript and stream files were not changed apart from one import fix (D1).

### 1.1 `markdown/` — Markdown block renderers

| File | Content |
|---|---|
| `KitMdModel.kt` | The kit-local render model (C5). `KitMdBlock` (Prose, Heading, Quote, ListItem with checkbox, Code with `open`, Table with aligns, Rule) with parser `key`s, `KitInlines`/`KitSpan`/`KitSpanKind` (Bold, Italic, Strike, Code, Link(url), Path(path, line)). One-to-one with plan 09 §9.1 `Block`/`Inlines`/`InlineSpan`, so `:feature:chat` maps `:core:markdown` output with a field copy. |
| `InlineAnnotated.kt` | `inlineAnnotated(inlines, style, sink, confirmed)` → `AnnotatedString`. **Total**: spans out of range, reversed, empty or overlapping are clamped or skipped. Inline code and paths are wrapped in FSI…PDI (R15). Links become `LinkAnnotation.Clickable` whose listener hands the address to the caller; the kit never opens anything (R17). A path span becomes clickable only when the caller confirmed the file (B-CHAT-064). |
| `KitMarkdown.kt` | `KitMarkdown(blocks, actions, live, reveal)` (one reply in one item, each block `key(block.key)`), `KitMarkdownBlock` (one block; the transcript puts each block in its own lazy row), and the 06 §7.8 parts `KitMarkdownProse`, `KitMarkdownHeading` (heading semantics; h1–h2 Headline, h3–h6 RowTitle), `KitMarkdownListItem` (own numbers, depth indent, done checklist items muted not struck), `KitMarkdownQuote` (rail on the start side), `KitMarkdownTable` (measured columns ≤ 320 dp in one horizontal scroller), `KitMarkdownRule`. `KitMarkdownSelection` = one `SelectionContainer` with the context menu filtered to Copy and Select all (plan 09 §7.8 layer 1). The live block is in `DisableSelection`. `Modifier.kitStreamCaret` draws a 2 dp bar after the last shown character and takes no width (B-CHAT-060). Streamed text reuses P3's `RevealRegistry` clock, `kitFreshFade` and `growOnly`. The `AnnotatedString` is built in `remember(inlines, style, confirmedPaths)`, once per block instance; link lambdas go through `rememberUpdatedState`, so a new `actions` object never rebuilds text. |

### 1.2 `code/` — code blocks and the colour seam

| File | Content |
|---|---|
| `CodeTokenizer.kt` | `CodeRole`, `CodeToken`, `fun interface CodeTokenizer` (the swap seam), `PlainCodeTokenizer` (last fallback), `CachedCodeTokenizer` (plan 09 §9.2: plain for unknown languages and sources over 20,000 chars; LRU of 48 entries keyed by language + source, independent of the theme; lock-free atomic persistent map, safe from any thread), `LocalKitCodeTokenizer` (default: SyntaxMP behind the cache). |
| `SyntaxMpCodeTokenizer.kt` | The only file that imports SyntaxMP 0.3.0. It folds SyntaxMP's role tree onto `CodeRole`, including the diff addition/deletion lines. It merges adjacent same-role spans, clamps to the source and never throws. |
| `CodeLanguage.kt` | 31 grammars plus `Plain`. The whole Flutter alias table (`kit_code_block.dart:46`); the plan's mappings for missing grammars (scss → css, svg → xml, console → bash, patch → diff, gradle → kotlin); `of(info)` reads the first word of a fence info string; `forPath(path)` (extension, or `Dockerfile`/`Makefile`). |
| `CodeText.kt` | Ports of `kitCodeBreakable` (zero-width break chances after punctuation, never inside words), `stripCodeBreaks` (copying a selection copies the source), and `kitCommandLineStarts` (`$` only where a command starts: heredoc bodies, trailing `\` `|` `&&` `(`, open quotes or brackets and `if`/`for` bodies continue the command). |
| `KitCodeBlock.kt` | `KitCodeBlock(source, kind: Code/Command/Output, language, open, maxLines = 12, wrap, copyLabel/onCopy, moreLabel/onMore)`. It **displays** `LocalKitRedactor.redact(source)` and copies the redacted source, never the prompts, the cap or the break chances. Colour is worked out on `LocalKitChatWork` (the app provides its `AppDispatchers` context; rule 6). An open fence is never coloured. The block is always LTR, in the Mono role, scrolls sideways unless `wrap`, has the `$` gutter in `DisableSelection`, Copy at the end of the first line, and "Open full output" through `moreLabel(hidden)`. `KitCodeColors` maps roles to `KitColors` (codeKeyword/String/Type, success/attention for diff lines). |

### 1.3 `diff/` — diff model and inline view (port of `kit_diff_model.dart`)

`DiffModel.parseFilePatch`, `parsePatch` (one or many files: `diff --git`, `---`/`+++`, `/dev/null`, renames, modes;
hunk counts decide where a hunk ends), `fromBeforeAfter` (LCS with Flutter's 1,000,000-cell limit and its one-block
fallback that keeps prefix and suffix), `foldContext` (3 context lines), `displayRows(file, maxLines)` (lines, gaps
with their position, hunk headers with old/new ranges, and the cap). `DiffFile` facts are computed once per instance.
`\ No newline at end of file` is kept as a note on the line above. Every function is total.
`KitDiffView(file, maxLines = 12, hunkLabel, gapLabel)` is the inline tool-step form: a plain `Column`, always LTR,
mono, number gutter, `+`/`−` sign column beside a low-alpha tint (never colour alone), redacted text, one sideways
scroll. Labels come from the caller.

### 1.4 `tool/` and `request/` — tool step rows and the permission card shell

- `KitToolView` / `KitToolKind` / `KitToolStatus` (kit-local models, C5).
- `KitToolRow(view, opened, onToggle, body)`:
  - layout: kind glyph (vector, no icon library), title, mono LTR subject, detail, status, duration, `+n −n` counts as text;
  - a stopped step reads neutral and a failed one uses no danger red;
  - the body slot is drawn only when opened, and the header is one tappable row with expand/collapse semantics (only when there is a body);
  - the subject is redacted.
- `KitCommandBox`: `$` outside the command, output capped at 12 lines, exit label, all redacted.
- `KitPermissionCard(title, summary, subject, allowOnceLabel, alwaysLabel?, denyLabel, …, enabled, disabledReason)`:
  - the decide-form shell on the attention surface: reading title and summary, a mono LTR redacted subject that scrolls inside the card;
  - answers as visible buttons, with Deny never in the accent colour;
  - width ≤ 700 dp and height ≤ 45 % of the window.

### 1.5 Shared

- `KitChatLocals.kt`:
  - `LocalKitRedactor`: no default; a mono part drawn without it fails loudly;
  - `LocalKitChatWork`: the work context; empty means the composition's own context.
- `build.gradle.kts`: `:core:security` (an allowed edge), `kotlinx-collections-immutable`, SyntaxMP, and the Roborazzi plugin plus host-test dependencies, copied from `:ui:kit`'s P7 setup.

### 1.6 Tests and fixtures

- `commonTest`:
  - `ChatTestTheme`: the real `KitTheme` plus a redactor and a fake platform;
  - `ChatScenes`: shared by the desktop tests and the goldens, en and ar sample copy;
  - `markdown/TestMarkdown.kt`: a **test-only** streaming block producer, see D2;
  - `markdown/P3Fixtures.kt`: the P3 `perf:stream-6000` generator, same seed and the same delta cuts as `quality/…/p3/Stream6000.kt`, plus an SSE text-delta reader.
- `desktopTest/resources/oc2/events.sse`: a copy of the P3 OC2 recording. It was checked for addresses and emails; it has none.
- Goldens: `src/screenshots/<Part>/<scene>-<light|dark>-<en|ar>.png`.
  - Six scenes × four variants = 24 PNGs, 1.9 MB.
  - Scenes: KitMarkdown reply and reply-live, KitCodeBlock kinds, KitToolRow steps, KitDiffView edit-step, KitPermissionCard ask.

---

## 2. Rows covered (test names)

| Row | Tests |
|---|---|
| B-CHAT-055 | `CodeTextTest`, 10 cases: separate commands; heredoc body and terminator; `<<-` and here-string; trailing `\` `|` `&&`; open quote or bracket; for/if bodies; a comment or quoted `<<` opens nothing; break chances after punctuation never inside a word; runs and closers stay whole; copy strips the break chances. `KitCodeBlockTest`: a heredoc command shows one prompt per command. |
| B-CHAT-056 | `CodeTextTest`: fence hints map to grammars, every Flutter alias resolves, unknown stays plain. `CodeTokenizerTest`: SyntaxMP colours Kotlin, shell, JSON and TS; a diff colours added and removed lines; unknown language and oversize fall back plain; cache of 48, oldest out; tokens total over 2,000 random inputs. `KitCodeBlockTest`: copy hands over the source, never the prompts or the cap; output over 12 lines shows 12 and says how many more; an open fence is never coloured and a closed one is tokenised once. |
| B-CHAT-058 | `DiffModelTest` (14): counts, numbers and hunks; preambles dropped; no-newline note; fold keeps 3 context lines; after-only = added and before-only = deleted; separate changes found; the cell-limit fallback; hunk ranges; display rows; the cap; multi-file patch; CRLF; malformed headers; 1,500 random patches never throw. `KitDiffViewTest` (4): draws ≤ maxLines; caller's words; secret never drawn; LTR under RTL. Not covered: split mode, selection, change navigation (§5). |
| B-CHAT-060 | `KitMarkdownTest`: a streaming reply shows a caret after its last words, never after code and never once settled. The text-to-speech half (`proseForSpeech`) is S2-MD's. |
| B-CHAT-062 | `KitToolRowTest` (7): title/subject/detail/status/duration as given; body only when opened, tap asks to toggle; stopped label and counts as text; subject LTR in Arabic; command box keeps `$` out of the command and caps output at 12; output that fits shows whole. Not covered: the timeline rail and step light (S3-WORK). |
| B-CHAT-064 | `InlineAnnotatedTest`: a path chip is a link only once the file is confirmed, with the line kept apart. The path *scanner* is S2-MD's; the test producer applies the row's rule. |
| B-CHAT-096 (renderer half) | `MarkdownStreamingTest`: **P3 stream-6000**, the whole 35,379-character reply in 5,435 deltas, published every 25 deltas: settled keys never move, settled block instances are kept, and inline builds ≤ text blocks + 2 × publishes (settled text is never rebuilt). The same stream in a reversed keyed `LazyColumn` (the transcript shape), 3,000 deltas: builds ≤ 2 × publishes + 40, nothing left revealing. **OC2 recording**: the real `events.sse` text deltas give two settled "pong." replies with no caret. |
| B-CHAT-123 (shell) | `KitPermissionCardTest` (6): the ask shows its words, subject and answers, and each fires once; subject LTR in Arabic; without Always two answers and Deny never accent (pixel check); a long subject scrolls inside a card ≤ 45 % of the window and ≤ 700 dp; a card that cannot answer says why and sends nothing. The wording (B-CHAT-122) is the engine's. |
| B-SEC-094 (display) | Secrets registered with a real `Redactor` never reach the semantics tree or the copy: `KitCodeBlockTest`, `KitDiffViewTest`, `KitToolRowTest`, `KitPermissionCardTest`. |
| B-SEC-064 (kit half) | `InlineAnnotatedTest`: a link is handed to the caller, never opened by the kit. The policy itself is `ExternalLinkPolicy`'s. |
| B-KIT-001 / R15 | `InlineAnnotatedTest`: inline code and paths are isolated in RTL prose. Goldens in `ar` show code, commands, diffs and paths LTR. |
| Plan 09 §9.1 totality | `InlineAnnotatedTest`: the inline builder is total over 10,000 random span sets, plus every line of the stream-6000 reply. |

Totals: 67 desktop tests (63 new, plus P3's 4 `RevealTest`) and 24 golden captures, 0 failures.

---

## 3. Commands and results

Environment: Android SDK from `commandlinetools-linux-16111833_latest.zip` in `/opt/android-sdk`.
- Packages: `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`; licences accepted.
- JDK: Gradle on JDK 21; `openjdk-17-jdk-headless` installed for the toolchains.
- `BASH_DEFAULT_TIMEOUT_MS` raised in `~/.claude/settings.json`.
- As in P1/P3, Maven Central answered HTTP 429 through the proxy. A container-only `~/.gradle/init.d/central-mirror.gradle.kts`, not in the repo, puts Google's Central mirror first. The checked-in settings are unchanged.

All commands ran from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/opt/android-sdk`.
Only this module was built.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :ui:kit-chat:desktopTest` on the base | **Compile failure**: `Unresolved reference 'MotionTokens'` in `TranscriptList.kt` (D1). After the one-line fix: BUILD SUCCESSFUL, 4/4. |
| 2 | `./gradlew :ui:kit-chat:desktopTest :ui:kit-chat:compileAndroidMain` after the build file | BUILD SUCCESSFUL. SyntaxMP 0.3.0 resolves for desktop and Android. |
| 3 | `./gradlew :ui:kit-chat:desktopTest --tests '*code*'` | 16, then 21 tests, 0 failures. |
| 4 | `./gradlew :ui:kit-chat:desktopTest` (markdown first run) | 3 failures, all in test code: a test-parser nesting quirk, heading semantics not on the text node (fixed in the part with `mergeDescendants`), and the OC2 recording holding two replies. All fixed; then green. |
| 5 | `./gradlew :ui:kit-chat:recordRoborazziAndroidHostTest` | 24 goldens recorded. Each was looked at by eye (§4). |
| 6 | `./gradlew :ui:kit-chat:compileAndroidMain :ui:kit-chat:compileKotlinDesktop :ui:kit-chat:compileTestKotlinDesktop :ui:kit-chat:desktopTest :ui:kit-chat:verifyRoborazziAndroidHostTest -Poc.werror=true` | **BUILD SUCCESSFUL.** First try: `-Werror` failed on 3 deprecations (`PersistentMap.remove/put` → `removing/putting`) and 3 needless `!!`; all fixed. Then desktopTest 67/67 and testAndroidHostTest 24/24 verified. |

The two parallel slices, diff and tool/request, ran their own red-then-green loops in separate worktrees. Their
commits were cherry-picked here and re-run in commands 6.
- The diff slice went red on 249 unresolved references, then green 18/18.
- The tool/request slice went red on unresolved references, then green 13/13. A mutation check turned redaction off and both B-SEC-094 tests failed.

Not run: `:quality:test`, other modules and `build`. The brief limits this unit to its own module, and M0 is being
reconciled in parallel.

---

## 4. Goldens, by eye

- **Markdown**: heading, inline bold/italic/link/confirmed path chip, ordered list starting at 1 with a nested bullet, quote rail, table, Kotlin fence.
  - Light: Kotlin keywords, strings, comments and numbers are coloured.
  - Dark Arabic: the paragraph flows RTL with `lib/main.dart:42` kept LTR, the table columns are mirrored, the numbers column is end-aligned, and the code stays LTR.
- **Code**: the Python block is coloured; the command shows `$` on two of five lines (heredoc body and continuation have none); the output is capped at 12 lines with "Show all, 4 more lines". Copy sits at the end of the first line.
- **Diff**: an edit step opened on its diff, folded to 3 context lines with "N unchanged lines" gaps, old/new number gutters, `−`/`+` signs beside the tints, LTR in Arabic.
- **Permission card**: attention surface; title, summary, mono subject; Deny (secondary) and Allow once (accent) in a row, Always allow below.

---

## 5. Deviations

| # | Deviation | Why |
|---|---|---|
| D1 | `transcript/TranscriptList.kt` (P3, S2-KCA's area) got a one-line fix: import `ui.design.motion.MotionTokens`, `LONG_MS`/`SHORT_MS` → `STANDARD`/`QUICK` (same values, 250/150). | The module did not compile on the M0 tip; `:ui:design` renamed the tokens after P3. Nothing else in the transcript/stream files changed. |
| D2 | **No JetBrains parser in this unit.** Renderers take `KitMdBlock` (the 09 §9.1 shape). Streaming tests drive them with a test-only producer, `TestMarkdown.kt`, with the same contract as P3's `StreamingBlocks`. | `ExternalRules` allows `org.jetbrains:markdown` only in `:core:markdown`, which does not exist on this branch and is S2-MD's write set. Adding the library here fails configuration. The parser half of "keys stable while streaming" is S2-MD's to prove; this unit proves the renderer half. |
| D3 | Code blocks, the tokenizer seam and the diff live in `:ui:kit-chat` (`…kitchat.code`, `…kitchat.diff`), not in `:ui:kit-content` (plan 09 §0.3, §9.2, §9.3). | This brief's write set. `:ui:kit-content` does not exist. Moving the packages later is mechanical: they depend only on kit, design and security. |
| D4 | The brief's S2-KCB (renderers) differs from plan 15's S2-KCB row (message, composer, …) and overlaps S2-KCA's "Markdown block renderers". | Followed the brief. S2-KCA should take these parts instead of writing them again. |
| D5 | SyntaxMP is declared by coordinates, not from the catalog, in `ui/kit-chat/build.gradle.kts`. Test-only Roborazzi and ui-test artifacts are declared the same way, as `:ui:kit` does (P7). | The catalog has no entry and is not this unit's. See the catalog requests. |
| D6 | Package root `io.github.eslamasabry.oc.ui.kitchat.*`, not `…ui.kit.chat.*`. | The module's namespace (P3) is `ui.kitchat`; one root per module. |
| D7 | `KitMarkdownTable` cells wrap inside a column capped at 320 dp. Inline-code cells are not forced to `softWrap = false`. | Simpler first cut; the table still scrolls sideways inside the 700 dp cap. |
| D8 | The redacted display string is computed in composition (`remember(source)`), not off the main thread with the tokens (plan 09 §9.2). | The first frame must already be redacted. Tokens are worked out on `LocalKitChatWork`. Moving redaction there too needs a "not ready" frame; a candidate for S2-CHAT once there are perf numbers. |
| D9 | `KitPermissionCard` (the brief's name) rather than 06's `KitRequestCard`. It has an extra `disabledReason`, because kit buttons require a reason when disabled (B-KIT-051). `KitCommandBox` and `KIT_COMMAND_OUTPUT_LINES` were added. Deny and Always use a secondary button drawn in `request/`, because `:ui:kit` has none yet. | See the slice notes in §1.4. The secondary button should move into `:ui:kit` when its owner adds one. |
| D10 | The diff count rules follow Dart, not the plan text. Only lines count against `maxLines`. A hunk header right after a gap is left out (`kit_diff_rows.dart:61`), so the first hunk of a patch that does not start at line 1 shows no "Lines 12–18" label. Blank lines inside a hunk are skipped. An empty `before` with an `after` is an added file; Dart called it modified. | Parity first. The owner may prefer the plan's wording; it is a one-line change in `displayRows`. |

---

## 6. Catalog requests (for the build-logic owner)

1. `syntaxmp = "0.3.0"` and `syntaxmp-tokenizer = { module = "com.gallatinapps.syntaxmp:syntaxmp-tokenizer", version.ref = "syntaxmp" }`. Then `ui/kit-chat/build.gradle.kts` uses `libs.syntaxmp.tokenizer`. Apache-2.0, pure common Kotlin, published for android, jvm, ios and wasm. The license notice needs adding to the app's open-source list.
2. Test libraries already pinned by version but with no library entry: `compose-ui-test-junit4`, `compose-ui-test-manifest`, `roborazzi`, `roborazzi-compose`, `roborazzi-junit-rule`. This is the same request as P7's.
3. The library entry for `markdown = "0.7.18"` (`org.jetbrains:markdown`) is for S2-MD, not this module.

## 7. Open problems and hand-offs

1. **S2-MD / `:core:markdown`:**
   - the JetBrains-based `StreamingMarkdown` and a `Block → KitMdBlock` adapter. The adapter belongs in `:feature:chat`, or the renderers take `Block` directly once kit-chat → `:core:markdown` is real;
   - B-CHAT-125 (close open bold/italic/code/link on the last line while streaming), `PathChipScanner`, `PictureScanner` and `proseForSpeech`.
2. **Not built here:** `KitPictureGroup`, `KitAgentBlock` (choices/checklist/command fences), `KitFindMark`, the full diff page (split view, lazy rows, selection with Comment/Add to prompt/Copy, change navigator, file list), diff syntax colour (tokenise old and new once, slice per line), wrap toggle, the `.fill` reader mode, and copy feedback ("Copied", Try again).
3. **SP-4 is still owed**: SyntaxMP vs Highlights vs kotlin-textmate on real agent output, by eye on the emulator, with the 20k-char time. The seam makes the swap one file plus `LocalKitCodeTokenizer`.
4. **`SmartSelectionOffTest`** (plan 09 §7.8) needs the context menu opened on Android. Only the filter is wired here; layer 2 (the flag and R8 rule) is the coordinator's.
5. **No device numbers.** Frame cost of revealing Markdown blocks and of `KitMarkdownTable` measurement is unmeasured. P3's open problem 1, a 400-line open fence over budget on the JVM, still applies to `KitCodeBlock(open = true)`.
6. **`KitToolRow` in Arabic:** the LTR subject is laid out in an LTR box, so it sits at the start of an LTR line, under the title's opposite edge. It reads correctly but may want end alignment. This needs a judgement by eye from the kit-chat owner.
7. The goldens were recorded on this container's Robolectric native graphics, as P7's were. A different host may need re-recording, or per-image tolerances.
