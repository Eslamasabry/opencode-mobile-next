import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart' show ProviderScope;

import '../../diagnostics/app_diagnostics.dart';
import '../../diagnostics/crash_diagnostics.dart';
import '../../diagnostics/report_problem.dart';
import '../../diagnostics/report_problem_startup.dart';
import '../../domain/app_diagnostics_gateway.dart';
import '../../domain/connection_status.dart';
import '../../feedback/problem_report.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/profiles.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../widgets/external_link.dart';
import '../widgets/termux_running_server_entry.dart' show isPhoneOwnServer;
import 'app_diagnostics_kept_screen.dart';
import 'recent_error_words.dart';

export 'app_diagnostics_kept_screen.dart' show AppDiagnosticsKeptScreen;

/// Opens Report a problem, prefilled with [error] when a failure brought the
/// person here. Every "Report a problem" in the app ends here (P8.2).
Future<void> openReportProblem(BuildContext context, {KitReport? error}) =>
    pushKitPage<void>(context, (_) => AppDiagnosticsScreen(error: error));

/// Report a problem (vision 2026-10-20, "B, trimmed"): the report arrives
/// already knowing what went wrong, and the person only adds words if they
/// want.
///
/// One calm card says it in plain words: the failure that opened the page
/// ([error]), else the newest error kept on this phone, else that the app
/// noticed none (then the words are required). Under it, what goes with the
/// report, and Details, which holds exactly the text that leaves the phone
/// (the same text Copy copies and GitHub's form receives), then
/// "Everything this phone kept" ([AppDiagnosticsKeptScreen]). Then the
/// privacy line, the optional field and, pinned at the thumb, the one
/// primary "Send as a GitHub issue" (through [openExternalLink], which asks
/// first; nothing is filed until the person submits GitHub's form) with
/// Copy beside it. Nothing leaves the phone before one of those taps.
///
/// The report carries the app version, the platform, the connection in use
/// in words (never its address or name) and the recent redacted
/// diagnostics. Built from kit parts only. [controller] is optional: a page
/// opened from any error ([openReportProblem]) reads the app's connection
/// and diagnostics.
class AppDiagnosticsScreen extends StatefulWidget {
  const AppDiagnosticsScreen({
    super.key,
    this.controller,
    this.diagnostics,
    this.store,
    this.error,
    this.version,
    this.linkLauncher,
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

  /// The failure that opened the page, already redacted by the kit.
  final KitReport? error;

  /// Tests: the app version instead of the platform's.
  final Future<String> Function()? version;

  /// Tests: handed to [openExternalLink].
  final Future<bool> Function(Uri uri)? linkLauncher;

  /// Handed to [AppDiagnosticsKeptScreen] (tests: the crash store).
  final CrashDiagnosticsController? crash;

  /// Handed to [AppDiagnosticsKeptScreen].
  final Future<CrashDiagnosticsController?>? crashReady;

  /// Handed to [AppDiagnosticsKeptScreen] (exit history, crash sharing).
  final AppDiagnosticsGateway? diagnosticsGateway;

  /// Handed to [AppDiagnosticsKeptScreen].
  final DateTime Function()? clock;

  @override
  State<AppDiagnosticsScreen> createState() => _AppDiagnosticsScreenState();
}

class _AppDiagnosticsScreenState extends State<AppDiagnosticsScreen> {
  final _description = TextEditingController();

  /// What the person typed survives leaving the page (DATA-2); it is
  /// forgotten once the report went to GitHub. A report is about the app,
  /// and the page also opens with no server (a failure's details), so it is
  /// one app-wide draft, the same from every entry point, rather than one
  /// per server that a server-less entry could never sweep
  /// (KitDraft.appWide, G10).
  late final _draft = KitDraft(
    target: 'report-problem',
    profileId: KitDraft.appWide,
    controller: _description,
  );
  bool _describeFirst = false;
  bool _connectionRead = false;
  ConnectionController? _connection;

  /// The app version once the platform answered; null until then.
  String? _version;

  AppDiagnosticsController? get _diagnostics =>
      widget.diagnostics ??
      widget.controller?.diagnostics ??
      ReportProblemStartup.diagnostics;

  ReportProblem? get _store =>
      widget.store ?? ReportProblemStartup.current?.report;

  List<ProblemReportEvent> get _events =>
      problemReportEvents(store: _store, diagnostics: _diagnostics);

  @override
  void initState() {
    super.initState();
    unawaited(
      (widget.version ?? () => problemReportAppVersion(timeout: null))().then((
        version,
      ) {
        if (mounted) setState(() => _version = version);
      }),
    );
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_connectionRead) return;
    _connectionRead = true;
    _connection = widget.controller ?? _appConnection();
  }

  /// The app's connection, when this page runs under its ProviderScope; a
  /// scope without one (previews, tests) has none.
  ConnectionController? _appConnection() {
    try {
      return ProviderScope.containerOf(
        context,
        listen: false,
      ).read(connProvider);
    } catch (_) {
      return null;
    }
  }

  @override
  void dispose() {
    _description.dispose();
    super.dispose();
  }

  /// The newest error kept on this phone, as Report a problem names it.
  ProblemReportEvent? get _newestError {
    for (final event in _events) {
      if (event.isError) return event;
    }
    return null;
  }

  ProblemReport _build(String version, AppLocalizations copy) {
    final kept = widget.error == null ? _newestError : null;
    return ProblemReport.build(
      description: _description.text,
      version: version,
      platform: bugReportPlatformLabel(),
      error: widget.error,
      connection: _connectionWords(_connection),
      fallbackTitle: kept == null ? null : recentErrorTitle(copy, kept),
      events: _events,
    );
  }

  /// Words are needed only when nothing says what went wrong.
  bool get _needsWords => widget.error == null && _newestError == null;

  Future<void> _send(ProblemReport report) async {
    if (_needsWords && _description.text.trim().isEmpty) {
      setState(() => _describeFirst = true);
      return;
    }
    final copy = _screenCopy(context);
    final link = report.link();
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
  }

  void _openKept() => unawaited(
    pushKitPage<void>(
      context,
      (_) => AppDiagnosticsKeptScreen(
        controller: widget.controller,
        diagnostics: widget.diagnostics,
        store: widget.store,
        crash: widget.crash,
        crashReady: widget.crashReady,
        diagnosticsGateway: widget.diagnosticsGateway,
        clock: widget.clock,
      ),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final copy = _screenCopy(context);
    final sources = <Listenable>[?_diagnostics, ?_store, _description];
    return ListenableBuilder(
      listenable: Listenable.merge(sources),
      builder: (context, _) {
        // Until the platform answers (at once on a phone), the version is
        // honestly unknown; no timer waits on it.
        final report = _build(_version ?? 'unknown+0', copy);
        // Large text or a short window: the actions end the page instead
        // of holding half of it at the bottom.
        final inline =
            MediaQuery.textScalerOf(context).scale(16) > 16 * 1.5 ||
            MediaQuery.sizeOf(context).height < 560;
        final actions = _bottom(context, copy, report);
        return KitScreen(
          topBar: KitTopBar(title: copy.e7LibraryReportABug),
          width: KitScreenWidth.reading,
          bottom: inline ? null : actions,
          body: _body(context, copy, report, inline ? actions : null),
        );
      },
    );
  }

  Widget _body(
    BuildContext context,
    AppLocalizations copy,
    ProblemReport report,
    Widget? actions,
  ) {
    final tokens = KitTokens.of(context);
    final error = widget.error;
    final kept = error == null ? _newestError : null;
    final String title;
    final String lead;
    if (error != null) {
      title = error.title;
      lead = switch (error.log) {
        null => copy.reportProblemAttachedLead,
        final log when log.trim().isEmpty => copy.reportProblemJobNoLogLead,
        _ => copy.reportProblemJobLead,
      };
    } else if (kept != null) {
      title = recentErrorTitle(copy, kept);
      lead = copy.reportProblemKeptLead(KitTime.moment(context, kept.time));
    } else {
      title = copy.reportProblemNothingTitle;
      lead = copy.reportProblemNothingLead;
    }
    final needsWords = _needsWords;
    return ListView(
      key: const ValueKey('app-diagnostics-report'),
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        end: tokens.gutter,
        top: tokens.space2,
        bottom: KitScreen.endPadding(context),
      ),
      children: [
        // On the page itself in Solid, like its lists; a card elsewhere.
        KitSurface(
          key: const ValueKey('report-problem-summary'),
          level: KitSurfaceLevel.surface2,
          outlined: true,
          flatInLists: true,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Align(
                alignment: AlignmentDirectional.centerStart,
                child: KitSurface.tile(AppIconography.bug),
              ),
              SizedBox(height: tokens.space3),
              Semantics(
                header: true,
                child: KitText(
                  title,
                  key: const ValueKey('report-problem-title'),
                  // From large text the headline face, so a long word
                  // keeps whole on a phone.
                  role: actions != null
                      ? KitTextRole.headline
                      : KitTextRole.title,
                ),
              ),
              SizedBox(height: tokens.space2),
              KitText(
                lead,
                key: const ValueKey('report-problem-lead'),
                role: KitTextRole.body,
                tone: KitTextTone.secondary,
              ),
              SizedBox(height: tokens.space3),
              const KitDivider(),
              SizedBox(height: tokens.space1),
              KitDetailsFold(
                foldKey: const ValueKey('report-problem-details'),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    KitText.mono(
                      report.text,
                      key: const ValueKey('report-problem-preview-text'),
                      selectable: true,
                    ),
                    SizedBox(height: tokens.space2),
                    Align(
                      alignment: AlignmentDirectional.centerStart,
                      child: KitButton.tertiary(
                        key: const ValueKey('report-problem-kept'),
                        label: copy.reportProblemKeptLink,
                        icon: AppIconography.chevronRight,
                        onPressed: _openKept,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        SizedBox(height: tokens.space4),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const KitIcon(
              AppIconography.privacy,
              size: KitIconSize.medium,
              tone: KitTextTone.secondary,
            ),
            SizedBox(width: tokens.space3),
            Expanded(
              child: KitText(
                copy.reportProblemPrivacy,
                key: const ValueKey('report-problem-privacy'),
                role: KitTextRole.secondary,
                tone: KitTextTone.secondary,
              ),
            ),
          ],
        ),
        SizedBox(height: tokens.space5),
        KitField(
          fieldKey: const ValueKey('report-problem-description'),
          label: needsWords
              ? copy.reportProblemDescribeLabel
              : copy.reportProblemAddLabel,
          hint: needsWords
              ? copy.reportProblemDescribeHint
              : copy.reportProblemAddHint,
          optional: !needsWords,
          kind: KitFieldKind.multiline,
          minLines: 3,
          draft: _draft,
          error: _describeFirst ? copy.reportProblemDescribeFirst : null,
          onChanged: (_) {
            if (_describeFirst) setState(() => _describeFirst = false);
          },
        ),
        if (actions != null) ...[SizedBox(height: tokens.space6), actions],
      ],
    );
  }

  Widget _bottom(
    BuildContext context,
    AppLocalizations copy,
    ProblemReport report,
  ) {
    final tokens = KitTokens.of(context);
    final link = report.link();
    final copies = link.clipboard != ProblemReportClipboard.none;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // The line says what Send will do; it changes (cross-fading) when
        // the words make the link too long to carry the diagnostics.
        KitSwap(
          alignment: AlignmentDirectional.centerStart,
          child: KitText(
            copies
                ? link.clipboard == ProblemReportClipboard.whole
                      ? copy.reportProblemLinkCopiesWhole
                      : copy.reportProblemLinkCopiesDiagnostics
                : copy.reportProblemSendHint,
            key: ValueKey(
              copies
                  ? 'report-problem-link-copies'
                  : 'report-problem-send-hint',
            ),
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
        ),
        SizedBox(height: tokens.space3),
        KitActionBlock(
          pair: true,
          primary: KitAction(
            key: const ValueKey('report-problem-send'),
            label: copy.reportProblemSendGitHub,
            icon: AppIconography.github,
            onPressed: () => unawaited(_send(report)),
          ),
          secondary: KitAction.copy(
            key: const ValueKey('report-problem-copy'),
            label: copy.reportProblemCopyShort,
            text: () => report.text,
          ),
        ),
      ],
    );
  }
}

/// The connection in use, in words a public report may carry: what it is,
/// where it runs and how it is doing; never its address, name or folder.
String _connectionWords(ConnectionController? controller) {
  final profile = controller?.profile;
  if (controller == null || profile == null) return 'none';
  final version = switch (profile.serverVersion) {
    final raw? => RegExp(r'\d+\.\d+\.\d+').firstMatch(raw)?.group(0),
    null => null,
  };
  final what = switch (profile.backend) {
    ServerBackend.openCode =>
      version == null ? 'OpenCode 2' : 'OpenCode $version',
    ServerBackend.codex => version == null ? 'Codex' : 'Codex $version',
    ServerBackend.paseo => version == null ? 'Paseo' : 'Paseo $version',
  };
  final where = isPhoneOwnServer(profile)
      ? 'on this phone'
      : 'on another computer';
  final state = switch (controller.connectionStatus.phase) {
    ConnectionStatusPhase.connected => 'connected',
    ConnectionStatusPhase.connecting => 'connecting',
    ConnectionStatusPhase.reconnecting => 'reconnecting',
    ConnectionStatusPhase.serverBusy => 'busy',
    ConnectionStatusPhase.hidden => null,
    ConnectionStatusPhase.credentialsRequired ||
    ConnectionStatusPhase.credentialsUnreadable => 'needs sign-in',
    _ => 'offline',
  };
  return [what, where, ?state].join(' · ');
}

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
