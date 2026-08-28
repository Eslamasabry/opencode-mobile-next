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
import 'package:opencode_mobile/ui/screens/session_destination_sheet.dart';
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
  Future<List<PermissionRequest>> pendingPermissionsV2() async => [];

  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => [];

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

class _DestinationReleaseRepository extends _ReleaseRepository {
  @override
  Future<List<WorkspaceProject>> listProjects() async => const [
    WorkspaceProject(
      id: 'project-1',
      name: 'Acme',
      directory: '/work/acme',
      worktrees: ['/work/acme-copy'],
      updatedAt: 1,
    ),
  ];

  @override
  Future<List<ProjectDirectoryInfo>> listProjectDirectories(
    String projectID,
  ) async => const [
    ProjectDirectoryInfo(directory: '/work/acme'),
    ProjectDirectoryInfo(directory: '/work/acme-copy'),
  ];

  @override
  Future<VersionControlHealth> loadVersionControlHealth() async =>
      const VersionControlHealth(changes: []);

  @override
  Future<List<ConsoleOrganization>> listConsoleOrganizations() async => const [
    ConsoleOrganization(
      accountID: 'account-1',
      accountEmail: 'dev@example.com',
      accountUrl: 'https://console.example.com',
      orgID: 'org-1',
      orgName: 'Acme engineering',
      active: true,
    ),
  ];
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
    expect(
      validateServerProfileUrl('https://server.example:4096/api'),
      contains('Remove the path'),
    );
    expect(validateServerProfileUrl('https://server.example:4096/'), isNull);
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

  test('Android launcher supports adaptive, round, and themed icons', () {
    final manifest = File(
      'android/app/src/main/AndroidManifest.xml',
    ).readAsStringSync();
    final adaptive = File(
      'android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',
    ).readAsStringSync();
    final themed = File(
      'android/app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml',
    ).readAsStringSync();

    expect(manifest, contains('android:roundIcon="@mipmap/ic_launcher"'));
    expect(adaptive, contains('<adaptive-icon'));
    expect(adaptive, contains('@drawable/ic_launcher_foreground'));
    expect(themed, contains('@drawable/ic_launcher_monochrome'));
  });

  test('Android background coding alerts are private and actionable', () {
    final activity = File(
      'android/app/src/main/kotlin/ai/opencode/opencode_mobile/MainActivity.kt',
    ).readAsStringSync();
    final service = File(
      'android/app/src/main/kotlin/ai/opencode/opencode_mobile/'
      'BackgroundConnectionService.kt',
    ).readAsStringSync();

    expect(activity, contains('"showCodingAlert"'));
    expect(activity, contains('"dismissCodingAlert"'));
    expect(service, contains('NotificationManager.IMPORTANCE_HIGH'));
    expect(service, contains('NotificationManager.IMPORTANCE_DEFAULT'));
    expect(service, contains('setVisibility(Notification.VISIBILITY_PRIVATE)'));
    expect(
      service,
      contains('lockscreenVisibility = Notification.VISIBILITY_PRIVATE'),
    );
    expect(service, contains('OpenCode needs permission'));
    expect(service, contains('OpenCode needs your input'));
    expect(service, contains('OpenCode finished'));
    expect(service, contains('OpenCode session needs attention'));
    expect(service, isNot(contains('sessionTitle')));
    expect(service, isNot(contains('errorMessage')));
  });

  test('Shorebird updates have one app-controlled owner', () {
    final shorebird = File('shorebird.yaml').readAsStringSync();
    final main = File('lib/main.dart').readAsStringSync();

    expect(
      shorebird,
      contains(RegExp(r'^auto_update: false$', multiLine: true)),
    );
    expect(main, contains('ShorebirdUpdateNotice('));
    expect(main, contains('ShorebirdAppUpdateService()'));
  });

  test('privacy policy is bundled and names sensitive product surfaces', () {
    final pubspec = File('pubspec.yaml').readAsStringSync();
    final policy = File('PRIVACY.md').readAsStringSync();

    expect(pubspec, contains('    - PRIVACY.md'));
    expect(policy, contains('## Microphone and local voice input'));
    expect(policy, contains('## Files, terminal access, and Termux'));
    expect(policy, contains('## Background mode and updates'));
    expect(policy, contains('AI provider'));
    expect(policy, contains('Shorebird'));
  });

