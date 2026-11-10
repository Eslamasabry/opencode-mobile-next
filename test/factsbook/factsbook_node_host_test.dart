// The node's passphrase lives in Keystore-backed secure storage, not in a file
// next to the vault. A file left by an earlier version is moved and deleted.
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/factsbook_node_host.dart';

class _Linux extends BuiltinLinux {
  final scripts = <String>[];
  final services = <String>[];
  var stagedWhileRunning = <bool>[];
  late File Function() stagedFile;

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
    String? input,
  }) async {
    scripts.add(script);
    stagedWhileRunning.add(stagedFile().existsSync());
    return const BuiltinLinuxRunResult(
      exitCode: 0,
      output: 'fb init=0\nfb removed\n',
    );
  }

  @override
  Future<void> startService(
    String name,
    String script, {
    int? port,
    String? notice,
  }) async => services.add(script);

  @override
  Future<void> stopService(String name) async {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Map<String, String> store;
  late Directory root;
  late _Linux linux;
  late BuiltinLinuxNodeHost host;

  File staged() => File('${root.path}${BuiltinLinuxNodeHost.passphrasePath}');

  setUp(() {
    store = {};
    root = Directory.systemTemp.createTempSync('fb-host');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async {
            final args = (call.arguments as Map).cast<String, Object?>();
            switch (call.method) {
              case 'read':
                return store[args['key']];
              case 'write':
                store[args['key']! as String] = args['value']! as String;
                return null;
              case 'delete':
                store.remove(args['key']);
                return null;
            }
            return null;
          },
        );
    linux = _Linux()..stagedFile = staged;
    host = BuiltinLinuxNodeHost(linux: linux, rootfs: () async => root);
  });

  tearDown(() => root.deleteSync(recursive: true));

  test(
    'a new vault keeps its passphrase in secure storage, not on disk',
    () async {
      await host.createVault();
      expect(store[BuiltinLinuxNodeHost.secureKey], isNotEmpty);
      expect(staged().existsSync(), isFalse);
      // The file existed only while the node's command ran.
      expect(linux.stagedWhileRunning, [true]);
    },
  );

  test('the passphrase made once is the one used again', () async {
    await host.createVault();
    final first = store[BuiltinLinuxNodeHost.secureKey];
    await host.createVault();
    expect(store[BuiltinLinuxNodeHost.secureKey], first);
  });

  test(
    'a file left by an earlier version moves to secure storage and is deleted',
    () async {
      staged().parent.createSync(recursive: true);
      staged().writeAsStringSync('old-passphrase\n');
      await host.migratePassphrase();
      expect(store[BuiltinLinuxNodeHost.secureKey], 'old-passphrase');
      expect(staged().existsSync(), isFalse);
    },
  );

  test(
    'starting hands the node the passphrase and removes the file itself',
    () async {
      store[BuiltinLinuxNodeHost.secureKey] = 'kept';
      await host.startNode();
      final script = linux.services.single;
      expect(script, contains('exec 3<${BuiltinLinuxNodeHost.passphrasePath}'));
      expect(script, contains('rm -f ${BuiltinLinuxNodeHost.passphrasePath}'));
      expect(script, isNot(contains('kept')));
    },
  );

  test('removing the memory deletes the passphrase too', () async {
    store[BuiltinLinuxNodeHost.secureKey] = 'kept';
    await host.removeData();
    expect(store, isEmpty);
  });
}
