import 'dart:ui' show Locale;

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/setup/setup_engine.dart';
import 'package:opencode_mobile/builtin/setup/setup_finish.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/domain/server_probe.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _OwnershipLinux extends BuiltinLinux {
  @override
  Future<BuiltinLinuxStatus> status() async => const BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: false,
  );
  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async => const BuiltinLinuxRunResult(exitCode: 0, output: '');
  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async => throw const BuiltinLinuxException(
    'The app cannot safely identify previous work.',
    code: 'runtime_ownership_unknown',
  );
}

class _ReadyLinux extends _OwnershipLinux {
  String? startedProfileId;
  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    startedProfileId = restoreRecipe?.profileId;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'ownership failure keeps its stable recovery code through setup finish',
    () async {
      SharedPreferences.setMockInitialValues({});
      const secure = MethodChannel(
        'plugins.it_nomads.com/flutter_secure_storage',
      );
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(secure, (_) async => null);
      addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
      final starter = BuiltinServerStarter(linux: _OwnershipLinux());
      addTearDown(starter.dispose);
      var connected = false;
      final finisher = BuiltinSetupFinisher(
        store: ProfileStore(prefs: await SharedPreferences.getInstance()),
        starter: starter,
        connect: (_) async {
          connected = true;
          return null;
        },
        isConnectedTo: (_) => false,
        strings: () => lookupAppLocalizations(const Locale('en')),
      );
      expect(
        await finisher.call(
          const SetupFinishRequest(
            host: SetupHostKind.builtin,
            runtime: TermuxRuntime.openCode2,
            openCodeChanged: true,
          ),
        ),
        'runtime_ownership_unknown',
      );
      expect(connected, isFalse);
    },
  );

  test(
    'built-in finisher refuses Termux before accessing profiles or native',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final starter = BuiltinServerStarter(linux: BuiltinLinux());
      addTearDown(starter.dispose);
      final calls = <String>[];
      const secure = MethodChannel(
        'plugins.it_nomads.com/flutter_secure_storage',
      );
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(secure, (call) async {
            calls.add(call.method);
            return null;
          });
      addTearDown(() {
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(secure, null);
      });
      final strings = lookupAppLocalizations(const Locale('en'));
      final finisher = BuiltinSetupFinisher(
        store: ProfileStore(prefs: prefs),
        starter: starter,
        connect: (_) async {
          calls.add('connect');
          return null;
        },
        isConnectedTo: (_) {
          calls.add('isConnected');
          return false;
        },
        strings: () => strings,
      );

      expect(
        await finisher.call(
          const SetupFinishRequest(
            host: SetupHostKind.termux,
            runtime: TermuxRuntime.openCode2,
            openCodeChanged: true,
          ),
        ),
        strings.phoneSetupErrorCannotStart,
      );
      expect(calls, isEmpty);
      expect(prefs.getKeys(), isEmpty);
    },
  );

  test(
    'switching the app\'s server to OpenCode 2 takes the AI Team along',
    () async {
      SharedPreferences.setMockInitialValues({});
      const secure = MethodChannel(
        'plugins.it_nomads.com/flutter_secure_storage',
      );
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(secure, (_) async => null);
      addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      final older = await ensureBuiltinProfile(
        store,
        flavor: ServerFlavor.v2,
        name: 'This phone',
      );
      older.orchestration = BuiltinTeam.config();
      await store.upsert(older);
      final starter = BuiltinServerStarter(linux: _OwnershipLinux());
      addTearDown(starter.dispose);
      final finisher = BuiltinSetupFinisher(
        store: store,
        starter: starter,
        connect: (_) async => null,
        isConnectedTo: (_) => false,
        strings: () => lookupAppLocalizations(const Locale('en')),
      );
      await finisher.call(
        const SetupFinishRequest(
          host: SetupHostKind.builtin,
          runtime: TermuxRuntime.openCode2,
          openCodeChanged: true,
        ),
      );
      final newer = findBuiltinProfile(store, ServerFlavor.v2)!;
      expect(BuiltinTeam.isBuiltinConfig(newer.orchestration), isTrue);
      expect(newer, same(older));
      expect(BuiltinTeam.isBuiltinConfig(older.orchestration), isTrue);
    },
  );
  for (final active in [true, false]) {
    test(
      'legacy source survives an earlier OC2 alias with active=$active',
      () async {
        SharedPreferences.setMockInitialValues({});
        const secure = MethodChannel(
          'plugins.it_nomads.com/flutter_secure_storage',
        );
        final messenger =
            TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
        messenger.setMockMethodCallHandler(secure, (_) async => null);
        addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
        final store = ProfileStore(
          prefs: await SharedPreferences.getInstance(),
        );
        final alias = ServerProfile(
          id: 'earlier-oc2',
          name: 'Earlier OC2',
          baseUrl: BuiltinLinux.serverUrl,
          password: 'alias-secret',
        );
        final original = ServerProfile(
          id: 'original-phone',
          name: 'My phone',
          baseUrl: BuiltinLinux.serverUrl,
          password: 'original-secret',
          requiresOpenCode2Migration: true,
        );
        await store.upsert(alias);
        await store.upsert(original);
        await store.prefs.setString(
          'oc.savedDraft.original-phone',
          'keep this draft',
        );
        if (active) await store.setActiveId(original.id);
        final linux = _ReadyLinux();
        final starter = BuiltinServerStarter(
          linux: linux,
          pollInterval: Duration.zero,
        );
        addTearDown(starter.dispose);
        final originalProbe = serverProbe;
        serverProbe = ({required baseUrl, username, password}) async =>
            const ServerProbeResult.success('2.1.1');
        addTearDown(() => serverProbe = originalProbe);
        ServerProfile? connected;
        final finisher = BuiltinSetupFinisher(
          store: store,
          starter: starter,
          connect: (profile) async {
            connected = profile;
            return null;
          },
          isConnectedTo: (_) => false,
          strings: () => lookupAppLocalizations(const Locale('en')),
        );
        expect(
          await finisher.call(
            const SetupFinishRequest(
              runtime: TermuxRuntime.openCode2,
              openCodeChanged: true,
            ),
          ),
          isNull,
        );
        expect(connected, same(original));
        expect(linux.startedProfileId, original.id);
        expect(original.requiresOpenCode2Migration, isFalse);
        expect(
          original.openCode2HistoryPending,
          isTrue,
          reason:
              'A healthy server does not prove the history import completed',
        );
        expect(original.password, 'original-secret');
        expect(
          store.prefs.getString('oc.savedDraft.original-phone'),
          'keep this draft',
        );
        expect(alias.openCode2HistoryPending, isFalse);
        expect(alias.password, 'alias-secret');
        expect(store.profiles.map((p) => p.id), [
          'earlier-oc2',
          'original-phone',
        ]);
      },
    );
  }
}
