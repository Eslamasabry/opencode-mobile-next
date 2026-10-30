import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/termux_host_setup.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/termux/managed_server_recovery.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/termux_channel_fixture.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late TermuxChannelFixture termux;
  late MemoryProfileStore store;
  late FakePhoneConnection connection;
  late TermuxHostSetup setup;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    store = MemoryProfileStore(prefs: await SharedPreferences.getInstance());
    connection = FakePhoneConnection(store);
    termux = TermuxChannelFixture()..install();
    setup = TermuxHostSetup(
      store: store,
      connection: connection,
      copy: () => lookupAppLocalizations(const Locale('en')),
      connectWhenReady: false,
    );
    addTearDown(() {
      setup.dispose();
      connection.dispose();
    });
  });

  test(
    'fresh Termux install selects pinned OC2 and stores an OC2 profile',
    () async {
      await setup.begin(TermuxHostJob.install);
      expect(termux.launchCalls, 1);
      expect(termux.runtime, TermuxRuntime.openCode2);
      expect(termux.lastLaunchScript, contains("setup '4096' '2.1.1'"));
      expect(store.saved.single.flavor, ServerFlavor.v2);
      expect(setup.runtime, TermuxRuntime.openCode2);
    },
  );

  test('legacy installation automatically uses the safe OC2 move', () async {
    termux.inventoryOutput = 'ubuntu=installed\nversion=1.19.0\n';
    store.saved.add(
      ServerProfile(
        id: 'legacy',
        name: 'This phone',
        baseUrl: TermuxBridge.managedServerUrl,
        requiresOpenCode2Migration: true,
        password: 'synthetic-test-secret',
      ),
    );
    await setup.begin(TermuxHostJob.install);
    expect(termux.launchCalls, 0);
    expect(termux.switchCalls, 1);
    expect(termux.runtime, TermuxRuntime.openCode2);
    expect(store.saved, hasLength(1));
    expect(store.saved.single.id, 'legacy');
    expect(store.saved.single.openCode2HistoryPending, isTrue);
    expect(store.saved.single.requiresOpenCode2Migration, isFalse);
  });

  test(
    'legacy selection without recoverable credentials never installs OC1',
    () async {
      termux.inventoryOutput = 'ubuntu=absent\nversion=\nruntime=opencode1\n';
      await setup.begin(TermuxHostJob.install);
      expect(termux.launchCalls, 0);
      expect(store.saved.every((p) => p.flavor == ServerFlavor.v2), isTrue);
    },
  );

  test(
    'saved legacy phone retains identity and credentials while moving',
    () async {
      store.saved.add(
        ServerProfile(
          id: 'existing',
          name: 'This phone',
          baseUrl: TermuxBridge.managedServerUrl,
          requiresOpenCode2Migration: true,
          password: 'synthetic-test-secret',
        ),
      );
      await setup.begin(TermuxHostJob.install);
      expect(termux.launchCalls, 0);
      final original = store.saved.singleWhere((p) => p.id == 'existing');
      expect(original.openCode2HistoryPending, isTrue);
      expect(original.password, 'synthetic-test-secret');
    },
  );

  test(
    'an independently ready OC2 history cannot adopt the legacy profile',
    () async {
      store.saved.add(
        ServerProfile(
          id: 'legacy',
          name: 'This phone',
          baseUrl: TermuxBridge.managedServerUrl,
          requiresOpenCode2Migration: true,
          password: 'synthetic-test-secret',
        ),
      );
      termux
        ..inventoryOutput =
            'ubuntu=installed\nversion=2.1.1\nruntime=opencode2\n'
        ..statusOutput = termuxSnapshot(
          phase: 'ready',
          runtime: TermuxRuntime.openCode2,
          version: '2.1.1',
        )
        ..historyCheckExitCode = 74;
      await setup.begin(
        TermuxHostJob.switchRuntime,
        target: TermuxRuntime.openCode2,
      );
      expect(setup.phase, TermuxHostPhase.failed);
      expect(store.saved.single.id, 'legacy');
      expect(store.saved.single.requiresOpenCode2Migration, isTrue);
      expect(store.saved.single.openCode2HistoryPending, isFalse);
      expect(termux.switchCalls, 0);
    },
  );

  for (final pending in [false, true]) {
    test(
      'OC2 alias cannot replace the original migration profile (pending=$pending)',
      () async {
        setup.dispose();
        setup = TermuxHostSetup(
          store: store,
          connection: connection,
          copy: () => lookupAppLocalizations(const Locale('en')),
        );
        addTearDown(
          () => ManagedServerRecovery.disposeForPreferences(store.prefs),
        );
        final alias = ServerProfile(
          id: 'oc2-alias',
          name: 'Earlier OC2 alias',
          baseUrl: TermuxBridge.managedServerUrl,
          password: 'alias-only',
        );
        final original = ServerProfile(
          id: 'legacy-owner',
          name: 'My phone',
          baseUrl: TermuxBridge.managedServerUrl,
          password: 'original-only',
          requiresOpenCode2Migration: !pending,
          openCode2HistoryPending: pending,
        );
        store.saved.addAll([alias, original]);
        await store.setActiveId(original.id);
        await store.prefs.setString(
          'oc.localDraft.${original.id}',
          'owner draft',
        );
        termux
          ..inventoryOutput =
              'ubuntu=installed\nversion=2.1.1\nruntime=opencode2\n'
          ..statusOutput = termuxSnapshot(
            phase: 'ready',
            runtime: TermuxRuntime.openCode2,
            version: '2.1.1',
          );
        await setup.begin(
          TermuxHostJob.switchRuntime,
          target: TermuxRuntime.openCode2,
        );
        expect(setup.phase, TermuxHostPhase.ready);
        expect(connection.connectedTo, same(original));
        expect(store.activeId, original.id);
        expect(original.requiresOpenCode2Migration, isFalse);
        expect(original.openCode2HistoryPending, isTrue);
        expect(original.password, 'original-only');
        expect(alias.password, 'alias-only');
        expect(alias.openCode2HistoryPending, isFalse);
        expect(
          store.prefs.getString('oc.localDraft.${original.id}'),
          'owner draft',
        );
        expect(
          store.prefs.containsKey(
            ManagedServerRecovery.preferenceKey(original.id),
          ),
          isTrue,
        );
        expect(
          store.prefs.containsKey(
            ManagedServerRecovery.preferenceKey(alias.id),
          ),
          isFalse,
        );
        expect(termux.switchCalls, 0);
        expect(store.saved.map((p) => p.id), ['oc2-alias', 'legacy-owner']);
      },
    );
  }

  test('unreadable inventory cannot be treated as a fresh install', () async {
    termux.failInventory = true;
    await setup.begin(TermuxHostJob.install);
    expect(setup.phase, TermuxHostPhase.failed);
    expect(termux.launchCalls, 0);
    expect(store.saved, isEmpty);
  });
}
