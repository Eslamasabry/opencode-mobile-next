// Files of a Claude Code / Paseo project: browse, read and find by name,
// against a scripted daemon (shapes from @getpaseo/protocol messages.js).
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon;

const _dir = '/work/app';

Map<String, dynamic> _entry(String name, String kind, {String? path}) => {
  'name': name,
  'path': path ?? name,
  'kind': kind,
  'size': 12,
  'modifiedAt': '2026-10-09T10:00:00.000Z',
};

void main() {
  late FakeDaemon daemon;
  late PaseoGateway gateway;

  setUp(() {
    daemon = FakeDaemon();
    gateway = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
      directory: _dir,
    );
  });
  tearDown(() => gateway.close());

  test('Paseo turns Files on', () {
    expect(gateway.capabilities.fileBrowsing, isTrue);
  });

  test(
    'listing a folder asks the daemon for it and puts folders first',
    () async {
      daemon.handlers['file_explorer_request'] = (_) => (
        'file_explorer_response',
        {
          'cwd': _dir,
          'path': 'lib',
          'mode': 'list',
          'directory': {
            'path': 'lib',
            'entries': [
              _entry('main.dart', 'file', path: 'lib/main.dart'),
              _entry('src', 'directory', path: 'lib/src'),
              _entry('App.dart', 'file', path: 'lib/App.dart'),
            ],
          },
          'file': null,
          'error': null,
        },
      );
      final nodes = await gateway.listFiles('lib');
      expect(nodes.map((n) => n.name), ['src', 'App.dart', 'main.dart']);
      expect(nodes.first.isDir, isTrue);
      expect(nodes.first.path, 'lib/src');
      final sent = daemon.of('file_explorer_request').single;
      expect(sent['cwd'], _dir);
      expect(sent['path'], 'lib');
      expect(sent['mode'], 'list');
    },
  );

  test('the project root and absolute paths become relative ones', () async {
    daemon.handlers['file_explorer_request'] = (_) => (
      'file_explorer_response',
      {
        'cwd': _dir,
        'path': '.',
        'mode': 'list',
        'directory': {'path': '.', 'entries': <Object>[]},
        'file': null,
        'error': null,
      },
    );
    await gateway.listFiles('');
    await gateway.listFiles('$_dir/lib/');
    await gateway.listFiles(_dir);
    expect(daemon.of('file_explorer_request').map((m) => m['path']), [
      '.',
      'lib/',
      '.',
    ]);
  });

  test(
    'reading a text file returns its text, with a size limit sent',
    () async {
      daemon.handlers['file_explorer_request'] = (_) => (
        'file_explorer_response',
        {
          'cwd': _dir,
          'path': 'a.txt',
          'mode': 'file',
          'directory': null,
          'file': {
            'path': 'a.txt',
            'kind': 'text',
            'encoding': 'utf-8',
            'content': 'hello\nworld',
            'mimeType': 'text/plain',
            'size': 11,
            'modifiedAt': '2026-10-09T10:00:00.000Z',
          },
          'error': null,
        },
      );
      final content = await gateway.fileContent('$_dir/a.txt');
      expect(content.content, 'hello\nworld');
      expect(content.isBinary, isFalse);
      final sent = daemon.of('file_explorer_request').single;
      expect(sent['path'], 'a.txt');
      expect(sent['mode'], 'file');
      expect(sent['maxBytes'], greaterThan(0));
    },
  );

  test('a picture comes back as base64 and a binary has no text', () async {
    daemon.handlers['file_explorer_request'] = (m) => (
      'file_explorer_response',
      {
        'cwd': _dir,
        'path': m['path'],
        'mode': 'file',
        'directory': null,
        'file': m['path'] == 'p.png'
            ? {
                'path': 'p.png',
                'kind': 'image',
                'encoding': 'base64',
                'content': 'AAEC',
                'mimeType': 'image/png',
                'size': 3,
                'modifiedAt': '2026-10-09T10:00:00.000Z',
              }
            : {
                'path': 'x.bin',
                'kind': 'binary',
                'encoding': 'none',
                'mimeType': 'application/octet-stream',
                'size': 3,
                'modifiedAt': '2026-10-09T10:00:00.000Z',
              },
        'error': null,
      },
    );
    final image = await gateway.fileContent('p.png');
    expect(image.encoding, 'base64');
    expect(image.bytes(), [0, 1, 2]);
    final binary = await gateway.fileContent('x.bin');
    expect(binary.isBinary, isTrue);
    expect(binary.content, isEmpty);
  });

  test('a refused read says it plainly, never the daemon text', () async {
    daemon.handlers['file_explorer_request'] = (_) => (
      'file_explorer_response',
      {
        'cwd': _dir,
        'path': 'big.log',
        'mode': 'file',
        'directory': null,
        'file': null,
        'error': 'File is too large to display /secret/path',
      },
    );
    await expectLater(
      gateway.fileContent('big.log'),
      throwsA(
        isA<PaseoFailure>().having(
          (e) => e.message,
          'message',
          isNot(contains('/secret')),
        ),
      ),
    );
  });

  test('finding by name lists files only', () async {
    daemon.handlers['directory_suggestions_request'] = (_) => (
      'directory_suggestions_response',
      {
        'directories': <String>[],
        'entries': [
          {'path': 'lib/main.dart', 'kind': 'file'},
          {'path': 'lib', 'kind': 'directory'},
        ],
        'error': null,
      },
    );
    expect(await gateway.findFile('main'), ['lib/main.dart']);
    final sent = daemon.of('directory_suggestions_request').single;
    expect(sent['cwd'], _dir);
    expect(sent['includeFiles'], isTrue);
    expect(sent['query'], 'main');
    expect(await gateway.findFile('  '), isEmpty);
    expect(daemon.of('directory_suggestions_request'), hasLength(1));
  });

  test('a folder change while a listing runs drops the answer', () async {
    daemon.handlers['file_explorer_request'] = (_) => (
      'file_explorer_response',
      {
        'cwd': _dir,
        'path': '.',
        'mode': 'list',
        'directory': {'path': '.', 'entries': <Object>[]},
        'file': null,
        'error': null,
      },
    );
    final pending = gateway.listFiles('');
    gateway.setLocation(directory: '/work/other');
    await expectLater(
      pending,
      throwsA(
        isA<PaseoFailure>().having(
          (e) => e.kind,
          'kind',
          PaseoFailureKind.scopeMismatch,
        ),
      ),
    );
  });
}
