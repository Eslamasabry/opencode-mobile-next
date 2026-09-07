import 'dart:async';
import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';

import 'support/profile_monitor_fixture.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        ),
  );
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          null,
        ),
  );
  test(
    'off by default; explicit opt-in persists and zero is a known observation',
    () async {
      final store = await monitorStore();
      var reads = 0;
      ProfileMonitor create() => ProfileMonitor(
        store: store,
        isReadable: (_) => true,
        createGateway: (_) {
          reads++;
          return (
            gateway: MonitorTestGateway(),
            operations: MonitorTestOperations(),
          );
        },
        networkWifi: () async => true,
        alert: (_, r, k, t) async => true,
        dismiss: (_) async => true,
      );
      var monitor = create();
      await monitor.refresh();
      expect(reads, 0);
      expect(monitor.unknownProfileCount, 2);
      await monitor.setEnabled('profile-1', true);
      await monitor.refresh();
      expect(monitor.snapshotFor('profile-1').pendingCount, 0);
      expect(monitor.unknownProfileCount, 1);
      monitor.dispose();
      monitor = create();
      addTearDown(monitor.dispose);
      expect(monitor.rulesFor('profile-1').enabled, isTrue);
      await monitor.refresh();
      expect(reads, 2);
      expect(store.prefs.getBool('oc.keepLiveInBackground'), isNull);
    },
  );
  test(
    'Wi-Fi false and thrown network policy both pause without opening transport',
    () async {
      final store = await monitorStore(count: 1);
      var reads = 0;
      var throws = false;
      final monitor = ProfileMonitor(
        store: store,
        isReadable: (_) => true,
        createGateway: (_) {
          reads++;
          return (
            gateway: MonitorTestGateway(),
            operations: MonitorTestOperations(),
          );
        },
        networkWifi: () async {
          if (throws) throw StateError('network plugin');
          return false;
        },
        alert: (_, r, k, t) async => true,
        dismiss: (_) async => true,
      );
      addTearDown(monitor.dispose);
      await monitor.setRules(
        'profile-1',
        const ProfileNotifyRules(enabled: true, wifiOnly: true),
      );
      await monitor.refresh();
      throws = true;
      await monitor.refresh();
      expect(reads, 0);
      expect(
        monitor.snapshotFor('profile-1').status,
        ProfileMonitorStatus.wifiRequired,
      );
    },
  );
  test(
    'failure is unknown with backoff; expired success and changed location are unknown',
    () async {
      final store = await monitorStore(count: 1);
      var now = DateTime(2026, 9, 7, 10);
      var fail = true;
      var reads = 0;
      final monitor = ProfileMonitor(
        store: store,
        isReadable: (_) => true,
        now: () => now,
        createGateway: (_) {
          reads++;
          return (
            gateway: MonitorTestGateway(failure: fail),
            operations: MonitorTestOperations(),
          );
        },
        networkWifi: () async => true,
        alert: (_, r, k, t) async => true,
        dismiss: (_) async => true,
      );
      addTearDown(monitor.dispose);
      await monitor.setEnabled('profile-1', true);
      await monitor.refresh();
      expect(monitor.snapshotFor('profile-1').pendingCount, isNull);
      expect(
        monitor.snapshotFor('profile-1').status,
        ProfileMonitorStatus.unavailable,
      );
      await monitor.refresh();
      expect(reads, 1);
      now = now.add(const Duration(minutes: 3));
      fail = false;
      await monitor.refresh();
      expect(reads, 2);
      expect(monitor.snapshotFor('profile-1').pendingCount, 0);
      await store.setLocation('profile-1', directory: '/changed');
      expect(monitor.snapshotFor('profile-1').pendingCount, isNull);
      await store.setLocation('profile-1');
      now = now.add(const Duration(minutes: 3));
      expect(monitor.snapshotFor('profile-1').pendingCount, isNull);
      expect(
        jsonEncode(monitor.snapshots.keys.toList()),
        isNot(contains('SENSITIVE')),
      );
    },
  );
  test(
    'background requires an existing active service and never enables keep-live',
    () async {
      final store = await monitorStore(count: 1);
      var reads = 0;
      final monitor = ProfileMonitor(
        store: store,
        isReadable: (_) => true,
        createGateway: (_) {
          reads++;
          return (
            gateway: MonitorTestGateway(),
            operations: MonitorTestOperations(),
          );
        },
        networkWifi: () async => true,
        alert: (_, r, k, t) async => true,
        dismiss: (_) async => true,
      );
      addTearDown(monitor.dispose);
      monitor.setRuntime(foreground: false, backgroundAllowed: false);
      await monitor.setEnabled('profile-1', true);
      await monitor.refresh();
      expect(reads, 0);
      expect(
        monitor.snapshotFor('profile-1').status,
        ProfileMonitorStatus.paused,
      );
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      await monitor.refresh();
      expect(reads, 1);
      expect(store.prefs.getBool('oc.keepLiveInBackground'), isNull);
    },
  );
  test(
    'same session IDs get distinct profile alerts and durable opaque routes',
    () async {
      final store = await monitorStore();
      var now = DateTime(2026, 9, 7, 10);
      final posted = <(String, String, String)>[];
      final monitor = ProfileMonitor(
        store: store,
        isReadable: (_) => true,
        now: () => now,
        createGateway: (_) => (
          gateway: MonitorTestGateway(requests: [request(1)]),
          operations: MonitorTestOperations(),
        ),
        networkWifi: () async => true,
        alert: (id, r, key, token) async {
          posted.add((id, key, token));
          return true;
        },
        dismiss: (_) async => true,
      );
      addTearDown(monitor.dispose);
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      await monitor.setEnabled('profile-1', true);
      await monitor.refresh();
      await monitor.setEnabled('profile-2', true);
      await monitor.refresh();
      expect(posted, hasLength(2));
      expect(posted[0].$2, isNot(posted[1].$2));
      for (final post in posted) {
        expect(
          monitor.routeForToken(post.$1, post.$3)?.sessionID,
          'same-session',
        );
        expect(
          monitor.routeForToken(
            post.$1 == 'profile-1' ? 'profile-2' : 'profile-1',
            post.$3,
          ),
          isNull,
        );
      }
      final stored = store.prefs.getString(
        ProfileMonitor.routesKey('profile-1'),
      )!;
      expect(stored, isNot(contains('Private title')));
      now = now.add(const Duration(days: 2));
      expect(monitor.routeForToken(posted[0].$1, posted[0].$3), isNull);
      now = now.subtract(const Duration(days: 2));
      store.profiles.first.username = 'replacement principal';
      final restored = ProfileMonitor(
        store: store,
        isReadable: (_) => true,
        createGateway: (_) => (
          gateway: MonitorTestGateway(),
          operations: MonitorTestOperations(),
        ),
        now: () => now,
        networkWifi: () async => true,
        alert: (_, r, k, t) async => true,
        dismiss: (_) async => true,
      );
      addTearDown(restored.dispose);
      expect(
        restored.routeForToken(posted[0].$1, posted[0].$3),
        isNull,
        reason: 'A recreated monitor must reject the prior principal route',
      );
    },
  );
  test(
    'quiet hours wrap midnight and later requests beyond first eight eventually alert',
    () async {
      final store = await monitorStore(count: 1);
      var now = DateTime(2026, 9, 7, 23);
      final posted = <String>[];
      final monitor = ProfileMonitor(
        store: store,
        isReadable: (_) => true,
        now: () => now,
        createGateway: (_) => (
          gateway: MonitorTestGateway(requests: List.generate(10, request)),
          operations: MonitorTestOperations(),
        ),
        networkWifi: () async => true,
        alert: (id, r, key, t) async {
          posted.add(key);
          return true;
        },
        dismiss: (_) async => true,
      );
      addTearDown(monitor.dispose);
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      await monitor.setRules(
        'profile-1',
        const ProfileNotifyRules(
          enabled: true,
          quietStart: 22 * 60,
          quietEnd: 8 * 60,
        ),
      );
      await monitor.refresh();
      expect(posted, isEmpty);
      now = DateTime(2026, 9, 8, 9);
      await monitor.refresh();
      expect(posted, hasLength(8));
      now = now.add(const Duration(minutes: 6));
      await monitor.refresh();
      expect(posted.toSet(), hasLength(10));
    },
  );
  test(
    'consent changes during a failed alert stop retries before the next post',
    () async {
      final store = await monitorStore(count: 1);
      var consent = true;
      var calls = 0;
      final monitor = ProfileMonitor(
        store: store,
        isReadable: (_) => true,
        createGateway: (_) => (
          gateway: MonitorTestGateway(requests: List.generate(2, request)),
          operations: MonitorTestOperations(),
        ),
        networkWifi: () async => true,
        alertsAllowed: (_) => consent,
        alert: (_, r, key, token) async {
          calls++;
          consent = false;
          return false;
        },
        dismiss: (_) async => true,
      );
      addTearDown(monitor.dispose);
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      await monitor.setEnabled('profile-1', true);
      await monitor.refresh();

      expect(calls, 1);
      expect(
        store.prefs.getString(ProfileMonitor.alertsKey('profile-1')),
        isNull,
      );
    },
  );
  test(
    'quiet hours changing during a failed alert stop retries before the next post',
    () async {
      final store = await monitorStore(count: 1);
      var now = DateTime(2026, 9, 7, 10);
      var calls = 0;
      final monitor = ProfileMonitor(
        store: store,
        isReadable: (_) => true,
        now: () => now,
        createGateway: (_) => (
          gateway: MonitorTestGateway(requests: List.generate(2, request)),
          operations: MonitorTestOperations(),
        ),
        networkWifi: () async => true,
        alert: (_, r, key, token) async {
          calls++;
          now = DateTime(2026, 9, 7, 23);
          return false;
        },
        dismiss: (_) async => true,
      );
      addTearDown(monitor.dispose);
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      await monitor.setRules(
        'profile-1',
        const ProfileNotifyRules(
          enabled: true,
          quietStart: 22 * 60,
          quietEnd: 8 * 60,
        ),
      );
      await monitor.refresh();

      expect(calls, 1);
      expect(
        store.prefs.getString(ProfileMonitor.alertsKey('profile-1')),
        isNull,
      );
    },
  );
  test(
    'a failed alert dismissal keeps its key for a later successful retry',
    () async {
      final store = await monitorStore(count: 1);
      await store.prefs.setStringList(
        ProfileMonitor.alertsKey('profile-1'),
        const ['monitor:profile-1:stale'],
      );
      var now = DateTime(2026, 9, 7, 10);
      final dismissed = <String>[];
      final monitor = ProfileMonitor(
        store: store,
        isReadable: (_) => true,
        now: () => now,
        createGateway: (_) => (
          gateway: MonitorTestGateway(),
          operations: MonitorTestOperations(),
        ),
        networkWifi: () async => true,
        alert: (_, r, key, token) async => true,
        dismiss: (key) async {
          dismissed.add(key);
          return dismissed.length > 1;
        },
      );
      addTearDown(monitor.dispose);
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      await monitor.setEnabled('profile-1', true);
      await monitor.refresh();

      expect(dismissed, ['monitor:profile-1:stale']);
      expect(store.prefs.getStringList(ProfileMonitor.alertsKey('profile-1')), [
        'monitor:profile-1:stale',
      ]);
      now = now.add(const Duration(minutes: 6));
      await monitor.refresh();
      expect(dismissed, ['monitor:profile-1:stale', 'monitor:profile-1:stale']);
      expect(
        store.prefs.getStringList(ProfileMonitor.alertsKey('profile-1')),
        isEmpty,
      );
    },
  );
  test('a timed-out poll cannot publish its late attention result', () async {
    final store = await monitorStore(count: 1);
    final pending = Completer<List<PermissionRequest>>();
    late MonitorTestGateway gateway;
    final monitor = ProfileMonitor(
      store: store,
      isReadable: (_) => true,
      createGateway: (_) => (
        gateway: gateway = MonitorTestGateway(pending: pending),
        operations: MonitorTestOperations(),
      ),
      networkWifi: () async => true,
      alert: (_, r, key, token) async => true,
      dismiss: (_) async => true,
      timeout: const Duration(milliseconds: 1),
    );
    addTearDown(monitor.dispose);
    await monitor.setEnabled('profile-1', true);
    await monitor.refresh();

    expect(gateway.isClosed, isTrue);
    expect(
      monitor.snapshotFor('profile-1').status,
      ProfileMonitorStatus.unavailable,
    );
    pending.complete([request(1)]);
    await monitor.drain('profile-1');
    expect(monitor.snapshotFor('profile-1').pendingCount, isNull);
  });
  test(
    'deletion cancels admission and sweeps settings, alert routing, and late results',
    () async {
      final store = await monitorStore(count: 1);
      final pending = Completer<List<PermissionRequest>>();
      late MonitorTestGateway gateway;
      final monitor = ProfileMonitor(
        store: store,
        isReadable: (_) => true,
        createGateway: (_) => (
          gateway: gateway = MonitorTestGateway(pending: pending),
          operations: MonitorTestOperations(),
        ),
        networkWifi: () async => true,
        alert: (_, r, k, t) async => true,
        dismiss: (_) async => true,
      );
      addTearDown(monitor.dispose);
      await monitor.setEnabled('profile-1', true);
      await Future<void>.delayed(Duration.zero);
      monitor.removeProfile('profile-1');
      expect(gateway.isClosed, isTrue);
      pending.complete([request(1)]);
      await monitor.drain('profile-1');
      expect(monitor.snapshots.containsKey('profile-1'), isFalse);
      expect(
        store.profileScopedPreferenceKeys('profile-1'),
        contains(ProfileMonitor.rulesKey('profile-1')),
      );
      await store.removeScopedPreferences('profile-1');
      expect(
        store.prefs.getString(ProfileMonitor.rulesKey('profile-1')),
        isNull,
      );
    },
  );
  test(
    'same-ID source edits invalidate rows, backoff and old alert routes',
    () async {
      final store = await monitorStore(count: 1);
      final tokens = <String>[];
      final dismissed = <String>[];
      var reads = 0;
      final monitor = ProfileMonitor(
        store: store,
        isReadable: (_) => true,
        createGateway: (_) {
          reads++;
          return (
            gateway: MonitorTestGateway(requests: [request(1)]),
            operations: MonitorTestOperations(),
          );
        },
        networkWifi: () async => true,
        alert: (_, r, k, token) async {
          tokens.add(token);
          return true;
        },
        dismiss: (key) async {
          dismissed.add(key);
          return true;
        },
      );
      addTearDown(monitor.dispose);
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      await monitor.setEnabled('profile-1', true);
      await monitor.refresh();
      expect(monitor.snapshotFor('profile-1').pendingCount, 1);
      final oldToken = tokens.single;
      store.profiles.single.baseUrl = 'https://replacement.example';
      expect(monitor.snapshotFor('profile-1').pendingCount, isNull);
      expect(monitor.routeForToken('profile-1', oldToken), isNull);
      await monitor.refresh();
      expect(
        reads,
        2,
        reason: 'Old source backoff cannot suppress the new source',
      );
      expect(dismissed, isNotEmpty);
      expect(monitor.routeForToken('profile-1', oldToken), isNull);
      store.profiles.single.password = 'synthetic replacement credential';
      expect(monitor.snapshotFor('profile-1').pendingCount, isNull);
      expect(monitor.routeForToken('profile-1', tokens.last), isNull);
    },
  );

  test(
    'clock rollback makes snapshots unknown and refuses future routes',
    () async {
      final store = await monitorStore(count: 1);
      var now = DateTime(2026, 9, 7, 10);
      String? token;
      final monitor = ProfileMonitor(
        store: store,
        isReadable: (_) => true,
        now: () => now,
        createGateway: (_) => (
          gateway: MonitorTestGateway(requests: [request(1)]),
          operations: MonitorTestOperations(),
        ),
        networkWifi: () async => true,
        alert: (_, r, k, value) async {
          token = value;
          return true;
        },
        dismiss: (_) async => true,
      );
      addTearDown(monitor.dispose);
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      await monitor.setEnabled('profile-1', true);
      await monitor.refresh();
      expect(monitor.snapshotFor('profile-1').pendingCount, 1);
      now = now.subtract(const Duration(minutes: 1));
      expect(monitor.snapshotFor('profile-1').pendingCount, isNull);
      expect(monitor.routeForToken('profile-1', token!), isNull);
    },
  );
}
