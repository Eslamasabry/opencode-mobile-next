// Runs only in a browser: `flutter test --platform chrome test/web`.
// The web test entrypoint registers no plugins, so the real
// flutter_secure_storage web implementation is installed here by hand.
@TestOn('browser')
library;

import 'package:flutter_secure_storage_platform_interface/flutter_secure_storage_platform_interface.dart';
import 'package:flutter_secure_storage_web/flutter_secure_storage_web.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/browser_secret_storage_web.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:web/web.dart' as web;

/// The plugin's localStorage names: `<publicKey>` holds the key material,
/// `<publicKey>.<key>` each ciphertext. Only these are touched; nothing
/// else on the origin is cleared.
const _keyMaterial = 'FlutterSecureStorage';
const _passwordEntry = 'FlutterSecureStorage.pw.workstation';

ServerProfile _profile() => ServerProfile(
  id: 'workstation',
  name: 'Workstation',
  baseUrl: 'https://server.example:4096',
  username: 'dev',
  password: 'hunter2',
);

Future<ProfileStore> _store() async {
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  return store;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(
    () => FlutterSecureStoragePlatform.instance = FlutterSecureStorageWeb(),
  );
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    web.window.localStorage.removeItem(_passwordEntry);
    web.window.localStorage.removeItem(_keyMaterial);
  });
  tearDown(() {
    web.window.localStorage.removeItem(_passwordEntry);
    web.window.localStorage.removeItem(_keyMaterial);
  });

  test(
    'the test page is a secure context with writable site storage',
    () async {
      expect(platformCapabilities.isWeb, isTrue);
      expect(browserSecretStorageState(), BrowserSecretStorageState.available);
      expect(await (await _store()).secureStorageProblem(), isNull);
      expect(
        web.window.localStorage.getItem(browserSecretStorageProbeKey),
        isNull,
      );
    },
  );

  test(
    'a password survives reopening its store encrypted and leaves with its profile',
    () async {
      final store = await _store();
      await store.upsert(_profile());
      final stored = web.window.localStorage.getItem(_passwordEntry);
      expect(stored, isNotNull);
      expect(stored, isNot(contains('hunter2')));
      for (var i = 0; i < web.window.localStorage.length; i++) {
        final key = web.window.localStorage.key(i)!;
        expect(
          web.window.localStorage.getItem(key),
          isNot(contains('hunter2')),
          reason: key,
        );
      }

      final reloaded = await _store();
      expect(reloaded.profiles.single.password, 'hunter2');
      expect(reloaded.profiles.single.requiresPasswordReentry, isFalse);

      await reloaded.remove('workstation');
      expect(web.window.localStorage.getItem(_passwordEntry), isNull);
      expect((await _store()).profiles, isEmpty);
    },
  );

  test(
    'lost key material asks for the password again instead of connecting without one',
    () async {
      await (await _store()).upsert(_profile());
      web.window.localStorage.removeItem(_keyMaterial);
      final lost = await _store();
      expect(lost.profiles.single.password, isEmpty);
      expect(lost.profiles.single.requiresPasswordReentry, isTrue);

      final profile = lost.profiles.single..password = 'hunter3';
      await lost.upsert(profile);
      expect((await _store()).profiles.single.password, 'hunter3');
    },
  );
}
