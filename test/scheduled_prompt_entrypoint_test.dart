import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/scheduling/entrypoint.dart';
import 'package:opencode_mobile/scheduling/models.dart';
import 'package:opencode_mobile/scheduling/platform.dart';
import 'package:opencode_mobile/scheduling/recurrence.dart';
import 'package:opencode_mobile/scheduling/runner.dart';
import 'package:opencode_mobile/scheduling/store.dart';

class _Backend implements ScheduledPromptBackend {
  final values = <String, String>{};
  @override
  Future<String?> read(String key) async => values[key];
  @override
  Future<bool> compareAndSet(String key, String? expected, String value) async {
    if (values[key] != expected) return false;
    values[key] = value;
    return true;
  }
}

class _Platform extends ScheduledPromptsPlatform {
  int syncs = 0;
  DateTime? digestAt;
  List<Map<String, Object?>> entries = [];
  @override
  Future<void> replaceProfile(
    String profileId,
    List<Map<String, Object?>> entries, {
    DateTime? digestAt,
  }) async {
    syncs++;
    this.entries = entries;
    this.digestAt = digestAt;
  }
}

class _Runner extends ScheduledPromptRunner {
  _Runner(this.clock) : super(loadProfile: (_) async => null);
  final DateTime Function() clock;
  int sends = 0;
  bool crashAfterDispatch = false;
  @override
  Future<ScheduledPromptRun> execute(
    ScheduledPrompt schedule, {
    required int deadlineEpochMs,
    required Future<bool> Function() isCurrent,
    Future<void> Function(String)? onSessionCreated,
  }) async {
    expect(await isCurrent(), isTrue);
    await onSessionCreated?.call('session_1');
    sends++;
    if (crashAfterDispatch) throw StateError('Simulated engine loss');
    return ScheduledPromptRun(
      id: scheduledOccurrenceId(schedule, schedule.nextRunAt!),
      scheduleId: schedule.id,
      scheduledAt: schedule.nextRunAt!,
      startedAt: clock(),
      finishedAt: clock(),
      status: ScheduledPromptRunStatus.ran,
      sessionId: 'session_1',
    );
  }
}

final _due = DateTime.utc(2026, 10, 15, 8);
ScheduledPrompt _schedule({MissedRunPolicy policy = MissedRunPolicy.catchUp}) =>
    ScheduledPrompt(
      id: 'morning',
      profileId: 'phone',
      profileFingerprint: 'route',
      prompt: 'Review tasks',
      projectPath: '/project',
      createdAt: _due,
      nextRunAt: _due,
      missedPolicy: policy,
      recurrence: ScheduleRecurrence.interval(hours: 2, anchor: _due),
    );
