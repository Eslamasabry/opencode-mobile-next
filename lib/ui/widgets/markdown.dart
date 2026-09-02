import 'dart:async';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../app_theme.dart';
import '../desktop/desktop_interaction.dart';
import 'agent_blocks.dart';
import 'code_highlight.dart';
import 'external_link.dart';

// The chat transcript (a `part` of chat_screen.dart) reaches the glossary
// through this library, which it already imports.

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
      context.dependOnInheritedWidgetOfExactType<MarkdownFileLinks>();

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
    return _blocks = _splitBlocks(widget.data);
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
    return AgentChoiceScope(onChoice: widget.onChoice, child: styled);
  }

  List<Widget> _splitBlocks(String src) {
    final selectable = widget.selectable;
    final widgets = <Widget>[];
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

      // GitHub-flavored Markdown table. A table starts with a header row and
      // a delimiter row such as `| --- | :---: | ---: |`.
      if (i + 1 < lines.length &&
          _tableCells(line).length >= 2 &&
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

      // Fenced code block
      final fence = RegExp(r'^\s*```\s*(\S*)\s*$').firstMatch(line);
      if (fence != null) {
        flushParagraph();
        final lang = fence.group(1);
        final code = <String>[];
        i++;
        while (i < lines.length && !RegExp(r'^\s*```').hasMatch(lines[i])) {
          code.add(lines[i]);
          i++;
        }
        // While a fence is still open (streaming), the block re-parses on
        // every delta flush — defer syntax highlighting until it closes so
        // `highlight.parse` never runs per token on a growing buffer. The
        // code still appears streamed, as plain monospace.
        final closed = i < lines.length;
        i++; // skip closing fence
        final body = code.join('\n');
        if (AgentBlockKinds.matches(lang)) {
          widgets.add(_agentBlock(lang!.trim().toLowerCase(), body));
          continue;
        }
        widgets.add(
          CodeBlock(code: body, language: lang, highlightEnabled: closed),
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
        final items = <String>[];
        while (i < lines.length && RegExp(r'^\s*[-*+]\s+').hasMatch(lines[i])) {
          items.add(lines[i].replaceFirst(RegExp(r'^\s*[-*+]\s+'), ''));
          i++;
        }
        widgets.add(_List(items: items, ordered: false));
        continue;
      }

      // Ordered list
      if (RegExp(r'^\s*\d+[.)]\s+').hasMatch(line)) {
        flushParagraph();
        final items = <String>[];
        while (i < lines.length &&
            RegExp(r'^\s*\d+[.)]\s+').hasMatch(lines[i])) {
          items.add(lines[i].replaceFirst(RegExp(r'^\s*\d+[.)]\s+'), ''));
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
  var body = trimmed;
  if (body.startsWith('|')) body = body.substring(1);
  if (body.endsWith('|')) body = body.substring(0, body.length - 1);
  return body.split(RegExp(r'(?<!\\)\|')).map((cell) {
    return cell.trim().replaceAll(r'\|', '|');
  }).toList();
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
    bool rightAligned(int index) =>
        index < delimiter.length && delimiter[index].trim().endsWith(':');

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
              theme.colorScheme.surfaceContainerHighest.withValues(alpha: .7),
            ),
            headingTextStyle: theme.textTheme.labelMedium?.copyWith(
              fontWeight: FontWeight.w700,
            ),
            dataTextStyle: theme.textTheme.bodySmall,
            // Chat density: DataTable's defaults are sized for data screens.
            dataRowMinHeight: 32,
            dataRowMaxHeight: 40,
            headingRowHeight: 36,
            horizontalMargin: 12,
            columnSpacing: 16,
            dividerThickness: .7,
            columns: [
              for (var column = 0; column < headers.length; column++)
                DataColumn(
                  numeric: rightAligned(column),
                  label: Text.rich(
                    _InlineParser(headers[column]).parse(context),
                  ),
                ),
            ],
            rows: [
              for (final row in rows)
                DataRow(
                  cells: [
                    for (var column = 0; column < headers.length; column++)
                      DataCell(
                        Text.rich(
                          _InlineParser(cellAt(row, column)).parse(context),
                        ),
                      ),
                  ],
                ),
            ],
          ),
        ),
      ),
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
    return Text(
      text,
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

class _List extends StatelessWidget {
  final List<String> items;
  final bool ordered;
  const _List({required this.items, required this.ordered});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var i = 0; i < items.length; i++)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 1.5),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(
                  width: 22,
                  child: Text(
                    ordered ? '${i + 1}.' : '\u2022',
                    style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                      color: Theme.of(context).colorScheme.primary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                Expanded(
                  child: Text.rich(_InlineParser(items[i]).parse(context)),
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
      spans.addAll(_InlineParser(lines[i]).parse(context).children!);
    }
    final span = TextSpan(children: spans);
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

  TextSpan parse(BuildContext context) => TextSpan(children: _spans(context));

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
        final gesture = TapGestureRecognizer()
          ..onTap = () => openExternalLink(context, url);
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
      margin: const EdgeInsets.symmetric(horizontal: 2),
      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1.5),
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
           margin: const EdgeInsets.symmetric(horizontal: 2),
           padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1.5),
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
           child: Text(
             code,
             style: base.copyWith(
               fontFamily: AppTheme.monoFamily,
               fontSize: (base.fontSize ?? 14) - 1.5,
               color: Theme.of(context).colorScheme.tertiary,
             ),
           ),
         ),
       );
}

