import 'dart:convert';

import '../api/models.dart';

RegExp literalTranscriptQuery(String query) =>
    RegExp(RegExp.escape(query), caseSensitive: false, unicode: true);

class TranscriptMatch {
  const TranscriptMatch({
    required this.messageID,
    required this.partIndex,
    required this.start,
    required this.end,
    required this.text,
    this.kind = 'text',
  });
  final String messageID;
  final int partIndex;
  final int start;
  final int end;
  final String text;
  final String kind;
  String get key => '$messageID/$partIndex/$start';
  int get previewStart {
    var value = (start - 60).clamp(0, text.length);
    if (value > 0 &&
        text.codeUnitAt(value) >= 0xDC00 &&
        text.codeUnitAt(value) <= 0xDFFF) {
      value--;
    }
    return value;
  }

  int get previewEnd {
    var value = (end + 80).clamp(0, text.length);
    if (value < text.length &&
        text.codeUnitAt(value) >= 0xDC00 &&
        text.codeUnitAt(value) <= 0xDFFF) {
      value++;
    }
    return value;
  }

  String get preview =>
      '${previewStart > 0 ? '…' : ''}${text.substring(previewStart, previewEnd).replaceAll(RegExp(r'\s+'), ' ')}${previewEnd < text.length ? '…' : ''}';
}

/// Searches available message text, reasoning, tool data and file names.
/// Binary attachments, synthetic prompts and redacted protocol rows stay out.
/// Per-message caching avoids rescanning unchanged replies during streaming.
class TranscriptSearchIndex {
  String _query = '';
  final _cache =
      <String, ({List<String?> texts, List<TranscriptMatch> hits})>{};
  List<TranscriptMatch> search(
    Iterable<MessageWithParts> messages,
    String query,
  ) {
    query = query.trim();
    if (_query != query) {
      _cache.clear();
      _query = query;
    }
    if (query.isEmpty) return [];
    final pattern = literalTranscriptQuery(query);
    final ids = <String>{};
    final result = <TranscriptMatch>[];
    for (final message in messages) {
      final id = message.info.id;
      ids.add(id);
      final texts = [
        for (final part in message.parts) searchablePartText(part),
      ];
      final previous = _cache[id];
      if (previous != null && _same(texts, previous.texts)) {
        result.addAll(previous.hits);
        continue;
      }
      final hits = <TranscriptMatch>[];
      for (var i = 0; i < texts.length; i++) {
        final text = texts[i];
        if (text == null) continue;
        for (final match in pattern.allMatches(text)) {
          hits.add(
            TranscriptMatch(
              messageID: id,
              partIndex: i,
              start: match.start,
              end: match.end,
              text: text,
              kind: message.parts[i].type,
            ),
          );
        }
      }
      _cache[id] = (texts: texts, hits: hits);
      result.addAll(hits);
    }
    _cache.removeWhere((id, _) => !ids.contains(id));
    return result;
  }

  static bool _same(List<String?> left, List<String?> right) {
    if (left.length != right.length) return false;
    for (var i = 0; i < left.length; i++) {
      if (left[i] != right[i]) return false;
    }
    return true;
  }

  static String? searchablePartText(Part part) {
    if (part.synthetic) return null;
    if (part.type == 'text' || part.type == 'reasoning') return part.text;
    if (part.type == 'file') return part.filename;
    if (part.type != 'tool') return null;
    final state = part.toolState;
    return [
      part.toolName,
      state.title,
      state.inputJson ??
          (state.input.isEmpty
              ? null
              : const JsonEncoder.withIndent('  ').convert(state.input)),
      if (!state.pruned) state.output,
      for (final file in state.outputFiles) file.displayName,
    ].whereType<String>().where((value) => value.isNotEmpty).join('\n');
  }
}
