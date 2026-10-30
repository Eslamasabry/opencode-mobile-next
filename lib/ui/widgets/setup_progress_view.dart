import 'dart:async';
import 'dart:math' as math;

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';

import '../../builtin/setup/components.dart' show SetupComponentIds;
import '../../builtin/setup/setup_contract.dart';
import '../../diagnostics/failed_job_report.dart';
import '../../feedback/bug_report.dart' show failedJobReportAction;
import '../../l10n/app_localizations.dart';
import '../../termux/bridge.dart' show TermuxBridge;
import '../app_theme.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_checklist.dart';
import '../kit/kit_log_panel.dart';
import '../kit/kit_progress.dart';
import '../kit/kit_state_view.dart';
import '../kit/kit_status_mark.dart';
import '../kit/scenes/setup_steps_scene.dart';
import 'setup_ui_messages.dart';

/// The progress of any setup job as one state (design standard §3, §4,
/// §10), now a thin adapter over the kit (KitChecklist.md, C24): it maps
/// [SetupProgress] and [SetupComponent] onto a [KitStateView] (the drawing
/// of the journey, a title that says where the job stands, a body, one
/// overall bar) whose content is a [KitChecklist] (a mark per component,
/// Continue setup or Cancel, the live log folded under Details). The kit
/// must not import `lib/builtin/`, so the mapping stays here.
///
/// It knows nothing about where it is shown: no Scaffold, no navigation, no
/// engine. First setup hosts it on screen B; "Add tools" and updates host
/// the same widget with their own [progress] and [title]. Every number it
/// draws comes straight from [progress]; where a component reports only
/// stages it says what is happening, never an estimate dressed up as a
/// measurement.
class SetupProgressView extends StatefulWidget {
  const SetupProgressView({
    super.key,
    required this.progress,
    required this.components,
    this.onContinue,
    this.onCancel,
    this.note,
    this.title,
    this.personActions = const {},
    this.failureActions = const {},
    this.timeLine,
    this.checklistOnly = false,
  });

  final SetupProgress progress;

  /// Only the steps and the log, with no title, drawing, bar or buttons:
  /// the Details sheet of a host that shows the job its own way (the phone
  /// setup ring). Report this failure stays on a failed row.
  final bool checklistOnly;

  /// Where row titles come from. Before the job reports its own components
  /// (the first poll after `run`), these are listed as pending so the
  /// screen never opens empty.
  final List<SetupComponent> components;

  /// "Continue setup" after a failure, an interruption or a cancel. Hidden
  /// when null.
  final VoidCallback? onContinue;

  /// Offered only while the job runs. The host decides whether to confirm.
  final VoidCallback? onCancel;

  /// One quiet line under the title while the job runs; the host's own
  /// words ("You can leave the app…"), since only it knows what leaving means.
  final String? note;

  /// What the job is, while it runs or has just finished ("Setting up
  /// OpenCode on this phone", "Adding Python"). A stopped or failed job says
  /// that instead, so the title never contradicts the bar.
  final String? title;

  /// Steps only the person can do, by row id ("Get Termux", "Allow
  /// Termux" on the Termux host): a pending row with one leads with the
  /// needs-you mark, says its [ComponentProgress.stage] as the instruction,
  /// and offers this button.
  final Map<String, KitAction> personActions;

  /// A failed row's own way forward, by row id, where it is not Continue
  /// setup ("Get the current Termux" on a too-old Termux): the row's one
  /// button. Rows named here or in [personActions] are the host's own, and
  /// their error is the host's words; every other row's error is the job's
  /// technical text, which goes under Details.
  final Map<String, KitAction> failureActions;

  /// The line under the bar in place of the engine's estimate, for a host
  /// that knows how long it has been going but not how long is left.
  final String? timeLine;

  /// Network trouble as curl, apt and the resolver word it. Matched so the
  /// person is told to reconnect instead of being shown a curl exit code.
  static final networkError = RegExp(
    r'could not resolve|resolve host|name resolution|temporary failure resolving'
    r'|\bdns\b|network is unreachable|no route to host|failed to connect'
    r'|connection (?:refused|timed out|reset)|couldn.t connect'
    r'|curl: \((?:6|7|28|35|52|56)\)|operation timed out|unable to connect'
    r'|no internet|offline',
    caseSensitive: false,
  );

