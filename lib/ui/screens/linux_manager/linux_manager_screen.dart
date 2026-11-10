/// This phone › Linux: the hub for managing the app's own Ubuntu (owner
/// request 2026-10-16). One list of what can be done, each row opening its
/// own page: what runs, ports, storage, packages; then Repair and Reset,
/// which sit last. A package job in progress is shown on top of every page
/// that can start one, and survives leaving them.
///
/// For the in-app host only (the caller gates on the host kind). Termux
/// keeps its own processes and storage pages.
///
/// Built from kit parts only: [KitScreen], [KitRowGroup], [KitRow].
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../builtin/linux_manager/linux_manager.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import 'linux_manager_common.dart';
import 'linux_packages_page.dart';
import 'linux_ports_page.dart';
import 'linux_processes_page.dart';
import 'linux_reset.dart';
import 'linux_storage_page.dart';

/// Opens the Linux manager. [onRepair] runs phone setup's check-and-repair
/// (the caller owns that flow); it is called after the question is
/// confirmed.
Future<void> openLinuxManager(
  BuildContext context, {
  required Future<void> Function() onRepair,
}) => pushKitPage<void>(
  context,
  (_) => LinuxManagerScreen(onRepair: onRepair),
  settings: const RouteSettings(name: 'linux-manager'),
);

class LinuxManagerScreen extends ConsumerStatefulWidget {
  const LinuxManagerScreen({super.key, required this.onRepair});

  final Future<void> Function() onRepair;

  @override
  ConsumerState<LinuxManagerScreen> createState() => _LinuxManagerScreenState();
}

class _LinuxManagerScreenState extends ConsumerState<LinuxManagerScreen> {
  Future<void> _repair() async {
    final l10n = AppLocalizations.of(context);
    try {
      await ref.read(linuxManagerProvider).requireQuiet();
    } on LinuxManagerException catch (error) {
      if (!mounted) return;
      await showKitAlert(
        context,
        title: l10n.linuxMgrRepairTitle,
        body: linuxProblemText(l10n, error),
        icon: AppIconography.warning,
        alertKey: const ValueKey('linux-repair-blocked'),
      );
      return;
    }
    if (!mounted) return;
    final yes = await showKitConfirm(
      context,
      title: l10n.linuxMgrRepairTitle,
      body: l10n.linuxMgrRepairBody,
      confirmLabel: l10n.linuxMgrRepair,
      icon: AppIconography.tools,
      consequenceItems: [
        KitConsequence(l10n.linuxMgrRepairKept, mark: KitConsequenceMark.kept),
      ],
      sheetKey: const ValueKey('linux-repair-sheet'),
      confirmKey: const ValueKey('linux-repair-confirm'),
    );
    if (yes && mounted) await widget.onRepair();
  }

  Future<void> _reset() async {
    final done = await resetLinux(context, ref);
    // A reset ends this page's purpose: Linux is gone until setup is done.
    if (done && mounted) Navigator.of(context).maybePop();
  }

  Future<void> _open(Widget page, String name) => pushKitPage<void>(
    context,
    (_) => page,
    settings: RouteSettings(name: name),
  );

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    Widget icon(IconData data) => KitRow.icon(context, data);
    return KitScreen(
      topBar: KitTopBar(title: l10n.linuxMgrTitle),
      width: KitScreenWidth.reading,
      body: ListView(
        key: const ValueKey('linux-manager-list'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          const LinuxJobPanel(),
          KitRowGroup(
            children: [
              KitRow(
                key: const ValueKey('linux-row-running'),
                leading: icon(AppIconography.processor),
                title: l10n.linuxMgrRunning,
                supporting: TextSpan(text: l10n.linuxMgrRunningDetail),
                supportingMaxLines: 2,
                trailing: const KitChevron(),
                onTap: () =>
                    _open(const LinuxProcessesPage(), 'linux-processes'),
              ),
              KitRow(
                key: const ValueKey('linux-row-ports'),
                leading: icon(AppIconography.network),
                title: l10n.linuxMgrPorts,
                supporting: TextSpan(text: l10n.linuxMgrPortsDetail),
                supportingMaxLines: 2,
                trailing: const KitChevron(),
                onTap: () => _open(const LinuxPortsPage(), 'linux-ports'),
              ),
              KitRow(
                key: const ValueKey('linux-row-storage'),
                leading: icon(AppIconography.database),
                title: l10n.linuxMgrStorage,
                supporting: TextSpan(text: l10n.linuxMgrStorageDetail),
                supportingMaxLines: 2,
                trailing: const KitChevron(),
                onTap: () => _open(const LinuxStoragePage(), 'linux-storage'),
              ),
              KitRow(
                key: const ValueKey('linux-row-packages'),
                leading: icon(AppIconography.package),
                title: l10n.linuxMgrPackages,
                supporting: TextSpan(text: l10n.linuxMgrPackagesDetail),
                supportingMaxLines: 2,
                trailing: const KitChevron(),
                onTap: () => _open(const LinuxPackagesPage(), 'linux-packages'),
              ),
              KitRow(
                key: const ValueKey('linux-row-repair'),
                leading: icon(AppIconography.tools),
                title: l10n.linuxMgrRepair,
                supporting: TextSpan(text: l10n.linuxMgrRepairDetail),
                supportingMaxLines: 3,
                trailing: const KitChevron(),
                onTap: () => unawaited(_repair()),
              ),
              // Last, and never red (owner rule, LOOK-5): the typed
              // question is what guards it, as on This phone's Remove.
              KitRow(
                key: const ValueKey('linux-row-reset'),
                leading: icon(AppIconography.restart),
                title: l10n.linuxMgrReset,
                supporting: TextSpan(text: l10n.linuxMgrResetDetail),
                supportingMaxLines: 2,
                onTap: () => unawaited(_reset()),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
