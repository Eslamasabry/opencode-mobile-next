// KitWorkLine: the one line a turn's work folds under
// (docs/ux-system/kit-api/KitWorkLine.md, visual language §5: "work is
// folded into one chip: Read 3 files · edited 1"). A KitChip.summary that
// says what was done, what is happening now, or "Waiting for you", and
// opens to the steps in the order they happened.
//
// States (KIT-12): running, waitingForYou, done, endedFailed, stopped; each
// folded or expanded. No loading, empty, disabled or error state of its own:
// a turn without work has no work line, the chip always opens, and
// endedFailed is the work's state, not the part's.
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../kit_buttons.dart';
import '../kit_motion.dart';
import '../kit_status_mark.dart';
import 'kit_step_timeline.dart';

/// Where a turn's work stands. The host decides; the line only says it.
enum KitWorkState {
  /// A step is running now.
  running,

  /// A request waits for the person (AUTO-15): never a spinner.
  waitingForYou,

  /// Finished; failures the agent got past stay quiet (STATE-15).
  done,

  /// The work ended on a failure: opens by default.
  endedFailed,

  /// The person stopped the turn.
  stopped,
}

/// What the work did, counted by the host from its tool calls.
@immutable
class KitWorkCounts {
  const KitWorkCounts({
    this.read = 0,
    this.searched = 0,
    this.listed = 0,
    this.edited = 0,
    this.ran = 0,
    this.fetched = 0,
    this.delegated = 0,
    this.other = 0,
    this.notRun = 0,
    this.steps = 0,
  });

  /// Files read.
  final int read;

  /// Searches (glob, grep).
  final int searched;

  /// Folders listed.
  final int listed;

  /// Files edited or written.
  final int edited;

  /// Commands run.
  final int ran;

  /// Pages fetched or searched on the web.
  final int fetched;

  /// Sub-agents started.
  final int delegated;

  /// Other calls.
  final int other;

  /// Calls the server never ran.
  final int notRun;

  /// Model steps (the fallback words).
  final int steps;

  /// No counted call and no step.
  bool get isEmpty =>
      read == 0 &&
      searched == 0 &&
      listed == 0 &&
      edited == 0 &&
      ran == 0 &&
      fetched == 0 &&
      delegated == 0 &&
      other == 0 &&
      notRun == 0 &&
      steps == 0;

  @override
  bool operator ==(Object other) =>
      other is KitWorkCounts &&
      other.read == read &&
      other.searched == searched &&
      other.listed == listed &&
      other.edited == edited &&
      other.ran == ran &&
      other.fetched == fetched &&
      other.delegated == delegated &&
      other.other == this.other &&
      other.notRun == notRun &&
      other.steps == steps;

  @override
  int get hashCode => Object.hash(
    read,
    searched,
    listed,
    edited,
    ran,
    fetched,
    delegated,
    other,
    notRun,
    steps,
  );
}

/// A turn's work, folded under one chip (STATE-16, KIT-41). Steps are the
/// turn's work and the prose between steps, oldest first; step boundaries
/// are never drawn.
///
/// States: running, waitingForYou, done, endedFailed, stopped; folded or
/// expanded (KIT-12).
class KitWorkLine extends StatefulWidget {
  const KitWorkLine({
    super.key,
    required this.counts,
    required this.state,
    required this.steps,
    this.now,
    this.expanded,
    this.onExpansionChanged,
    this.lineKey,
    this.stepsKey,
    this.tail,
  });

  /// What the work did; the chip's summary words.
  final KitWorkCounts counts;

  /// Where the work stands.
  final KitWorkState state;

  /// KitToolRow, KitMessage.thought, KitMessage.reply (prose between
  /// steps), oldest first.
  final List<Widget> steps;

  /// While running and folded: the live step's words ("Editing
  /// lib/main.dart"); the chip's label, beside the working mark. Null falls
  /// back to [summaryOf]. Opened, the chip reads "Hide steps" with no mark:
  /// the running step in the list shows the progress.
  final String? now;

  /// Non-null: controlled (the host's expansion store survives recycling).
  final bool? expanded;

  /// Called with the new value whenever the chip is toggled.
  final ValueChanged<bool>? onExpansionChanged;

  /// On the chip (today's `Key('work-group-header')`).
  final Key? lineKey;

