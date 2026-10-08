// KitChecklist: a job made of steps, with steps only the person can do mixed
// in (docs/ux-system/kit-api/KitChecklist.md; kit-v2.md §1.12, §2.8, §4.9;
// STATE-5, STATE-6, STATE-11, KIT-37, MOT-6, A11Y-3, TEST-5).
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'kit_buttons.dart';
import 'kit_details_fold.dart';
import 'kit_divider.dart';
import 'kit_log_panel.dart';
import 'kit_motion.dart';
import 'kit_needs_you.dart';
import 'kit_notice.dart';
import 'kit_progress.dart';
import 'kit_row.dart';
import 'kit_since.dart';
import 'kit_status_mark.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_animated_rows.dart';
import 'motion/kit_haptics.dart';
import 'motion/kit_reveal.dart';

/// One step of a [KitChecklist]. Its mark comes from the engine, never
/// inferred: a step from an existing install is [KitMarkState.done] with
/// "Already installed" as its [supporting] (STATE-11), and "waiting" is never
/// drawn as done.
@immutable
class KitStep {
  const KitStep({
    required this.title,
    required this.state,
    this.paused = false,
    this.supporting,
    this.personAction,
    this.retry,
    this.report,
    this.value,
    this.key,
  }) : assert(
         !paused ||
             state == KitMarkState.waiting ||
             state == KitMarkState.working,
         'KitStep: paused only applies to a waiting or working step',
       ),
       assert(
         retry == null || state == KitMarkState.failed,
         'KitStep: "Try again" (retry) belongs on the failed row only',
       ),
       assert(
         report == null || state == KitMarkState.failed,
         'KitStep: "Report this failure" belongs on the failed row only',
       ),
       assert(
         value == null || (value >= 0 && value <= 1),
         'KitStep: value must be between 0 and 1',
       );

  /// "Download Ubuntu".
  final String title;
  final KitMarkState state;

  /// With waiting or working: a heat pause, or a force stop the app can
  /// resume ([KitStatusMark.paused]).
  final bool paused;

  /// "29 of 30 MB · about 1 min left"; the failure's reason; "Already
  /// installed". Byte counts and sizes arrive wrapped in `KitBidi.ltr` by
  /// the caller (COPY-30).
  final String? supporting;

  /// A step only the person can do ("Allow", "Get Termux"): the row leads
  /// with the needs-you mark, its supporting line starts "Needs you · ", and
  /// this is its button.
  final KitAction? personAction;

  /// "Try again", on the failed row only (asserted).
  final KitAction? retry;

  /// "Report this failure" (P8.4), on the failed row only (asserted): a
  /// tertiary action under the row's words, after its one button, that
  /// opens Report a problem with the job's log attached.
  final KitAction? report;

  /// 0..1 within this step, when measured: a thin bar under the row while
  /// it works.
  final double? value;

  /// The row's key (`setup-progress-row-<id>` in SetupProgressView).
  final Key? key;

  bool get _needsPerson => personAction != null && state != KitMarkState.done;
}

/// A job made of steps, with steps only the person can do mixed in: phone
/// setup, Termux, Claude Code, AI Team turn-on, the voice model, worktree
/// creation, a team task's cycle.
///
/// A mark per step, one optional bar for the whole job ([progress]), the
/// [estimate] and [cost] before the start, Stop and Resume, a failure on
/// its own row with Try again, the job's one [log] folded under Details,
/// and a one-line [compact] form (the team stage strip) that unfolds in
/// place.
///
/// Each row is one semantics node ("Step 2 of 5, Download, Working, 29 of
/// 30 MB"); its mark is excluded because the words say it. The job header
/// is the live region (A11Y-3): the checklist owns a visually hidden
/// summary that changes only when the current step or its state changes,
/// so a byte tick is never announced. A working step with [since]
/// escalates once after [KitMotion.escalateAfter] (STATE-5, through
/// [KitSince]): its supporting line reads "Still waiting after 8 s" and the
/// [onSlow] ways out unfold. [KitHaptics.done] fires once when every step
/// turns done while the checklist is visible; nothing on failures or stops.
///
/// Stop never confirms by itself: the caller confirms only when work would
/// be lost (DATA-11). The log is folded by default and never opens itself.
///
/// States: before-start, working, person-step, slow, failed, paused,
/// stopped, done, compact (+ disabled actions).
class KitChecklist extends StatefulWidget {
  const KitChecklist({
    super.key,
    required this.steps,
    this.progress,
    this.estimate,
    this.cost = const [],
    this.stop,
    this.resume,
    this.log,
    this.since,
    this.onSlow = const [],
    this.compact = false,
    this.next,
    this.checklistKey,
    this.detailsKey,
  }) : assert(steps.length > 0, 'KitChecklist: at least one step'),
       assert(onSlow.length <= 2, 'KitChecklist: at most two ways out');

