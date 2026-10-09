import '../builtin/setup/setup_contract.dart';
import '../builtin/team/builtin_team_job.dart';
import '../domain/development_service.dart';
import '../domain/managed_shell.dart';
import '../domain/orchestration_gateway.dart';
import '../ui/kit/kit_notice.dart' show KitReport;
import '../ui/kit/kit_redact.dart';

enum FailedJobKind { setup, teamSetup, teamWork, teamRun, developmentService }

/// An immutable, redacted preview. Creating one neither saves nor uploads it.
///
/// Register loaded credentials with KitRedact before capture. All fields are
/// redacted before bounding, including the complete multiline log. The log is
/// an excerpt, not a promise that the host retained the complete failed job.
/// Keep the same snapshot for preview and the user's explicit Copy/Share.
class FailedJobReport {
  FailedJobReport._({
    required this.kind,
    required String jobId,
    required String title,
    String detail = '',
    String log = '',
  }) : jobId = _bounded(jobId, 256),
       title = _bounded(title, 256),
       detail = _bounded(detail, 2048),
       logExcerpt = _excerpt(log);

  static const maxLogLines = 120;
  static const maxLogCharacters = 16384;

  final FailedJobKind kind;
  final String jobId;
  final String title;
  final String detail;
  final String logExcerpt;
  bool get hasLog => logExcerpt.isNotEmpty;

  /// Plain technical attachment; localize the surrounding UI, not this text.
  /// The UI must show this exact text before offering Copy or Share.
  String get preview => [
    'OpenCode Mobile failed job',
    'Kind: ${kind.name}',
    'Job: $jobId',
    'Title: $title',
    if (detail.isNotEmpty) 'Detail: $detail',
    hasLog ? 'Log excerpt:\n$logExcerpt' : 'Log excerpt: unavailable',
  ].join('\n');

  /// This snapshot as the Report a problem page's attachment (P8.4): the
  /// page shows [logExcerpt] in a KitLogPanel and puts it in the report,
  /// or says no log was kept. [title] is the failure as the surface that
  /// offered Report words it (localized); null keeps [title].
  KitReport toKitReport({String? title}) => KitReport(
    title: title == null || title.trim().isEmpty ? this.title : title,
    details: detail.isEmpty ? null : detail,
    source: 'failed job · ${kind.name} · $jobId',
    log: logExcerpt,
  );

  /// A v2 failed row receives the job's log, never a different component's
  /// invented log. Omitting [componentId] captures the whole failed job.
  static FailedJobReport? setup(SetupProgress progress, {String? componentId}) {
    if (progress.state != SetupState.failed) return null;
    ComponentProgress? component;
    if (componentId != null) {
      for (final item in progress.components) {
        if (item.id == componentId) component = item;
      }
      if (component == null || component.state != ComponentState.failed) {
        return null;
      }
    }
    return FailedJobReport._(
      kind: FailedJobKind.setup,
      jobId: progress.jobId ?? 'unknown',
      title: componentId ?? progress.current ?? 'setup',
      detail: component?.error ?? progress.error ?? '',
      log: progress.logTail,
    );
  }

  /// Built-in team startup failures already carry script/service log detail
  /// in their exception. No raw exception is retained by the snapshot.
  static FailedJobReport? teamSetup(BuiltinTeamJob job) {
    if (job.running || job.error == null) return null;
    return FailedJobReport._(
      kind: FailedJobKind.teamSetup,
      jobId: job.stage?.name ?? 'unknown',
      title: 'AI Team setup',
      log: job.error.toString(),
    );
  }

  /// [sessionId] is the identity attached to [logTail], not an agent chosen
  /// by display name. A mismatched/missing session produces no log attachment.
  static FailedJobReport? teamWork(
    WorkItem work, {
    String? sessionId,
    String logTail = '',
  }) {
    if (work.state != WorkState.failed) return null;
    return FailedJobReport._(
      kind: FailedJobKind.teamWork,
      jobId: work.id,
      title: work.title,
      log: _workLog(work, sessionId, logTail),
    );
  }

  /// The Run failed gate can always offer Report, including when its host
  /// cannot supply output. Attach only a work item explicitly linked to it.
  /// Never substitute the currently active session of a reused agent.
  static FailedJobReport? teamGate(
    OrchestrationGate gate, {
    WorkItem? work,
    String? sessionId,
    String logTail = '',
  }) {
    if (gate.kind != GateKind.runFailed) return null;
    final linked =
        work != null &&
        (gate.workId != null
            ? gate.workId == work.id &&
                  (gate.runId == null || gate.runId == work.runId)
            : gate.runId != null && gate.runId == work.runId);
    return FailedJobReport._(
      kind: FailedJobKind.teamRun,
      jobId: gate.runId ?? gate.id,
      title: gate.title,
      detail: gate.prompt ?? '',
      log: linked ? _workLog(work, sessionId, logTail) : '',
    );
  }

  /// Requires an owned, terminal failure. Unknown exits and deliberate stops
  /// are not failures. Ownership tokens and commands are never exported.
  static FailedJobReport? developmentService(
    DevelopmentService service,
    ManagedShell shell, {
    String logTail = '',
  }) {
    final run = service.run;
    if (run == null ||
        run.stopped ||
        run.ownerToken != shell.ownerToken ||
        run.shellID != shell.id ||
        run.startedAt != shell.startedAt.millisecondsSinceEpoch ||
        service.command != shell.command ||
        service.directory != shell.directory) {
      return null;
    }
    final failed =
        shell.status == ManagedShellStatus.timeout ||
        shell.status == ManagedShellStatus.killed ||
        (shell.status == ManagedShellStatus.exited &&
            shell.exitCode != null &&
            shell.exitCode != 0);
    if (!failed) return null;
    return FailedJobReport._(
      kind: FailedJobKind.developmentService,
      jobId: shell.id,
      title: service.name,
      detail:
          '${shell.status.name}'
          '${shell.exitCode == null ? '' : ' (exit ${shell.exitCode})'}',
      log: logTail,
    );
  }

  static String _workLog(WorkItem work, String? sessionId, String log) =>
      sessionId != null && sessionId.isNotEmpty && work.sessionId == sessionId
      ? log
      : '';

  static String _clean(String value) => KitRedact.opaqueTokens(
    KitRedact.text(
      value
          .replaceAll(RegExp(r'\x1B\][^\x07]*(?:\x07|\x1B\\)'), '')
          .replaceAll(RegExp(r'\x1B\[[0-?]*[ -/]*[@-~]'), '')
          .replaceAll('\r\n', '\n')
          .replaceAll('\r', '\n'),
    ),
  );

  static String _bounded(String value, int limit) {
    final safe = _clean(value);
    return safe.length <= limit ? safe : safe.substring(0, limit);
  }

  // Timing lines stay in the device log and diagnostics: in a failed job's
  // excerpt they would push the real error out of its last lines.
  static String _excerpt(String log) {
    final lines = _clean(setupLogForPeople(log)).trimRight().split('\n');
    final tail = lines
        .skip(lines.length > maxLogLines ? lines.length - maxLogLines : 0)
        .join('\n');
    return tail.length <= maxLogCharacters
        ? tail
        : tail.substring(tail.length - maxLogCharacters);
  }
}
