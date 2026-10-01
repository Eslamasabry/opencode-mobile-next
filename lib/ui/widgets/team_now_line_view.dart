/// The team's one Now line (slice-P5.1, the `KitNowLine`: a
/// [KitStatusLine] with its `next`): what is happening and for how long,
/// what comes next and how long that usually takes ("Starting a worker ·
/// 1 min", "Next: the worker begins the task · usually within 5 min").
///
/// The state is [TeamNowLineController]'s (lib/state/team_now_line.dart):
/// after eight seconds without the next stage, even with no new event from
/// the team, the line says honestly why in one short sentence and offers
/// Why?, which unfolds in place (never a sheet over the page): how this
/// stage works, and the ways out the page offers.
///
/// It replaces the dispatch cycle strip (its seven engine steps) and the
/// "How the host dispatches" sheet: the stage is said in the person's
/// words, and the expectation the sheet set is the Why fold. No engine
/// word is said here; those stay under Task details.
library;

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../state/orchestration.dart'
    show OrchestrationController, OrchestrationControllerCycles;
import '../../state/team_now_line.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

export '../../state/team_now_line.dart'
    show
        TeamNowAction,
        TeamNowActivity,
        TeamNowInput,
        TeamNowNext,
        TeamNowReason;

/// How long a wait for a worker usually takes: the host's documented
/// "usually 1–5 min" (`DispatchHint.usualWait`), said only before a worker
/// has been sent. A starting worker is timed by this phone's last measured
/// start instead. An expectation, never a deadline or a measured figure.
const teamWorkerStartUsual = Duration(minutes: 5);

/// "42 s" or "1 min 20 s": a measured span down to the second.
String teamShortDuration(AppLocalizations l10n, Duration span) {
  final seconds = span.inSeconds < 1 ? 1 : span.inSeconds;
  return seconds < 60
      ? l10n.chatUiDurationSeconds(seconds)
      : l10n.chatUiDurationMinutesSeconds(seconds ~/ 60, seconds % 60);
}

/// What the person sees for [activity], with the time since it began
/// when known: "Starting a worker · 1 min". Also a task row's supporting
/// line while the team plans it.
String teamNowActivityLine(
  AppLocalizations l10n,
  TeamNowActivity activity, {
  Duration? elapsed,
}) {
  final word = switch (activity) {
    TeamNowActivity.planning => l10n.teamNowActivityPlanning,
    TeamNowActivity.waitingForWorker => l10n.teamNowActivityWaitingForWorker,
    TeamNowActivity.startingWorker => l10n.teamNowActivityStartingWorker,
    TeamNowActivity.working => l10n.teamNowActivityWorking,
    TeamNowActivity.reviewing => l10n.teamNowActivityReviewing,
    TeamNowActivity.needsYou => l10n.teamNowActivityNeedsYou,
    TeamNowActivity.delayed => l10n.teamNowActivityDelayed,
    TeamNowActivity.unconfirmed => l10n.teamNowActivityUnconfirmed,
    TeamNowActivity.refused => l10n.teamNowActivityRefused,
    TeamNowActivity.unavailable => l10n.teamNowActivityUnavailable,
    TeamNowActivity.completed => l10n.teamNowActivityCompleted,
    TeamNowActivity.failed => l10n.teamNowActivityFailed,
    TeamNowActivity.cancelled => l10n.teamNowActivityCancelled,
  };
  // A question, a refusal or a lost connection is not measured: how long
  // it has been so is not what the person acts on.
  final timed = switch (activity) {
    TeamNowActivity.planning ||
    TeamNowActivity.waitingForWorker ||
    TeamNowActivity.startingWorker ||
    TeamNowActivity.working ||
    TeamNowActivity.reviewing ||
    TeamNowActivity.delayed => true,
    _ => false,
  };
  if (!timed || elapsed == null || elapsed < const Duration(minutes: 1)) {
    return word;
  }
  return '$word · ${KitSince.durationWords(l10n, elapsed)}';
}