  final List<KitStep> steps;

  /// One bar for the whole job ([KitProgress.staged]); omit it when the
  /// host already shows the job's bar.
  final KitProgress? progress;

  /// Before the start, in words: "about 8 minutes the first time".
  final String? estimate;

  /// Before the start: the [KitNotice.cost] items (KIT-37).
  final List<String> cost;

  /// "Stop". The caller confirms only when work would be lost.
  final KitAction? stop;

  /// "Continue setup": after a failure, a force stop or a heat pause.
  final KitAction? resume;

  /// The job's one log, folded under Details.
  final KitLogPanel? log;

  /// The last progress event; the working step escalates after 8 s.
  final DateTime? since;

  /// At most two ways out, shown once the working step has escalated.
  final List<KitAction> onSlow;

  /// One line, "Step 3 of 7 · Reviewing · next: merge", that unfolds to the
  /// list.
  final bool compact;

  /// [compact] only: the next step's words, "merge".
  final String? next;

  /// The steps column (`setup-progress-checklist` in the adapter).
  final Key? checklistKey;

  /// The log fold's toggle (`setup-progress-details` in the adapter).
  final Key? detailsKey;

  @override
  State<KitChecklist> createState() => _KitChecklistState();
}

class _KitChecklistState extends State<KitChecklist> {
  late bool _wasDone;
  bool _unfolded = false;

  /// The person opened the log's fold (not a fold that was open already).
  bool _logOpenedByPerson = false;

  @override
  void initState() {
    super.initState();
    // Set here, not lazily: a first build that is already done is not a
    // turn to done.
    _wasDone = _allDone(widget.steps);
  }

  static bool _allDone(List<KitStep> steps) =>
      steps.every((s) => s.state == KitMarkState.done);

  @override
  void didUpdateWidget(KitChecklist old) {
    super.didUpdateWidget(old);
    final done = _allDone(widget.steps);
    // Once, on the turn to done, and only while the person can see it.
    if (done && !_wasDone && TickerMode.valuesOf(context).enabled) {
      KitHaptics.done(context);
    }
    _wasDone = done;
  }

  /// The step the job is at: the first working, failed or person step,
  /// else the first waiting one, else the last.
  int get _current {
    final steps = widget.steps;
    for (var i = 0; i < steps.length; i++) {
      final s = steps[i];
      if (s.state == KitMarkState.working ||
          s.state == KitMarkState.failed ||
          s._needsPerson) {
        return i;
      }
    }
    for (var i = 0; i < steps.length; i++) {
      if (steps[i].state == KitMarkState.waiting) return i;
    }
    return steps.length - 1;
  }

  bool get _beforeStart => widget.steps.every(
    (s) => s.state == KitMarkState.waiting && !s.paused && !s._needsPerson,
  );

  bool get _working =>
      widget.steps.any((s) => s.state == KitMarkState.working && !s.paused);

  @override
  Widget build(BuildContext context) {
    // Only a step that works can be slow; a stopped or finished job waits
    // for nobody.
    return KitSince(
      since: _working ? widget.since : null,
      builder: (context, status) => widget.compact
          ? _compact(context, slow: status.isSlow)
          : _full(context, slow: status.isSlow),
    );
  }

