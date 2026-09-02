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
  const _MemoryStorage();

  @override
  Future<String?> read({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async => null;
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
      await (await _store(const _MemoryStorage())).secureStorageProblem(),
      isNull,
    );

    _onPlatform(TargetPlatform.android);
    expect(
      await (await _store(const _NoKeyringStorage())).secureStorageProblem(),
      isNull,
    );
  });

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
