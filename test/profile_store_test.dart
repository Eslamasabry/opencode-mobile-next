import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/connection.dart';
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

/// Keystore that accepts writes and deletes without a platform.
class _MemoryStorage extends FlutterSecureStorage {
  const _MemoryStorage();

  static final values = <String, String>{};

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

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'keeps profile metadata when its secure password is unreadable',
    () async {
      SharedPreferences.setMockInitialValues({
        'oc.profiles':
            '[{"id":"server-1","name":"Workstation",'
            '"baseUrl":"https://server.example:4096","username":"dev"}]',
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
      expect(profiles.single.username, 'dev');
      expect(profiles.single.backend, ServerBackend.openCode);
      expect(profiles.single.codexToken, isEmpty);
      expect(profiles.single.requiresCodexTokenReentry, isFalse);
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

  test('persists an exact location independently for each server', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final store = ProfileStore(prefs: prefs);

    await store.setLocation(
      'server-1',
      directory: '/work/acme',
      workspace: 'workspace-1',
    );
    await store.setLocation('server-2', directory: r'C:\work\mobile');

    final first = ProfileStore(prefs: prefs).locationFor('server-1');
    final second = ProfileStore(prefs: prefs).locationFor('server-2');
    expect(first?.directory, '/work/acme');
    expect(first?.workspace, 'workspace-1');
    expect(second?.directory, r'C:\work\mobile');
    expect(second?.workspace, isNull);

    await store.clearLocation('server-1');
    expect(store.locationFor('server-1'), isNull);
    expect(store.locationFor('server-2')?.directory, r'C:\work\mobile');
  });

  test('ignores malformed and empty saved locations', () async {
    SharedPreferences.setMockInitialValues({
      'oc.location.broken': '{not-json',
      'oc.location.empty': '{"directory":"","workspace":null}',
    });
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());

    expect(store.locationFor('broken'), isNull);
    expect(store.locationFor('empty'), isNull);
  });

  test('persists app-wide transcript display preferences', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final store = ProfileStore(prefs: prefs);

    expect(store.transcriptReasoningExpanded, isFalse);
    expect(store.transcriptTimestampsVisible, isFalse);

    await store.setTranscriptReasoningExpanded(true);
    await store.setTranscriptTimestampsVisible(true);

    final restored = ProfileStore(prefs: prefs);
    expect(restored.transcriptReasoningExpanded, isTrue);
    expect(restored.transcriptTimestampsVisible, isTrue);
    final restoredController = ConnectionController(restored);
    expect(restoredController.transcriptReasoningExpanded, isTrue);
    expect(restoredController.transcriptTimestampsVisible, isTrue);
    restoredController.dispose();

    await restored.setTranscriptReasoningExpanded(false);
    await restored.setTranscriptTimestampsVisible(false);
    expect(store.transcriptReasoningExpanded, isFalse);
    expect(store.transcriptTimestampsVisible, isFalse);
  });

