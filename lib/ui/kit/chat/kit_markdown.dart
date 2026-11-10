// KitMarkdown — agent Markdown in kit text (docs/ux-system/kit-api/
// KitMarkdown.md; kit-v2 §9.2 chat parts, §5 chat transcript group).
//
// The parser, table, heading, quote, list, inline parser and path chip moved
// here from lib/ui/widgets/markdown.dart (C24, R12). The kit imports neither
// agent_blocks.dart nor transcript_highlight.dart: the host hands those in
// as a [KitMarkdownBlockBuilder] and a [KitMarkdownHighlighter].
import 'dart:async';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/physics.dart' show SpringSimulation;
import 'package:flutter/rendering.dart';
import 'package:flutter/scheduler.dart' show Ticker;
import 'package:flutter/services.dart';

import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../theme_roles.dart';
import '../../widgets/external_link.dart';
import '../kit_bidi.dart';
import '../kit_chip.dart';
import '../kit_code_block.dart';
import '../kit_divider.dart';
import '../kit_image.dart';
import '../kit_layout.dart';
import '../kit_link_scope.dart';
import '../kit_max_width.dart';
import '../kit_motion.dart';
import '../kit_surface_look.dart';
import '../kit_tappable.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';
import 'kit_stream_caret.dart';

part 'kit_markdown_fresh.dart';
part 'kit_markdown_image.dart';
part 'kit_markdown_table.dart';

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

/// How inline code that looks like a server path becomes a link, and a
/// Markdown picture that names a server file becomes a tile. The host
/// memoises [validate]; a path renders as plain mono until it resolves true.
@immutable
class KitMarkdownFileLinks {
  const KitMarkdownFileLinks({
    required this.validate,
    required this.open,
    this.readImage,
  });
  final Future<bool> Function(String path) validate;
  final void Function(String path) open;

  /// The bytes of a validated picture file, for its thumbnail in a reply
  /// (the host's own file transport). Null, or a null result: the picture
  /// shows as a named chip instead. The host should keep the function
  /// stable (a method, not a new closure per build): thumbnails of the same
  /// path through the same function share one cached image.
  final Future<Uint8List?> Function(String path)? readImage;
}

/// Agent Markdown in kit text. Chat parts follow the transcript turn model
/// (STANDARDS STATE-16, KIT-41): this part is the prose of a reply or a
/// thought; it never draws a turn's controls.
///
/// States: default, streaming (an unclosed fence stays unhighlighted; with
/// [streaming] a [KitStreamCaret] follows the last words), non-interactive,
/// empty (renders nothing). No loading, error or disabled:
/// the text is given (KIT-12).
class KitMarkdown extends StatefulWidget {
  const KitMarkdown(
    this.data, {
    super.key,
    this.role = KitTextRole.body,
    this.tone,
    this.selectable = true,
    this.interactive = true,
    this.blockBuilder,
    this.highlighter,
    this.fileLinks,
    this.codeWrap,
    this.onCodeWrapChanged,
    this.onOpenCode,
    this.codeLanguage,
    this.streaming = false,
  }) : assert(
         role == KitTextRole.body || role == KitTextRole.secondary,
         'KitMarkdown runs in body or secondary only',
       );

  final String data;

  /// body: replies, documents; secondary: a tool's note, a thought.
  final KitTextRole role;

  /// Null: the role's own tone ([KitText.defaultTone]).
  final KitTextTone? tone;

  /// False where long-press belongs to a menu (a prompt bubble).
  final bool selectable;

  /// False: links and path chips draw as text (demo, isolated previews).
  final bool interactive;
  final KitMarkdownBlockBuilder? blockBuilder;
  final KitMarkdownHighlighter? highlighter;
  final KitMarkdownFileLinks? fileLinks;

  /// Null: [KitCodeBlock.defaultWrap] for the window.
  final bool? codeWrap;

  /// The reader preference; null: each block keeps its own.
  final ValueChanged<bool>? onCodeWrapChanged;

  /// Non-null: capped code says "Open full output" and calls this.
  final void Function(String code, String? language)? onOpenCode;

  /// Non-null: all of [data] is one code block in this language.
  final String? codeLanguage;

  /// The words are still being written: a [KitStreamCaret] sits after the
  /// last of them, on their own line. A reply that ends in code, a table or
  /// pictures shows none (the caret never makes a line of its own).
  final bool streaming;

  /// Conservative path test, moved from markdown.dart: multi-segment, no
  /// spaces, no scheme, anchored (/, ~/, ./) or ending in an extension.
  /// `lib/a/b.dart:12` and `/tmp/shots/home.png` match; `and/or`, URLs and
  /// lone words do not.
  static bool looksLikeFilePath(String code) {
    if (code.length < 4 || code.length > 300) return false;
    if (code.contains('://')) return false;
    if (!_pathLikePattern.hasMatch(code)) return false;
    if (code.startsWith('/') ||
        code.startsWith('~/') ||
        code.startsWith('./')) {
      return true;
    }
    final path = stripPathLineSuffix(code);
    final name = path.substring(path.lastIndexOf('/') + 1);
    final dot = name.lastIndexOf('.');
    return dot > 0 && dot < name.length - 1 && name.length - dot <= 9;
  }

