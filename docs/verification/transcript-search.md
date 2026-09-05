# Conversation search — cycle 18

Issue: [#25](https://github.com/Eslamasabry/opencode-mobile-next/issues/25).

## Behavior

Session menu → Find in conversation and the existing Find shortcut open an
inline search bar. The timeline uses the same index and carries its selected
query/message into inline navigation. Previous/Next wrap through occurrences;
F3/Shift+F3 navigate and Escape closes, including after clicking a navigation
button. Counts retain the selected occurrence as older pages arrive.

Search is case-insensitive literal matching over available stored message text,
reasoning, tool names/titles/input/output, and attachment filenames. It searches
Markdown source, so phrases separated by formatting delimiters are not treated
as contiguous rendered prose. Binary attachment bodies, synthetic prompts,
redacted protocol rows and pruned tool output are excluded. This is local
transcript search, not a remote semantic search endpoint.

Matching prose and code receive rendering-time highlights without reparsing
Markdown or changing copy buffers. The active occurrence has a labeled source
excerpt, including matches inside collapsed tool/reasoning content or Markdown
syntax that is not visible as prose. Navigation materializes the message and
then brings that excerpt into view, even in very long replies. Existing tool
expansion choices are preserved.

The bar states when only loaded history is covered. Search all history follows
the existing server cursor, including empty intermediate pages, until there is
no continuation. Failures retain results and offer recovery. Cancel stops
requesting subsequent pages; an already pending shared history request may
finish. Scope changes close search and invalidate the walk. The existing
staged-revert visibility boundary remains in effect.

## Evidence

- Nine focused checks in `test/transcript_search_test.dart` pass: literal and
  Unicode offsets, content scope, streaming/cache invalidation, Markdown/code
  highlights, real-chat navigation/retry/coverage, menu/timeline integration,
  cancellation, a 180-line reply and compact enlarged-text interaction.
- The focused suite plus existing transcript lens, Markdown path-link and
  agent-block regression suites pass: 34 checks total. Copy, selections,
  expansion state, streaming batching and path actions remain covered.
- `flutter analyze --no-pub` reports no issues.
- The 411px rendered fixture was inspected at
  `.dart_tool/transcript-find-preview.png`. The 320px, 1.7x text fixture with a
  simulated keyboard inset keeps Next reachable and functional.

These are local rendering and gateway-fixture checks. Native keyboard,
TalkBack, final device performance and release-candidate validation remain
pending; this document does not certify the final v1 release.

## Cycle 19 CI follow-up

Cycle 18 Windows CI passed; Android/Linux failed two existing tests. One assumed
that timeline selection renders only one copy of a matching string, before the
active source excerpt existed. It now checks the unchanged Markdown body and
visible excerpt independently. The other identified a permission announcement
merged with composer semantics. The announcement now has its own live-region
node, and the search navigation focus node does not expose semantics. The
original exact-label assertion remains unchanged. The context meter likewise
keeps its own accessible label.

The stronger timeline check exposed a distant-match visibility bug. Search now
materializes the target directly, waits for layout and measures the excerpt
relative to its viewport before moving it into view. Flutter's generic sliver
reveal offset was unreliable for this list's centered slivers. Both a distant
timeline target and the 180-line reply now pass explicit hit-testable checks.
