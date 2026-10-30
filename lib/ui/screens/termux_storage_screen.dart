/// Settings › Termux server › Storage (TEAM-304): what the phone server
/// uses, per category, with the exact paths a Clean removes, a background
/// scan that shows its live output in the one log view and can be
/// cancelled, and explicit confirmation before every cache cleanup.
///
/// Projects and OpenCode itself are listed with their sizes and never
/// offered for cleaning; the script refuses those paths as well.
///
/// Built from kit parts only (screen-phone-1): a [KitScreen] page, the
/// intro and the scan as [KitStateView]s, the scan's categories filling in
/// on a [KitChecklist] with its output in a folded [KitLogPanel], categories as [KitExpandRow]s on one [KitRowGroup], the
/// clean question through [showKitConfirm] (the clean runs inside it, so a
/// failure keeps it open), and every result or problem as a [KitNotice].
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../termux/bridge.dart';
import '../../termux/storage.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../widgets/product_states.dart' show productErrorText;
import 'termux_processes_screen.dart';

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// "11.6 GB" through the catalog, so the unit reads in the user's language.
String formatTermuxBytes(AppLocalizations l10n, int bytes) {
  final split = splitBytes(bytes);
  return switch (split.unit) {
    TermuxByteUnit.gb => l10n.termuxStorageBytesGb(split.value),
    TermuxByteUnit.mb => l10n.termuxStorageBytesMb(split.value),
    TermuxByteUnit.kb => l10n.termuxStorageBytesKb(split.value),
    TermuxByteUnit.b => l10n.termuxStorageBytesB(split.value),
  };
}

String termuxStorageCategoryLabel(
  AppLocalizations l10n,
  TermuxStorageCategory category,
) => switch (category.known) {
  TermuxStorageCategoryKey.buildCaches => l10n.termuxStorageCatBuildCaches,
  TermuxStorageCategoryKey.agentScratch => l10n.termuxStorageCatAgentScratch,
  TermuxStorageCategoryKey.projectBuildOutputs =>
    l10n.termuxStorageCatProjectBuildOutputs,
  TermuxStorageCategoryKey.toolchains => l10n.termuxStorageCatToolchains,
  TermuxStorageCategoryKey.aiTeam => l10n.termuxStorageCatAiTeam,
  TermuxStorageCategoryKey.opencode => l10n.termuxStorageCatOpenCode,
  TermuxStorageCategoryKey.projects => l10n.termuxStorageCatProjects,
  TermuxStorageCategoryKey.sharedCaches => l10n.termuxStorageCatSharedCaches,
  null => category.key,
};

String? termuxStorageCategoryNote(
  AppLocalizations l10n,
  TermuxStorageCategory category,
) => switch (category.known) {
  TermuxStorageCategoryKey.buildCaches => l10n.termuxStorageNoteBuildCaches,
  TermuxStorageCategoryKey.agentScratch => l10n.termuxStorageNoteAgentScratch,
  TermuxStorageCategoryKey.projectBuildOutputs =>
    l10n.termuxStorageNoteProjectBuildOutputs,
  TermuxStorageCategoryKey.toolchains => l10n.termuxStorageNoteToolchains,
  TermuxStorageCategoryKey.aiTeam => l10n.termuxStorageNoteAiTeam,
  TermuxStorageCategoryKey.opencode => l10n.termuxStorageNoteOpenCode,
  TermuxStorageCategoryKey.projects => l10n.termuxStorageNoteProjects,
  TermuxStorageCategoryKey.sharedCaches => l10n.termuxStorageNoteSharedCaches,
  null => null,
};

class TermuxStorageScreen extends StatefulWidget {
  const TermuxStorageScreen({
    super.key,
    this.now,
    this.pollInterval = const Duration(seconds: 2),
  });

  /// Clock for "scanned N min ago"; tests pass a fixed one.
  final DateTime Function()? now;

  /// How often a running scan's log is re-read.
  final Duration pollInterval;

  @override
  State<TermuxStorageScreen> createState() => _TermuxStorageScreenState();
}

