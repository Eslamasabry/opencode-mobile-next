import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart' show ProviderScope;

import '../../diagnostics/app_diagnostics.dart';
import '../../diagnostics/crash_diagnostics.dart';
import '../../diagnostics/device_diagnostics_gateway.dart';
import '../../diagnostics/report_problem.dart';
import '../../diagnostics/report_problem_startup.dart';
import '../../domain/app_diagnostics_gateway.dart';
import '../../feedback/problem_report.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/share_intent.dart';
import '../../state/connection.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../widgets/external_link.dart';
import 'crash_reports_section.dart';
import 'exit_history_section.dart';
import 'perf_trace_section.dart';
import 'recent_error_words.dart';

/// Opens Report a problem, prefilled with [error] when a failure brought the
/// person here. Every "Report a problem" in the app ends here (P8.2).
Future<void> openReportProblem(BuildContext context, {KitReport? error}) =>
    pushKitPage<void>(context, (_) => AppDiagnosticsScreen(error: error));

/// What the preview sheet's person chose; Copy happens inside the sheet.
enum _Send { github, share }

/// Report a problem (map: app-diagnostics, owner verdict "a first grade
/// page that users willingly use to raise bugs"): the person says what went
/// wrong, the error that brought them here is attached, recent redacted
/// diagnostics are included by choice, and Review report opens
/// report-problem-preview-sheet with exactly the text that is sent. From
/// there it goes out as a prefilled GitHub issue (through
/// [openExternalLink]; nothing is filed until the person submits the form),
/// is copied, or is shared. It replaced both old paths: the GitHub form with
/// only the version, and the server-log send.
///
/// One list under the form, newest first: the errors kept on this phone
/// (persisted across restarts by [ReportProblem] when it opened, else this
/// run's), with Clear on its header (app-diagnostics-clear-sheet), then the
/// Performance timings ([PerfTraceSection]) folded under Details.
///
/// Built from kit parts only. [controller] is optional: a page opened from
/// any error ([openReportProblem]) uses the app-wide diagnostics.
class AppDiagnosticsScreen extends StatefulWidget {
  const AppDiagnosticsScreen({
    super.key,
    this.controller,
    this.diagnostics,
    this.store,
    this.error,
    this.version,
    this.linkLauncher,
    this.share,
    this.crash,
    this.crashReady,
    this.diagnosticsGateway,
  });

  final ConnectionController? controller;

  /// The in-memory errors; defaults to the controller's, then the app's.
  final AppDiagnosticsController? diagnostics;

  /// The persisted report; defaults to the one opened at start-up.
  final ReportProblem? store;

  /// The failure that opened the page, already redacted by the kit.
  final KitReport? error;

  /// Tests: the app version instead of the platform's.
  final Future<String> Function()? version;

  /// Tests: handed to [openExternalLink].
  final Future<bool> Function(Uri uri)? linkLauncher;

  /// Tests: the share sheet; defaults to [ShareOut.text] on Android.
  final Future<bool> Function(String text, String subject)? share;

  /// Tests: the saved crash reports store; defaults to the one opened at
  /// start-up ([CrashDiagnosticsStartup]).
  final CrashDiagnosticsController? crash;

  /// Tests: when [crash] is null, the start-up result to wait for.
  final Future<CrashDiagnosticsController?>? crashReady;

  /// Tests: device diagnostics (exit history, crash report sharing);
  /// defaults to the app's [deviceDiagnosticsGatewayProvider] when there is
  /// one. Without either, those parts stay off the page.
  final AppDiagnosticsGateway? diagnosticsGateway;

  @override
  State<AppDiagnosticsScreen> createState() => _AppDiagnosticsScreenState();
}