  /// A failed ownership check cannot be repaired by rerunning this job.
  static bool needsOwnershipRecovery(SetupProgress progress) =>
      progress.state == SetupState.failed &&
      [
        progress.error,
        for (final row in progress.components)
          if (row.state == ComponentState.failed) row.error,
      ].any(
        (error) =>
            error != null &&
            (error == 'runtime_ownership_unknown' ||
                RegExp(r'\bownershipUnknown\b').hasMatch(error)),
      );

  /// Why a failed job stopped, in one plain sentence, for a host that
  /// shows the job its own way: the same words the failed row (or, with no
  /// failed row, the job) says here. Technical text stays under Details.
  static String failureSentence(
    AppLocalizations l10n,
    SetupProgress progress,
    String Function(String id) titleOf,
  ) {
    if (needsOwnershipRecovery(progress)) {
      return l10n.inAppServerOwnershipRecoveryBody;
    }
    if (_SetupProgressViewState._looksLikeNetwork(progress)) {
      return l10n.setupProgressViewNoInternet;
    }
    ComponentProgress? row;
    for (final candidate in progress.components) {
      if (candidate.state == ComponentState.failed) {
        row = candidate;
        break;
      }
    }
    if (row == null) {
      final error = progress.error;
      final plain = error == null ? null : setupPlainMessage(l10n, error);
      if (plain != null) return plain;
      final current = progress.current;
      return current != null
          ? l10n.setupProgressViewFailedAt(titleOf(current))
          : l10n.setupProgressViewFailedUnknown;
    }
    final reason = row.error ?? progress.error;
    final title = titleOf(row.id);
    if ({
      l10n.phoneSetupErrorInstall(title),
      l10n.phoneSetupErrorNoSpace(title),
      l10n.phoneSetupErrorChecksum(title),
      l10n.phoneSetupErrorOffline(title),
    }.contains(reason)) {
      return reason!;
    }
    final plain = reason == null ? null : setupPlainMessage(l10n, reason);
    if (plain != null) return plain;
    final stage = row.stage;
    if (stage != null) return l10n.setupProgressViewFailedDuring(stage);
    return l10n.setupProgressViewFailedStep;
  }

  /// Formats a byte pair in the total's unit ("18 of 30 MB"). The done
  /// figure is rounded down so the row never claims more than has arrived.
  static (String done, String total) formatBytePair(int done, int total) {
    const units = ['B', 'KB', 'MB', 'GB'];
    var unit = 0;
    var scale = 1.0;
    while (unit < units.length - 1 && total >= scale * 1000) {
      scale *= 1000;
      unit++;
    }
    final decimals = unit == 0 || total / scale >= 10 ? 0 : 1;
    String fixed(double value, {required bool down}) {
      final factor = math.pow(10, decimals);
      final rounded = down
          ? (value * factor).floor() / factor
          : (value * factor).round() / factor;
      return rounded.toStringAsFixed(decimals);
    }

    return (
      fixed(done.clamp(0, total) / scale, down: true),
      '${fixed(total / scale, down: false)} ${units[unit]}',
    );
  }

  /// The drawing of [progress] (design standard §10): which part of the
  /// journey is lit, how many components are in, and whether it stands
  /// still. [rows] defaults to the job's own components; setup start draws
  /// the same scene for a job that runs or stopped part way.
  static SetupStepsScene sceneFor(
    AppLocalizations l10n,
    SetupProgress progress, [
    List<ComponentProgress>? rows,
  ]) {
    final list = rows ?? progress.components;
    return SetupStepsScene(
      stage: _SetupProgressViewState._stageOf(l10n, progress, list),
      done: list
          .where(
            (r) =>
                r.state == ComponentState.done ||
                r.state == ComponentState.skipped,
          )
          .length,
      total: list.length,
      fraction: _SetupProgressViewState._fractionOf(list),
      halted: switch (progress.state) {
        SetupState.failed => SetupSceneHalt.failed,
        SetupState.interrupted || SetupState.cancelled => SetupSceneHalt.paused,
        _ => null,
      },
    );
  }

  @override
  State<SetupProgressView> createState() => _SetupProgressViewState();
}

class _SetupProgressViewState extends State<SetupProgressView> {
  /// The furthest the bar has been in this job. The engine's figure can dip
  /// (a component's weight re-estimated, a stage restarting its bytes); the
  /// bar must not, or it reads as setup going backwards.
  double _furthest = 0;
  String _jobKey = '';

