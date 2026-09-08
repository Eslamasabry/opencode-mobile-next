import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'support/profile_monitor_fixture.dart';

/// A monitor gateway whose session statuses the test drives poll by poll.
class _StatusGateway extends MonitorTestGateway {
  _StatusGateway(this.statuses, {this.statusFailure = false});
  final Map<String, String> statuses;
  final bool statusFailure;
  @override
  Future<Map<String, String>> sessionStatuses() async {
    if (statusFailure) throw StateError('SENSITIVE status outage');
    return Map.of(statuses);
  }

  @override
  Future<ServerPage<Session>> sessionPage({
    String? cursor,
    int limit = 100,
  }) async => ServerPage(
    items: [
      for (final id in statuses.keys)
        Session(
          id: id,
          title: 'Title of $id',
          directory: directory,
          workspaceID: workspace,
        ),
    ],
  );
}

class _Harness {
  _Harness({required this.store, required this.now, required this.statuses});
  final ProfileStore store;
  DateTime now;
  Map<String, String> statuses;
  bool statusFailure = false;
  final alerts = <MonitoredRequest>[];
  final dismissed = <String>[];
  bool alertResult = true;
  void Function()? beforeAlert;

  ProfileMonitor create() => ProfileMonitor(
    store: store,
    isReadable: (_) => true,
    now: () => now,
    backgroundInterval: const Duration(minutes: 1),
    createGateway: (_) => (
      gateway: _StatusGateway(statuses, statusFailure: statusFailure),
      operations: MonitorTestOperations(),
    ),
    networkWifi: () async => true,
    alert: (_, request, key, token) async {
      beforeAlert?.call();
      alerts.add(request);
      return alertResult;
    },
    dismiss: (key) async {
      dismissed.add(key);
      return true;
    },
  );
}

class _RefusingClaimStore extends InMemorySharedPreferencesStore {
  _RefusingClaimStore(super.data) : super.withData();
  @override
  Future<bool> setValue(String valueType, String key, Object value) async {
    if (key.endsWith('oc.monitorBusy.profile-1') &&
        value is String &&
        value.contains('"reminderClaimed":true')) {
      return false;
    }
    return super.setValue(valueType, key, value);
  }
}

class _HeldNotificationStore extends InMemorySharedPreferencesStore {
  _HeldNotificationStore(super.data, {required this.claim}) : super.withData();
  final bool claim;
  bool hold = false;
  final entered = Completer<void>();
  final release = Completer<void>();

