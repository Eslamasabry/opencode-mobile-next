import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/development_service.dart';
import 'package:opencode_mobile/domain/managed_shell.dart';
import 'package:opencode_mobile/state/development_service_store.dart';
import 'package:opencode_mobile/state/development_services.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/development_service_fakes.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late DevelopmentServiceStore store;
  late ServiceRepository gateway;
  late DevelopmentServices model;
  var current = true;
  var writable = true;
  var supported = true;
  Future<ManagedShellGateway?> Function()? resolver;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    current = writable = supported = true;
    resolver = null;
    store = DevelopmentServiceStore(
      preferences: prefs,
      profileID: 'p',
      canWrite: () => writable,
    );
    gateway = ServiceRepository();
    model = DevelopmentServices(
      store: store,
      directory: sampleService.directory,
      workspace: null,
      resolveGateway: () => resolver?.call() ?? Future.value(gateway),
      isCurrent: () => current,
      supported: () => supported,
    );
  });
  tearDown(() => model.dispose());

  test('registration survives restart and never starts a command', () async {
    await model.register(sampleService);
    expect(gateway.starts, 0);
    expect(store.load().single.command, 'npm run dev');
    expect(
      model.status(model.services.single),
      DevelopmentServiceStatus.notStarted,
    );
  });

  test(
    'start logs stop restart control only receipts from this feature',
    () async {
      await model.register(sampleService);
      await model.start('vite');
      expect(
        model.status(model.services.single),
        DevelopmentServiceStatus.running,
      );
      gateway.shells['sh_foreign'] = ManagedShell(
        id: 'sh_foreign',
        command: 'npm run dev',
        status: ManagedShellStatus.running,
        startedAt: DateTime.now(),
      );
      await model.readLogs('vite');
      expect(model.logs['vite'], contains('VITE ready'));
      await model.restart('vite');
      expect(gateway.stops, ['sh_1']);
      expect(gateway.starts, 2);
      expect(gateway.shells.containsKey('sh_foreign'), isTrue);
      await model.stop('vite');
      expect(
        model.status(model.services.single),
        DevelopmentServiceStatus.stopped,
      );
      expect(model.logs, isEmpty);
    },
  );

  test(
    'lost create response stays unknown and recovers only its ownership nonce',
    () async {
      await model.register(sampleService);
      gateway.loseCreateResponse = true;
      await model.start('vite');
      expect(
        model.status(model.services.single),
        DevelopmentServiceStatus.unknown,
      );
      expect(model.canStart(model.services.single), isFalse);
      expect(store.load().single.run!.shellID, isNull);
      await model.refresh();
      expect(model.services.single.run!.shellID, 'sh_1');
      expect(
        model.status(model.services.single),
        DevelopmentServiceStatus.running,
      );
      expect(gateway.starts, 1);
    },
  );

  test(
    'same ID with foreign ownership cannot authorize stop or logs',
    () async {
      await model.register(sampleService);
      await model.start('vite');
      final original = gateway.shells['sh_1']!;
      gateway.shells['sh_1'] = ManagedShell(
        id: original.id,
        command: original.command,
        directory: original.directory,
        ownerToken: 'foreign',
        status: ManagedShellStatus.running,
        startedAt: original.startedAt,
      );
      await model.stop('vite');
      expect(gateway.stops, isEmpty);
      expect(
        model.status(model.services.single),
        DevelopmentServiceStatus.unknown,
      );
      await model.readLogs('vite');
      expect(gateway.cursors, isEmpty);
    },
  );

  test('failed refresh does not leave running status claimed', () async {
    await model.register(sampleService);
    await model.start('vite');
    gateway.failReads = true;
    await model.refresh();
    expect(
      model.status(model.services.single),
      DevelopmentServiceStatus.unknown,
    );
    expect(model.error, isNotNull);
  });

  test(
    'scope change during resolution prevents POST and pending receipt',
    () async {
      await model.register(sampleService);
      resolver = () async {
        current = false;
        return gateway;
      };
      await model.start('vite');
      expect(gateway.starts, 0);
      expect(store.load().single.run, isNull);
    },
  );

  test('scope change during stop revalidation prevents DELETE', () async {
    await model.register(sampleService);
    await model.start('vite');
    gateway.infoGate = Completer<void>();
    final stopping = model.stop('vite');
    await Future<void>.delayed(Duration.zero);
    current = false;
    model.invalidateRuntime();
    gateway.infoGate!.complete();
    await stopping;
    expect(gateway.stops, isEmpty);
  });

  test(
    'a late create response is saved for recovery after route disposal',
    () async {
      await model.register(sampleService);
      gateway.createGate = Completer<void>();
      final starting = model.start('vite');
      await Future<void>.delayed(Duration.zero);
      current = false;
      model.invalidateRuntime();
      gateway.createGate!.complete();
      await starting;
      expect(store.load().single.run!.shellID, 'sh_1');
    },
  );

  test(
    'profile deletion blocks late receipt without recreating preferences',
    () async {
      await model.register(sampleService);
      gateway.createGate = Completer<void>();
      final starting = model.start('vite');
      await Future<void>.delayed(Duration.zero);
      writable = false;
      await prefs.remove(store.key);
      gateway.createGate!.complete();
      await starting;
      expect(prefs.containsKey(store.key), isFalse);
    },
  );

  test(
    'log reading requests only a bounded byte tail and strips control sequences',
    () async {
      await model.register(sampleService);
      await model.start('vite');
      gateway.output = '${'x' * 90000}\u001b[31mTAIL\u001b[0m';
      await model.readLogs('vite');
      expect(gateway.cursors.first, 0);
      expect(gateway.cursors.last, greaterThan(24000));
      expect(model.logs['vite']!.length, lessThanOrEqualTo(65536));
      expect(model.logs['vite'], endsWith('TAIL'));
    },
  );

  test(
    'unsupported transport saves configuration without shell calls',
    () async {
      supported = false;
      await model.register(sampleService);
      await model.start('vite');
      expect(gateway.starts, 0);
      expect(model.canStart(model.services.single), isFalse);
      await model.remove('vite');
      expect(store.load(), isEmpty);
    },
  );

  test(
    'unknown runs need explicit local forgetting; removal never kills',
    () async {
      await model.register(sampleService);
      gateway.loseCreateResponse = true;
      await model.start('vite');
      await model.start('vite');
      expect(gateway.starts, 1);
      await model.forgetRun('vite');
      expect(model.canStart(model.services.single), isTrue);
      await model.remove('vite');
      expect(gateway.stops, isEmpty);
    },
  );

  test(
    'standard profile deletion sweep includes services but preserves peers',
    () async {
      const channel = MethodChannel(
        'plugins.it_nomads.com/flutter_secure_storage',
      );
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (_) async => null);
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null),
      );
      await store.save(sampleService);
      await prefs.setString('oc.developmentServices.peer', 'retained');
      final profiles = ProfileStore(prefs: prefs);
      expect(profiles.profileScopedPreferenceKeys('p'), contains(store.key));
      expect(await profiles.removeScopedPreferences('p'), isEmpty);
      expect(prefs.containsKey(store.key), isFalse);
      expect(prefs.getString('oc.developmentServices.peer'), 'retained');
    },
  );

  test('corrupt saved configuration is not silently overwritten', () async {
    await prefs.setString(store.key, '{broken');
    model.reload();
    await model.register(sampleService);
    expect(model.readable, isFalse);
    expect(prefs.getString(store.key), '{broken');
  });
}
