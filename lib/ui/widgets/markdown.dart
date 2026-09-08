import 'dart:async';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../desktop/desktop_interaction.dart';
import 'agent_blocks.dart';
import 'code_highlight.dart';
import 'external_link.dart';
import 'transcript_highlight.dart';

// The chat transcript (a `part` of chat_screen.dart) reaches the glossary
// through this library, which it already imports.

/// Restricts markdown to local presentation in isolated previews. Defaults to
/// normal product interaction when no scope is installed.
class MarkdownInteractionScope extends InheritedWidget {
  const MarkdownInteractionScope({
    super.key,
    required this.enabled,
    required super.child,
  });
  final bool enabled;
  static bool enabledOf(BuildContext context) =>
      context
          .dependOnInheritedWidgetOfExactType<MarkdownInteractionScope>()
          ?.enabled ??
      true;
  @override
  bool updateShouldNotify(MarkdownInteractionScope oldWidget) =>
      enabled != oldWidget.enabled;
}

/// Installed by screens that can resolve server file paths. Inline code
/// spans that look like paths stay plain until [validate] confirms the file
/// is actually readable on the connected server; only then do they render
/// as tappable links routed through [open].
class MarkdownFileLinks extends InheritedWidget {
  const MarkdownFileLinks({
    super.key,
    required this.validate,
    required this.open,
    required super.child,
  });

  /// Must be memoized by the provider: spans re-request on every rebuild.
  final Future<bool> Function(String path) validate;
  final void Function(String path) open;

  static MarkdownFileLinks? maybeOf(BuildContext context) =>
      MarkdownInteractionScope.enabledOf(context)
      ? context.dependOnInheritedWidgetOfExactType<MarkdownFileLinks>()
      : null;

  @override
  bool updateShouldNotify(MarkdownFileLinks oldWidget) =>
      validate != oldWidget.validate || open != oldWidget.open;
}

final _pathLikePattern = RegExp(
  r'^(?:~/|\.{0,2}/)?[A-Za-z0-9_.@+-]+(?:/[A-Za-z0-9_.@+-]+)+(?::\d{1,6})?$',
);

/// Conservative: multi-segment, no spaces, no scheme, and either anchored
/// (`/`, `~/`, `./`) or ending in a file extension — `lib/a/b.dart:12` and
/// `/tmp/shots/home.png` match; `and/or`, URLs and lone words do not.
bool looksLikeFilePath(String code) {
  if (code.length < 4 || code.length > 300) return false;
  if (code.contains('://')) return false;
  if (!_pathLikePattern.hasMatch(code)) return false;
  if (code.startsWith('/') || code.startsWith('~/') || code.startsWith('./')) {
    return true;
  }
  final path = stripPathLineSuffix(code);
  final name = path.substring(path.lastIndexOf('/') + 1);
  final dot = name.lastIndexOf('.');
  return dot > 0 && dot < name.length - 1 && name.length - dot <= 9;
}

/// `lib/a.dart:120` -> `lib/a.dart`.
String stripPathLineSuffix(String code) =>
    code.replaceFirst(RegExp(r':\d{1,6}$'), '');

/// Lightweight markdown renderer tuned for LLM chat output:
/// headings, bold/italic/strikethrough, inline code, fenced code blocks,
/// bullet/ordered lists, blockquotes, horizontal rules and links.
class MarkdownText extends StatefulWidget {
  final String data;
  final TextStyle? baseStyle;
  final String? codeBlockLanguage;

  /// Chat bubbles that own a long-press action menu render non-selectable
  /// prose so the gesture reaches the menu instead of text selection.
  final bool selectable;

  /// Receives the text of a tapped option from a ```choices block. Without a
  /// handler the option is copied to the clipboard instead.
  final ValueChanged<String>? onChoice;

  const MarkdownText(
    this.data, {
    super.key,
    this.baseStyle,
    this.codeBlockLanguage,
    this.selectable = true,
    this.onChoice,
  });

  /// Counts full block re-parses. Tests use it to assert that streaming
  /// rebuilds do not re-parse unchanged markdown.
  @visibleForTesting
  static int debugParseCount = 0;

  @override
  State<MarkdownText> createState() => _MarkdownTextState();
}

