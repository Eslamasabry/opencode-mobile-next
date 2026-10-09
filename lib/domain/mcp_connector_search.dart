import '../ui/kit/kit_redact.dart';
import 'mcp_catalog.dart';
import 'setup_registry.dart';

final _catalogId = RegExp(r'^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$');
final _url = RegExp(
  r'\b(?:[a-z][a-z0-9+.-]*:(?://)?|www\.)[^\s<>]+',
  caseSensitive: false,
);
final _spacing = RegExp(
  r'[\s\x00-\x1f\x7f-\x9f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u206f]+',
);

const _invalidRequest = FormatException('Invalid connector search request.');

/// Bounded, read-only discovery in metadata already loaded by the person.
///
/// The result deliberately excludes endpoint URLs, commands, settings, and
/// credential values. Registry metadata does not establish OAuth support.
Map<String, Object?> searchMcpConnectors({
  required Object? arguments,
  required Iterable<RegistryEntry>? entries,
  required Map<String, bool>? connected,
}) {
  if (arguments is! Map ||
      arguments.keys.any((key) => key != 'query' && key != 'limit')) {
    throw _invalidRequest;
  }
  final rawQuery = arguments['query'];
  final limit = arguments.containsKey('limit') ? arguments['limit'] : 5;
  if (rawQuery is! String || limit is! int || limit < 1 || limit > 10) {
    throw _invalidRequest;
  }
  final query = rawQuery.trim();
  if (query.isEmpty ||
      query.runes.length > 100 ||
      KitRedact.containsSecret(query) ||
      _url.hasMatch(query)) {
    throw _invalidRequest;
  }
  final terms = _prose(query, 100).toLowerCase().split(' ');
  if (terms.every((term) => term.isEmpty)) throw _invalidRequest;
  if (entries == null) {
    return {
      'status': 'catalogue_not_loaded',
      'message': 'Catalogue not loaded. Open Tools > MCP to load it.',
      'matches': <Map<String, Object?>>[],
    };
  }

  final candidates =
      <({RegistryEntry entry, String title, String description})>[];
  for (final entry in entries.take(100)) {
    if (entry.name.length > 256 ||
        !_catalogId.hasMatch(entry.name) ||
        KitRedact.containsSecret(entry.name)) {
      continue;
    }
    final title = _prose(entry.title, 120);
    final fullDescription = _prose(entry.description, 1000);
    final searchable = '${entry.name} $title $fullDescription'.toLowerCase();
    if (!terms.every(searchable.contains)) continue;
    candidates.add((
      entry: entry,
      title: title,
      description: String.fromCharCodes(fullDescription.runes.take(300)),
    ));
  }
  // Resolve duplicate versions consistently without exposing version or using
  // an unbounded sort. At most the 100 inspected records can reach this list.
  candidates.sort((a, b) {
    var order = a.entry.name.toLowerCase().compareTo(
      b.entry.name.toLowerCase(),
    );
    if (order != 0) return order;
    order = a.entry.id.compareTo(b.entry.id);
    if (order != 0) return order;
    order = a.title.compareTo(b.title);
    return order != 0 ? order : a.description.compareTo(b.description);
  });
  final seen = <String>{};
  final matches = <Map<String, Object?>>[];
  for (final candidate in candidates) {
    final entry = candidate.entry;
    if (!seen.add(entry.name)) continue;
    final item = McpCatalogItem.from(entry);
    matches.add({
      'catalogId': entry.name,
      'name': candidate.title.isEmpty ? item.serverName : candidate.title,
      'description': candidate.description,
      'runtime': switch (item.runtime) {
        McpCatalogRuntime.hosted => 'hosted',
        McpCatalogRuntime.node => 'npx',
        McpCatalogRuntime.python => 'uvx',
        McpCatalogRuntime.docker => 'docker',
        McpCatalogRuntime.none => 'unavailable',
      },
      'needsSignIn': 'unknown',
      'connected': connected == null
          ? null
          : (connected[item.serverName] ?? false),
    });
    if (matches.length == limit) break;
  }
  return {'status': 'ok', 'matches': matches};
}

String _prose(String value, int maxScalars) {
  final safe = KitRedact.text(
    KitRedact.text(value).replaceAll(_url, ' ').replaceAll(_spacing, ' '),
  ).trim();
  return String.fromCharCodes(safe.runes.take(maxScalars));
}
