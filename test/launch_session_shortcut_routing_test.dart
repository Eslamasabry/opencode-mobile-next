import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/platform/launch_shortcut.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/update/shorebird_update_notice.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Routing for the F6 launch surfaces: a pinned-session launcher shortcut
/// (cold and warm delivery) and the Quick Settings tile's Activity action.
/// Both are explicit navigation only — nothing is created or sent.

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

class _LaunchApi extends OpenCodeApi with CompleteMessageHistory {
  _LaunchApi() : super(baseUrl: 'http://localhost:4096');

  int created = 0;
  int prompted = 0;

  @override
  Future<Session> createSession() async {
    created += 1;
    return Session(id: 'created-$created', title: 'New conversation');
  }

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) async {
    prompted += 1;
  }

  @override
  Future<List<MessageWithParts>> messages(String id) async => const [];

  @override
  Future<List<Session>> sessions() async => [
    Session(id: 'pinned-1', title: 'Fix auth'),
  ];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => const [];

  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => const [];
}

class _LaunchRepository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];

  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];

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

Future<ConnectionController> _controller({
  required bool connected,
  bool hasProfile = true,
  String baseUrl = 'http://localhost:4096',
}) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  final profile = ServerProfile(
    id: 'server-1',
    name: 'Local',
    baseUrl: baseUrl,
  );
  final store = _MemoryProfileStore(prefs: preferences, savedProfile: profile);
  if (hasProfile) await store.setActiveId(profile.id);
  final controller = ConnectionController(store);
  if (connected) {
    controller
      ..api = _LaunchApi()
      ..repository = _LaunchRepository()
      ..version = '1.18.23'
      ..status = StreamStatus.connected;
  }
  return controller;
}

const _testChannel = MethodChannel('oc/shortcut-test');

LaunchShortcut _shortcut() {
  final shortcut = LaunchShortcut(channel: _testChannel);
  addTearDown(shortcut.dispose);
  return shortcut;
}

const _pinned = SessionLaunch(profileID: 'server-1', sessionID: 'pinned-1');

Widget _app(ConnectionController controller, LaunchShortcut shortcut) =>
    ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
        connProvider.overrideWithValue(controller),
      ],
      child: OcApp(updateService: _NoUpdateService(), launchShortcut: shortcut),
    );

bool _noticeShown() => _appNotice.evaluate().isNotEmpty;

/// The shared connection line (P4.4, 3d64653c). It outranks the app's
/// one-shot notice in the one status slot (connection, app stopped, heat,
/// local work, update), so where the connection failed it is what the
/// person reads on Servers, with its way forward.
bool _connectionLineShown() => find
    .byKey(const ValueKey('connection-status-banner'))
    .evaluate()
    .isNotEmpty;

Future<void> _drainNotices(WidgetTester tester) =>
    tester.pump(const Duration(seconds: 9));

