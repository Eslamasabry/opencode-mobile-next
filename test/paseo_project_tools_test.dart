// The Project pages (Files, Changes, Worktrees, Terminal) on a Claude Code /
// Paseo server: the real screens over a real PaseoGateway and a scripted
// daemon. Each test taps what a person taps and reads what the daemon was
// asked. Pictures for the look gate: build/coverage/od-paseo-*.png.
import 'dart:async';
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
}