class _TermuxStorageScreenState extends State<TermuxStorageScreen> {
  TermuxStorageScanStatus? _status;
  TermuxStorageReport? _report;
  String? _error;
  bool _loading = true;
  bool _busy = false;
  String? _cleaningKey;
  String? _resultLine;
  bool _resultIsProblem = false;
  Timer? _poll;

  /// The running scan's output, re-sent whole on every read.
  final _log = KitLogBuffer();

  bool get _scanning =>
      _status?.state == TermuxStorageScanState.running ||
      _busy && _report == null && _cleaningKey == null;

  @override
  void initState() {
    super.initState();
    unawaited(_refresh());
  }

  @override
  void dispose() {
    _poll?.cancel();
    _log.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    try {
      final status = await TermuxStorage.status();
      if (!mounted) return;
      _log.replaceText(status.log);
      setState(() {
        _status = status;
        _report = status.report ?? _report;
        _error = null;
        _loading = false;
      });
      _schedulePoll();
    } on TermuxBridgeException catch (error) {
      if (!mounted) return;
      setState(() {
        _error = productErrorText(error);
        _loading = false;
      });
    }
  }

  void _schedulePoll() {
    _poll?.cancel();
    if (_status?.state != TermuxStorageScanState.running) return;
    _poll = Timer(widget.pollInterval, () {
      if (mounted) unawaited(_refresh());
    });
  }

