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
import '../kit_tappable.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';

part 'kit_markdown_image.dart';

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
/// States: default, streaming (an unclosed fence stays unhighlighted),
/// non-interactive, empty (renders nothing). No loading, error or disabled:
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
    required super.child,
  });

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
      onOpenCode != old.onOpenCode;
}

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
    final lines = src.replaceAll('\r\n', '\n').split('\n');
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

    final fencePattern = RegExp(r'^( {0,3})(`{3,}|~{3,})(.*)$');
    final heading = RegExp(r'^(#{1,6})\s+(.*)$');
    final rule = RegExp(r'^\s*(-{3,}|\*{3,}|_{3,})\s*$');
    final bullet = RegExp(r'^(\s*)[-*+]\s+');
    final ordered = RegExp(r'^(\s*)(\d{1,9})[.)]\s+');

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
        final lang = info.isEmpty ? null : info.split(RegExp(r'\s+')).first;
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
          quote.add(lines[i].replaceFirst(RegExp(r'^\s*>\s?'), ''));
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
        while (i < lines.length && listMarker.hasMatch(lines[i])) {
          final m = listMarker.firstMatch(lines[i])!;
          items.add((
            indent: m.group(1)!.length,
            text: lines[i].substring(m.end),
          ));
          i++;
        }
        final isOrdered = identical(listMarker, ordered);
        // CommonMark: an ordered list starts at its first item's number, so
        // "2." after a code block that split a list reads 2, not 1 again.
        final start = isOrdered
            ? int.parse(ordered.firstMatch(lines[i - items.length])!.group(2)!)
            : 1;
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
  Widget build(BuildContext context) {
    if (widget.data.trim().isEmpty) return const SizedBox.shrink();
    final blocks = _blocksFor(context);
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
    final scoped = _KitMdScope(
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
    // tap or a focus (the old MarkdownInteractionScope contract).
    return IgnorePointer(
      ignoring: !widget.interactive,
      child: ExcludeFocus(excluding: !widget.interactive, child: scoped),
    );
  }
}

// ---------------------------------------------------------------------------
// Tables.

List<String> _tableCells(String line) {
  final trimmed = line.trim();
  if (!trimmed.contains('|')) return const [];
  final cells = <String>[];
  var cell = StringBuffer();
  var start = trimmed.startsWith('|') ? 1 : 0;
  var endedWithSeparator = false;
  while (start < trimmed.length) {
    final char = trimmed[start];
    if (char == r'\' && start + 1 < trimmed.length) {
      final next = trimmed[start + 1];
      if (next == '|') {
        cell.write('|');
        start += 2;
        endedWithSeparator = false;
        continue;
      }
      if (next == r'\') {
        cell.write(r'\\');
        start += 2;
        endedWithSeparator = false;
        continue;
      }
    }
    if (char == '|') {
      cells.add(cell.toString().trim());
      cell = StringBuffer();
      endedWithSeparator = true;
    } else {
      cell.write(char);
      endedWithSeparator = false;
    }
    start++;
  }
  if (!endedWithSeparator) cells.add(cell.toString().trim());
  return cells;
}

bool _isTableDelimiter(String line) {
  final cells = _tableCells(line);
  return cells.length >= 2 &&
      cells.every((cell) => RegExp(r'^:?-{3,}:?$').hasMatch(cell));
}

/// A cell that is one inline code span and nothing else: it never breaks
/// mid-token.
bool _isMonoCell(String cell) => RegExp(r'^`[^`\n]+`$').hasMatch(cell.trim());

/// A cell's words as they are drawn (markup dropped), for measuring.
String _plainOf(String source) =>
    KitMarkdownImage.withAltText(source).replaceAllMapped(
      _renderPattern,
      (m) =>
          m.group(7) ??
          m.group(6) ??
          m.group(1) ??
          m.group(2) ??
          m.group(3) ??
          m.group(4) ??
          m.group(5) ??
          m.group(9) ??
          '',
    );

class _KitMdTable extends StatefulWidget {
  const _KitMdTable({
    required this.headers,
    required this.delimiter,
    required this.rows,
  });

  final List<String> headers;
  final List<String> delimiter;
  final List<List<String>> rows;

  @override
  State<_KitMdTable> createState() => _KitMdTableState();
}

class _KitMdTableState extends State<_KitMdTable> {
  final _controller = ScrollController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  String _cellAt(List<String> cells, int index) =>
      index < cells.length ? cells[index] : '';

