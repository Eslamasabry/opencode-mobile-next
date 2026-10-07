import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/builtin_server_recovery.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Store extends ProfileStore {
  _Store({required super.prefs, required this.all});
  final List<ServerProfile> all;

  @override
  List<ServerProfile> get profiles => all;
}

Map<String, Object?> _budget({
  int attempts = 0,
  int revision = 0,
  bool pending = false,
  String? eventId,
  int? generation,
  DateTime? confirmedAt,
}) => {
  'version': 1,
  'revision': revision,
  'attempts': attempts,
  'nextAt': null,
  'pending': pending,
  'eventId': eventId,
  'recoveryGeneration': generation,
  'confirmedAt': confirmedAt?.millisecondsSinceEpoch,
};

/// Native owns durable reservation; Dart can only consume receipt snapshots.
class _NativeLinux extends BuiltinLinux {
  _NativeLinux(this.prefs);

  final SharedPreferences prefs;
  Map<String, Object?>? budget;
  final List<Map<String, Object?>> receipts = [];
  final List<Map<String, Object?>?> migrations = [];
  final List<Map<String, Object?>> stagedMigrations = [];
  final List<String> calls = [];
  final List<String> acknowledgements = [];
  String? boundProfile;
  bool enabled = false;
  bool wanted = true;
  bool running = false;
  bool failStart = false;
  bool scheduled = false;
  bool corruptBudget = false;
  bool stageWriteFails = false;
  bool counterWriteRejected = false;
  bool nativeOwned = false;
  bool manualStarted = false;
  int generation = 1;
  int directStarts = 0;
  int backgroundStarts = 0;
  int manualResets = 0;
  int confirmations = 0;
  int eventSequence = 0;
  Completer<void>? writeGate;
  Completer<void>? writeEntered;
  Completer<void>? deleteGate;
  Completer<void>? deleteEntered;

  int get attempts => budget!['attempts']! as int;

  bool get markerValid {
    try {
      final raw = prefs.getString(
        BuiltinServerRecovery.keyFor(boundProfile ?? 'phone'),
      );
      final value = raw == null ? null : jsonDecode(raw);
      return value is Map &&
          value['version'] == 2 &&
          value['nativeAuthority'] == true;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: running,
    serverRestartWanted: wanted,
    serverRecoveryGeneration: nativeOwned && running
        ? budget!['recoveryGeneration']! as int
        : generation,
    serverRecoveryAuthority: true,
    serverRecoveryScheduled: scheduled,
  );

  @override
  Future<Map<Object?, Object?>> stageServerRecovery(
    String profileId,
    Map<String, Object?> legacyBudget,
  ) async {
    calls.add('stage');
    enabled = false;
    stagedMigrations.add(Map.of(legacyBudget));
    if (stageWriteFails) throw StateError('Native staging write failed');
    budget ??= Map.of(legacyBudget);
    return Map.of(budget!);
  }

  @override
  Future<Map<Object?, Object?>> bindServerRecovery({
    required String profileId,
    required bool enabled,
    Map<String, Object?>? legacyBudget,
  }) async {
    calls.add('bind');
    boundProfile = profileId;
    if (!markerValid) throw StateError('Migration marker missing');
    migrations.add(legacyBudget == null ? null : Map.of(legacyBudget));
    if (budget == null) throw StateError('Native budget missing');
    this.enabled = enabled;
    return serverRecoveryBudget(profileId);
  }

  @override
  Future<Map<Object?, Object?>> serverRecoveryBudget(String profileId) async {
    if (budget == null) throw StateError('Native budget missing');
    return corruptBudget ? {'version': 1, 'attempts': -1} : Map.of(budget!);
  }

  @override
  Future<Map<Object?, Object?>> updateServerRecoveryReceipt(
    String profileId,
    Map<String, Object?> next,
  ) async {
    calls.add('receipt');
    if (next['attempts'] != budget!['attempts'] ||
        next['revision'] != budget!['revision']) {
      counterWriteRejected = true;
      throw StateError('Consumer cannot change native counter');
    }
    budget = {...next, 'revision': (budget!['revision']! as int) + 1};
    return Map.of(budget!);
  }

  void _reserve({required bool background}) {
    if (!wanted || !enabled || !markerValid || attempts >= 3) {
      throw const BuiltinLinuxException('The phone server could not restart.');
    }
    final current = budget!;
    if (current['confirmedAt'] != null) receipts.add(Map.of(current));
    budget = _budget(
      attempts: attempts + 1,
      revision: (current['revision']! as int) + 1,
      pending: true,
      eventId: 'native-restart:${++eventSequence}',
      generation: generation,
    );
    nativeOwned = background;
    running = !failStart;
    scheduled = false;
    if (background) {
      backgroundStarts++;
    } else {
      directStarts++;
    }
  }

  void backgroundRestart() => _reserve(background: true);

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    calls.add('password-write');
    writeEntered?.complete();
    await writeGate?.future;
    calls.add('password-written');
    return const BuiltinLinuxRunResult(exitCode: 0, output: '');
  }

