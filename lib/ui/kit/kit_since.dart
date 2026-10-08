import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/widgets.dart';

import '../../l10n/app_localizations.dart';
import 'kit_motion.dart';

/// Where a wait is (KitSince.md).
enum KitSincePhase {
  /// `since == null`: nothing is being waited for.
  idle,

  /// `since` was less than [KitMotion.escalateAfter] ago.
  waiting,

  /// `since` was [KitMotion.escalateAfter] or more ago.
  slow,
}

/// A wait's phase and how long it has run, from [KitSince.statusOf] or a
/// [KitSince] builder.
@immutable
class KitSinceStatus {
  const KitSinceStatus({required this.phase, required this.elapsed});

  final KitSincePhase phase;

  /// [Duration.zero] when [phase] is [KitSincePhase.idle]; never negative.
  final Duration elapsed;

  /// Whether the wait has reached [KitMotion.escalateAfter].
  bool get isSlow => phase == KitSincePhase.slow;
}

/// How often a slow wait rebuilds after it turned slow (KitSince.md).
enum KitSinceTicks {
  /// Rebuilds once, at the escalation: "Still waiting after 8 s".
  none,

  /// Rebuilds once at the escalation, then once per whole minute of
  /// elapsed time: "Waiting 4 min".
  minutes,

  /// Rebuilds on every whole second of elapsed time, from the start: a
  /// running reply's "Thinking · 12 s".
  seconds,
}

/// The kit's one wait timer (KitSince.md, C12). Told when a wait [since]
/// began, it rebuilds its [builder] exactly once when the wait turns
/// [KitSincePhase.slow] at [KitMotion.escalateAfter], and — with
/// [ticks] set to [KitSinceTicks.minutes] — once a minute after that, so a
/// host can say "Still waiting after 8 s" and offer a way out.
///
/// It draws nothing itself and reads time through `package:clock`, so
/// widget tests control it with the fake clock `testWidgets` already runs.
///
/// States: none — draws nothing; a wait's phase is not a visual state.
///
/// [KitSincePhase.idle], [KitSincePhase.waiting] and [KitSincePhase.slow]
/// are this widget's whole phase model; loading, empty, error, disabled
/// and working stay the host's.
class KitSince extends StatefulWidget {
  const KitSince({
    super.key,
    required this.since,
    required this.builder,
    this.ticks = KitSinceTicks.none,
    this.onEscalated,
  });

  /// When the wait began, on this phone's clock. `null` means idle: nothing
  /// is being waited for. A value in the future (clock skew, a server
  /// timestamp) reads as just started, never as negative.
  final DateTime? since;

  final Widget Function(BuildContext context, KitSinceStatus status) builder;

  final KitSinceTicks ticks;

  /// Called once per [since] value, when it turns slow: a host's log or
  /// telemetry, never for announcing (the host's live region does that,
  /// exactly once, because this phase flips exactly once per [since]).
  final VoidCallback? onEscalated;

  /// "Still waiting after 8 s", with the number from
  /// [KitMotion.escalateAfter].
  static String slowLabel(BuildContext context) => lookupAppLocalizations(
    Localizations.localeOf(context),
  ).kitSinceStillWaiting(KitMotion.escalateAfter.inSeconds);

  /// [slowLabel] for a wait that has run [elapsed]: "Still waiting after
  /// 23 s" under a minute (never under the escalation's own 8 s), then
  /// "Waiting 1 min" ([waitingLabel]), for a slow line that keeps counting.
  static String slowLabelAt(BuildContext context, Duration elapsed) {
    if (elapsed.inMinutes >= 1) return waitingLabel(context, elapsed);
    final seconds = elapsed.inSeconds < KitMotion.escalateAfter.inSeconds
        ? KitMotion.escalateAfter.inSeconds
        : elapsed.inSeconds;
    return lookupAppLocalizations(
      Localizations.localeOf(context),
    ).kitSinceStillWaiting(seconds);
  }

