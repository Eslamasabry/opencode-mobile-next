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
import '../../state/connection.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import 'crash_reports_section.dart';
import 'exit_history_section.dart';
import 'perf_trace_section.dart';
import 'recent_error_words.dart';

/// App diagnostics: everything this phone kept, one tap under Report a
/// problem's Details (vision 2026-10-20, "B, trimmed"). Report a problem
/// itself only says what went wrong and sends it; the lists that used to
/// sit under its form live here, unchanged:
///
/// the errors kept on this phone, newest first (persisted across restarts
/// by [ReportProblem] when it opened, else this run's), with Clear on
/// their header (app-diagnostics-clear-sheet); the opt-in crash reports
/// ([CrashReportsSection]); Android's record of each time the app closed
/// ([ExitHistorySection]); and the Performance timings
/// ([PerfTraceSection]) folded under Details. The report carries the same
/// events; nothing here sends anything.
///
/// Built from kit parts only.
class AppDiagnosticsKeptScreen extends StatefulWidget {
  const AppDiagnosticsKeptScreen({
    super.key,
    this.controller,
    this.diagnostics,
    this.store,
    this.crash,
    this.crashReady,
    this.diagnosticsGateway,
    this.clock,
  });

  final ConnectionController? controller;

  /// The in-memory errors; defaults to the controller's, then the app's.
  final AppDiagnosticsController? diagnostics;

  /// The persisted report; defaults to the one opened at start-up.
  final ReportProblem? store;

  /// Tests: the saved crash reports store; defaults to the one opened at
  /// start-up ([CrashDiagnosticsStartup]).
  final CrashDiagnosticsController? crash;

  /// Tests: when [crash] is null, the start-up result to wait for.
  final Future<CrashDiagnosticsController?>? crashReady;

  /// Tests: device diagnostics (exit history, crash report sharing);
  /// defaults to the app's [deviceDiagnosticsGatewayProvider] when there is
  /// one. Without either, those parts stay off the page.
  final AppDiagnosticsGateway? diagnosticsGateway;

  /// Tests: the moment exit times count "Today" and "Yesterday" from.
  final DateTime Function()? clock;

  @override
  State<AppDiagnosticsKeptScreen> createState() =>
      _AppDiagnosticsKeptScreenState();
}

class _AppDiagnosticsKeptScreenState extends State<AppDiagnosticsKeptScreen>
    with WidgetsBindingObserver {
  AppDiagnosticsGateway? _gateway;
  bool _gatewayResolved = false;

  /// The latest exit history read (FD1); null while the first read runs.
  AppExitHistory? _exitHistory;
  int _exitRead = 0;
  bool _clearFailed = false;

  AppDiagnosticsController? get _diagnostics =>
      widget.diagnostics ??
      widget.controller?.diagnostics ??
      ReportProblemStartup.diagnostics;

  ReportProblem? get _store =>
      widget.store ?? ReportProblemStartup.current?.report;

  List<ProblemReportEvent> get _events =>
      problemReportEvents(store: _store, diagnostics: _diagnostics);

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
      // The most Android gives, so problem exits are found among routine
      // updates; the section shows the problems and folds the rest.
      history = await gateway.exitHistory(limit: 50);
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
    super.dispose();
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
      topBar: KitTopBar(title: copy.e7SettingsUi88),
      width: KitScreenWidth.reading,
      body: ListenableBuilder(
        listenable: Listenable.merge(sources),
        builder: (context, _) {
          // Android's exits show once: in Recent app exits when that list
          // could be read, else here.
          final exitsListed = switch (_exitHistory) {
            final history? => history.supported && history.error == null,
            null => false,
          };
          // Crash records show once, in plain words, in their own section
          // below (CrashReportsSection); the report still carries them.
          final errors = _events
              .where(
                (event) =>
                    event.isError &&
                    !isCrashStoreRecord(event) &&
                    !(exitsListed && isAndroidExitRecord(event)),
              )
              .toList();
          return ListView(
            key: const ValueKey('app-diagnostics'),
            padding: EdgeInsetsDirectional.only(
              top: tokens.space2,
              bottom: KitScreen.endPadding(context),
            ),
            children: [
              if (_clearFailed)
                Padding(
                  padding: EdgeInsetsDirectional.only(
                    start: tokens.gutter,
                    end: tokens.gutter,
                    bottom: tokens.space3,
                  ),
                  child: KitNotice(
                    key: const ValueKey('report-problem-clear-failed'),
                    tone: AppStatusTone.failure,
                    icon: AppIconography.error,
                    message: copy.reportProblemClearFailed,
                  ),
                ),
              if (errors.isNotEmpty)
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
                clock: widget.clock,
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
bool isCrashStoreRecord(ProblemReportEvent event) =>
    crashStoreSources.contains(event.source);

/// An exit Android recorded (startup recovery), listed by
/// [ExitHistorySection] when that list could be read.
bool isAndroidExitRecord(ProblemReportEvent event) =>
    event.kind == ProblemEventKind.androidExit ||
    event.source == 'android.exit';

AppLocalizations _screenCopy(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(const Locale('en'));