  TextAlign _alignmentAt(int index) {
    final delimiter = widget.delimiter;
    final value = index < delimiter.length ? delimiter[index].trim() : '';
    if (value.startsWith(':') && value.endsWith(':')) return TextAlign.center;
    return value.endsWith(':') ? TextAlign.end : TextAlign.start;
  }

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is KeyUpEvent || !_controller.hasClients) {
      return KeyEventResult.ignored;
    }
    final step = KitTokens.of(context).minTarget;
    final double delta;
    if (event.logicalKey == LogicalKeyboardKey.arrowRight) {
      delta = step;
    } else if (event.logicalKey == LogicalKeyboardKey.arrowLeft) {
      delta = -step;
    } else {
      return KeyEventResult.ignored;
    }
    final position = _controller.position;
    _controller.jumpTo(
      (_controller.offset + delta).clamp(0.0, position.maxScrollExtent),
    );
    return KeyEventResult.handled;
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final scope = _KitMdScope.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final hairline = BorderSide(
      color: roles.hairline,
      width: KitTokens.hairlineWidth(context),
    );
    final columns = widget.headers.length;
    final allRows = [widget.headers, ...widget.rows];
    final allMono = allRows.every(
      (row) => [
        for (var c = 0; c < columns; c++) _cellAt(row, c),
      ].every((cell) => cell.isEmpty || _isMonoCell(cell)),
    );

    return LayoutBuilder(
      builder: (context, constraints) {
        final available = constraints.hasBoundedWidth
            ? constraints.maxWidth
            : MediaQuery.sizeOf(context).width;
        final widths = _columnWidths(context, scope, allRows, available);
        // One widget row per Markdown row, each merged for the screen reader
        // so the table reads row by row; cells line up on their first
        // baseline (a mono address beside body text).
        Widget rowOf(int r) => MergeSemantics(
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: r == 0 ? roles.surface1 : null,
              border: r == 0 ? null : Border(top: hairline),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                for (var c = 0; c < columns; c++)
                  SizedBox(
                    width: widths.cells[c],
                    child: Padding(
                      padding: EdgeInsets.symmetric(
                        horizontal: tokens.space3,
                        vertical: tokens.space2,
                      ),
                      child: _KitMdText(
                        source: _cellAt(allRows[r], c),
                        role: scope.role,
                        tone: scope.tone,
                        header: r == 0,
                        textAlign: _alignmentAt(c),
                        detectDirection: false,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        );
        final table = DecoratedBox(
          decoration: BoxDecoration(border: Border.fromBorderSide(hairline)),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [for (var r = 0; r < allRows.length; r++) rowOf(r)],
          ),
        );
        Widget body = table;
        if (!widths.fits) {
          // A table wider than its box scrolls inside it, never the page,
          // with a visible bar on a fine pointer (KIT-6). Tab reaches it and
          // the arrow keys scroll it.
          body = Focus(
            onKeyEvent: _onKey,
            child: Scrollbar(
              controller: _controller,
              thumbVisibility: KitLayout.finePointer(context),
              child: SingleChildScrollView(
                controller: _controller,
                scrollDirection: Axis.horizontal,
                padding: EdgeInsets.only(bottom: tokens.space2),
                child: table,
              ),
            ),
          );
        } else {
          body = Align(
            alignment: AlignmentDirectional.centerStart,
            child: body,
          );
        }
        if (allMono) {
          body = Directionality(textDirection: TextDirection.ltr, child: body);
        }
        return Semantics(
          container: true,
          label: l10n.kitMarkdownTable(widget.rows.length),
          child: body,
        );
      },
    );
  }

  /// Natural widths when the table fits; otherwise every column keeps at
  /// least its longest word (a mono cell its whole token) and the rest is
  /// shared out. When even the longest words do not fit, the table scrolls.
  ({List<double> cells, bool fits}) _columnWidths(
    BuildContext context,
    _KitMdScope scope,
    List<List<String>> rows,
    double available,
  ) {
    final tokens = KitTokens.of(context);
    final pad = tokens.space3 * 2;
    final scaler = MediaQuery.textScalerOf(context);
    final body = KitText.styleOf(context, scope.role, tone: scope.tone);
    final header = body.copyWith(fontWeight: FontWeight.w600);
    final mono = KitText.styleOf(context, KitTextRole.mono);
    double measure(String text, TextStyle style) {
      if (text.isEmpty) return 0;
      final painter = TextPainter(
        text: TextSpan(text: text, style: style),
        textDirection: TextDirection.ltr,
        textScaler: scaler,
      )..layout();
      final width = painter.width.ceilToDouble();
      painter.dispose();
      return width;
    }

    final columns = widget.headers.length;
    final natural = List<double>.filled(columns, 0);
    final minimum = List<double>.filled(columns, 0);
    for (var r = 0; r < rows.length; r++) {
      for (var c = 0; c < columns; c++) {
        final cell = _cellAt(rows[r], c);
        final monoCell = _isMonoCell(cell);
        final style = monoCell ? mono : (r == 0 ? header : body);
        final plain = _plainOf(cell);
        final full = measure(plain, style);
        natural[c] = math.max(natural[c], full);
        final longestWord = monoCell
            ? full
            : plain
                  .split(RegExp(r'\s+'))
                  .map((word) => measure(word, style))
                  .fold<double>(0, math.max);
        minimum[c] = math.max(minimum[c], longestWord);
      }
    }
    // Hairlines add a little to the table; keep one logical pixel spare.
    final room = available - columns * pad - 1;
    final naturalSum = natural.fold<double>(0, (a, b) => a + b);
    if (naturalSum <= room) {
      return (cells: [for (final w in natural) w + pad], fits: true);
    }
    final minimumSum = minimum.fold<double>(0, (a, b) => a + b);
    if (minimumSum > room) {
      // Scrolls: each column wraps at a readable measure.
      final cap = tokens.minTarget * 6;
      return (
        cells: [
          for (var c = 0; c < columns; c++)
            math.max(minimum[c], math.min(natural[c], cap)) + pad,
        ],
        fits: false,
      );
    }
    // Fits once wide columns wrap: find the cap that fills the room.
    var low = 0.0;
    var high = natural.fold<double>(0, math.max);
    double sumAt(double cap) {
      var sum = 0.0;
      for (var c = 0; c < columns; c++) {
        sum += math.max(minimum[c], math.min(natural[c], cap));
      }
      return sum;
    }

    for (var step = 0; step < 24; step++) {
      final mid = (low + high) / 2;
      if (sumAt(mid) <= room) {
        low = mid;
      } else {
        high = mid;
      }
    }
    return (
      cells: [
        for (var c = 0; c < columns; c++)
          math.max(minimum[c], math.min(natural[c], low)).floorToDouble() + pad,
      ],
      fits: true,
    );
  }
}

