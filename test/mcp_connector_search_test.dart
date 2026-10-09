import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/mcp_catalog.dart';
import 'package:opencode_mobile/domain/mcp_connector_search.dart';
import 'package:opencode_mobile/domain/setup_registry.dart';

RegistryEntry _entry(
  String id, {
  String title = '',
  String description = '',
  String? runtime,
  String version = '1.0.0',
  bool needsKey = false,
}) => RegistryEntry.fromJson({
  'name': id,
  'version': version,
  'title': title,
  'description': description,
  if (runtime == 'hosted')
    'remotes': [
      {
        'type': 'streamable-http',
        'url': 'https://connector.example.test/mcp',
        if (needsKey)
          'headers': [
            {'name': 'Authorization', 'isRequired': true, 'isSecret': true},
          ],
      },
    ],
  if (runtime != null && runtime != 'hosted')
    'packages': [
      {
        'registryType': runtime,
        'identifier': 'test-package',
        'version': version,
      },
    ],
})!;

List<Map> _matches(Map<String, Object?> result) =>
    (result['matches']! as List).cast<Map>();

void main() {
  test(
    'unloaded catalogue is distinct from a loaded catalogue with no match',
    () {
      final unloaded = searchMcpConnectors(
        arguments: {'query': 'design'},
        entries: null,
        connected: null,
      );
      expect(unloaded, {
        'status': 'catalogue_not_loaded',
        'message': 'The connector catalogue could not be loaded right now. Try again in a moment.',
        'matches': <Object>[],
      });
      expect(
        searchMcpConnectors(
          arguments: {'query': 'design'},
          entries: const [],
          connected: null,
        ),
        {'status': 'ok', 'matches': <Object>[]},
      );
    },
  );

  test(
    'matches all case-insensitive tokens and returns canonical sorted IDs',
    () {
      final entries = [
        _entry(
          'io.test/zeta',
          title: 'Design references',
          description: 'Mobile',
        ),
        _entry(
          'io.test/alpha',
          title: 'Mobile design',
          description: 'References',
        ),
        _entry('io.test/unrelated', title: 'Design desktop'),
        _entry('invalid', title: 'Mobile design'),
      ];
      final result = searchMcpConnectors(
        arguments: {'query': '  DESIGN mobile  '},
        entries: entries,
        connected: null,
      );
      expect(result['status'], 'ok');
      expect(_matches(result).map((row) => row['catalogId']), [
        'io.test/alpha',
        'io.test/zeta',
      ]);
      expect(_matches(result).first['name'], 'Mobile design');
      expect(_matches(result).every((row) => row['connected'] == null), isTrue);
    },
  );

  test(
    'projects existing catalogue runtimes and never infers OAuth from keys',
    () {
      final entries = [
        _entry('io.test/hosted', runtime: 'hosted', needsKey: true),
        _entry('io.test/node', runtime: 'npm'),
        _entry('io.test/python', runtime: 'pypi'),
        _entry('io.test/container', runtime: 'oci'),
        _entry('io.test/none'),
      ];
      final inventory = {
        for (final entry in entries)
          McpCatalogItem.from(entry).serverName: false,
        'hosted': true,
        'unrelated': true,
      };
      final result = searchMcpConnectors(
        arguments: {'query': 'io.test', 'limit': 10},
        entries: entries,
        connected: inventory,
      );
      final rows = {for (final row in _matches(result)) row['catalogId']: row};
      expect(rows['io.test/hosted']!['runtime'], 'hosted');
      expect(rows['io.test/node']!['runtime'], 'npx');
      expect(rows['io.test/python']!['runtime'], 'uvx');
      expect(rows['io.test/container']!['runtime'], 'docker');
      expect(rows['io.test/none']!['runtime'], 'unavailable');
      expect(
        rows.values.every((row) => row['needsSignIn'] == 'unknown'),
        isTrue,
      );
      expect(rows['io.test/hosted']!['connected'], isTrue);
      expect(rows['io.test/node']!['connected'], isFalse);
      expect(inventory['unrelated'], isTrue);
    },
  );

  test(
    'default and requested limits apply after matching and deduplication',
    () {
      final entries = [
        _entry('io.test/item0'),
        _entry('io.test/item0', version: '2.0.0'),
        for (var i = 1; i < 12; i++) _entry('io.test/item$i'),
      ];
      expect(
        _matches(
          searchMcpConnectors(
            arguments: {'query': 'io.test'},
            entries: entries,
            connected: null,
          ),
        ),
        hasLength(5),
      );
      final rows = _matches(
        searchMcpConnectors(
          arguments: {'query': 'io.test', 'limit': 10},
          entries: entries,
          connected: null,
        ),
      );
      expect(rows, hasLength(10));
      expect(rows.map((row) => row['catalogId']).toSet(), hasLength(10));
    },
  );

  test('search inspects at most 100 records including nonmatching entries', () {
    var reads = 0;
    Iterable<RegistryEntry> entries() sync* {
      for (var i = 0; i < 100; i++) {
        reads++;
        yield _entry('io.test/miss$i');
      }
      throw StateError('Search scanned beyond its input budget');
    }

    final result = searchMcpConnectors(
      arguments: {'query': 'target'},
      entries: entries(),
      connected: null,
    );
    expect(_matches(result), isEmpty);
    expect(reads, 100);
  });

  test('prose is single-line, bounded by scalars, redacted and URL-free', () {
    final result = searchMcpConnectors(
      arguments: {'query': 'safe'},
      entries: [
        _entry(
          'io.test/safe',
          title: 'Safe https://title.example.test/path',
          description:
              'token=private-value\nhttps://secret.example.test/private '
              'www.example.test/page ${List.filled(350, '🎨').join()}',
        ),
      ],
      connected: const {},
    );
    final encoded = jsonEncode(result);
    expect(encoded, isNot(contains('private-value')));
    expect(encoded, isNot(contains('secret.example.test')));
    expect(encoded, isNot(contains('title.example.test')));
    expect(encoded, isNot(contains('www.example.test')));
    final description = _matches(result).single['description'] as String;
    expect(description.runes.length, lessThanOrEqualTo(300));
    expect(description, isNot(contains('\n')));
    expect(_matches(result).single['connected'], isFalse);
  });

  test('invalid requests fail with fixed copy and never echo input', () {
    final invalid = <Object?>[
      null,
      [],
      {},
      {'query': ''},
      {'query': '   '},
      {'query': 1},
      {'query': List.filled(101, '🎨').join()},
      {'query': 'safe', 'limit': 0},
      {'query': 'safe', 'limit': 11},
      {'query': 'safe', 'limit': 1.0},
      {'query': 'safe', 'extra': 'private-value'},
      {'query': 'token=private-value'},
      {'query': 'https://private.example.test/path'},
    ];
    for (final arguments in invalid) {
      expect(
        () => searchMcpConnectors(
          arguments: arguments,
          entries: null,
          connected: null,
        ),
        throwsA(
          isA<FormatException>().having(
            (error) => error.message,
            'fixed message',
            'Invalid connector search request.',
          ),
        ),
      );
    }
  });

  test('100 Unicode scalars are accepted regardless of UTF-16 length', () {
    expect(
      searchMcpConnectors(
        arguments: {'query': List.filled(100, '🎨').join(), 'limit': 1},
        entries: const [],
        connected: null,
      ),
      {'status': 'ok', 'matches': <Object>[]},
    );
  });

  test('search covers description text beyond the returned prose limit', () {
    final result = searchMcpConnectors(
      arguments: {'query': 'needle'},
      entries: [
        _entry(
          'io.test/long',
          description: '${List.filled(320, 'x').join()} needle',
        ),
      ],
      connected: null,
    );
    final row = _matches(result).single;
    expect(row['catalogId'], 'io.test/long');
    expect((row['description'] as String).runes.length, 300);
  });
}
