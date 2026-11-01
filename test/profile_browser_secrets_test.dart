import 'package:flutter/foundation.dart' show TargetPlatform;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Behaves like flutter_secure_storage_web: WebCrypto is only reachable in
/// a secure context (an [UnsupportedError] otherwise), a stored entry that
/// no longer decrypts reads as null while [containsKey] still sees it, and
/// a refused localStorage write arrives as the browser's own exception
/// object rather than a Dart [Error].
class _BrowserLikeStorage extends FlutterSecureStorage {
  _BrowserLikeStorage({this.secureContext = true});
  bool secureContext;
  bool undecryptable = false;
  Object? writeFailure;
  final values = <String, String>{};

  @override
  Future<String?> read({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    if (!values.containsKey(key)) return null;
    if (!secureContext) {
      throw UnsupportedError(
        'FlutterSecureStorageWeb only works in secure contexts',
      );
    }
    return undecryptable ? null : values[key];
  }

  @override
  Future<bool> containsKey({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async => values.containsKey(key);

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
    final failure = writeFailure;
    if (failure != null) throw failure;
    if (!secureContext) {
      throw UnsupportedError(
        'FlutterSecureStorageWeb only works in secure contexts',
      );
    }
    values[key] = value!;
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

ServerProfile _profile({String password = 'hunter2'}) => ServerProfile(
  id: 'workstation',
  name: 'Workstation',
  baseUrl: 'https://server.example:4096',
  username: 'dev',
  password: password,
);

/// A browser build on a Linux host: [PlatformCapabilities.isWeb] must win
/// over the platform, so the Linux keyring probe is never consulted.
void _inBrowser() {
  debugPlatformCapabilities = const PlatformCapabilities(
    platform: TargetPlatform.linux,
    isWeb: true,
  );
  addTearDown(() => debugPlatformCapabilities = null);
}

Future<ProfileStore> _store(
  _BrowserLikeStorage secure, {
  BrowserSecretStorageState state = BrowserSecretStorageState.available,
}) async {
  final store = ProfileStore(
    prefs: await SharedPreferences.getInstance(),
    secure: secure,
    browserSecretStorage: () => state,
  );
  await store.load();
  return store;
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('the probe names the page problem before a password is typed', () async {
    _inBrowser();
    final secure = _BrowserLikeStorage();
    expect(await (await _store(secure)).secureStorageProblem(), isNull);
    expect(
      await (await _store(
        secure,
        state: BrowserSecretStorageState.insecureContext,
      )).secureStorageProblem(),
      SecureStorageUnavailable.browserInsecureContextMessage,
    );
    expect(
      await (await _store(
        secure,
        state: BrowserSecretStorageState.storageBlocked,
      )).secureStorageProblem(),
      SecureStorageUnavailable.browserStorageBlockedMessage,
    );
    expect(
      SecureStorageUnavailable.browserInsecureContextMessage,
      contains('https://'),
    );
  });

  test(
    'an insecure page refuses the password and keeps nothing, in the clear or otherwise',
    () async {
      _inBrowser();
      final secure = _BrowserLikeStorage(secureContext: false);
      final store = await _store(
        secure,
        state: BrowserSecretStorageState.insecureContext,
      );
      await expectLater(
        store.upsert(_profile()),
        throwsA(
          isA<SecureStorageUnavailable>().having(
            (e) => e.message,
            'message',
            SecureStorageUnavailable.browserInsecureContextMessage,
          ),
        ),
      );
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('oc.profiles'), isNull);
      expect(store.profiles, isEmpty);
      expect(secure.values, isEmpty);
      for (final key in prefs.getKeys()) {
        expect(prefs.get(key).toString(), isNot(contains('hunter2')));
      }
    },
  );

  test(
    'a profile without a password still saves on an insecure page',
    () async {
      _inBrowser();
      final secure = _BrowserLikeStorage(secureContext: false);
      final store = await _store(
        secure,
        state: BrowserSecretStorageState.insecureContext,
      );
      await store.upsert(_profile(password: ''));
      expect(store.profiles.single.id, 'workstation');
      expect(secure.values, isEmpty);
      final reloaded = await _store(secure);
      expect(reloaded.profiles.single.requiresPasswordReentry, isFalse);
    },
  );

  test(
    'ciphertext that no longer decrypts asks for the password again',
    () async {
      _inBrowser();
      final secure = _BrowserLikeStorage();
      await (await _store(secure)).upsert(_profile());
      expect(secure.values, hasLength(1));
      final intact = await _store(secure);
      expect(intact.profiles.single.password, 'hunter2');
      expect(intact.profiles.single.requiresPasswordReentry, isFalse);

      secure.undecryptable = true;
      final lost = await _store(secure);
      expect(lost.profiles.single.password, isEmpty);
      expect(lost.profiles.single.requiresPasswordReentry, isTrue);

      // Re-entering it stores a fresh entry and clears the flag.
      secure.undecryptable = false;
      final profile = lost.profiles.single..password = 'hunter3';
      await lost.upsert(profile);
      expect(profile.requiresPasswordReentry, isFalse);
      expect((await _store(secure)).profiles.single.password, 'hunter3');
    },
  );

  test(
    'a browser exception object from the write is reported, not crashed on',
    () async {
      _inBrowser();
      final secure = _BrowserLikeStorage()..writeFailure = 'SecurityError';
      final store = await _store(secure);
      await expectLater(
        store.upsert(_profile()),
        throwsA(
          isA<SecureStorageUnavailable>()
              .having(
                (e) => e.message,
                'message',
                SecureStorageUnavailable.browserMessage,
              )
              .having((e) => e.cause, 'cause', 'SecurityError'),
        ),
      );
      expect(store.profiles, isEmpty);
      expect(
        (await SharedPreferences.getInstance()).getString('oc.profiles'),
        isNull,
      );
    },
  );

  test('a programming error is not disguised as a storage problem', () async {
    _inBrowser();
    final secure = _BrowserLikeStorage()
      ..writeFailure = StateError('synthetic bug');
    final store = await _store(secure);
    await expectLater(store.upsert(_profile()), throwsStateError);
    expect(store.profiles, isEmpty);
  });

  test('removing a profile deletes its browser secret with the row', () async {
    _inBrowser();
    final secure = _BrowserLikeStorage();
    final store = await _store(secure);
    await store.upsert(_profile());
    await store.setActiveId('workstation');
    await store.remove('workstation');
    expect(secure.values, isEmpty);
    expect(store.profiles, isEmpty);
    expect(store.activeId, isNull);
    expect((await _store(secure)).profiles, isEmpty);
  });

  test('a native build never consults the browser probe', () async {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    addTearDown(() => debugPlatformCapabilities = null);
    final store = ProfileStore(
      prefs: await SharedPreferences.getInstance(),
      secure: _BrowserLikeStorage(),
      browserSecretStorage: () => throw StateError('probe consulted'),
    );
    await store.load();
    expect(await store.secureStorageProblem(), isNull);
    await store.upsert(_profile());
    expect(store.profiles.single.password, 'hunter2');
  });
}