  /// `lib/a.dart:120` -> `lib/a.dart`.
  static String stripPathLineSuffix(String code) =>
      code.replaceFirst(RegExp(r':\d{1,6}$'), '');

  /// The prose a reader would speak: fences, tables and link targets
  /// dropped (read-aloud and voice replies). Moved from markdown.dart. No
  /// link recognizers, URL lookups, code blocks or tool payloads are made.
  static String proseForSpeech(String source) {
    if (source.length > 131072) {
      throw const FormatException('Speech text is too long');
    }
    final output = StringBuffer();
    final fencePattern = RegExp(r'^\s*(`{3,}|~{3,})');
    String? fence;
    for (final line in source.replaceAll('\r\n', '\n').split('\n')) {
      final marker = fencePattern.firstMatch(line);
      if (fence != null) {
        if (marker != null &&
            marker.group(1)!.startsWith(fence[0]) &&
            marker.group(1)!.length >= fence.length &&
            line.substring(marker.end).trim().isEmpty) {
          fence = null;
        }
        continue;
      }
      if (marker != null) {
        fence = marker.group(1);
        continue;
      }
      if (RegExp(r'^\s*(-{3,}|\*{3,}|_{3,})\s*$').hasMatch(line) ||
          _isTableDelimiter(line)) {
        continue;
      }
      final prose = KitMarkdownImage.withAltText(
        line.replaceFirst(
          RegExp(r'^\s*(?:#{1,6}\s+|>\s?|[-*+]\s+|\d+\.\s+)'),
          '',
        ),
      );
      output.writeln(
        prose.replaceAllMapped(_speechPattern, (match) {
          if (match.group(6) != null) return ' ';
          // Link destinations live in group 8 and are never spoken.
          return match.group(7) ??
              match.group(1) ??
              match.group(2) ??
              match.group(3) ??
              match.group(4) ??
              match.group(5) ??
              '';
        }),
      );
      if (output.length > 12000) {
        throw const FormatException('Speech text is too long');
      }
    }
    return output.toString().trim();
  }

  /// Counts full block re-parses (tests assert streaming does not re-parse
  /// unchanged Markdown). For tests only. Not annotated @visibleForTesting
  /// (the frozen API says it is): the retired `MarkdownText.debugParseCount`
  /// forwards to it from lib/, which that annotation forbids without an
  /// analyzer suppression. Reported to the coordinator.
  static int debugParseCount = 0;

  @override
  State<KitMarkdown> createState() => _KitMarkdownState();
}

final _pathLikePattern = RegExp(
  r'^(?:~/|\.{0,2}/)?[A-Za-z0-9_.@+-]+(?:/[A-Za-z0-9_.@+-]+)+(?::\d{1,6})?$',
);

// Groups: 1=***bold italic*** 2=**bold** 3=*italic* 4=__bold__
//         5=~~strike~~ 6=`code` 7=[label](url) label 8=url
const _inlineSource =
    r'\*\*\*(.+?)\*\*\*'
    r'|\*\*(.+?)\*\*'
    r'|\*(.+?)\*'
    r'|__(.+?)__'
    r'|~~(.+?)~~'
    r'|`([^`\n]+)`'
    r'|\[([^\]]+?)\]\(([^)\s]+?)\)';

/// What an inline run lays out as once drawn: the words without their
/// Markdown marks (a link is its label, not its address), and how many file
/// paths in code spans draw as chips, each wider than its text by the chip's
/// inset. A prompt bubble sizes itself from this, not from the raw source.
({String text, int pathChips}) kitMarkdownDisplay(String source) {
  var chips = 0;
  final text = source.replaceAllMapped(_renderPattern, (m) {
    final label = m.group(7);
    if (label != null) return label;
    final code = m.group(6);
    if (code != null) {
      if (KitMarkdown.looksLikeFilePath(code)) chips += 1;
      return code;
    }
    for (final group in [1, 2, 3, 4, 5]) {
      final inner = m.group(group);
      if (inner != null) return inner;
    }
    return m.group(0)!;
  });
  return (text: text, pathChips: chips);
}

/// Speech keeps today's pattern: a bare URL is read as the agent wrote it.
final _speechPattern = RegExp(_inlineSource, dotAll: true);

/// Rendering adds 9 = a bare https/http URL (trailing punctuation left out).
final _renderPattern = RegExp(
  '$_inlineSource'
  r'''|(https?://[^\s<>()\[\]`]*[^\s<>()\[\]`.,;:!?'"*_~])''',
  dotAll: true,
);

// ---------------------------------------------------------------------------
// Configuration shared by every block. Parsed blocks are cached by source
// and read their settings here, so a change of tone, search query or wrap
// preference never forces a re-parse.

class _KitMdScope extends InheritedWidget {
  const _KitMdScope({
    required this.role,
    required this.tone,
    required this.selectable,
    required this.interactive,
    required this.highlighter,
    required this.fileLinks,
    required this.codeWrap,
    required this.onCodeWrapChanged,
    required this.onOpenCode,
    this.live = false,
    this.fadeNew = false,
    required super.child,
  });

  /// The reply is being written: words added to a block fade in.
  final bool live;

  /// Blocks that begin now fade in whole (the reply was already on screen
  /// when they began; a reply opened mid-stream shows what it has at once).
  final bool fadeNew;

