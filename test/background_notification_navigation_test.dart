import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/activity_screen.dart';
import 'package:opencode_mobile/update/shorebird_update_notice.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _MemoryProfileStore extends ProfileStore {
  _MemoryProfileStore({required super.prefs, required this.savedProfile});

  final ServerProfile savedProfile;
  String? selectedID;

  @override
  List<ServerProfile> get profiles => [savedProfile];

  @override
  String? get activeId => selectedID;

  @override
  Future<void> setActiveId(String? id) async {
    selectedID = id;
  }
}

class _NavigationApi extends OpenCodeApi with CompleteMessageHistory {
  _NavigationApi({required this.question})
    : super(baseUrl: 'http://localhost:4096');

  final PendingQuestion question;

  @override
  Future<List<MessageWithParts>> messages(String id) async => const [];

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => const [];

  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => [
    {
      'id': question.id,
      'sessionID': question.sessionID,
      'questions': [
        for (final prompt in question.prompts)
          {
            'header': prompt.title,
            'question': prompt.question,
            'multiple': prompt.multiple,
            'custom': prompt.custom,
            'options': [
              for (final choice in prompt.choices)
                {'label': choice.label, 'description': choice.description},
            ],
          },
      ],
    },
  ];
}

class _NavigationRepository implements ProductRepository {
  _NavigationRepository(this.question);

  final PendingQuestion question;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];

  @override
  Future<List<PendingQuestion>> listQuestions() async => [question];

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _NoUpdateService implements AppUpdateService {
  @override
  bool get isAvailable => false;

  @override
  Future<AppUpdateState> checkForUpdate() async => AppUpdateState.unavailable;

  @override
  Future<void> downloadUpdate() async {}
}

Future<ConnectionController> _controllerFor(
  CodingAlertKind kind, {
  String? tapProfileID,
}) async {
  SharedPreferences.setMockInitialValues({
    BackgroundLiveController.preferenceKey: true,
  });
  final preferences = await SharedPreferences.getInstance();
  final profile = ServerProfile(
    id: 'server-1',
    name: 'Local',
    baseUrl: 'http://localhost:4096',
  );
  final store = _MemoryProfileStore(prefs: preferences, savedProfile: profile);
  await store.setActiveId(profile.id);
  var consumed = false;
  final backgroundLive = BackgroundLiveController(
    preferences: preferences,
    // Fake time never advances past the debounce; send status at once.
    liveStatusDebounce: Duration.zero,
    invoke: (method, [arguments]) async {
      if (method == 'consumeCodingAlertOpen' && !consumed) {
        consumed = true;
        return {
          'kind': kind.wireValue,
          'sessionID': 'session-1',
          'profileID': ?tapProfileID,
        };
      }
      return const {
        'enabled': true,
        'active': true,
        'notificationGranted': true,
        'batteryOptimizationIgnored': false,
      };
    },
  );
  const question = PendingQuestion(
    id: 'question-1',
    sessionID: 'session-1',
    prompts: [
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
  final controller = ConnectionController(store, backgroundLive: backgroundLive)
    ..api = _NavigationApi(question: question)
    ..repository = _NavigationRepository(question)
    ..version = '1.18.23'
    ..status = StreamStatus.connected
    ..sessionsById['session-1'] = Session(
      id: 'session-1',
      title: 'Background session',
    );
  controller.permissions['permission-1'] = PermissionRequest(
    id: 'permission-1',
    sessionID: 'session-1',
    permission: 'edit',
    patterns: const ['lib/main.dart'],
  );
  controller.questions[question.id] = question;
  return controller;
}

Widget _app(ConnectionController controller) => ProviderScope(
  overrides: [
    bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
    connProvider.overrideWithValue(controller),
  ],
  child: OcApp(updateService: _NoUpdateService()),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('permission notification opens its exact chat and request', (
    tester,
  ) async {
    final controller = await _controllerFor(CodingAlertKind.permission);
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pump();
    await tester.pump();
    await tester.pumpAndSettle();

    final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
    expect(chat.sessionID, 'session-1');
    expect(find.text('Edit a file'), findsOneWidget);
    expect(find.text('Allow once'), findsOneWidget);
  });

  testWidgets('question notification opens the exact answer sheet', (
    tester,
  ) async {
    final controller = await _controllerFor(CodingAlertKind.question);
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pump();
    await tester.pump();
    await tester.pumpAndSettle();

    expect(find.byType(ActivityScreen), findsOneWidget);
    expect(find.text('Deployment'), findsWidgets);
    expect(find.text('Which target should be used?'), findsWidgets);
    expect(find.text('Send answers'), findsOneWidget);
  });

  testWidgets('widget row tap on the active profile opens its exact chat', (
    tester,
  ) async {
    final controller = await _controllerFor(
      CodingAlertKind.complete,
      tapProfileID: 'server-1',
    );
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pump();
    await tester.pump();
    await tester.pumpAndSettle();

    final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
    expect(chat.sessionID, 'session-1');
  });

  testWidgets('widget row tap from another profile opens the app normally', (
    tester,
  ) async {
    final controller = await _controllerFor(
      CodingAlertKind.complete,
      tapProfileID: 'server-2',
    );
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pump();
    await tester.pump();
    await tester.pumpAndSettle();

    // No silent profile switch: the stale destination is dropped and the
    // app stays on its normal root instead of pushing a chat.
    expect(find.byType(ChatScreen), findsNothing);
    expect(find.byType(ActivityScreen), findsNothing);
    expect(controller.pendingCodingAlertOpen, isNull);
  });
}
