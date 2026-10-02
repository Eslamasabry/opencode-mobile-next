// The empty, quiet and failure moments draw one drawing per kind of state
// (design standard §10; docs/design/motion-and-illustration-2026-09-25.md,
// slice C): the right drawing for each state, nothing looping on a resting
// screen, the chat's working mark only while a reply is written, and the
// finished frame at once under reduced motion.
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
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/scenes/states_scenes.dart';
import 'package:opencode_mobile/ui/screens/activity_screen.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:opencode_mobile/ui/screens/global_sessions_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/local_terminal_screen.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart';
import 'support/fake_local_terminal.dart';
import 'support/work_tab_fixture.dart';

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

/// The scenes drawn on screen now.
List<KitScene> _drawn(WidgetTester tester) => [
  for (final widget in tester.widgetList<KitIllustration>(
    find.byType(KitIllustration),
  ))
    widget.scene,
];

Future<void> _frames(WidgetTester tester, [int count = 10]) async {
  for (var i = 0; i < count; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<void> _mount(
  WidgetTester tester,
  ConnectionController controller,
  Widget home, {
  bool reduce = false,
  Widget Function(Widget app)? wrap,
}) async {
  _mockSecureStorage(tester);
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  addTearDown(controller.dispose);
  Widget app = captureApp(
    home: reduce
        ? Builder(
            builder: (context) => MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: true),
              child: home,
            ),
          )
        : home,
    boundaryKey: GlobalKey(),
    controller: controller,
  );
  if (wrap != null) app = wrap(app);
  await tester.pumpWidget(app);
  await _frames(tester);
}