  Widget _compact(BuildContext context, {required bool slow}) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final index = _current;
    final step = widget.steps[index];
    final next = widget.next;
    final line = [
      l10n.kitProgressStep(index + 1, widget.steps.length),
      step.title,
      if (next != null) l10n.kitChecklistNext(next),
    ].join(' · ');
    final spoken = [
      _summary(context, slow: slow),
      if (next != null) l10n.kitChecklistNext(next),
    ].join(', ');
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Semantics(
          expanded: _unfolded,
          liveRegion: true,
          child: KitTappable(
            tappableKey: const ValueKey('kit-checklist-compact'),
            label: spoken,
            tooltip: _unfolded
                ? l10n.kitChecklistHideSteps
                : l10n.kitChecklistShowSteps,
            onTap: () => setState(() => _unfolded = !_unfolded),
            shape: KitShape.tile,
            child: ConstrainedBox(
              constraints: BoxConstraints(minHeight: tokens.minTarget),
              child: Padding(
                padding: EdgeInsetsDirectional.symmetric(
                  horizontal: tokens.space2,
                ),
                child: Row(
                  children: [
                    _Mark(step: step),
                    SizedBox(width: tokens.space3),
                    Expanded(
                      child: KitText(
                        line,
                        key: const ValueKey('kit-checklist-compact-line'),
                        role: KitTextRole.label,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
        KitReveal(
          child: _unfolded
              ? KeyedSubtree(
                  key: const ValueKey('kit-checklist-unfolded'),
                  child: _full(context, slow: slow, announce: false),
                )
              : null,
        ),
      ],
    );
  }

  Widget _full(
    BuildContext context, {
    required bool slow,
    bool announce = true,
  }) {
    final tokens = KitTokens.of(context);
    final steps = widget.steps;
    final current = _current;
    final estimate = widget.estimate;
    final progress = widget.progress;
    final log = widget.log;
    final beforeStart =
        _beforeStart && (estimate != null || widget.cost.isNotEmpty);
    final actions = KitActionBlock(
      primary: widget.resume,
      tertiary: [?widget.stop],
    );

    final rows = KitAnimatedRows(
      children: [
        for (var i = 0; i < steps.length; i++)
          _StepRow(
            key: steps[i].key ?? ValueKey('kit-checklist-step-$i'),
            step: steps[i],
            index: i,
            total: steps.length,
            slow: slow && i == current,
            since: widget.since,
          ),
      ],
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (announce)
          // The job header's live region (A11Y-3): a visually hidden summary
          // that changes only with the current step or its state.
          Semantics(
            key: const ValueKey('kit-checklist-summary'),
            container: true,
            liveRegion: true,
            label: _summary(context, slow: slow),
            child: const SizedBox(
              width: KitTokens.liveRegionSize,
              height: KitTokens.liveRegionSize,
            ),
          ),
        if (beforeStart) ...[
          if (estimate != null)
            KitText(
              estimate,
              key: const ValueKey('kit-checklist-estimate'),
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
          if (widget.cost.isNotEmpty) ...[
            if (estimate != null) SizedBox(height: tokens.space2),
            KitNotice.cost(
              widget.cost,
              key: const ValueKey('kit-checklist-cost'),
            ),
          ],
          SizedBox(height: tokens.space3),
        ],
        if (progress != null) ...[
          KitProgressView(progress: progress),
          SizedBox(height: tokens.space3),
        ],
        KeyedSubtree(key: widget.checklistKey, child: rows),
        if (widget.onSlow.isNotEmpty)
          KitReveal(
            child: slow
                ? Padding(
                    key: const ValueKey('kit-checklist-on-slow'),
                    padding: EdgeInsetsDirectional.only(top: tokens.space2),
                    child: KitActionBlock(tertiary: widget.onSlow),
                  )
                : null,
          ),
        if (!actions.isEmpty) ...[SizedBox(height: tokens.space3), actions],
        if (log != null) ...[
          SizedBox(height: tokens.sectionGap),
          const KitDivider(),
          SizedBox(height: tokens.space2),
          KitDetailsFold(
            foldKey: widget.detailsKey,
            // Opened, the log is brought into view as it unfolds: it sits
            // last, so it would otherwise open below the fold (design
            // regressions ledger row 16).
            onExpansionChanged: (open) => _logOpenedByPerson = open,
            child: _IntoView(enabled: () => _logOpenedByPerson, child: log),
          ),
        ],
      ],
    );
  }

  /// "Step 3 of 7, Installing, Working[, needs you, Allow][, Still waiting
  /// after 8 s]": what the live region says.
  String _summary(BuildContext context, {required bool slow}) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final index = _current;
    final step = widget.steps[index];
    return [
      l10n.kitProgressStep(index + 1, widget.steps.length),
      step.title,
      KitStatusMark.wordFor(context, step.state, paused: step.paused),
      if (step._needsPerson)
        l10n.kitChecklistNeedsYou(step.personAction!.label),
      if (slow && step.state == KitMarkState.working && !step.paused)
        KitSince.slowLabel(context),
    ].join(', ');
  }
}

/// A step's leading mark: the needs-you mark for a person step, else its
/// state mark. A change cross-fades on [KitMotion.quick]; instant under
/// reduced motion. Excluded from semantics: the row's words say it.
class _Mark extends StatelessWidget {
  const _Mark({required this.step});

  final KitStep step;

  @override
  Widget build(BuildContext context) {
    final person = step._needsPerson;
    return ExcludeSemantics(
      child: AnimatedSwitcher(
        duration: KitMotion.reduced(context) ? Duration.zero : KitMotion.quick,
        switchInCurve: KitMotion.enter,
        switchOutCurve: KitMotion.exit,
        child: person
            ? KitNeedsYou.mark(key: const ValueKey('kit-checklist-mark-person'))
            : KitStatusMark(
                key: ValueKey(
                  'kit-checklist-mark-${step.state.name}-${step.paused}',
                ),
                state: step.state,
                paused: step.paused,
              ),
      ),
    );
  }
}

/// One step as a [KitRow]: its mark, its title, its supporting line and,
/// for a person step or a failed one, its one button at the end (under the
/// words when there is no room). One semantics node for the words; the
/// button is its own focusable node inside it.
class _StepRow extends StatelessWidget {
  const _StepRow({
    super.key,
    required this.step,
    required this.index,
    required this.total,
    required this.slow,
    this.since,
  });

  final KitStep step;
  final int index;
  final int total;
  final bool slow;

  /// When the checklist's wait began: a slow working step counts from it.
  final DateTime? since;

  @override
  Widget build(BuildContext context) {
    final working = step.state == KitMarkState.working && !step.paused;
    final since = this.since;
    if (!slow || !working || since == null) return _row(context, null);
    // The slow line counts on ("Still waiting after 23 s"): a number that
    // stood at the escalation's 8 s for half a minute read as stuck. Only
    // this row ticks; the live region keeps its one escalation.
    return KitSince(
      since: since,
      ticks: KitSinceTicks.seconds,
      builder: (context, status) => _row(context, status.elapsed),
    );
  }

  Widget _row(BuildContext context, Duration? waited) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final person = step._needsPerson;
    final working = step.state == KitMarkState.working && !step.paused;
    final supportingText = slow && working
        ? (waited == null
              ? KitSince.slowLabel(context)
              : KitSince.slowLabelAt(context, waited))
        : step.supporting;
    final InlineSpan? supporting = person
        ? TextSpan(
            children: [
              KitNeedsYou.span(context),
              if (supportingText != null) TextSpan(text: supportingText),
            ],
          )
        : supportingText == null
        ? null
        : TextSpan(
            text: supportingText,
            style: const TextStyle(
              fontFeatures: [FontFeature.tabularFigures()],
            ),
          );
    final action = person ? step.personAction : step.retry;
    final label = [
      l10n.kitProgressStep(index + 1, total),
      step.title,
      KitStatusMark.wordFor(context, step.state, paused: step.paused),
      ?supportingText,
      if (person) l10n.kitChecklistNeedsYou(step.personAction!.label),
    ].join(', ');

    final words = ExcludeSemantics(
      child: KitRow(
        padding: EdgeInsetsDirectional.symmetric(vertical: tokens.space1),
        leading: _Mark(step: step),
        title: step.title,
        titleMaxLines: 2,
        supporting: supporting,
        supportingMaxLines: 4,
      ),
    );
    final value = step.value;
    // Under the words: the mark slot and KitRow's 12 dp gap.
    final indent = KitTokens.markSlotSize + tokens.space3;
    final bar = working && value != null
        ? Padding(
            padding: EdgeInsetsDirectional.only(
              start: indent,
              bottom: tokens.space1,
            ),
            child: ExcludeSemantics(child: _StepBar(value: value)),
          )
        : null;
    final button = action == null
        ? null
        : KitButton.fromAction(
            action,
            role: KitButtonRole.secondary,
            expand: false,
          );
    final report = step.report == null || person
        ? null
        : Padding(
            padding: EdgeInsetsDirectional.only(
              start: indent,
              bottom: tokens.space1,
            ),
            child: Align(
              alignment: AlignmentDirectional.centerStart,
              child: KitButton.fromAction(
                step.report!,
                role: KitButtonRole.tertiary,
                expand: false,
              ),
            ),
          );

    Widget content;
    if (button == null) {
      content = words;
    } else {
      // 200 % text, a narrow window or a long label ("Allow the permission
      // in Settings"): the button moves under the words, so neither is cut.
      Widget stacked() => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          words,
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: indent,
              bottom: tokens.space2,
            ),
            child: Align(
              alignment: AlignmentDirectional.centerStart,
              child: button,
            ),
          ),
        ],
      );
      content = AppTheme.stackedActions(context)
          ? stacked()
          : LayoutBuilder(
              builder: (context, constraints) =>
                  _buttonWidth(context, action!) > constraints.maxWidth / 2
                  ? stacked()
                  : Row(
                      children: [
                        Expanded(child: words),
                        Padding(
                          padding: EdgeInsetsDirectional.only(
                            start: tokens.space3,
                          ),
                          child: button,
                        ),
                      ],
                    ),
            );
    }
    return Semantics(
      container: true,
      label: label,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [content, ?bar, ?report],
      ),
    );
  }
}