class _MarkdownTextState extends State<MarkdownText> {
  /// Parsed block widgets, reused verbatim while [MarkdownText.data] is
  /// unchanged so transcript-wide rebuilds during streaming skip re-parsing
  /// (and therefore re-highlighting) every other message.
  List<Widget>? _blocks;
  String? _parsedData;
  bool? _parsedSelectable;

  List<Widget> _blocksFor() {
    final cached = _blocks;
    if (cached != null &&
        _parsedData == widget.data &&
        _parsedSelectable == widget.selectable) {
      return cached;
    }
    MarkdownText.debugParseCount++;
    _parsedData = widget.data;
    _parsedSelectable = widget.selectable;
    final next = _splitBlocks(widget.data);
    // A growing later fence must not rebuild an unchanged earlier code block
    // or disturb its local selection, horizontal offset or wrap choice.
    if (cached != null) {
      for (var i = 0; i < next.length && i < cached.length; i++) {
        final old = cached[i];
        final value = next[i];
        if (old is CodeBlock &&
            value is CodeBlock &&
            old.code == value.code &&
            old.originalSource == value.originalSource &&
            old.language == value.language &&
            old.highlightEnabled == value.highlightEnabled) {
          next[i] = old;
        }
      }
    }
    return _blocks = next;
  }

  @override
  Widget build(BuildContext context) {
    final blocks = _blocksFor();
    final content = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var i = 0; i < blocks.length; i++) ...[
          if (i > 0) const SizedBox(height: 8),
          blocks[i],
        ],
      ],
    );
    final styled = widget.baseStyle == null
        ? content
        : DefaultTextStyle.merge(style: widget.baseStyle, child: content);
    // The scope carries the live handler so parsed blocks stay cacheable.
    final interactive = MarkdownInteractionScope.enabledOf(context);
    return IgnorePointer(
      ignoring: !interactive,
      child: ExcludeFocus(
        excluding: !interactive,
        child: AgentChoiceScope(onChoice: widget.onChoice, child: styled),
      ),
    );
  }

  List<Widget> _splitBlocks(String src) {
    final selectable = widget.selectable;
    final widgets = <Widget>[];
    final originalLines = src.split('\n');
    final lines = src.replaceAll('\r\n', '\n').split('\n');
    var i = 0;
    var paragraph = <String>[];

    void flushParagraph() {
      if (paragraph.isEmpty) return;
      widgets.add(
        _RichLines(lines: List.of(paragraph), selectable: selectable),
      );
      paragraph.clear();
    }

    while (i < lines.length) {
      final line = lines[i];

      // Fenced code block
      final fence = RegExp(r'^( {0,3})(`{3,}|~{3,})(.*)$').firstMatch(line);
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
        // While a fence is still open (streaming), the block re-parses on
        // every delta flush — defer syntax highlighting until it closes so
        // `highlight.parse` never runs per token on a growing buffer. The
        // code still appears streamed, as plain monospace.
        final closed = i < lines.length;
        final original =
            originalLines.sublist(sourceStart, i).join('\n') +
            (closed && i > sourceStart ? '\n' : '');
        if (closed) i++; // skip closing fence
        final body = code.join('\n');
        if (AgentBlockKinds.matches(lang)) {
          widgets.add(_agentBlock(lang!.trim().toLowerCase(), body));
          continue;
        }
        widgets.add(
          CodeBlock(
            code: body,
            originalSource: original,
            language: lang,
            highlightEnabled: closed,
          ),
        );
        continue;
      }

      // GitHub-flavored Markdown table. A table starts with a header row and
      // a delimiter row such as `| --- | :---: | ---: |`.
      if (i + 1 < lines.length &&
          _tableCells(line).length >= 2 &&
          _tableCells(line).length == _tableCells(lines[i + 1]).length &&
          _isTableDelimiter(lines[i + 1])) {
        flushParagraph();
        final headers = _tableCells(line);
        final delimiter = _tableCells(lines[i + 1]);
        final rows = <List<String>>[];
        i += 2;
        while (i < lines.length) {
          final cells = _tableCells(lines[i]);
          if (lines[i].trim().isEmpty || cells.length < 2) break;
          rows.add(cells);
          i++;
        }
        widgets.add(
          _MarkdownTable(headers: headers, delimiter: delimiter, rows: rows),
        );
        continue;
      }

      // Heading
      final h = RegExp(r'^(#{1,6})\s+(.*)$').firstMatch(line);
      if (h != null) {
        flushParagraph();
        final level = h.group(1)!.length;
        widgets.add(_Heading(level: level, text: h.group(2)!));
        i++;
        continue;
      }

      // Horizontal rule
      if (RegExp(r'^\s*(-{3,}|\*{3,}|_{3,})\s*$').hasMatch(line)) {
        flushParagraph();
        widgets.add(
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 4),
            child: Divider(height: 1),
          ),
        );
        i++;
        continue;
      }

      // Blockquote
      if (line.trimLeft().startsWith('>')) {
        flushParagraph();
        final quote = <String>[];
        while (i < lines.length && lines[i].trimLeft().startsWith('>')) {
          quote.add(lines[i].replaceFirst(RegExp(r'^\s*>\s?'), ''));
          i++;
        }
        widgets.add(_Quote(text: quote.join('\n')));
        continue;
      }

      // Unordered list
      if (RegExp(r'^\s*[-*+]\s+').hasMatch(line)) {
        flushParagraph();
        final items = <_ListItem>[];
        final marker = RegExp(r'^(\s*)[-*+]\s+');
        while (i < lines.length && marker.hasMatch(lines[i])) {
          final m = marker.firstMatch(lines[i])!;
          items.add((
            indent: m.group(1)!.length,
            text: lines[i].substring(m.end),
          ));
          i++;
        }
        widgets.add(_List(items: items, ordered: false));
        continue;
      }

      // Ordered list
      if (RegExp(r'^\s*\d+[.)]\s+').hasMatch(line)) {
        flushParagraph();
        final items = <_ListItem>[];
        final marker = RegExp(r'^(\s*)\d+[.)]\s+');
        while (i < lines.length && marker.hasMatch(lines[i])) {
          final m = marker.firstMatch(lines[i])!;
          items.add((
            indent: m.group(1)!.length,
            text: lines[i].substring(m.end),
          ));
          i++;
        }
        widgets.add(_List(items: items, ordered: true));
        continue;
      }

      // Blank line -> paragraph boundary
      if (line.trim().isEmpty) {
        flushParagraph();
        i++;
        continue;
      }

      paragraph.add(line);
      i++;
    }
    flushParagraph();
    return widgets;
  }

  /// Fences with a reserved info string render as rich agent blocks; see
  /// [AgentBlockKinds].
  Widget _agentBlock(String kind, String body) => switch (kind) {
    AgentBlockKinds.choices => AgentChoicesBlock(
      options: AgentChoicesBlock.parse(body),
    ),
    AgentBlockKinds.checklist => AgentChecklistBlock(
      items: AgentChecklistBlock.parse(body),
    ),
    _ => AgentCommandBlock(commands: AgentCommandBlock.parse(body)),
  };
}

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

