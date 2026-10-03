import 'dart:async';
import 'support/complete_message_history.dart';

import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitButton, KitTappable;
import 'package:opencode_mobile/ui/screens/about_screen.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/session_destination_sheet.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';
import 'package:opencode_mobile/ui/widgets/external_link.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _ReleaseApi extends OpenCodeApi with CompleteMessageHistory {
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
  Future<Session> getSessionDetails(String id) async =>
      Session(id: id, projectID: 'project-1', directory: '/work/acme');

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

/// A chat whose session is already shared, reached the way a person does it:
/// through the consent sheet.
Future<void> _pumpSharedChat(
  WidgetTester tester,
  _ReleaseRepository repository,
) async {
  final controller = await _controller(repository: repository);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await tester.pumpAndSettle();
  await tester.tap(find.byTooltip('Conversation menu'));
  await tester.pumpAndSettle();
  await tester.ensureVisible(find.text('Share conversation'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Share conversation'));
  await tester.pumpAndSettle();
  await tester.tap(find.widgetWithText(KitButton, 'Share conversation'));
  await tester.pumpAndSettle();
  expect(repository.shared, isTrue);
  expect(repository.unshared, isFalse);
}

Future<ConnectionController> _controller({
  _ReleaseApi? api,
  ProductRepository? repository,
  bool savedProfile = false,
}) async {
  SharedPreferences.setMockInitialValues({
    if (savedProfile) ...{
      'oc.profiles': jsonEncode([
        {'id': 'profile', 'name': 'Synthetic', 'baseUrl': 'http://localhost'},
      ]),
      'oc.activeProfile': 'profile',
    },
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  if (savedProfile) {
    // Navigation guards require the saved profile whose location they protect.
    const secure = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(secure, (_) async => null);
    addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
    await store.load();
  }
  return ConnectionController(store)
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
    expect(validateServerProfileUrl('https://192.0.2.4:4096'), isNull);
    expect(
      validateServerProfileUrl('devbox.local:4096'),
      contains('Include https://'),
    );
    expect(
      validateServerProfileUrl(
        'http://192.0.2.4:4096',
        username: 'opencode',
        password: 'secret',
      ),
      contains('Basic credentials'),
    );
    expect(
      validateServerProfileUrl('http://192.0.2.4:4096'),
      contains('HTTP is allowed only'),
    );
    expect(
      validateServerProfileUrl('https://server.example:4096/api'),
      contains('Remove the path'),
    );
    expect(validateServerProfileUrl('https://server.example:4096/'), isNull);
  });

  test('IPv6 loopback is loopback everywhere', () {
    // `[::1]` is this device as much as `127.0.0.1` is, and the three
    // loopback checks used to disagree about it. HTTP to it is allowed...
    expect(validateServerProfileUrl('http://[::1]:4096'), isNull);
    expect(validateServerProfileUrl('http://[::1]'), isNull);
    // ...and every other IPv6 address is still remote.
    expect(
      validateServerProfileUrl('http://[2001:db8::1]:4096'),
      contains('HTTP is allowed only'),
    );
    expect(validateServerProfileUrl('https://[2001:db8::1]:4096'), isNull);
    // The message names the form the user has to type.
    expect(
      validateServerProfileUrl('http://[2001:db8::1]:4096'),
      contains('[::1]'),
    );
    expect(isLoopbackHost('::1'), isTrue);
    expect(isLoopbackHost('::2'), isFalse);
  });

  test('bare IPv6 addresses normalize into usable URLs', () {
    // Splitting on ':' read the host of `[::1]:4096` as '[', so IPv6
    // loopback was silently sent to HTTPS, which the local server does not
    // serve.
    expect(normalizeServerProfileUrl('[::1]:4096'), 'http://[::1]:4096');
    expect(normalizeServerProfileUrl('[::1]'), 'http://[::1]');
    // An unbracketed literal gains brackets: `http://::1` does not parse.
    expect(normalizeServerProfileUrl('::1'), 'http://[::1]');
    expect(Uri.parse(normalizeServerProfileUrl('::1')).host, '::1');
    expect(
      normalizeServerProfileUrl('[2001:db8::1]:4096'),
      'https://[2001:db8::1]:4096',
    );
    expect(normalizeServerProfileUrl('fe80::1'), 'https://[fe80::1]');
    // And the IPv4 and hostname behavior is unchanged.
    expect(
      normalizeServerProfileUrl('127.0.0.1:4096'),
      'http://127.0.0.1:4096',
    );
    expect(normalizeServerProfileUrl(':4096'), ':4096');
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
    // Plain HTTP is gated in Dart (loopback + Tailscale addresses only);
    // Android cannot express an address range, so the platform policy is
    // open and must stay documented as such. 2026-09-11: a closed policy
    // blocked the AI Team front at http://100.x:8373 with no usable error.
    expect(network, contains('<base-config cleartextTrafficPermitted="true"'));
    expect(network, contains('isOrchestrationUrlAllowed'));
    expect(network, isNot(contains('192.168.')));
    expect(launch, contains('@color/launch_background'));
    expect(launch, isNot(contains('@android:color/white')));
  });

  test('secure storage biometric lint exception stays narrow and truthful', () {
    final manifest = File(
      'android/app/src/main/AndroidManifest.xml',
    ).readAsStringSync();
    final profiles = File('lib/state/profiles.dart').readAsStringSync();
    final rootGradle = File('android/build.gradle.kts').readAsStringSync();

    expect(profiles, contains('const FlutterSecureStorage()'));
    expect(profiles, isNot(contains('AndroidOptions.biometric')));
    expect(manifest, isNot(contains('android.permission.USE_BIOMETRIC')));
    expect(rootGradle, contains('project.name == "flutter_secure_storage"'));
    expect(rootGradle, contains('disable += "MissingPermission"'));
    expect(
      'disable += "MissingPermission"'.allMatches(rootGradle),
      hasLength(1),
    );
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
      'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/MainActivity.kt',
    ).readAsStringSync();
    final service = File(
      'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/'
      'BackgroundConnectionService.kt',
    ).readAsStringSync();

    expect(activity, contains('"showCodingAlert"'));
    expect(activity, contains('"dismissCodingAlert"'));
    expect(activity, contains('"consumeCodingAlertOpen"'));
    expect(activity, contains('override fun onNewIntent(intent: Intent)'));
    expect(activity, contains('captureCodingAlertOpen(intent)'));
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
    expect(service, contains('putExtra(EXTRA_CODING_ALERT_KIND, kind)'));
    expect(
      service,
      contains('putExtra(EXTRA_CODING_ALERT_SESSION_ID, sessionID)'),
    );
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
    final workflow = File(
      '.github/workflows/android-release.yml',
    ).readAsStringSync();

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
    expect(workflow, contains('Create draft stable GitHub release'));
    expect(workflow, contains('--draft --prerelease=false'));
    expect(workflow, contains('dist/SHA256SUMS'));
    expect(workflow, contains('Signer SHA-256'));
  });

  test('Android exposes build identity and authenticates shell approval', () {
    final activity = File(
      'android/app/src/main/kotlin/io/github/eslamasabry/'
      'opencode_mobile/MainActivity.kt',
    ).readAsStringSync();
    final background = File(
      'android/app/src/main/kotlin/io/github/eslamasabry/'
      'opencode_mobile/BackgroundConnectionService.kt',
    ).readAsStringSync();
    final about = File('lib/ui/screens/about_screen.dart').readAsStringSync();

    expect(activity, contains('getSigningCertificateSha256'));
    expect(activity, contains('MessageDigest.getInstance("SHA-256")'));
    expect(about, contains('l10n.aboutSigningCertificate'));
    expect(background, contains('setAuthenticationRequired(true)'));
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
                  onPressed: () => openExternalLink(context, 'intent://steal'),
                  child: const Text('Blocked'),
                ),
                TextButton(
                  onPressed: () => openExternalLink(
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
    await tester.pumpAndSettle();
    expect(find.textContaining('Link blocked'), findsOneWidget);
    // The blocked answer is a blocking alert (a snackbar is only for Undo,
    // KIT-34); it closes before anything else is tapped.
    await tester.tap(find.text('Close'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('HTTP'));
    await tester.pumpAndSettle();
    expect(find.text('Opens docs.example outside this app.'), findsOneWidget);
    expect(find.text('Open insecure HTTP link?'), findsOneWidget);
    expect(launched, isNull);
    await tester.tap(find.text('Open HTTP link'));
    await tester.pumpAndSettle();
    expect(launched, Uri.parse('http://docs.example/path'));
  });

  testWidgets('sharing requires privacy consent and exposes stop sharing', (
    tester,
  ) async {
    final copiedLinks = <String>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copiedLinks.add((call.arguments as Map)['text'] as String);
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
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
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    // Sharing sits under Actions in the merged menu; scroll it into view on
    // the short test surface before tapping.
    await tester.ensureVisible(find.text('Share conversation'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Share conversation'));
    await tester.pumpAndSettle();
    expect(find.text('Share this conversation?'), findsOneWidget);
    expect(find.textContaining('Anyone with the link'), findsOneWidget);
    expect(repository.shared, isFalse);
    await tester.tap(find.widgetWithText(KitButton, 'Share conversation'));
    await tester.pumpAndSettle();
    expect(repository.shared, isTrue);
    expect(
      find.bySemanticsLabel(RegExp('Shared: anyone with the link can view')),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const ValueKey('chat-status-copy-share-link')));
    await tester.pumpAndSettle();
    expect(copiedLinks.last, 'https://share.example/session/session-1');
    // Chat-6 exposes the public URL through Copy link; stopping lives under
    // the status line's More menu and still asks before revoking the link.
    await tester.tap(find.byKey(const ValueKey('kit-status-more')));
    await tester.pumpAndSettle();
    expect(find.text('Stop sharing'), findsOneWidget);
    // The banner's Stop sharing asks first; the link is live for other people.
    await tester.tap(find.text('Stop sharing'));
    await tester.pumpAndSettle();
    expect(find.text('Stop sharing this conversation?'), findsOneWidget);
    expect(
      find.textContaining('The link stops working for anyone who has it.'),
      findsOneWidget,
    );
    await tester.tap(find.text('Keep sharing'));
    await tester.pumpAndSettle();
    expect(repository.unshared, isFalse);
    await tester.tap(find.byKey(const ValueKey('kit-status-more')));
    await tester.pumpAndSettle();
    expect(find.text('Stop sharing'), findsOneWidget);

    await tester.tap(find.text('Stop sharing'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('confirm-stop-sharing')));
    await tester.pumpAndSettle();
    expect(repository.unshared, isTrue);
    semantics.dispose();
  });

  testWidgets('session menu Stop sharing waits for the confirm sheet', (
    tester,
  ) async {
    final repository = _ReleaseRepository();
    await _pumpSharedChat(tester, repository);

    Future<void> chooseFromMenu() async {
      await tester.tap(find.byTooltip('Conversation menu'));
      await tester.pumpAndSettle();
      // The banner behind the sheet carries the same label; the sheet row is
      // the later one in the tree.
      await tester.ensureVisible(find.text('Stop sharing').last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Stop sharing').last);
      await tester.pumpAndSettle();
    }

    await chooseFromMenu();
    expect(
      find.byKey(const ValueKey('stop-sharing-confirm-sheet')),
      findsOneWidget,
    );
    // Dismissing by the scrim counts as "no".
    await tester.tapAt(const Offset(10, 10));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('stop-sharing-confirm-sheet')),
      findsNothing,
    );
    expect(repository.unshared, isFalse);

    await chooseFromMenu();
    await tester.tap(find.byKey(const ValueKey('confirm-stop-sharing')));
    await tester.pumpAndSettle();
    expect(repository.unshared, isTrue);
  });

  testWidgets('/unshare waits for the confirm sheet', (tester) async {
    final repository = _ReleaseRepository();
    await _pumpSharedChat(tester, repository);

    Future<void> runSlash() async {
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        '/unshare',
      );
      await tester.pump();
      // Typed: the conversation menu is Stop sharing's listed home, the
      // slash word still runs (slice-P10.2).
      await tester.tap(find.byKey(const Key('chat-send-button')));
      await tester.pumpAndSettle();
    }

    await runSlash();
    expect(
      find.byKey(const ValueKey('stop-sharing-confirm-sheet')),
      findsOneWidget,
    );
    await tester.tap(find.text('Keep sharing'));
    await tester.pumpAndSettle();
    expect(repository.unshared, isFalse);

    await runSlash();
    await tester.tap(find.byKey(const ValueKey('confirm-stop-sharing')));
    await tester.pumpAndSettle();
    expect(repository.unshared, isTrue);
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
      // The sheet as the chat's question card opens it (P4.2a: the
      // notification deep link now lands on that card, not on this sheet).
      await tester.pumpWidget(_scaledApp(const Scaffold(), bottomInset: 96));
      await controller.refreshPendingQuestions();
      unawaited(
        showQuestionSheet(
          tester.element(find.byType(Scaffold).first),
          controller,
          controller.questions['q1']!,
        ),
      );
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
    final microphone = find.byKey(const ValueKey('kit-composer-mic'));
    expect(microphone.hitTestable(), findsOneWidget);
    expect(find.byKey(const Key('chat-composer-surface')), findsOneWidget);
    // UX-P0-03: Commands, Attach, and Voice collapsed into one leading
    // tools button; all three stay reachable from its sheet.
    final tools = find.byKey(const Key('composer-tools-button'));
    expect(tools, findsOneWidget);
    await tester.tap(tools);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('composer-tool-commands')), findsOneWidget);
    expect(find.byKey(const Key('composer-tool-attach')), findsOneWidget);
    expect(find.byKey(const Key('composer-tool-voice')), findsOneWidget);
    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('chat-workbench')), findsNothing);
    final sendButton = find.byKey(const Key('chat-send-button'));
    expect(sendButton, findsNothing);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      '/mod',
    );
    await tester.pump();
    expect(find.byKey(const Key('inline-command-suggestions')), findsNothing);
    await tester.pump(const Duration(milliseconds: 400));
    expect(
      tester.getSemantics(sendButton),
      isSemantics(isEnabled: true, hasTapAction: true),
    );
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
    expect(find.byKey(const Key('prompt-editor-button')), findsNothing);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'A draft to expand and finish',
    );
    await tester.pumpAndSettle();
    final editor = find.byKey(const Key('prompt-editor-button'));
    expect(
      editor.hitTestable(),
      findsOneWidget,
      reason:
          'The prompt editor must be reachable at 320dp with 2x text and the keyboard open. ${_hitTestOwners(tester, editor)}',
    );
    await tester.tap(editor);
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
      savedProfile: true,
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
    expect(find.text('Move conversation'), findsOneWidget);
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
    // KitToolRow: the whole line is one KitTappable (48 dp floor) whose
    // label reads title, command, state in that order.
    final target = find.byType(KitTappable).first;
    expect(tester.getSize(target).height, greaterThanOrEqualTo(48));
    expect(
      find.bySemanticsLabel(RegExp('Shell, flutter test, Running')),
      findsOneWidget,
    );
    expect(find.byType(CircularProgressIndicator), findsNothing);
    await tester.tap(target);
    await tester.pump();
    // KitCodeBlock(command) draws the $ prompt outside the command text.
    final command = find.byKey(const Key('tool-shell-command'));
    expect(command, findsOneWidget);
    expect(
      find.descendant(
        of: command,
        matching: find.textContaining('flutter test', findRichText: true),
      ),
      findsWidgets,
    );
    expect(find.text('INPUT'), findsNothing);
    semantics.dispose();
  });

  testWidgets('bundled privacy policy and open source notices render in app', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: AboutScreen(),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('About'), findsOneWidget);

    // Upstream asks third-party projects that use the OpenCode name to say
    // plainly that they are not the official project. It has to be on the tab
    // the reader lands on, not only behind a tap.
    expect(find.byKey(const Key('about-non-affiliation')), findsOneWidget);
    expect(find.text(nonAffiliationDisclaimer), findsOneWidget);
    expect(
      nonAffiliationDisclaimer,
      contains('not built, maintained, endorsed by, or affiliated with'),
    );

    // P3.10 folded the Open source tab into the About page; the bundled
    // document opens from its row under Open source (owner review, 2061).
    final bundled = find.byKey(const ValueKey('about-bundled-components'));
    await tester.scrollUntilVisible(
      bundled,
      200,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.tap(bundled);
    await tester.pumpAndSettle();
    expect(find.text('Bundled components'), findsWidgets);
    expect(find.textContaining('sherpa-onnx'), findsWidgets);

    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: PrivacySettingsScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    final policy = find.byKey(const ValueKey('privacy-policy'));
    await tester.scrollUntilVisible(
      policy,
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(policy);
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('privacy-policy-viewer')), findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('privacy-policy-viewer')),
        matching: find.text('Privacy policy'),
      ),
      findsOneWidget,
    );
    expect(find.text('Where your data goes'), findsOneWidget);

    // The same sentence is the public README's opening claim, so the two
    // cannot drift apart.
    final readme = File(
      'README.md',
    ).readAsStringSync().replaceAll(RegExp(r'[>\s]+'), ' ');
    expect(readme, contains(nonAffiliationDisclaimer));
  });

  testWidgets('offline banner states that displayed data may be stale', (
    tester,
  ) async {
    // The shared status speaks for a selected server only (3d64653c: no
    // server, no status).
    final controller = await _controller(savedProfile: true)
      ..status = StreamStatus.disconnected;
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          // The app root publishes the one connection status every screen's
          // status slot reads (AppConnectionStatusScope, 3d64653c); this
          // host mounts the same adapter without the rest of the app.
          home: _SharedConnectionStatus(child: HomeScreen()),
        ),
      ),
    );
    await tester.pumpAndSettle();
    // The one status line says it; the staleness explanation and the raw
    // error live behind its Details action.
    await tester.pump(const Duration(seconds: 9));
    await tester.pump(const Duration(milliseconds: 300));
    expect(
      find.byKey(const ValueKey('connection-status-banner')),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const ValueKey('kit-status-more')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('connection-banner-details')));
    await tester.pumpAndSettle();
    expect(find.textContaining('may be stale'), findsOneWidget);
    await tester.pumpWidget(const MaterialApp(home: SizedBox()));
    controller.dispose();
  });
}

String _hitTestOwners(WidgetTester tester, Finder finder) => tester
    .hitTestOnBinding(tester.getCenter(finder))
    .path
    .where((entry) => entry.target is RenderObject)
    .take(5)
    .map((entry) => (entry.target as RenderObject).debugCreator)
    .join('\n');

/// The app root's shared connection status (main.dart
/// AppConnectionStatusScope) without the rest of the app.
class _SharedConnectionStatus extends ConsumerWidget {
  const _SharedConnectionStatus({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controller = ref.watch(connProvider);
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) => AppConditionsScope(
        conditions: [
          connectionKitStatus(
            context,
            controller,
            actionContext: () => context,
          ),
        ],
        child: child,
      ),
    );
  }
}
