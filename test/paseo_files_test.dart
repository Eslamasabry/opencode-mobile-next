// Files of a Claude Code / Paseo project: browse, read and find by name,
// against a scripted daemon (shapes from @getpaseo/protocol messages.js).
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show VcsDiffMode, VersionControlSetupState;
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;

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

  group('Changes', () {
    Map<String, dynamic> diffFile(
      String path, {
      bool isNew = false,
      bool isDeleted = false,
      String? status,
    }) => {
      'path': path,
      'isNew': isNew,
      'isDeleted': isDeleted,
      'additions': 2,
      'deletions': 1,
      'status': ?status,
      'hunks': [
        {
          'oldStart': 1,
          'oldCount': 2,
          'newStart': 1,
          'newCount': 3,
          'lines': [
            {'type': 'header', 'content': '@@ -1,2 +1,3 @@'},
            {'type': 'context', 'content': 'keep'},
            {'type': 'remove', 'content': 'old'},
            {'type': 'add', 'content': 'new'},
            {'type': 'add', 'content': 'newer'},
          ],
        },
      ],
    };

    void answerDiff(List<Map<String, dynamic>> files) {
      daemon.handlers['checkout.diff.get.request'] = (_) => (
        'checkout.diff.get.response',
        {'cwd': _dir, 'files': files, 'error': null},
      );
    }

    test('Paseo turns Changes on', () {
      expect(gateway.capabilities.sessionDiff, isTrue);
    });

    test('the working tree diff becomes a unified patch per file', () async {
      answerDiff([diffFile('lib/a.dart')]);
      final diffs = await gateway.listVcsDiffs(VcsDiffMode.workingTree);
      expect(diffs.single.file, 'lib/a.dart');
      expect(diffs.single.additions, 2);
      expect(diffs.single.deletions, 1);
      expect(
        diffs.single.patch,
        '--- a/lib/a.dart\n+++ b/lib/a.dart\n@@ -1,2 +1,3 @@\n keep\n-old\n+new\n+newer\n',
      );
      final sent = daemon.of('checkout.diff.get.request').single;
      expect(sent['cwd'], _dir);
      expect(sent['compare'], containsPair('mode', 'uncommitted'));
    });

    test('the branch view compares with the base branch', () async {
      answerDiff([diffFile('b.txt', isNew: true)]);
      final diffs = await gateway.listVcsDiffs(VcsDiffMode.branch);
      expect(diffs.single.status, 'added');
      expect(diffs.single.patch, startsWith('--- /dev/null\n+++ b/b.txt\n'));
      expect(
        daemon.of('checkout.diff.get.request').single['compare'],
        containsPair('mode', 'base'),
      );
    });

    test('file marks come from the same diff', () async {
      answerDiff([
        diffFile('a.dart'),
        diffFile('new.dart', isNew: true),
        diffFile('gone.dart', isDeleted: true),
      ]);
      final marks = await gateway.listFileStatuses();
      expect(marks.map((f) => f.status), ['modified', 'added', 'deleted']);
      expect(marks.first.additions, 2);
    });

    test('a binary file says it differs, without hunks', () async {
      answerDiff([
        {
          'path': 'logo.png',
          'isNew': false,
          'isDeleted': false,
          'additions': 0,
          'deletions': 0,
          'hunks': <Object>[],
          'status': 'binary',
        },
      ]);
      final diffs = await gateway.listVcsDiffs(VcsDiffMode.workingTree);
      expect(diffs.single.patch, contains('Binary files'));
    });

    test('an agent shows what changed in the folder it runs in', () async {
      daemon.handlers['fetch_agents_request'] = (_) => (
        'fetch_agents_response',
        {
          'entries': [
            {'agent': agentJson('a1', cwd: _dir)},
          ],
          'pageInfo': {'hasMore': false},
        },
      );
      answerDiff([diffFile('x.dart')]);
      await gateway.sessions();
      final diffs = await gateway.diff('a1');
      expect(diffs.single.file, 'x.dart');
      expect(daemon.of('checkout.diff.get.request').single['cwd'], _dir);
    });

    test('a worktree is read in its own folder', () async {
      answerDiff([diffFile('w.dart')]);
      final marks = await gateway.listWorktreeFileStatuses('/work/app-wt');
      expect(marks.single.path, 'w.dart');
      expect(
        daemon.of('checkout.diff.get.request').single['cwd'],
        '/work/app-wt',
      );
    });

    test('a diff the daemon refuses is an error, not an empty list', () async {
      daemon.handlers['checkout.diff.get.request'] = (_) => (
        'checkout.diff.get.response',
        {
          'cwd': _dir,
          'files': <Object>[],
          'error': {'code': 'NOT_GIT_REPO', 'message': 'not a git repo /x'},
        },
      );
      await expectLater(
        gateway.listVcsDiffs(VcsDiffMode.workingTree),
        throwsA(isA<PaseoFailure>()),
      );
    });

    test('branch and checkout health come from the checkout status', () async {
      daemon.handlers['checkout_status_request'] = (_) => (
        'checkout_status_response',
        {
          'cwd': _dir,
          'isGit': true,
          'isPaseoOwnedWorktree': false,
          'repoRoot': _dir,
          'currentBranch': 'feat/x',
          'isDirty': true,
          'baseRef': 'main',
          'error': null,
        },
      );
      answerDiff([diffFile('a.dart')]);
      final health = await gateway.loadVersionControlHealth();
      expect(health.branch, 'feat/x');
      expect(health.defaultBranch, 'main');
      expect(health.setupState, VersionControlSetupState.git);
      expect(health.changes, hasLength(1));
    });

    test('a folder that is not a repository reads as no git', () async {
      daemon.handlers['checkout_status_request'] = (_) => (
        'checkout_status_response',
        {
          'cwd': _dir,
          'isGit': false,
          'isPaseoOwnedWorktree': false,
          'repoRoot': null,
          'currentBranch': null,
          'isDirty': null,
          'baseRef': null,
          'error': null,
        },
      );
      final health = await gateway.loadVersionControlHealth();
      expect(health.setupState, VersionControlSetupState.absent);
    });
  });
}
