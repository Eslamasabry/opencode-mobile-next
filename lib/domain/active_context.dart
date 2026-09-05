abstract interface class ActiveContextGateway {
  bool get activeContextSupported;
  Future<List<ActiveContextMessage>> loadActiveContext(String sessionID);
}

enum ActiveContextFailure { unsupported, changed, invalidResponse }

class ActiveContextException implements Exception {
  const ActiveContextException(this.failure);
  final ActiveContextFailure failure;
}

enum ContextContentKind {
  text,
  reasoning,
  toolInput,
  toolOutput,
  file,
  notice,
  pruned,
  truncated,
}

class ContextContent {
  const ContextContent(this.kind, this.text, {this.name});
  final ContextContentKind kind;
  final String text;
  final String? name;
}

class ActiveContextMessage {
  const ActiveContextMessage({
    required this.id,
    required this.type,
    required this.content,
  });
  final String id;
  final String type;
  final List<ContextContent> content;

  bool matches(String query) {
    if (query.isEmpty) return true;
    return id.toLowerCase().contains(query) ||
        type.toLowerCase().contains(query) ||
        content.any(
          (part) =>
              part.text.toLowerCase().contains(query) ||
              (part.name?.toLowerCase().contains(query) ?? false),
        );
  }

  String previewFor(String query) {
    final candidates = content
        .map((part) => part.text)
        .where((text) => text.isNotEmpty)
        .toList();
    final text = candidates.firstWhere(
      (text) => query.isNotEmpty && text.toLowerCase().contains(query),
      orElse: () => candidates.firstOrNull ?? '',
    );
    final match = query.isEmpty ? 0 : text.toLowerCase().indexOf(query);
    var start = match > 60 ? match - 60 : 0;
    if (start > 0 &&
        text.codeUnitAt(start) >= 0xDC00 &&
        text.codeUnitAt(start) <= 0xDFFF) {
      start--;
    }
    var end = (start + 180).clamp(0, text.length);
    if (end < text.length &&
        text.codeUnitAt(end) >= 0xDC00 &&
        text.codeUnitAt(end) <= 0xDFFF) {
      end--;
    }
    return '${start > 0 ? '…' : ''}${text.substring(start, end).replaceAll(RegExp(r'\s+'), ' ')}${end < text.length ? '…' : ''}';
  }
}
