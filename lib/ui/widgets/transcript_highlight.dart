import 'package:flutter/material.dart';

import '../../domain/transcript_search.dart';
import '../../l10n/app_localizations.dart';

/// Adds search color at rendering time without reparsing Markdown or changing
/// selection, link recognizers, code content, or clipboard data.
class TranscriptHighlight extends InheritedWidget {
  const TranscriptHighlight({
    super.key,
    required this.query,
    required super.child,
  });
  final String query;
  @override
  bool updateShouldNotify(TranscriptHighlight oldWidget) =>
      query != oldWidget.query;

  static TextSpan decorate(
    BuildContext context,
    TextSpan span, {
    String? source,
  }) {
    final query =
        context
            .dependOnInheritedWidgetOfExactType<TranscriptHighlight>()
            ?.query ??
        '';
    if (query.isEmpty) return span;
    final ranges = literalTranscriptQuery(
      query,
    ).allMatches(span.toPlainText(includeSemanticsLabels: false)).toList();
    if (ranges.isEmpty) return span;
    // Source-only hits (e.g. a link URL) use the active excerpt. Do not add
    // uncounted highlights created solely by removing Markdown delimiters.
    if (source != null &&
        literalTranscriptQuery(query).allMatches(source).length !=
            ranges.length) {
      return span;
    }
    final scheme = Theme.of(context).colorScheme;
    final style = TextStyle(
      backgroundColor: scheme.tertiaryContainer,
      color: scheme.onTertiaryContainer,
    );
    var offset = 0;
    var rangeIndex = 0;
    InlineSpan visit(InlineSpan value) {
      if (value is! TextSpan) {
        offset += value.toPlainText(includeSemanticsLabels: false).length;
        return value;
      }
      final text = value.text ?? '';
      final start = offset;
      offset += text.length;
      final children = <InlineSpan>[];
      var cursor = 0;
      while (rangeIndex < ranges.length && ranges[rangeIndex].end <= start) {
        rangeIndex++;
      }
      for (
        var index = rangeIndex;
        index < ranges.length && ranges[index].start < offset;
        index++
      ) {
        final range = ranges[index];
        final from = (range.start - start).clamp(0, text.length);
        final to = (range.end - start).clamp(0, text.length);
        if (from >= to) continue;
        if (from > cursor) {
          children.add(
            TextSpan(
              text: text.substring(cursor, from),
              recognizer: value.recognizer,
            ),
          );
        }
        children.add(
          TextSpan(
            text: text.substring(from, to),
            style: style,
            recognizer: value.recognizer,
          ),
        );
        cursor = to;
      }
      if (cursor < text.length) {
        children.add(
          TextSpan(text: text.substring(cursor), recognizer: value.recognizer),
        );
      }
      for (final child in value.children ?? const <InlineSpan>[]) {
        children.add(visit(child));
      }
      return TextSpan(
        style: value.style,
        recognizer: value.recognizer,
        mouseCursor: value.mouseCursor,
        onEnter: value.onEnter,
        onExit: value.onExit,
        semanticsLabel: value.semanticsLabel,
        locale: value.locale,
        spellOut: value.spellOut,
        children: children,
      );
    }

    return visit(span) as TextSpan;
  }
}

/// The active occurrence stays visible even inside a very long message, code
/// block, or Markdown markup whose source is not rendered as prose.
class TranscriptMatchExcerpt extends StatelessWidget {
  const TranscriptMatchExcerpt({
    super.key,
    required this.match,
    required this.label,
  });
  final TranscriptMatch match;
  final String label;
  @override
  Widget build(BuildContext context) {
    final from = match.previewStart;
    final to = match.previewEnd;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final source = switch (match.kind) {
      'reasoning' => l10n.transcriptFindReasoning,
      'tool' => l10n.transcriptFindTool,
      'file' => l10n.transcriptFindFile,
      _ => null,
    };
    String compact(String value) => value.replaceAll(RegExp(r'\s+'), ' ');
    final scheme = Theme.of(context).colorScheme;
    return Container(
      key: ValueKey('transcript-match-${match.key}'),
      width: double.infinity,
      padding: const EdgeInsets.all(10),
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: scheme.secondaryContainer,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            source == null ? label : '$label · $source',
            style: TextStyle(
              color: scheme.onSecondaryContainer,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 4),
          Text.rich(
            TextSpan(
              children: [
                TextSpan(
                  text:
                      '${from > 0 ? '…' : ''}${compact(match.text.substring(from, match.start))}',
                ),
                TextSpan(
                  text: compact(match.text.substring(match.start, match.end)),
                  style: TextStyle(
                    backgroundColor: scheme.primary,
                    color: scheme.onPrimary,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                TextSpan(
                  text:
                      '${compact(match.text.substring(match.end, to))}${to < match.text.length ? '…' : ''}',
                ),
              ],
            ),
            style: TextStyle(color: scheme.onSecondaryContainer),
            maxLines: 5,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}
