import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/about_screen.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/requests_screen.dart';
import 'package:opencode_mobile/ui/widgets/markdown.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _ReleaseApi extends OpenCodeApi {
  _ReleaseApi() : super(baseUrl: 'http://localhost');

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => [];

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<Map<String, String>> sessionStatuses() async => {};
}

class _ReleaseRepository implements ProductRepository {
  _ReleaseRepository({this.questions = const []});

  final List<PendingQuestion> questions;
  bool shared = false;
  bool unshared = false;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<PendingQuestion>> listQuestions() async => questions;

  @override
  Future<String?> shareSession(String id) async {
    shared = true;
    return 'https://share.example/session/$id';
  }

  @override
  Future<void> unshareSession(String id) async {
    unshared = true;
  }

  @override
  Future<List<WorkspaceProject>> listProjects() async => [];

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

Future<ConnectionController> _controller({
  _ReleaseApi? api,
  ProductRepository? repository,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..api = api ?? _ReleaseApi()
    ..repository = repository ?? _ReleaseRepository()
    ..status = StreamStatus.connected;
}

Widget _scaledApp(Widget home, {double bottomInset = 0}) {
  return MaterialApp(
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(
        textScaler: const TextScaler.linear(2),
        viewInsets: EdgeInsets.only(bottom: bottomInset),
      ),
      child: child!,
    ),
    home: home,
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('release URL policy permits only loopback HTTP', () {
    expect(validateServerProfileUrl('http://localhost:4096'), isNull);
    expect(validateServerProfileUrl('http://127.0.0.1:4096'), isNull);
    expect(validateServerProfileUrl('https://192.168.1.4:4096'), isNull);
    expect(
      validateServerProfileUrl('devbox.local:4096'),
      contains('Include https://'),
    );
    expect(
      validateServerProfileUrl(
        'http://192.168.1.4:4096',
        username: 'opencode',
        password: 'secret',
      ),
      contains('Basic credentials'),
    );
    expect(
      validateServerProfileUrl('http://192.168.1.4:4096'),
      contains('HTTP is allowed only'),
    );
  });

  test('Android cleartext and launch resources are release-safe', () {
    final manifest = File(
      'android/app/src/main/AndroidManifest.xml',
    ).readAsStringSync();
    final network = File(
      'android/app/src/main/res/xml/network_security_config.xml',
    ).readAsStringSync();
    final launch = File(
      'android/app/src/main/res/drawable/launch_background.xml',
    ).readAsStringSync();
    expect(manifest, contains('android:usesCleartextTraffic="false"'));
    expect(manifest, contains('@xml/network_security_config'));
    expect(network, contains('<base-config cleartextTrafficPermitted="false"'));
    expect(network, contains('>localhost</domain>'));
    expect(network, contains('>127.0.0.1</domain>'));
    expect(network, isNot(contains('192.168.')));
    expect(launch, contains('@color/launch_background'));
    expect(launch, isNot(contains('@android:color/white')));
  });

  testWidgets('markdown blocks custom schemes and confirms HTTP with host', (
    tester,
  ) async {
    Uri? launched;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) => Column(
              children: [
                TextButton(
                  onPressed: () =>
                      openMarkdownExternalLink(context, 'intent://steal'),
                  child: const Text('Blocked'),
                ),
                TextButton(
                  onPressed: () => openMarkdownExternalLink(
                    context,
                    'http://docs.example/path',
                    launcher: (uri) async {
                      launched = uri;
                      return true;
                    },
                  ),
                  child: const Text('HTTP'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Blocked'));
    await tester.pump();
    expect(find.textContaining('Link blocked'), findsOneWidget);

    await tester.tap(find.text('HTTP'));
    await tester.pumpAndSettle();
    expect(find.text('docs.example'), findsOneWidget);
    expect(find.text('Open insecure HTTP link?'), findsOneWidget);
    expect(launched, isNull);
    await tester.tap(find.text('Open HTTP link'));
    await tester.pumpAndSettle();
    expect(launched, Uri.parse('http://docs.example/path'));
  });

  testWidgets('sharing requires privacy consent and exposes stop sharing', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final repository = _ReleaseRepository();
    final controller = await _controller(repository: repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Share session'));
    await tester.pumpAndSettle();
    expect(find.text('Share this session?'), findsOneWidget);
    expect(find.textContaining('Anyone with the link'), findsOneWidget);
    expect(repository.shared, isFalse);
    await tester.tap(find.widgetWithText(FilledButton, 'Share session'));
    await tester.pumpAndSettle();
    expect(repository.shared, isTrue);
    expect(
      find.bySemanticsLabel(
        RegExp('Shared session link https://share.example/session/session-1'),
      ),
      findsOneWidget,
    );
    expect(find.text('Stop sharing'), findsOneWidget);
    await tester.tap(find.text('Stop sharing'));
    await tester.pumpAndSettle();
    expect(repository.unshared, isTrue);
    semantics.dispose();
  });

  testWidgets(
    'question sheet keeps actions reachable with keyboard and 2x text',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(640, 320));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final question = PendingQuestion(
        id: 'q1',
        sessionID: 's1',
        prompts: const [
          QuestionPrompt(
            title: 'Deployment',
            question: 'Which target should be used?',
            multiple: false,
            custom: true,
            choices: [
              QuestionChoice(label: 'Staging', description: 'Test environment'),
            ],
          ),
        ],
      );
      final controller = await _controller(
        repository: _ReleaseRepository(questions: [question]),
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _scaledApp(RequestsScreen(controller: controller), bottomInset: 96),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Deployment'));
      await tester.pumpAndSettle();
      expect(find.text('Dismiss'), findsOneWidget);
      expect(find.text('Send answers'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('chat composer remains reachable at 640x320 with 2x text', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(640, 320));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: _scaledApp(const ChatScreen(sessionID: 's1'), bottomInset: 96),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byTooltip('Send'), findsOneWidget);
    expect(find.byTooltip('Attach file'), findsOneWidget);
    expect(find.byKey(const Key('voice-input-button')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('tool expansion has 48dp target and reduced-motion semantics', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      MaterialApp(
        home: MediaQuery(
          data: const MediaQueryData(disableAnimations: true),
          child: Scaffold(
            body: ToolCard(
              toolName: 'bash',
              state: ToolState(
                status: 'running',
                title: 'Run tests',
                inputJson: '{"command":"flutter test"}',
              ),
            ),
          ),
        ),
      ),
    );
    final target = find.byType(InkWell).first;
    expect(tester.getSize(target).height, greaterThanOrEqualTo(48));
    expect(find.bySemanticsLabel(RegExp('Run tests, running')), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    await tester.tap(target);
    await tester.pump();
    expect(find.text('INPUT'), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('bundled open source notices render in app', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: AboutScreen()));
    await tester.pumpAndSettle();
    expect(find.text('About and open source notices'), findsOneWidget);
    expect(find.text('Third-Party Notices'), findsOneWidget);
    expect(find.text('sherpa-onnx'), findsWidgets);
  });

  testWidgets('offline banner states that displayed data may be stale', (
    tester,
  ) async {
    final controller = await _controller()
      ..status = StreamStatus.disconnected;
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(home: HomeScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('Displayed data may be stale'), findsOneWidget);
    await tester.pumpWidget(const MaterialApp(home: SizedBox()));
    controller.dispose();
  });
}