  /// Bumped when a new job starts: the state is built afresh, so the bar
  /// takes the new job's value at once instead of easing backwards from the
  /// old end (KitProgress: "a new job is a new KitProgressView").
  int _generation = 0;

  /// The job's log, for the kit's one log panel under Details.
  final _log = KitLogBuffer();
  String _logText = '';

  /// apt's lines stay one line each, long ones scroll sideways, on a phone
  /// too (design regressions ledger row 16: wrapped in two, they were hard
  /// to read); the panel's Wrap toggle still wraps them on request.
  bool _wrapLog = false;

  /// The time left as shown, in seconds, for this job: it never goes up,
  /// and the steps not started yet count at least their own estimates, so
  /// a fast download never reads "Less than a minute" with the install
  /// still to come (emulator QA F11: "Less than a minute" at 30 %, then
  /// "~5 min left"). Null until the engine gives an estimate.
  int? _eta;

  /// When the engine last reported progress: the working row escalates
  /// after 8 s without a new report (STATE-5).
  late DateTime _lastReport;
  String _signature = '';

  @override
  void initState() {
    super.initState();
    final progress = widget.progress;
    _jobKey = _keyOf(progress);
    _furthest = progress.overall.clamp(0, 1).toDouble();
    _lastReport = clock.now();
    _signature = _signatureOf(progress);
    _trackEta(progress);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // The log's first fill needs the locale (what counts as the app's own
    // words), so it waits for the dependencies.
    _syncLog(widget.progress);
  }

  @override
  void didUpdateWidget(SetupProgressView old) {
    super.didUpdateWidget(old);
    final next = widget.progress;
    final key = _keyOf(next);
    // A new job id (or, without one, a new set of components) is a new job;
    // so is a fresh start after a finished or never-started one.
    final restarted =
        next.state == SetupState.running &&
        (old.progress.state == SetupState.done ||
            old.progress.state == SetupState.idle);
    if (key != _jobKey || restarted) {
      _jobKey = key;
      _generation++;
      _furthest = next.overall.clamp(0, 1).toDouble();
      _eta = null;
    } else {
      _furthest = math.max(_furthest, next.overall.clamp(0, 1).toDouble());
    }
    final signature = _signatureOf(next);
    if (signature != _signature) {
      _signature = signature;
      _lastReport = clock.now();
    }
    _trackEta(next);
    _syncLog(next);
  }

  void _trackEta(SetupProgress progress) {
    final raw = progress.etaSeconds;
    if (progress.state != SetupState.running || raw == null) return;
    var later = 0;
    for (final row in progress.components) {
      if (row.state != ComponentState.pending) continue;
      for (final component in widget.components) {
        if (component.id == row.id) later += component.estimatedSeconds;
      }
    }
    final estimate = math.max(raw, later);
    final shown = _eta;
    _eta = shown == null ? estimate : math.min(shown, estimate);
  }

  @override
  void dispose() {
    _log.dispose();
    super.dispose();
  }

  void _syncLog(SetupProgress progress) {
    final text = _logOf(progress);
    if (text == _logText) return;
    _logText = text;
    _log.replaceText(text);
  }

  /// The job's log, then any technical text a failure carried that the log
  /// does not already hold: the words on screen leave it out (no raw
  /// errors), so Details is where it is read.
  String _logOf(SetupProgress progress) {
    final log = progress.logTail.trimRight();
    if (progress.state != SetupState.failed) return log;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final extra = <String>[
      for (final raw in [
        for (final row in progress.components)
          if (row.state == ComponentState.failed && !_hostRow(row.id))
            row.error,
        progress.error,
      ])
        if (raw != null &&
            raw.trim().isNotEmpty &&
            !log.contains(raw.trim()) &&
            setupPlainMessage(l10n, raw) == null)
          raw.trim(),
    ];
    return [
      if (log.isNotEmpty) log,
      ...{...extra},
    ].join('\n');
  }

  /// A row the host drew itself (a person step): its words are the host's.
  bool _hostRow(String id) =>
      widget.personActions.containsKey(id) ||
      widget.failureActions.containsKey(id);