  Future<void> _startScan() async {
    setState(() {
      _busy = true;
      _error = null;
      _resultLine = null;
    });
    try {
      await TermuxStorage.startScan();
      if (!mounted) return;
      _log.clear();
      setState(() {
        _status = const TermuxStorageScanStatus(
          state: TermuxStorageScanState.running,
          log: '',
          report: null,
        );
      });
      _schedulePoll();
    } on TermuxBridgeException catch (error) {
      if (mounted) setState(() => _error = productErrorText(error));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _cancelScan() async {
    _poll?.cancel();
    try {
      await TermuxStorage.cancelScan();
    } on TermuxBridgeException catch (error) {
      if (mounted) setState(() => _error = productErrorText(error));
    }
    if (mounted) await _refresh();
  }

  Future<void> _clean(TermuxStorageCategory category) async {
    if (!category.canClean || _report?.isStale != false || _busy) return;
    final l10n = _copy(context);
    final size = formatTermuxBytes(l10n, category.bytes);
    final label = termuxStorageCategoryLabel(l10n, category);
    TermuxStorageCleanResult? result;
    setState(() {
      _resultLine = null;
      _error = null;
    });
    // The clean runs inside the question: it shows it is working, and a
    // failure keeps it open with Try again instead of closing on an error
    // (DATA-14).
    final confirmed = await showKitConfirm(
      context,
      title: l10n.termuxStorageCleanConfirmTitle(size, label),
      body: l10n.termuxStorageCleanConfirmBody,
      confirmLabel: l10n.termuxStorageCleanConfirm(size),
      cancelLabel: l10n.termuxStorageKeep,
      icon: AppIconography.delete,
      kind: KitConfirmKind.destructive,
      details: [
        for (final entry in category.paths)
          KitTechnicalValue(
            formatTermuxBytes(l10n, entry.bytes),
            displayTermuxPath(entry.path),
          ),
      ],
      action: () async {
        if (mounted) {
          setState(() {
            _busy = true;
            _cleaningKey = category.key;
          });
        }
        try {
          result = await TermuxStorage.clean(category.key);
        } finally {
          if (mounted) {
            setState(() {
              _busy = false;
              _cleaningKey = null;
            });
          }
        }
      },
      sheetKey: const Key('termux-storage-confirm'),
      confirmKey: const Key('termux-storage-confirm-remove'),
    );
    if (!mounted) return;
    final done = result;
    setState(() {
      if (!confirmed || done == null) return;
      final inUse = done.inUse;
      if (inUse != null) {
        _resultLine = l10n.termuxStorageInUse(inUse.processes.join(', '));
        _resultIsProblem = true;
      } else {
        final freed = done.freedBytes > 0
            ? l10n.termuxStorageFreed(formatTermuxBytes(l10n, done.freedBytes))
            : l10n.termuxStorageFreedNothing;
        _resultLine = done.refused.isEmpty
            ? freed
            : '$freed · ${l10n.termuxStorageRefusedCount(done.refused.length)}';
        _resultIsProblem = false;
        if (done.rescanRequired) _report = _report?.asStale();
      }
    });
  }

  String _scanAge(AppLocalizations l10n, DateTime scannedAt) {
    final now = widget.now?.call() ?? DateTime.now();
    final age = now.difference(scannedAt);
    if (age.inMinutes < 1) return l10n.termuxStorageScannedJustNow;
    if (age.inHours < 1) {
      return l10n.termuxStorageScannedMinutesAgo(age.inMinutes);
    }
    return l10n.termuxStorageScannedHoursAgo(age.inHours);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    final report = _report;
    final scanning = _scanning;
    final Widget body;
    if (_loading) {
      body = ListView(
        key: const ValueKey('termux-storage-loading'),
        padding: KitScreen.padding(context),
        children: const [KitSkeletonRows()],
      );
    } else if (scanning) {
      body = _buildScanning(l10n);
    } else {
      final tokens = KitTokens.of(context);
      final error = _error;
      final scanNotice = switch (_status?.state) {
        TermuxStorageScanState.cancelled => KitNotice(
          key: const Key('termux-storage-cancelled'),
          icon: AppIconography.stop,
          message: l10n.termuxStorageCancelled,
        ),
        // Map statesMissing "scan failed": said as a failure, with the way
        // on beside it (the intro's Scan is that way when nothing was ever
        // measured).
        TermuxStorageScanState.failed => KitNotice.error(
          key: const Key('termux-storage-failed'),
          message: l10n.termuxStorageFailed,
          retry: report == null
              ? null
              : KitAction(
                  label: l10n.termuxStorageRescanAction,
                  onPressed: _busy ? null : _startScan,
                ),
        ),
        _ => null,
      };
      body = ListView(
        key: const ValueKey('termux-storage-list'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          if (error != null)
            _onRails(
              KitNotice.error(
                key: const Key('termux-storage-error'),
                message: error,
                retry: KitAction(label: l10n.commonRetry, onPressed: _refresh),
              ),
            ),
          if (scanNotice != null) _onRails(scanNotice),
          if (report == null)
            _buildIntro(l10n)
          else
            ..._buildReport(l10n, report),
        ],
      );
    }
    return KitScreen(
      topBar: KitTopBar(
        title: l10n.termuxStorageTitle,
        actions: [
          if (report != null && !scanning)
            KitAction(
              key: const Key('termux-storage-rescan'),
              label: l10n.termuxStorageRescanAction,
              icon: AppIconography.retry,
              onPressed: _busy ? null : _startScan,
              disabledReason: _busy ? l10n.termuxStorageCleaning : null,
            ),
        ],
      ),
      width: KitScreenWidth.reading,
      loading: _loading,
      loadingLabel: l10n.termuxStorageRowScanning,
      body: body,
    );
  }

  /// A block on the screen's gutters with a section gap under it.
  Widget _onRails(Widget child) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        end: tokens.gutter,
        bottom: tokens.sectionGap,
      ),
      child: child,
    );
  }

  Widget _buildIntro(AppLocalizations l10n) => KitStateView(
    icon: AppIconography.storage,
    title: l10n.termuxStorageRowNotScanned,
    body: l10n.termuxStorageIntro,
    bodyKey: const Key('termux-storage-intro'),
    size: KitStateSize.inline,
    liveRegion: false,
    primary: KitAction(
      key: const Key('termux-storage-scan'),
      label: l10n.termuxStorageScanAction,
      icon: AppIconography.search,
      onPressed: _busy ? null : _startScan,
    ),
  );

  /// The scan as a working state: one row per category, filling in as the
  /// script reaches it (the sizes arrive with the report), Cancel, and the
  /// raw output folded under Details.
  Widget _buildScanning(AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    final reached = TermuxStorageScanStage.reached(_status?.log ?? '');
    final at = reached?.index ?? 0;
    return ListView(
      key: const ValueKey('termux-storage-scan-view'),
      padding: EdgeInsetsDirectional.only(
        top: tokens.space2,
        bottom: KitScreen.endPadding(context),
      ),
      children: [
        KitStateView(
          icon: AppIconography.storage,
          tone: AppStatusTone.progress,
          title: l10n.termuxStorageScanning,
          titleKey: const Key('termux-storage-scanning'),
          body: l10n.termuxStorageScanningDetail,
          size: KitStateSize.inline,
        ),
        _onRails(
          KitChecklist(
            steps: [
              for (final stage in TermuxStorageScanStage.values)
                KitStep(
                  key: ValueKey('termux-storage-stage-${stage.name}'),
                  title: _stageTitle(l10n, stage),
                  state: stage.index < at
                      ? KitMarkState.done
                      : stage.index == at
                      ? KitMarkState.working
                      : KitMarkState.waiting,
                ),
            ],
            stop: KitAction(
              key: const Key('termux-storage-cancel'),
              label: l10n.termuxStorageCancel,
              icon: AppIconography.stop,
              onPressed: _cancelScan,
            ),
            detailsKey: const ValueKey('termux-storage-log'),
            log: KitLogPanel(lines: _log, live: true),
          ),
        ),
      ],
    );
  }

  static String _stageTitle(
    AppLocalizations l10n,
    TermuxStorageScanStage stage,
  ) => switch (stage) {
    TermuxStorageScanStage.buildCaches => l10n.termuxStorageCatBuildCaches,
    TermuxStorageScanStage.sharedCaches => l10n.termuxStorageCatSharedCaches,
    TermuxStorageScanStage.agentScratch => l10n.termuxStorageCatAgentScratch,
    TermuxStorageScanStage.toolchains => l10n.termuxStorageCatToolchains,
    TermuxStorageScanStage.aiTeam => l10n.termuxStorageCatAiTeam,
    TermuxStorageScanStage.opencode => l10n.termuxStorageCatOpenCode,
    TermuxStorageScanStage.projects => l10n.termuxStorageCatProjects,
    TermuxStorageScanStage.total => l10n.termuxStorageStageTotal,
  };

  List<Widget> _buildReport(AppLocalizations l10n, TermuxStorageReport report) {
    final tokens = KitTokens.of(context);
    final scannedAt = report.scannedAt;
    final result = _resultLine;
    return [
      _onRails(
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            KitText(
              l10n.termuxStorageTotal(
                formatTermuxBytes(l10n, report.totalBytes),
              ),
              key: const Key('termux-storage-total'),
              role: KitTextRole.title,
            ),
            SizedBox(height: tokens.space1),
            KitText(
              [
                if (report.isStale)
                  l10n.termuxStorageRescanRequired
                else
                  l10n.termuxStorageDeletableTotal(
                    formatTermuxBytes(l10n, report.deletableBytes),
                  ),
                if (scannedAt != null) _scanAge(l10n, scannedAt),
              ].join(' · '),
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
          ],
        ),
      ),
      // Always one slot, so the keyed category rows below keep their
      // unfolded state when a result line appears or goes.
      if (result == null)
        const SizedBox.shrink()
      else
        _onRails(
          KitNotice(
            key: const Key('termux-storage-result-notice'),
            messageKey: const Key('termux-storage-result'),
            tone: _resultIsProblem ? AppStatusTone.neutral : AppStatusTone.ok,
            icon: _resultIsProblem
                ? AppIconography.warning
                : AppIconography.check,
            message: result,
            actions: [
              if (_resultIsProblem)
                KitAction(
                  key: const Key('termux-storage-open-running'),
                  label: l10n.termuxStorageOpenRunning,
                  onPressed: () => unawaited(
                    pushKitPage<void>(
                      context,
                      (_) => const TermuxProcessesScreen(),
                    ),
                  ),
                )
              // Map actionsMissing "rescan after a clean": the sizes are
              // stale now, and measuring again is the way on.
              else if (report.isStale)
                KitAction(
                  key: const Key('termux-storage-rescan-after-clean'),
                  label: l10n.termuxStorageRescanAction,
                  onPressed: _busy ? null : _startScan,
                ),
            ],
          ),
        ),
      KitRowGroup(
        leadingIcons: false,
        children: [
          for (final category in report.categories)
            _CategoryRow(
              key: ValueKey('termux-storage-row-${category.key}'),
              category: category,
              cleaning: _cleaningKey == category.key,
              stale: report.isStale,
              busy: _busy,
              onClean: () => _clean(category),
            ),
        ],
      ),
      if (report.projects.isNotEmpty) ...[
        SizedBox(height: tokens.sectionGap),
        KitRowGroup(
          label: l10n.termuxStorageCatProjects,
          children: [
            for (final project in report.projects)
              KitRow(
                key: Key('termux-storage-project-${project.name}'),
                leading: KitRow.icon(context, AppIconography.folders),
                title: project.name,
                titleMaxLines: 2,
                supporting: TextSpan(
                  text: l10n.termuxStorageProjectBuild(
                    formatTermuxBytes(l10n, project.bytes),
                    formatTermuxBytes(l10n, project.buildBytes),
                  ),
                ),
                supportingMaxLines: 2,
              ),
          ],
        ),
      ],
      // Said once, under everything listed: the rows do not repeat it.
      Padding(
        padding: EdgeInsetsDirectional.fromSTEB(
          tokens.gutter,
          tokens.space2,
          tokens.gutter,
          tokens.space2,
        ),
        child: KitText(
          l10n.termuxStorageOnlyBuildCaches,
          key: const Key('termux-storage-footnote'),
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
      ),
    ];
  }
}

