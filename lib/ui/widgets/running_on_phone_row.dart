/// This phone's "Running on this phone" row (in-app Linux): opens the page,
/// and its line is the one status about what runs, in the row itself (no
/// new bar): what was left behind, what stays heavy, or Android's limit
/// coming close; otherwise how many run and their memory.
///
/// This phone reads it when it opens and every 15 seconds while it is the
/// page in front; nothing reads in the background.
library;

import 'package:flutter/material.dart';

import '../../builtin/phone_process_watch.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../kit/kit.dart';
import '../screens/termux_processes_screen.dart' show formatTermuxCpuPct;
import 'phone_server_card.dart' show formatPhoneStorage;
import 'running_on_phone_text.dart';

class RunningOnPhoneRow extends StatelessWidget {
  const RunningOnPhoneRow({
    super.key,
    required this.reading,
    required this.onTap,
  });

  /// The latest reading; the page that holds the row leaves the row out
  /// until there is one with something in it (no dead row).
  final PhoneProcessReading reading;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final look = reading.needsLook || reading.nearLimit;
    return KitRow(
      // Heat is neutral with a warning glyph; amber means "needs you" only.
      leading: KitRow.icon(
        context,
        look ? AppIconography.warning : AppIconography.heartbeat,
      ),
      title: l10n.runningPhoneTitle,
      supporting: TextSpan(
        text: runningRowStatus(l10n, reading) ?? l10n.runningPhoneRowIdle,
        // At label weight when it needs a look; the words say what.
        style: look ? KitText.styleOf(context, KitTextRole.label) : null,
      ),
      supportingKey: const Key('running-row-status'),
      supportingMaxLines: 2,
      trailing: const KitChevron(),
      onTap: onTap,
    );
  }
}

/// The row's line for [reading], the most pressing first; null when the
/// reading holds nothing (a phone that cannot list its processes).
String? runningRowStatus(AppLocalizations l10n, PhoneProcessReading reading) {
  final left = reading.leftBehind;
  if (left.isNotEmpty) return l10n.runningPhoneRowLeftBehind(left.length);
  final heavy = reading.heavy;
  if (heavy.isNotEmpty) {
    final top = heavy.first.process;
    return l10n.runningPhoneRowHeavy(
      heavy.length,
      runningProcessName(l10n, top),
      formatTermuxCpuPct(top.cpuPercent),
    );
  }
  if (reading.nearLimit) {
    return l10n.runningPhoneRowNearLimit(
      reading.childCount,
      PhoneProcessWatch.androidLimit,
    );
  }
  final count = reading.snapshot.processes.length;
  if (count == 0) return null;
  return l10n.runningPhoneRowNormal(
    count,
    formatPhoneStorage(reading.rssBytes),
  );
}
