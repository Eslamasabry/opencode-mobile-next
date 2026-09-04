import 'dart:convert';
import 'dart:typed_data';

/// Returns the provider-facing MIME for a prompt attachment, or null for an
/// unsupported binary format. OpenCode expands exact `text/plain` data URLs
/// into text instead of sending them to a model as arbitrary file parts.
String? promptAttachmentMime({
  required String filename,
  required Uint8List bytes,
  String? declaredMime,
}) {
  final declared = declaredMime?.split(';').first.trim().toLowerCase() ?? '';
  final normalizedMedia = switch (declared) {
    'image/png' => 'image/png',
    'image/jpeg' || 'image/jpg' => 'image/jpeg',
    'image/gif' => 'image/gif',
    'image/webp' => 'image/webp',
    'application/pdf' => 'application/pdf',
    _ => null,
  };
  if (normalizedMedia != null) return normalizedMedia;
  if (_isPromptTextMime(declared)) {
    if (bytes.isNotEmpty && !_looksLikePromptText(bytes)) return null;
    return 'text/plain';
  }
  if (declared.startsWith('image/')) return null;

  final extension = filename.contains('.')
      ? filename.split('.').last.toLowerCase()
      : '';
  final inferred = switch (extension) {
    'png' => 'image/png',
    'jpg' || 'jpeg' => 'image/jpeg',
    'gif' => 'image/gif',
    'webp' => 'image/webp',
    'pdf' => 'application/pdf',
    'md' ||
    'markdown' ||
    'txt' ||
    'log' ||
    'html' ||
    'htm' ||
    'css' ||
    'csv' ||
    'json' ||
    'xml' ||
    'yaml' ||
    'yml' ||
    'toml' ||
    'dart' ||
    'js' ||
    'ts' ||
    'tsx' ||
    'jsx' ||
    'py' ||
    'go' ||
    'rs' ||
    'java' ||
    'kt' ||
    'kts' ||
    'c' ||
    'cc' ||
    'cpp' ||
    'h' ||
    'hpp' ||
    'sh' ||
    'sql' => 'text/plain',
    _ => null,
  };
  if (inferred != null) {
    if (inferred == 'text/plain' &&
        bytes.isNotEmpty &&
        !_looksLikePromptText(bytes)) {
      return null;
    }
    return inferred;
  }
  if ((declared.isEmpty || declared == 'application/octet-stream') &&
      _looksLikePromptText(bytes)) {
    return 'text/plain';
  }
  return null;
}

bool _isPromptTextMime(String mime) =>
    mime.startsWith('text/') ||
    mime == 'application/json' ||
    mime == 'application/javascript' ||
    mime == 'application/x-javascript' ||
    mime == 'application/xml' ||
    mime == 'application/yaml' ||
    mime == 'application/x-yaml' ||
    mime == 'application/toml' ||
    mime == 'application/x-sh' ||
    mime == 'application/sql' ||
    mime == 'application/graphql' ||
    mime == 'application/x-ndjson' ||
    mime.endsWith('+json') ||
    mime.endsWith('+xml');

bool _looksLikePromptText(Uint8List bytes) {
  if (bytes.isEmpty || bytes.contains(0)) return false;
  try {
    final text = utf8.decode(bytes, allowMalformed: false);
    return !text.runes.any(
      (rune) => rune < 0x09 || (rune > 0x0D && rune < 0x20),
    );
  } on FormatException {
    return false;
  }
}
