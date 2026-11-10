/// Linux › Running: what runs inside the app's own Linux, by what it is
/// (OpenCode server, Paseo, agents, dev servers, other), with memory and
/// whether it is busy, and a named, confirmed Stop.
///
/// One list ordered by what runs, then busy before idle, then memory. It
/// refreshes on pull and every 10 seconds while open. Busy and Idle are
/// measured between two readings, never guessed from one. The OpenCode
/// server is protected: it is stopped from This phone. A stop sends exactly
/// the processes the question named (and what they started), each checked
/// again against its start time by the phone.
///
/// Kit parts only: [KitScreen], [KitSkeletonRows], [KitStateView],
/// [KitRowGroup], [KitNotice], [showKitSheet], [showKitConfirm].
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../builtin/linux_manager/linux_manager.dart';
import '../../../builtin/linux_manager/linux_processes.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../../widgets/process_format.dart';
import 'linux_manager_common.dart';

class LinuxProcessesPage extends ConsumerStatefulWidget {
  const LinuxProcessesPage({
    super.key,
    this.refreshInterval = const Duration(seconds: 10),
    this.sampleDelay = const Duration(seconds: 4),
  });

  final Duration refreshInterval;

  /// When the second reading comes after the page opens: Busy and Idle
  /// need two.
  final Duration sampleDelay;

  @override
  ConsumerState<LinuxProcessesPage> createState() => _State();
}

class _State extends ConsumerState<LinuxProcessesPage> {
  LinuxProcessScan? _scan;
  LinuxProcessScan? _previous;
  Object? _failure;
  bool _loading = true;
  bool _busy = false;
  String? _outcome;
  bool _outcomeOk = true;
  Timer? _timer;
  Timer? _sample;

