/// This phone's Termux rows, Storage and Running on this phone
/// (TEAM-304/305, P5.3), and the watcher behind the Work tab's "OpenCode
/// has been busy" line.
///
/// Each reads through `~/.oc/tools.sh` and swallows every bridge failure: a
/// phone without the tools installed simply shows no numbers (or, for the
/// process list, no row), and a desktop build never gets here (the callers
/// gate on the Termux platform).
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../termux/bridge.dart';
import '../../termux/processes.dart';
import '../../termux/storage.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../screens/termux_processes_screen.dart';
import '../screens/termux_storage_screen.dart';
import 'work_status_line.dart'
    show RunawayStopOutcome, WorkRunawayNotice, stopRunawayHelper;

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// Termux's Storage row on This phone, with its live summary ("11.6 GB
/// used"); opens Storage and reads again on the way back.
class TermuxStorageRow extends StatefulWidget {
  const TermuxStorageRow({super.key});

  @override
  State<TermuxStorageRow> createState() => _TermuxStorageRowState();
}

class _TermuxStorageRowState extends State<TermuxStorageRow> {
  TermuxStorageSummary? _storage;
  bool _storageFailed = false;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  Future<void> _load() async {
    try {
      final summary = await TermuxStorage.summary();
      if (mounted) setState(() => _storage = summary);
    } catch (_) {
      if (mounted) setState(() => _storageFailed = true);
    }
  }

  Future<void> _open() async {
    await pushKitPage<void>(context, (_) => const TermuxStorageScreen());
    if (mounted) unawaited(_load());
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    final storage = _storage;
    final storageSubtitle = _storageFailed
        ? l10n.termuxStorageRowNotScanned
        : storage == null
        ? l10n.termuxProcsRowLoading
        : storage.state == TermuxStorageScanState.running
        ? l10n.termuxStorageRowScanning
        : storage.totalBytes == null
        ? l10n.termuxStorageRowNotScanned
        : l10n.termuxStorageRowUsed(
            formatTermuxBytes(l10n, storage.totalBytes!),
          );
    return KitRow(
      key: const Key('termux-storage-row'),
      leading: KitRow.icon(context, AppIconography.database),
      title: l10n.thisPhoneStorage,
      supporting: TextSpan(text: storageSubtitle),
      supportingKey: const Key('termux-storage-row-subtitle'),
      trailing: const KitChevron(),
      onTap: () => unawaited(_open()),
    );
  }
}

/// The Running on this phone row (P5.3): the budget as its summary ("18 of
/// 32 background processes"), "Checking…" until the first reading.
///
/// The page that holds it reads the list, so that a list that cannot be
/// read leaves the row out of its group altogether (no dead "Not available
/// right now" row, and no stray hairline where it was).
class PhoneProcessesRow extends StatelessWidget {
  const PhoneProcessesRow({
    super.key,
    required this.report,
    required this.onTap,
  });

  /// Null while the first reading runs.
  final TermuxProcessReport? report;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    final report = this.report;
    return KitRow(
      key: const Key('termux-procs-row'),
      leading: KitRow.icon(context, AppIconography.processor),
      title: l10n.termuxProcsTitle,
      supporting: TextSpan(
        text: report == null
            ? l10n.termuxProcsRowLoading
            : phoneProcessBudget(l10n, report),
      ),
      supportingKey: const Key('termux-procs-row-subtitle'),
      trailing: const KitChevron(),
      onTap: onTap,
    );
  }
}

/// The project a leftover process belongs to, by its working folder: the
/// first folder under a `projects` folder (`/root/projects/FinanceHub/src`
/// is FinanceHub). Null for the projects folder itself, a home folder or
/// anything else, which the line calls "OpenCode" rather than show a path.
String? runawayProjectName(String cwd) {
  final parts = cwd.split('/').where((part) => part.isNotEmpty).toList();
  final index = parts.lastIndexOf('projects');
  if (index < 0 || index + 1 >= parts.length) return null;
  final name = parts[index + 1];
  return name.startsWith('.') ? null : name;
}