  test(
    'persists app-wide appearance with a migration-safe dark default',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = ProfileStore(prefs: prefs);

      expect(store.appearance, AppAppearance.dark);

      await store.setAppearance(AppAppearance.system);
      expect(ProfileStore(prefs: prefs).appearance, AppAppearance.system);

      await store.setAppearance(AppAppearance.light);
      final controller = ConnectionController(ProfileStore(prefs: prefs));
      expect(controller.appearance.value, AppAppearance.light);
      controller.dispose();

      await prefs.setString('oc.appearance', 'unknown-old-value');
      expect(ProfileStore(prefs: prefs).appearance, AppAppearance.dark);
    },
  );

  test(
    'normalizes and validates Codex origins without changing HTTP rules',
    () {
      expect(normalizeCodexServerUrl('localhost:4141'), 'ws://localhost:4141');
      expect(
        normalizeCodexServerUrl('codex.example:4141'),
        'wss://codex.example:4141',
      );
      expect(normalizeCodexServerUrl('[::1]:4141'), 'ws://[::1]:4141');
      expect(validateCodexServerUrl('ws://localhost:4141'), isNull);
      expect(validateCodexServerUrl('wss://codex.example:4141'), isNull);
      expect(validateCodexServerUrl('ws://codex.example:4141'), isNotNull);
      final credentialError = validateCodexServerUrl(
        'wss://user:secret@codex.example',
      );
      expect(credentialError, isNotNull);
      expect(credentialError, isNot(contains('secret')));
      final queryError = validateCodexServerUrl(
        'wss://codex.example/?token=secret',
      );
      expect(queryError, isNotNull);
      expect(queryError, isNot(contains('secret')));
      expect(validateCodexServerUrl('wss://codex.example/project'), isNotNull);
    },
  );

  test('persists the AI Team plugin config with the profile', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final store = ProfileStore(prefs: prefs, secure: const _MemoryStorage());
    final config = OrchestrationConfig(
      provider: OrchestrationProvider.gascity,
      url: 'http://100.64.0.9:8080',
      city: 'bright-lights',
      hostMode: OrchestrationHostMode.phone,
      enabledAt: DateTime.utc(2026, 9, 11, 9),
    );
    // TEAM-206: the chosen kind of computer travels with the config.
    const laptop = OrchestrationConfig(
      provider: OrchestrationProvider.gascity,
      url: 'http://100.64.0.10:8372',
      hostKind: OrchestrationHostKind.laptop,
    );
    await store.upsert(
      ServerProfile(
        id: 'team',
        name: 'Team host',
        baseUrl: 'https://server.example:4096',
        orchestration: config,
      ),
    );
    await store.upsert(
      ServerProfile(
        id: 'laptop',
        name: 'Laptop',
        baseUrl: 'https://l:4096',
        orchestration: laptop,
      ),
    );
    await store.upsert(
      ServerProfile(id: 'plain', name: 'Plain', baseUrl: 'https://p:4096'),
    );

    final reloaded = ProfileStore(prefs: prefs, secure: const _MemoryStorage());
    final profiles = await reloaded.load();
    expect(profiles.firstWhere((p) => p.id == 'team').orchestration, config);
    expect(
      profiles.firstWhere((p) => p.id == 'team').orchestration!.hostKind,
      OrchestrationHostKind.phone,
    );
    expect(profiles.firstWhere((p) => p.id == 'laptop').orchestration, laptop);
    expect(
      profiles.firstWhere((p) => p.id == 'laptop').orchestration!.hostKind,
      OrchestrationHostKind.laptop,
    );
    expect(profiles.firstWhere((p) => p.id == 'plain').orchestration, isNull);

    // Turning the plugin off drops the field from the stored JSON (the
    // laptop profile keeps its own).
    final team = profiles.firstWhere((p) => p.id == 'team')
      ..orchestration = null;
    await reloaded.upsert(team);
    final stored = prefs.getString('oc.profiles')!;
    expect('orchestration'.allMatches(stored).length, 1);
    expect(stored, isNot(contains('"hostMode":"phone"')));
    expect(stored, contains('"hostKind":"laptop"'));
  });

  test('validates Codex directory and token bounds without echoing input', () {
    expect(validateCodexProjectDirectory('/work/acme'), isNull);
    expect(validateCodexProjectDirectory(r'C:\work\acme'), isNull);
    expect(validateCodexProjectDirectory('relative/project'), isNotNull);
    expect(validateCodexProjectDirectory('/work\nacme'), isNotNull);
    expect(validateCodexProjectDirectory('/work\u0085acme'), isNotNull);
    expect(validateCodexConnectionToken('token_123'), isNull);
    expect(validateCodexConnectionToken('token with space'), isNotNull);
    expect(validateCodexConnectionToken('token\nsecret'), isNotNull);
    expect(validateCodexConnectionToken('token\u0085secret'), isNotNull);
    final secret = 'x' * 32;
    expect(validateCodexConnectionToken(secret), isNull);
    expect(validateCodexConnectionToken('$secret '), isNot(contains(secret)));
  });
  test(
    'profile deletion sweeps BYO vault and metadata only for that profile',
    () async {
      SharedPreferences.setMockInitialValues({
        'oc.byoHost.phone-a': 'record-a',
        'oc.byoHost.phone-b': 'record-b',
      });
      final prefs = await SharedPreferences.getInstance();
      _MemoryStorage.values['oc.byoHostSecrets.phone-a'] = 'private-a';
      _MemoryStorage.values['oc.byoHostSecrets.phone-b'] = 'private-b';
      final store = ProfileStore(prefs: prefs, secure: const _MemoryStorage());
      await store.remove('phone-a');
      expect(prefs.getString('oc.byoHost.phone-a'), isNull);
      expect(_MemoryStorage.values['oc.byoHostSecrets.phone-a'], isNull);
      expect(prefs.getString('oc.byoHost.phone-b'), 'record-b');
      expect(_MemoryStorage.values['oc.byoHostSecrets.phone-b'], 'private-b');
    },
  );
  test(
    'a private transport lease cannot persist its address or credential',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      _MemoryStorage.values.clear();
      final store = ProfileStore(prefs: prefs, secure: const _MemoryStorage());
      await expectLater(
        store.upsert(
          ServerProfile(
            id: 'lease',
            name: 'Host',
            baseUrl: 'http://127.0.0.1:19000',
            transientTransport: true,
            password: 'must-stay-in-byo-vault',
          ),
        ),
        throwsA(isA<Exception>()),
      );
      expect(store.profiles, isEmpty);
      expect(prefs.getString('oc.profiles'), isNull);
      expect(_MemoryStorage.values, isEmpty);
    },
  );
}