  test('Android release builds require the production signing lineage', () {
    final appGradle = File('android/app/build.gradle.kts').readAsStringSync();
    final gradleProperties = File(
      'android/gradle.properties',
    ).readAsStringSync();
    final settings = File('android/settings.gradle.kts').readAsStringSync();
    final wrapper = File(
      'android/gradle/wrapper/gradle-wrapper.properties',
    ).readAsStringSync();
    final example = File('android/key.properties.example').readAsStringSync();

    expect(appGradle, contains('create("release")'));
    expect(
      appGradle,
      contains('signingConfig = signingConfigs.getByName("release")'),
    );
    expect(
      appGradle,
      isNot(contains('signingConfig = signingConfigs.getByName("debug")')),
    );
    expect(settings, contains('version "9.3.2"'));
    expect(
      settings,
      contains(
        'id("org.jetbrains.kotlin.android") version "2.4.0" apply false',
      ),
    );
    expect(appGradle, isNot(contains('id("kotlin-android")')));
    expect(appGradle, contains('compilerOptions'));
    expect(gradleProperties, contains('android.builtInKotlin=true'));
    expect(gradleProperties, contains('android.newDsl=false'));
    expect(wrapper, contains('gradle-9.5.0-all.zip'));
    expect(example, contains('storeFile=/absolute/path/'));
    expect(example, contains('keyAlias=upload'));
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
    await tester.tap(find.byType(PopupMenuButton<String>).last);
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
    final send = find.byTooltip('Send');
    expect(send, findsOneWidget);
    expect(find.byTooltip('Attach file'), findsOneWidget);
    expect(find.byKey(const Key('voice-input-button')), findsOneWidget);
    expect(find.byKey(const Key('chat-composer-surface')), findsOneWidget);
    expect(find.byKey(const Key('command-launcher-button')), findsOneWidget);
    expect(find.byKey(const Key('chat-workbench')), findsNothing);
    final sendButton = find.byKey(const Key('chat-send-button'));
    expect(tester.widget<IconButton>(sendButton).onPressed, isNull);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      '/mod',
    );
    await tester.pump();
    expect(find.byKey(const Key('inline-command-suggestions')), findsNothing);
    expect(tester.widget<IconButton>(sendButton).onPressed, isNotNull);
    expect(tester.takeException(), isNull);
  });

  testWidgets('compact composer shows one slash result when height allows', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(640, 520));
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
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      '/mod',
    );
    await tester.pump();

    expect(find.byKey(const Key('inline-command-suggestions')), findsOneWidget);
    expect(find.byKey(const Key('inline-command-models')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('full-screen prompt editor fits a 320dp phone at 2x text', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: _scaledApp(const ChatScreen(sessionID: 's1'), bottomInset: 180),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('prompt-editor-button')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('prompt-editor-screen')), findsOneWidget);
    expect(find.byKey(const Key('prompt-editor-field')), findsOneWidget);
    expect(find.byKey(const Key('prompt-editor-attach')), findsOneWidget);
    final done = find.byKey(const Key('prompt-editor-done'));
    expect(done, findsOneWidget);
    expect(tester.getSize(done).height, greaterThanOrEqualTo(48));
    expect(tester.takeException(), isNull);
  });

  testWidgets('session destination controls fit a 320dp phone at 2x text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller(
      repository: _DestinationReleaseRepository(),
    );
    controller
      ..directory = '/work/acme'
      ..sessionsById['session-1'] = Session(
        id: 'session-1',
        projectID: 'project-1',
        directory: '/work/acme',
      );
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _scaledApp(
        Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  FilledButton(
                    key: const Key('open-session-destinations'),
                    onPressed: () => showSessionDestinationSheet(
                      context,
                      controller: controller,
                      sessionID: 'session-1',
                      mode: SessionDestinationMode.move,
                    ),
                    child: const Text('Move'),
                  ),
                  FilledButton(
                    key: const Key('open-console-organizations'),
                    onPressed: () => showConsoleOrganizationSheet(
                      context,
                      controller: controller,
                    ),
                    child: const Text('Org'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const Key('open-session-destinations')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('move-session-sheet')), findsOneWidget);
    expect(find.text('Move session'), findsOneWidget);
    expect(
      find.byKey(const Key('move-destination-/work/acme-copy')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
    expect(
      tester.getSize(find.byTooltip('Close')).height,
      greaterThanOrEqualTo(48),
    );

    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('open-console-organizations')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('console-organization-sheet')), findsOneWidget);
    expect(find.text('Acme engineering'), findsOneWidget);
    expect(tester.takeException(), isNull);
    expect(
      tester.getSize(find.byTooltip('Close')).height,
      greaterThanOrEqualTo(48),
    );
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
                input: const {'command': 'flutter test'},
              ),
            ),
          ),
        ),
      ),
    );
    final target = find.byType(InkWell).first;
    expect(tester.getSize(target).height, greaterThanOrEqualTo(48));
    expect(find.bySemanticsLabel(RegExp('Shell, running')), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    await tester.tap(target);
    await tester.pump();
    expect(find.text(r'$ flutter test'), findsOneWidget);
    expect(find.text('INPUT'), findsNothing);
    semantics.dispose();
  });

  testWidgets('bundled privacy policy and open source notices render in app', (
    tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: AboutScreen()));
    await tester.pumpAndSettle();
    expect(find.text('About and open source notices'), findsOneWidget);
    expect(find.text('Privacy Policy'), findsOneWidget);
    expect(find.text('Where your data goes'), findsOneWidget);
    await tester.tap(find.text('Open source'));
    await tester.pumpAndSettle();
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