  // The engine names each job; the component list is only the fallback for
  // hosts whose progress carries no id.
  static String _keyOf(SetupProgress progress) =>
      progress.jobId ?? progress.components.map((c) => c.id).join(',');

  /// What counts as news from the engine: the bar, the current component,
  /// and each row's state, stage and measured amount.
  static String _signatureOf(SetupProgress progress) => [
    progress.state.name,
    progress.overall,
    progress.current,
    for (final c in progress.components)
      '${c.id}:${c.state.name}:${c.stage}:${c.bytesDone}:${c.percent}',
  ].join('|');

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final progress = widget.progress;
    final rows = progress.components.isNotEmpty
        ? progress.components
        : [
            for (final component in widget.components)
              ComponentProgress(
                id: component.id,
                state: ComponentState.pending,
              ),
          ];
    final failed = progress.state == SetupState.failed;
    final failedRow = rows.any((r) => r.state == ComponentState.failed);
    final stopped =
        progress.state == SetupState.interrupted ||
        progress.state == SetupState.cancelled;
    final done = progress.state == SetupState.done;
    final running = !failed && !stopped && !done;
    final ownershipRecovery = SetupProgressView.needsOwnershipRecovery(
      progress,
    );
    final network = !ownershipRecovery && failed && _looksLikeNetwork(progress);
    final title = widget.title ?? l10n.phoneSetupProgressTitle;

    // A job can fail between components (the server start after the last
    // install): the body says why even though no row carries it.
    final String? body;
    Key? bodyKey;
    if (failed) {
      if (failedRow) {
        body = null;
      } else {
        body = network
            ? l10n.setupProgressViewNoInternet
            : _jobFailureText(l10n, progress);
        bodyKey = const Key('setup-progress-job-error');
      }
    } else if (stopped) {
      body = progress.state == SetupState.interrupted
          ? l10n.setupProgressViewInterrupted
          : l10n.setupProgressViewCancelled;
    } else if (running) {
      body = widget.note;
    } else {
      body = null;
    }

    final checklist = KitChecklist(
      checklistKey: const Key('setup-progress-checklist'),
      detailsKey: const Key('setup-progress-details'),
      steps: [
        for (final row in rows)
          KitStep(
            key: ValueKey('setup-progress-row-${row.id}'),
            title: _titleOf(row.id),
            state: _markOf(row.state),
            supporting: row.state == ComponentState.failed
                ? _failureText(l10n, progress, row, network)
                : row.state == ComponentState.pending &&
                      widget.personActions.containsKey(row.id)
                ? row.stage
                : _detail(l10n, row),
            personAction: row.state == ComponentState.pending
                ? widget.personActions[row.id]
                : null,
            retry: row.state == ComponentState.failed && !ownershipRecovery
                ? widget.failureActions[row.id]
                : null,
            // Report this failure (P8.4): the job's log as it is at the tap
            // (widget.progress is the host's latest), for this row.
            report: row.state == ComponentState.failed
                ? failedJobReportAction(
                    context,
                    key: ValueKey('setup-progress-report-${row.id}'),
                    capture: () => FailedJobReport.setup(
                      widget.progress,
                      componentId: row.id,
                    ),
                  )
                : null,
          ),
      ],
      since: running ? _lastReport : null,
      resume: widget.checklistOnly
          ? null
          : ownershipRecovery
          ? KitAction(
              key: const Key('setup-progress-recover-ownership'),
              label: l10n.e7SetupOpenAppSettings,
              onPressed: () => unawaited(TermuxBridge.openAppSettings()),
            )
          : progress.canContinue && widget.onContinue != null
          ? KitAction(
              key: const Key('setup-progress-continue'),
              label: l10n.setupProgressViewContinue,
              onPressed: widget.onContinue,
            )
          : null,
      stop: running && widget.onCancel != null && !widget.checklistOnly
          ? KitAction(
              key: const Key('setup-progress-cancel'),
              label: l10n.setupProgressViewCancel,
              onPressed: widget.onCancel,
              // Stopping drops the component being installed.
              destructive: true,
            )
          : null,
      log: KitLogPanel(
        panelKey: const Key('setup-progress-log'),
        lines: _log,
        wrap: _wrapLog,
        onWrapChanged: (wrap) => setState(() => _wrapLog = wrap),
        live: running,
        emptyText: l10n.setupProgressViewNoLog,
        // A job that failed between components has no failed row to carry
        // the report: the whole job's report sits on its log instead.
        headerAction: failed && !failedRow
            ? failedJobReportAction(
                context,
                key: const Key('setup-progress-report-job'),
                capture: () => FailedJobReport.setup(widget.progress),
              )
            : null,
      ),
    );

