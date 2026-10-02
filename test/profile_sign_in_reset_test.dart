import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

const _opaque = 'synthetic-reset-opaque-value';

class _ResetSecureStorage extends FlutterSecureStorage {
  final values = <String, String>{};
  final deleted = <String>[];
  int enumerations = 0;
  int? failEnumeration;
  String? failDelete;
  String? retainDelete;

  @override
  Future<Map<String, String>> readAll({
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    if (++enumerations == failEnumeration) throw StateError(_opaque);
    return Map.of(values);
  }

  @override
  Future<String?> read({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async => values[key];

  @override
  Future<void> delete({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    deleted.add(key);
    if (key == failDelete) throw StateError(_opaque);
    if (key != retainDelete) values.remove(key);
  }
}

class _ResetPreferences extends InMemorySharedPreferencesStore {
  _ResetPreferences(super.data) : super.withData();
  bool refuseActiveRemoval = false;

  @override
  Future<bool> remove(String key) async =>
      (key == 'oc.activeProfile' || key == 'flutter.oc.activeProfile') &&
          refuseActiveRemoval
      ? false
      : super.remove(key);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late _ResetPreferences disk;
  late _ResetSecureStorage secure;
  late ProfileStore store;
  late Map<String, Object> preserved;

  setUp(() async {
    KitRedact.clearKnownSecrets();
    preserved = {
      'oc.profiles': jsonEncode([
        ServerProfile(
          id: 'server',
          name: 'Saved server',
          baseUrl: 'https://example.invalid',
        ).toJson(),
        ServerProfile(
          id: 'agent',
          name: 'Saved agent',
          baseUrl: 'ws://localhost:1234',
          backend: ServerBackend.codex,
        ).toJson(),
      ]),
      'oc.offlineQueue': 'retained queue source',
      'oc.sessionDrafts': 'retained draft source',
      'oc.keptQueuedPrompts': 'retained saved prompts',
      'oc.automation.server': 'retained server preference',
      'oc.appearance': 'light',
    };
    SharedPreferences.setMockInitialValues({
      ...preserved,
      'oc.activeProfile': 'server',
    });
    prefs = await SharedPreferences.getInstance();
    disk = _ResetPreferences(
      await SharedPreferencesStorePlatform.instance.getAll(),
    );
    SharedPreferencesStorePlatform.instance = disk;
    secure = _ResetSecureStorage()
      ..values.addAll({
        'pw.server': _opaque,
        'oc.codexToken.agent': 'synthetic-reset-agent-value',
        'pw.orphan': 'synthetic-reset-orphan-value',
        'oc.codexToken.orphan': 'synthetic-reset-orphan-agent-value',
        'other.signIn': 'synthetic-unrelated-value',
        'oc.orchestration.token.server': 'synthetic-team-value',
        'pw': 'synthetic-prefix-neighbor',
        'oc.codexToken': 'synthetic-prefix-neighbor',
      });
    store = ProfileStore(prefs: prefs, secure: secure);
  });
  tearDown(KitRedact.clearKnownSecrets);

  void expectPreservedPreferences() {
    for (final entry in preserved.entries) {
      expect(prefs.get(entry.key) == entry.value, isTrue, reason: entry.key);
    }
  }

  Matcher safeResetFailure() => isA<SavedSignInResetException>().having(
    (error) => error.toString().contains(_opaque),
    'does not expose keyring text',
    isFalse,
  );

  test(
    'bootstrap reset removes only owned sign-ins and is idempotent',
    () async {
      expect(store.profiles, isEmpty, reason: 'load is not a prerequisite');
      await store.resetSavedSignIns();
      expect(store.activeId, isNull);
      expect(
        secure.values.keys,
        unorderedEquals([
          'other.signIn',
          'oc.orchestration.token.server',
          'pw',
          'oc.codexToken',
        ]),
      );
      expectPreservedPreferences();
      expect(KitRedact.text(_opaque).contains(_opaque), isFalse);
      await store.resetSavedSignIns();
      expect(secure.deleted, hasLength(4));
      expectPreservedPreferences();
    },
  );

  test('reset does not parse or erase unreadable profile metadata', () async {
    await prefs.setInt('oc.profiles', 7);
    await store.resetSavedSignIns();
    expect(prefs.get('oc.profiles'), 7);
    expect(store.activeId, isNull);
    expect(
      secure.values.keys.any(
        (key) => key.startsWith('pw.') || key.startsWith('oc.codexToken.'),
      ),
      isFalse,
    );
  });

  test('cached profile references lose secrets and retain metadata', () async {
    await store.load();
    final server = store.profiles.first;
    final agent = store.profiles.last;
    var notifications = 0;
    store.changes.addListener(() => notifications++);
    await store.resetSavedSignIns();
    expect(store.profiles.first, same(server));
    expect(server.password.isEmpty && agent.codexToken.isEmpty, isTrue);
    expect(server.requiresPasswordReentry, isTrue);
    expect(agent.requiresCodexTokenReentry, isTrue);
    expect(server.name, 'Saved server');
    expect(notifications, 1);
    expect(KitRedact.text(_opaque).contains(_opaque), isFalse);
    expectPreservedPreferences();
  });

  test('failed enumeration never broadens reset or reports success', () async {
    await store.load();
    secure.failEnumeration = 1;
    await expectLater(store.resetSavedSignIns(), throwsA(safeResetFailure()));
    expect(secure.deleted, isEmpty);
    expect(secure.values, hasLength(8));
    expect(store.activeId, 'server');
    expect(
      store.profiles.every((p) => p.password.isEmpty && p.codexToken.isEmpty),
      isTrue,
    );
    expectPreservedPreferences();
    secure.failEnumeration = null;
    await store.resetSavedSignIns();
    expect(store.activeId, isNull);
  });

  test('refused active-selection removal retains durable sign-ins', () async {
    disk.refuseActiveRemoval = true;
    await expectLater(store.resetSavedSignIns(), throwsA(safeResetFailure()));
    expect(secure.deleted, isEmpty);
    expect(store.activeId, 'server');
    expectPreservedPreferences();
    disk.refuseActiveRemoval = false;
    await store.resetSavedSignIns();
    expect(store.activeId, isNull);
  });

  test(
    'partial secure deletion clears cached secrets and safely retries',
    () async {
      await store.load();
      secure.failDelete = 'oc.codexToken.agent';
      await expectLater(store.resetSavedSignIns(), throwsA(safeResetFailure()));
      expect(store.activeId, isNull);
      expect(secure.values.containsKey('pw.server'), isFalse);
      expect(secure.values.containsKey('oc.codexToken.agent'), isTrue);
      expect(
        store.profiles.every((p) => p.password.isEmpty && p.codexToken.isEmpty),
        isTrue,
      );
      expectPreservedPreferences();
      secure.failDelete = null;
      await store.resetSavedSignIns();
      expect(
        secure.values.keys.any(
          (key) => key.startsWith('pw.') || key.startsWith('oc.codexToken.'),
        ),
        isFalse,
      );
    },
  );

  test(
    'unconfirmed secure deletion is a failure until verification succeeds',
    () async {
      secure.retainDelete = 'pw.orphan';
      await expectLater(store.resetSavedSignIns(), throwsA(safeResetFailure()));
      expect(secure.values.containsKey('pw.orphan'), isTrue);
      expectPreservedPreferences();
      secure.retainDelete = null;
      await store.resetSavedSignIns();
      expect(secure.values.containsKey('pw.orphan'), isFalse);
    },
  );

  test('failed post-deletion enumeration is not successful reset', () async {
    secure.failEnumeration = 2;
    await expectLater(store.resetSavedSignIns(), throwsA(safeResetFailure()));
    expectPreservedPreferences();
    secure.failEnumeration = null;
    await store.resetSavedSignIns();
    expect(store.activeId, isNull);
  });
  test(
    'sign-in reset owns the BYO envelope while retaining its journal',
    () async {
      secure.values['oc.byoHostSecrets.host-a'] = 'synthetic-byo-envelope';
      await prefs.setString('oc.byoHost.host-a', 'nonsecret-journal');
      await store.resetSavedSignIns();
      expect(secure.values['oc.byoHostSecrets.host-a'], isNull);
      expect(prefs.getString('oc.byoHost.host-a'), 'nonsecret-journal');
      expect(secure.values['other.signIn'], isNotNull);
    },
  );
}
