// The Project pages (Files, Changes, Worktrees, Terminal) on a Claude Code /
// Paseo server: the real screens over a real PaseoGateway and a scripted
// daemon. Each test taps what a person taps and reads what the daemon was
// asked. Pictures for the look gate: build/coverage/od-paseo-*.png.
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:opencode_mobile/ui/screens/project_hub_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_gateway_test.dart' show FakeDaemon;

const _dir = '/work/app';

class _Store extends ProfileStore {
  _Store({required super.prefs});

  @override
  List<ServerProfile> get profiles => const [];

  @override
  String? get activeId => null;
}

class _Rig {
  _Rig._(this.daemon, this.gateway, this.controller);

  final FakeDaemon daemon;
  final PaseoGateway gateway;
  final ConnectionController controller;

  static Future<_Rig> start() async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final daemon = FakeDaemon();
    // The Project tab counts running terminals beside the changed files.
    daemon.handlers['list_terminals_request'] = (_) =>
        ('list_terminals_response', {'cwd': _dir, 'terminals': <Object>[]});
    final gateway = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
      directory: _dir,
    );
    final controller = ConnectionController(_Store(prefs: prefs))
      ..api = gateway
      ..repository = gateway
      ..directory = _dir
      ..status = StreamStatus.connected;
    return _Rig._(daemon, gateway, controller);
  }

  Future<void> dispose() async {
    controller.dispose();
    gateway.close();
    await gateway.transport.close();
  }
}

Map<String, dynamic> _entry(String name, String kind, String path) => {
  'name': name,
  'path': path,
  'kind': kind,
  'size': 20,
  'modifiedAt': '2026-10-09T10:00:00.000Z',
};

const _rootKey = ValueKey('od-paseo-root');