    if (widget.checklistOnly) return checklist;

    return KeyedSubtree(
      key: ValueKey('setup-progress-job-$_generation'),
      child: KitStateView(
        icon: failed ? AppIconography.error : AppIconography.download,
        tone: failed ? AppStatusTone.failure : AppStatusTone.neutral,
        // The drawing says the same as the title and the rows, so it stays
        // decorative (design standard §10).
        illustration: SetupProgressView.sceneFor(l10n, progress, rows),
        illustrationAmbient: running,
        illustrationWidth: 264,
        title: failed || stopped ? l10n.setupProgressViewFailedTitle : title,
        titleKey: const Key('setup-progress-title'),
        body: body,
        bodyKey: bodyKey,
        // The checklist's own summary is the job's live region (A11Y-3).
        liveRegion: false,
        progress: KitProgress.known(
          _furthest,
          key: const Key('setup-progress-overall'),
          tone: failed
              ? AppStatusTone.failure
              : stopped
              ? AppStatusTone.neutral
              : null,
          semanticsLabel: l10n.setupProgressViewOverallLabel,
          caption: running || done
              ? widget.timeLine ?? _timeLine(l10n, progress)
              : null,
        ),
        content: checklist,
      ),
    );
  }

  static KitMarkState _markOf(ComponentState state) => switch (state) {
    ComponentState.done || ComponentState.skipped => KitMarkState.done,
    ComponentState.running || ComponentState.checking => KitMarkState.working,
    ComponentState.failed => KitMarkState.failed,
    ComponentState.pending => KitMarkState.waiting,
  };

  /// Where the job is, as the drawing shows it: OpenCode starting, a
  /// download (bytes still arriving), an unpack, or an install.
  static SetupSceneStage _stageOf(
    AppLocalizations l10n,
    SetupProgress progress,
    List<ComponentProgress> rows,
  ) {
    if (progress.state == SetupState.done) return SetupSceneStage.start;
    ComponentProgress? current;
    for (final row in rows) {
      if (row.state == ComponentState.running ||
          row.state == ComponentState.checking ||
          row.state == ComponentState.failed ||
          row.id == progress.current) {
        current = row;
        break;
      }
    }
    if (current == null) {
      final finished = rows.every(
        (r) =>
            r.state == ComponentState.done || r.state == ComponentState.skipped,
      );
      return finished && rows.isNotEmpty
          ? SetupSceneStage.start
          : SetupSceneStage.download;
    }
    if (current.id == SetupComponentIds.start) return SetupSceneStage.start;
    final stage = current.stage ?? '';
    if (stage == l10n.phoneSetupStageUnpackingLinux ||
        _unpacking.hasMatch(stage)) {
      return SetupSceneStage.unpack;
    }
    final total = current.bytesTotal ?? 0;
    if ((total > 0 && (current.bytesDone ?? 0) < total) ||
        stage == l10n.phoneSetupStageDownloadingLinux ||
        _downloading.hasMatch(stage)) {
      return SetupSceneStage.download;
    }
    return SetupSceneStage.install;
  }

  static final _unpacking = RegExp(r'unpack|extract', caseSensitive: false);
  static final _downloading = RegExp(r'download|fetch', caseSensitive: false);

  /// The current component's measured share, when it reports one.
  static double? _fractionOf(List<ComponentProgress> rows) {
    for (final row in rows) {
      if (row.state != ComponentState.running &&
          row.state != ComponentState.checking) {
        continue;
      }
      final total = row.bytesTotal;
      if (total != null && total > 0 && row.bytesDone != null) {
        return (row.bytesDone! / total).clamp(0, 1).toDouble();
      }
      if (row.percent case final percent?) {
        return (percent / 100).clamp(0, 1).toDouble();
      }
      return null;
    }
    return null;
  }

  String _titleOf(String id) {
    for (final component in widget.components) {
      if (component.id == id) return component.shortTitle;
    }
    return id;
  }

  /// The one line under the bar while it moves: how long is left, only
  /// when the engine knows ([_eta]). "Less than a minute" only on the last
  /// step; before it, at least "~1 min left".
  String _timeLine(AppLocalizations l10n, SetupProgress progress) {
    if (progress.state == SetupState.done) return l10n.setupProgressViewDone;
    final eta = _eta;
    if (progress.etaSeconds == null || eta == null) {
      return l10n.setupProgressViewGettingStarted;
    }
    final lastStep = !progress.components.any(
      (row) => row.state == ComponentState.pending,
    );
    if (eta < 60 && lastStep) return l10n.setupProgressViewUnderMinute;
    return l10n.setupProgressViewMinutesLeft(math.max(1, (eta / 60).round()));
  }

  /// A row's supporting line, from the row's own signal only: the version
  /// once done, the stage and the measured amount while it works.
  static String? _detail(AppLocalizations l10n, ComponentProgress row) {
    switch (row.state) {
      case ComponentState.done || ComponentState.skipped:
        return row.version;
      case ComponentState.pending || ComponentState.failed:
        return null;
      case ComponentState.running || ComponentState.checking:
        break;
    }
    final measured = _measured(l10n, row);
    final stage =
        row.stage ??
        (row.state == ComponentState.checking
            ? l10n.setupProgressViewChecking
            : null);
    if (measured != null && stage != null) {
      return l10n.setupProgressViewStageMeasured(stage, measured);
    }
    return measured ?? stage ?? l10n.setupProgressViewStarting;
  }

  static String? _measured(AppLocalizations l10n, ComponentProgress row) {
    final total = row.bytesTotal;
    final done = row.bytesDone;
    if (total != null && total > 0 && done != null) {
      final (d, t) = SetupProgressView.formatBytePair(done, total);
      return l10n.setupProgressViewBytes(d, t);
    }
    if (row.percent case final percent?) {
      return l10n.setupProgressViewPercent(percent.clamp(0, 100).floor());
    }
    return null;
  }

  String _failureText(
    AppLocalizations l10n,
    SetupProgress progress,
    ComponentProgress row,
    bool network,
  ) {
    if (SetupProgressView.needsOwnershipRecovery(progress)) {
      return l10n.inAppServerOwnershipRecoveryBody;
    }
    if (network) return l10n.setupProgressViewNoInternet;
    final reason = row.error ?? progress.error;
    final title = _titleOf(row.id);
    if ({
      l10n.phoneSetupErrorInstall(title),
      l10n.phoneSetupErrorNoSpace(title),
      l10n.phoneSetupErrorChecksum(title),
      l10n.phoneSetupErrorOffline(title),
    }.contains(reason)) {
      return reason!;
    }
    // The host's own row: its words are already the person's.
    if (_hostRow(row.id) && reason != null) return reason;
    // A message the app wrote is said in its words; anything else (a
    // script's error, native text) is under Details (no raw errors).
    final plain = reason == null ? null : setupPlainMessage(l10n, reason);
    if (plain != null) return plain;
    final stage = row.stage;
    if (stage != null) return l10n.setupProgressViewFailedDuring(stage);
    return l10n.setupProgressViewFailedStep;
  }

  /// A job that failed with no failed row (between components): where it
  /// stopped, in words; its own text is under Details.
  String _jobFailureText(AppLocalizations l10n, SetupProgress progress) {
    if (SetupProgressView.needsOwnershipRecovery(progress)) {
      return l10n.inAppServerOwnershipRecoveryBody;
    }
    final error = progress.error;
    final plain = error == null ? null : setupPlainMessage(l10n, error);
    if (plain != null) return plain;
    final current = progress.current;
    if (current != null) {
      return l10n.setupProgressViewFailedAt(_titleOf(current));
    }
    return l10n.setupProgressViewFailedUnknown;
  }

  /// Errors first, then the end of the log: a failed download often exits
  /// with a bare code while the log's last lines hold curl's own words.
  static bool _looksLikeNetwork(SetupProgress progress) {
    final lines = progress.logTail.trimRight().split('\n');
    final tail = lines.sublist(math.max(0, lines.length - 6)).join('\n');
    final texts = [
      progress.error,
      for (final c in progress.components)
        if (c.state == ComponentState.failed) c.error,
      tail,
    ];
    return texts.any(
      (text) => text != null && SetupProgressView.networkError.hasMatch(text),
    );
  }
}
