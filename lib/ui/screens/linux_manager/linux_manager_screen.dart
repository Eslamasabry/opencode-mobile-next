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
import 'package:shared_preferences/shared_preferences.dart';

import '../../../builtin/linux_backup/linux_backup.dart'
    show linuxLastBackupKey;
import '../../../builtin/linux_manager/linux_manager.dart';
import '../../../builtin/linux_services/linux_services.dart';
import '../../../builtin/linux_vitals.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import 'linux_backups_page.dart';
import 'linux_manager_common.dart';
import 'linux_packages_page.dart';
import 'linux_ports_page.dart';
import 'linux_processes_page.dart';
import 'linux_reset.dart';
import 'linux_services_common.dart';
import 'linux_services_page.dart';
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
  /// The services, for the Services row's line. Null until read, and after
  /// a read that failed: the row then says what the page is for.
  List<LinuxServiceState>? _services;

  /// The vitals on top: read every 5 s while this page is open and on top,
  /// never otherwise (S2a).
  late final LinuxVitalsWatch _vitals;
  ModalRoute<Object?>? _route;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _route = ModalRoute.of(context);
  }

  @override
  void initState() {
    super.initState();
    unawaited(_readServices());
    unawaited(_readLastBackup());
    _vitals = LinuxVitalsWatch(
      ref.read(linuxVitalsSourceProvider),
      visible: () => mounted && (_route?.isCurrent ?? true),
    );
    // After the first frame: the page's route is known by then.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _vitals.start();
    });
  }

  @override
  void dispose() {
    _vitals.dispose();
    super.dispose();
  }

  Widget _vitalsStrip(BuildContext context, AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    return ListenableBuilder(
      listenable: _vitals,
      builder: (context, _) {
        final v = _vitals.vitals;
        final heat = v?.heat;
        final heatWord = heat == null ? null : linuxHeatWord(l10n, heat);
        final cells = <KitVital>[
          if (v == null || heatWord != null)
            KitVital(
              value: heatWord ?? '',
              word: l10n.linuxVitalsHeat,
              semanticsLabel: l10n.linuxVitalsHeatLabel(heatWord ?? ''),
            ),
          if (v == null || v.phoneMemoryFreeBytes != null)
            KitVital(
              value: linuxSize(v?.phoneMemoryFreeBytes ?? 0),
              word: l10n.linuxVitalsMemoryFree,
              semanticsLabel: l10n.linuxVitalsMemoryLabel(
                linuxSize(v?.phoneMemoryFreeBytes ?? 0),
              ),
            ),
          if (v == null || v.linuxCpuPercent != null)
            KitVital(
              value: '${(v?.linuxCpuPercent ?? 0).round()}%',
              word: l10n.linuxVitalsLinuxCpu,
              semanticsLabel: l10n.linuxVitalsCpuLabel(
                '${(v?.linuxCpuPercent ?? 0).round()}%',
              ),
            ),
        ];
        final summary = v == null ? null : linuxVitalsSentence(l10n, v);
        final names =
            v != null &&
            heat != null &&
            heat != LinuxHeat.cool &&
            v.hasHeavyProgram;
        return Padding(
          padding: EdgeInsetsDirectional.fromSTEB(
            tokens.gutter,
            tokens.space2,
            tokens.gutter,
            tokens.sectionGap,
          ),
          child: KitVitalsStrip(
            stripKey: const ValueKey('linux-vitals'),
            vitals: cells,
            loading: v == null,
            summary: summary,
            summaryLabel: names ? l10n.linuxVitalsOpenRunning : null,
            onSummaryTap: names
                ? () => _open(const LinuxProcessesPage(), 'linux-processes')
                : null,
          ),
        );
      },
    );
  }

  Future<void> _readServices() async {
    try {
      final list = await ref.read(linuxServicesProvider).read();
      if (mounted) setState(() => _services = list);
    } catch (_) {
      // The row keeps its fixed words; the Services page tells the problem.
    }
  }

  String _servicesLine(AppLocalizations l10n) {
    final all = _services;
    if (all == null || all.isEmpty) return l10n.linuxSvcRowDetail;
    final crashed = all
        .where((s) => s.phase == LinuxServicePhase.failed)
        .toList();
    if (crashed.isNotEmpty) {
      return l10n.linuxSvcHubCrashed(crashed.first.def.title);
    }
    final running = linuxServicesByUrgency(
      all,
    ).where((s) => s.phase == LinuxServicePhase.running).toList();
    if (running.isEmpty) return l10n.linuxSvcHubOff(all.length);
    return l10n.linuxSvcHubRunning(
      running.length,
      running.map((s) => s.def.title).join(', '),
    );
  }

  /// When the last backup was saved; null until read, or when none was.
  DateTime? _lastBackup;

  Future<void> _readLastBackup() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final ms = prefs.getInt(linuxLastBackupKey);
      if (mounted) {
        setState(
          () => _lastBackup = ms == null
              ? null
              : DateTime.fromMillisecondsSinceEpoch(ms),
        );
      }
    } catch (_) {
      // No settings store (a test): the row says there is no backup yet.
    }
  }

  String _backupLine(BuildContext context, AppLocalizations l10n) {
    final at = _lastBackup;
    if (at == null) return l10n.linuxBackupRowNever;
    return l10n.linuxBackupRowLast(
      MaterialLocalizations.of(context).formatMediumDate(at),
    );
  }

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
          _vitalsStrip(context, l10n),
          const LinuxJobPanel(),
          KitRowGroup(
            children: [
              KitRow(
                key: const ValueKey('linux-row-services'),
                leading: icon(AppIconography.play),
                title: l10n.linuxSvcTitle,
                supporting: TextSpan(text: _servicesLine(l10n)),
                supportingMaxLines: 2,
                trailing: const KitChevron(),
                onTap: () async {
                  await _open(const LinuxServicesPage(), 'linux-services');
                  if (mounted) unawaited(_readServices());
                },
              ),
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
                key: const ValueKey('linux-row-backups'),
                leading: icon(AppIconography.archive),
                title: l10n.linuxBackupRow,
                supporting: TextSpan(text: _backupLine(context, l10n)),
                supportingMaxLines: 2,
                trailing: const KitChevron(),
                onTap: () async {
                  await openLinuxBackups(context);
                  if (mounted) unawaited(_readLastBackup());
                },
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
