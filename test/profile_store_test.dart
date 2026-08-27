import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _ThrowingSecureStorage extends FlutterSecureStorage {
  const _ThrowingSecureStorage();

  @override
  Future<String?> read({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) => throw StateError('secure storage is unavailable');
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'keeps profile metadata when its secure password is unreadable',
    () async {
      SharedPreferences.setMockInitialValues({
        'oc.profiles':
            '[{"id":"server-1","name":"Workstation",'
            '"baseUrl":"https://server.example:4096","username":"eslam"}]',
        'oc.activeProfile': 'server-1',
      });
      final prefs = await SharedPreferences.getInstance();
      final store = ProfileStore(
        prefs: prefs,
        secure: const _ThrowingSecureStorage(),
      );

      final profiles = await store.load();

      expect(profiles, hasLength(1));
      expect(profiles.single.id, 'server-1');
      expect(profiles.single.name, 'Workstation');
      expect(profiles.single.baseUrl, 'https://server.example:4096');
      expect(profiles.single.username, 'eslam');
      expect(profiles.single.password, isEmpty);
      expect(profiles.single.requiresPasswordReentry, isTrue);
      expect(store.active, same(profiles.single));
    },
  );

  test('persists and clears a thinking variant per profile', () async {
    SharedPreferences.setMockInitialValues({});
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());

    await store.setVariant('server-1', 'high');
    expect(store.variantFor('server-1'), 'high');

    await store.setVariant('server-1', '');
    expect(store.variantFor('server-1'), isEmpty);
  });
}