/// About how wide a row's button draws [action]: its label in the button's
/// text style plus the filled button's side padding and icon. Only decides
/// beside-or-under, so an estimate is enough.
double _buttonWidth(BuildContext context, KitAction action) {
  final painter = TextPainter(
    text: TextSpan(
      text: action.label,
      style: Theme.of(context).textTheme.labelLarge,
    ),
    textDirection: Directionality.of(context),
    textScaler: MediaQuery.textScalerOf(context),
    maxLines: 1,
  )..layout();
  final width = painter.width;
  painter.dispose();
  return width + 48 + (action.icon == null ? 0 : 26);
}

/// The thin per-row bar: `accent` on `surface3`, [KitTokens.loadingBarHeight]
/// tall, filling from the start and easing on [KitMotion.standard] (paint
/// only; instant under reduced motion).
class _StepBar extends StatelessWidget {
  const _StepBar({required this.value});

  final double value;

  @override
  Widget build(BuildContext context) {
    final roles = KitTokens.of(context).roles;
    Widget track(double shown) => LinearProgressIndicator(
      value: shown,
      minHeight: KitTokens.loadingBarHeight,
      color: roles.accent,
      backgroundColor: roles.surface3,
      borderRadius: BorderRadius.circular(KitTokens.loadingBarHeight / 2),
    );
    if (KitMotion.reduced(context)) return track(value);
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(end: value),
      duration: KitMotion.standard,
      curve: KitMotion.enter,
      builder: (context, shown, _) => track(shown),
    );
  }
}

/// Keeps [child] in view while it unfolds: after each frame of the reveal,
/// the nearest scrollable shows its end (never scrolling past its start).
/// Only when [enabled] says the person opened it, so a page that opens
/// with the fold already open does not jump.
class _IntoView extends StatefulWidget {
  const _IntoView({required this.enabled, required this.child});

  final bool Function() enabled;
  final Widget child;

  @override
  State<_IntoView> createState() => _IntoViewState();
}

class _IntoViewState extends State<_IntoView> {
  /// Frames the reveal may take before following stops (a reveal is a few
  /// hundred milliseconds; this bounds it without a timer).
  static const _maxFrames = 60;
  int _frames = 0;

  @override
  void initState() {
    super.initState();
    if (widget.enabled()) _follow();
  }

  void _follow() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final object = context.findRenderObject();
      final position = Scrollable.maybeOf(context)?.position;
      if (object == null || position == null) return;
      unawaited(
        position.ensureVisible(
          object,
          alignmentPolicy: ScrollPositionAlignmentPolicy.keepVisibleAtEnd,
        ),
      );
      // Still unfolding: the next frame shows more of it.
      if (++_frames < _maxFrames &&
          SchedulerBinding.instance.hasScheduledFrame) {
        _follow();
      }
    });
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