  @override
  void initState() {
    super.initState();
    unawaited(_refresh());
    _sample = Timer(widget.sampleDelay, () {
      if (mounted && !_busy) unawaited(_refresh());
    });
    _timer = Timer.periodic(widget.refreshInterval, (_) {
      if (mounted && !_busy) unawaited(_refresh());
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _sample?.cancel();
    super.dispose();
  }

  Future<void> _refresh() async {
    try {
      final scan = await ref.read(linuxManagerProvider).scanProcesses();
      if (!mounted) return;
      setState(() {
        _previous = _scan;
        _scan = scan;
        _failure = null;
        _loading = false;
      });
    } on LinuxManagerException catch (error) {
      if (!mounted) return;
      setState(() {
        _failure = error;
        _loading = false;
      });
    }
  }

  bool _busyNow(LinuxProcessGroup g) => g.members.any((p) {
    final pct = _scan?.cpuPercent(p, _previous);
    return pct != null && pct >= 5;
  });

  /// Facts without the kind: "Busy · 43 MB · running 1 h 1 min".
  String _facts(AppLocalizations l10n, LinuxProcessGroup g) {
    final known = g.members.any((p) => _scan?.cpuPercent(p, _previous) != null);
    final memory = g.memoryMb;
    return [
      if (g.count > 1) l10n.linuxMgrProcGroupCount(g.count),
      if (known) _busyNow(g) ? l10n.termuxProcsBusy : l10n.termuxProcsIdle,
      if (memory > 0) l10n.termuxProcsMemoryMb(memory),
      if (g.elapsedSeconds > 0)
        l10n.termuxProcsRunningFor(
          formatProcessDuration(l10n, g.elapsedSeconds),
        ),
    ].join(' · ');
  }

  List<LinuxProcessGroup> _ordered() {
    final list = [...?_scan?.groups()];
    list.sort((a, b) {
      for (final order in [
        a.leader.kind.index.compareTo(b.leader.kind.index),
        (_busyNow(a) ? 0 : 1).compareTo(_busyNow(b) ? 0 : 1),
        b.memoryMb.compareTo(a.memoryMb),
      ]) {
        if (order != 0) return order;
      }
      return a.leader.pid.compareTo(b.leader.pid);
    });
    return list;
  }

  /// "Stop vite?" naming the target, what else stops with it and that
  /// nothing restarts it.
  Future<bool> _confirmStop(BuildContext context, LinuxProcessGroup g) {
    final l10n = AppLocalizations.of(context);
    final name = linuxProcessTitle(l10n, g.leader);
    final helpers = (_scan?.withDescendants(g.roots).length ?? g.count) - 1;
    return showKitConfirm(
      context,
      title: l10n.linuxMgrStopTitle(name),
      body: l10n.linuxMgrStopBody,
      confirmLabel: l10n.linuxMgrStopLabel(name),
      icon: AppIcons.stop,
      kind: KitConfirmKind.stop,
      consequenceItems: [
        if (helpers > 0) KitConsequence(l10n.linuxMgrStopHelpers(helpers)),
      ],
      sheetKey: const Key('linux-procs-confirm'),
      confirmKey: const Key('linux-procs-confirm-stop'),
    );
  }

  Future<void> _stop(LinuxProcessGroup group) async {
    final l10n = AppLocalizations.of(context);
    final scan = _scan;
    if (scan == null) return;
    setState(() {
      _busy = true;
      _outcome = null;
      _failure = null;
    });
    try {
      final result = await ref
          .read(linuxManagerProvider)
          .stop(group.roots, scan);
      if (!mounted) return;
      final parts = <String>[
        l10n.linuxMgrStopped(result.ended),
        if (result.forced > 0) l10n.linuxMgrStopForced(result.forced),
        if (result.remaining > 0) l10n.linuxMgrStopRemaining(result.remaining),
        if (result.changed > 0) l10n.linuxMgrStopChanged(result.changed),
      ];
      setState(() {
        _outcome = parts.join(' · ');
        _outcomeOk = result.remaining == 0;
      });
    } on LinuxManagerException catch (error) {
      if (mounted) setState(() => _failure = error);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
    if (mounted) await _refresh();
  }

  Future<void> _stopFromList(LinuxProcessGroup group) async {
    if (!await _confirmStop(context, group) || !mounted) return;
    await _stop(group);
  }

  Future<void> _showDetails(LinuxProcessGroup group) async {
    final process = group.leader;
    final l10n = AppLocalizations.of(context);
    BuildContext? inside;
    await showKitSheet<void>(
      context,
      title: linuxProcessTitle(l10n, process),
      subtitle: linuxKindLabel(l10n, process.kind),
      icon: linuxKindIcon(process.kind),
      secondary: process.protected
          ? null
          : KitAction(
              key: const Key('linux-procs-details-stop'),
              label: l10n.linuxMgrStopLabel(linuxProcessTitle(l10n, process)),
              icon: AppIcons.stop,
              destructive: true,
              onPressed: _busy
                  ? null
                  : () async {
                      final sheet = inside;
                      if (sheet == null) return;
                      if (!await _confirmStop(sheet, group) || !mounted) {
                        return;
                      }
                      if (sheet.mounted) KitSheet.close<void>(sheet);
                      await _stop(group);
                    },
              disabledReason: _busy ? l10n.linuxMgrStopping : null,
            ),
      tertiary: [
        KitAction.copy(
          key: const Key('linux-procs-details-copy'),
          label: l10n.linuxMgrCopyCommand,
          text: () => process.cmd,
        ),
      ],
      body: (sheetContext) {
        inside = sheetContext;
        return _Details(process: process, facts: _facts(l10n, group));
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final scan = _scan;
    final Widget body;
    if (scan == null && _loading) {
      body = ListView(
        key: const ValueKey('linux-procs-loading'),
        padding: KitScreen.padding(context),
        children: const [KitSkeletonRows()],
      );
    } else if (scan == null) {
      body = KitStateView.error(
        key: const ValueKey('linux-procs-load-error'),
        title: l10n.linuxMgrLoadFailedTitle,
        body: linuxProblemText(l10n, _failure ?? Object()),
        details: linuxProblemDetails(_failure ?? Object()),
        retry: KitAction(label: l10n.commonRetry, onPressed: _refresh),
      );
    } else {
      body = KitRefresh(
        onRefresh: _refresh,
        child: ListView(
          key: const ValueKey('linux-procs-list'),
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsetsDirectional.only(
            top: KitTokens.of(context).space2,
            bottom: KitScreen.endPadding(context),
          ),
          children: [_list(context, l10n, scan)],
        ),
      );
    }
    return KitScreen(
      topBar: KitTopBar(title: l10n.linuxMgrProcTitle),
      width: KitScreenWidth.reading,
      loading: _busy,
      loadingLabel: l10n.linuxMgrStopping,
      body: body,
    );
  }

  Widget _list(
    BuildContext context,
    AppLocalizations l10n,
    LinuxProcessScan scan,
  ) {
    final tokens = KitTokens.of(context);
    final rails = linuxRails(context);
    final ordered = _ordered();
    final totalMb = ordered.fold<int>(0, (sum, g) => sum + g.memoryMb);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (ordered.isNotEmpty)
          Padding(
            padding: rails,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                KitText(
                  l10n.linuxMgrProcSummary(
                    ordered.length,
                    l10n.termuxProcsMemoryMb(totalMb),
                  ),
                  key: const Key('linux-procs-summary'),
                  role: KitTextRole.headline,
                  tabular: true,
                ),
                SizedBox(height: tokens.space1),
                KitText(l10n.termuxProcsAutoRefresh, role: KitTextRole.caption),
              ],
            ),
          ),
        if (_failure != null)
          Padding(
            padding: rails,
            child: KitNotice.error(
              message: linuxProblemText(l10n, _failure!),
              details: linuxProblemDetails(_failure!),
              messageKey: const Key('linux-procs-error'),
              retry: KitAction(label: l10n.commonRetry, onPressed: _refresh),
            ),
          ),
        if (_outcome != null)
          Padding(
            padding: rails,
            child: KitNotice(
              key: const ValueKey('linux-procs-outcome'),
              tone: _outcomeOk ? AppStatusTone.ok : AppStatusTone.failure,
              icon: _outcomeOk ? AppIconography.check : AppIconography.error,
              title: _outcomeOk ? null : l10n.linuxMgrNotStoppedTitle,
              message: _outcome!,
              messageKey: const Key('linux-procs-result'),
            ),
          ),
        if (ordered.isEmpty)
          Padding(
            padding: rails,
            child: KitStateView(
              key: const Key('linux-procs-empty'),
              icon: AppIconography.processor,
              title: l10n.linuxMgrProcEmpty,
              body: l10n.linuxMgrProcEmptyBody,
              size: KitStateSize.inline,
              padding: EdgeInsets.zero,
            ),
          )
        else
          Padding(
            padding: EdgeInsetsDirectional.only(bottom: tokens.sectionGap),
            child: KitRowGroup(
              key: const Key('linux-procs-group'),
              children: [for (final p in ordered) _row(context, l10n, p)],
            ),
          ),
      ],
    );
  }

  Widget _row(
    BuildContext context,
    AppLocalizations l10n,
    LinuxProcessGroup g,
  ) {
    final p = g.leader;
    final title = linuxProcessTitle(l10n, p);
    final facts = _facts(l10n, g);
    final line = [
      if (p.kind != LinuxProcessKind.openCodeServer &&
          p.kind != LinuxProcessKind.paseo)
        linuxKindLabel(l10n, p.kind)
      else
        null,
      if (facts.isNotEmpty) facts,
    ].nonNulls.join(' · ');
    return KitRow(
      key: Key('linux-proc-${p.pid}'),
      leading: KitRow.icon(context, linuxKindIcon(p.kind)),
      title: title,
      supporting: TextSpan(text: line.isEmpty ? facts : line),
      supportingKey: Key('linux-proc-line-${p.pid}'),
      supportingMaxLines: 2,
      below: p.protected
          ? KitText(
              l10n.linuxMgrProtected,
              key: Key('linux-proc-protected-${p.pid}'),
              role: KitTextRole.secondary,
            )
          : null,
      trailing: const KitChevron(),
      onTap: () => _showDetails(g),
      menu: [
        KitMenuItem(
          label: l10n.kitDetails,
          icon: AppIconography.info,
          onSelected: () => _showDetails(g),
        ),
        KitMenuItem.copy(label: l10n.linuxMgrCopyCommand, text: () => p.cmd),
        if (!p.protected)
          KitMenuItem(
            key: Key('linux-proc-stop-${p.pid}'),
            label: l10n.linuxMgrStopLabel(title),
            icon: AppIcons.stop,
            destructive: true,
            enabled: !_busy,
            disabledReason: _busy ? l10n.linuxMgrStopping : null,
            onSelected: () => _stopFromList(g),
          ),
      ],
    );
  }
}

class _Details extends StatelessWidget {
  const _Details({required this.process, required this.facts});