Widget _app(Widget home) => RepaintBoundary(
  key: _rootKey,
  child: MaterialApp(
    theme: AppTheme.dark(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: home,
  ),
);

void _phone(WidgetTester tester) {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

Future<void> _shoot(WidgetTester tester, String name) async {
  final render =
      tester.renderObject(find.byKey(_rootKey)) as RenderRepaintBoundary;
  await tester.runAsync(() async {
    final image = await render.toImage();
    final png = await image.toByteData(format: ui.ImageByteFormat.png);
    Directory('build/coverage').createSync(recursive: true);
    File(
      'build/coverage/od-paseo-$name.png',
    ).writeAsBytesSync(png!.buffer.asUint8List());
  });
}

/// Ends a test: the screen leaves and the connection's timers stop, so the
/// framework finds nothing pending.
Future<void> _finish(WidgetTester tester, _Rig rig) async {
  await tester.pumpWidget(const SizedBox.shrink());
  await rig.dispose();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });

  group('Files', () {
    late _Rig rig;
    setUp(() async {
      rig = await _Rig.start();
      rig.daemon.handlers['file_explorer_request'] = (m) {
        if (m['mode'] == 'list') {
          final lib = m['path'] == 'lib';
          return (
            'file_explorer_response',
            {
              'cwd': _dir,
              'path': m['path'],
              'mode': 'list',
              'directory': {
                'path': m['path'],
                'entries': lib
                    ? [_entry('main.dart', 'file', 'lib/main.dart')]
                    : [
                        _entry('lib', 'directory', 'lib'),
                        _entry('README.md', 'file', 'README.md'),
                      ],
              },
              'file': null,
              'error': null,
            },
          );
        }
        return (
          'file_explorer_response',
          {
            'cwd': _dir,
            'path': m['path'],
            'mode': 'file',
            'directory': null,
            'file': {
              'path': m['path'],
              'kind': 'text',
              'encoding': 'utf-8',
              'content': 'void main() {\n  print("hi from the agent");\n}\n',
              'mimeType': 'text/plain',
              'size': 44,
              'modifiedAt': '2026-10-09T10:00:00.000Z',
            },
            'error': null,
          },
        );
      };
    });

    testWidgets('the folder lists, a folder opens and a file is read', (
      tester,
    ) async {
      _phone(tester);
      await tester.pumpWidget(
        _app(Scaffold(body: FilesScreen(controller: rig.controller))),
      );
      await tester.pumpAndSettle();
      expect(find.text('lib'), findsOneWidget);
      expect(find.text('README.md'), findsOneWidget);
      await _shoot(tester, 'files-list');

      await tester.tap(find.text('lib'));
      await tester.pumpAndSettle();
      expect(find.text('main.dart'), findsOneWidget);
      expect(
        rig.daemon.of('file_explorer_request').last['path'],
        'lib',
        reason: 'the folder that was tapped is the one asked for',
      );

      await tester.tap(find.text('main.dart'));
      await tester.pumpAndSettle();
      expect(find.textContaining('hi from the agent'), findsOneWidget);
      final read = rig.daemon
          .of('file_explorer_request')
          .where((m) => m['mode'] == 'file')
          .single;
      expect(read['path'], 'lib/main.dart');
      expect(read['cwd'], _dir);
      await _shoot(tester, 'files-viewer');
      await _finish(tester, rig);
    });

    testWidgets('a folder the daemon cannot read says so and offers retry', (
      tester,
    ) async {
      _phone(tester);
      rig.daemon.handlers['file_explorer_request'] = (_) => (
        'file_explorer_response',
        {
          'cwd': _dir,
          'path': '.',
          'mode': 'list',
          'directory': null,
          'file': null,
          'error': 'ENOENT /home/secret',
        },
      );
      await tester.pumpWidget(
        _app(Scaffold(body: FilesScreen(controller: rig.controller))),
      );
      await tester.pumpAndSettle();
      expect(find.text('Try again'), findsOneWidget);
      expect(find.textContaining('ENOENT'), findsNothing);
      expect(find.textContaining('/home/secret'), findsNothing);
      await _shoot(tester, 'files-failed');
      await _finish(tester, rig);
    });
  });

  group('Changes', () {
    late _Rig rig;
    setUp(() async {
      rig = await _Rig.start();
      rig.daemon.handlers['checkout.diff.get.request'] = (_) => (
        'checkout.diff.get.response',
        {
          'cwd': _dir,
          'files': [
            {
              'path': 'lib/greet.dart',
              'isNew': false,
              'isDeleted': false,
              'additions': 2,
              'deletions': 1,
              'hunks': [
                {
                  'oldStart': 1,
                  'oldCount': 2,
                  'newStart': 1,
                  'newCount': 3,
                  'lines': [
                    {'type': 'header', 'content': '@@ -1,2 +1,3 @@'},
                    {'type': 'context', 'content': 'void greet() {'},
                    {'type': 'remove', 'content': '  print("hi");'},
                    {'type': 'add', 'content': '  print("hello");'},
                    {'type': 'add', 'content': '  print("bye");'},
                    {'type': 'context', 'content': '}'},
                  ],
                },
              ],
            },
          ],
          'error': null,
        },
      );
    });

    testWidgets('the Project tab counts the changes and Changes opens them', (
      tester,
    ) async {
      _phone(tester);
      await tester.pumpWidget(
        _app(Scaffold(body: ProjectHub(controller: rig.controller))),
      );
      await tester.pumpAndSettle();
      expect(find.text('1 file changed'), findsOneWidget);
      await _shoot(tester, 'project-tab');

      await tester.tap(find.byKey(const ValueKey('project-hub-changes')));
      await tester.pumpAndSettle();
      expect(find.textContaining('greet.dart'), findsWidgets);
      expect(find.textContaining('print("hello")'), findsOneWidget);
      expect(find.textContaining('print("hi")'), findsOneWidget);
      final asked = rig.daemon.of('checkout.diff.get.request');
      expect(asked, isNotEmpty);
      expect(asked.every((m) => m['cwd'] == _dir), isTrue);
      await _shoot(tester, 'changes-review');
      await _finish(tester, rig);
    });

    testWidgets('the Files list marks the changed file', (tester) async {
      _phone(tester);
      rig.daemon.handlers['file_explorer_request'] = (m) => (
        'file_explorer_response',
        {
          'cwd': _dir,
          'path': '.',
          'mode': 'list',
          'directory': {
            'path': '.',
            'entries': [_entry('lib', 'directory', 'lib')],
          },
          'file': null,
          'error': null,
        },
      );
      await tester.pumpWidget(
        _app(Scaffold(body: FilesScreen(controller: rig.controller))),
      );
      await tester.pumpAndSettle();
      expect(
        find.text('File change indicators are unavailable on this server.'),
        findsNothing,
      );
      expect(find.byKey(const ValueKey('files-statuses-error')), findsNothing);
      await _shoot(tester, 'files-with-changes');
      await _finish(tester, rig);
    });

    testWidgets('a checkout the daemon cannot diff says so in plain words', (
      tester,
    ) async {
      _phone(tester);
      rig.daemon.handlers['checkout.diff.get.request'] = (_) => (
        'checkout.diff.get.response',
        {
          'cwd': _dir,
          'files': <Object>[],
          'error': {'code': 'NOT_GIT_REPO', 'message': 'fatal: /home/x'},
        },
      );
      await tester.pumpWidget(
        _app(Scaffold(body: ProjectHub(controller: rig.controller))),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('project-hub-changes')));
      await tester.pumpAndSettle();
      expect(find.textContaining('fatal'), findsNothing);
      expect(find.textContaining('/home/x'), findsNothing);
      await _shoot(tester, 'changes-failed');
      await _finish(tester, rig);
    });
  });

  group('Worktrees', () {
    const wt = '/home/u/.paseo/worktrees/app/fix-login';
    late _Rig rig;
    var listed = <Map<String, dynamic>>[];

    setUp(() async {
      rig = await _Rig.start();
      listed = [
        {
          'worktreePath': wt,
          'createdAt': '2026-10-01T10:00:00.000Z',
          'branchName': 'fix-login',
        },
      ];
      rig.daemon.handlers['paseo_worktree_list_request'] = (_) => (
        'paseo_worktree_list_response',
        {'worktrees': listed, 'error': null},
      );
      rig.daemon.handlers['checkout.diff.get.request'] = (m) => (
        'checkout.diff.get.response',
        {'cwd': m['cwd'], 'files': <Object>[], 'error': null},
      );
    });

    Future<void> openWorktrees(WidgetTester tester) async {
      await tester.pumpWidget(
        _app(Scaffold(body: ProjectHub(controller: rig.controller))),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('project-hub-worktrees')));
      await tester.pumpAndSettle();
    }

    testWidgets('Worktrees lists what the daemon made', (tester) async {
      _phone(tester);
      await openWorktrees(tester);
      expect(find.byKey(const ValueKey('worktree-$wt')), findsOneWidget);
      expect(find.text('fix-login'), findsWidgets);
      expect(rig.daemon.of('paseo_worktree_list_request').single['cwd'], _dir);
      expect(find.text('Reset'), findsNothing);
      await _shoot(tester, 'worktrees-list');
      await _finish(tester, rig);
    });

    testWidgets('New worktree names it and the row is ready at once', (
      tester,
    ) async {
      _phone(tester);
      const made = '/home/u/.paseo/worktrees/app/mobile-review';
      rig.daemon.handlers['create_paseo_worktree_request'] = (_) {
        listed = [
          ...listed,
          {
            'worktreePath': made,
            'createdAt': '2026-10-02T10:00:00.000Z',
            'branchName': 'mobile-review',
          },
        ];
        return (
          'create_paseo_worktree_response',
          {
            'workspace': {
              'id': 'ws2',
              'projectId': 'p1',
              'projectDisplayName': 'app',
              'projectRootPath': _dir,
              'workspaceDirectory': made,
              'gitRuntime': {'currentBranch': 'mobile-review'},
            },
            'error': null,
            'setupTerminalId': null,
          },
        );
      };
      await openWorktrees(tester);
      await tester.tap(find.byKey(const ValueKey('create-worktree')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('worktree-name-field')),
        'mobile-review',
      );
      await tester.tap(find.byKey(const ValueKey('confirm-create-worktree')));
      await tester.pumpAndSettle();
      final sent = rig.daemon.of('create_paseo_worktree_request').single;
      expect(sent['cwd'], _dir);
      expect(sent['worktreeSlug'], 'mobile-review');
      expect(find.byKey(const ValueKey('worktree-$made')), findsOneWidget);
      expect(find.textContaining('is ready'), findsOneWidget);
      await _shoot(tester, 'worktrees-created');
      await _finish(tester, rig);
    });

    testWidgets('Delete asks by name, then removes that worktree', (
      tester,
    ) async {
      _phone(tester);
      rig.daemon.handlers['paseo_worktree_archive_request'] = (_) {
        listed = [];
        return (
          'paseo_worktree_archive_response',
          {'success': true, 'removedAgents': <String>[], 'error': null},
        );
      };
      await openWorktrees(tester);
      await tester.longPress(find.byKey(const ValueKey('worktree-$wt')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Delete'));
      await tester.pumpAndSettle();
      expect(find.textContaining('fix-login'), findsWidgets);
      await _shoot(tester, 'worktrees-remove-ask');
      await tester.enterText(
        find.byKey(const ValueKey('kit-confirm-typed-name')),
        'fix-login',
      );
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('confirm-remove-worktree')));
      await tester.pumpAndSettle();
      final sent = rig.daemon.of('paseo_worktree_archive_request').single;
      expect(sent['worktreePath'], wt);
      expect(sent['repoRoot'], _dir);
      expect(find.byKey(const ValueKey('worktree-$wt')), findsNothing);
      await _finish(tester, rig);
    });

    testWidgets('a worktree list that fails says so and can retry', (
      tester,
    ) async {
      _phone(tester);
      rig.daemon.handlers['paseo_worktree_list_request'] = (_) => (
        'paseo_worktree_list_response',
        {
          'worktrees': <Object>[],
          'error': {'code': 'NOT_GIT_REPO', 'message': 'fatal: /home/x'},
        },
      );
      await openWorktrees(tester);
      expect(find.text('Try again'), findsOneWidget);
      expect(find.textContaining('fatal'), findsNothing);
      await _shoot(tester, 'worktrees-failed');
      await _finish(tester, rig);
    });
  });

  group('Terminal', () {
    late _Rig rig;
    var terminals = <Map<String, dynamic>>[];

    Map<String, dynamic> info(String id, String title) => {
      'id': id,
      'name': title,
      'title': title,
      'cwd': _dir,
    };

    setUp(() async {
      rig = await _Rig.start();
      terminals = [info('t1', 'npm run dev')];
      rig.daemon.handlers['list_terminals_request'] = (_) =>
          ('list_terminals_response', {'cwd': _dir, 'terminals': terminals});
      rig.daemon.handlers['subscribe_terminal_request'] = (m) => (
        'subscribe_terminal_response',
        {'terminalId': m['terminalId'], 'slot': 4, 'error': null},
      );
      rig.daemon.handlers['kill_terminal_request'] = (m) {
        terminals = [];
        return (
          'kill_terminal_response',
          {'terminalId': m['terminalId'], 'success': true},
        );
      };
    });

    Future<void> openTerminals(WidgetTester tester) async {
      await tester.pumpWidget(
        _app(Scaffold(body: ProjectHub(controller: rig.controller))),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('project-hub-terminal')));
      await tester.pumpAndSettle();
    }

    testWidgets('Terminal lists the daemon\'s terminals', (tester) async {
      _phone(tester);
      await openTerminals(tester);
      expect(find.text('npm run dev'), findsOneWidget);
      expect(find.text('Running · app'), findsOneWidget);
      expect(rig.daemon.of('list_terminals_request').first['cwd'], _dir);
      await _shoot(tester, 'terminal-list');
      await _finish(tester, rig);
    });

    testWidgets('opening one shows what the daemon streams and sends typing', (
      tester,
    ) async {
      _phone(tester);
      await openTerminals(tester);
      await tester.tap(find.text('npm run dev'));
      await tester.pumpAndSettle();
      expect(
        rig.daemon.of('subscribe_terminal_request').single['terminalId'],
        't1',
      );
      await tester.tap(find.byKey(const ValueKey('terminal-surface-menu')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('terminal-accessible-mode')));
      await tester.pumpAndSettle();

      rig.daemon.pushBinary(0x05, 4, utf8.encode('app\$ npm run dev\r\n'));
      rig.daemon.pushBinary(0x01, 4, utf8.encode('ready on port 3000\r\n'));
      await tester.pump();
      await tester.pump();
      await tester.pump();
      expect(find.textContaining('ready on port 3000'), findsOneWidget);
      await _shoot(tester, 'terminal-output');

      final input = find.descendant(
        of: find.byKey(const Key('terminal-accessible-input')),
        matching: find.byType(TextField),
      );
      await tester.enterText(input, 'ls');
      await tester.testTextInput.receiveAction(TextInputAction.send);
      await tester.pump();
      final typed = rig.daemon
          .of('terminal_input')
          .where((m) => (m['message'] as Map)['type'] == 'input');
      expect(typed.single['terminalId'], 't1');
      expect((typed.single['message'] as Map)['data'], 'ls\r');
      await _finish(tester, rig);
    });

    testWidgets('New terminal asks the daemon for one in the folder', (
      tester,
    ) async {
      _phone(tester);
      rig.daemon.handlers['create_terminal_request'] = (_) {
        terminals = [...terminals, info('t2', 'Terminal 2')];
        return (
          'create_terminal_response',
          {'terminal': info('t2', 'Terminal 2'), 'error': null},
        );
      };
      await openTerminals(tester);
      await tester.tap(find.byKey(const ValueKey('terminal-new')).first);
      await tester.pumpAndSettle();
      final sent = rig.daemon.of('create_terminal_request').single;
      expect(sent['cwd'], _dir);
      expect(
        rig.daemon.of('subscribe_terminal_request').single['terminalId'],
        't2',
      );
      await _finish(tester, rig);
    });

    testWidgets('Rename asks for the new name and sends it', (tester) async {
      _phone(tester);
      rig.daemon.handlers['terminal.rename.request'] = (m) {
        terminals = [info('t1', m['title'] as String)];
        return ('terminal.rename.response', {'success': true, 'error': null});
      };
      await openTerminals(tester);
      await tester.longPress(find.text('npm run dev'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('terminal-menu-rename')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('terminal-rename-field')),
        'dev server',
      );
      await tester.tap(find.byKey(const ValueKey('terminal-rename-confirm')));
      await tester.pumpAndSettle();
      final sent = rig.daemon.of('terminal.rename.request').single;
      expect(sent['terminalId'], 't1');
      expect(sent['title'], 'dev server');
      expect(find.text('dev server'), findsOneWidget);
      await _finish(tester, rig);
    });

    testWidgets('Stop names the terminal and then closes that one', (
      tester,
    ) async {
      _phone(tester);
      await openTerminals(tester);
      await tester.longPress(find.text('npm run dev'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('terminal-menu-remove')));
      await tester.pumpAndSettle();
      expect(find.textContaining('npm run dev'), findsWidgets);
      await _shoot(tester, 'terminal-stop-ask');
      await tester.tap(find.byKey(const ValueKey('terminal-remove-confirm')));
      await tester.pumpAndSettle();
      expect(rig.daemon.of('kill_terminal_request').single['terminalId'], 't1');
      expect(find.text('npm run dev'), findsNothing);
      await _finish(tester, rig);
    });

    testWidgets('a daemon with no list says so and offers a retry', (
      tester,
    ) async {
      _phone(tester);
      rig.daemon.handlers['list_terminals_request'] = (_) => (
        'list_terminals_response',
        {'cwd': _dir, 'error': 'boom /secret', 'terminals': <Object>[]},
      );
      await openTerminals(tester);
      expect(find.textContaining('boom'), findsNothing);
      expect(find.textContaining('/secret'), findsNothing);
      await _shoot(tester, 'terminal-failed');
      await _finish(tester, rig);
    });
  });
}
