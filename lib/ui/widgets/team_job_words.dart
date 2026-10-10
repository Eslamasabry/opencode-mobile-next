/// A scheduled job's "when" in plain words ("Every day at 09:00"), never a
/// cron expression or a duration string. A schedule too unusual to say simply
/// reads "On a schedule"; the job's own page text is the host's.
library;

import 'package:intl/intl.dart';

import '../../domain/orchestration_gateway.dart';
import '../../l10n/app_localizations.dart';

/// When [job] runs, in the person's words.
String teamJobWhen(AppLocalizations l10n, ScheduledJob job) =>
    switch (job.trigger) {
      ScheduledJobTrigger.cron => _cron(l10n, job.schedule),
      ScheduledJobTrigger.interval => _interval(l10n, job.interval),
      ScheduledJobTrigger.event => l10n.teamJobWhenEvent,
      ScheduledJobTrigger.manual => l10n.teamJobManual,
      ScheduledJobTrigger.webhook => l10n.teamJobWebhook,
      ScheduledJobTrigger.condition => l10n.teamJobCondition,
      ScheduledJobTrigger.other => l10n.teamJobOther,
    };

String _two(int n) => n.toString().padLeft(2, '0');

String _cron(AppLocalizations l10n, String? expression) {
  final fields = expression?.trim().split(RegExp(r'\s+'));
  if (fields == null || fields.length != 5) return l10n.teamJobOnSchedule;
  final [minute, hour, dayOfMonth, month, dayOfWeek] = fields;
  int? number(String text) => int.tryParse(text);
  final m = number(minute), h = number(hour);
  final everyDay = dayOfMonth == '*' && month == '*';
  if (!everyDay) return l10n.teamJobOnSchedule;
  final step = RegExp(r'^\*/(\d+)$');
  if (hour == '*' && dayOfWeek == '*') {
    final every = step.firstMatch(minute);
    if (every != null && number(every.group(1)!) != null) {
      final n = number(every.group(1)!)!;
      if (n > 0) return l10n.teamJobEveryMinutes(n);
    }
    if (m == 0) return l10n.teamJobEveryHour;
    if (minute == '*') return l10n.teamJobEveryMinutes(1);
  }
  final everyHours = step.firstMatch(hour);
  if (m == 0 && everyHours != null && dayOfWeek == '*') {
    final n = number(everyHours.group(1)!);
    if (n != null && n > 0) return l10n.teamJobEveryHours(n);
  }
  if (m == null || h == null || m > 59 || h > 23) {
    return l10n.teamJobOnSchedule;
  }
  final time = '${_two(h)}:${_two(m)}';
  if (dayOfWeek == '*') return l10n.teamJobEveryDayAt(time);
  if (dayOfWeek == '1-5') return l10n.teamJobEveryWeekdayAt(time);
  final day = number(dayOfWeek);
  if (day == null || day < 0 || day > 7) return l10n.teamJobOnSchedule;
  // 2021-01-03 was a Sunday; 0 and 7 are both Sunday in cron.
  try {
    final name = DateFormat.EEEE(
      l10n.localeName,
    ).format(DateTime.utc(2021, 1, 3 + (day % 7)));
    return l10n.teamJobEveryDowAt(name, time);
  } on Object {
    // No day names for this language yet: the plain fallback.
    return l10n.teamJobOnSchedule;
  }
}

String _interval(AppLocalizations l10n, String? text) {
  final match = RegExp(r'^\s*(\d+)\s*(s|m|h)\s*$').firstMatch(text ?? '');
  if (match == null) return l10n.teamJobRepeats;
  final n = int.parse(match.group(1)!);
  if (n == 0) return l10n.teamJobRepeats;
  return switch (match.group(2)) {
    's' => l10n.teamJobEverySeconds(n),
    'm' => l10n.teamJobEveryMinutes(n),
    _ => l10n.teamJobEveryHours(n),
  };
}