  final KitTextRole role;
  final KitTextTone? tone;
  final bool selectable;
  final bool interactive;
  final KitMarkdownHighlighter? highlighter;
  final KitMarkdownFileLinks? fileLinks;
  final bool? codeWrap;
  final ValueChanged<bool>? onCodeWrapChanged;
  final void Function(String code, String? language)? onOpenCode;

  static _KitMdScope of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_KitMdScope>()!;

  @override
  bool updateShouldNotify(_KitMdScope old) =>
      role != old.role ||
      tone != old.tone ||
      selectable != old.selectable ||
      interactive != old.interactive ||
      highlighter != old.highlighter ||
      fileLinks != old.fileLinks ||
      codeWrap != old.codeWrap ||
      onCodeWrapChanged != old.onCodeWrapChanged ||
      onOpenCode != old.onOpenCode ||
      live != old.live ||
      fadeNew != old.fadeNew;
}

// Compiled once: a streaming reply re-splits its blocks on every flush.
final _mdCommentStart = RegExp(r'^ {0,3}<!--');

/// [source] with its HTML comment blocks (a line that starts with `<!--`,
/// through the line that closes it with `-->`, as CommonMark reads them)
/// emptied out, outside code fences. Line breaks stay, so the line count and
/// the fences after the comment do not move; a block still open at the end
/// (a reply that is streaming) is hidden too. A comment inside a line is
/// left as it is: in a coding chat it is usually words about HTML, often in
/// backticks.
String kitMarkdownBlankComments(String source) {
  if (!source.contains('<!--')) return source;
  final lines = source.split('\n');
  String? fence;
  var inComment = false;
  for (var i = 0; i < lines.length; i++) {
    final line = lines[i];
    if (inComment) {
      if (line.contains('-->')) inComment = false;
      lines[i] = '';
      continue;
    }
    final m = _mdFence.firstMatch(line);
    if (fence != null) {
      if (m != null &&
          m.group(2)!.startsWith(fence[0]) &&
          m.group(2)!.length >= fence.length &&
          m.group(3)!.trim().isEmpty) {
        fence = null;
      }
    } else if (m != null) {
      fence = m.group(2);
    } else if (_mdCommentStart.hasMatch(line)) {
      inComment = !line.substring(line.indexOf('<!--') + 4).contains('-->');
      lines[i] = '';
    }
  }
  return lines.join('\n');
}

final _mdFence = RegExp(r'^( {0,3})(`{3,}|~{3,})(.*)$');
final _mdHeading = RegExp(r'^(#{1,6})\s+(.*)$');
final _mdRule = RegExp(r'^\s*(-{3,}|\*{3,}|_{3,})\s*$');
final _mdBullet = RegExp(r'^(\s*)[-*+]\s+');
final _mdOrdered = RegExp(r'^(\s*)(\d{1,9})[.)]\s+');
final _mdSpace = RegExp(r'\s+');
final _mdQuote = RegExp(r'^\s*>\s?');

int _leadingSpaces(String line) => line.length - line.trimLeft().length;

class _KitMarkdownState extends State<KitMarkdown> {
  /// Parsed block widgets, reused verbatim while [KitMarkdown.data] is
  /// unchanged so transcript-wide rebuilds during streaming skip re-parsing.
  List<Widget>? _blocks;
  String? _parsedData;
  String? _parsedLanguage;

  /// Block widgets from the previous parse keyed by kind and source. A
  /// streaming reply only grows at its tail, so every block before the tail
  /// comes back as the very same widget instance and Flutter skips
  /// rebuilding it on each delta flush.
  Map<String, Widget> _blocksBySource = const {};

  /// This reply has been on screen before this build: blocks that begin
  /// while it streams fade in ([_KitMdScope.fadeNew]).
  bool _shown = false;

  /// The reply stopped streaming in this very update: its last words still
  /// fade in like the ones before them.
  bool _justEnded = false;

  @override
  void didUpdateWidget(KitMarkdown oldWidget) {
    super.didUpdateWidget(oldWidget);
    _justEnded = oldWidget.streaming && !widget.streaming;
  }

  List<Widget> _blocksFor(BuildContext context) {
    final cached = _blocks;
    if (cached != null &&
        _parsedData == widget.data &&
        _parsedLanguage == widget.codeLanguage) {
      return cached;
    }
    KitMarkdown.debugParseCount++;
    _parsedData = widget.data;
    _parsedLanguage = widget.codeLanguage;
    final language = widget.codeLanguage;
    if (language != null) {
      _blocksBySource = const {};
      return _blocks = [
        _KitMdFence(
          info: language,
          body: widget.data,
          original: widget.data,
          closed: true,
        ),
      ];
    }
    return _blocks = _splitBlocks(context, widget.data);
  }

