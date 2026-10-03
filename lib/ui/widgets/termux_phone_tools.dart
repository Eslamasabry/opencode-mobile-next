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
import '../../termux/processes.dart';
import '../../termux/storage.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../screens/termux_processes_screen.dart';
import '../screens/termux_storage_screen.dart';

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
