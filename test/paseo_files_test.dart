// Files of a Claude Code / Paseo project: browse, read and find by name,
// against a scripted daemon (shapes from @getpaseo/protocol messages.js).
import 'dart:async';
import 'dart:convert';

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

  group('Worktrees', () {
    test('Paseo turns New worktree on and keeps Reset off', () {
      expect(gateway.capabilities.worktreeCreate, isTrue);
      expect(gateway.capabilities.worktreeReset, isFalse);
    });

    test('the list reads the project\'s worktrees', () async {
      daemon.handlers['paseo_worktree_list_request'] = (_) => (
        'paseo_worktree_list_response',
        {
          'worktrees': [
            {
              'worktreePath': '/home/u/.paseo/worktrees/app/fix-login',
              'createdAt': '2026-10-01T10:00:00.000Z',
              'branchName': 'fix-login',
              'head': 'abc123',
            },
            {
              'worktreePath': '/home/u/.paseo/worktrees/app/detached',
              'createdAt': '2026-10-01T10:00:00.000Z',
              'branchName': null,
            },
          ],
          'error': null,
        },
      );
      final list = await gateway.listWorktrees(projectDirectory: _dir);
      expect(list.map((w) => w.name), ['fix-login', 'detached']);
      expect(list.first.branch, 'fix-login');
      expect(list.last.branch, isNull);
      expect(list.first.directory, '/home/u/.paseo/worktrees/app/fix-login');
      expect(daemon.of('paseo_worktree_list_request').single['cwd'], _dir);
    });

    Map<String, dynamic> created(String dir, String branch) => {
      'workspace': {
        'id': 'ws1',
        'projectId': 'p1',
        'projectDisplayName': 'app',
        'projectRootPath': _dir,
        'workspaceDirectory': dir,
        'gitRuntime': {'currentBranch': branch},
      },
      'error': null,
      'setupTerminalId': null,
    };

    test('creating one sends the name and comes back ready', () async {
      daemon.handlers['create_paseo_worktree_request'] = (_) => (
        'create_paseo_worktree_response',
        created('/home/u/.paseo/worktrees/app/mobile-review', 'mobile-review'),
      );
      final made = await gateway.createWorktree(
        projectDirectory: _dir,
        name: ' mobile-review ',
      );
      expect(made.name, 'mobile-review');
      expect(made.branch, 'mobile-review');
      final sent = daemon.of('create_paseo_worktree_request').single;
      expect(sent['cwd'], _dir);
      expect(sent['worktreeSlug'], 'mobile-review');
      expect(made.ready, isTrue);
    });

    test('a nameless worktree sends no slug', () async {
      daemon.handlers['create_paseo_worktree_request'] = (_) => (
        'create_paseo_worktree_response',
        created('/home/u/.paseo/worktrees/app/calm-otter', 'calm-otter'),
      );
      await gateway.createWorktree(projectDirectory: _dir, name: '');
      expect(
        daemon
            .of('create_paseo_worktree_request')
            .single
            .containsKey('worktreeSlug'),
        isFalse,
      );
    });

    test('a refused creation is a plain failure', () async {
      daemon.handlers['create_paseo_worktree_request'] = (_) => (
        'create_paseo_worktree_response',
        {'workspace': null, 'error': 'git said no /x', 'setupTerminalId': null},
      );
      await expectLater(
        gateway.createWorktree(projectDirectory: _dir, name: 'x'),
        throwsA(isA<PaseoFailure>()),
      );
    });

    test('removing one archives the worktree and its folder', () async {
      daemon.handlers['paseo_worktree_archive_request'] = (_) => (
        'paseo_worktree_archive_response',
        {'success': true, 'removedAgents': <String>[], 'error': null},
      );
      await gateway.removeWorktree(
        projectDirectory: _dir,
        directory: '/home/u/.paseo/worktrees/app/fix-login',
      );
      final sent = daemon.of('paseo_worktree_archive_request').single;
      expect(sent['worktreePath'], '/home/u/.paseo/worktrees/app/fix-login');
      expect(sent['repoRoot'], _dir);
      expect(sent['scope'], 'worktree');
    });

    test('a removal the daemon did not do is a failure', () async {
      daemon.handlers['paseo_worktree_archive_request'] = (_) => (
        'paseo_worktree_archive_response',
        {
          'success': false,
          'error': {'code': 'UNKNOWN', 'message': 'busy'},
        },
      );
      await expectLater(
        gateway.removeWorktree(projectDirectory: _dir, directory: '/x/y'),
        throwsA(isA<PaseoFailure>()),
      );
    });
  });

  group('Terminal', () {
    Map<String, dynamic> info(String id, {String? title}) => {
      'id': id,
      'name': 'Shell $id',
      'cwd': _dir,
      'title': ?title,
    };

    test(
      'Paseo turns the Terminal page on, but not shell commands in chat',
      () {
        expect(gateway.capabilities.terminal, isTrue);
        expect(gateway.capabilities.conversationShellOn, isFalse);
      },
    );

    test('the list shows this folder\'s terminals by title or name', () async {
      daemon.handlers['list_terminals_request'] = (_) => (
        'list_terminals_response',
        {
          'cwd': _dir,
          'terminals': [info('t1', title: 'npm run dev'), info('t2')],
        },
      );
      final list = await gateway.listTerminals();
      expect(list.map((t) => t.title), ['npm run dev', 'Shell t2']);
      expect(list.every((t) => t.running && t.directory == _dir), isTrue);
      expect(daemon.of('list_terminals_request').single['cwd'], _dir);
    });

    test('a new terminal opens in the folder under the given name', () async {
      daemon.handlers['create_terminal_request'] = (_) => (
        'create_terminal_response',
        {'terminal': info('t9', title: 'Terminal 1'), 'error': null},
      );
      final made = await gateway.createTerminal(title: 'Terminal 1');
      expect(made.id, 't9');
      final sent = daemon.of('create_terminal_request').single;
      expect(sent['cwd'], _dir);
      expect(sent['name'], 'Terminal 1');
    });

    test('renaming and closing send the terminal id', () async {
      daemon.handlers['terminal.rename.request'] = (_) =>
          ('terminal.rename.response', {'success': true, 'error': null});
      daemon.handlers['kill_terminal_request'] = (m) => (
        'kill_terminal_response',
        {'terminalId': m['terminalId'], 'success': true},
      );
      await gateway.renameTerminal('t1', ' logs ');
      await gateway.removeTerminal('t1');
      expect(daemon.of('terminal.rename.request').single['title'], 'logs');
      expect(daemon.of('kill_terminal_request').single['terminalId'], 't1');
    });

    test('closing a terminal that already ended is not a failure', () async {
      daemon.handlers['kill_terminal_request'] = (m) => (
        'kill_terminal_response',
        {'terminalId': m['terminalId'], 'success': false},
      );
      daemon.handlers['list_terminals_request'] = (_) =>
          ('list_terminals_response', {'cwd': _dir, 'terminals': <Object>[]});
      await gateway.removeTerminal('gone');
    });

    test(
      'closing a terminal that is still there but would not close fails',
      () async {
        daemon.handlers['kill_terminal_request'] = (m) => (
          'kill_terminal_response',
          {'terminalId': m['terminalId'], 'success': false},
        );
        daemon.handlers['list_terminals_request'] = (_) => (
          'list_terminals_response',
          {
            'cwd': _dir,
            'terminals': [info('stuck')],
          },
        );
        await expectLater(
          gateway.removeTerminal('stuck'),
          throwsA(isA<PaseoFailure>()),
        );
      },
    );

    test(
      'opening one subscribes, replays the screen, then streams output',
      () async {
        daemon.handlers['subscribe_terminal_request'] = (m) => (
          'subscribe_terminal_response',
          {'terminalId': m['terminalId'], 'slot': 3, 'error': null},
        );
        final channel = await gateway.connectTerminal('t1');
        final text = StringBuffer();
        channel.output.listen(text.write);
        final sent = daemon.of('subscribe_terminal_request').single;
        expect(sent['terminalId'], 't1');
        expect(sent['restore'], containsPair('mode', 'visible-snapshot'));

        daemon.pushBinary(0x05, 3, utf8.encode('\$ ls\r\n'));
        daemon.pushBinary(0x01, 9, utf8.encode('OTHER SLOT'));
        daemon.pushBinary(0x01, 3, utf8.encode('file.txt\r\n'));
        await Future<void>.delayed(Duration.zero);
        await Future<void>.delayed(Duration.zero);
        expect(text.toString(), '\x1bc\$ ls\r\nfile.txt\r\n');
        await channel.close();
      },
    );

    test('a character split across two frames arrives whole', () async {
      daemon.handlers['subscribe_terminal_request'] = (m) => (
        'subscribe_terminal_response',
        {'terminalId': m['terminalId'], 'slot': 1, 'error': null},
      );
      final channel = await gateway.connectTerminal('t1');
      final text = StringBuffer();
      channel.output.listen(text.write);
      final bytes = utf8.encode('é');
      daemon.pushBinary(0x01, 1, [bytes[0]]);
      daemon.pushBinary(0x01, 1, [bytes[1]]);
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);
      expect(text.toString(), 'é');
      await channel.close();
    });

    test('typing and resizing go to the terminal', () async {
      daemon.handlers['subscribe_terminal_request'] = (m) => (
        'subscribe_terminal_response',
        {'terminalId': m['terminalId'], 'slot': 2, 'error': null},
      );
      final channel = await gateway.connectTerminal('t1');
      channel.write('ls\r');
      await gateway.resizeTerminal('t1', rows: 30, cols: 90);
      final inputs = daemon.of('terminal_input');
      expect(inputs.first['terminalId'], 't1');
      expect(inputs.first['message'], {'type': 'input', 'data': 'ls\r'});
      expect(inputs.last['message'], {
        'type': 'resize',
        'rows': 30,
        'cols': 90,
      });
      await channel.close();
      expect(
        daemon.of('unsubscribe_terminal_request').single['terminalId'],
        't1',
      );
    });

    test('a shell that exits ends the stream', () async {
      daemon.handlers['subscribe_terminal_request'] = (m) => (
        'subscribe_terminal_response',
        {'terminalId': m['terminalId'], 'slot': 2, 'error': null},
      );
      final channel = await gateway.connectTerminal('t1');
      final done = Completer<void>();
      channel.output.listen((_) {}, onDone: done.complete);
      daemon.push('terminal_stream_exit', {'terminalId': 't1'});
      await done.future.timeout(const Duration(seconds: 2));
    });

    test('a terminal the daemon cannot open is a plain failure', () async {
      daemon.handlers['subscribe_terminal_request'] = (m) => (
        'subscribe_terminal_response',
        {'terminalId': m['terminalId'], 'error': 'Terminal not found'},
      );
      await expectLater(
        gateway.connectTerminal('nope'),
        throwsA(isA<PaseoFailure>()),
      );
    });
  });
}
