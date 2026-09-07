import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/demo/demo_store.dart';
import 'package:opencode_mobile/domain/provider_quota.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/provider_quota_monitor.dart';

import 'provider_quota_test.dart' show providerQuotaFixture;

class _Preferences extends DemoPreferences {
  bool refuse = false;
  @override
  Future<bool> setString(String key, String value) async =>
      refuse ? false : super.setString(key, value);
  @override
  Future<bool> remove(String key) async => refuse ? false : super.remove(key);
}

class _Store extends ProfileStore {
  _Store(this.memory) : super(prefs: memory);
  final _Preferences memory;
  final entries = <ServerProfile>[];
  @override
  List<ServerProfile> get profiles => List.unmodifiable(entries);
}

class _Gateway implements ProviderQuotaGateway {
  _Gateway(this.result);
  final Future<ProviderQuotaSnapshot> result;
  int closed = 0;
  @override
  Future<ProviderQuotaSnapshot> readSnapshot() => result;
  @override
  void close() {
    closed++;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late _Store store;
  late ProviderQuotaMonitor monitor;
  late DateTime now;
  late ProviderQuotaSnapshot response;
  final reads = <String>[],
      alerts = <Map<String, String>>[],
      gateways = <_Gateway>[];
  final deleted = <String>{};
  final dismissed = <String>[];
  bool? wifi = true;
  bool wifiThrows = false, shown = true;
  Completer<ProviderQuotaSnapshot>? pending;
  ProviderQuotaSnapshot sample({
    String account = 'a',
    bool noReset = false,
    int? fetched,
    double percent = 100,
  }) {
    final data = providerQuotaFixture(
      fetchedAtMs: fetched ?? now.millisecondsSinceEpoch,
    );
    (data['account'] as Map)['ref'] = account * 64;
    final window = (data['windows'] as List).first as Map;
    window['usedPercent'] = percent;
    if (noReset) {
      window.remove('resetsAtMs');
    }
    return ProviderQuotaSnapshot.fromJson(data);
  }

  ProviderQuotaMonitor make() => ProviderQuotaMonitor(
    store: store,
    createGateway: (profile, provider) {
      reads.add(profile.id);
      final gateway = _Gateway(pending?.future ?? Future.value(response));
      gateways.add(gateway);
      return gateway;
    },
    isReadable: (id) =>
        !deleted.contains(id) && store.entries.any((p) => p.id == id),
    networkWifi: () async {
      if (wifiThrows) {
        throw StateError('private-network-error');
      }
      return wifi;
    },
    alert: ({required profileID, required key, required token}) async {
      alerts.add({'profileID': profileID, 'key': key, 'token': token});
      return shown;
    },
    dismiss: (key) async {
      dismissed.add(key);
      return true;
    },
    clock: () => now,
  );
  void add(String id) => store.entries.add(
    ServerProfile(
      id: id,
      name: 'Synthetic',
      baseUrl: 'https://$id.example',
      username: 'fixture',
      password: 'private-fixture-password',
    ),
  );
  setUp(() {
    now = DateTime.utc(2026, 9, 7, 12);
    store = _Store(_Preferences());
    add('profile');
    reads.clear();
    alerts.clear();
    gateways.clear();
    deleted.clear();
    dismissed.clear();
    wifi = true;
    wifiThrows = false;
    shown = true;
    pending = null;
    response = sample();
    monitor = make();
    addTearDown(() => monitor.dispose());
  });

  test(
    'no reads before independent enrollment and future evidence is refused',
    () async {
      monitor.start();
      await monitor.refresh();
      expect(reads, isEmpty);
      expect(
        await monitor.enroll(
          'profile',
          sample(fetched: now.millisecondsSinceEpoch + 1),
          notifications: true,
        ),
        isFalse,
      );
      expect(
        await monitor.enroll('profile', response, notifications: false),
        isTrue,
      );
      await monitor.refresh();
      expect(reads, ['profile']);
      expect(alerts, isEmpty);
      expect(
        store.memory.getString(ProviderQuotaMonitor.key('profile')),
        isNot(contains('usedPercent')),
      );
    },
  );

  test(
    'background polling needs active runtime and unknown Wi-Fi fails closed',
    () async {
      await monitor.enroll(
        'profile',
        response,
        notifications: true,
        wifiOnly: true,
      );
      monitor.setRuntime(foreground: false, backgroundAllowed: false);
      await monitor.refresh();
      expect(reads, isEmpty);
      wifi = null;
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      await monitor.refresh();
      expect(reads, isEmpty);
      expect(
        monitor.observationFor('profile', QuotaProvider.codex).status,
        QuotaMonitorStatus.wifiRequired,
      );
      wifiThrows = true;
      await monitor.refresh();
      expect(reads, isEmpty);
    },
  );

  test('native opt-in and quiet hours are separate from fresh reads', () async {
    await monitor.enroll(
      'profile',
      response,
      notifications: true,
      quietStart: 0,
      quietEnd: 0,
    );
    monitor.setRuntime(foreground: false, backgroundAllowed: true);
    await monitor.refresh();
    expect(reads, isNotEmpty);
    expect(alerts, isEmpty);
    await monitor.setPolicy(
      'profile',
      QuotaProvider.codex,
      notifications: false,
      wifiOnly: false,
    );
    await monitor.refresh();
    expect(alerts, isEmpty);
    await monitor.setPolicy(
      'profile',
      QuotaProvider.codex,
      notifications: true,
      wifiOnly: false,
    );
    await monitor.refresh();
    expect(alerts, hasLength(1));
    expect(alerts.single.keys, unorderedEquals(['profileID', 'key', 'token']));
    expect(alerts.single.toString(), isNot(contains('private')));
  });

  test(
    'unknown resets dedupe across restart and account rotation cannot publish or route',
    () async {
      response = sample(noReset: true);
      await monitor.enroll('profile', response, notifications: true);
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      await monitor.refresh();
      expect(alerts, hasLength(1));
      final token = alerts.single['token']!;
      final restored = make();
      addTearDown(restored.dispose);
      restored.setRuntime(foreground: false, backgroundAllowed: true);
      await restored.refresh();
      expect(alerts, hasLength(1));
      response = sample(account: 'b', noReset: true);
      await restored.refresh();
      expect(alerts, hasLength(1));
      expect(
        restored.observationFor('profile', QuotaProvider.codex).status,
        QuotaMonitorStatus.sourceChanged,
      );
      expect(await restored.resolveRoute('profile', token), isNull);
    },
  );

  test(
    'three-source cycles rotate fairly without relabelling the active server',
    () async {
      for (var i = 0; i < 4; i++) {
        add('extra$i');
      }
      for (final profile in store.entries) {
        await monitor.enroll(profile.id, response, notifications: false);
      }
      await monitor.refresh();
      expect(reads, hasLength(3));
      await monitor.refresh();
      expect(reads, hasLength(6));
      expect(reads.toSet(), hasLength(5));
    },
  );

  test(
    'background cancellation and deletion reject late provider completions',
    () async {
      await monitor.enroll('profile', response, notifications: true);
      pending = Completer<ProviderQuotaSnapshot>();
      final read = monitor.refresh();
      await Future<void>.delayed(Duration.zero);
      monitor.setRuntime(foreground: false, backgroundAllowed: false);
      deleted.add('profile');
      monitor.removeProfile('profile');
      await store.removeScopedPreferences('profile');
      pending!.complete(response);
      await read;
      await monitor.drain('profile');
      expect(gateways.single.closed, greaterThan(0));
      expect(alerts, isEmpty);
      expect(
        store.memory.containsKey(ProviderQuotaMonitor.key('profile')),
        isFalse,
      );
      expect(monitor.routeForToken('profile', 'a' * 64), isNull);
    },
  );

  test('failed disable pauses reads until cleanup can be retried', () async {
    await monitor.enroll('profile', response, notifications: true);
    store.memory.refuse = true;
    expect(await monitor.disable('profile', QuotaProvider.codex), isFalse);
    await monitor.refresh();
    expect(reads, isEmpty);
    store.memory.refuse = false;
    expect(await monitor.disable('profile', QuotaProvider.codex), isTrue);
    expect(monitor.sources, isEmpty);
  });

  test(
    'recovery dismisses old alerts and changing threshold rearms its own marker',
    () async {
      response = sample(percent: 60, noReset: true);
      await monitor.enroll(
        'profile',
        response,
        notifications: true,
        threshold: 50,
      );
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      await monitor.refresh();
      expect(alerts, hasLength(1));
      dismissed.clear();
      final missing = providerQuotaFixture(
        fetchedAtMs: now.millisecondsSinceEpoch,
      );
      (missing['windows'] as List)[0] = {'id': 'primary', 'status': 'missing'};
      response = ProviderQuotaSnapshot.fromJson(missing);
      await monitor.refresh();
      expect(dismissed, isEmpty, reason: 'a missing window is not recovery');
      response = sample(percent: 20, noReset: true);
      await monitor.refresh();
      expect(dismissed, contains('quota:profile:codex'));
      expect(alerts, hasLength(1));
      await monitor.setPolicy(
        'profile',
        QuotaProvider.codex,
        notifications: true,
        wifiOnly: false,
        threshold: 90,
      );
      response = sample(percent: 95, noReset: true);
      await monitor.refresh();
      expect(alerts, hasLength(2));
      await monitor.refresh();
      expect(alerts, hasLength(2));
    },
  );

  testWidgets(
    'unenrolled startup and disposed queued work leave no timers or reads',
    (tester) async {
      monitor.start();
      expect(monitor.refreshing, isFalse);
      monitor.dispose();
      await tester.pump();
      expect(reads, isEmpty);
      monitor = make();
      await monitor.enroll('profile', response, notifications: false);
      final queued = monitor.refresh();
      monitor.dispose();
      await tester.pump();
      await queued;
      expect(reads, isEmpty);
    },
  );

  test(
    'valid historical route survives unavailable refresh but disable rejects pending route',
    () async {
      await monitor.enroll('profile', response, notifications: true);
      final token = monitor.rulesFor('profile', QuotaProvider.codex)!.token;
      pending = Completer<ProviderQuotaSnapshot>();
      final route = monitor.resolveRoute('profile', token);
      await Future<void>.delayed(Duration.zero);
      expect(
        monitor.observationFor('profile', QuotaProvider.codex).status,
        QuotaMonitorStatus.checking,
      );
      pending!.completeError(
        const ProviderQuotaFailure(QuotaFailureKind.unavailable),
      );
      expect((await route)?.profileID, 'profile');
      expect(
        monitor.observationFor('profile', QuotaProvider.codex).status,
        QuotaMonitorStatus.unavailable,
      );
      expect(
        monitor.observationFor('profile', QuotaProvider.codex).snapshot,
        isNull,
      );
      pending = Completer<ProviderQuotaSnapshot>();
      final revoked = monitor.resolveRoute('profile', token);
      await Future<void>.delayed(Duration.zero);
      await monitor.disable('profile', QuotaProvider.codex);
      pending!.complete(response);
      expect(await revoked, isNull);
    },
  );

  testWidgets(
    'expired measurements hide while historical alerts remain until review',
    (tester) async {
      response = sample(noReset: true);
      await monitor.enroll('profile', response, notifications: true);
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      final reading = monitor.refresh();
      await tester.pump();
      await reading;
      expect(alerts, hasLength(1));
      dismissed.clear();
      final token = alerts.single['token']!;
      final advance =
          response.expiresAt.difference(now) + const Duration(seconds: 1);
      now = now.add(advance);
      await tester.pump(advance);
      expect(
        monitor.observationFor('profile', QuotaProvider.codex).snapshot,
        isNull,
      );
      expect(dismissed, isEmpty);
      final review = monitor.resolveRoute('profile', token);
      await tester.pump();
      expect((await review)?.profileID, 'profile');
      expect(dismissed, contains('quota:profile:codex'));
      expect(alerts, hasLength(1));
      monitor.dispose();
    },
  );

  test(
    'quota native routes require opaque scope and cannot carry quick actions',
    () {
      expect(
        CodingAlertOpen.fromPlatform({
          'kind': 'quota',
          'sessionID': 'quota',
          'profileID': 'profile',
          'monitorToken': 'a' * 64,
        })?.kind,
        CodingAlertKind.quota,
      );
      expect(
        CodingAlertOpen.fromPlatform({
          'kind': 'quota',
          'sessionID': 'session',
          'profileID': 'profile',
          'monitorToken': 'a' * 64,
        }),
        isNull,
      );
      expect(
        CodingAlertAction.fromPlatform({
          'kind': 'quota',
          'sessionID': 'quota',
          'decision': 'allow',
        }),
        isNull,
      );
    },
  );
}