/// One category: its size in the row, and, unfolded, what it is, the exact
/// paths a Clean removes and the Clean itself.
class _CategoryRow extends StatelessWidget {
  const _CategoryRow({
    super.key,
    required this.category,
    required this.cleaning,
    required this.stale,
    required this.busy,
    required this.onClean,
  });

  final TermuxStorageCategory category;
  final bool cleaning;
  final bool stale;
  final bool busy;
  final VoidCallback onClean;

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    final tokens = KitTokens.of(context);
    final label = termuxStorageCategoryLabel(l10n, category);
    final size = formatTermuxBytes(l10n, category.bytes);
    final note = termuxStorageCategoryNote(l10n, category);
    final empty = category.paths.isEmpty;
    final canClean = category.canClean && !empty;
    return KitExpandRow(
      headerKey: Key('termux-storage-cat-${category.key}'),
      title: label,
      supporting: TextSpan(text: empty ? l10n.termuxStorageNothingHere : size),
      supportingMaxLines: 2,
      children: [
        Padding(
          padding: EdgeInsetsDirectional.fromSTEB(
            tokens.gutter,
            tokens.space1,
            tokens.gutter,
            tokens.space3,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              if (note != null) ...[
                KitText(
                  note,
                  role: KitTextRole.secondary,
                  tone: KitTextTone.secondary,
                ),
                SizedBox(height: tokens.space3),
              ],
              if (!empty) ...[
                KitText(
                  category.canClean
                      ? l10n.termuxStorageWillRemove
                      : l10n.termuxStorageWhereItIs,
                  role: KitTextRole.label,
                  tone: KitTextTone.secondary,
                ),
                SizedBox(height: tokens.space1),
                for (final entry in category.paths)
                  Padding(
                    padding: EdgeInsetsDirectional.symmetric(
                      vertical: tokens.space1,
                    ),
                    child: Wrap(
                      alignment: WrapAlignment.spaceBetween,
                      spacing: tokens.space3,
                      children: [
                        KitText.mono(
                          displayTermuxPath(entry.path),
                          selectable: true,
                        ),
                        KitText(
                          formatTermuxBytes(l10n, entry.bytes),
                          role: KitTextRole.secondary,
                          tone: KitTextTone.secondary,
                          tabular: true,
                        ),
                      ],
                    ),
                  ),
              ],
              if (canClean) ...[
                SizedBox(height: tokens.space3),
                KitActionBlock(
                  secondary: KitAction(
                    key: Key('termux-storage-clean-${category.key}'),
                    // Names what it removes and how much.
                    label: cleaning
                        ? l10n.termuxStorageCleaning
                        : l10n.termuxStorageCleanBuildCaches(size),
                    icon: AppIconography.delete,
                    working: cleaning,
                    onPressed: stale || busy ? null : onClean,
                    // Said beside it, never only in a tooltip (STATE-8).
                    disabledReason: stale
                        ? l10n.termuxStorageRescanRequired
                        : busy
                        ? l10n.termuxStorageCleaning
                        : null,
                  ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}