  List<Widget> _splitBlocks(BuildContext context, String src) {
    final widgets = <Widget>[];
    final originalLines = src.split('\n');
    final previous = _blocksBySource;
    final current = <String, Widget>{};
    final lines = kitMarkdownBlankComments(
      src.replaceAll('\r\n', '\n'),
    ).split('\n');
    var i = 0;
    final paragraph = <String>[];

    void add(String source, Widget Function() create) {
      widgets.add(current[source] ??= previous[source] ?? create());
    }

    void flushParagraph() {
      if (paragraph.isEmpty) return;
      final text = paragraph.join('\n');
      add('p\u0000$text', () => _KitMdParagraph(text: text));
      paragraph.clear();
    }

    final fencePattern = _mdFence;
    final heading = _mdHeading;
    final rule = _mdRule;
    final bullet = _mdBullet;
    final ordered = _mdOrdered;

    while (i < lines.length) {
      final line = lines[i];

      // Fenced code block.
      final fence = fencePattern.firstMatch(line);
      if (fence != null &&
          !(fence.group(2)!.startsWith('`') && fence.group(3)!.contains('`'))) {
        flushParagraph();
        final marker = fence.group(2)!;
        final indent = fence.group(1)!.length;
        final info = fence.group(3)!.trim();
        final lang = info.isEmpty ? null : info.split(_mdSpace).first;
        final closing = RegExp(
          '^ {0,3}${RegExp.escape(marker[0])}{${marker.length},}[ \\t]*\$',
        );
        final code = <String>[];
        i++;
        final sourceStart = i;
        while (i < lines.length && !closing.hasMatch(lines[i])) {
          var removed = 0;
          while (removed < indent &&
              removed < lines[i].length &&
              lines[i][removed] == ' ') {
            removed++;
          }
          code.add(lines[i].substring(removed));
          i++;
        }
        // While a fence is still open (streaming) the block re-parses on
        // every delta: syntax colour waits until it closes, so the grammar
        // never runs per token on a growing buffer.
        final closed = i < lines.length;
        final original =
            originalLines
                .sublist(sourceStart, math.min(i, originalLines.length))
                .join('\n') +
            (closed && i > sourceStart ? '\n' : '');
        if (closed) i++;
        final body = code.join('\n');
        final builder = widget.blockBuilder;
        if (closed && builder != null && lang != null) {
          final key = 'a\u0000$lang\u0000$body';
          final reused = previous[key];
          if (reused != null) {
            widgets.add(current[key] = reused);
            continue;
          }
          final built = builder(context, lang, body);
          if (built != null) {
            widgets.add(current[key] = built);
            continue;
          }
        }
        add(
          'c\u0000$lang\u0000$closed\u0000$original',
          () => _KitMdFence(
            info: lang,
            body: body,
            original: original,
            closed: closed,
          ),
        );
        continue;
      }

      // GitHub-flavoured table: a header row, then a delimiter row such as
      // `| --- | :---: | ---: |`.
      if (i + 1 < lines.length &&
          _tableCells(line).length >= 2 &&
          _tableCells(line).length == _tableCells(lines[i + 1]).length &&
          _isTableDelimiter(lines[i + 1])) {
        flushParagraph();
        final headers = _tableCells(line);
        final delimiter = _tableCells(lines[i + 1]);
        final rows = <List<String>>[];
        final start = i;
        i += 2;
        while (i < lines.length) {
          final cells = _tableCells(lines[i]);
          if (lines[i].trim().isEmpty || cells.length < 2) break;
          rows.add(cells);
          i++;
        }
        add(
          't\u0000${lines.sublist(start, i).join('\n')}',
          () => _KitMdTable(headers: headers, delimiter: delimiter, rows: rows),
        );
        continue;
      }

      final h = heading.firstMatch(line);
      if (h != null) {
        flushParagraph();
        final level = h.group(1)!.length;
        add(
          'h\u0000$line',
          () => _KitMdHeading(level: level, text: h.group(2)!),
        );
        i++;
        continue;
      }

      if (rule.hasMatch(line)) {
        flushParagraph();
        add('r\u0000', () => const _KitMdRule());
        i++;
        continue;
      }

      if (line.trimLeft().startsWith('>')) {
        flushParagraph();
        final quote = <String>[];
        while (i < lines.length && lines[i].trimLeft().startsWith('>')) {
          quote.add(lines[i].replaceFirst(_mdQuote, ''));
          i++;
        }
        final text = quote.join('\n');
        add('q\u0000$text', () => _KitMdQuote(text: text));
        continue;
      }

      final listMarker = bullet.hasMatch(line)
          ? bullet
          : ordered.hasMatch(line)
          ? ordered
          : null;
      if (listMarker != null) {
        flushParagraph();
        final items = <_KitMdListItem>[];
        // CommonMark: an ordered list starts at its first item's number, so
        // "2." after a code block that split a list reads 2, not 1 again.
        final first = identical(listMarker, ordered)
            ? int.parse(ordered.firstMatch(line)!.group(2)!)
            : 1;
        while (i < lines.length && listMarker.hasMatch(lines[i])) {
          final m = listMarker.firstMatch(lines[i])!;
          final indent = m.group(1)!.length;
          var text = lines[i].substring(m.end);
          i++;
          // A wrapped item: the lines indented under it are its own words
          // (CommonMark continuation), not a paragraph of their own.
          while (i < lines.length &&
              lines[i].trim().isNotEmpty &&
              !bullet.hasMatch(lines[i]) &&
              !ordered.hasMatch(lines[i]) &&
              !_mdFence.hasMatch(lines[i].trimLeft()) &&
              _leadingSpaces(lines[i]) >= math.max(2, indent + 1)) {
            text = '$text\n${lines[i].trim()}';
            i++;
          }
          items.add((indent: indent, text: text));
        }
        final isOrdered = identical(listMarker, ordered);
        final start = isOrdered ? first : 1;
        add(
          '${isOrdered ? 'o$start' : 'u'}\u0000${items.join('\n')}',
          () => _KitMdList(items: items, ordered: isOrdered, start: start),
        );
        continue;
      }

      if (line.trim().isEmpty) {
        flushParagraph();
        i++;
        continue;
      }

      // Lines that are only pictures: one group, drawn as tiles.
      final group = _pictureGroup(lines, i);
      if (group != null) {
        flushParagraph();
        i = group.end;
        add('i\u0000${group.source}', () => _KitMdImages(images: group.images));
        continue;
      }

      final text = _lineOf(lines, i);
      if (text.trim().isNotEmpty) paragraph.add(text);
      i++;
    }
    flushParagraph();
    _blocksBySource = current;
    return widgets;
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    if (widget.data.trim().isEmpty) return const SizedBox.shrink();
    final parsed = _blocksFor(context);
    // The caret rides on a copy of the tail block; the parsed (cached)
    // blocks stay as they are, so the caret never forces a re-parse.
    final tail = widget.streaming && parsed.isNotEmpty
        ? _withCaret(parsed.last)
        : null;
    final blocks = tail == null
        ? parsed
        : [...parsed.sublist(0, parsed.length - 1), tail];
    final gap = KitTokens.of(context).space3;
    // Its own semantics boundary, each block still its own node for a
    // screen reader. Without it a long streamed reply's blocks are
    // recompiled with everything around them (the turn, its footer) on
    // every frame, which made one 5,000-paragraph reply cost seconds a
    // frame (test/perf_chat_test.dart; docs/qa/slice-P4.4-2026-09-28).
    final content = Semantics(
      container: true,
      explicitChildNodes: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var i = 0; i < blocks.length; i++) ...[
            if (i > 0) SizedBox(height: gap),
            blocks[i],
          ],
        ],
      ),
    );
    final live = widget.streaming || _justEnded;
    // A reply that begins while the conversation is on screen arrives:
    // its first words fade in and its box grows in from nothing. One drawn
    // on opening the page (still moving in), or a long one rebuilt as the
    // list scrolls back to it, shows what it has at once.
    final fadeNew =
        live &&
        (_shown ||
            (widget.data.length <= _arrivalReach &&
                (ModalRoute.of(context)?.animation?.isCompleted ?? true)));
    _shown = true;
    _justEnded = false;
    final scoped = _KitMdScope(
      live: live,
      fadeNew: fadeNew,
      role: widget.role,
      tone: widget.tone,
      selectable: widget.selectable,
      interactive: widget.interactive,
      highlighter: widget.highlighter,
      fileLinks: widget.interactive ? widget.fileLinks : null,
      codeWrap: widget.codeWrap,
      onCodeWrapChanged: widget.onCodeWrapChanged,
      onOpenCode: widget.interactive ? widget.onOpenCode : null,
      child: widget.selectable ? KitSelectable(child: content) : content,
    );
    // An isolated preview is looked at, not used: nothing in it takes a
    // tap or a focus (the old MarkdownInteractionScope contract). While the
    // reply streams its box grows to each new line smoothly (_KitMdGrow).
    return _KitMdGrow(
      enabled: live,
      fromZero: fadeNew,
      child: IgnorePointer(
        ignoring: !widget.interactive,
        child: ExcludeFocus(excluding: !widget.interactive, child: scoped),
      ),
    );
  }

  /// The longest reply that still counts as just begun when first drawn.
  static const _arrivalReach = 280;
}

