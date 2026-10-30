import 'models.dart';

/// A delayed alarm is late only after the operating system's 15-minute grace.
const scheduledPromptLateTolerance = Duration(minutes: 15);

/// Finds one occurrence strictly after [after]. Once schedules finish at null.
/// Intervals retain an absolute anchor, so delayed work does not drift.
/// Daily and weekday schedules follow the device's local calendar, including
/// timezone and daylight-saving changes.
DateTime? nextScheduledOccurrence(
  ScheduleRecurrence recurrence,
  DateTime after,
) {
  recurrence.validate();
  switch (recurrence.kind) {
    case ScheduleRecurrenceKind.once:
      return recurrence.at!.isAfter(after) ? recurrence.at!.toUtc() : null;
    case ScheduleRecurrenceKind.interval:
      final anchor = recurrence.at!.toUtc();
      if (anchor.isAfter(after)) return anchor;
      final period = Duration(hours: recurrence.hours).inMicroseconds;
      final elapsed = after.toUtc().difference(anchor).inMicroseconds;
      return anchor.add(
        Duration(microseconds: (elapsed ~/ period + 1) * period),
      );
    case ScheduleRecurrenceKind.daily:
    case ScheduleRecurrenceKind.weekdays:
      final weekdaysOnly = recurrence.kind == ScheduleRecurrenceKind.weekdays;
      final local = after.toLocal();
      for (var offset = 0; offset <= 7; offset++) {
        // Calendar construction, not adding 24 hours, preserves wall-clock time
        // when a daylight-saving transition changes the length of a local day.
        final candidate = DateTime(
          local.year,
          local.month,
          local.day + offset,
          recurrence.hour,
          recurrence.minute,
        );
        if ((!weekdaysOnly || candidate.weekday <= DateTime.friday) &&
            candidate.isAfter(after)) {
          return candidate.toUtc();
        }
      }
      throw StateError('No next weekday');
  }
}

String scheduledOccurrenceId(ScheduledPrompt schedule, DateTime scheduledAt) =>
    '${schedule.id}:${schedule.revision}:${scheduledAt.toUtc().millisecondsSinceEpoch}';

class ScheduledPromptDue {
  const ScheduledPromptDue({
    required this.scheduledAt,
    required this.late,
    required this.shouldRun,
    required this.nextRunAt,
  });
  final DateTime scheduledAt;
  final bool late;
  final bool shouldRun;
  final DateTime? nextRunAt;
}

/// Collapses all missed occurrences into one catch-up or one skipped record.
/// The caller durably claims its identity before any network mutation.
ScheduledPromptDue? scheduledPromptDue(ScheduledPrompt schedule, DateTime now) {
  final due = schedule.nextRunAt;
  if (!schedule.enabled || due == null || due.isAfter(now)) return null;
  final late = now.difference(due) > scheduledPromptLateTolerance;
  return ScheduledPromptDue(
    scheduledAt: due,
    late: late,
    shouldRun: !late || schedule.missedPolicy == MissedRunPolicy.catchUp,
    nextRunAt: nextScheduledOccurrence(schedule.recurrence, now),
  );
}
