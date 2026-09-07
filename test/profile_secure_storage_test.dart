import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show PlatformException;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// What flutter_secure_storage throws on a Linux desktop with no Secret
/// Service (no GNOME Keyring or KWallet, or no desktop session).
PlatformException _libsecretFailure() => PlatformException(
  code: 'Libsecret error',
  message: 'Failed to unlock the keyring',
);

class _NoKeyringStorage extends FlutterSecureStorage {
  const _NoKeyringStorage();

  @override
  Future<String?> read({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) => throw _libsecretFailure();

  @override
  Future<void> write({
    required String key,
    required String? value,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) => throw _libsecretFailure();

  @override
  Future<void> delete({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) => throw _libsecretFailure();
}

class _MemoryStorage extends FlutterSecureStorage {
  final values = <String, String>{};

  _MemoryStorage();

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
  Future<void> write({
    required String key,
    required String? value,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    if (value == null) {
      values.remove(key);
    } else {
      values[key] = value;
    }
  }

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
    values.remove(key);
  }
}

class _DeleteFailingStorage extends _MemoryStorage {
  @override
  Future<void> delete({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) => throw _libsecretFailure();
}

Future<ProfileStore> _store(FlutterSecureStorage secure) async {
  SharedPreferences.setMockInitialValues({});
  final store = ProfileStore(
    prefs: await SharedPreferences.getInstance(),
    secure: secure,
  );
  await store.load();
  return store;
}

ServerProfile _profile() => ServerProfile(
  id: 'workstation',
  name: 'Workstation',
  baseUrl: 'https://server.example:4096',
  username: 'dev',
  password: 'hunter2',
);

void _onPlatform(TargetPlatform platform) {
  debugPlatformCapabilities = PlatformCapabilities(platform: platform);
  addTearDown(() => debugPlatformCapabilities = null);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'a Linux keyring failure surfaces as SecureStorageUnavailable',
    () async {
      _onPlatform(TargetPlatform.linux);
      final store = await _store(const _NoKeyringStorage());

      await expectLater(
        store.upsert(_profile()),
        throwsA(
          isA<SecureStorageUnavailable>()
              .having(
                (e) => e.message,
                'message',
                SecureStorageUnavailable.linuxMessage,
              )
              .having((e) => e.cause, 'cause', isA<PlatformException>()),
        ),
      );
      // The half-written profile was rolled back with the secret.
      expect(store.profiles, isEmpty);
      expect(store.prefs.getString('oc.profiles'), isNull);
    },
  );

  test('elsewhere the same failure gets the generic device sentence', () async {
    _onPlatform(TargetPlatform.android);
    final store = await _store(const _NoKeyringStorage());

    await expectLater(
      store.upsert(_profile()),
      throwsA(
        isA<SecureStorageUnavailable>().having(
          (e) => e.message,
          'message',
          SecureStorageUnavailable.genericMessage,
        ),
      ),
    );
  });

  test('the keyring probe reports only a Linux desktop without one', () async {
    _onPlatform(TargetPlatform.linux);
    expect(
      await (await _store(const _NoKeyringStorage())).secureStorageProblem(),
      SecureStorageUnavailable.linuxMessage,
    );
    expect(
      await (await _store(_MemoryStorage())).secureStorageProblem(),
      isNull,
    );

    _onPlatform(TargetPlatform.android);
    expect(
      await (await _store(const _NoKeyringStorage())).secureStorageProblem(),
      isNull,
    );
  });

  test('Codex token is isolated from password storage and JSON', () async {
    final secure = _MemoryStorage();
    final store = await _store(secure);
    final profile = ServerProfile(
      id: 'codex-1',
      name: 'Codex',
      baseUrl: 'wss://codex.example:4141',
      backend: ServerBackend.codex,
      password: 'must-not-be-used',
      codexToken: 'codex-secret',
      codexDirectory: '/work/acme',
    );

    await store.upsert(profile);

    expect(secure.values['oc.codexToken.codex-1'], 'codex-secret');
    expect(secure.values.containsKey('pw.codex-1'), isFalse);
    final saved = store.prefs.getString('oc.profiles')!;
    expect(saved, contains('"backend":"codex"'));
    expect(saved, contains('"codexDirectory":"/work/acme"'));
    expect(saved, isNot(contains('codex-secret')));
    expect(saved, isNot(contains('must-not-be-used')));

    final restored = ProfileStore(prefs: store.prefs, secure: secure);
    final loaded = await restored.load();
    expect(loaded.single.codexToken, 'codex-secret');
    expect(loaded.single.password, isEmpty);
    expect(loaded.single.requiresCodexTokenReentry, isFalse);
  });

  test(
    'missing Codex token requests reentry without exposing metadata',
    () async {
      final secure = _MemoryStorage();
      final store = await _store(secure);
      await store.upsert(
        ServerProfile(
          id: 'codex-2',
          name: 'Codex',
          baseUrl: 'wss://codex.example:4141',
          backend: ServerBackend.codex,
          codexDirectory: '/work/acme',
        ),
      );

      final restored = ProfileStore(prefs: store.prefs, secure: secure);
      final loaded = await restored.load();
      expect(loaded.single.codexToken, isEmpty);
      expect(loaded.single.requiresCodexTokenReentry, isTrue);
    },
  );

  test(
    'Codex save rolls metadata back when its token cannot be stored',
    () async {
      final store = await _store(const _NoKeyringStorage());
      final profile = ServerProfile(
        id: 'codex-3',
        name: 'Codex',
        baseUrl: 'wss://codex.example:4141',
        backend: ServerBackend.codex,
        codexToken: 'codex-secret',
        codexDirectory: '/work/acme',
      );

      await expectLater(
        store.upsert(profile),
        throwsA(isA<SecureStorageUnavailable>()),
      );
      expect(store.profiles, isEmpty);
      expect(store.prefs.getString('oc.profiles'), isNull);
    },
  );

  test('Codex deletion removes only its token', () async {
    final secure = _MemoryStorage();
    final store = await _store(secure);
    await store.upsert(
      ServerProfile(
        id: 'codex-4',
        name: 'Codex',
        baseUrl: 'wss://codex.example:4141',
        backend: ServerBackend.codex,
        codexToken: 'codex-secret',
        codexDirectory: '/work/acme',
      ),
    );
    await store.remove('codex-4');
    expect(secure.values.containsKey('oc.codexToken.codex-4'), isFalse);
    expect(secure.values.containsKey('pw.codex-4'), isFalse);
    expect(store.profiles, isEmpty);
  });

  test(
    'Codex deletion rolls metadata back when its token cannot be deleted',
    () async {
      final secure = _DeleteFailingStorage();
      final store = await _store(secure);
      await store.upsert(
        ServerProfile(
          id: 'codex-5',
          name: 'Codex',
          baseUrl: 'wss://codex.example:4141',
          backend: ServerBackend.codex,
          codexToken: 'codex-secret',
          codexDirectory: '/work/acme',
        ),
      );

      await expectLater(
        store.remove('codex-5'),
        throwsA(isA<SecureStorageUnavailable>()),
      );
      expect(store.profiles.single.id, 'codex-5');
      expect(store.prefs.getString('oc.profiles'), contains('"codex"'));
      expect(secure.values['oc.codexToken.codex-5'], 'codex-secret');
    },
  );

  testWidgets(
    'the Servers editor warns about the missing keyring before and after save',
    (tester) async {
      _onPlatform(TargetPlatform.linux);
      final store = await _store(const _NoKeyringStorage());
      final connection = ConnectionController(store);
      addTearDown(connection.dispose);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            bootstrapProvider.overrideWithValue(AppBootstrap(store)),
            connProvider.overrideWithValue(connection),
          ],
          child: const MaterialApp(home: ServersScreen()),
        ),
      );
      await tester.pumpAndSettle();
      final connect = find.byKey(const ValueKey('welcome-connect-card'));
      await tester.ensureVisible(connect);
      await tester.pumpAndSettle();
      await tester.tap(connect);
      await tester.pumpAndSettle();

      // The probe ran when the editor opened: the notice sits above the form.
      final notice = find.byKey(const ValueKey('server-secure-storage-notice'));
      expect(notice, findsOneWidget);
      expect(
        find.descendant(
          of: notice,
          matching: find.textContaining('no keyring is available'),
        ),
        findsOneWidget,
      );

      await tester.enterText(
        find.byKey(const ValueKey('server-url-field')),
        'https://server.example:4096',
      );
      // Saving touches the keyring even for an empty password (the stale
      // secret is deleted), so the failure surfaces without typing one.
      await tester.tap(find.byKey(const ValueKey('save-server-profile')));
      await tester.pumpAndSettle();

      // The save failure names the keyring, never "OpenCode is unreachable".
      final failure = find.byKey(const ValueKey('server-save-failure'));
      expect(failure, findsOneWidget);
      expect(
        find.descendant(
          of: failure,
          matching: find.textContaining('Install GNOME Keyring or KWallet'),
        ),
        findsOneWidget,
      );
      expect(find.textContaining('unreachable'), findsNothing);
      expect(store.profiles, isEmpty);
    },
  );
}