/// [block] with the streaming caret after its last words, or null when the
/// block has no words to end on (code, a table, pictures, a rule).
Widget? _withCaret(Widget block) => switch (block) {
  _KitMdParagraph(:final text) => _KitMdParagraph(text: text, caret: true),
  _KitMdHeading(:final level, :final text) => _KitMdHeading(
    level: level,
    text: text,
    caret: true,
  ),
  _KitMdQuote(:final text) => _KitMdQuote(text: text, caret: true),
  _KitMdList(:final items, :final ordered, :final start) => _KitMdList(
    items: items,
    ordered: ordered,
    start: start,
    caret: true,
  ),
  _ => null,
};

// ---------------------------------------------------------------------------
// Blocks.

class _KitMdParagraph extends StatelessWidget {
  const _KitMdParagraph({required this.text, this.caret = false});
  final String text;
  final bool caret;

  @override
  Widget build(BuildContext context) {
    final scope = _KitMdScope.of(context);
    return _KitMdText(
      source: text,
      role: scope.role,
      tone: scope.tone,
      caret: caret,
    );
  }
}

class _KitMdHeading extends StatelessWidget {
  const _KitMdHeading({
    required this.level,
    required this.text,
    this.caret = false,
  });
  final int level;
  final String text;
  final bool caret;

  @override
  Widget build(BuildContext context) {
    final scope = _KitMdScope.of(context);
    return Semantics(
      header: true,
      child: _KitMdText(
        source: text,
        role: level <= 2 ? KitTextRole.headline : KitTextRole.rowTitle,
        tone: scope.tone,
        caret: caret,
      ),
    );
  }
}

class _KitMdRule extends StatelessWidget {
  const _KitMdRule();

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.symmetric(vertical: KitTokens.of(context).space1),
    child: const KitDivider(),
  );
}