class _MarkdownTable extends StatelessWidget {
  const _MarkdownTable({
    required this.headers,
    required this.delimiter,
    required this.rows,
  });

  final List<String> headers;
  final List<String> delimiter;
  final List<List<String>> rows;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    String cellAt(List<String> cells, int index) =>
        index < cells.length ? cells[index] : '';
    TextAlign alignmentAt(int index) {
      final value = index < delimiter.length ? delimiter[index].trim() : '';
      if (value.startsWith(':') && value.endsWith(':')) return TextAlign.center;
      return value.endsWith(':') ? TextAlign.right : TextAlign.left;
    }

    final headerStyle = theme.textTheme.labelLarge!.copyWith(
      fontWeight: FontWeight.w700,
    );

    return LayoutBuilder(
      builder: (context, constraints) {
        final available = constraints.hasBoundedWidth
            ? constraints.maxWidth
            : MediaQuery.sizeOf(context).width;
        // Keep individual cells readable; wide tables scroll as one surface.
        // The rows grow with wrapped content rather than clipping at 40px.
        final cellWidth = ((available - 40) / headers.length).clamp(
          156.0,
          280.0,
        );
        return DecoratedBox(
          decoration: BoxDecoration(
            border: Border.all(color: theme.colorScheme.outlineVariant),
            borderRadius: BorderRadius.circular(8),
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: DataTable(
                headingRowColor: WidgetStatePropertyAll(
                  theme.colorScheme.surfaceContainerHighest.withValues(
                    alpha: .7,
                  ),
                ),
                headingTextStyle: headerStyle,
                dataTextStyle: theme.textTheme.bodyMedium,
                dataRowMinHeight: 48,
                dataRowMaxHeight: double.infinity,
                headingRowHeight: 0,
                horizontalMargin: 12,
                columnSpacing: 16,
                dividerThickness: .7,
                columns: [
                  for (var column = 0; column < headers.length; column++)
                    const DataColumn(label: SizedBox.shrink()),
                ],
                rows: [
                  // DataTable requires a fixed heading height. A semantic
                  // header in an auto-height row measures the real rich spans,
                  // including wrapped inline code and validated path chips.
                  DataRow(
                    color: WidgetStatePropertyAll(
                      theme.colorScheme.surfaceContainerHighest.withValues(
                        alpha: .7,
                      ),
                    ),
                    cells: [
                      for (var column = 0; column < headers.length; column++)
                        DataCell(
                          Semantics(
                            header: true,
                            child: Padding(
                              padding: const EdgeInsets.symmetric(vertical: 12),
                              child: SizedBox(
                                width: cellWidth,
                                child: DefaultTextStyle(
                                  style: headerStyle,
                                  child: Builder(
                                    builder: (context) => Text.rich(
                                      _InlineParser(
                                        headers[column],
                                      ).parse(context),
                                      textAlign: alignmentAt(column),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                  for (final row in rows)
                    DataRow(
                      cells: [
                        for (var column = 0; column < headers.length; column++)
                          DataCell(
                            Padding(
                              padding: const EdgeInsets.symmetric(vertical: 10),
                              child: SizedBox(
                                width: cellWidth,
                                child: Text.rich(
                                  _InlineParser(
                                    cellAt(row, column),
                                  ).parse(context),
                                  style: theme.textTheme.bodyMedium,
                                  textAlign: alignmentAt(column),
                                ),
                              ),
                            ),
                          ),
                      ],
                    ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _Heading extends StatelessWidget {
  final int level;
  final String text;
  const _Heading({required this.level, required this.text});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final textTheme = theme.textTheme;
    // Sizes step down from the theme's title styles so a text-scale or theme
    // change moves every heading level with the surrounding prose.
    final style = switch (level.clamp(1, 6)) {
      1 => textTheme.titleLarge,
      2 => textTheme.titleMedium?.copyWith(
        fontSize: (textTheme.titleMedium?.fontSize ?? 16) * 1.125,
      ),
      3 => textTheme.titleMedium,
      4 => textTheme.titleSmall?.copyWith(
        fontSize: (textTheme.titleSmall?.fontSize ?? 14) * 1.07,
      ),
      _ => textTheme.titleSmall,
    };
    return Text.rich(
      TranscriptHighlight.decorate(context, TextSpan(text: text)),
      style: (style ?? textTheme.titleMedium!).copyWith(
        fontWeight: FontWeight.w700,
      ),
    );
  }
}

class _Quote extends StatelessWidget {
  final String text;
  const _Quote({required this.text});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.fromLTRB(10, 6, 8, 6),
      decoration: BoxDecoration(
        border: Border(
          left: BorderSide(
            color: theme.colorScheme.primary.withValues(alpha: .5),
            width: 3,
          ),
        ),
        color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: .35),
      ),
      child: Text.rich(_InlineParser(text).parse(context)),
    );
  }
}

/// One list line: [indent] is the raw leading-whitespace width of the
/// source line, so nesting survives the parse.
typedef _ListItem = ({int indent, String text});

/// Marker gutter: the bullet sits 3 px from the block's left edge and the
/// item text starts at 18 px. Nested levels step in by [_listNestIndent].
const _listMarkerInset = 3.0;
const _listMarkerWidth = 15.0;
const _listNestIndent = 14.0;

class _List extends StatelessWidget {
  final List<_ListItem> items;
  final bool ordered;
  const _List({required this.items, required this.ordered});

  /// Nesting depth per item, normalised by the smallest indent step used in
  /// this list so both 2- and 4-space nesting land one level deeper.
  List<int> _levels() {
    final indents = items.map((e) => e.indent).toList();
    final base = indents.reduce((a, b) => a < b ? a : b);
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
    final levels = _levels();
    // Ordered numbering restarts each time a nested run begins.
    final counters = <int>[];
    final numbers = <int>[];
    for (final level in levels) {
      while (counters.length > level + 1) {
        counters.removeLast();
      }
      while (counters.length < level + 1) {
        counters.add(0);
      }
      counters[level]++;
      numbers.add(counters[level]);
    }
    final markerStyle = Theme.of(context).textTheme.bodyMedium!.copyWith(
      color: Theme.of(context).colorScheme.primary,
      fontWeight: FontWeight.w600,
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var i = 0; i < items.length; i++)
          Padding(
            padding: EdgeInsets.fromLTRB(
              _listNestIndent * levels[i],
              1.5,
              0,
              1.5,
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(left: _listMarkerInset),
                  child: SizedBox(
                    width: _listMarkerWidth,
                    child: Text(
                      ordered ? '${numbers[i]}.' : '\u2022',
                      style: markerStyle,
                    ),
                  ),
                ),
                Expanded(
                  child: Text.rich(_InlineParser(items[i].text).parse(context)),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

/// A run of plain markdown lines rendered as one rich-text flow.
class _RichLines extends StatelessWidget {
  final List<String> lines;
  final bool selectable;
  const _RichLines({required this.lines, this.selectable = true});

  @override
  Widget build(BuildContext context) {
    // Join with two-space line breaks preserved as newlines.
    final spans = <InlineSpan>[];
    for (var i = 0; i < lines.length; i++) {
      if (i > 0) spans.add(const TextSpan(text: '\n'));
      spans.addAll(_InlineParser(lines[i])._spans(context));
    }
    final span = TranscriptHighlight.decorate(
      context,
      TextSpan(children: spans),
      source: lines.join('\n'),
    );
    return selectable ? SelectableText.rich(span) : Text.rich(span);
  }
}

/// Parses **bold**, *italic*, ~~strike~~, `code`, and [text](url) inline.
class _InlineParser {
  final String src;

  // Groups: 1=***bold italic*** 2=**bold** 3=*italic* 4=__bold__
  //         5=~~strike~~ 6=`code` 7=[label](url) label 8=url
  static final _pattern = RegExp(
    r'\*\*\*(.+?)\*\*\*'
    r'|\*\*(.+?)\*\*'
    r'|\*(.+?)\*'
    r'|__(.+?)__'
    r'|~~(.+?)~~'
    r'|`([^`\n]+)`'
    r'|\[([^\]]+?)\]\(([^)\s]+?)\)',
    dotAll: true,
  );

  _InlineParser(this.src);

  TextSpan parse(BuildContext context) => TranscriptHighlight.decorate(
    context,
    TextSpan(children: _spans(context)),
    source: src,
  );

  List<InlineSpan> _spans(BuildContext context) {
    final spans = <InlineSpan>[];
    final base = DefaultTextStyle.of(context).style;
    final theme = Theme.of(context);
    var pos = 0;
    for (final m in _pattern.allMatches(src)) {
      if (m.start > pos) spans.add(TextSpan(text: src.substring(pos, m.start)));
      pos = m.end;

      if (m.group(7) != null) {
        // [label](url)
        final url = m.group(8)!;
        final gesture = MarkdownInteractionScope.enabledOf(context)
            ? (TapGestureRecognizer()
                ..onTap = () => openExternalLink(context, url))
            : null;
        spans.add(
          TextSpan(
            text: m.group(7),
            style: base.copyWith(
              color: theme.colorScheme.primary,
              decoration: TextDecoration.underline,
            ),
            recognizer: gesture,
          ),
        );
      } else if (m.group(6) != null) {
        final code = m.group(6)!;
        final links = MarkdownFileLinks.maybeOf(context);
        if (links != null && looksLikeFilePath(code)) {
          spans.add(
            WidgetSpan(
              alignment: PlaceholderAlignment.middle,
              child: _PathCodeChip(code: code, links: links, base: base),
            ),
          );
        } else {
          spans.add(_CodeSpan(code, base: base, context: context));
        }
      } else if (m.group(5) != null) {
        spans.add(
          TextSpan(
            text: m.group(5),
            style: base.copyWith(decoration: TextDecoration.lineThrough),
          ),
        );
      } else if (m.group(1) != null || m.group(4) != null) {
        spans.add(
          TextSpan(
            text: m.group(1) ?? m.group(4),
            style: base.copyWith(fontWeight: FontWeight.w700),
          ),
        );
      } else if (m.group(2) != null) {
        spans.add(
          TextSpan(
            text: m.group(2),
            style: base.copyWith(fontWeight: FontWeight.w700),
          ),
        );
      } else if (m.group(3) != null) {
        spans.add(
          TextSpan(
            text: m.group(3),
            style: base.copyWith(fontStyle: FontStyle.italic),
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
}

/// Prose-only speech input using the same inline syntax as this renderer.
/// No link recognizers, URL lookups, code blocks or tool payloads are created.
String markdownProseForSpeech(String source) {
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
    final prose = line.replaceFirst(
      RegExp(r'^\s*(?:#{1,6}\s+|>\s?|[-*+]\s+|\d+\.\s+)'),
      '',
    );
    output.writeln(
      prose.replaceAllMapped(_InlineParser._pattern, (match) {
        if (match.group(6) != null) return ' ';
        // Link destinations live in group 8 and are deliberately never spoken.
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

/// An inline code chip whose text looks like a file path. Renders exactly
/// like [_CodeSpan] until the server confirms the file is readable, then
/// gains link styling and a tap target. Invalid or unreachable paths keep
/// the plain chip — no dead affordances.
class _PathCodeChip extends StatefulWidget {
  const _PathCodeChip({
    required this.code,
    required this.links,
    required this.base,
  });

  final String code;
  final MarkdownFileLinks links;
  final TextStyle base;

  @override
  State<_PathCodeChip> createState() => _PathCodeChipState();
}

class _PathCodeChipState extends State<_PathCodeChip> {
  bool _readable = false;
  Timer? _retry;
  int _retriesLeft = 5;

  @override
  void initState() {
    super.initState();
    _check();
  }

  @override
  void didUpdateWidget(_PathCodeChip oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.code != widget.code || oldWidget.links != widget.links) {
      _readable = false;
      _retriesLeft = 5;
    }
    // Re-validate on every rebuild: the provider memoizes positives forever
    // and misses for a short TTL, so this is free until a miss expires —
    // which is exactly when a file the agent just created should light up.
    if (!_readable) _check();
  }

  @override
  void dispose() {
    _retry?.cancel();
    super.dispose();
  }

  void _check() {
    final code = widget.code;
    widget.links.validate(stripPathLineSuffix(code)).then((ok) {
      if (!mounted || code != widget.code) return;
      if (ok) {
        _retry?.cancel();
        setState(() => _readable = true);
        return;
      }
      // An idle transcript never rebuilds, so poll a few times on the
      // provider's cadence before giving up; any later rebuild retries too.
      if (_retriesLeft > 0 && (_retry == null || !_retry!.isActive)) {
        _retriesLeft--;
        _retry = Timer(const Duration(seconds: 22), () {
          if (mounted) _check();
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final chip = Container(
      margin: const EdgeInsets.symmetric(horizontal: 1),
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1.5),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: .6),
        borderRadius: BorderRadius.circular(4),
        border: Border.all(
          color: _readable
              ? theme.colorScheme.primary.withValues(alpha: .45)
              : AppTheme.hairline(theme),
          width: _readable ? .8 : .5,
        ),
      ),
      child: Text.rich(
        TranscriptHighlight.decorate(
          context,
          TextSpan(
            text: widget.code,
            children: [
              if (_readable)
                WidgetSpan(
                  alignment: PlaceholderAlignment.middle,
                  child: Padding(
                    padding: const EdgeInsets.only(left: 3),
                    child: Icon(
                      Icons.open_in_new_rounded,
                      size: (widget.base.fontSize ?? 14) - 2,
                      color: theme.colorScheme.primary,
                    ),
                  ),
                ),
            ],
          ),
        ),
        // The enclosing WidgetSpan already scales the entire chip.
        textScaler: TextScaler.noScaling,
        style: widget.base.copyWith(
          fontFamily: AppTheme.monoFamily,
          fontSize: (widget.base.fontSize ?? 14) - 1.5,
          color: _readable
              ? theme.colorScheme.primary
              : theme.colorScheme.tertiary,
        ),
      ),
    );
    if (!_readable) return chip;
    // A bare GestureDetector leaves the desktop pointer as an arrow, so a
    // live file link reads as prose. ClickCursor is a no-op on Android.
    return ClickCursor(
      child: GestureDetector(
        key: Key('path-link-${widget.code}'),
        onTap: () => widget.links.open(widget.code),
        child: chip,
      ),
    );
  }
}

class _CodeSpan extends WidgetSpan {
  _CodeSpan(
    String code, {
    required TextStyle base,
    required BuildContext context,
  }) : super(
         alignment: PlaceholderAlignment.middle,
         child: Container(
           margin: const EdgeInsets.symmetric(horizontal: 1),
           padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1.5),
           decoration: BoxDecoration(
             color: Theme.of(
               context,
             ).colorScheme.surfaceContainerHighest.withValues(alpha: .6),
             borderRadius: BorderRadius.circular(4),
             border: Border.all(
               color: AppTheme.hairline(Theme.of(context)),
               width: .5,
             ),
           ),
           child: Text.rich(
             TranscriptHighlight.decorate(context, TextSpan(text: code)),
             // WidgetSpan applies the paragraph's accessibility scale once.
             textScaler: TextScaler.noScaling,
             style: base.copyWith(
               fontFamily: AppTheme.monoFamily,
               fontSize: (base.fontSize ?? 14) - 1.5,
               color: Theme.of(context).colorScheme.tertiary,
             ),
           ),
         ),
       );
}

/// Selectable local code with independent display wrapping and exact copying.
class CodeBlock extends StatefulWidget {
  const CodeBlock({
    super.key,
    required this.code,
    this.originalSource,
    this.language,
    this.highlightEnabled = true,
    this.initialWrap = false,
    this.canExpand = true,
  });
  final String code;

  /// Original fence body, before line-ending/indent display normalization.
  /// Direct callers omit this: their [code] is already the exact source.
  final String? originalSource;
  final String? language;
  final bool highlightEnabled;
  final bool initialWrap;
  final bool canExpand;

  @override
  State<CodeBlock> createState() => _CodeBlockState();
}

class _CodeBlockState extends State<CodeBlock> {
  late bool _wrap = widget.initialWrap;
  final _readerPermission = ValueNotifier(true);
  final _selectionKey = GlobalKey();
  bool _readerOpen = false;
  bool _permissionDisposed = false;
  String? _displaySource;
  String? _displayValue;

  AppLocalizations get l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));
  bool get _interactive =>
      mounted && MarkdownInteractionScope.enabledOf(context);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final enabled = _interactive;
    if (_readerOpen) {
      scheduleMicrotask(() {
        if (!_permissionDisposed) _readerPermission.value = enabled;
      });
    } else {
      _readerPermission.value = enabled;
    }
  }

  @override
  void dispose() {
    if (_readerOpen) {
      // The snapshot route may outlive its source transcript row. Retire its
      // controls after this tree update, then dispose when that route closes.
      scheduleMicrotask(() {
        if (!_permissionDisposed) _readerPermission.value = false;
      });
    } else {
      _permissionDisposed = true;
      _readerPermission.dispose();
    }
    super.dispose();
  }

  Future<void> _copy(String original) async {
    if (!_interactive) return;
    try {
      await Clipboard.setData(ClipboardData(text: original));
      if (!mounted || !_interactive) return;
      ScaffoldMessenger.maybeOf(context)?.showSnackBar(
        SnackBar(
          content: Text(l10n.markdownCopied),
          duration: const Duration(seconds: 2),
        ),
      );
    } catch (_) {
      if (!mounted || !_interactive) return;
      ScaffoldMessenger.maybeOf(context)?.showSnackBar(
        SnackBar(
          content: Text(l10n.markdownCopyFailed),
          action: SnackBarAction(
            label: l10n.markdownCopyRetry,
            onPressed: () => _copy(original),
          ),
        ),
      );
    }
  }

  Future<void> _openReader() async {
    if (!_interactive || _readerOpen || !widget.canExpand) return;
    // Capture values now. A streaming source may change behind this route.
    final snapshot = widget.code;
    final original = widget.originalSource ?? widget.code;
    final language = widget.language;
    final highlighted = widget.highlightEnabled;
    final wrap = _wrap;
    final query =
        context
            .dependOnInheritedWidgetOfExactType<TranscriptHighlight>()
            ?.query ??
        '';
    _readerOpen = true;
    try {
      await Navigator.of(context).push<void>(
        MaterialPageRoute(
          builder: (context) {
            final strings = lookupAppLocalizations(
              Localizations.localeOf(context),
            );
            return Scaffold(
              appBar: AppBar(title: Text(strings.markdownReaderTitle)),
              body: SafeArea(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      if (!highlighted)
                        Padding(
                          padding: const EdgeInsets.only(bottom: 12),
                          child: Text(strings.markdownSnapshot),
                        ),
                      ValueListenableBuilder<bool>(
                        valueListenable: _readerPermission,
                        builder: (context, enabled, _) =>
                            MarkdownInteractionScope(
                              enabled: enabled,
                              child: TranscriptHighlight(
                                query: query,
                                child: CodeBlock(
                                  code: snapshot,
                                  originalSource: original,
                                  language: language,
                                  highlightEnabled: highlighted,
                                  initialWrap: wrap,
                                  canExpand: false,
                                ),
                              ),
                            ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        ),
      );
    } finally {
      _readerOpen = false;
      if (!mounted) {
        _permissionDisposed = true;
        _readerPermission.dispose();
      }
    }
  }

  /// Bound pathological lines for layout only. Copy never reads this value.
  static String _displayCode(String source) {
    const limit = 1000;
    if (!source.split('\n').any((line) => line.length > limit)) return source;
    return source
        .split('\n')
        .map((line) {
          final pieces = <String>[];
          var start = 0;
          while (line.length - start > limit) {
            var end = start + limit;
            // Avoid splitting a UTF-16 surrogate pair at a display line boundary.
            final unit = line.codeUnitAt(end - 1);
            if (unit >= 0xD800 && unit <= 0xDBFF) end--;
            pieces.add(line.substring(start, end));
            start = end;
          }
          pieces.add(line.substring(start));
          return pieces.join('\n');
        })
        .join('\n');
  }

  // Compact visuals keep complete touch targets, including with dense themes.
  ButtonStyle _toolbarStyle(ThemeData theme) => IconButton.styleFrom(
    fixedSize: const Size(48, 48),
    minimumSize: const Size(48, 48),
    maximumSize: const Size(48, 48),
    visualDensity: VisualDensity.standard,
    tapTargetSize: MaterialTapTargetSize.shrinkWrap,
    iconSize: 19,
    foregroundColor: theme.colorScheme.onSurfaceVariant,
  );

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final enabled = _interactive;
    if (_displaySource != widget.code) {
      _displaySource = widget.code;
      _displayValue = _displayCode(widget.code);
    }
    final display = _displayValue!;
    final text = SelectableText.rich(
      TranscriptHighlight.decorate(
        context,
        widget.highlightEnabled
            ? highlightedCode(
                display,
                widget.language,
                CodeHighlightTheme.of(context),
              )
            : TextSpan(text: display),
      ),
      key: _selectionKey,
      style: theme.textTheme.bodySmall!.copyWith(
        fontFamily: AppTheme.monoFamily,
        fontSize: AppTheme.codeFontSize,
        height: 1.45,
      ),
      textDirection: TextDirection.ltr,
      textAlign: TextAlign.left,
    );
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: theme.brightness == Brightness.dark
            ? Colors.black.withValues(alpha: .45)
            : Colors.black.withValues(alpha: .05),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppTheme.hairline(theme)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsetsDirectional.only(start: 12, end: 4),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    widget.language ?? '',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.labelMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
                if (enabled) ...[
                  Semantics(
                    toggled: _wrap,
                    child: IconButton(
                      tooltip: _wrap
                          ? l10n.markdownScrollCode
                          : l10n.markdownWrapCode,
                      style: _toolbarStyle(theme),
                      onPressed: () {
                        if (_interactive) setState(() => _wrap = !_wrap);
                      },
                      icon: Icon(
                        Icons.wrap_text_rounded,
                        color: _wrap ? theme.colorScheme.primary : null,
                      ),
                    ),
                  ),
                  if (widget.canExpand)
                    IconButton(
                      tooltip: l10n.markdownExpandCode,
                      style: _toolbarStyle(theme),
                      onPressed: _openReader,
                      icon: const Icon(Icons.open_in_full_rounded),
                    ),
                  IconButton(
                    tooltip: l10n.markdownCopyCode,
                    style: _toolbarStyle(theme),
                    onPressed: () =>
                        _copy(widget.originalSource ?? widget.code),
                    icon: const Icon(AppIcons.copy),
                  ),
                ],
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(10, 0, 10, 10),
            child: IgnorePointer(
              ignoring: !enabled,
              child: ExcludeFocus(
                excluding: !enabled,
                // Code starts at its left edge even when reader chrome is RTL.
                child: Directionality(
                  textDirection: TextDirection.ltr,
                  child: _wrap
                      ? text
                      : SingleChildScrollView(
                          scrollDirection: Axis.horizontal,
                          child: text,
                        ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
