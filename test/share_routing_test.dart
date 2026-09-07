import 'dart:async';

import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/platform/share_intent.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
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

class _ShareApi extends OpenCodeApi with CompleteMessageHistory {
  _ShareApi() : super(baseUrl: 'http://localhost:4096');

  int created = 0;
  Object? createError;
  Completer<Session>? pendingCreate;

  @override
  Future<Session> createSession() async {
    created += 1;
    if (createError != null) throw createError!;
    final pending = pendingCreate;
    pendingCreate = null;
    if (pending != null) return pending.future;
    return Session(id: 'shared-$created', title: 'New session');
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

class _ShareRepository implements ProductRepository {
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

Future<ConnectionController> _controller({required bool connected}) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  final profile = ServerProfile(
    id: 'server-1',
    name: 'Local',
    baseUrl: 'http://localhost:4096',
  );
  final store = _MemoryProfileStore(prefs: preferences, savedProfile: profile);
  await store.setActiveId(profile.id);
  final controller = ConnectionController(store);
  if (connected) {
    controller
      ..api = _ShareApi()
      ..repository = _ShareRepository()
      ..version = '1.18.23'
      ..status = StreamStatus.connected;
  }
  return controller;
}

Widget _app(ConnectionController controller, ShareIntent share) =>
    ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
        connProvider.overrideWithValue(controller),
      ],
      child: OcApp(updateService: _NoUpdateService(), shareIntent: share),
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('shared text opens a new session with the text as the prompt', (
    tester,
  ) async {
    final controller = await _controller(connected: true);
    addTearDown(controller.dispose);
    final share = ShareIntent(channel: const MethodChannel('oc/share-test'));
    addTearDown(share.dispose);

    await tester.pumpWidget(_app(controller, share));
    await tester.pump();
    share.pending.value = 'https://example.com/issue/42';
    await tester.pump();
    await tester.pump();
    await tester.pumpAndSettle();

    final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
    expect(chat.sessionID, 'shared-1');
    expect(chat.initialText, 'https://example.com/issue/42');
    expect(share.pending.value, isNull);
  });

  testWidgets('shared text waits for a connection and says so once', (
    tester,
  ) async {
    final controller = await _controller(connected: false);
    addTearDown(controller.dispose);
    final share = ShareIntent(channel: const MethodChannel('oc/share-test'));
    addTearDown(share.dispose);

    await tester.pumpWidget(_app(controller, share));
    await tester.pump();
    share.pending.value = 'paste me later';
    await tester.pump();
    await tester.pump();

    expect(find.textContaining('Connect to a server'), findsOneWidget);
    expect(share.pending.value, 'paste me later');
    expect(find.byType(ChatScreen), findsNothing);
    // Let the snackbar's own timer run out before the tree is torn down.
    await tester.pump(const Duration(seconds: 5));
  });

  testWidgets(
    'startup share waits for connection and saved location restoration',
    (tester) async {
      final controller = await _controller(connected: true);
      addTearDown(controller.dispose);
      final api = controller.api! as _ShareApi;
      controller.status = StreamStatus.connecting;
      final share = ShareIntent(channel: const MethodChannel('oc/share-test'));
      addTearDown(share.dispose);
      await tester.pumpWidget(_app(controller, share));
      await tester.pump(const Duration(milliseconds: 500));
      share.pending.value = 'earlier startup text';
      await tester.pump(const Duration(milliseconds: 500));
      expect(api.created, 0);
      controller.status = StreamStatus.connected;
      controller.locationLoading = true;
      share.pending.value = 'latest startup text';
      await tester.pump(const Duration(milliseconds: 500));
      expect(api.created, 0);
      controller.locationLoading = false;
      controller.locationRevision += 1;
      await controller.refreshSessions();
      await tester.pumpAndSettle();
      expect(api.created, 1);
      expect(
        tester.widget<ChatScreen>(find.byType(ChatScreen)).initialText,
        'latest startup text',
      );
      expect(share.pending.value, isNull);
      expect(find.byType(MaterialBanner), findsNothing);
      await tester.pump(const Duration(seconds: 5));
    },
  );

  testWidgets('failed shared session keeps text and waits for explicit retry', (
    tester,
  ) async {
    final controller = await _controller(connected: true);
    addTearDown(controller.dispose);
    final api = controller.api! as _ShareApi;
    api.createError = StateError('sensitive raw server failure');
    final share = ShareIntent(channel: const MethodChannel('oc/share-test'));
    addTearDown(share.dispose);
    await tester.pumpWidget(_app(controller, share));
    await tester.pumpAndSettle();
    share.pending.value = 'keep this shared text';
    await tester.pumpAndSettle();

    expect(api.created, 1);
    expect(share.pending.value, 'keep this shared text');
    expect(find.byType(ChatScreen), findsNothing);
    expect(find.textContaining('sensitive raw'), findsNothing);
    expect(find.byType(MaterialBanner), findsOneWidget);
    await controller.refreshSessions();
    await tester.pump(const Duration(seconds: 8));
    expect(api.created, 1);
    expect(find.text('Retry'), findsOneWidget);

    api.createError = null;
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
    expect(chat.initialText, 'keep this shared text');
    expect(api.created, 2);
    expect(share.pending.value, isNull);
    expect(find.byType(MaterialBanner), findsNothing);
  });

  testWidgets('a newer share survives an older in-flight creation failure', (
    tester,
  ) async {
    final controller = await _controller(connected: true);
    addTearDown(controller.dispose);
    final api = controller.api! as _ShareApi;
    final creation = Completer<Session>();
    api.pendingCreate = creation;
    final share = ShareIntent(channel: const MethodChannel('oc/share-test'));
    addTearDown(share.dispose);
    await tester.pumpWidget(_app(controller, share));
    await tester.pumpAndSettle();
    share.pending.value = 'older text';
    await tester.pumpAndSettle();
    share.pending.value = 'newest text';
    await tester.pump();
    expect(api.created, 1);
    creation.completeError(StateError('creation failed'));
    await tester.pumpAndSettle();
    final chat = tester.widget<ChatScreen>(find.byType(ChatScreen));
    expect(chat.initialText, 'newest text');
    expect(api.created, 2);
    expect(share.pending.value, isNull);
  });

  testWidgets('share creation cannot route into a replacement connection', (
    tester,
  ) async {
    final controller = await _controller(connected: true);
    addTearDown(controller.dispose);
    final api = controller.api! as _ShareApi;
    final creation = Completer<Session>();
    api.pendingCreate = creation;
    final share = ShareIntent(channel: const MethodChannel('oc/share-test'));
    addTearDown(share.dispose);
    await tester.pumpWidget(_app(controller, share));
    await tester.pumpAndSettle();
    share.pending.value = 'review in the right connection';
    await tester.pumpAndSettle();
    controller.repository = _ShareRepository();
    creation.complete(Session(id: 'old-connection-session'));
    await tester.pumpAndSettle();
    expect(find.byType(ChatScreen), findsNothing);
    expect(share.pending.value, 'review in the right connection');
    expect(find.text('Retry'), findsOneWidget);
    expect(api.created, 1);
  });
}
