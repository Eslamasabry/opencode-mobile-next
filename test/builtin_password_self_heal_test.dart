import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/domain/server_probe.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Linux extends BuiltinLinux {
  final scripts = <String>[];
  var running = false;

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: running,
  );

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    scripts.add(script);
    return const BuiltinLinuxRunResult(exitCode: 0, output: '');
  }

  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async => running = true;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final secure = <String, String>{};
  var unreadable = false;

  setUp(() {
    secure.clear();
    unreadable = false;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async {
            final key = (call.arguments as Map)['key'] as String?;
            switch (call.method) {
              case 'read':
                if (unreadable) throw PlatformException(code: 'keystore');
                return secure[key];
              case 'write':
                secure[key!] = (call.arguments as Map)['value'] as String;
                return null;
              case 'delete':
                secure.remove(key);
                return null;
            }
            return null;
          },
        );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('oc/background'),
          (_) async => null,
        );
  });

  tearDown(() => serverProbe = probeServerConnection);

  test(
    'a password the Keystore lost is replaced and the start goes on',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = ProfileStore(prefs: prefs);
      final saved =
          ServerProfile(
              id: 'builtin',
              name: 'This phone',
              baseUrl: BuiltinLinux.serverUrl,
              flavor: ServerFlavor.v2,
            )
            ..username = BuiltinLinux.serverUsername
            ..password = 'old-secret';
      await store.upsert(saved);

      unreadable = true; // restore: the Keystore keys are gone
      await store.load();
      final profile = store.profiles.single;
      expect(profile.password, isEmpty);
      expect(profile.requiresPasswordReentry, isTrue);
      unreadable = false;
      secure.clear();

      final linux = _Linux();
      String? probedWith;
      serverProbe = ({required baseUrl, username, password}) async {
        probedWith = password;
        return linux.running
            ? const ServerProbeResult.success('2.1.1')
            : const ServerProbeResult.failure('refused');
      };
      final starter = BuiltinServerStarter(linux: linux)
        ..saveRenewedPassword = store.upsert;

      final failure = await starter.start(profile);

      expect(failure, isNull);
      expect(profile.password, isNotEmpty);
      expect(profile.requiresPasswordReentry, isFalse);
      expect(probedWith, profile.password);
      expect(secure['pw.builtin'], profile.password);
      expect(linux.scripts.any((s) => s.contains(profile.password)), isTrue);
      starter.dispose();
    },
  );
}