  @override
  Future<bool> setValue(String valueType, String key, Object value) async {
    final target = claim
        ? key.endsWith(ProfileMonitor.busyIntervalsKey('profile-1')) &&
              value is String &&
              value.contains('"reminderClaimed":true')
        : key.endsWith(ProfileMonitor.routesKey('profile-1'));
    if (hold && target) {
      hold = false;
      entered.complete();
      await release.future;
    }
    return super.setValue(valueType, key, value);
  }
}

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

  const rules = ProfileNotifyRules(enabled: true, checkInAfterMinutes: 30);

  Future<_Harness> harness({
    Map<String, String> statuses = const {'ses-1': 'busy'},
  }) async => _Harness(
    store: await monitorStore(count: 1),
    now: DateTime(2026, 9, 8, 9),
    statuses: Map.of(statuses),
  );

  test('the rule is off by default and round-trips through JSON', () {
    expect(const ProfileNotifyRules().checkInAfterMinutes, isNull);
    expect(const ProfileNotifyRules().checkInAfter, isNull);
    final decoded = ProfileNotifyRules.fromJson(
      Map<String, dynamic>.from(jsonDecode(jsonEncode(rules.toJson())) as Map),
    );
    expect(decoded.checkInAfterMinutes, 30);
    expect(decoded.checkInAfter, const Duration(minutes: 30));
    // An older payload without the key, and junk values, both read as off.
    expect(
      ProfileNotifyRules.fromJson({'enabled': true}).checkInAfterMinutes,
      isNull,
    );
    expect(
      ProfileNotifyRules.fromJson({
        'checkInAfterMinutes': -5,
      }).checkInAfterMinutes,
      isNull,
    );
    expect(rules.copyWith(clearCheckIn: true).checkInAfterMinutes, isNull);
  });

  test(
    'a busy session is tracked as an observed interval and reminds once',
    () async {
      final h = await harness();
      final monitor = h.create();
      addTearDown(monitor.dispose);
      await monitor.setRules('profile-1', rules);
      monitor.setRuntime(foreground: false, backgroundAllowed: true);

      await monitor.refresh();
      var snapshot = monitor.snapshotFor('profile-1');
      expect(snapshot.busyIntervals.single.sessionID, 'ses-1');
      expect(snapshot.busyIntervals.single.title, 'Title of ses-1');
      expect(snapshot.busyIntervals.single.observedFor, Duration.zero);
      expect(snapshot.dueCheckIns(rules), isEmpty);
      // Busy sessions are not pending attention.
      expect(snapshot.pendingCount, 0);
      expect(h.alerts, isEmpty);

      // Observed for 29 minutes: not due yet.
      h.now = h.now.add(const Duration(minutes: 15));
      await monitor.refresh();
      h.now = h.now.add(const Duration(minutes: 14));
      await monitor.refresh();
      expect(h.alerts, isEmpty);

      // Observed for 30 minutes: due, one reminder for this interval.
      h.now = h.now.add(const Duration(minutes: 1));
      await monitor.refresh();
      snapshot = monitor.snapshotFor('profile-1');
      expect(snapshot.dueCheckIns(rules).single.sessionID, 'ses-1');
      expect(h.alerts.single.kind, MonitoredRequestKind.checkIn);
      expect(h.alerts.single.sessionID, 'ses-1');
      expect(h.alerts.single.title, 'Title of ses-1');
      expect(
        monitor.routeForToken('profile-1', _lastToken(h.store))?.kind,
        MonitoredRequestKind.checkIn,
      );

      // Still busy on later polls: no second reminder.
      h.now = h.now.add(const Duration(minutes: 5));
      await monitor.refresh();
      h.now = h.now.add(const Duration(minutes: 5));
      await monitor.refresh();
      expect(h.alerts, hasLength(1));
      // The elapsed figure only grows with observations.
      expect(
        monitor.snapshotFor('profile-1').busyIntervals.single.observedFor,
        const Duration(minutes: 40),
      );
    },
  );

  test('observed idle ends the interval and retires its reminder; a later '
      'busy interval reminds again', () async {
    final h = await harness();
    final monitor = h.create();
    addTearDown(monitor.dispose);
    await monitor.setRules('profile-1', rules);
    monitor.setRuntime(foreground: false, backgroundAllowed: true);
    await monitor.refresh();
    h.now = h.now.add(const Duration(minutes: 15));
    await monitor.refresh();
    h.now = h.now.add(const Duration(minutes: 15));
    await monitor.refresh();
    expect(h.alerts, hasLength(1));
    final firstKey = ProfileMonitor.alertKey('profile-1', h.alerts.single);

    h.statuses['ses-1'] = 'idle';
    h.now = h.now.add(const Duration(minutes: 1));
    await monitor.refresh();
    expect(monitor.snapshotFor('profile-1').busyIntervals, isEmpty);
    expect(h.dismissed, contains(firstKey));
    expect(
      h.store.prefs.getString(ProfileMonitor.busyIntervalsKey('profile-1')),
      isNull,
    );

    // Busy again: a fresh interval that reminds after its own 30 minutes.
    h.statuses['ses-1'] = 'busy';
    h.now = h.now.add(const Duration(minutes: 1));
    await monitor.refresh();
    h.now = h.now.add(const Duration(minutes: 15));
    await monitor.refresh();
    h.now = h.now.add(const Duration(minutes: 15));
    await monitor.refresh();
    expect(h.alerts, hasLength(2));
    expect(
      ProfileMonitor.alertKey('profile-1', h.alerts.last),
      isNot(firstKey),
    );
  });

  test(
    'a failed poll neither ends nor extends the interval; absence ends it',
    () async {
      final h = await harness(statuses: {'ses-1': 'busy', 'ses-2': 'busy'});
      final monitor = h.create();
      addTearDown(monitor.dispose);
      await monitor.setRules('profile-1', rules);
      await monitor.refresh();
      final started = h.now;

      h.statusFailure = true;
      h.now = h.now.add(const Duration(minutes: 10));
      await monitor.refresh();
      expect(
        monitor.snapshotFor('profile-1').status,
        ProfileMonitorStatus.unavailable,
      );

      // Recovery within the gap: both intervals continue from their first
      // observation; the failed poll added nothing to observedFor.
      h.statusFailure = false;
      h.statuses.remove('ses-2');
      h.now = started.add(const Duration(minutes: 13));
      await monitor.refresh();
      final intervals = monitor.snapshotFor('profile-1').busyIntervals;
      expect(intervals.single.sessionID, 'ses-1');
      expect(intervals.single.firstObservedBusyAt, started);
      expect(intervals.single.observedFor, const Duration(minutes: 13));
    },
  );

  test('a gap longer than the observation limit restarts the interval instead '
      'of asserting continuous work', () async {
    final h = await harness();
    final monitor = h.create();
    addTearDown(monitor.dispose);
    await monitor.setRules('profile-1', rules);
    monitor.setRuntime(foreground: false, backgroundAllowed: true);
    await monitor.refresh();
    h.now = h.now.add(const Duration(minutes: 20));
    await monitor.refresh();
    expect(
      monitor.snapshotFor('profile-1').busyIntervals.single.observedFor,
      const Duration(minutes: 20),
    );

    // Nothing observed for longer than the limit (app killed, no network).
    h.now = h.now.add(
      ProfileMonitor.busyObservationGap + const Duration(minutes: 1),
    );
    await monitor.refresh();
    final restarted = monitor.snapshotFor('profile-1').busyIntervals.single;
    expect(restarted.firstObservedBusyAt, h.now);
    expect(restarted.observedFor, Duration.zero);
    expect(h.alerts, isEmpty);
  });

  test('intervals and posted reminders survive a restart without reminding '
      'twice', () async {
    final h = await harness();
    var monitor = h.create();
    await monitor.setRules('profile-1', rules);
    monitor.setRuntime(foreground: false, backgroundAllowed: true);
    await monitor.refresh();
    h.now = h.now.add(const Duration(minutes: 15));
    await monitor.refresh();
    h.now = h.now.add(const Duration(minutes: 15));
    await monitor.refresh();
    expect(h.alerts, hasLength(1));
    monitor.dispose();

    // New process a few minutes later: the persisted interval continues
    // and the persisted alert key keeps the reminder from repeating.
    monitor = h.create();
    addTearDown(monitor.dispose);
    monitor.setRuntime(foreground: false, backgroundAllowed: true);
    h.now = h.now.add(const Duration(minutes: 5));
    await monitor.refresh();
    final interval = monitor.snapshotFor('profile-1').busyIntervals.single;
    expect(interval.observedFor, const Duration(minutes: 35));
    expect(h.alerts, hasLength(1));
  });

  test(
    'no reminder in the foreground, during quiet hours, or when off',
    () async {
      final h = await harness();
      final monitor = h.create();
      addTearDown(monitor.dispose);
      await monitor.setRules('profile-1', rules);
      await monitor.refresh();
      h.now = h.now.add(const Duration(minutes: 15));
      await monitor.refresh();
      h.now = h.now.add(const Duration(minutes: 15));

      // Foreground: the row is due, no notification.
      await monitor.refresh();
      expect(monitor.snapshotFor('profile-1').dueCheckIns(rules), hasLength(1));
      expect(h.alerts, isEmpty);

      // Quiet hours cover 09:00-10:00 local time.
      await monitor.setRules(
        'profile-1',
        rules.copyWith(quietStart: 9 * 60, quietEnd: 10 * 60),
      );
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      h.now = h.now.add(const Duration(minutes: 1));
      await monitor.refresh();
      expect(h.alerts, isEmpty);

      // Rule cleared: nothing is due, nothing is posted, intervals still shown.
      await monitor.setRules('profile-1', rules.copyWith(clearCheckIn: true));
      h.now = h.now.add(const Duration(minutes: 1));
      await monitor.refresh();
      final snapshot = monitor.snapshotFor('profile-1');
      expect(snapshot.busyIntervals, hasLength(1));
      expect(snapshot.dueCheckIns(monitor.rulesFor('profile-1')), isEmpty);
      expect(h.alerts, isEmpty);
    },
  );

  test('turning the rule off retires only check-in reminders', () async {
    final h = await harness();
    final monitor = h.create();
    addTearDown(monitor.dispose);
    await monitor.setRules('profile-1', rules);
    monitor.setRuntime(foreground: false, backgroundAllowed: true);
    await monitor.refresh();
    h.now = h.now.add(const Duration(minutes: 15));
    await monitor.refresh();
    h.now = h.now.add(const Duration(minutes: 15));
    await monitor.refresh();
    final key = ProfileMonitor.alertKey('profile-1', h.alerts.single);
    expect(ProfileMonitor.isCheckInAlertKey(key), isTrue);
    expect(
      ProfileMonitor.isCheckInAlertKey(
        ProfileMonitor.alertKey(
          'profile-1',
          const MonitoredRequest(
            id: 'request-1',
            sessionID: 'ses-1',
            kind: MonitoredRequestKind.permission,
          ),
        ),
      ),
      isFalse,
    );

    await monitor.setRules('profile-1', rules.copyWith(clearCheckIn: true));
    expect(h.dismissed, contains(key));
    expect(
      h.store.prefs.getStringList(ProfileMonitor.alertsKey('profile-1')),
      isNot(contains(key)),
    );
  });

  test('disabling monitoring forgets the intervals', () async {
    final h = await harness();
    final monitor = h.create();
    addTearDown(monitor.dispose);
    await monitor.setRules('profile-1', rules);
    await monitor.refresh();
    expect(
      h.store.prefs.getString(ProfileMonitor.busyIntervalsKey('profile-1')),
      isNotNull,
    );
    await monitor.setEnabled('profile-1', false);
    expect(
      h.store.prefs.getString(ProfileMonitor.busyIntervalsKey('profile-1')),
      isNull,
    );
  });

  test(
    'claim is durable before native dispatch and a refused alert is not retried after restart',
    () async {
      final h = await harness();
      h.alertResult = false;
      h.beforeAlert = () {
        final saved = h.store.prefs.getString(
          ProfileMonitor.busyIntervalsKey('profile-1'),
        )!;
        expect(saved, contains('"reminderClaimed":true'));
        expect(saved, isNot(contains('Title of')));
      };
      var monitor = h.create();
      await monitor.setRules('profile-1', rules);
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      for (var i = 0; i < 3; i++) {
        await monitor.refresh();
        h.now = h.now.add(const Duration(minutes: 15));
      }
      expect(h.alerts, hasLength(1));
      monitor.dispose();
      monitor = h.create();
      addTearDown(monitor.dispose);
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      await monitor.refresh();
      expect(h.alerts, hasLength(1));
      expect(monitor.snapshotFor('profile-1').dueCheckIns(rules), hasLength(1));
    },
  );

  for (final claim in [false, true]) {
    test(
      'foreground return during the ${claim ? 'claim' : 'route'} write prevents notification dispatch',
      () async {
        final h = await harness();
        final original = SharedPreferencesStorePlatform.instance;
        final held = _HeldNotificationStore(
          await original.getAll(),
          claim: claim,
        );
        SharedPreferencesStorePlatform.instance = held;
        addTearDown(() {
          if (!held.release.isCompleted) held.release.complete();
          SharedPreferencesStorePlatform.instance = original;
        });
        final monitor = h.create();
        addTearDown(monitor.dispose);
        await monitor.setRules('profile-1', rules);
        monitor.setRuntime(foreground: false, backgroundAllowed: true);
        await monitor.refresh();
        h.now = h.now.add(const Duration(minutes: 15));
        await monitor.refresh();
        h.now = h.now.add(const Duration(minutes: 15));

        held.hold = true;
        final pending = monitor.refresh();
        await held.entered.future;
        monitor.setRuntime(foreground: true, backgroundAllowed: true);
        held.release.complete();
        await pending;
        await monitor.refresh();
        expect(h.alerts, isEmpty);
        final stored = h.store.prefs.getString(
          ProfileMonitor.busyIntervalsKey('profile-1'),
        );
        expect(
          stored,
          claim
              ? contains('"reminderClaimed":true')
              : isNot(contains('"reminderClaimed":true')),
        );
        expect(
          monitor.snapshotFor('profile-1').dueCheckIns(rules),
          hasLength(1),
        );

        monitor.setRuntime(foreground: false, backgroundAllowed: true);
        h.now = h.now.add(monitor.foregroundInterval);
        await monitor.refresh();
        // A saved claim is never undone. An aborted route write has not yet
        // claimed the interval, so later permitted delivery remains possible.
        expect(h.alerts, hasLength(claim ? 0 : 1));
      },
    );
  }

  test(
    'clearing notification keys by toggling the rule does not repeat its interval',
    () async {
      final h = await harness();
      final monitor = h.create();
      addTearDown(monitor.dispose);
      await monitor.setRules('profile-1', rules);
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      for (var i = 0; i < 3; i++) {
        await monitor.refresh();
        h.now = h.now.add(const Duration(minutes: 15));
      }
      await monitor.setRules('profile-1', rules.copyWith(clearCheckIn: true));
      await monitor.setRules(
        'profile-1',
        rules.copyWith(checkInAfterMinutes: 15),
      );
      await monitor.refresh();
      expect(h.alerts, hasLength(1));
    },
  );

  test(
    'refused durable claim keeps due row but sends no native alert',
    () async {
      final h = await harness();
      final original = SharedPreferencesStorePlatform.instance;
      SharedPreferencesStorePlatform.instance = _RefusingClaimStore(
        await original.getAll(),
      );
      addTearDown(() => SharedPreferencesStorePlatform.instance = original);
      final monitor = h.create();
      addTearDown(monitor.dispose);
      await monitor.setRules('profile-1', rules);
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      for (var i = 0; i < 3; i++) {
        await monitor.refresh();
        h.now = h.now.add(const Duration(minutes: 15));
      }
      await monitor.refresh();
      expect(h.alerts, isEmpty);
      expect(monitor.snapshotFor('profile-1').dueCheckIns(rules), hasLength(1));
    },
  );

  test(
    'failed dispatch claims do not starve later sessions past the batch limit',
    () async {
      final h = await harness(
        statuses: {for (var i = 0; i < 9; i++) 'ses-$i': 'busy'},
      );
      h.alertResult = false;
      final monitor = h.create();
      addTearDown(monitor.dispose);
      await monitor.setRules('profile-1', rules);
      monitor.setRuntime(foreground: false, backgroundAllowed: true);
      for (var i = 0; i < 3; i++) {
        await monitor.refresh();
        h.now = h.now.add(const Duration(minutes: 15));
      }
      expect(h.alerts, hasLength(8));
      await monitor.refresh();
      expect(h.alerts, hasLength(9));
      expect(h.alerts.map((a) => a.sessionID).toSet(), hasLength(9));
    },
  );

  test('profile deletion sweeps interval, rules and route data', () async {
    final h = await harness();
    final monitor = h.create();
    await monitor.setRules('profile-1', rules);
    await monitor.refresh();
    monitor.dispose();
    final controller = ConnectionController(h.store);
    addTearDown(controller.dispose);
    await controller.deleteProfileAndLocalData('profile-1');
    expect(
      h.store.prefs.getKeys().where((key) => key.endsWith('.profile-1')),
      isEmpty,
    );
  });

  test(
    'same session in a different workspace starts a new observation',
    () async {
      final h = await harness();
      await h.store.setLocation(
        'profile-1',
        directory: '/project',
        workspace: 'a',
      );
      final monitor = h.create();
      addTearDown(monitor.dispose);
      await monitor.setRules('profile-1', rules);
      await monitor.refresh();
      h.now = h.now.add(const Duration(minutes: 15));
      await monitor.refresh();
      await h.store.setLocation(
        'profile-1',
        directory: '/project',
        workspace: 'b',
      );
      h.now = h.now.add(const Duration(minutes: 15));
      await monitor.refresh();
      final interval = monitor.snapshotFor('profile-1').busyIntervals.single;
      expect(interval.workspace, 'b');
      expect(interval.observedFor, Duration.zero);
      expect(h.alerts, isEmpty);
    },
  );
}

String _lastToken(ProfileStore store) {
  final routes = Map<String, dynamic>.from(
    jsonDecode(
          store.prefs.getString(ProfileMonitor.routesKey('profile-1')) ?? '{}',
        )
        as Map,
  );
  return routes.keys.last;
}