/// Watches the managed phone server for a helper that has burned more than
/// [threshold] of CPU with nothing waiting on it, and hands the worst one to
/// [builder] as a [WorkRunawayNotice] (null when there is none, or when the
/// person dismissed this very process). Polls every [interval] while
/// mounted; a phone without the tools simply never reports one.
class TermuxRunawayWatcher extends StatefulWidget {
  const TermuxRunawayWatcher({
    super.key,
    required this.builder,
    this.interval = const Duration(seconds: 60),
    this.threshold = const Duration(minutes: 10),
    this.scan,
    this.stop,
  });

  final Widget Function(BuildContext context, WorkRunawayNotice? notice)
  builder;
  final Duration interval;
  final Duration threshold;

  /// Test seam; defaults to [TermuxProcesses.scan] behind the bridge check.
  final Future<TermuxProcessReport> Function()? scan;

  /// Test seam; defaults to [TermuxProcesses.stopPid].
  final Future<TermuxProcessStopResult> Function(int pid)? stop;

  /// Dismissed processes, by pid and name, for the life of the app: the line
  /// comes back only for a different process.
  static final _dismissed = <(int, String)>{};

  @visibleForTesting
  static void resetDismissedForTesting() => _dismissed.clear();

  @override
  State<TermuxRunawayWatcher> createState() => _TermuxRunawayWatcherState();
}

class _TermuxRunawayWatcherState extends State<TermuxRunawayWatcher> {
  TermuxProcess? _worst;
  Timer? _timer;

  /// The process whose last stop did not end it.
  (int, String)? _stopFailed;

  @override
  void initState() {
    super.initState();
    unawaited(_check());
    _timer = Timer.periodic(widget.interval, (_) => unawaited(_check()));
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _check() async {
    final scan = widget.scan;
    if (scan == null && !TermuxBridge.supported) return;
    try {
      final report = await (scan ?? TermuxProcesses.scan)();
      final orphans = report.orphansOver(widget.threshold)
        ..sort((a, b) => b.cpuSeconds.compareTo(a.cpuSeconds));
      if (mounted) {
        setState(() => _worst = orphans.isEmpty ? null : orphans.first);
      }
    } catch (_) {
      // No tools, no Termux, or a bridge hiccup: the line simply stays away.
      if (mounted && _worst != null) setState(() => _worst = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final worst = _worst;
    final identity = worst == null ? null : (worst.pid, worst.name);
    if (worst == null || TermuxRunawayWatcher._dismissed.contains(identity)) {
      return widget.builder(context, null);
    }
    final l10n = _copy(context);
    return widget.builder(
      context,
      WorkRunawayNotice(
        identity: identity!,
        helper: worst.name,
        project: runawayProjectName(worst.cwd),
        busyFor: formatTermuxDuration(l10n, worst.cpuSeconds),
        stopFailed: _stopFailed == identity,
        onDismiss: () =>
            setState(() => TermuxRunawayWatcher._dismissed.add(identity)),
        onStop: () => unawaited(_stop(worst, identity)),
      ),
    );
  }

  /// Asks, stops exactly this process, and hides the line at once when it
  /// ended (the next scan confirms); a stop that failed keeps the line in
  /// its failure words.
  Future<void> _stop(TermuxProcess process, (int, String) identity) async {
    final outcome = await stopRunawayHelper(
      context,
      pid: process.pid,
      helper: process.name,
      stop: widget.stop,
    );
    if (!mounted || outcome == RunawayStopOutcome.kept) return;
    setState(() {
      if (outcome == RunawayStopOutcome.stopped) {
        TermuxRunawayWatcher._dismissed.add(identity);
        _stopFailed = null;
      } else {
        _stopFailed = identity;
      }
    });
    unawaited(_check());
  }
}