/// The factual short reason, uncertainty kept explicit.
String teamNowReasonSentence(
  AppLocalizations l10n,
  TeamNowReason reason,
) => switch (reason) {
  TeamNowReason.noPlanReported => l10n.teamNowReasonNoPlanReported,
  TeamNowReason.noWorkerReported => l10n.teamNowReasonNoWorkerReported,
  TeamNowReason.workerStarting => l10n.teamNowReasonWorkerStarting,
  TeamNowReason.workerPreparing => l10n.teamNowReasonWorkerPreparing,
  TeamNowReason.workerRunning => l10n.teamNowReasonWorkerRunning,
  TeamNowReason.workerTaskDelivered => l10n.teamNowReasonWorkerTaskDelivered,
  TeamNowReason.workInProgress => l10n.teamNowReasonWorkInProgress,
  TeamNowReason.reviewPending => l10n.teamNowReasonReviewPending,
  TeamNowReason.answerNeeded => l10n.teamNowReasonAnswerNeeded,
  TeamNowReason.workerCouldNotStart => l10n.teamNowReasonWorkerCouldNotStart,
  TeamNowReason.providerLimit => l10n.teamNowReasonProviderLimit,
  TeamNowReason.workTakingLonger => l10n.teamNowReasonWorkTakingLonger,
  TeamNowReason.confirmationMissing => l10n.teamNowReasonConfirmationMissing,
  TeamNowReason.requestRefused => l10n.teamNowReasonRequestRefused,
  TeamNowReason.connectionUnavailable =>
    l10n.teamNowReasonConnectionUnavailable,
  TeamNowReason.causeUnknown => l10n.teamNowReasonCauseUnknown,
};

/// How this stage works, the Why fold's first words (what the retired
/// "How the host dispatches" sheet explained, without engine words).
String? _whyText(
  AppLocalizations l10n,
  TeamNowActivity activity,
  TeamNowReason? reason,
) => switch (activity) {
  TeamNowActivity.planning => l10n.teamNowWhyPlanning,
  TeamNowActivity.waitingForWorker => l10n.teamNowWhyWaitingForWorker,
  TeamNowActivity.startingWorker => l10n.teamNowWhyStartingWorker,
  TeamNowActivity.working => l10n.teamNowWhyWorking,
  TeamNowActivity.reviewing => l10n.teamNowWhyReviewing,
  TeamNowActivity.unconfirmed => l10n.teamNowWhyUnconfirmed,
  TeamNowActivity.delayed => switch (reason) {
    TeamNowReason.noWorkerReported => l10n.teamNowWhyWaitingForWorker,
    TeamNowReason.workerCouldNotStart => l10n.teamNowWhyWorkerCouldNotStart,
    TeamNowReason.providerLimit => l10n.teamNowWhyProviderLimit,
    TeamNowReason.workTakingLonger => l10n.teamNowWhyWorkTakingLonger,
    TeamNowReason.reviewPending => l10n.teamNowWhyReviewing,
    _ => l10n.teamNowWhyCauseUnknown,
  },
  _ => null,
};

/// "Next: a worker starts · usually within 1 min"; null when nothing
/// comes next that the line should say.
String? _nextLine(
  AppLocalizations l10n,
  TeamNowLineState state, {
  required Duration? elapsed,
}) {
  final step = switch (state.next) {
    TeamNowNext.plan => l10n.teamNowNextPlan,
    TeamNowNext.worker => l10n.teamNowNextWorker,
    TeamNowNext.work => l10n.teamNowNextWork,
    TeamNowNext.review => l10n.teamNowNextReview,
    TeamNowNext.finish => l10n.teamNowNextFinish,
    // The question's card says what to answer; "check activity" is the
    // Why fold's ways out, not a stage.
    TeamNowNext.yourAnswer ||
    TeamNowNext.checkActivity ||
    TeamNowNext.none => null,
  };
  if (step == null) return null;
  // A worker's start is timed by the last one measured on this phone, said
  // as a fact ("took 42 s last time"); with none measured, nothing is said.
  final last = state.lastWorkerStart;
  if (last != null && state.next == TeamNowNext.work) {
    return [
      step,
      l10n.teamNowLastStart(teamShortDuration(l10n, last)),
    ].join(' · ');
  }
  final usual = state.typicalUpperBound;
  // The usual time is an expectation, never a deadline: once it has
  // passed it is no longer said (the reason says it takes longer).
  final timing = usual == null || (elapsed != null && elapsed >= usual)
      ? null
      : l10n.teamNowUsuallyWithin(KitSince.durationWords(l10n, usual));
  return [step, ?timing].join(' · ');
}

