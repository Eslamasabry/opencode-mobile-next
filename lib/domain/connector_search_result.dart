import 'dart:convert';

/// How a connector search step ended, as the helper reports it
/// (docs/design/BD3-mcp-chat-contract.md "Agent connector search").
enum ConnectorSearchStatus { ok, catalogueNotLoaded, unavailable }

/// One match in plain fields. Registry prose is untrusted: the helper already
/// strips URLs and secrets, and this reads only bounded text.
final class ConnectorSearchMatch {
  const ConnectorSearchMatch({
    required this.name,
    required this.runtime,
    required this.connected,
  });

  final String name;

  /// `hosted`, `npx`, `uvx`, `docker` or `unavailable`.
  final String runtime;

  /// Null when the server's list could not be confirmed.
  final bool? connected;
}

final class ConnectorSearchResult {
  const ConnectorSearchResult(this.status, this.matches);
  final ConnectorSearchStatus status;
  final List<ConnectorSearchMatch> matches;
}

/// Reads a finished connector search from its structured output or its JSON
/// text; null when the output is not a search result (a refused request, a
/// failed call, anything else), so the step falls back to its plain output.
ConnectorSearchResult? parseConnectorSearchResult({
  Object? value,
  String? text,
}) {
  Object? data = value;
  if (data is! Map) {
    final raw = text?.trim();
    if (raw == null || raw.isEmpty || raw.length > 32768) return null;
    try {
      data = jsonDecode(raw);
    } catch (_) {
      return null;
    }
  }
  if (data is! Map) return null;
  final status = switch (data['status']) {
    'ok' => ConnectorSearchStatus.ok,
    'catalogue_not_loaded' => ConnectorSearchStatus.catalogueNotLoaded,
    'unavailable' => ConnectorSearchStatus.unavailable,
    _ => null,
  };
  if (status == null) return null;
  final rows = data['matches'];
  final matches = <ConnectorSearchMatch>[];
  if (status == ConnectorSearchStatus.ok && rows is List) {
    for (final row in rows.take(10)) {
      if (row is! Map) continue;
      final name = row['name'];
      final id = row['catalogId'];
      final label = name is String && name.trim().isNotEmpty
          ? name.trim()
          : id is String
          ? id.trim()
          : '';
      if (label.isEmpty) continue;
      final runtime = row['runtime'];
      final connected = row['connected'];
      matches.add(
        ConnectorSearchMatch(
          name: String.fromCharCodes(label.runes.take(120)),
          runtime: runtime is String ? runtime : 'unavailable',
          connected: connected is bool ? connected : null,
        ),
      );
    }
  }
  return ConnectorSearchResult(status, matches);
}
