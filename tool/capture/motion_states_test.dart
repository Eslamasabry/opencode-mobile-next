// Before/after captures for the empty, quiet and failure moments (design
// standard §10; docs/design/motion-and-illustration-2026-09-25.md, slice C):
// the real screens at 412x915 dp, dark and light, real fonts, no server.
//
// This file only drives the public screens with controller and API state,
// so the same file renders the old code and the new:
//
//   flutter test --concurrency=1 --dart-define=MOTION_STATES_CAPTURE=before \
//     tool/capture/motion_states_test.dart        # on the old commit
//   flutter test --concurrency=1 tool/capture/motion_states_test.dart
//
// Output: docs/qa/motion-states-2026-09-25/<before|after>-<n>-<state>-<theme>.png
//
// ignore_for_file: invalid_use_of_visible_for_testing_member

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/local_terminal.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:opencode_mobile/ui/screens/global_sessions_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/local_terminal_screen.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../test/support/fake_local_terminal.dart';
import '../../test/support/work_tab_fixture.dart';
import 'fixtures.dart';

const _phase = String.fromEnvironment(
  'MOTION_STATES_CAPTURE',
  defaultValue: 'after',
);
const _out = 'docs/qa/motion-states-2026-09-25';

/// Conversations across projects: none, or a failure.
class _Finder extends CaptureRepository {
  _Finder({this.error});

  final Object? error;

  @override
  Future<ServerPage<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived = false,
    String? cursor,
    int limit = 50,
  }) async {
    if (error case final error?) throw error;
    return const ServerPage(items: []);
  }
}

class _Linux extends BuiltinLinux {
  _Linux({required this.installed});

  final bool installed;

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: installed,
    phase: installed ? BuiltinLinuxPhase.ready : BuiltinLinuxPhase.idle,
    services: const [],
  );
}

class _ChatApi extends CaptureApi {
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

void _mockSecureStorage(WidgetTester tester) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secure,
      null,
    ),
  );
}

Future<void> _settle(WidgetTester tester, [int frames = 14]) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

