# Unit S2-MD: streaming Markdown blocks (report)

Branch `native/md-2026-11-01`, from `claude/native-md-streaming-gdiumw` at `6b9a2b21`. Date: 2026-11-01.
Plan: `plan/09` §3.8 and §9.1, roadmap row S2-MD in `plan/15`. Write set: `native/core/markdown/**` (new module), plus
this report.

Finish line (roadmap): *Streaming blocks, path chips, agent-block scanners and `proseForSpeech` pass on the ported
fixtures.*

Status: **implemented, verified locally (desktop tests and the Android compile), committed, pushed.** Nothing uses it
yet. S2-TX (engine) and S2-KCA (renderers) are the consumers.

## What was built

`:core:markdown` (`io.github.eslamasabry.oc.core.markdown`, convention `oc.kmp.library`, explicit API). It has no
dependencies beyond the convention defaults (coroutines and collections-immutable). It does not use `:core:common`
or `:core:model`.

| File | What it holds |
|---|---|
| `Blocks.kt` | The block model from 09 §9.1: `Block` (`Prose`, `Heading`, `Quote`, `ListItem`, `Code`, `Table`, `PictureGroup`, `AgentBlock`, `Rule`), `Inlines` / `InlineSpan` / `SpanKind` (`Bold`, `Italic`, `Strike`, `Code`, `Link(url)`, `PathCandidate(path, line)`, `Picture`), `CodeKind`, `Align`, `AgentBlockType`, `AgentItem`, `BlockDelta`, and `Block.carriesCaret`. |
| `StreamingMarkdown.kt` | `StreamingMarkdown(partId)`: `append`, `replaceAll`, `finish`, `blocks`, `text`, and the counters `markdownParses`, `tailReparses` and `openFenceAppends`. Also `MarkdownBlocks.parse` and `MarkdownBlocks.wholeCode`. |
| `BlockParser.kt` | A line-by-line port of Flutter `_KitMarkdownState._splitBlocks`, and `blankHtmlComments`. |
| `InlineParser.kt` | A port of `_spansOf`. Pictures inside a sentence become a U+FFFC placeholder with a `Picture` span. Also `markdownDisplay` (Flutter `kitMarkdownDisplay`: words without marks, and the path-chip count for the prompt bubble). |
| `PathChipScanner.kt` | `looksLikeFilePath` and `stripLineSuffix(s): Pair<String, Int?>`. |
| `PictureScanner.kt` | `MarkdownPicture`, `PictureKind`, and `PictureScanner` (`scan`, `only`, `parse`, `withAltText`, `withoutUnfinished`). It also has a small internal URI reader, because common code has no URI class. |
| `UnfinishedTail.kt` | A port of `kit_markdown_unfinished.dart` (plan 06 asks for it in `:core:markdown`). |
| `AgentBlocks.kt` | The `choices`, `checklist` and `command` body parsers from `agent_blocks.dart`. |
| `Speech.kt` | `proseForSpeech` and `SpeechTooLongException`. |
| `TableScanner.kt`, `Dart.kt` | Pipe-table cells and alignment, and regex pieces that keep Dart's meaning of `.` and `$`. |

### How streaming works

- The raw text is kept as received. Lines are read with the `\r` of a CR/LF removed, so a CR/LF split across two
  chunks needs no special case. The last line keeps a lone `\r` because its `\n` may still arrive.
- **Re-parsing only the tail.** The parser is forward-only and line-based. An append can only change the old last
  line (L) and the lines after it, but a growing line can still join the group above it: `-` turns into a list item,
  `|` into a table row, and a blank line into a list continuation. A table header is also recognised from the line
  below it. So the tail starts at **the last group that began before L − 1**. Every group above that one started on a
  line whose meaning can no longer change, and those groups are handed back as the same instances. A tail block that
  equals the block it replaces is also handed back as the same instance. The fuzz test below found both of these
  rules. The first version, which used "the last group", failed it.
- **Open-fence fast path.** While the tail is an open fence whose opening line is complete, each append only extends
  the code text. It runs no parser and no inline regex, and checks only the new lines against the closing pattern.
- **While streaming**, the last line goes through `UnfinishedTail` before it is parsed. `finish()` re-parses the
  exact text. Blocks that come out the same keep their instances.
- **HTML comments.** Text that contains `<!--` is fully re-parsed on each update, because a comment block can hide
  lines far below it. This case is rare, and it is counted in `markdownParses`.
- **Keys** are `<partId>:<start offset>`. They are unique within a part and stay stable while the part grows. The
  engine adds the message id (09 §3.7: `b:<messageId>/<partId>:<startOffset>`).

## Row ids covered

| Row | Tests |
|---|---|
| B-CHAT-059 (Markdown pictures) | `PictureScannerTest`: all 12 scanner tests from `kit_markdown_image_test.dart`, plus picture groups, inline pictures, a half-written picture on the last line, and the drawable formats |
| B-CHAT-060 (caret + speech) | `ProseForSpeechTest` (6), and the `BlockParseTest` caret rule (`carriesCaret`) |
| B-CHAT-064 (path chips) | `PathChipScannerTest` (anchored and extensioned, non-paths, line suffix, `PathCandidate` spans) |
| B-CHAT-061 (prompt bubble measures and counts chips; logic part only) | `PathChipScannerTest` `markdownDisplay` |
| map CHAT-GENUI-16 / 17 (renderer and fences; no catalog B- row) | `BlockParseTest` (role table, ordered list kept across a code block, nesting, list continuation, comments, 7 fence-delimiter cases, invalid fences, CRLF copy text, tables, inline marks in headings, keys, empty text), `InlineParserTest` |
| map CHAT-GENUI-20 (agent blocks) | `BlockParseTest` agent fences, and `StreamingMarkdownTest` agent block only once closed |
| map CHAT-GENUI-21 (streaming polish) | `UnfinishedTailTest`: every case in `markdown_unfinished_syntax_test.dart`'s case table and `kitMarkdownCloseTail` group |
| map CHAT-GENUI-02 / plan 09 §14 `TailUpdateTest` | `StreamingMarkdownTest`: ten tail updates (10 tail re-parses, 0 more full parses, and the heading, code and list instances unchanged), delta identity, edited earlier block, fence closing, a 400-line open fence, CR/LF across chunks, `finish`, comments, and two equivalence tests |

