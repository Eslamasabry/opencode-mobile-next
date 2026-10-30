import 'package:intl/intl.dart';

import '../../../l10n/app_localizations.dart';
import '../../../scheduling/models.dart';

String scheduleErrorCopy(AppLocalizations l, String? code) => switch (code) {
  'busy' => l.scheduleBusy,
  'needs_you' || 'permission_required' => l.scheduleNeedsYou,
  'auth_required' => l.scheduleSignInAgain,
  'cleartext_unconfirmed' => l.scheduleAllowHttp,
  'agent_failed' => l.scheduleAgentFailed,
  'certificate_changed' => l.scheduleCertificateChanged,
  'certificate_invalid' => l.scheduleCertificateInvalid,
  'stale_target' || 'wrong_project' => l.scheduleTargetChanged,
  'interrupted' => l.scheduleInterrupted,
  'timeout' => l.scheduleTimeout,
  'uncertain_delivery' => l.scheduleUncertain,
  'missed' => l.scheduleMissed,
  'unsupported' => l.scheduleUnsupported,
  _ => l.scheduleUnavailable,
};

/// An agent's name for people: "build" reads "Build", "claude-code" reads
/// "Claude Code". Names that already carry capitals are left alone.
String scheduleAgentLabel(String name, [String? locale]) {
  final trimmed = name.trim();
  if (trimmed.isEmpty || trimmed != trimmed.toLowerCase()) return trimmed;
  return trimmed
      .split(RegExp(r'[-_\s]+'))
      .where((word) => word.isNotEmpty)
      .map((word) => toBeginningOfSentenceCase(word, locale))
      .join(' ');
}

/// A moment the way a person says it: "in 5 min", "Today, 3:10 AM",
/// "Tomorrow, 8:00 AM", "Mon, 8:00 AM", "Oct 28, 8:00 AM". The year appears
/// only for moments more than about six months away (or past).
String scheduleWhen(AppLocalizations l, DateTime moment, DateTime now) {
  final t = moment.toLocal();
  final n = now.toLocal();
  final clock = DateFormat.jm(l.localeName).format(t);
  final diff = t.difference(n);
  if (!diff.isNegative && diff < const Duration(minutes: 60)) {
    return l.scheduleInMinutes(
      '${diff.inMinutes < 1 ? 1 : diff.inMinutes + 1}',
    );
  }
  final days = DateTime(
    t.year,
    t.month,
    t.day,
  ).difference(DateTime(n.year, n.month, n.day)).inDays;
  if (days == 0) return l.scheduleToday(clock);
  if (days == 1) return l.scheduleTomorrow(clock);
  if (days > 1 && days < 7) {
    return l.scheduleDayAt(DateFormat.E(l.localeName).format(t), clock);
  }
  if ((days >= 7 || days < 0) && days.abs() < 183) {
    return l.scheduleDayAt(DateFormat.MMMd(l.localeName).format(t), clock);
  }
  return l.scheduleDayAt(DateFormat.yMMMd(l.localeName).format(t), clock);
}

/// A wall-clock time the way the person's locale writes it: "9:00 AM".
String scheduleClock(AppLocalizations l, int hour, int minute) =>
    DateFormat.jm(l.localeName).format(DateTime(2000, 1, 1, hour, minute));

/// How a schedule repeats, in plain words: "Every weekday at 9:00 AM",
/// "Every day at 9:00 AM", "Every 3 hours", or, for a one-time schedule,
/// its moment ("Tomorrow, 9:00 AM").
String scheduleRule(AppLocalizations l, ScheduleRecurrence r, DateTime now) =>
    switch (r.kind) {
      ScheduleRecurrenceKind.daily => l.scheduleRuleDaily(
        scheduleClock(l, r.hour, r.minute),
      ),
      ScheduleRecurrenceKind.weekdays => l.scheduleRuleWeekdays(
        scheduleClock(l, r.hour, r.minute),
      ),
      ScheduleRecurrenceKind.interval => l.scheduleRuleHours(r.hours),
      ScheduleRecurrenceKind.once => scheduleWhen(l, r.at!, now),
    };

/// The day of a past moment, said inside a sentence: "today", "yesterday",
/// "Mon", "Oct 12".
String scheduleDay(AppLocalizations l, DateTime moment, DateTime now) {
  final t = moment.toLocal();
  final n = now.toLocal();
  final days = DateTime(
    n.year,
    n.month,
    n.day,
  ).difference(DateTime(t.year, t.month, t.day)).inDays;
  if (days <= 0) return l.scheduleDayToday;
  if (days == 1) return l.scheduleDayYesterday;
  if (days < 7) return DateFormat.E(l.localeName).format(t);
  return DateFormat.MMMd(l.localeName).format(t);
}

/// What the last run did, honestly: "Sent yesterday" (the server took the
/// prompt), "Didn't go out today", "Skipped Mon", "Sending now".
String scheduleLastResult(
  AppLocalizations l,
  ScheduledPromptRun run,
  DateTime now,
) {
  final day = scheduleDay(l, run.finishedAt ?? run.scheduledAt, now);
  return switch (run.status) {
    ScheduledPromptRunStatus.ran => l.scheduleSent(day),
    ScheduledPromptRunStatus.failed => l.scheduleNotSent(day),
    ScheduledPromptRunStatus.skipped => l.scheduleSkippedWhen(day),
    ScheduledPromptRunStatus.running => l.scheduleSendingNow,
  };
}

/// A schedule's prompt as its name: the first line with words.
String scheduleName(AppLocalizations l, ScheduledPrompt s) {
  final first = s.prompt
      .split('\n')
      .map((line) => line.trim())
      .firstWhere((line) => line.isNotEmpty, orElse: () => '');
  return first.isEmpty ? l.schedulePrompt : first;
}

/// The project's own name: the last part of its folder.
String scheduleProjectName(String path) {
  final parts = path.split(RegExp(r'[/\\]')).where((p) => p.isNotEmpty);
  return parts.isEmpty ? path : parts.last;
}