  @override
  Future<void> restartServer(
    String script, {
    int port = BuiltinLinux.serverPort,
    required int expectedGeneration,
  }) async {
    if (expectedGeneration != generation) {
      throw const BuiltinLinuxException('The phone server could not restart.');
    }
    _reserve(background: false);
  }

  @override
  Future<void> startServer(
    String script, {
    int port = BuiltinLinux.serverPort,
  }) async {
    generation++;
    wanted = true;
    running = !failStart;
    nativeOwned = false;
    manualStarted = true;
  }

  @override
  Future<void> confirmServerRecovery({required int expectedGeneration}) async {
    if (!wanted ||
        !running ||
        expectedGeneration != budget!['recoveryGeneration']) {
      throw const BuiltinLinuxException('The phone server could not restart.');
    }
    confirmations++;
  }

  @override
  Future<Map<Object?, Object?>> confirmManualServerStart(
    String profileId,
  ) async {
    if (boundProfile != profileId || !manualStarted || !running || !wanted) {
      throw StateError('Manual start not confirmed');
    }
    if (budget!['confirmedAt'] != null) receipts.add(Map.of(budget!));
    budget = _budget(revision: (budget!['revision']! as int) + 1);
    manualStarted = false;
    manualResets++;
    return Map.of(budget!);
  }

  @override
  Future<List<Map<Object?, Object?>>> serverRecoveryReceipts(
    String profileId,
  ) async => [for (final receipt in receipts) Map.of(receipt)];

  @override
  Future<void> ackServerRecoveryReceipt(
    String profileId,
    String eventId,
  ) async {
    acknowledgements.add(eventId);
    receipts.removeWhere((receipt) => receipt['eventId'] == eventId);
  }

  @override
  Future<void> cancelServerRecovery() async {
    generation++;
    if (!nativeOwned &&
        budget?['pending'] == true &&
        budget?['confirmedAt'] == null) {
      running = false;
    }
  }