  /// On the opened steps (today's `Key('work-group-steps')`).
  final Key? stepsKey;

  /// While the work runs: how many of the newest steps stay in view when the
  /// line is opened; older ones sit behind the "Show {count} earlier steps"
  /// row. Null (or fewer than three steps beyond it) shows the steps up to
  /// [stepCap].
  final int? tail;

  /// Opened without asking when the work ended on a failure; otherwise
  /// folded. The host passes its stored choice as [expanded] when there is
  /// one.
  static bool opensByDefault(KitWorkState state) =>
      state == KitWorkState.endedFailed;

  /// The summary words for [counts]: "Read 3 files · edited 1 file · ran 2
  /// commands" (first segment capitalised in scripts with case; " · "
  /// between segments). With no counted call it is "{steps} steps".
  static String summaryOf(BuildContext context, KitWorkCounts counts) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final segments = <String>[
      if (counts.read > 0) l10n.kitWorkRead(counts.read),
      if (counts.searched > 0) l10n.kitWorkSearched(counts.searched),
      if (counts.listed > 0) l10n.kitWorkListed(counts.listed),
      if (counts.edited > 0) l10n.kitWorkEdited(counts.edited),
      if (counts.ran > 0) l10n.kitWorkRan(counts.ran),
      if (counts.fetched > 0) l10n.kitWorkFetched(counts.fetched),
      if (counts.delegated > 0) l10n.kitWorkDelegated(counts.delegated),
      if (counts.other > 0) l10n.kitWorkOther(counts.other),
      if (counts.notRun > 0) l10n.kitWorkNotRun(counts.notRun),
    ];
    if (segments.isEmpty) return l10n.kitWorkSteps(counts.steps);
    final first = segments.first;
    // Capitalise the first letter; a script without case is unchanged.
    segments[0] = first.isEmpty
        ? first
        : first.substring(0, 1).toUpperCase() + first.substring(1);
    return segments.join(l10n.kitWorkSeparator);
  }

  /// The most steps an opened line builds at once; older ones sit behind a
  /// "Show {count} earlier steps" row that reveals them in place (PERF-2).
  static const int stepCap = 25;

  @override
  State<KitWorkLine> createState() => _KitWorkLineState();
}

