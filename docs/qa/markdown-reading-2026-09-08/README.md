# Markdown reading verification

Candidate: `feature/markdown-reading`, based on `45cef77`, with the coordinator's preliminary table/fence edits incorporated. Implemented and locally verified; independent review and integration remain with the coordinator.

## Finish line and boundaries

Read rendered replies and Markdown files with growing table rows, correct alignment and code that wraps or opens in a full-screen snapshot. Copy retains the original code source and offers retry after clipboard refusal. Local reading controls have 48 dp minimum heights, wrap on narrow screens and remain disabled in inert previews. No parser/package migration, remote content, execution or persistence.

Fenced display follows opening indentation while copying retains the original fence body, excluding fence markers but including original line endings, indentation and the newline before a closing fence. Direct `CodeBlock` callers copy their exact input. Display-only breaks on pathological lines and syntax highlighting never feed Copy.

The full-screen reader snapshots code, highlight state and transcript search query at opening. An unfinished fence explains that later stream updates require closing/reopening the reader. It has selection, wrapping and copying only; no file-link callbacks, custom agent block actions or server operations.

## Reading stability

- Unchanged Markdown retains its parsed widget cache. Unchanged earlier code widgets are reused while a later fence grows; streaming open fences remain unhighlighted. Display hard-wrap preparation is cached by source.
- Wrap changes and reader navigation do not invoke transcript navigation. The reader's snapshot and selection remain independent of parent streaming.
- Existing chat behavior pins message count once the reader is more than 480 px from latest, and explicit Jump to latest releases it. This slice preserves that policy. A narrow guard excludes nested/horizontal code and table scroll notifications from changing transcript follow state.
- DataTable's fixed heading height could not measure inline-code placeholders accurately. The visible header is now a semantic header in an auto-height row, so real rich-span layout determines height. Individual columns remain bounded and the table scrolls horizontally.
- Inline code and validated path chips scale once with the paragraph, avoiding double-scaled text at accessibility sizes. Code's horizontal viewport starts at the left edge in RTL while the controls and navigation follow the surrounding direction.

## Verification performed

Pinned Flutter 3.47.2, own package configuration, process-local `build/traycer/env.ps1`, no global Windows changes. `flutter pub get` resolved packages and wrote the worktree package configuration, then returned exit 1 for the host's Windows plugin-symlink prerequisite. Subsequent checks used `--no-pub` and completed. Localization was generated once with `flutter gen-l10n`; changed Dart files were formatted.

All test commands were serial (`flutter test --no-pub --concurrency=1`). Focused coverage totals **69 distinct checks**, plus four capture cases. This is a focused development ladder, not a full repository gate:

| Command suffix / check | Result |
| --- | --- |
| `test/markdown_reading_test.dart` | 20 passed on the final renderer: long/tilde and malformed fences, unterminated highlight deferral, exact CRLF/indent/newline copy after wrap, denied clipboard retry, inert/dynamically retired controls, scaled table/header layout, escaped pipes, cache identity, snapshot/selection, scroll retention and RTL starting position |
| `test/markdown_agent_blocks_test.dart test/markdown_path_link_test.dart test/code_highlight_test.dart test/transcript_search_test.dart test/read_aloud_test.dart test/chat_transcript_lens_test.dart test/l10n_coverage_test.dart` | 46 passed; path-link, code-highlight and transcript-search suites were also rerun with the scaling correction (21 existing checks, alongside the then-19 renderer checks: 40 passed) |
| `test/chat_live_events_test.dart --name 'nested code scrolling\|opens non-image tool files\|floating transcript pills'` | 3 passed, rerun after the RTL viewport correction |
| `flutter analyze --no-pub` | No issues found after final source changes |
| `tool/capture/markdown_reading_test.dart` | 4 passed; actual renderer, theme and fonts, with 16 PNGs |
| `git diff --check` | Passed |

The live-chat regression uses a real horizontal code drag, a real vertical transcript drag and incoming message/part events. Nested scrolling leaves follow-latest unchanged; scrolling away hides a new streamed message until explicit Jump to latest. Its unfinished-stream animation uses bounded frames rather than an invalid `pumpAndSettle` expectation. Initial test-only finder/timing assumptions and analyzer findings were corrected and affected checks rerun.

## Visual evidence

The captures use local fixture content through production `MarkdownText` and the real code-reader route. They are widget-rendered evidence, not Android screenshots. Inspection found and corrected double-scaled inline chips and the RTL code viewport's initial position. Table columns deliberately remain horizontally scrollable; the second table view follows a real drag to show the final, right-aligned column. At 320 px and 2× text, labels wrap vertically, rows grow with content and code can be read without horizontal scrolling after Wrap lines.

| Mode | Table start | Table after horizontal drag | Wrapped code | Full-screen reader |
| --- | --- | --- | --- | --- |
| Light, 390 px | [Table](light-table.png) | [Final column](light-table-end.png) | [Wrap](light-wrap.png) | [Reader](light-reader.png) |
| Dark, 390 px | [Table](dark-table.png) | [Final column](dark-table-end.png) | [Wrap](dark-wrap.png) | [Reader](dark-reader.png) |
| Light, 320 px / 2× | [Table](narrow-table.png) | [Final column](narrow-table-end.png) | [Wrap](narrow-wrap.png) | [Reader](narrow-reader.png) |
| RTL, 390 px | [Table](rtl-table.png) | [Final column](rtl-table-end.png) | [Wrap](rtl-wrap.png) | [Reader](rtl-reader.png) |

No native/device smoothness, full integration suite or APK claim is made by these widget checks. The coordinator owns integration and final native/signing work. Common file-type rendering is proposed for a separate next feature after a bounded inventory of existing `file_preview` support; it is not implemented in this checkout.