/// The team's one Now line for one task or step. Owns its
/// [TeamNowLineController]; the page passes the current facts as [input]
/// on every build and says which ways out it offers through [wayOut].
class TeamNowLineView extends StatefulWidget {
  const TeamNowLineView({
    super.key,
    required this.input,
    required this.wayOut,
    this.clock,
    this.message,
    this.explains = true,
    this.keyPrefix = 'team-now-line',
    this.watchCycles,
    this.watchWorkId,
  });

  /// A team whose dispatch cycles this line keeps fresh while it shows
  /// (a sheet over a page that does not already watch them).
  final OrchestrationController? watchCycles;

  /// The step whose cycle [watchCycles] reads again once watched, so the
  /// agent's transcript is probed (a usage limit is seen) even though
  /// the page derived it before the line started watching.
  final String? watchWorkId;

  final TeamNowInput input;

  /// The page's way out for a suggested action, or null when the page
  /// does not offer it (an action with no handler is not shown).
  final KitAction? Function(TeamNowAction action) wayOut;

  /// The page's clock; tests pin it.
  final DateTime Function()? clock;

  /// Words the page knows better (a task quiet for hours: "No progress
  /// for 1 d 21 h"), in place of the activity line.
  final String? message;

  /// False when the page explains the stage itself, below the line (the
  /// no-progress notice with its own ways out): the line then gives no
  /// reason and no Why, so nothing is said twice.
  final bool explains;

  /// Keys: `<prefix>-now`, `-now-text`, `-now-next`, `-now-reason`,
  /// `-now-why`, `-now-why-fold`, `-retry`.
  final String keyPrefix;

  @override
  State<TeamNowLineView> createState() => _TeamNowLineViewState();
}

class _TeamNowLineViewState extends State<TeamNowLineView> {
  late final TeamNowLineController _controller = TeamNowLineController(
    widget.input,
    clock: widget.clock,
  );
  bool _open = false;

  @override
  void initState() {
    super.initState();
    _watch(widget);
  }

  void _watch(TeamNowLineView line) {
    final team = line.watchCycles;
    if (team == null) return;
    team.watchCycles();
    if (line.watchWorkId case final id?) team.cycleFor(id);
  }