class _KitMdQuote extends StatelessWidget {
  const _KitMdQuote({required this.text, this.caret = false});
  final String text;
  final bool caret;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final scope = _KitMdScope.of(context);
    final bar = KitSurfaceLook.divider(
      context,
      color: tokens.roles.text3.withValues(alpha: 0.45),
      width: KitTokens.focusRingWidth(context),
    );
    return DecoratedBox(
      decoration: BoxDecoration(
        border: BorderDirectional(
          start: BorderSide.none.copyWith(
            style: BorderStyle.solid,
            color: bar.color,
            width: bar.width,
          ),
        ),
      ),
      child: Padding(
        padding: EdgeInsetsDirectional.only(start: tokens.space2),
        child: _KitMdText(
          source: text,
          role: scope.role,
          tone: KitTextTone.secondary,
          caret: caret,
        ),
      ),
    );
  }
}

/// One list line: [indent] is the raw leading-whitespace width of the
/// source line, so nesting survives the parse.
typedef _KitMdListItem = ({int indent, String text});

class _KitMdList extends StatelessWidget {
  const _KitMdList({
    required this.items,
    required this.ordered,
    this.start = 1,
    this.caret = false,
  });
  final List<_KitMdListItem> items;
  final bool ordered;

  /// The streaming caret after the last item's words.
  final bool caret;

  /// The first top-level item's number (CommonMark list start).
  final int start;