// ---------------------------------------------------------------------------
// Blocks.

class _KitMdParagraph extends StatelessWidget {
  const _KitMdParagraph({required this.text});
  final String text;

  @override
  Widget build(BuildContext context) {
    final scope = _KitMdScope.of(context);
    return _KitMdText(source: text, role: scope.role, tone: scope.tone);
  }
}

class _KitMdHeading extends StatelessWidget {
  const _KitMdHeading({required this.level, required this.text});
  final int level;
  final String text;

  @override
  Widget build(BuildContext context) {
    final scope = _KitMdScope.of(context);
    return Semantics(
      header: true,
      child: _KitMdText(
        source: text,
        role: level <= 2 ? KitTextRole.headline : KitTextRole.rowTitle,
        tone: scope.tone,
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
  const _KitMdQuote({required this.text});
  final String text;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final scope = _KitMdScope.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        border: BorderDirectional(
          start: BorderSide(
            color: tokens.roles.hairline,
            width: KitTokens.hairlineWidth(context),
          ),
        ),
      ),
      child: Padding(
        padding: EdgeInsetsDirectional.only(start: tokens.space2),
        child: _KitMdText(
          source: text,
          role: scope.role,
          tone: KitTextTone.secondary,
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
  });
  final List<_KitMdListItem> items;
  final bool ordered;

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
  });

  final String source;
  final KitTextRole role;
  final KitTextTone? tone;

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
    Widget text = KitText.rich(
      span,
      role: widget.role,
      tone: widget.tone,
      textAlign: widget.textAlign,
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
      ..onTap = () => openExternalLink(context, url);
    _recognizers.add(recognizer);
    return TextSpan(
      text: label,
      style: TextStyle(
        color: roles.accent,
        decoration: TextDecoration.underline,
        decorationColor: roles.accent,
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
