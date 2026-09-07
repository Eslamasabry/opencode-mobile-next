import 'dart:async';

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
import 'package:opencode_mobile/platform/share_intent.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
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

class _ShortcutApi extends OpenCodeApi with CompleteMessageHistory {
  _ShortcutApi() : super(baseUrl: 'http://localhost:4096');

  int created = 0;
  int prompted = 0;
  Object? createError;
  Completer<Session>? pendingCreate;

  @override
  Future<Session> createSession() async {
    created += 1;
    if (createError != null) throw createError!;
    final pending = pendingCreate;
    pendingCreate = null;
    if (pending != null) return pending.future;
    return Session(id: 'shortcut-$created', title: 'New session');
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
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => const [];

  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => const [];
}

class _ShortcutRepository implements ProductRepository {
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

/// A controller in one of the states the shortcut router has to tell apart.
///
/// `connected` wires a fake transport as if a connect had finished.
/// `hasProfile: false` leaves no active server (first run). A saved profile
/// that `requiresPasswordReentry` never auto-connects. An unparsable
/// `baseUrl` makes the root's automatic connect fail synchronously with a
/// validation error, which is the cheapest deterministic "final error" state.
Future<ConnectionController> _controller({
  required bool connected,
  bool hasProfile = true,
  bool requiresPasswordReentry = false,
  String baseUrl = 'http://localhost:4096',
}) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  final profile = ServerProfile(
    id: 'server-1',
    name: 'Local',
    baseUrl: baseUrl,
    requiresPasswordReentry: requiresPasswordReentry,
  );
  final store = _MemoryProfileStore(prefs: preferences, savedProfile: profile);
  if (hasProfile) await store.setActiveId(profile.id);
  final controller = ConnectionController(store);
  if (connected) {
    controller
      ..api = _ShortcutApi()
      ..repository = _ShortcutRepository()
      ..version = '1.18.23'
      ..status = StreamStatus.connected;
  }
  return controller;
}

LaunchShortcut _shortcut() {
  final shortcut = LaunchShortcut(
    channel: const MethodChannel('oc/shortcut-test'),
  );
  addTearDown(shortcut.dispose);
  return shortcut;
}

Widget _app(
  ConnectionController controller,
  LaunchShortcut shortcut, {
  ShareIntent? share,
}) => ProviderScope(
  overrides: [
    bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
    connProvider.overrideWithValue(controller),
  ],
  child: OcApp(
    updateService: _NoUpdateService(),
    launchShortcut: shortcut,
    shareIntent: share,
  ),
);

/// The user-facing notice that a shortcut could not be honoured. The router
/// owns the wording and the surface (snack bar or banner); this only checks
/// that something was shown at all.
bool _noticeShown() =>
    find.byType(SnackBar).evaluate().isNotEmpty ||
    find.byType(MaterialBanner).evaluate().isNotEmpty;

/// Lets any snack bar timer run out before the tree is torn down.
Future<void> _drainNotices(WidgetTester tester) =>
    tester.pump(const Duration(seconds: 5));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('connect opens server selection and keeps the connection', (
    tester,
  ) async {
    final controller = await _controller(connected: true);
    addTearDown(controller.dispose);
    final api = controller.api! as _ShortcutApi;
    final repository = controller.repository;
    final shortcut = _shortcut();

    await tester.pumpWidget(_app(controller, shortcut));
    await tester.pumpAndSettle();
    expect(find.byType(ServersScreen), findsNothing);

    shortcut.pending.value = LaunchAction.connect;
    await tester.pumpAndSettle();

    expect(find.byType(ServersScreen), findsOneWidget);
    expect(find.byType(ChatScreen), findsNothing);
    expect(shortcut.pending.value, isNull);
    expect(api.created, 0);
    expect(controller.status, StreamStatus.connected);
    expect(identical(controller.api, api), isTrue);
    expect(identical(controller.repository, repository), isTrue);
    expect(controller.version, '1.18.23');
    expect(_noticeShown(), isFalse);
  });

  testWidgets('new task while connected creates exactly one empty session', (
    tester,
  ) async {
    final controller = await _controller(connected: true);
    addTearDown(controller.dispose);
    final api = controller.api! as _ShortcutApi;
    final shortcut = _shortcut();

    await tester.pumpWidget(_app(controller, shortcut));
    await tester.pumpAndSettle();
    shortcut.pending.value = LaunchAction.newTask;
    await tester.pumpAndSettle();

    final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
    expect(chat.sessionID, 'shortcut-1');
    expect(chat.initialText, isEmpty);
    expect(chat.initialAttachments, isEmpty);
    expect(api.created, 1);
    expect(api.prompted, 0);
    expect(shortcut.pending.value, isNull);
    expect(find.byType(ServersScreen), findsNothing);
    expect(_noticeShown(), isFalse);

    // Nothing re-fires the same launch on later controller changes.
    await controller.refreshSessions();
    await tester.pumpAndSettle();
    expect(api.created, 1);
    expect(find.byType(ChatScreen), findsOneWidget);
  });