The two equivalence tests:

- **Corpus test.** Twelve fixtures cover every block kind, CRLF and comments. Each is streamed in chunks of 1, 3 and
  7 characters and in random chunks, and compared with a full parse after every delta.
- **Fuzz test.** 1,500 random Markdown-ish texts are streamed the same way. During development it was run once at
  20,000 rounds each with seeds 7 and 99, and both passed.

The Flutter tests that are about widgets (sizes, semantics, the fade, chip focus, clipboard, the 12-column scroll)
are not ported here. They belong to S2-KCA. The logic each of them relies on is covered above.

## Commands and results

Environment: Android SDK installed at `/opt/android-sdk` (`platforms;android-37.1`, `build-tools;37.0.0`,
`platform-tools`), with `local.properties` `sdk.dir`. Gradle 9.7.1 runs on JDK 21, and foojay supplied JDK 17.

```
export ANDROID_HOME=/opt/android-sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :core:markdown:compileAndroidMain :core:markdown:desktopTest -Poc.werror=true
→ BUILD SUCCESSFUL; 67 tests, 0 failures (desktop)
```

Maven Central returned `429 Too Many Requests` many times while dependencies were being fetched. A retry loop with
a 45 s back-off got through. Expect the same on a cold cache.

Not run:

- `:quality:test`. It hit 429 again, it builds every module, and it is expected to fail on MODULE_OWNERS (see below).
- Android host tests. The convention enables them only for layers with platform source sets, and AGP prints a
  warning that `commonTest` exists without them. That is harmless.

## Deviations

1. **Parser: the plan's documented fallback, not JetBrains `StreamingMarkdownFile`.** The version catalog has the
   `markdown = "0.7.18"` version but **no library alias**. The write set does not include the catalog, and spike
   SP-2 has not run. Plan 09 §9.1 names this fallback ("port Flutter's own block parser … to pure Kotlin in
   `:core:markdown` behind the same `StreamingMarkdown` API. The renderer does not change"). The upside is that the
   output equals the Flutter fixtures by construction. If SP-2 later picks JetBrains, the API, the fixtures and the
   equivalence and fuzz tests stay as the gate.
2. **`Block.Quote` holds `inlines`, not child blocks.** Flutter draws a quote as one run of text, and the fixtures
   follow that.
3. **`ListItem` has `firstInList`.** It is not in the plan sketch. The renderer needs it for the list's top edge (the
   first item gets no item gap).
4. **`Code` has `copyText`.** The original body, with CR/LF and indent kept, is what Copy gives (`markdown_reading_test`
   "Copy preserves original CRLF…").
5. **`PathCandidate` is emitted alongside a `Code` span over the same range**, rather than replacing it. That keeps a
   candidate mono until the engine confirms the file.
6. **Fixtures live in the Kotlin test sources** (input next to a compact expected dump), not in
   `commonTest/resources/md/*.md` + `*.blocks.json`. Common tests have no resource loader.
7. **Tests for the map features without B- rows** are named after the map ids (`CHAT-GENUI-02/16/17/20/21`). The
   catalog has only B-CHAT-059, 060, 061 and 064 for Markdown.
8. **CR/LF while streaming.** The unfinished-tail fence check reads CR/LF-normalised lines. Flutter's reads raw lines,
   where a `` ``` `` line ending in `\r` is not seen as a fence. This is a Flutter edge-case bug, not carried over.
9. **Not in this unit, and owned by the engine (S2-TX), per 09 §9.1 and map CHAT-GENUI-15:** the path-confirmation
   cache (one listing per folder, a 20 s miss hold, transient failures forget), the finished-reply LRU of 512 parts,
   and the `TranscriptCounters` wiring. `StreamingMarkdown` exposes the counters for that wiring.

## Catalog / coordinator requests

- **`MODULE_OWNERS`:** add `:core:markdown	S	chat-lead	S2` (or the owner the coordinator names).
  `ModuleOwnersTest` fails until this row exists. Module discovery already includes the folder.
- **Version catalog:** add `jetbrains-markdown = { module = "org.jetbrains:markdown", version.ref = "markdown" }`
  only if SP-2 chooses the JetBrains parser. This unit does not need it.
- **Catalog rows:** consider B- rows for the block rules now covered under map ids (fences, lists, comments,
  unfinished tail, streaming equivalence) so later units can cite them.

## Open problems

- No benchmark yet. Plan 13's `StreamingMarkdownBench` and SP-2's "≤ 1 ms per append for the 400-line fence on the
  emulator" are unmeasured. On desktop the 400-line fence test takes well under a second in total, with 399 fast-path
  appends and at most 2 re-parses.
- iOS klibs were not compiled (`-Poc.ios=true` is nightly only). The regexes avoid JVM-only options (`[\s\S]` instead
  of DOT_MATCHES_ALL, `(?![\s\S])` instead of `$`) so they should port, but this is unverified.
- `PictureScanner`'s URI reader covers what the fixtures need: scheme, host, explicit non-default port, path and
  percent-decoding. Unlike Dart's `Uri`, it does not normalise dot segments.