class _KitWorkLineState extends State<KitWorkLine>
    with SingleTickerProviderStateMixin {
  /// The uncontrolled choice; ignored while [KitWorkLine.expanded] is set.
  late bool _open = KitWorkLine.opensByDefault(widget.state);

  /// The person toggled this line; a later state change no longer opens or
  /// folds it on its own.
  bool _touched = false;

  /// How many earlier steps "Show earlier steps" has revealed, in chunks
  /// of [KitWorkLine.stepCap] (PERF-2: a long turn never lays out at once).
  int _revealed = 0;

  /// The step the last reveal began with: focus lands there.
  int? _focusIndex;

  /// Wraps the first revealed step so focus can land there (Accessibility).
  /// Never a Tab stop: it takes focus itself only once, when that step has
  /// no control of its own, and gives the ability up as focus moves on
  /// (LAY-10, G14).
  final _firstRevealed = FocusNode(
    debugLabel: 'kit-work-line-first-step',
    skipTraversal: true,
    canRequestFocus: false,
  );

  /// The steps' fade-in on opening (paint only, MOT-5).
  late final AnimationController _fade = AnimationController(
    vsync: this,
    duration: KitMotion.quick,
    value: 1,
  );

  bool get _expanded => widget.expanded ?? _open;

  @override
  void didUpdateWidget(KitWorkLine oldWidget) {
    super.didUpdateWidget(oldWidget);
    // Controlled and opened by the host: the steps fade in all the same.
    if (widget.expanded == true &&
        oldWidget.expanded != true &&
        !_fade.isAnimating) {
      _startFade();
    }
    // Uncontrolled and untouched: a turn that comes to end on a failure
    // opens by itself (KitWorkLine.opensByDefault).
    if (widget.expanded == null &&
        !_touched &&
        widget.state != oldWidget.state) {
      final open = KitWorkLine.opensByDefault(widget.state);
      if (open != _open) {
        _open = open;
        if (open) _startFade();
      }
    }
  }

  @override
  void dispose() {
    _fade.dispose();
    _firstRevealed.dispose();
    super.dispose();
  }

  void _startFade() {
    if (KitMotion.reduced(context)) {
      _fade.value = 1;
    } else {
      _fade.forward(from: 0);
    }
  }

  void _toggle() {
    final next = !_expanded;
    _touched = true;
    if (widget.expanded == null) setState(() => _open = next);
    if (next) _startFade();
    widget.onExpansionChanged?.call(next);
  }

  /// The steps folded behind "Show earlier steps".
  int get _hidden {
    final steps = widget.steps;
    final tail = widget.tail;
    final window = tail != null && steps.length >= tail + 3
        ? tail
        : KitWorkLine.stepCap;
    return math.max(0, steps.length - window - _revealed);
  }

  void _revealEarlier() {
    final before = _hidden;
    final added = math.min(before, KitWorkLine.stepCap);
    setState(() {
      _revealed += added;
      _focusIndex = before - added;
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      // The step's own first control takes focus, so its ring shows.
      final own = _firstRevealed.traversalDescendants.firstOrNull;
      if (own != null) {
        own.requestFocus();
        return;
      }
      // A step with no control (prose): the wrapper holds focus this once,
      // so reading continues from there; Tab moves on in order.
      _firstRevealed.canRequestFocus = true;
      _firstRevealed.requestFocus();
    });
  }

  void _onFirstRevealedFocus(bool hasFocus) {
    if (!hasFocus) _firstRevealed.canRequestFocus = false;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final summary = KitWorkLine.summaryOf(context, widget.counts);
    final expanded = _expanded;
    final sep = l10n.kitWorkSeparator;
    final c = widget.counts;

    final (String label, String spoken, Widget? mark) = switch (widget.state) {
      // Opened while a step runs: the running step on the rail carries the
      // progress and its words, so the line keeps the summary and no mark.
      // One live mark, the step named once (owner, 2026-10-08).
      KitWorkState.running when expanded => (
        summary,
        '${l10n.kitWorkWorking}, $summary',
        null,
      ),
      KitWorkState.running => (
        widget.now ?? summary,
        '${l10n.kitWorkWorking}, ${widget.now ?? summary}',
        const KitStatusMark(state: KitMarkState.working),
      ),
      KitWorkState.waitingForYou => (
        l10n.kitWorkWaitingForYou,
        '${l10n.kitWorkWaitingForYou}, $summary',
        null,
      ),
      KitWorkState.done => (summary, summary, null),
      KitWorkState.endedFailed => (
        '$summary$sep${l10n.kitWorkDidntFinish}',
        '${l10n.kitWorkDidntFinish}, $summary',
        KitStatusMark(
          state: KitMarkState.failed,
          label: l10n.kitWorkDidntFinish,
        ),
      ),
      KitWorkState.stopped => (
        '$summary$sep${l10n.kitWorkStopped}',
        '${l10n.kitWorkStopped}, $summary',
        null,
      ),
    };

    final steps = widget.steps;
    final hidden = expanded ? _hidden : 0;
    final shown = <Widget>[
      for (var i = hidden; i < steps.length; i++)
        if (i == _focusIndex)
          Focus(
            focusNode: _firstRevealed,
            onFocusChange: _onFirstRevealedFocus,
            child: steps[i],
          )
        else
          steps[i],
    ];
    return KitStepTimeline(
      label: label,
      spoken: spoken,
      icon: KitStepTimeline.iconFor(
        read: c.read,
        searched: c.searched,
        listed: c.listed,
        edited: c.edited,
        ran: c.ran,
        fetched: c.fetched,
        delegated: c.delegated,
        other: c.other,
      ),
      mark: mark,
      expanded: expanded,
      onPressed: _toggle,
      head: hidden > 0
          ? Align(
              alignment: AlignmentDirectional.centerStart,
              child: KitButton.tertiary(
                label: l10n.kitWorkEarlierSteps(
                  math.min(hidden, KitWorkLine.stepCap),
                ),
                onPressed: _revealEarlier,
              ),
            )
          : null,
      steps: shown,
      // The steps appear at once and fade in (paint only, no size change;
      // MOT-5). Under reduced motion they are simply there.
      fade: KitMotion.reduced(context) ? null : _fade,
      lineKey: widget.lineKey,
      stepsKey: widget.stepsKey,
    );
  }
}