  testWidgets('new task without a saved server stays on server selection', (
    tester,
  ) async {
    final controller = await _controller(connected: false, hasProfile: false);
    addTearDown(controller.dispose);
    final shortcut = _shortcut();

    await tester.pumpWidget(_app(controller, shortcut));
    await tester.pumpAndSettle();
    expect(find.byType(ServersScreen), findsOneWidget);

    shortcut.pending.value = LaunchAction.newTask;
    await tester.pumpAndSettle();

    expect(find.byType(ServersScreen), findsWidgets);
    expect(find.byType(ChatScreen), findsNothing);
    expect(_noticeShown(), isTrue);
    expect(shortcut.pending.value, isNull);
    expect(controller.api, isNull);

    // A connection arriving later must not resurrect the dropped action.
    controller
      ..api = _ShortcutApi()
      ..repository = _ShortcutRepository()
      ..version = '1.18.23'
      ..status = StreamStatus.connected;
    await controller.refreshSessions();
    await tester.pumpAndSettle();
    expect((controller.api! as _ShortcutApi).created, 0);
    expect(find.byType(ChatScreen), findsNothing);
    await _drainNotices(tester);
  });

  testWidgets('new task during password re-entry drops with a notice', (
    tester,
  ) async {
    final controller = await _controller(
      connected: false,
      requiresPasswordReentry: true,
    );
    addTearDown(controller.dispose);
    final shortcut = _shortcut();

    await tester.pumpWidget(_app(controller, shortcut));
    await tester.pumpAndSettle();
    expect(find.byType(ServersScreen), findsOneWidget);

    shortcut.pending.value = LaunchAction.newTask;
    await tester.pumpAndSettle();

    expect(find.byType(ServersScreen), findsWidgets);
    expect(find.byType(ChatScreen), findsNothing);
    expect(_noticeShown(), isTrue);
    expect(shortcut.pending.value, isNull);
    expect(controller.api, isNull);
    expect(controller.status, StreamStatus.disconnected);
    await _drainNotices(tester);
  });

  testWidgets('new task after a final connection error routes to servers', (
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
    expect(controller.status, StreamStatus.disconnected);
    expect(controller.connectionLoading, isFalse);
    expect(controller.lastError, isNotNull);
    expect(find.byType(ServersScreen), findsNothing);

    shortcut.pending.value = LaunchAction.newTask;
    await tester.pumpAndSettle();

    expect(find.byType(ServersScreen), findsOneWidget);
    expect(find.byType(ChatScreen), findsNothing);
    expect(_noticeShown(), isTrue);
    expect(shortcut.pending.value, isNull);
    expect(controller.api, isNull);
    await _drainNotices(tester);
  });

  testWidgets('new task while connecting waits and creates once ready', (
    tester,
  ) async {
    final controller = await _controller(connected: true);
    addTearDown(controller.dispose);
    final api = controller.api! as _ShortcutApi;
    controller.status = StreamStatus.connecting;
    final shortcut = _shortcut();

    await tester.pumpWidget(_app(controller, shortcut));
    await tester.pump(const Duration(milliseconds: 500));
    shortcut.pending.value = LaunchAction.newTask;
    await tester.pump(const Duration(milliseconds: 500));

    expect(api.created, 0);
    expect(shortcut.pending.value, LaunchAction.newTask);
    expect(find.byType(ServersScreen), findsNothing);
    expect(find.byType(ChatScreen), findsNothing);

    // Connected, but the saved location is still being restored.
    controller.status = StreamStatus.connected;
    controller.locationLoading = true;
    await controller.refreshSessions();
    await tester.pump(const Duration(milliseconds: 500));
    expect(api.created, 0);
    expect(shortcut.pending.value, LaunchAction.newTask);

    controller.locationLoading = false;
    controller.locationRevision += 1;
    await controller.refreshSessions();
    await tester.pumpAndSettle();

    expect(api.created, 1);
    final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
    expect(chat.sessionID, 'shortcut-1');
    expect(chat.initialText, isEmpty);
    expect(api.prompted, 0);
    expect(shortcut.pending.value, isNull);
    await _drainNotices(tester);
  });

