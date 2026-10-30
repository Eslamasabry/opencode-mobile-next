import 'package:intl/intl.dart';

import '../domain/models.dart' show Todo;
import '../l10n/app_localizations.dart';
import 'orchestration_gateway.dart' show WorkItem, WorkState;
import 'relative_age.dart';
import 'run_result.dart';

/// Shared vocabulary for a row, its header and notification copy.
enum WorkRowPhase {
  working,
  needsYou,
  stalled,
  done,
  failed,
  stopped,
  waiting,
  queued,
  ready,
  review,
  unknown,
}

/// Only measured progress: cancelled/skipped steps are not completed steps.
class WorkRowSteps {
  const WorkRowSteps({required this.completed, required this.total});
  final int completed;
  final int total;
  bool get isValid => total > 0 && completed >= 0 && completed <= total;
}

/// Credential-free facts for one chat turn or team task. No titles, tool input,
/// raw errors or provider payloads are retained; nothing is persisted.
class WorkRowFacts {
  const WorkRowFacts({
    required this.phase,
    this.steps,
    this.startedAt,
    this.finishedAt,
  });

  factory WorkRowFacts.chat({
    required bool busy,
    required bool needsYou,
    RunResult? result,
    Iterable<Todo>? todos,
    // This app stopped the latest turn: OpenCode 2 reports that as a normal
    // finish, which is not "Done".
    bool stoppedByPerson = false,
  }) {
    final plan = todos?.toList();
    final phase = needsYou
        ? WorkRowPhase.needsYou
        : busy
        ? WorkRowPhase.working
        : switch (result?.outcome.kind) {
            RunOutcomeKind.completed when stoppedByPerson =>
              WorkRowPhase.stopped,
            RunOutcomeKind.completed => WorkRowPhase.done,
            RunOutcomeKind.failed ||
            RunOutcomeKind.cutOff => WorkRowPhase.failed,
            RunOutcomeKind.aborted => WorkRowPhase.stopped,
            // Idleness and old running messages do not prove completion.
            _ => WorkRowPhase.unknown,
          };
    return WorkRowFacts(
      phase: phase,
      steps: plan == null || plan.isEmpty
          ? null
          : WorkRowSteps(
              completed: plan.where((todo) => todo.done).length,
              total: plan.length,
            ),
      // A previous result is not the start time of a newly busy turn.
      startedAt: result?.outcome.kind == RunOutcomeKind.running
          ? result?.startedAt
          : null,
      finishedAt: phase == WorkRowPhase.done ? result?.finishedAt : null,
    );
  }

  factory WorkRowFacts.team({
    required WorkItem item,
    bool needsYou = false,
    // Supply the current task's P3.5 TeamNowKind.stalled or dispatch-cycle
    // evidence. Row age and transport silence alone never establish a stall.
    bool stalled = false,
    WorkRowSteps? steps,
    DateTime? startedAt,
    DateTime? finishedAt,
  }) => WorkRowFacts(
    phase: needsYou
        ? WorkRowPhase.needsYou
        : switch (item.state) {
            WorkState.needsInput => WorkRowPhase.needsYou,
            WorkState.completed => WorkRowPhase.done,
            WorkState.failed => WorkRowPhase.failed,
            WorkState.cancelled => WorkRowPhase.stopped,
            _ when stalled => WorkRowPhase.stalled,
            WorkState.working => WorkRowPhase.working,
            WorkState.waiting || WorkState.blocked => WorkRowPhase.waiting,
            WorkState.queued => WorkRowPhase.queued,
            WorkState.ready => WorkRowPhase.ready,
            WorkState.review => WorkRowPhase.review,
            WorkState.unknown => WorkRowPhase.unknown,
          },
    steps: steps,
    // createdAt/updatedAt are not evidence of a run starting/finishing.
    startedAt: startedAt,
    finishedAt: finishedAt,
  );

  final WorkRowPhase phase;
  final WorkRowSteps? steps;
  final DateTime? startedAt;
  final DateTime? finishedAt;
}

/// One rendering source. Reuse [word] in headers/notifications and [line] in
/// Work/Inbox. A stale projection never permits a live or animated mark.
class WorkRowStatus {
  const WorkRowStatus({
    required this.facts,
    required this.observedAt,
    required this.isFresh,
  });
  final WorkRowFacts facts;
  final DateTime observedAt;
  final bool isFresh;
  bool get showLiveMark => isFresh && facts.phase == WorkRowPhase.working;

  String word(AppLocalizations l10n) => switch (facts.phase) {
    WorkRowPhase.working => l10n.kitMarkWorking,
    WorkRowPhase.needsYou => l10n.e7WorkspaceNeedsYou,
    WorkRowPhase.stalled => l10n.workStalled,
    WorkRowPhase.done => l10n.kitMarkDone,
    WorkRowPhase.failed => l10n.kitMarkFailed,
    WorkRowPhase.stopped => l10n.workStopped,
    WorkRowPhase.waiting => l10n.kitMarkWaiting,
    WorkRowPhase.queued => l10n.teamUiWorkStateQueued,
    WorkRowPhase.ready => l10n.teamUiWorkStateReady,
    WorkRowPhase.review => l10n.teamUiWorkStateReview,
    WorkRowPhase.unknown => l10n.workUnknown,
  };

  /// No clock is owned here. A caller may tick fresh rows, but disconnected
  /// rows freeze at their last observation, including their completion age.
  ///
  /// [moment] writes the "as of" time; a screen passes the app's one time
  /// formatter (`KitTime.moment`, the device's 12/24-hour clock) so every
  /// row and banner agree (F15). Without it, a fixed numeric pattern.
  String line(
    AppLocalizations l10n, {
    required DateTime now,
    String Function(DateTime at)? moment,
  }) {
    final at = isFresh ? now : observedAt;
    var text = word(l10n);
    final steps = facts.steps;
    if (facts.phase == WorkRowPhase.working) {
      if (steps != null && steps.isValid) {
        text = l10n.workStatusElapsed(
          text,
          l10n.teamUiHomeRunProgress(steps.completed, steps.total),
        );
      }
      final start = facts.startedAt;
      if (start != null) {
        text = l10n.workStatusElapsed(
          text,
          l10n.kitSinceAge(_age(at, start).inMinutes),
        );
      }
    } else if (facts.phase == WorkRowPhase.done && facts.finishedAt != null) {
      text = l10n.teamUiTaskDoneAgo(
        _relative(l10n, _age(at, facts.finishedAt!)),
      );
    }
    if (!isFresh) {
      text = l10n.workStatusElapsed(
        text,
        l10n.kitProgressRowAsOf(
          moment?.call(observedAt) ?? _asOf(l10n, observedAt),
        ),
      );
    }
    return text;
  }

  /// intl's per-locale date data is loaded by the app's localization
  /// delegates, not by [AppLocalizations]. This source must also render
  /// outside a widget tree (tests, notification copy), so a missing locale
  /// table falls back to a fixed numeric pattern instead of throwing.
  static String _asOf(AppLocalizations l10n, DateTime at) {
    final local = at.toLocal();
    try {
      return DateFormat.yMd(l10n.localeName).add_Hm().format(local);
    } on Exception {
      return DateFormat('yyyy-MM-dd HH:mm', 'en_US').format(local);
    }
  }

  static Duration _age(DateTime now, DateTime then) {
    final age = now.difference(then);
    return age.isNegative ? Duration.zero : age;
  }

  static String _relative(AppLocalizations l10n, Duration age) =>
      relativeAgeLabel(age, l10n: l10n);
}
