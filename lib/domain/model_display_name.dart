/// A readable stand-in for a model's name, made from its id alone, for the
/// moment before the server's model list arrives: "claude-sonnet-5" reads
/// "Sonnet 5", "claude-opus-4-1-20250805" reads "Opus 4.1", "gpt-6-sol"
/// reads "GPT-6 Sol". The catalog's own name replaces it once read; this
/// only keeps a raw id off the screen in the meantime.
String modelNameFromId(String modelID) {
  var id = modelID.trim();
  final slash = id.lastIndexOf('/');
  if (slash >= 0) id = id.substring(slash + 1);
  // Release dates and moving tags say nothing a person picks by.
  id = id
      .replaceFirst(RegExp(r'[-@_](\d{8}|\d{4}-\d{2}-\d{2})$'), '')
      .replaceFirst(RegExp(r'-latest$', caseSensitive: false), '');
  final tokens = id
      .split(RegExp(r'[-_\s]+'))
      .where((token) => token.isNotEmpty)
      .toList();
  // The Claude family is named by its tier ("Sonnet 5"), as its makers do.
  if (tokens.length > 1 && tokens.first.toLowerCase() == 'claude') {
    tokens.removeAt(0);
  }
  if (tokens.isEmpty) return modelID.trim();

  final words = <String>[];
  for (var i = 0; i < tokens.length; i++) {
    final token = tokens[i];
    final lower = token.toLowerCase();
    if (_acronyms.contains(lower)) {
      // "gpt-6" → "GPT-6": an acronym keeps its hyphen to the version.
      final next = i + 1 < tokens.length ? tokens[i + 1] : null;
      if (next != null && _isVersionStart(next)) {
        final version = _joinVersion(tokens, i + 1);
        words.add('${lower.toUpperCase()}-${version.text}');
        i = version.end - 1;
      } else {
        words.add(lower.toUpperCase());
      }
    } else if (_isNumber(token)) {
      final version = _joinVersion(tokens, i);
      words.add(version.text);
      i = version.end - 1;
    } else if (RegExp(r'^[a-z]\d').hasMatch(token)) {
      // "o3", "o4" stay as their makers write them; "k2", "v4" are tags.
      words.add(
        lower.startsWith('o')
            ? lower
            : '${lower[0].toUpperCase()}${lower.substring(1)}',
      );
    } else {
      words.add('${token[0].toUpperCase()}${token.substring(1)}');
    }
  }
  return words.join(' ');
}

const _acronyms = {'gpt', 'glm', 'qwq'};

bool _isNumber(String token) => RegExp(r'^\d+(\.\d+)*$').hasMatch(token);

bool _isVersionStart(String token) => RegExp(r'^\d').hasMatch(token);

/// Short number runs are one version ("4-1" → "4.1"); a lone token that
/// starts with digits ("4o", "5.3") stays as written.
({String text, int end}) _joinVersion(List<String> tokens, int start) {
  final first = tokens[start];
  if (!_isNumber(first)) return (text: first, end: start + 1);
  final parts = [first];
  var end = start + 1;
  while (end < tokens.length &&
      RegExp(r'^\d{1,2}$').hasMatch(tokens[end]) &&
      !parts.last.contains('.')) {
    parts.add(tokens[end]);
    end++;
  }
  return (text: parts.join('.'), end: end);
}
