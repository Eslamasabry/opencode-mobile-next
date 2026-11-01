/// The process budget's one line in the app-wide status slot: new heavy
/// work waits (no room under the app's ceiling, a warm phone, a low
/// battery) and starts by itself. No new bar: it is a status-slot
/// condition like heat, shown on every page while something waits.
library;

import 'package:flutter/material.dart';

import '../../builtin/process_budget.dart';
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

/// The words of the waiting line for [guard], or null when nothing waits.
String? processBudgetLine(AppLocalizations l10n, ProcessBudgetGuard guard) =>
    switch (guard.wait) {
      null => null,
      BudgetWait.warm => l10n.processBudgetWaitWarm,
      BudgetWait.battery => l10n.processBudgetWaitBattery,
      BudgetWait.room => [
        l10n.processBudgetWaitRoom(guard.lastCeiling),
        if (guard.runningAgents > 0)
          l10n.processBudgetAgentsRunning(guard.runningAgents),
      ].join(' '),
    };

/// The waiting line as a status-slot condition; [onOpen] opens Running on
/// this phone (what runs, and what can be stopped).
KitStatus? processBudgetKitStatus(
  BuildContext context,
  ProcessBudgetGuard guard, {
  VoidCallback? onOpen,
}) {
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  final line = processBudgetLine(l10n, guard);
  final wait = guard.wait;
  if (line == null || wait == null) return null;
  return KitStatus(
    // A working line: nothing is wrong, the work is held and starts by
    // itself. Neutral, as heat is (amber means "needs you" only).
    kind: KitStatusKind.work,
    id: 'budget:${wait.name}',
    key: ValueKey('process-budget-${wait.name}'),
    icon: switch (wait) {
      BudgetWait.room => AppIconography.processor,
      BudgetWait.warm => AppIconography.warning,
      BudgetWait.battery => AppIconography.batteryWarning,
    },
    tone: AppStatusTone.neutral,
    message: line,
    semanticsLabel: line,
    action: wait == BudgetWait.room && onOpen != null
        ? KitAction(
            key: const ValueKey('process-budget-open'),
            label: l10n.processBudgetSeeRunning,
            onPressed: onOpen,
          )
        : null,
  );
}