  @override
  Future<void> unbindServerRecovery(
    String profileId, {
    bool delete = false,
  }) async {
    enabled = false;
    scheduled = false;
    calls.add(delete ? 'delete-entered' : 'unbind');
    if (delete) {
      deleteEntered?.complete();
      await deleteGate?.future;
      budget = null;
      receipts.clear();
      calls.add('deleted');
    }
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late _Store store;
  late _NativeLinux linux;
  late BuiltinServerStarter starter;
  late BuiltinServerRecovery recovery;
  late ServerProfile phone;
  late DateTime now;
  late bool recordsAllowed;
  late Map<String, DateTime> acts;

  BuiltinServerRecovery makeRecovery() => BuiltinServerRecovery(
    store: store,
    linux: linux,
    starter: starter,
    now: () => now,
    onRestart: ({required profileId, required eventId, required at}) async {
      if (!recordsAllowed) return false;
      acts.putIfAbsent(eventId, () => at);
      return true;
    },
  );

  Future<void> recreateOwner() async {
    recovery.dispose();
    await Future<void>.delayed(Duration.zero);
    recovery = makeRecovery()..setForeground(true);
  }

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    AutomationPolicyController.resetShared();
    phone = ServerProfile(
      id: 'phone',
      name: 'This phone',
      baseUrl: BuiltinLinux.serverUrl,
    )..username = 'opencode';
    phone.password = 'test-password';
    store = _Store(prefs: prefs, all: [phone]);
    linux = _NativeLinux(prefs);
    starter = BuiltinServerStarter(
      linux: linux,
      readyTimeout: Duration.zero,
      pollInterval: Duration.zero,
    );
    now = DateTime.utc(2026, 10, 7);
    recordsAllowed = true;
    acts = {};
    serverProbe = ({required baseUrl, username, password}) async =>
        linux.running
        ? const ServerProbeResult.success('1')
        : const ServerProbeResult.failure('Unavailable');
    recovery = makeRecovery()..setForeground(true);
  });

  tearDown(() {
    recovery.dispose();
    starter.dispose();
    serverProbe = probeServerConnection;
  });

  test(
    'spent legacy budget migrates once with marker before activation',
    () async {
      await prefs.setString(
        BuiltinServerRecovery.keyFor(phone.id),
        jsonEncode(_budget(attempts: 2)),
      );
      linux.running = true;
      await recovery.check(phone);
      expect(recovery.value.phase, BuiltinRecoveryPhase.ready);
      expect(linux.stagedMigrations.single['attempts'], 2);
      expect(linux.migrations.single, isNull);
      expect(linux.markerValid, isTrue);
      expect(linux.attempts, 2);
      expect(recovery.value.attempts, 2);

      await recreateOwner();
      await recovery.check(phone);
      expect(linux.migrations, hasLength(2));
      expect(linux.migrations.last, isNull);
      expect(linux.stagedMigrations, hasLength(1));
      expect(linux.attempts, 2);
      expect(linux.directStarts, 0);
    },
  );

  test(
    'failed native staging keeps legacy spent attempts across retry',
    () async {
      await prefs.setString(
        BuiltinServerRecovery.keyFor(phone.id),
        jsonEncode(_budget(attempts: 2)),
      );
      linux.stageWriteFails = true;
      await recovery.check(phone);
      expect(recovery.value.phase, BuiltinRecoveryPhase.storageUnavailable);
      expect(linux.enabled, isFalse);
      expect(linux.directStarts, 0);
      expect(linux.budget, isNull);
      expect(
        jsonDecode(
          prefs.getString(BuiltinServerRecovery.keyFor(phone.id))!,
        )['attempts'],
        2,
      );
      expect(linux.markerValid, isFalse);

      await recreateOwner();
      linux.stageWriteFails = false;
      linux.failStart = true;
      await recovery.check(phone);
      expect(linux.attempts, 3);
      expect(linux.directStarts, 1);
      await recovery.check(phone);
      await recreateOwner();
      await recovery.check(phone);
      expect(recovery.value.phase, BuiltinRecoveryPhase.exhausted);
      expect(linux.attempts, 3);
      expect(linux.directStarts, 1);
    },
  );

  test(
    'retrying staged migration never overwrites native spent attempts',
    () async {
      linux.budget = _budget(attempts: 3, revision: 4);
      await prefs.setString(
        BuiltinServerRecovery.keyFor(phone.id),
        jsonEncode(_budget(attempts: 1)),
      );
      await recovery.check(phone);
      expect(recovery.value.phase, BuiltinRecoveryPhase.exhausted);
      expect(linux.attempts, 3);
      expect(linux.directStarts, 0);
      expect(linux.stagedMigrations.single['attempts'], 1);
    },
  );

  test(
    'valid v2 marker with missing native budget admits no restart',
    () async {
      await prefs.setString(
        BuiltinServerRecovery.keyFor(phone.id),
        jsonEncode({'version': 2, 'nativeAuthority': true}),
      );
      await recovery.check(phone);
      expect(linux.directStarts, 0);
      expect(linux.budget, isNull);
      expect(linux.enabled, isFalse);
      expect(recovery.value.phase, isNot(BuiltinRecoveryPhase.ready));
    },
  );

  test(
    'corrupt migration marker admits no native binding or restart',
    () async {
      for (final raw in ['broken', '{"version":2,"nativeAuthority":false}']) {
        await prefs.setString(BuiltinServerRecovery.keyFor(phone.id), raw);
        await recovery.check(phone);
        expect(recovery.value.phase, BuiltinRecoveryPhase.storageUnavailable);
        expect(linux.migrations, isEmpty);
        expect(linux.directStarts, 0);
      }
    },
  );

  test('malformed native snapshot fails closed', () async {
    linux.corruptBudget = true;
    await recovery.check(phone);
    expect(recovery.value.phase, BuiltinRecoveryPhase.storageUnavailable);
    expect(linux.directStarts, 0);
    expect(acts, isEmpty);
  });

  test(
    'direct and background reservations share three attempts across owners',
    () async {
      linux.failStart = true;
      await recovery.check(phone);
      expect(linux.directStarts, 1);
      expect(linux.attempts, 1);

      recovery.setForeground(false);
      await Future<void>.delayed(Duration.zero);
      linux.backgroundRestart();
      expect(linux.attempts, 2);
      expect(linux.backgroundStarts, 1);
      await recovery.check(phone);
      expect(linux.directStarts, 1);

      await recreateOwner();
      await recovery.check(phone);
      expect(linux.attempts, 3);
      expect(linux.directStarts, 2);
      await recovery.check(phone);
      expect(recovery.value.phase, BuiltinRecoveryPhase.exhausted);
      await recreateOwner();
      await recovery.check(phone);
      expect(recovery.value.phase, BuiltinRecoveryPhase.exhausted);
      expect(linux.directStarts + linux.backgroundStarts, 3);
      expect(linux.counterWriteRejected, isFalse);
      expect(acts, isEmpty);
    },
  );

  test('only confirmed manual Start resets native attempts', () async {
    await prefs.setString(
      BuiltinServerRecovery.keyFor(phone.id),
      jsonEncode(_budget(attempts: 3)),
    );
    await recovery.check(phone);
    expect(recovery.value.phase, BuiltinRecoveryPhase.exhausted);
    linux.failStart = true;
    expect(await starter.start(phone), isNotNull);
    await recovery.check(phone);
    expect(linux.attempts, 3);
    expect(linux.manualResets, 0);

    linux.failStart = false;
    expect(await starter.start(phone), isNull);
    await recovery.check(phone);
    expect(linux.manualResets, 1);
    expect(linux.attempts, 0);
    expect(recovery.value.attempts, 0);
    linux.running = false;
    await recovery.check(phone);
    expect(linux.attempts, 1);
    expect(linux.counterWriteRejected, isFalse);
  });

  test(
    'healthy manual Start resets budget with automatic restart disabled',
    () async {
      await prefs.setString(
        BuiltinServerRecovery.keyFor(phone.id),
        jsonEncode(_budget(attempts: 2)),
      );
      linux.failStart = true;
      await recovery.check(phone);
      await recovery.check(phone);
      expect(linux.attempts, 3);
      expect(recovery.value.phase, BuiltinRecoveryPhase.exhausted);
      expect(linux.directStarts, 1);

      await AutomationPolicyController.forProfile(
        prefs,
        phone.id,
      ).setBehavior(AutomationBehavior.restartPhoneServer, false);
      linux.failStart = false;
      expect(await starter.start(phone), isNull);
      await recovery.check(phone);
      expect(linux.boundProfile, phone.id);
      expect(linux.enabled, isFalse);
      expect(linux.manualResets, 1);
      expect(linux.attempts, 0);
      expect(linux.directStarts, 1);
      expect(acts, isEmpty);

      linux.running = false;
      await recovery.check(phone);
      expect(linux.attempts, 0);
      expect(linux.directStarts, 1);
      expect(linux.backgroundStarts, 0);
    },
  );

  test(
    'Stop background and scheduled native recovery never dispatch from Dart',
    () async {
      linux.wanted = false;
      await recovery.check(phone);
      expect(recovery.value.phase, BuiltinRecoveryPhase.stopped);
      linux.wanted = true;
      recovery.setForeground(false);
      await recovery.check(phone);
      expect(recovery.value.phase, BuiltinRecoveryPhase.paused);
      recovery.setForeground(true);
      linux.scheduled = true;
      await recovery.check(phone);
      expect(recovery.value.phase, BuiltinRecoveryPhase.waiting);
      expect(linux.directStarts, 0);
      expect(linux.attempts, 0);
    },
  );

  test(
    'legacy pending confirmation survives migration and records after Stop',
    () async {
      final confirmedAt = now.subtract(const Duration(minutes: 1));
      await prefs.setString(
        BuiltinServerRecovery.keyFor(phone.id),
        jsonEncode(
          _budget(
            attempts: 2,
            pending: true,
            eventId: 'legacy-confirmed',
            generation: 1,
            confirmedAt: confirmedAt,
          ),
        ),
      );
      recordsAllowed = false;
      linux.wanted = false;
      await recovery.check(phone);
      expect(linux.budget?['pending'], isTrue);
      expect(linux.budget?['confirmedAt'], confirmedAt.millisecondsSinceEpoch);
      expect(linux.attempts, 2);
      await recreateOwner();
      recordsAllowed = true;
      await recovery.check(phone);
      await recovery.check(phone);
      expect(acts.keys, ['legacy-confirmed']);
      expect(acts['legacy-confirmed']!.toUtc(), confirmedAt);
      expect(linux.budget?['pending'], isFalse);
      expect(linux.attempts, 2);
      expect(linux.directStarts, 0);
    },
  );

  test(
    'native background pending attempt is confirmed without another dispatch',
    () async {
      linux.running = true;
      await recovery.check(phone);
      recovery.setForeground(false);
      await Future<void>.delayed(Duration.zero);
      linux.running = false;
      linux.backgroundRestart();
      expect(linux.budget?['pending'], isTrue);
      expect(acts, isEmpty);
      await recreateOwner();
      await recovery.check(phone);
      expect(linux.confirmations, 1);
      expect(linux.directStarts, 0);
      expect(linux.backgroundStarts, 1);
      expect(acts, hasLength(1));
      expect(linux.budget?['pending'], isFalse);
      expect(recovery.value.phase, BuiltinRecoveryPhase.ready);
    },
  );

  test(
    'historical confirmed receipt survives later crash and records once',
    () async {
      recordsAllowed = false;
      await recovery.check(phone);
      final confirmedAt = now;
      final firstEvent = linux.budget!['eventId']! as String;
      expect(linux.budget?['confirmedAt'], confirmedAt.millisecondsSinceEpoch);
      recovery.setForeground(false);
      await Future<void>.delayed(Duration.zero);
      linux.running = false;
      linux.failStart = true;
      linux.backgroundRestart();
      expect(linux.receipts.single['eventId'], firstEvent);
      await recreateOwner();
      linux.wanted = false;
      await recovery.check(phone);
      expect(linux.receipts, hasLength(1));
      expect(linux.acknowledgements, isEmpty);
      recordsAllowed = true;
      now = now.add(const Duration(hours: 1));
      await recovery.check(phone);
      await recreateOwner();
      await recovery.check(phone);
      expect(acts.keys, [firstEvent]);
      expect(acts[firstEvent]!.toUtc(), confirmedAt);
      expect(linux.acknowledgements, [firstEvent]);
      expect(linux.receipts, isEmpty);
      expect(linux.attempts, 2);
    },
  );

  test(
    'profile deletion drains the active check and native erase before sweep',
    () async {
      linux.writeEntered = Completer<void>();
      linux.writeGate = Completer<void>();
      linux.deleteEntered = Completer<void>();
      linux.deleteGate = Completer<void>();
      final checking = recovery.check(phone);
      await linux.writeEntered!.future;
      var deleted = false;
      final deleting = BuiltinServerRecovery.suspendForProfile(
        prefs,
        phone.id,
      ).then((_) => deleted = true);
      linux.writeGate!.complete();
      await checking;
      await linux.deleteEntered!.future;
      expect(deleted, isFalse);
      expect(linux.directStarts, 0);
      expect(
        linux.calls.indexOf('password-written'),
        lessThan(linux.calls.indexOf('delete-entered')),
      );
      linux.deleteGate!.complete();
      await deleting;
      store.all.clear();
      await prefs.remove(BuiltinServerRecovery.keyFor(phone.id));
      await recovery.check(phone);
      expect(linux.budget, isNull);
      expect(linux.calls.last, 'deleted');
      expect(
        prefs.containsKey(BuiltinServerRecovery.keyFor(phone.id)),
        isFalse,
      );
      expect(linux.directStarts, 0);
    },
  );
}