Future<void> _unmount(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pump(const Duration(seconds: 1));
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

Scaffold _page(Widget body) => Scaffold(body: SafeArea(child: body));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  tearDown(() => KitMotion.loops = false);

  group('Work', () {
    testWidgets('no conversations yet is the fresh sheet, and it rests', (
      tester,
    ) async {
      // Loops allowed, as in the app: a resting state still does not move.
      KitMotion.loops = true;
      await _mount(
        tester,
        await workController(),
        const HomeScreen(initialTab: 0),
      );
      expect(_drawn(tester), [isA<StatesSheetScene>()]);
      expect(tester.hasRunningAnimations, isFalse);
      await _unmount(tester);
    });

    testWidgets('no project yet is the open folder', (tester) async {
      await _mount(
        tester,
        await workController(
          directory: null,
          savedLocation: false,
          repository: WorkRepository()..projects = const [],
        ),
        const HomeScreen(initialTab: 0),
      );
      expect(
        find.byKey(const ValueKey('workspace-folder-chooser')),
        findsOneWidget,
      );
      expect(_drawn(tester), [isA<StatesFolderScene>()]);
      await _unmount(tester);
    });

    testWidgets('not answering with nothing listed: placeholder rows for 8 s, '
        'then the unplugged cable', (tester) async {
      await _mount(
        tester,
        await workController(status: StreamStatus.reconnecting),
        const HomeScreen(initialTab: 0),
      );
      expect(find.byType(KitSkeletonRows), findsOneWidget);
      expect(_drawn(tester).whereType<StatesUnpluggedScene>(), isEmpty);

      await tester.pump(const Duration(seconds: 9));
      await _frames(tester);
      expect(find.byType(KitSkeletonRows), findsNothing);
      expect(
        find.byKey(const ValueKey('work-not-answering-list')),
        findsOneWidget,
      );
      expect(find.text('Your conversations will be back'), findsOneWidget);
      expect(_drawn(tester), [isA<StatesUnpluggedScene>()]);
      await _unmount(tester);
    });
  });

  testWidgets('Inbox: all caught up is the tray', (tester) async {
    final controller = await _connected();
    controller
      ..busySessions = {}
      ..permissions = {}
      ..questions = {};
    await _mount(
      tester,
      controller,
      _page(ActivityScreen(controller: controller, embedded: true)),
    );
    expect(find.byKey(const ValueKey('activity-all-clear')), findsOneWidget);
    expect(_drawn(tester), [isA<StatesTrayScene>()]);
    await _unmount(tester);
  });

  group('All conversations', () {
    testWidgets('none yet is the fresh sheet', (tester) async {
      final controller = await _connected(repository: _Finder());
      await _mount(
        tester,
        controller,
        GlobalSessionsScreen(controller: controller),
      );
      expect(_drawn(tester), [isA<StatesSheetScene>()]);
      await _unmount(tester);
    });

    testWidgets('a search with no match is the magnifier', (tester) async {
      final controller = await _connected(repository: _Finder());
      await _mount(
        tester,
        controller,
        GlobalSessionsScreen(controller: controller),
      );
      await tester.enterText(find.byType(TextField).first, 'invoice');
      await tester.pump(const Duration(seconds: 1));
      await _frames(tester);
      expect(_drawn(tester), [isA<StatesSearchScene>()]);
      expect(find.text('Clear search'), findsOneWidget);
      await _unmount(tester);
    });

    testWidgets('could not load is the unplugged cable, with Try again and '
        'Report a problem', (tester) async {
      final controller = await _connected(
        repository: _Finder(error: ApiException('Connection refused')),
      );
      await _mount(
        tester,
        controller,
        GlobalSessionsScreen(controller: controller),
      );
      expect(_drawn(tester), [isA<StatesUnpluggedScene>()]);
      expect(find.text("Couldn't load conversations"), findsOneWidget);
      expect(find.text('Try again'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('product-error-report-bug')),
        findsOneWidget,
      );
      await _unmount(tester);
    });
  });

  testWidgets('Files: an empty folder is the open folder', (tester) async {
    final controller = await _connected();
    await _mount(
      tester,
      controller,
      _page(FilesScreen(controller: controller)),
    );
    expect(_drawn(tester), [isA<StatesFolderScene>()]);
    await _unmount(tester);
  });

  group('Terminal', () {
    testWidgets('no terminal on the server is the terminal window', (
      tester,
    ) async {
      final controller = await _connected();
      await _mount(
        tester,
        controller,
        _page(TerminalScreen(controller: controller)),
      );
      expect(_drawn(tester), [
        isA<StatesTerminalScene>().having((s) => s.ended, 'ended', isFalse),
      ]);
      await _unmount(tester);
    });

    Widget Function(Widget) phone(
      LocalTerminalSessions sessions, {
      required bool installed,
    }) =>
        (app) => ProviderScope(
          overrides: [
            builtinLinuxProvider.overrideWithValue(
              _Linux(installed: installed),
            ),
            localTerminalProvider.overrideWithValue(sessions),
          ],
          child: app,
        );

    testWidgets('this phone not set up is the terminal window', (tester) async {
      final backend = FakeLocalTerminalBackend();
      final sessions = LocalTerminalSessions(backend: backend);
      addTearDown(() async {
        sessions.dispose();
        await backend.close();
      });
      await _mount(
        tester,
        await _connected(),
        _page(const LocalTerminalView()),
        wrap: phone(sessions, installed: false),
      );
      expect(_drawn(tester), [
        isA<StatesTerminalScene>().having((s) => s.ended, 'ended', isFalse),
      ]);
      await _unmount(tester);
    });

    testWidgets('a shell that ended is the dimmed terminal window', (
      tester,
    ) async {
      final backend = FakeLocalTerminalBackend();
      final sessions = LocalTerminalSessions(backend: backend);
      addTearDown(() async {
        sessions.dispose();
        await backend.close();
      });
      await _mount(
        tester,
        await _connected(),
        _page(const LocalTerminalView()),
        wrap: phone(sessions, installed: true),
      );
      expect(_drawn(tester), isEmpty);
      backend.exit(1, 0);
      await _frames(tester);
      expect(_drawn(tester), [
        isA<StatesTerminalScene>().having((s) => s.ended, 'ended', isTrue),
      ]);
      await _unmount(tester);
    });
  });

  group('Chat', () {
    testWidgets('an empty conversation starts on the fresh sheet', (
      tester,
    ) async {
      final api = _ChatApi()
        ..busy = {}
        ..messagesHandler = (_) async => [];
      await _mount(
        tester,
        await _connected(api: api),
        const ChatScreen(sessionID: darkModeSessionID),
      );
      // The drawing carries the caret; the screen has no second blinking
      // one (chat-5), so the fresh sheet is the only scene drawn.
      expect(find.byKey(const ValueKey('chat-start-caret')), findsNothing);
      expect(_drawn(tester), [isA<StatesSheetScene>()]);
      await _unmount(tester);
    });

    testWidgets('could not load is the unplugged cable', (tester) async {
      final api = _ChatApi()
        ..busy = {}
        ..messagesHandler = (_) async =>
            throw ApiException('Connection refused');
      await _mount(
        tester,
        await _connected(api: api),
        const ChatScreen(sessionID: checkoutSessionID),
      );
      expect(find.byKey(const ValueKey('chat-load-error')), findsOneWidget);
      expect(_drawn(tester), [isA<StatesUnpluggedScene>()]);
      await _unmount(tester);
    });

    // chat-3 (e28442b0, LOOK-20) removed the composer's animated working
    // mark; the composer edge's status, with its Stop, is the
    // working signal (still words, no loop). It shows while a reply is
    // written and goes when the run ends, and nothing is left moving.
    testWidgets('while a reply is written the edge status with Stop is the '
        'working signal; it goes when the run ends', (tester) async {
      KitMotion.loops = true;
      final api = _ChatApi()..messagesHandler = (_) async => sampleTranscript();
      final controller = await _connected(api: api);
      await _mount(
        tester,
        controller,
        const ChatScreen(sessionID: checkoutSessionID),
      );
      final stop = find.byKey(const Key('chat-stop-button'));
      expect(stop, findsOneWidget);
      expect(find.byKey(const ValueKey('chat-working-mark')), findsNothing);

      controller.busySessions.remove(checkoutSessionID);
      controller.notifyListeners();
      await _frames(tester, 4);
      // The live dot and Stop's ring leave with the run, then nothing is
      // left moving.
      await tester.pumpAndSettle();
      expect(stop, findsNothing);
      expect(tester.hasRunningAnimations, isFalse);
      await _unmount(tester);
    });

    testWidgets('under reduced motion the working signal is still', (
      tester,
    ) async {
      KitMotion.loops = true;
      final api = _ChatApi()..messagesHandler = (_) async => sampleTranscript();
      await _mount(
        tester,
        await _connected(api: api),
        const ChatScreen(sessionID: checkoutSessionID),
        reduce: true,
      );
      expect(find.byKey(const Key('chat-stop-button')), findsOneWidget);
      expect(tester.hasRunningAnimations, isFalse);
      await _unmount(tester);
    });
  });
}
