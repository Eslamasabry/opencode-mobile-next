import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/scheduling/models.dart';
import 'package:opencode_mobile/scheduling/recurrence.dart';

ScheduledPrompt _schedule({MissedRunPolicy policy = MissedRunPolicy.catchUp}) =>
    ScheduledPrompt(
      id: 'morning',
      profileId: 'phone',
      profileFingerprint: 'route_digest',
      prompt: 'Review the open tasks',
      projectPath: '/project',
      createdAt: DateTime.utc(2026, 10, 15),
      recurrence: ScheduleRecurrence.interval(
        hours: 2,
        anchor: DateTime.utc(2026, 10, 15, 8),
      ),
      nextRunAt: DateTime.utc(2026, 10, 15, 8),
      missedPolicy: policy,
    );

void main() {
  test(
    'schedule roundtrip retains private target and explicit agent/model',
    () {
      final schedule = ScheduledPrompt(
        id: 'once',
        profileId: 'phone',
        profileFingerprint: 'route_digest',
        prompt: 'Review the open tasks',
        projectPath: '/project',
        workspace: 'worktree_2',
        sessionId: 'session_1',
        agent: 'build',
        modelProviderId: 'provider',
        modelId: 'model',
        recurrence: ScheduleRecurrence.once(DateTime.utc(2026, 10, 15, 8)),
        createdAt: DateTime.utc(2026, 10, 15),
        nextRunAt: DateTime.utc(2026, 10, 15, 8),
      );
      expect(
        ScheduledPrompt.fromJson(schedule.toJson()).toJson(),
        schedule.toJson(),
      );
      expect(
        ScheduledPromptDigest.fromJson(
          const ScheduledPromptDigest().toJson(),
        ).enabled,
        isFalse,
      );
    },
  );

  test('invalid recurrence and malformed stored schedules are rejected', () {
    expect(
      () => const ScheduleRecurrence.weekdays(hour: 24).validate(),
      throwsFormatException,
    );
    expect(
      () => ScheduleRecurrence.interval(
        hours: 0,
        anchor: DateTime.utc(2026),
      ).validate(),
      throwsFormatException,
    );
    expect(
      () => ScheduledPrompt.fromJson({..._schedule().toJson(), 'prompt': ' '}),
      throwsFormatException,
    );
    expect(
      () => ScheduledPrompt.fromJson({
        ..._schedule().toJson(),
        'profileId': '../other',
      }),
      throwsFormatException,
    );
  });

  test('once has no future occurrence after its scheduled instant', () {
    final at = DateTime.utc(2026, 10, 15, 8);
    final recurrence = ScheduleRecurrence.once(at);
    expect(
      nextScheduledOccurrence(
        recurrence,
        at.subtract(const Duration(seconds: 1)),
      ),
      at,
    );
    expect(nextScheduledOccurrence(recurrence, at), isNull);
  });

  test('intervals remain anchored after days of missed occurrences', () {
    final recurrence = _schedule().recurrence;
    expect(
      nextScheduledOccurrence(recurrence, DateTime.utc(2026, 10, 20, 9, 50)),
      DateTime.utc(2026, 10, 20, 10),
    );
    expect(
      nextScheduledOccurrence(recurrence, DateTime.utc(2026, 10, 15, 7)),
      DateTime.utc(2026, 10, 15, 8),
    );
  });

  test('weekday calendar skips weekend and exact current occurrence', () {
    const recurrence = ScheduleRecurrence.weekdays(hour: 8, minute: 30);
    // Local constructors intentionally keep this assertion timezone-independent.
    final friday = DateTime(2026, 10, 16, 8, 30);
    expect(
      nextScheduledOccurrence(recurrence, friday),
      DateTime(2026, 10, 19, 8, 30).toUtc(),
    );
    expect(
      nextScheduledOccurrence(
        recurrence,
        friday.subtract(const Duration(minutes: 1)),
      ),
      friday.toUtc(),
    );
  });

  test('every day runs on the weekend too and round-trips its rule', () {
    const recurrence = ScheduleRecurrence.daily(hour: 9);
    final friday = DateTime(2026, 10, 16, 9);
    expect(
      nextScheduledOccurrence(recurrence, friday),
      DateTime(2026, 10, 17, 9).toUtc(),
    );
    final back = ScheduleRecurrence.fromJson(recurrence.toJson());
    expect(back.kind, ScheduleRecurrenceKind.daily);
    expect((back.hour, back.minute), (9, 0));
  });

  test('catch-up collapses missed runs and marks only beyond grace late', () {
    final schedule = _schedule();
    final due = scheduledPromptDue(
      schedule,
      DateTime.utc(2026, 10, 20, 9, 50),
    )!;
    expect(due.scheduledAt, schedule.nextRunAt);
    expect(due.shouldRun, isTrue);
    expect(due.late, isTrue);
    expect(due.nextRunAt, DateTime.utc(2026, 10, 20, 10));
    expect(
      scheduledPromptDue(schedule, DateTime.utc(2026, 10, 15, 8, 15))!.late,
      isFalse,
    );
    expect(scheduledPromptDue(schedule, DateTime.utc(2026, 10, 15, 7)), isNull);
    expect(
      scheduledPromptDue(
        schedule.copyWith(enabled: false),
        DateTime.utc(2026, 10, 20),
      ),
      isNull,
    );
  });

  test('skip missed does not suppress on-time or within-grace occurrences', () {
    final schedule = _schedule(policy: MissedRunPolicy.skip);
    expect(
      scheduledPromptDue(
        schedule,
        DateTime.utc(2026, 10, 15, 8, 15),
      )!.shouldRun,
      isTrue,
    );
    final late = scheduledPromptDue(
      schedule,
      DateTime.utc(2026, 10, 15, 8, 16),
    )!;
    expect(late.shouldRun, isFalse);
    expect(late.nextRunAt, DateTime.utc(2026, 10, 15, 10));
  });

  test('occurrence identity includes revision and instant', () {
    final schedule = _schedule();
    final instant = schedule.nextRunAt!;
    final id = scheduledOccurrenceId(schedule, instant);
    expect(scheduledOccurrenceId(schedule, instant.toLocal()), id);
    expect(
      scheduledOccurrenceId(schedule.copyWith(revision: 2), instant),
      isNot(id),
    );
  });

  test('run error only accepts a fixed code and preserves late marker', () {
    final run = ScheduledPromptRun(
      id: 'morning:1:123',
      scheduleId: 'morning',
      scheduledAt: DateTime.utc(2026, 10, 15),
      startedAt: DateTime.utc(2026, 10, 16),
      status: ScheduledPromptRunStatus.failed,
      late: true,
      errorCode: 'uncertain_delivery',
    );
    expect(ScheduledPromptRun.fromJson(run.toJson()).toJson(), run.toJson());
    expect(
      () => ScheduledPromptRun.fromJson({
        ...run.toJson(),
        'errorCode': 'https://host/?secret=value',
      }),
      throwsFormatException,
    );
  });
}