/// Scrollable, selectable monospace block with a header row carrying the
/// language chip and copy button — a real row instead of an overlay, so the
/// first code lines are never covered and a top-right tap cannot copy by
/// accident.
class CodeBlock extends StatelessWidget {
  final String code;
  final String? language;

  /// Streaming callers pass false while the fence is still open so the
  /// grammar never re-parses a growing buffer per delta.
  final bool highlightEnabled;

  const CodeBlock({
    super.key,
    required this.code,
    this.language,
    this.highlightEnabled = true,
  });

  /// Hard-wraps pathological single lines (minified payloads) so a streamed
  /// 100k-char line cannot become a ~100k-px layout/selection surface. Copy
  /// still uses the original [code].
  static String _displayCode(String source) {
    const limit = 1000;
    var needsWrap = false;
    var runLength = 0;
    for (var i = 0; i < source.length; i++) {
      if (source.codeUnitAt(i) == 0x0A) {
        runLength = 0;
      } else if (++runLength > limit) {
        needsWrap = true;
        break;
      }
    }
    if (!needsWrap) return source;
    final out = StringBuffer();
    for (final line in source.split('\n')) {
      if (out.isNotEmpty) out.write('\n');
      if (line.length <= limit) {
        out.write(line);
        continue;
      }
      for (var start = 0; start < line.length; start += limit) {
        if (start > 0) out.write('\n');
        final end = start + limit < line.length ? start + limit : line.length;
        out.write(line.substring(start, end));
      }
    }
    return out.toString();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final display = _displayCode(code);
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
            padding: const EdgeInsets.fromLTRB(12, 2, 2, 0),
            child: Row(
              children: [
                if (language != null && language!.isNotEmpty)
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 6,
                      vertical: 1,
                    ),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.surfaceContainerHighest
                          .withValues(alpha: .7),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      language!,
                      style: theme.textTheme.labelSmall!.copyWith(color: muted),
                    ),
                  ),
                const Spacer(),
                IconButton(
                  tooltip: 'Copy code',
                  visualDensity: VisualDensity.compact,
                  iconSize: 16,
                  icon: Icon(AppIcons.copy, color: muted),
                  onPressed: () async {
                    await Clipboard.setData(ClipboardData(text: code));
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('Copied ${language ?? 'code'}'),
                          duration: const Duration(seconds: 1),
                        ),
                      );
                    }
                  },
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 0, 12, 10),
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: SelectableText.rich(
                highlightEnabled
                    ? highlightedCode(
                        display,
                        language,
                        CodeHighlightTheme.of(context),
                      )
                    : TextSpan(text: display),
                style: theme.textTheme.bodySmall!.copyWith(
                  fontFamily: AppTheme.monoFamily,
                  fontSize: AppTheme.codeFontSize,
                  height: 1.45,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
