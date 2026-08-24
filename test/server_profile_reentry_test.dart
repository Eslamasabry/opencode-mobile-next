import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _MemoryProfileStore extends ProfileStore {
  _MemoryProfileStore({
    required super.prefs,
    required this.profile,
    this.failUpsert = false,
    this.failRemove = false,
  });

  ServerProfile profile;
  bool failUpsert;
  bool failRemove;
  int removeCalls = 0;

  @override
  List<ServerProfile> get profiles => [profile];

  @override
  String? get activeId => profile.id;

  @override
  Future<void> upsert(ServerProfile value) async {
    if (failUpsert) throw StateError('storage unavailable');
    value.requiresPasswordReentry = false;
    profile = value;
  }

  @override
  Future<void> remove(String id) async {
    removeCalls++;
    if (failRemove) throw StateError('storage unavailable');
  }
}

class _RecordingConnection extends ConnectionController {
  _RecordingConnection(super.store);

  int connectCalls = 0;
  int disconnectCalls = 0;

  @override
  Future<void> connect(ServerProfile profile) async {
    connectCalls++;
  }

  @override
  Future<void> disconnect({
    bool keepActive = false,
    bool silent = false,
  }) async {
    disconnectCalls++;
  }
}

class _RecordingSecureStorage extends FlutterSecureStorage {
  _RecordingSecureStorage();

  final Map<String, String> values = {};
  bool failWrite = false;
  bool failDelete = false;

  @override
  Future<String?> read({
    required String key,
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async => values[key];

  @override
  Future<void> write({
    required String key,
    required String? value,
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    if (failWrite) throw StateError('secure write failed');
    if (value == null) {
      values.remove(key);
    } else {
      values[key] = value;
    }
  }

  @override
  Future<void> delete({
    required String key,
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    if (failDelete) throw StateError('secure delete failed');
    values.remove(key);
  }
}

Future<(_MemoryProfileStore, _RecordingConnection)> _memoryState({
  bool requiresPasswordReentry = false,
  bool failUpsert = false,
  bool failRemove = false,
}) async {
  SharedPreferences.setMockInitialValues({});
  final profile = ServerProfile(
    id: 'server-1',
    name: 'Workstation',
    baseUrl: 'https://server.example:4096',
    username: 'opencode',
    requiresPasswordReentry: requiresPasswordReentry,
  );
  final store = _MemoryProfileStore(
    prefs: await SharedPreferences.getInstance(),
    profile: profile,
    failUpsert: failUpsert,
    failRemove: failRemove,
  );
  return (store, _RecordingConnection(store));
}

Widget _serversApp(
  _MemoryProfileStore store,
  _RecordingConnection connection,
) => ProviderScope(
  overrides: [
    bootstrapProvider.overrideWithValue(AppBootstrap(store)),
    connProvider.overrideWithValue(connection),
  ],
  child: const MaterialApp(home: ServersScreen()),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'startup does not auto-connect when the active password needs re-entry',
    (tester) async {
      final semantics = tester.ensureSemantics();
      final (store, connection) = await _memoryState(
        requiresPasswordReentry: true,
      );
      addTearDown(connection.dispose);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            bootstrapProvider.overrideWithValue(AppBootstrap(store)),
            connProvider.overrideWithValue(connection),
          ],
          child: const OcApp(),
        ),
      );
      await tester.pump();

      expect(connection.connectCalls, 0);
      expect(find.byKey(const Key('password-reentry-banner')), findsOneWidget);
      expect(find.text('Password re-entry required'), findsOneWidget);
      expect(
        find.bySemanticsLabel(
          RegExp('Password re-entry required for the active server'),
        ),
        findsOneWidget,
      );
      semantics.dispose();

      await tester.tap(find.text('Workstation'));
      await tester.pumpAndSettle();

      expect(connection.connectCalls, 0);
      expect(find.text('Re-enter password'), findsNWidgets(2));
      expect(
        find.textContaining('The saved password is unavailable'),
        findsOneWidget,
      );
    },
  );

  testWidgets('failed profile save exposes an actionable error', (
    tester,
  ) async {
    final (store, connection) = await _memoryState(failUpsert: true);
    addTearDown(connection.dispose);
    await tester.pumpWidget(_serversApp(store, connection));

    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Edit'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(find.textContaining('Could not save Workstation'), findsOneWidget);
    expect(find.textContaining('Check device storage'), findsOneWidget);
    expect(connection.connectCalls, 0);
  });

  testWidgets('failed active delete keeps the current connection', (
    tester,
  ) async {
    final (store, connection) = await _memoryState(failRemove: true);
    addTearDown(connection.dispose);
    await tester.pumpWidget(_serversApp(store, connection));

    await tester.longPress(find.text('Workstation'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Remove'));
    await tester.pumpAndSettle();

    expect(store.removeCalls, 1);
    expect(connection.disconnectCalls, 0);
    expect(find.textContaining('Could not remove Workstation'), findsOneWidget);
    expect(find.textContaining('current connection were kept'), findsOneWidget);
  });

  test(
    'failed secure write leaves profile metadata and cache unchanged',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final secure = _RecordingSecureStorage();
      final store = ProfileStore(prefs: prefs, secure: secure);
      await store.load();
      await store.upsert(
        ServerProfile(
          id: 'server-1',
          name: 'Original',
          baseUrl: 'https://old.example:4096',
          password: 'old-secret',
        ),
      );

      secure.failWrite = true;
      await expectLater(
        store.upsert(
          ServerProfile(
            id: 'server-1',
            name: 'Changed',
            baseUrl: 'https://new.example:4096',
            password: 'new-secret',
          ),
        ),
        throwsStateError,
      );

      expect(store.profiles.single.name, 'Original');
      expect(prefs.getString('oc.profiles'), contains('Original'));
      expect(prefs.getString('oc.profiles'), isNot(contains('Changed')));
    },
  );

  test('failed secure delete preserves an active profile', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final secure = _RecordingSecureStorage();
    final store = ProfileStore(prefs: prefs, secure: secure);
    await store.load();
    await store.upsert(
      ServerProfile(
        id: 'server-1',
        name: 'Workstation',
        baseUrl: 'https://server.example:4096',
        password: 'secret',
      ),
    );
    await store.setActiveId('server-1');

    secure.failDelete = true;
    await expectLater(store.remove('server-1'), throwsStateError);

    expect(store.profiles.single.id, 'server-1');
    expect(store.activeId, 'server-1');
    expect(prefs.getString('oc.profiles'), contains('Workstation'));
  });
}