  final LinuxProcess process;
  final String facts;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final about = switch (process.kind) {
      LinuxProcessKind.openCodeServer => l10n.linuxMgrAboutServer,
      LinuxProcessKind.paseo => l10n.linuxMgrAboutPaseo,
      LinuxProcessKind.agent => l10n.linuxMgrAboutAgent,
      LinuxProcessKind.devServer => l10n.linuxMgrAboutDev,
      LinuxProcessKind.other => l10n.linuxMgrAboutOther,
    };
    return Column(
      key: const Key('linux-procs-details'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        KitText(about, key: const Key('linux-procs-details-about')),
        if (facts.isNotEmpty) ...[
          SizedBox(height: tokens.space2),
          KitText(
            facts,
            key: const Key('linux-procs-details-facts'),
            role: KitTextRole.secondary,
            tabular: true,
          ),
        ],
        SizedBox(height: tokens.space4),
        KitDetailsFold(
          values: [
            KitTechnicalValue(
              l10n.termuxProcsCommand,
              process.cmd,
              key: const Key('linux-procs-details-command'),
            ),
            KitTechnicalValue(l10n.termuxProcsProcessId, '${process.pid}'),
            KitTechnicalValue(l10n.termuxProcsParentId, '${process.ppid}'),
            KitTechnicalValue(
              l10n.termuxProcsCpuTime,
              formatProcessDuration(l10n, process.cpuSeconds),
            ),
          ],
        ),
      ],
    );
  }
}