/// The app's own status line (main.dart's notice, KitStatusLine keys
/// `kit-status-app:*`), which replaced the snack bars and the banner.
final _appNotice = find.byWidgetPredicate((widget) {
  final key = widget.key;
  return key is ValueKey && '${key.value}'.startsWith('kit-status-app:');
});

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() => messenger.setMockMethodCallHandler(_testChannel, null));

  testWidgets('a warm pinned-session tap opens exactly that chat, once', (
    tester,
  ) async {
    final controller = await _controller(connected: true);
    addTearDown(controller.dispose);
    final api = controller.api! as _LaunchApi;
    final shortcut = _shortcut();

    await tester.pumpWidget(_app(controller, shortcut));
    await tester.pumpAndSettle();
    expect(find.byType(ChatScreen), findsNothing);

    shortcut.pendingSession.value = _pinned;
    await tester.pumpAndSettle();

    final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
    expect(chat.sessionID, 'pinned-1');
    expect(chat.initialText, isEmpty);
    expect(chat.initialAttachments, isEmpty);
    expect(api.created, 0);
    expect(api.prompted, 0);
    expect(shortcut.pendingSession.value, isNull);
    expect(find.byType(ServersScreen), findsNothing);
    expect(_noticeShown(), isFalse);

    // Nothing re-fires the same launch on later controller changes.
    await controller.refreshSessions();
    await tester.pumpAndSettle();
    expect(find.byType(ChatScreen), findsOneWidget);
    expect(api.created, 0);
    expect(api.prompted, 0);
  });

  testWidgets('a cold-start pinned-session tap is drained and routed once', (
    tester,
  ) async {
    var consumes = 0;
    messenger.setMockMethodCallHandler(_testChannel, (call) async {
      if (call.method == 'consumeSessionLaunch') {
        consumes += 1;
        return {'profileID': 'server-1', 'sessionID': 'pinned-1'};
      }
      return null;
    });
    final controller = await _controller(connected: true);
    addTearDown(controller.dispose);
    final api = controller.api! as _LaunchApi;
    final shortcut = _shortcut();

    await tester.pumpWidget(_app(controller, shortcut));
    await tester.pumpAndSettle();

    expect(consumes, 1);
    final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
    expect(chat.sessionID, 'pinned-1');
    expect(chat.initialText, isEmpty);
    expect(api.created, 0);
    expect(api.prompted, 0);
    expect(shortcut.pendingSession.value, isNull);
    expect(_noticeShown(), isFalse);
  });

  testWidgets('a warm tap on the chat already showing does not stack it', (
    tester,
  ) async {
    final controller = await _controller(connected: true);
    addTearDown(controller.dispose);
    final shortcut = _shortcut();

    await tester.pumpWidget(_app(controller, shortcut));
    await tester.pumpAndSettle();
    shortcut.pendingSession.value = _pinned;
    await tester.pumpAndSettle();
    expect(find.byType(ChatScreen), findsOneWidget);

    shortcut.pendingSession.value = _pinned;
    await tester.pumpAndSettle();
    expect(find.byType(ChatScreen), findsOneWidget);
    expect(shortcut.pendingSession.value, isNull);
  });

  testWidgets('a tap for another server is dropped without switching', (
    tester,
  ) async {
    final controller = await _controller(connected: true);
    addTearDown(controller.dispose);
    final api = controller.api! as _LaunchApi;
    final shortcut = _shortcut();

    await tester.pumpWidget(_app(controller, shortcut));
    await tester.pumpAndSettle();

    shortcut.pendingSession.value = const SessionLaunch(
      profileID: 'server-2',
      sessionID: 'foreign-1',
    );
    await tester.pumpAndSettle();

    expect(find.byType(ChatScreen), findsNothing);
    expect(find.byType(ServersScreen), findsNothing);
    expect(_noticeShown(), isTrue);
    expect(shortcut.pendingSession.value, isNull);
    expect(controller.store.activeId, 'server-1');
    expect(identical(controller.api, api), isTrue);
    expect(api.created, 0);
    expect(api.prompted, 0);

    // A later controller change must not resurrect the dropped launch.
    await controller.refreshSessions();
    await tester.pumpAndSettle();
    expect(find.byType(ChatScreen), findsNothing);
    await _drainNotices(tester);
  });

  testWidgets('a tap without a saved server stays on server selection', (
    tester,
  ) async {
    final controller = await _controller(connected: false, hasProfile: false);
    addTearDown(controller.dispose);
    final shortcut = _shortcut();

    await tester.pumpWidget(_app(controller, shortcut));
    await tester.pumpAndSettle();
    expect(find.byType(ServersScreen), findsOneWidget);

    shortcut.pendingSession.value = _pinned;
    await tester.pumpAndSettle();

    expect(find.byType(ServersScreen), findsWidgets);
    expect(find.byType(ChatScreen), findsNothing);
    expect(_noticeShown(), isTrue);
    expect(shortcut.pendingSession.value, isNull);
    expect(controller.api, isNull);
    await _drainNotices(tester);
  });

  testWidgets('a tap after a final connection error routes to servers', (
    tester,
  ) async {
    final controller = await _controller(
      connected: false,
      baseUrl: 'localhost:4096', // no scheme → synchronous validation error
    );
    addTearDown(controller.dispose);
    final shortcut = _shortcut();

    await tester.pumpWidget(_app(controller, shortcut));
    await tester.pumpAndSettle();
    expect(controller.lastError, isNotNull);

    shortcut.pendingSession.value = _pinned;
    await tester.pumpAndSettle();

    expect(find.byType(ServersScreen), findsOneWidget);
    expect(find.byType(ChatScreen), findsNothing);
    // The connection line outranks the launch's notice (3d64653c).
    expect(_connectionLineShown(), isTrue);
    expect(shortcut.pendingSession.value, isNull);
    await _drainNotices(tester);
  });

  testWidgets('a tap while connecting waits and opens once ready', (
    tester,
  ) async {
    final controller = await _controller(connected: true);
    addTearDown(controller.dispose);
    final api = controller.api! as _LaunchApi;
    controller.status = StreamStatus.connecting;
    final shortcut = _shortcut();

    await tester.pumpWidget(_app(controller, shortcut));
    await tester.pump(const Duration(milliseconds: 500));
    shortcut.pendingSession.value = _pinned;
    await tester.pump(const Duration(milliseconds: 500));

    expect(shortcut.pendingSession.value, _pinned);
    expect(find.byType(ChatScreen), findsNothing);
    expect(find.byType(ServersScreen), findsNothing);

    controller.status = StreamStatus.connected;
    controller.locationLoading = true;
    await controller.refreshSessions();
    await tester.pump(const Duration(milliseconds: 500));
    expect(shortcut.pendingSession.value, _pinned);
    expect(find.byType(ChatScreen), findsNothing);

    controller.locationLoading = false;
    controller.locationRevision += 1;
    await controller.refreshSessions();
    await tester.pumpAndSettle();

    final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
    expect(chat.sessionID, 'pinned-1');
    expect(api.created, 0);
    expect(api.prompted, 0);
    expect(shortcut.pendingSession.value, isNull);
    await _drainNotices(tester);
  });

  testWidgets('the Quick Settings tile opens Chats on Needs you', (
    tester,
  ) async {
    final controller = await _controller(connected: true);
    addTearDown(controller.dispose);
    final api = controller.api! as _LaunchApi;
    final shortcut = _shortcut();

    await tester.pumpWidget(_app(controller, shortcut));
    await tester.pumpAndSettle();

    shortcut.pending.value = LaunchAction.activity;
    await tester.pumpAndSettle();

    // The Inbox is part of Chats: the tile lands there on "Needs you".
    expect(find.byType(ChatScreen), findsNothing);
    expect(
      tester
          .widget<ChatsHomeScreen>(find.byType(ChatsHomeScreen))
          .initialFilter,
      const ChatFeedFilter(needsYou: true),
    );
    expect(shortcut.pending.value, isNull);
    expect(api.created, 0);
    expect(api.prompted, 0);
    expect(_noticeShown(), isFalse);

    // A second tap while Chats shows does not stack anything over it.
    shortcut.pending.value = LaunchAction.activity;
    await tester.pumpAndSettle();
    expect(find.byType(ChatsHomeScreen), findsOneWidget);
  });

  testWidgets('the tile without a saved server stays on server selection', (
    tester,
  ) async {
    final controller = await _controller(connected: false, hasProfile: false);
    addTearDown(controller.dispose);
    final shortcut = _shortcut();

    await tester.pumpWidget(_app(controller, shortcut));
    await tester.pumpAndSettle();

    shortcut.pending.value = LaunchAction.activity;
    await tester.pumpAndSettle();

    expect(find.byType(ServersScreen), findsWidgets);
    expect(_noticeShown(), isTrue);
    expect(shortcut.pending.value, isNull);
    await _drainNotices(tester);
  });
}