  /// "Waiting 4 min" / "Waiting less than a minute", on whole minutes of
  /// [elapsed].
  static String waitingLabel(BuildContext context, Duration elapsed) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return elapsed.inHours < 1
        ? l10n.kitSinceWaitingFor(elapsed.inMinutes)
        : l10n.kitSinceWaitingForLong(durationLabel(context, elapsed));
  }

  /// The age alone, for a host that places it mid-line: "4 min" / "less
  /// than a minute" / "2 h 5 min". [KitNeedsYou] and [KitRequestCard]'s
  /// caption compose "waiting {age}" with it, so the kit words an age in
  /// one place.
  static String ageLabel(BuildContext context, Duration elapsed) =>
      elapsed.inHours < 1
      ? lookupAppLocalizations(
          Localizations.localeOf(context),
        ).kitSinceAge(elapsed.inMinutes)
      : durationLabel(context, elapsed);

  /// A span in the largest units that read at a glance: "less than a
  /// minute", "45 min", "3 h 20 min", "1 d 21 h". Never a count of
  /// thousands of minutes.
  static String durationLabel(BuildContext context, Duration elapsed) =>
      durationWords(
        lookupAppLocalizations(Localizations.localeOf(context)),
        elapsed,
      );

  /// [durationLabel] without a context.
  static String durationWords(AppLocalizations l10n, Duration elapsed) {
    final span = elapsed.isNegative ? Duration.zero : elapsed;
    if (span.inHours < 1) return l10n.kitSinceAge(span.inMinutes);
    if (span.inDays < 1) {
      final minutes = span.inMinutes % 60;
      return minutes == 0
          ? l10n.kitDurationHours(span.inHours)
          : l10n.kitDurationHoursMinutes(span.inHours, minutes);
    }
    final hours = span.inHours % 24;
    return hours == 0
        ? l10n.kitDurationDays(span.inDays)
        : l10n.kitDurationDaysHours(span.inDays, hours);
  }

  /// The phase of a wait that began at [since], at [now] (default
  /// `clock.now()`). For controllers and tests that need the rule without a
  /// widget.
  static KitSinceStatus statusOf(DateTime? since, {DateTime? now}) {
    if (since == null) {
      return const KitSinceStatus(
        phase: KitSincePhase.idle,
        elapsed: Duration.zero,
      );
    }
    var elapsed = (now ?? clock.now()).difference(since);
    if (elapsed.isNegative) elapsed = Duration.zero;
    return KitSinceStatus(
      phase: elapsed >= KitMotion.escalateAfter
          ? KitSincePhase.slow
          : KitSincePhase.waiting,
      elapsed: elapsed,
    );
  }

  @override
  State<KitSince> createState() => _KitSinceState();
}

class _KitSinceState extends State<KitSince> with WidgetsBindingObserver {
  Timer? _timer;
  late KitSinceStatus _status;

  /// The `since` value [onEscalated] last fired for, or this sentinel
  /// before it has fired at all. A plain [Object] is never `==` to a
  /// [DateTime], so the first real `since` always counts as new.
  Object? _escalatedFor = Object();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _status = KitSince.statusOf(widget.since);
    if (_status.isSlow) _escalate();
    _schedule();
  }

  @override
  void didUpdateWidget(KitSince oldWidget) {
    super.didUpdateWidget(oldWidget);
    final newSince = oldWidget.since != widget.since;
    if (newSince) _escalatedFor = Object();
    // A ticks change alone still recomputes the status first: the pending
    // wait is measured from `since` now, not from the last build.
    if (newSince || oldWidget.ticks != widget.ticks) _resync();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _resync();
  }

  /// Recomputes the status from the wall clock (a timer firing, a new
  /// [KitSince.since] or [KitSince.ticks], or time that passed while the app
  /// was backgrounded) and reschedules from it.
  void _resync() {
    final next = KitSince.statusOf(widget.since);
    if (mounted) {
      setState(() => _status = next);
    } else {
      _status = next;
    }
    if (next.isSlow) _escalate();
    _schedule();
  }

  void _escalate() {
    if (_escalatedFor == widget.since) return;
    _escalatedFor = widget.since;
    widget.onEscalated?.call();
  }

  void _schedule() {
    _timer?.cancel();
    _timer = null;
    if (widget.since == null) return;
    if (widget.ticks == KitSinceTicks.seconds &&
        _status.phase != KitSincePhase.idle) {
      final intoSecond =
          _status.elapsed - Duration(seconds: _status.elapsed.inSeconds);
      _timer = Timer(const Duration(seconds: 1) - intoSecond, _fire);
      return;
    }
    final Duration? wait = switch (_status.phase) {
      KitSincePhase.idle => null,
      KitSincePhase.waiting => KitMotion.escalateAfter - _status.elapsed,
      KitSincePhase.slow =>
        widget.ticks != KitSinceTicks.minutes
            ? null
            : const Duration(minutes: 1) -
                  (_status.elapsed -
                      Duration(minutes: _status.elapsed.inMinutes)),
    };
    if (wait == null) return;
    _timer = Timer(wait.isNegative ? Duration.zero : wait, _fire);
  }

  void _fire() {
    _timer = null;
    _resync();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.builder(context, _status);
}