class _AppDiagnosticsScreenState extends State<AppDiagnosticsScreen>
    with WidgetsBindingObserver {
  final _description = TextEditingController();

  AppDiagnosticsGateway? _gateway;
  bool _gatewayResolved = false;

  /// The latest exit history read (FD1); null while the first read runs.
  AppExitHistory? _exitHistory;
  int _exitRead = 0;

  /// What the person typed survives leaving the page (DATA-2); it is
  /// forgotten once the report went to GitHub or the share sheet. A report
  /// is about the app, and the page also opens with no server (a failure's
  /// details), so it is one app-wide draft, the same from every entry
  /// point, rather than one per server that a server-less entry could
  /// never sweep (KitDraft.appWide, G10).
  late final _draft = KitDraft(
    target: 'report-problem',
    profileId: KitDraft.appWide,
    controller: _description,
  );
  bool _includeDiagnostics = true;
  bool _describeFirst = false;
  bool _clearFailed = false;
  late final Future<String> _version =
      (widget.version ?? problemReportAppVersion)();

  AppDiagnosticsController? get _diagnostics =>
      widget.diagnostics ??
      widget.controller?.diagnostics ??
      ReportProblemStartup.diagnostics;

  ReportProblem? get _store =>
      widget.store ?? ReportProblemStartup.current?.report;

  Future<bool> Function(String, String)? get _share =>
      widget.share ?? (ShareOut.supported ? _shareOut : null);

  static Future<bool> _shareOut(String text, String subject) =>
      ShareOut.text(text, subject: subject);

  List<ProblemReportEvent> get _events =>
      problemReportEvents(store: _store, diagnostics: _diagnostics);

  /// The attached failed job's log, as the report's KitLogPanel shows it
  /// (P8.4); null when no job log is attached or the job kept none.
  late final ValueNotifier<List<KitLogLine>>? _jobLog =
      switch (widget.error?.log) {
        final log? when log.trim().isNotEmpty => ValueNotifier([
          for (final line in log.trimRight().split('\n')) KitLogLine(line),
        ]),
        _ => null,
      };

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_gatewayResolved) return;
    _gatewayResolved = true;
    _gateway = widget.diagnosticsGateway ?? _appGateway();
    if (_gateway == null) return;
    WidgetsBinding.instance.addObserver(this);
    unawaited(_loadExits());
  }

  /// The app's gateway, when this page runs under its ProviderScope; a
  /// scope without the live connection (previews, tests) has none.
  AppDiagnosticsGateway? _appGateway() {
    try {
      return ProviderScope.containerOf(
        context,
        listen: false,
      ).read(deviceDiagnosticsGatewayProvider);
    } catch (_) {
      return null;
    }
  }

  /// Refreshed on open and whenever the person comes back to the app.
  Future<void> _loadExits() async {
    final gateway = _gateway;
    if (gateway == null) return;
    final read = ++_exitRead;
    AppExitHistory history;
    try {
      history = await gateway.exitHistory();
    } catch (_) {
      history = AppExitHistory(
        supported: true,
        error: DiagnosticsError.unavailable,
      );
    }
    if (!mounted || read != _exitRead) return;
    setState(() => _exitHistory = history);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) unawaited(_loadExits());
  }

  @override
  void dispose() {
    if (_gateway != null) WidgetsBinding.instance.removeObserver(this);
    _description.dispose();
    _jobLog?.dispose();
    super.dispose();
  }

  Future<ProblemReport> _build() async => ProblemReport.build(
    description: _description.text,
    version: await _version,
    platform: bugReportPlatformLabel(),
    error: widget.error,
    events: _includeDiagnostics ? _events : const [],
  );

  Future<void> _review() async {
    final copy = _screenCopy(context);
    if (_description.text.trim().isEmpty && widget.error == null) {
      setState(() => _describeFirst = true);
      return;
    }
    final report = await _build();
    if (!mounted) return;
    final link = report.link();
    final share = _share;
    final choice = await showKitSheet<_Send>(
      context,
      title: copy.reportProblemReview,
      subtitle: copy.reportProblemPreviewSubtitle,
      icon: AppIconography.bug,
      height: KitSheetHeight.full,
      sheetKey: const ValueKey('report-problem-preview-sheet'),
      body: (sheetContext) {
        final tokens = KitTokens.of(sheetContext);
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitNotice(
              icon: AppIconography.info,
              message: copy.reportProblemPublicNotice,
              liveRegion: false,
            ),
            if (link.clipboard != ProblemReportClipboard.none) ...[
              SizedBox(height: tokens.space2),
              KitNotice(
                key: const ValueKey('report-problem-link-copies'),
                icon: AppIconography.copy,
                message: link.clipboard == ProblemReportClipboard.whole
                    ? copy.reportProblemLinkCopiesWhole
                    : copy.reportProblemLinkCopiesDiagnostics,
                liveRegion: false,
              ),
            ],
            SizedBox(height: tokens.space3),
            KitText.mono(
              report.text,
              key: const ValueKey('report-problem-preview-text'),
              selectable: true,
            ),
          ],
        );
      },
      primary: KitAction(
        key: const ValueKey('report-problem-open-github'),
        label: copy.reportProblemOpenGitHub,
        icon: AppIconography.externalLink,
        onPressed: () => Navigator.of(context).pop(_Send.github),
      ),
      secondary: KitAction.copy(
        key: const ValueKey('report-problem-copy'),
        label: copy.reportProblemCopy,
        text: () => report.text,
      ),
      tertiary: [
        if (share != null)
          KitAction(
            key: const ValueKey('report-problem-share'),
            label: copy.reportProblemShare,
            onPressed: () => Navigator.of(context).pop(_Send.share),
          ),
      ],
    );
    if (!mounted || choice == null) return;
    switch (choice) {
      case _Send.github:
        final paste = link.clipboardText;
        if (paste != null) {
          await KitCopy.copy(
            context,
            paste,
            announcement: link.clipboard == ProblemReportClipboard.whole
                ? copy.reportProblemCopied
                : copy.reportProblemDiagnosticsCopied,
          );
          if (!mounted) return;
        }
        final outcome = await openExternalLink(
          context,
          link.uri.toString(),
          launcher: widget.linkLauncher,
        );
        if (outcome == ExternalLinkOutcome.opened) await _draft.clear();
      case _Send.share:
        final opened = await share!(report.text, report.title);
        if (opened) await _draft.clear();
        if (!opened && mounted) {
          await KitCopy.copy(
            context,
            report.text,
            announcement: copy.reportProblemShareFallback,
          );
        }
    }
  }

  Future<void> _clear(int count) async {
    final copy = _screenCopy(context);
    // The crash store empties with App diagnostics (BD7 backend): say so
    // when there are saved crash reports to lose.
    final crashReports =
        (widget.crash ?? CrashDiagnosticsStartup.current)?.savedCount ?? 0;
    final confirmed = await showKitConfirm(
      context,
      title: copy.appDiagnosticsClearTitle(count),
      body: copy.appDiagnosticsClearBody(count),
      consequences: [if (crashReports > 0) copy.crashReportsClearedToo],
      confirmLabel: copy.appDiagnosticsClearConfirm(count),
      icon: AppIconography.delete,
      kind: KitConfirmKind.destructive,
      confirmKey: const ValueKey('clear-app-diagnostics-confirm'),
    );
    if (!confirmed || !mounted) return;
    var failed = false;
    // The capture adapter clears the saved report with the in-memory list;
    // an explicit store is cleared here too (idempotent).
    try {
      _diagnostics?.clear();
      _store?.clear();
    } on StateError {
      failed = true;
    }
    failed = failed || (_store?.storageFailed ?? false);
    setState(() => _clearFailed = failed);
  }

  String _time(DateTime value) {
    final local = value.toLocal();
    String two(int part) => part.toString().padLeft(2, '0');
    return '${local.year}-${two(local.month)}-${two(local.day)} '
        '${two(local.hour)}:${two(local.minute)}:${two(local.second)}';
  }

  @override
  Widget build(BuildContext context) {
    final copy = _screenCopy(context);
    final tokens = KitTokens.of(context);
    final sources = <Listenable>[?_diagnostics, ?_store];
    return KitScreen(
      topBar: KitTopBar(title: copy.e7LibraryReportABug),
      width: KitScreenWidth.reading,
      body: ListenableBuilder(
        listenable: Listenable.merge(sources),
        builder: (context, _) {
          final events = _events;
          // Android's exits show once: in Recent app exits when that list
          // could be read, else here.
          final exitsListed = switch (_exitHistory) {
            final history? => history.supported && history.error == null,
            null => false,
          };
          // Crash records show once, in plain words, in their own section
          // below (CrashReportsSection); the report still carries them.
          final errors = events
              .where(
                (event) =>
                    event.isError &&
                    !_isCrashRecord(event) &&
                    !(exitsListed && _isAndroidExit(event)),
              )
              .toList();
          final error = widget.error;
          return ListView(
            key: const ValueKey('app-diagnostics'),
            padding: EdgeInsetsDirectional.only(
              top: tokens.space2,
              bottom: KitScreen.endPadding(context),
            ),
            children: [
              Padding(
                padding: EdgeInsetsDirectional.symmetric(
                  horizontal: tokens.gutter,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    KitText(
                      copy.reportProblemIntro,
                      role: KitTextRole.secondary,
                    ),
                    if (error != null) ...[
                      SizedBox(height: tokens.space3),
                      KitNotice(
                        key: const ValueKey('report-problem-attached'),
                        tone: AppStatusTone.failure,
                        icon: AppIconography.error,
                        message: copy.reportProblemAttached(error.title),
                        liveRegion: false,
                      ),
                      // A failed job carries its log: shown as it goes in
                      // the report, or said to be missing (P8.4).
                      if (_jobLog case final log?) ...[
                        SizedBox(height: tokens.space2),
                        KitLogPanel(
                          panelKey: const ValueKey('report-problem-job-log'),
                          lines: log,
                          title: copy.reportProblemJobLog,
                          ended: const KitLogEnd(failed: true),
                        ),
                      ] else if (error.log != null) ...[
                        SizedBox(height: tokens.space2),
                        KitText(
                          copy.reportProblemJobLogNone,
                          key: const ValueKey('report-problem-job-log-none'),
                          role: KitTextRole.secondary,
                        ),
                      ],
                    ],
                    SizedBox(height: tokens.space3),
                    KitField(
                      fieldKey: const ValueKey('report-problem-description'),
                      label: copy.reportProblemDescribeLabel,
                      hint: copy.reportProblemDescribeHint,
                      kind: KitFieldKind.multiline,
                      draft: _draft,
                      error: _describeFirst
                          ? copy.reportProblemDescribeFirst
                          : null,
                      onChanged: (_) {
                        if (_describeFirst) {
                          setState(() => _describeFirst = false);
                        }
                      },
                    ),
                  ],
                ),
              ),
              if (events.isNotEmpty) ...[
                SizedBox(height: tokens.space2),
                KitRowGroup(
                  children: [
                    KitSwitchRow(
                      switchKey: const ValueKey('report-problem-include'),
                      title: copy.reportProblemIncludeDiagnostics,
                      supporting: copy.reportProblemIncludeDiagnosticsBody(
                        events.length,
                      ),
                      value: _includeDiagnostics,
                      onChanged: (value) =>
                          setState(() => _includeDiagnostics = value),
                    ),
                  ],
                ),
              ],
              SizedBox(height: tokens.space4),
              Padding(
                padding: EdgeInsetsDirectional.symmetric(
                  horizontal: tokens.gutter,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    KitButton.primary(
                      key: const ValueKey('report-problem-review'),
                      onPressed: () => unawaited(_review()),
                      icon: AppIconography.bug,
                      label: copy.reportProblemReview,
                    ),
                    SizedBox(height: tokens.space1),
                    KitText(
                      copy.reportProblemReviewHint,
                      role: KitTextRole.secondary,
                    ),
                    if (_clearFailed) ...[
                      SizedBox(height: tokens.space2),
                      KitNotice(
                        key: const ValueKey('report-problem-clear-failed'),
                        tone: AppStatusTone.failure,
                        icon: AppIconography.error,
                        message: copy.reportProblemClearFailed,
                      ),
                    ],
                  ],
                ),
              ),
              if (errors.isNotEmpty) ...[
                SizedBox(height: tokens.sectionGap),
                KitRowGroup(
                  label: copy.reportProblemErrorsLabel(errors.length),
                  // Clear acts on exactly these errors, so it sits on this
                  // list's header and names its count.
                  labelTrailing: KitIconButton(
                    key: const ValueKey('clear-app-diagnostics'),
                    icon: AppIconography.delete,
                    size: 20,
                    tooltip: copy.appDiagnosticsClearConfirm(errors.length),
                    onPressed: () => unawaited(_clear(errors.length)),
                  ),
                  children: [
                    for (final (index, entry) in errors.indexed)
                      KitExpandRow(
                        key: ValueKey('diagnostic-entry-${entry.id ?? index}'),
                        leading: KitRow.icon(context, AppIconography.error),
                        // Plain words by where it came from; the message
                        // and source id are technical: Details only.
                        title: recentErrorTitle(copy, entry),
                        titleMaxLines: 2,
                        supporting: TextSpan(
                          text: [
                            KitBidi.ltr(_time(entry.time)),
                            if (entry.occurrences > 1)
                              copy.e7SettingsDiagnosticOccurrences(
                                entry.occurrences,
                              ),
                          ].join(' · '),
                        ),
                        supportingMaxLines: 2,
                        children: [
                          Padding(
                            padding: EdgeInsetsDirectional.only(
                              start: tokens.gutter,
                              end: tokens.gutter,
                              bottom: tokens.space3,
                            ),
                            child: KitDetailsFold(
                              foldKey: ValueKey(
                                'diagnostic-details-${entry.id ?? index}',
                              ),
                              initiallyExpanded: true,
                              values: [
                                KitTechnicalValue(
                                  copy.crashReportDetailSource,
                                  entry.source,
                                ),
                              ],
                              text: [
                                entry.message,
                                if (entry.stack.isNotEmpty) entry.stack,
                              ].join('\n\n'),
                            ),
                          ),
                        ],
                      ),
                  ],
                ),
              ],
              // Opt-in crash reports (BD7): off until the person turns
              // them on; their saved rows and delete action live here.
              CrashReportsSection(
                crash: widget.crash,
                ready: widget.crashReady,
                savedErrors: errors.length,
                gateway: _gateway,
              ),
              // FD1: Android's record of each time the app closed.
              ExitHistorySection(
                history: _exitHistory,
                onRetry: () => unawaited(_loadExits()),
              ),
              SizedBox(height: tokens.sectionGap),
              // Timings are for whoever reads the report, not the person
              // filling it in: folded, last, still part of the report.
              Padding(
                padding: EdgeInsetsDirectional.symmetric(
                  horizontal: tokens.gutter,
                ),
                child: const KitDetailsFold(child: PerfTraceSection()),
              ),
            ],
          );
        },
      ),
    );
  }
}

/// An entry the opt-in crash store recorded (`crash.flutter`, `crash.anr`,
/// ...): listed by [CrashReportsSection], not in the recent errors. The
/// native summary (`crash.last`) is not one and stays listed.
bool _isCrashRecord(ProblemReportEvent event) =>
    crashStoreSources.contains(event.source);

/// An exit Android recorded (startup recovery), listed by
/// [ExitHistorySection] when that list could be read.
bool _isAndroidExit(ProblemReportEvent event) =>
    event.kind == ProblemEventKind.androidExit ||
    event.source == 'android.exit';

/// The count on Settings' Report a problem row: errors kept on this phone
/// (the saved report when it opened, else this run's).
int reportProblemErrorCount({
  ReportProblem? store,
  AppDiagnosticsController? diagnostics,
}) => problemReportEvents(
  store: store ?? ReportProblemStartup.current?.report,
  diagnostics: diagnostics ?? ReportProblemStartup.diagnostics,
).where((event) => event.isError).length;

AppLocalizations _screenCopy(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(const Locale('en'));
