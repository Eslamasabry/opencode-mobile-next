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

class _ShareApi extends OpenCodeApi {
  _ShareApi() : super(baseUrl: 'http://localhost:4096');

  int created = 0;

  @override
  Future<Session> createSession() async {
    created += 1;
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
}