  /// Nesting depth per item, normalised by the smallest indent step used in
  /// this list so both 2- and 4-space nesting land one level deeper.
  List<int> _levels() {
    final indents = items.map((e) => e.indent).toList();
    final base = indents.reduce(math.min);
    var unit = 0;
    for (final d in indents) {
      final step = d - base;
      if (step > 0 && (unit == 0 || step < unit)) unit = step;
    }
    return [
      for (final d in indents) unit == 0 ? 0 : ((d - base) ~/ unit).clamp(0, 4),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final scope = _KitMdScope.of(context);
    final levels = _levels();
    // Ordered numbering restarts each time a nested run begins.
    final counters = <int>[];
    final numbers = <int>[];
    for (final level in levels) {
      while (counters.length > level + 1) {
        counters.removeLast();
      }
      while (counters.length < level + 1) {
        // The top level counts on from the list's own start.
        counters.add(counters.isEmpty ? start - 1 : 0);
      }
      counters[level]++;
      numbers.add(counters[level]);
    }
    // The marker column grows with the text scale, so a bullet or "10." at
    // 200 % text keeps its gap from the item.
    final markerWidth = MediaQuery.textScalerOf(
      context,
    ).scale(ordered ? tokens.space6 : tokens.space4).roundToDouble();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var i = 0; i < items.length; i++)
          Padding(
            // The first item sits on the list's own top edge.
            padding: i == 0
                ? EdgeInsetsDirectional.only(start: tokens.space4 * levels[i])
                : EdgeInsetsDirectional.only(
                    start: tokens.space4 * levels[i],
                    top: tokens.space2,
                  ),
            // The marker ("1.", "•") is read before the item's words.
            child: MergeSemantics(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SizedBox(
                    width: markerWidth,
                    child: KitText(
                      ordered ? '${numbers[i]}.' : '\u2022',
                      role: scope.role,
                      tone: KitTextTone.secondary,
                      tabular: true,
                    ),
                  ),
                  Expanded(
                    child: _KitMdText(
                      source: items[i].text,
                      role: scope.role,
                      tone: scope.tone,
                      caret: caret && i == items.length - 1,
                    ),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

/// A closed or streaming fence. Everything but the words comes from the
/// scope, so a wrap or search change never re-parses the reply.
class _KitMdFence extends StatelessWidget {
  const _KitMdFence({
    required this.info,
    required this.body,
    required this.original,
    required this.closed,
  });

  final String? info;
  final String body;
  final String original;
  final bool closed;

  @override
  Widget build(BuildContext context) {
    final scope = _KitMdScope.of(context);
    final openCode = scope.onOpenCode;
    return KitCodeBlock(
      text: body,
      language: info,
      maxLines: 12,
      highlight: closed,
      copyText: original,
      copyable: scope.interactive,
      showWrapToggle: scope.interactive,
      wrap: scope.codeWrap,
      onWrapChanged: scope.onCodeWrapChanged,
      onOpenFull: openCode == null ? null : () => openCode(body, info),
    );
  }
}

// ---------------------------------------------------------------------------
// Inline text.

/// The reading direction of [text] from its first strong character, or
/// null when it has none (the ambient direction then applies).
TextDirection? _directionOf(String text) {
  for (final rune in text.runes) {
    if ((rune >= 0x0590 && rune <= 0x08FF) ||
        (rune >= 0xFB1D && rune <= 0xFDFF) ||
        (rune >= 0xFE70 && rune <= 0xFEFF)) {
      return TextDirection.rtl;
    }
    if ((rune >= 0x41 && rune <= 0x5A) ||
        (rune >= 0x61 && rune <= 0x7A) ||
        (rune >= 0xC0 && rune <= 0x024F) ||
        (rune >= 0x0370 && rune <= 0x058F)) {
      return TextDirection.ltr;
    }
  }
  return null;
}

/// A run of Markdown lines drawn as one rich text in [role]: emphasis,
/// inline code, links and path chips. Owns its link recognizers.
class _KitMdText extends StatefulWidget {
  const _KitMdText({
    required this.source,
    required this.role,
    required this.tone,
    this.header = false,
    this.textAlign,
    this.detectDirection = true,
    this.caret = false,
  });

  final String source;
  final KitTextRole role;
  final KitTextTone? tone;

  /// A [KitStreamCaret] after the last word (a reply still being written).
  final bool caret;

  /// A table's header cell: header semantics and the heavier weight.
  final bool header;
  final TextAlign? textAlign;
  final bool detectDirection;

  @override
  State<_KitMdText> createState() => _KitMdTextState();
}

class _KitMdTextState extends State<_KitMdText> {
  final _recognizers = <TapGestureRecognizer>[];

  void _disposeRecognizers() {
    for (final recognizer in _recognizers) {
      recognizer.dispose();
    }
    _recognizers.clear();
  }

  @override
  void dispose() {
    _disposeRecognizers();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    _disposeRecognizers();
    final scope = _KitMdScope.of(context);
    final roles = KitTokens.of(context).roles;
    final lines = widget.source.split('\n');
    final spans = <InlineSpan>[];
    for (var i = 0; i < lines.length; i++) {
      if (i > 0) spans.add(const TextSpan(text: '\n'));
      spans.addAll(_spansOf(context, scope, roles, lines[i]));
    }
    var span = TextSpan(
      style: widget.header
          ? const TextStyle(fontWeight: FontWeight.w600)
          : null,
      children: spans,
    );
    final highlighter = scope.highlighter;
    if (highlighter != null) {
      span = highlighter(context, span, source: widget.source);
    }
    // Selection is the whole reply's (KitSelectable around KitMarkdown), so
    // a drag runs across paragraphs and no paragraph is its own text field.
    // The words arriving while the reply streams fade in (_KitMdFresh).
    Widget text = _KitMdFresh(
      span: span,
      // Zero wide and no taller than the line: no word moves and no line
      // grows when it comes or goes. A mark, not words: no semantics.
      trailing: widget.caret
          ? const WidgetSpan(
              alignment: PlaceholderAlignment.middle,
              child: ExcludeSemantics(child: KitStreamCaret()),
            )
          : null,
      role: widget.role,
      tone: widget.tone,
      textAlign: widget.textAlign,
      live: scope.live,
      fadeIn: scope.fadeNew,
    );
    if (widget.detectDirection) {
      final direction = _directionOf(widget.source);
      if (direction != null && direction != Directionality.of(context)) {
        text = Directionality(textDirection: direction, child: text);
      }
    }
    if (widget.header) text = Semantics(header: true, child: text);
    return text;
  }

  List<InlineSpan> _spansOf(
    BuildContext context,
    _KitMdScope scope,
    ThemeRoles roles,
    String src,
  ) {
    final spans = <InlineSpan>[];
    // A picture inside a sentence is a small run of its own, never `!` and
    // a link; the words around it parse as usual.
    final pictures = _pictureSpans(
      src,
      widget.role,
      (text) => _spansOf(context, scope, roles, text),
    );
    if (pictures != null) return pictures;
    var pos = 0;
    for (final m in _renderPattern.allMatches(src)) {
      if (m.start > pos) spans.add(TextSpan(text: src.substring(pos, m.start)));
      pos = m.end;
      final label = m.group(7);
      final bare = m.group(9);
      if (label != null || bare != null) {
        spans.add(
          _link(context, scope, roles, label ?? bare!, m.group(8) ?? bare!),
        );
      } else if (m.group(6) != null) {
        final code = m.group(6)!;
        final links = scope.fileLinks;
        if (links != null && KitMarkdown.looksLikeFilePath(code)) {
          spans.add(
            WidgetSpan(
              alignment: PlaceholderAlignment.middle,
              child: _KitMdPathChip(
                code: code,
                links: links,
                role: widget.role,
              ),
            ),
          );
        } else {
          spans.add(_codeSpan(context, roles, code));
        }
      } else if (m.group(5) != null) {
        spans.add(
          TextSpan(
            style: const TextStyle(decoration: TextDecoration.lineThrough),
            children: _spansOf(context, scope, roles, m.group(5)!),
          ),
        );
      } else if (m.group(1) != null || m.group(4) != null) {
        spans.add(
          TextSpan(
            style: TextStyle(
              fontWeight: FontWeight.w700,
              fontStyle: m.group(1) != null ? FontStyle.italic : null,
            ),
            children: _spansOf(
              context,
              scope,
              roles,
              (m.group(1) ?? m.group(4))!,
            ),
          ),
        );
      } else if (m.group(2) != null) {
        spans.add(
          TextSpan(
            style: const TextStyle(fontWeight: FontWeight.w700),
            children: _spansOf(context, scope, roles, m.group(2)!),
          ),
        );
      } else if (m.group(3) != null) {
        spans.add(
          TextSpan(
            style: const TextStyle(fontStyle: FontStyle.italic),
            children: _spansOf(context, scope, roles, m.group(3)!),
          ),
        );
      } else {
        spans.add(TextSpan(text: m[0]));
      }
    }
    if (pos < src.length) spans.add(TextSpan(text: src.substring(pos)));
    if (spans.isEmpty) spans.add(const TextSpan(text: ''));
    return spans;
  }

  /// A link is accent text with an underline (never colour alone) and opens
  /// only through [openExternalLink] (SEC-1). Non-interactive: plain text.
  InlineSpan _link(
    BuildContext context,
    _KitMdScope scope,
    ThemeRoles roles,
    String label,
    String url,
  ) {
    if (!scope.interactive) return TextSpan(text: label);
    final recognizer = TapGestureRecognizer()
      ..onTap = () => KitLinkScope.open(context, url);
    _recognizers.add(recognizer);
    return TextSpan(
      text: label,
      // Links are in the Accent (ThemeRoles.partner), which reads as text.
      style: TextStyle(
        color: roles.partner,
        decoration: TextDecoration.underline,
        decorationColor: roles.partner,
      ),
      recognizer: recognizer,
      mouseCursor: SystemMouseCursors.click,
    );
  }
}

/// Inline code as text, not a boxed widget: it wraps with the sentence,
/// selects with it, and reads as prose. The mono face marks it. No
/// `surface3` tint (the spec's token): a tinted run beside thin prose is
/// what the G5 contrast check reads as the background, and it fails.
InlineSpan _codeSpan(BuildContext context, ThemeRoles roles, String code) =>
    TextSpan(
      text: code,
      style: KitText.styleOf(
        context,
        KitTextRole.mono,
      ).copyWith(color: roles.text1),
    );

/// Inline code that looks like a file path. Plain mono until the server
/// confirms the file is readable, then an accent underlined mono link. An
/// unreadable path keeps the plain look: no dead affordances.
class _KitMdPathChip extends StatefulWidget {
  const _KitMdPathChip({
    required this.code,
    required this.links,
    required this.role,
  });

  final String code;
  final KitMarkdownFileLinks links;
  final KitTextRole role;

  @override
  State<_KitMdPathChip> createState() => _KitMdPathChipState();
}

class _KitMdPathChipState extends State<_KitMdPathChip> {
  bool _readable = false;
  bool _focused = false;
  Timer? _retry;
  int _retriesLeft = 5;

  /// How long an unconfirmed path waits before asking again: the file
  /// provider's miss cadence.
  static const _retryAfter = Duration(seconds: 22);

  @override
  void initState() {
    super.initState();
    _check();
  }

  @override
  void didUpdateWidget(_KitMdPathChip oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.code != widget.code || oldWidget.links != widget.links) {
      _readable = false;
      _retriesLeft = 5;
    }
    // Re-validate on every rebuild: the host memoises positives and keeps
    // misses briefly, so this is free until a miss expires — exactly when
    // a file the agent just created should light up.
    if (!_readable) _check();
  }

  @override
  void dispose() {
    _retry?.cancel();
    super.dispose();
  }

  void _check() {
    final code = widget.code;
    widget.links.validate(KitMarkdown.stripPathLineSuffix(code)).then((ok) {
      if (!mounted || code != widget.code) return;
      if (ok) {
        _retry?.cancel();
        setState(() => _readable = true);
        return;
      }
      // An idle transcript never rebuilds, so ask a few times on the
      // provider's cadence before giving up; a later rebuild asks again.
      if (_retriesLeft > 0 && (_retry == null || !_retry!.isActive)) {
        _retriesLeft--;
        _retry = Timer(_retryAfter, () {
          if (mounted) _check();
        });
      }
    });
  }

  void _open() => widget.links.open(widget.code);

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final style = KitText.styleOf(context, KitTextRole.mono).copyWith(
      color: _readable ? roles.accent : roles.text1,
      decoration: _readable ? TextDecoration.underline : null,
      decorationColor: roles.accent,
    );
    // The enclosing WidgetSpan already scales the whole chip. A live link
    // paints through RichText: its semantics are the Semantics node below
    // (link, label, hint), and accent on ground is 4.6:1 in light and 11:1
    // in dark (LOOK-6). A plain Text would be re-sampled by the G5
    // contrast check at one pixel per logical pixel, where 13 px mono
    // strokes blur into the ground and no colour passes.
    final label = _readable
        ? RichText(
            text: TextSpan(text: widget.code, style: style),
            textScaler: TextScaler.noScaling,
            textDirection: TextDirection.ltr,
            softWrap: false,
          )
        : Text(
            widget.code,
            style: style,
            textScaler: TextScaler.noScaling,
            textDirection: TextDirection.ltr,
            softWrap: false,
          );
    final chip = DecoratedBox(
      decoration: BoxDecoration(
        border: _focused
            ? Border.all(
                color: roles.accent,
                width: KitTokens.focusRingWidth(context),
              )
            : null,
      ),
      child: Padding(
        padding: EdgeInsets.symmetric(horizontal: tokens.space1),
        child: label,
      ),
    );
    if (!_readable) return chip;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return Semantics(
      link: true,
      label: widget.code,
      hint: l10n.kitMarkdownOpenFile,
      onTap: _open,
      excludeSemantics: true,
      child: FocusableActionDetector(
        mouseCursor: SystemMouseCursors.click,
        onShowFocusHighlight: (value) => setState(() => _focused = value),
        actions: {
          ActivateIntent: CallbackAction<ActivateIntent>(
            onInvoke: (_) {
              _open();
              return null;
            },
          ),
        },
        child: GestureDetector(
          key: Key('path-link-${widget.code}'),
          behavior: HitTestBehavior.opaque,
          onTap: _open,
          child: chip,
        ),
      ),
    );
  }
}