  testWidgets(
    'a scope change aborts the stale creation without losing a newer launch',
    (tester) async {
      final controller = await _controller(connected: true);
      addTearDown(controller.dispose);
      final api = controller.api! as _ShortcutApi;
      final creation = Completer<Session>();
      api.pendingCreate = creation;
      final shortcut = _shortcut();

      await tester.pumpWidget(_app(controller, shortcut));
      await tester.pumpAndSettle();
      shortcut.pending.value = LaunchAction.newTask;
      await tester.pumpAndSettle();
      expect(api.created, 1);

      // The connection is replaced while the create is in flight, and the
      // user taps another shortcut before it resolves.
      controller.repository = _ShortcutRepository();
      shortcut.pending.value = LaunchAction.connect;
      await tester.pump();
      creation.complete(Session(id: 'stale-scope-session'));
      await tester.pumpAndSettle();

      expect(find.byType(ChatScreen), findsNothing);
      expect(find.byType(ServersScreen), findsOneWidget);
      expect(shortcut.pending.value, isNull);
      expect(api.created, 1);
      expect(api.prompted, 0);
      await _drainNotices(tester);
    },
  );

  testWidgets(
    'duplicate rapid new task taps while creation is held coalesce to one',
    (tester) async {
      final controller = await _controller(connected: true);
      addTearDown(controller.dispose);
      final api = controller.api! as _ShortcutApi;
      final creation = Completer<Session>();
      api.pendingCreate = creation;
      final shortcut = _shortcut();

      await tester.pumpWidget(_app(controller, shortcut));
      await tester.pumpAndSettle();
      shortcut.pending.value = LaunchAction.newTask;
      await tester.pump();
      await tester.pump();
      expect(api.created, 1);

      // The same shortcut is delivered again while the first create is still
      // in flight. Whether the notifier fires (value was taken) or stays
      // silent (same value), the outcome must be a single empty session.
      shortcut.pending.value = LaunchAction.newTask;
      await tester.pump();
      shortcut.pending.value = LaunchAction.newTask;
      await tester.pump();
      expect(api.created, 1);

      creation.complete(Session(id: 'shortcut-held', title: 'New session'));
      await tester.pumpAndSettle();

      expect(api.created, 1);
      expect(api.prompted, 0);
      expect(find.byType(ChatScreen), findsOneWidget);
      final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
      expect(chat.sessionID, 'shortcut-held');
      expect(chat.initialText, isEmpty);
      expect(shortcut.pending.value, isNull);

      // Nothing was left behind to fire on the next controller change.
      await controller.refreshSessions();
      await tester.pumpAndSettle();
      expect(api.created, 1);
      expect(find.byType(ChatScreen), findsOneWidget);
      await _drainNotices(tester);
    },
  );

  testWidgets('new task leaves a persisted composer draft untouched', (
    tester,
  ) async {
    final controller = await _controller(connected: true);
    addTearDown(controller.dispose);
    final api = controller.api! as _ShortcutApi;
    final shortcut = _shortcut();

    // Text the user typed into another session's composer and never sent.
    await controller.saveSessionDraft('existing-session', 'unsent words');
    expect(controller.sessionDraft('existing-session'), 'unsent words');
    final draftsBefore = controller.store.prefs.getString('oc.sessionDrafts');
    expect(draftsBefore, contains('unsent words'));

    await tester.pumpWidget(_app(controller, shortcut));
    await tester.pumpAndSettle();
    shortcut.pending.value = LaunchAction.newTask;
    await tester.pumpAndSettle();

    final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
    expect(chat.sessionID, 'shortcut-1');
    expect(chat.initialText, isEmpty);
    expect(api.created, 1);
    expect(api.prompted, 0);

    // The draft is neither consumed as the new session's prompt nor evicted
    // from storage, and the fresh session starts without one.
    expect(controller.sessionDraft('existing-session'), 'unsent words');
    expect(controller.sessionDraft('shortcut-1'), isNull);
    expect(controller.store.prefs.getString('oc.sessionDrafts'), draftsBefore);
    await _drainNotices(tester);
  });

  testWidgets('new task leaves a held shared draft untouched', (tester) async {
    final controller = await _controller(connected: true);
    addTearDown(controller.dispose);
    final api = controller.api! as _ShortcutApi;
    final shortcut = _shortcut();
    final share = ShareIntent(channel: const MethodChannel('oc/share-test'));
    addTearDown(share.dispose);

    // A share whose session failed is held behind an explicit Retry.
    api.createError = StateError('server hiccup');
    await tester.pumpWidget(_app(controller, shortcut, share: share));
    await tester.pumpAndSettle();
    share.pending.value = 'draft to keep';
    await tester.pumpAndSettle();
    expect(api.created, 1);
    expect(find.byType(ChatScreen), findsNothing);
    expect(share.pending.value, 'draft to keep');

    // The shortcut must open its own empty session and not spend the draft.
    api.createError = null;
    shortcut.pending.value = LaunchAction.newTask;
    await tester.pumpAndSettle();

    final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
    expect(chat.sessionID, 'shortcut-2');
    expect(chat.initialText, isEmpty);
    expect(api.created, 2);
    expect(api.prompted, 0);
    expect(shortcut.pending.value, isNull);
    expect(share.pending.value, 'draft to keep');
    await _drainNotices(tester);
  });
}