  @override
  void didUpdateWidget(TeamNowLineView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.input.activityKey != widget.input.activityKey ||
        oldWidget.input.activity != widget.input.activity) {
      _open = false;
    }
    if (oldWidget.watchCycles != widget.watchCycles) {
      oldWidget.watchCycles?.unwatchCycles();
      _watch(widget);
    }
    _controller.update(widget.input);
  }

  @override
  void dispose() {
    widget.watchCycles?.unwatchCycles();
    _controller.dispose();
    super.dispose();
  }

  DateTime _now() => (widget.clock ?? DateTime.now)();

  @override
  Widget build(BuildContext context) => ValueListenableBuilder(
    valueListenable: _controller,
    builder: (context, state, _) => _build(context, state),
  );

  Widget _build(BuildContext context, TeamNowLineState state) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final prefix = widget.keyPrefix;
    final since = state.since;
    final elapsed = since == null
        ? null
        : () {
            final span = _now().difference(since);
            return span.isNegative ? Duration.zero : span;
          }();
    final activity = state.activity;
    final (icon, tone) = switch (activity) {
      TeamNowActivity.completed => (AppIconography.check, AppStatusTone.ok),
      TeamNowActivity.failed ||
      TeamNowActivity.refused => (AppIconography.error, AppStatusTone.failure),
      TeamNowActivity.cancelled => (AppIconography.stop, AppStatusTone.neutral),
      TeamNowActivity.needsYou => (
        AppIconography.question,
        AppStatusTone.neutral,
      ),
      // A degraded state, never the attention look (LOOK-4).
      TeamNowActivity.unavailable => (
        AppIconography.cloudOff,
        AppStatusTone.failure,
      ),
      TeamNowActivity.delayed || TeamNowActivity.unconfirmed => (
        AppIconography.waiting,
        AppStatusTone.neutral,
      ),
      _ => (AppIconography.statusDot, AppStatusTone.progress),
    };
    // The ways out, the one that shows what is going on first; the
    // question is answered on its own card.
    const order = [
      TeamNowAction.openActivity,
      TeamNowAction.refresh,
      TeamNowAction.dismissRequest,
      TeamNowAction.cancelRun,
    ];
    final offered = [
      for (final action in order)
        if (state.actions.contains(action)) ?widget.wayOut(action),
    ];
    // A lost connection is its own reason: its line says so, its one way
    // out is Try again. A question's reason is its card.
    if (activity == TeamNowActivity.unavailable) {
      final retry = widget.wayOut(TeamNowAction.refresh);
      return KitStatusLine(
        key: ValueKey('$prefix-now'),
        messageKey: ValueKey('$prefix-now-text'),
        icon: icon,
        tone: tone,
        message: widget.message ?? teamNowActivityLine(l10n, activity),
        action: retry == null
            ? null
            : KitAction(
                key: ValueKey('$prefix-retry'),
                label: l10n.commonRetry,
                onPressed: retry.onPressed,
              ),
      );
    }
    final reason = state.reason;
    final explain =
        widget.explains &&
        state.explain &&
        reason != null &&
        activity != TeamNowActivity.needsYou;
    // A reason that only restates the title ("Working on your task" /
    // "The task is being worked on.") is not said twice; the Why fold still
    // explains the stage.
    final restates =
        reason == TeamNowReason.workInProgress ||
        (activity == TeamNowActivity.reviewing &&
            reason == TeamNowReason.reviewPending);
    final why = explain ? _whyText(l10n, activity, reason) : null;
    final foldable = explain && (why != null || offered.isNotEmpty);
    final open = foldable && _open;
    return Column(
      key: ValueKey('$prefix-now-block'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitStatusLine(
          key: ValueKey('$prefix-now'),
          messageKey: ValueKey('$prefix-now-text'),
          icon: icon,
          tone: tone,
          message:
              widget.message ??
              teamNowActivityLine(l10n, activity, elapsed: elapsed),
          next: _nextLine(l10n, state, elapsed: elapsed),
          nextKey: ValueKey('$prefix-now-next'),
          supporting: explain && !restates
              ? teamNowReasonSentence(l10n, reason)
              : null,
          supportingKey: ValueKey('$prefix-now-reason'),
          action: foldable
              ? KitAction(
                  key: ValueKey('$prefix-now-why'),
                  label: open ? l10n.teamNowWhyHide : l10n.teamNowWhy,
                  onPressed: () => setState(() => _open = !_open),
                )
              : null,
        ),
        KitReveal(
          child: open
              ? Padding(
                  key: ValueKey('$prefix-now-why-fold'),
                  padding: EdgeInsetsDirectional.fromSTEB(
                    tokens.gutter,
                    tokens.space2,
                    tokens.gutter,
                    tokens.space3,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      if (why != null)
                        KitText(
                          why,
                          key: ValueKey('$prefix-now-why-text'),
                          role: KitTextRole.secondary,
                          tone: KitTextTone.secondary,
                        ),
                      if (offered.isNotEmpty) ...[
                        SizedBox(height: tokens.space2),
                        KitActionBlock(
                          key: ValueKey('$prefix-now-ways'),
                          secondary: offered.first,
                          tertiary: offered.skip(1).toList(),
                        ),
                      ],
                    ],
                  ),
                )
              : null,
        ),
      ],
    );
  }
}
