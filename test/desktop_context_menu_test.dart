import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/gestures.dart' show kSecondaryMouseButton;
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/desktop/context_menu.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:opencode_mobile/ui/screens/workspace_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// A widget test that runs with the platform reported as Linux desktop. The
/// override must be cleared inside the body: flutter_test asserts no
/// foundation debug variable outlives the test, so tearDown runs too late.
void desktopTest(
  String description,
  Future<void> Function(WidgetTester tester) body,
) {
  testWidgets(description, (tester) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.linux;
    try {
      await body(tester);
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });
}

/// Captures clipboard writes: the platform channel has no handler in a
/// widget test, so Clipboard.getData would never complete.
List<String> _captureClipboard() {
  final written = <String>[];
  TestDefaultBinaryMessengerBinding
      .instance
      .defaultBinaryMessenger
      .setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') {
          written.add((call.arguments as Map)['text'] as String);
        }
        return null;
      });
  addTearDown(
    () => TestDefaultBinaryMessengerBinding
        .instance
        .defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, null),
  );
  return written;
}

/// Right-clicks the centre of [target].
Future<void> _rightClick(WidgetTester tester, Finder target) async {
  await tester.tapAt(tester.getCenter(target), buttons: kSecondaryMouseButton);
  await tester.pumpAndSettle();
}

class _MenuApi extends OpenCodeApi {
  _MenuApi({this.sessionList = const [], this.messageList = const []})
    : super(baseUrl: 'http://localhost');

  final List<Session> sessionList;
  final List<MessageWithParts> messageList;

  @override
  Future<List<Session>> sessions() async => sessionList;

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async => messageList;

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [
    FileNode(name: 'main.dart', path: 'main.dart', isDir: false),
  ];
}

class _MenuRepository implements ProductRepository {
  _MenuRepository(this.projects);

  final List<WorkspaceProject> projects;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => projects;

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => [];

  @override
  Future<List<TerminalProcess>> listTerminals() async => [];

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller(_MenuApi api) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..api = api
    ..status = StreamStatus.connected;
}

MessageWithParts _userMessage(String id, String text) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: 'user',
    time: MsgTime(created: 1, completed: 2),
  ),
  parts: [Part(id: '$id-p', type: 'text', text: text)],
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('the region itself', () {
    desktopTest('secondary tap opens the menu and runs the chosen action', (
      tester,
    ) async {
      var ran = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Center(
              child: ContextMenuRegion(
                actions: () => [
                  ContextMenuAction(
                    menuKey: const ValueKey('probe-run'),
                    label: 'Run it',
                    icon: Icons.play_arrow_rounded,
                    onSelected: () => ran++,
                  ),
                ],
                child: const SizedBox(
                  key: ValueKey('menu-target'),
                  width: 200,
                  height: 60,
                  child: ColoredBox(color: Color(0xFF202020)),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await _rightClick(tester, find.byKey(const ValueKey('menu-target')));
      expect(find.text('Run it'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('probe-run')));
      await tester.pumpAndSettle();
      expect(ran, 1);
    });

    testWidgets('android ignores a secondary tap', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Center(
              child: ContextMenuRegion(
                actions: () => [
                  ContextMenuAction(
                    label: 'Run it',
                    icon: Icons.play_arrow_rounded,
                    onSelected: () => fail('no menu on Android'),
                  ),
                ],
                child: const SizedBox(
                  key: ValueKey('menu-target'),
                  width: 200,
                  height: 60,
                  child: ColoredBox(color: Color(0xFF202020)),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await _rightClick(tester, find.byKey(const ValueKey('menu-target')));
      expect(find.text('Run it'), findsNothing);
    });
  });

  desktopTest('a transcript message offers copy and fork', (tester) async {
    final clipboard = _captureClipboard();
    final api = _MenuApi(messageList: [_userMessage('m1', 'hello desktop')]);
    final connection = await _controller(api);
    addTearDown(connection.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(connection)],
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
      ),
    );
    await tester.pumpAndSettle();

    await _rightClick(tester, find.text('hello desktop'));
    expect(
      find.byKey(const ValueKey('message-menu-copy')),
      findsOneWidget,
      reason: 'the right-click menu mirrors the long-press sheet',
    );
    expect(find.byKey(const ValueKey('message-menu-fork')), findsOneWidget);
    // Delete rides the same capability gate as the sheet's delete row.
    expect(
      find.byKey(const ValueKey('message-menu-delete')),
      connection.capabilities.messageDelete ? findsOneWidget : findsNothing,
    );

    await tester.tap(find.byKey(const ValueKey('message-menu-copy')));
    await tester.pumpAndSettle();
    expect(clipboard, ['hello desktop']);
  });

  desktopTest('a file row offers open and copy path', (tester) async {
    final clipboard = _captureClipboard();
    final connection = await _controller(_MenuApi());
    addTearDown(connection.dispose);

    await tester.pumpWidget(
      MaterialApp(home: Scaffold(body: FilesScreen(controller: connection))),
    );
    await tester.pumpAndSettle();

    await _rightClick(
      tester,
      find.byKey(const ValueKey('project-file-main.dart')),
    );
    expect(find.byKey(const ValueKey('file-menu-open')), findsOneWidget);
    expect(find.byKey(const ValueKey('file-menu-copy-path')), findsOneWidget);
    // No chat handoff was supplied, so neither add-to-prompt entry appears.
    expect(find.byKey(const ValueKey('file-menu-attach')), findsNothing);
    expect(find.byKey(const ValueKey('file-menu-reference')), findsNothing);

    await tester.tap(find.byKey(const ValueKey('file-menu-copy-path')));
    await tester.pumpAndSettle();
    expect(clipboard, ['main.dart']);
  });

  desktopTest('a session row offers the same actions as its overflow menu', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(800, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final api = _MenuApi(
      sessionList: [
        Session(id: 's1', title: 'Ship it', time: SessionTime(created: 1)),
      ],
    );
    final connection = await _controller(api);
    connection.repository = _MenuRepository([
      WorkspaceProject(
        id: 'p1',
        name: 'p1',
        directory: '/tmp/p1',
        worktrees: const [],
        updatedAt: 1,
      ),
    ]);
    addTearDown(connection.dispose);
    await connection.refreshSessions();

    await tester.pumpWidget(
      MaterialApp(home: Scaffold(body: WorkspaceScreen(controller: connection))),
    );
    await tester.pumpAndSettle();

    await _rightClick(tester, find.text('Ship it'));
    expect(find.byKey(const ValueKey('session-menu-open')), findsOneWidget);
    expect(find.byKey(const ValueKey('session-menu-rename')), findsOneWidget);
    expect(find.byKey(const ValueKey('session-menu-delete')), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('session-menu-rename')));
    await tester.pumpAndSettle();
    expect(find.text('Rename session'), findsOneWidget);
  });
}
