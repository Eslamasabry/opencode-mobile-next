import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

/// Lightweight markdown renderer tuned for LLM chat output:
/// headings, bold/italic/strikethrough, inline code, fenced code blocks,
/// bullet/ordered lists, blockquotes, horizontal rules and links.
class MarkdownText extends StatelessWidget {
  final String data;
  final TextStyle? baseStyle;
  final String? codeBlockLanguage;

  const MarkdownText(this.data, {super.key, this.baseStyle, this.codeBlockLanguage});

  @override
  Widget build(BuildContext context) {
    final blocks = _splitBlocks(data);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var i = 0; i < blocks.length; i++) ...[
          if (i > 0) const SizedBox(height: 8),
          blocks[i],
        ],
      ],
    );
  }

  List<Widget> _splitBlocks(String src) {
    final widgets = <Widget>[];
    final lines = src.replaceAll('\r\n', '\n').split('\n');
    var i = 0;
    var paragraph = <String>[];

    void flushParagraph() {
      if (paragraph.isEmpty) return;
      widgets.add(_RichLines(lines: List.of(paragraph)));
      paragraph.clear();
    }

    while (i < lines.length) {
      final line = lines[i];

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
        i++; // skip closing fence
        widgets.add(CodeBlock(code: code.join('\n'), language: lang));
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
        widgets.add(const Padding(
          padding: EdgeInsets.symmetric(vertical: 4),
          child: Divider(height: 1),
        ));
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
        while (i < lines.length && RegExp(r'^\s*\d+[.)]\s+').hasMatch(lines[i])) {
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
}

class _Heading extends StatelessWidget {
  final int level;
  final String text;
  const _Heading({required this.level, required this.text});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final sizes = [20.0, 18.0, 16.5, 15.5, 14.5, 14.0];
    return Text(
      text,
      style: theme.textTheme.titleMedium!.copyWith(
        fontSize: sizes[level.clamp(1, 6) - 1],
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
        border: Border(left: BorderSide(color: theme.colorScheme.primary.withValues(alpha: .5), width: 3)),
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
                Expanded(child: Text.rich(_InlineParser(items[i]).parse(context))),
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
  const _RichLines({required this.lines});

  @override
  Widget build(BuildContext context) {
    // Join with two-space line breaks preserved as newlines.
    final spans = <InlineSpan>[];
    for (var i = 0; i < lines.length; i++) {
      if (i > 0) spans.add(const TextSpan(text: '\n'));
      spans.addAll(_InlineParser(lines[i]).parse(context).children!);
    }
    return SelectableText.rich(TextSpan(children: spans));
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
          ..onTap = () async {
            final uri = Uri.tryParse(url);
            if (uri != null) {
              await launchUrl(uri, mode: LaunchMode.externalApplication);
            }
          };
        spans.add(TextSpan(
          text: m.group(7),
          style: base.copyWith(
            color: theme.colorScheme.primary,
            decoration: TextDecoration.underline,
          ),
          recognizer: gesture,
        ));
      } else if (m.group(6) != null) {
        spans.add(_CodeSpan(m.group(6)!, base: base, context: context));
      } else if (m.group(5) != null) {
        spans.add(TextSpan(
            text: m.group(5),
            style: base.copyWith(decoration: TextDecoration.lineThrough)));
      } else if (m.group(1) != null || m.group(4) != null) {
        spans.add(TextSpan(
            text: m.group(1) ?? m.group(4),
            style: base.copyWith(fontWeight: FontWeight.w700)));
      } else if (m.group(2) != null) {
        spans.add(TextSpan(text: m.group(2),
            style: base.copyWith(fontWeight: FontWeight.w700)));
      } else if (m.group(3) != null) {
        spans.add(TextSpan(text: m.group(3),
            style: base.copyWith(fontStyle: FontStyle.italic)));
      } else {
        spans.add(TextSpan(text: m[0]));
      }
    }
    if (pos < src.length) spans.add(TextSpan(text: src.substring(pos)));
    if (spans.isEmpty) spans.add(const TextSpan(text: ''));
    return spans;
  }
}

class _CodeSpan extends WidgetSpan {
  _CodeSpan(String code, {required TextStyle base, required BuildContext context})
      : super(
          alignment: PlaceholderAlignment.middle,
          child: Container(
            margin: const EdgeInsets.symmetric(horizontal: 2),
            padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1.5),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.surfaceContainerHighest.withValues(alpha: .6),
              borderRadius: BorderRadius.circular(4),
              border: Border.all(
                color: Theme.of(context).dividerColor.withValues(alpha: .4),
                width: .5,
              ),
            ),
            child: Text(
              code,
              style: base.copyWith(
                fontFamily: 'monospace',
                fontSize: (base.fontSize ?? 14) - 1.5,
                color: Theme.of(context).colorScheme.tertiary,
              ),
            ),
          ),
        );
}

/// Scrollable, selectable monospace block with a copy button.
class CodeBlock extends StatelessWidget {
  final String code;
  final String? language;
  const CodeBlock({super.key, required this.code, this.language});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Stack(
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
          decoration: BoxDecoration(
            color: theme.brightness == Brightness.dark
                ? Colors.black.withValues(alpha: .45)
                : Colors.black.withValues(alpha: .05),
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: theme.dividerColor.withValues(alpha: .5)),
          ),
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: SelectableText(
              code,
              style: theme.textTheme.bodySmall!.copyWith(
                fontFamily: 'monospace',
                fontSize: 12.5,
                height: 1.45,
              ),
            ),
          ),
        ),
        Positioned(
          top: 2,
          right: 2,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (language != null && language!.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(right: 4),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: .7),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(language!,
                        style: theme.textTheme.labelSmall!
                            .copyWith(fontSize: 10, color: theme.hintColor)),
                  ),
                ),
              IconButton(
                visualDensity: VisualDensity.compact,
                iconSize: 16,
                icon: Icon(Icons.copy_rounded, color: theme.hintColor),
                onPressed: () async {
                  await Clipboard.setData(ClipboardData(text: code));
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Copied ${language ?? 'code'}'),
                          duration: const Duration(seconds: 1)),
                    );
                  }
                },
              ),
            ],
          ),
        ),
      ],
    );
  }
}