/// Pumps [home] at 412x915 and writes the capture.
Future<void> _shoot(
  WidgetTester tester,
  String name, {
  required bool light,
  required Widget home,
  required ConnectionController controller,
  Widget Function(Widget app)? wrap,
  Future<void> Function()? before,
}) async {
  _mockSecureStorage(tester);
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final key = GlobalKey();
  try {
    final app = captureApp(
      home: home,
      boundaryKey: key,
      controller: controller,
      light: light,
    );
    await tester.pumpWidget(wrap == null ? app : wrap(app));
    await _settle(tester);
    await before?.call();
    await _settle(tester);
    expect(tester.takeException(), isNull);
    await writePng(
      '$_out/$_phase-$name-${light ? 'light' : 'dark'}.png',
      await capturePng(tester, key, pixelRatio: 1),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
    controller.dispose();
  }
}

Future<CaptureController> _connected({
  CaptureApi? api,
  CaptureRepository? repository,
}) async {
  SharedPreferences.setMockInitialValues({});
  return captureController(
    prefs: await SharedPreferences.getInstance(),
    api: api,
    repository: repository,
  );
}

Scaffold _page(String title, Widget body) => Scaffold(
  appBar: AppBar(title: Text(title)),
  body: body,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    testWidgets('1 work, no conversations yet · $mode', (tester) async {
      await _shoot(
        tester,
        '1-work-empty',
        light: light,
        controller: await workController(),
        home: const HomeScreen(initialTab: 0),
      );
    });

    testWidgets('2 work, no project yet · $mode', (tester) async {
      await _shoot(
        tester,
        '2-work-no-project',
        light: light,
        controller: await workController(
          directory: null,
          savedLocation: false,
          repository: WorkRepository()..projects = const [],
        ),
        home: const HomeScreen(initialTab: 0),
      );
    });

    testWidgets('3 work, not answering, nothing listed · $mode', (
      tester,
    ) async {
      final controller = await workController(
        status: StreamStatus.reconnecting,
      );
      await _shoot(
        tester,
        '3-work-not-answering',
        light: light,
        controller: controller,
        home: const HomeScreen(initialTab: 0),
        before: () => tester.pump(const Duration(seconds: 9)),
      );
    });

    testWidgets('5 all conversations, none yet · $mode', (tester) async {
      final controller = await _connected(repository: _Finder());
      await _shoot(
        tester,
        '5-all-conversations-empty',
        light: light,
        controller: controller,
        home: GlobalSessionsScreen(controller: controller),
      );
    });

    testWidgets('6 all conversations, no match · $mode', (tester) async {
      final controller = await _connected(repository: _Finder());
      await _shoot(
        tester,
        '6-all-conversations-no-match',
        light: light,
        controller: controller,
        home: GlobalSessionsScreen(controller: controller),
        before: () async {
          await tester.enterText(find.byType(TextField).first, 'invoice');
          await tester.pump(const Duration(seconds: 1));
          FocusManager.instance.primaryFocus?.unfocus();
        },
      );
    });

    testWidgets('7 all conversations, could not load · $mode', (tester) async {
      final controller = await _connected(
        repository: _Finder(
          error: ApiException(
            'Cannot reach http://192.168.1.20:4096: Connection refused',
          ),
        ),
      );
      await _shoot(
        tester,
        '7-all-conversations-error',
        light: light,
        controller: controller,
        home: GlobalSessionsScreen(controller: controller),
      );
    });

    testWidgets('8 files, empty folder · $mode', (tester) async {
      final controller = await _connected();
      await _shoot(
        tester,
        '8-files-empty',
        light: light,
        controller: controller,
        home: _page('Files', FilesScreen(controller: controller)),
      );
    });

    testWidgets('9 terminal, none yet · $mode', (tester) async {
      final controller = await _connected();
      await _shoot(
        tester,
        '9-terminal-none',
        light: light,
        controller: controller,
        home: _page('Terminal', TerminalScreen(controller: controller)),
      );
    });

    testWidgets('10 this phone terminal, not set up · $mode', (tester) async {
      final controller = await _connected();
      final backend = FakeLocalTerminalBackend();
      final sessions = LocalTerminalSessions(backend: backend);
      addTearDown(() async {
        sessions.dispose();
        await backend.close();
      });
      await _shoot(
        tester,
        '10-local-terminal-not-set-up',
        light: light,
        controller: controller,
        wrap: (app) => ProviderScope(
          overrides: [
            builtinLinuxProvider.overrideWithValue(_Linux(installed: false)),
            localTerminalProvider.overrideWithValue(sessions),
          ],
          child: app,
        ),
        home: const Scaffold(body: SafeArea(child: LocalTerminalView())),
      );
    });

    testWidgets('11 this phone terminal, shell ended · $mode', (tester) async {
      final controller = await _connected();
      final backend = FakeLocalTerminalBackend();
      final sessions = LocalTerminalSessions(backend: backend);
      addTearDown(() async {
        sessions.dispose();
        await backend.close();
      });
      await _shoot(
        tester,
        '11-local-terminal-ended',
        light: light,
        controller: controller,
        wrap: (app) => ProviderScope(
          overrides: [
            builtinLinuxProvider.overrideWithValue(_Linux(installed: true)),
            localTerminalProvider.overrideWithValue(sessions),
          ],
          child: app,
        ),
        home: const Scaffold(body: SafeArea(child: LocalTerminalView())),
        before: () async {
          backend.output(1, 'root@localhost:~# exit\r\n');
          backend.exit(1, 0);
        },
      );
    });

    testWidgets('12 chat, empty conversation · $mode', (tester) async {
      final api = _ChatApi()
        ..busy = {}
        ..messagesHandler = (_) async => [];
      final controller = await _connected(api: api);
      await _shoot(
        tester,
        '12-chat-empty',
        light: light,
        controller: controller,
        home: const ChatScreen(sessionID: darkModeSessionID),
      );
    });

    testWidgets('13 chat, could not load · $mode', (tester) async {
      final api = _ChatApi()
        ..busy = {}
        ..messagesHandler = (_) async => throw ApiException(
          'Cannot reach http://192.168.1.20:4096: Connection refused',
        );
      final controller = await _connected(api: api);
      await _shoot(
        tester,
        '13-chat-load-error',
        light: light,
        controller: controller,
        home: const ChatScreen(sessionID: checkoutSessionID),
      );
    });

    testWidgets('14 chat, a reply being written · $mode', (tester) async {
      final api = _ChatApi()..messagesHandler = (_) async => sampleTranscript();
      final controller = await _connected(api: api);
      await _shoot(
        tester,
        '14-chat-working',
        light: light,
        controller: controller,
        home: const ChatScreen(sessionID: checkoutSessionID),
      );
    });
  }
}