Map<String, dynamic> _args({bool recovery = false}) => {
  'profileId': 'phone',
  'scheduleId': 'morning',
  'revision': 1,
  'scheduledAtMs': _due.millisecondsSinceEpoch,
  'deadlineEpochMs': _due.add(const Duration(hours: 1)).millisecondsSinceEpoch,
  if (recovery) 'recoveryOnly': true,
};
ScheduledPromptRun _run(ScheduledPromptRunStatus status) => ScheduledPromptRun(
  id: scheduledOccurrenceId(_schedule(), _due),
  scheduleId: 'morning',
  scheduledAt: _due,
  startedAt: _due,
  status: status,
  finishedAt: status == ScheduledPromptRunStatus.running ? null : _due,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'engine loss retains the created conversation without resending',
    () async {
      final store = ScheduledPromptStore(backend: _Backend());
      final runner = _Runner(() => _due)..crashAfterDispatch = true;
      await store.save(_schedule());
      await expectLater(
        executeScheduledPrompt(
          _args(),
          store: store,
          platform: _Platform(),
          runner: runner,
          clock: () => _due,
        ),
        throwsStateError,
      );
      final pending = (await store.history('phone')).single;
      expect(pending.status, ScheduledPromptRunStatus.running);
      expect(pending.sessionId, 'session_1');
      final recovered = await executeScheduledPrompt(
        _args(recovery: true),
        store: store,
        platform: _Platform(),
        runner: runner,
        clock: () => _due.add(const Duration(minutes: 1)),
      );
      expect(recovered['error'], 'interrupted');
      expect(recovered['sessionId'], 'session_1');
      expect(runner.sends, 1);
    },
  );

  test(
    'accepted run records normal session and advances without repeating',
    () async {
      final store = ScheduledPromptStore(backend: _Backend());
      final platform = _Platform();
      final runner = _Runner(() => _due);
      await store.save(_schedule());
      final result = await executeScheduledPrompt(
        _args(),
        store: store,
        platform: platform,
        runner: runner,
        clock: () => _due,
      );
      expect(result['status'], 'ran');
      expect(result['sessionId'], 'session_1');
      expect(runner.sends, 1);
      expect((await store.history('phone')).single.sessionId, 'session_1');
      expect(
        (await store.load('phone')).single.nextRunAt,
        _due.add(const Duration(hours: 2)),
      );
      final repeat = await executeScheduledPrompt(
        _args(),
        store: store,
        platform: platform,
        runner: runner,
        clock: () => _due,
      );
      expect(repeat['status'], 'skipped');
      expect(runner.sends, 1);
    },
  );

  test('missed skip policy advances once and never calls runner', () async {
    final store = ScheduledPromptStore(backend: _Backend());
    final now = _due.add(const Duration(days: 3, minutes: 40));
    final runner = _Runner(() => now);
    await store.save(_schedule(policy: MissedRunPolicy.skip));
    final result = await executeScheduledPrompt(
      _args(),
      store: store,
      platform: _Platform(),
      runner: runner,
      clock: () => now,
    );
    expect(result['status'], 'skipped');
    expect(result['error'], 'missed');
    expect(result['late'], isTrue);
    expect(runner.sends, 0);
    expect((await store.history('phone')).length, 1);
    expect((await store.load('phone')).single.nextRunAt!.isAfter(now), isTrue);
  });

  test(
    'terminal duplicate receipt never sends and preserves original result',
    () async {
      final store = ScheduledPromptStore(backend: _Backend());
      final runner = _Runner(() => _due);
      await store.save(_schedule());
      await store.recordRun('phone', _run(ScheduledPromptRunStatus.ran));
      final result = await executeScheduledPrompt(
        _args(),
        store: store,
        platform: _Platform(),
        runner: runner,
        clock: () => _due,
      );
      expect(result['status'], 'ran');
      expect(runner.sends, 0);
      expect((await store.history('phone')).length, 1);
    },
  );

  test(
    'running occurrence is uncertain and cannot be dispatched twice',
    () async {
      final store = ScheduledPromptStore(backend: _Backend());
      final runner = _Runner(() => _due.add(const Duration(minutes: 2)));
      await store.save(_schedule());
      await store.recordRun('phone', _run(ScheduledPromptRunStatus.running));
      final result = await executeScheduledPrompt(
        _args(),
        store: store,
        platform: _Platform(),
        runner: runner,
        clock: runner.clock,
      );
      expect(result['status'], 'failed');
      expect(result['error'], 'interrupted');
      expect(runner.sends, 0);
      expect(
        (await store.history('phone')).single.status,
        ScheduledPromptRunStatus.failed,
      );
      expect((await store.history('phone')).single.startedAt, _due);
    },
  );

  test(
    'native interrupted recovery writes terminal history without dispatch',
    () async {
      final store = ScheduledPromptStore(backend: _Backend());
      final now = _due.add(const Duration(minutes: 2));
      final runner = _Runner(() => now);
      await store.save(_schedule());
      await store.recordRun('phone', _run(ScheduledPromptRunStatus.running));
      final result = await executeScheduledPrompt(
        _args(recovery: true),
        store: store,
        platform: _Platform(),
        runner: runner,
        clock: () => now,
      );
      expect(result['status'], 'failed');
      expect(result['error'], 'interrupted');
      expect(runner.sends, 0);
      final history = (await store.history('phone')).single;
      expect(history.status, ScheduledPromptRunStatus.failed);
      expect(history.startedAt, _due);
      expect(history.finishedAt, now);
    },
  );

  test('future and stale revision triggers never send', () async {
    final store = ScheduledPromptStore(backend: _Backend());
    final runner = _Runner(() => _due);
    await store.save(_schedule());
    final early = await executeScheduledPrompt(
      _args(),
      store: store,
      platform: _Platform(),
      runner: runner,
      clock: () => _due.subtract(const Duration(minutes: 1)),
    );
    expect(early['error'], 'not_due');
    final stale = await executeScheduledPrompt(
      {..._args(), 'revision': 99},
      store: store,
      platform: _Platform(),
      runner: runner,
      clock: () => _due,
    );
    expect(stale['error'], 'stale_target');
    expect(runner.sends, 0);
    expect(await store.history('phone'), isEmpty);
  });

  test('digest off performs no claim or notification summary', () async {
    final store = ScheduledPromptStore(backend: _Backend());
    final platform = _Platform();
    final result = await executeScheduledPrompt(
      {'profileId': 'phone', 'scheduleId': '__digest'},
      store: store,
      platform: platform,
      clock: () => _due,
    );
    expect(result, {'status': 'skipped'});
    expect((await store.digest('phone')).lastDeliveredAt, isNull);
    expect(platform.syncs, 0);
  });

  test(
    'digest counts only completed runs in window and claims cutoff once',
    () async {
      final store = ScheduledPromptStore(backend: _Backend());
      final platform = _Platform();
      await store.save(_schedule());
      final since = _due.subtract(const Duration(hours: 12));
      await store.setDigest(
        'phone',
        ScheduledPromptDigest(enabled: true, lastDeliveredAt: since),
      );
      final statuses = [
        ScheduledPromptRunStatus.ran,
        ScheduledPromptRunStatus.failed,
        ScheduledPromptRunStatus.skipped,
        ScheduledPromptRunStatus.running,
        ScheduledPromptRunStatus.ran,
        ScheduledPromptRunStatus.ran,
      ];
      for (var i = 0; i < statuses.length; i++) {
        final at = i == 4
            ? since.subtract(const Duration(minutes: 1))
            : i == 5
            ? _due.add(const Duration(minutes: 1))
            : _due.subtract(const Duration(hours: 1));
        await store.recordRun(
          'phone',
          ScheduledPromptRun(
            id: 'run_$i',
            scheduleId: 'morning',
            scheduledAt: at,
            startedAt: at,
            finishedAt: statuses[i] == ScheduledPromptRunStatus.running
                ? null
                : at,
            status: statuses[i],
          ),
        );
      }
      final args = {'profileId': 'phone', 'scheduleId': '__digest'};
      final result = await executeScheduledPrompt(
        args,
        store: store,
        platform: platform,
        clock: () => _due,
      );
      expect(result, {'status': 'digest', 'ran': 1, 'failed': 1, 'skipped': 1});
      expect((await store.digest('phone')).lastDeliveredAt, _due);
      expect(platform.digestAt!.isAfter(_due), isTrue);
      final repeat = await executeScheduledPrompt(
        args,
        store: store,
        platform: platform,
        clock: () => _due,
      );
      expect(repeat, {'status': 'skipped'});
    },
  );
}
