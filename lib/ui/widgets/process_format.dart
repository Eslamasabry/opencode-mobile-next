import '../../l10n/app_localizations.dart';

/// "42 s", "12 min", "3 h 5 min": how long a process on this phone ran.
String formatProcessDuration(AppLocalizations l10n, int seconds) {
  if (seconds < 60) return l10n.termuxProcsDurationSeconds(seconds);
  final minutes = seconds ~/ 60;
  if (minutes < 60) return l10n.termuxProcsDurationMinutes(minutes);
  return l10n.termuxProcsDurationHours(minutes ~/ 60, minutes % 60);
}

/// A CPU share as a short number: whole above 10 %, one decimal below.
String formatProcessCpuPct(double pct) =>
    pct >= 10 ? pct.round().toString() : pct.toStringAsFixed(1);
