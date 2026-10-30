import 'dart:ui' show SemanticsRole;

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// The screen's one loading indicator (design standard §4): a 2 dp bar
/// directly under the top bar or header while anything on the screen loads
/// for the first time. It always takes its 2 dp, so the page does not jump
/// when loading starts or ends, and it carries a label for screen readers.
class KitLoadingBar extends StatelessWidget {
  const KitLoadingBar({super.key, required this.loading, required this.label});

  final bool loading;
  final String label;

  @override
  Widget build(BuildContext context) => SizedBox(
    height: KitTokens.loadingBarHeight,
    child: loading
        ? Semantics(
            key: const ValueKey('kit-loading-bar'),
            label: label,
            child: const LinearProgressIndicator(
              minHeight: KitTokens.loadingBarHeight,
            ),
          )
        : null,
  );
}

/// Placeholder rows while a list loads (§4): the shape of the rows to come,
/// with no words and no motion, hidden from screen readers (the loading bar
/// already says the screen is loading). Matches [KitRow]'s geometry.
///
/// The shapes are `text1` laid thinly over whatever they sit on, so they
/// read on every surface level in light and dark: on the ground, on a
/// grouped card (`surface1`, white in light, where a `surface2` bar
/// vanished), on a sheet and on `surface3`.
class KitSkeletonRows extends StatelessWidget {
  const KitSkeletonRows({super.key, this.count = 4});

  final int count;

  static const _titleWidths = [0.72, 0.56, 0.64, 0.48, 0.6];

  /// How much `text1` the leading mark and the title bar carry.
  static const shapeAlpha = .12;

  /// How much `text1` the supporting bar carries (a step lighter).
  static const lineAlpha = .09;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final ink = tokens.roles.text1;
    final shape = ink.withValues(alpha: shapeAlpha);
    final line = ink.withValues(alpha: lineAlpha);
    Widget bar(double height, Color color) => Container(
      height: height,
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(height / 2),
      ),
    );
    return ExcludeSemantics(
      child: Column(
        key: const ValueKey('kit-skeleton-rows'),
        children: [
          for (var i = 0; i < count; i++)
            SizedBox(
              height: KitTokens.skeletonRowHeight,
              child: Padding(
                padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
                child: Row(
                  children: [
                    SizedBox.square(
                      dimension: 32,
                      child: Center(
                        child: Container(
                          width: 20,
                          height: 20,
                          decoration: BoxDecoration(
                            color: shape,
                            shape: BoxShape.circle,
                          ),
                        ),
                      ),
                    ),
                    SizedBox(width: tokens.space3),
                    Expanded(
                      child: LayoutBuilder(
                        builder: (context, constraints) => Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            SizedBox(
                              width:
                                  constraints.maxWidth *
                                  _titleWidths[i % _titleWidths.length],
                              child: bar(12, shape),
                            ),
                            SizedBox(height: tokens.space2),
                            SizedBox(
                              width: constraints.maxWidth * 0.28,
                              child: bar(9, line),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// The eta words for [KitProgress.known] and [KitProgress.staged]
/// (KitProgress.md "The eta words"): under a minute, rounded up to the
/// nearest 10 s; under an hour, whole minutes rounded up; beyond that,
/// whole hours, also rounded up, so the words never promise less time than
/// the measurement (2 h 54 min is "about 3 h left", never "about 2 h").
/// An [eta] of zero or less is never shown (STATE-6: never an estimate
/// dressed up as a measurement).
String? _etaWords(AppLocalizations l10n, Duration? eta) {
  if (eta == null || eta <= Duration.zero) return null;
  if (eta < const Duration(seconds: 60)) {
    final tenSeconds = (eta.inMilliseconds / 10000).ceil();
    return l10n.kitProgressEtaSeconds(tenSeconds * 10);
  }
  if (eta < const Duration(hours: 1)) {
    return l10n.kitProgressEtaMinutes((eta.inSeconds / 60).ceil());
  }
  return l10n.kitProgressEtaHours((eta.inSeconds / 3600).ceil());
}

/// Progress inside a [KitStateView] (also read by `KitChecklist` and
/// `KitProgressRow`): indeterminate while waiting, determinate once a
/// fraction or a stage is known, with one line under it ("29 of 30 MB ·
/// about 1 min left", "Step 3 of 5 · Installing · about 2 min left").
///
/// States: waiting, known, staged, stopped, failed.
class KitProgress {
  const KitProgress.waiting({
    this.caption,
    this.key,
    this.tone,
    this.semanticsLabel,
  }) : value = null,
       eta = null,
       step = null,
       of = null,
       label = null,
       stepValue = null;

  const KitProgress.known(
    double this.value, {
    this.caption,
    this.eta,
    this.key,
    this.tone,
    this.semanticsLabel,
  }) : assert(
         value >= 0 && value <= 1,
         'KitProgress.known: value must be between 0 and 1',
       ),
       step = null,
       of = null,
       label = null,
       stepValue = null;

  /// A job made of stages: "Step 3 of 5 · Installing · about 2 min left".
  /// The bar is determinate at `(step - 1 + (stepValue ?? 0)) / of`.
  ///
  /// Within one job (the same [of], [step] and [label]) the bar never goes
  /// backwards; a debug build asserts it (KitProgress.md "Data safety and
  /// honest state"). A step going back may lower it. A new job, or a stage
  /// measured again from zero, is a new [KitProgressView]: give it a new key.
  const KitProgress.staged({
    required int this.step,
    required int this.of,
    required String this.label,
    this.stepValue,
    this.eta,
    this.caption,
    this.key,
    this.tone,
    this.semanticsLabel,
  }) : assert(of >= 1, 'KitProgress.staged: of must be at least 1'),
       assert(
         step >= 1 && step <= of,
         'KitProgress.staged: step must be between 1 and of',
       ),
       assert(
         stepValue == null || (stepValue >= 0 && stepValue <= 1),
         'KitProgress.staged: stepValue must be between 0 and 1',
       ),
       value = (step - 1 + (stepValue ?? 0)) / of;

  /// 0..1, or null while the amount is unknown. Computed for [staged].
  final double? value;
  final String? caption;

  /// Time left, shown as words appended to [line]. Never an invented
  /// estimate: the caller supplies it only from a measurement or a
  /// component estimate.
  final Duration? eta;

  /// 1-based; 1 <= step <= of. Null outside [staged].
  final int? step;

  /// >= 1. Null outside [staged].
  final int? of;

  /// What this stage does, in words: "Installing". Null outside [staged].
  final String? label;

  /// 0..1 within the current stage, when measured. [staged] only.
  final double? stepValue;

  /// Key of the bar itself, for tests.
  final Key? key;

  /// The bar's colour: null is the accent (it is moving or on track);
  /// neutral is a stopped job (`text3`); failure is a failed one (`text1`,
  /// LOOK-5 interim: a failure is said in words, never in the danger red).
  final AppStatusTone? tone;

  /// What a screen reader calls the bar ("Setup progress").
  final String? semanticsLabel;

  /// True for [staged], where [value] is derived rather than given.
  bool get isStaged => step != null;

  /// The one line under the bar, joined with " · ":
  /// staged: "Step 3 of 5 · Installing[ · caption][ · about 2 min left]";
  /// known and waiting: "caption[ · about 1 min left]".
  String line(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final step = this.step, of = this.of, label = this.label;
    final etaWords = _etaWords(l10n, eta);
    final parts = <String>[
      if (step != null && of != null) l10n.kitProgressStep(step, of),
      ?label,
      ?caption,
      ?etaWords,
    ];
    return parts.join(' · ');
  }
}

/// Where the still waiting bar ends under reduced motion (KitProgress.md
/// "Motion and haptics": a static 30 % track segment at the start).
const double _stillWaitingEnd = 0.3;

/// Renders a [KitProgress]: a rounded bar and its line of words.
///
/// States: waiting, known, staged, stopped, failed.
class KitProgressView extends StatelessWidget {
  const KitProgressView({super.key, required this.progress});

  final KitProgress progress;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final theme = Theme.of(context);
    final roles = AppTheme.rolesOf(theme);
    final reduceMotion = KitMotion.reduced(context);
    final value = progress.value;
    final line = progress.line(context);
    final color = switch (progress.tone) {
      AppStatusTone.neutral => roles.text3,
      AppStatusTone.failure => roles.text1,
      _ => null,
    };
    final barKey = progress.key ?? const ValueKey('kit-state-progress');

    // A11Y-3, K2 §2.8: the bar is one semantics node, and the line is read
    // once, as part of it. Its label carries the line (a screen reader
    // hears label then value together). The value itself is left to the
    // framework: `LinearProgressIndicator` gives a determinate bar
    // `SemanticsRole.progressBar`, whose value must be a bare number or a
    // "NN%" string (framework invariant) — never a sentence, so the line
    // cannot live there. A percentage is what the framework then adds by
    // itself for `known` and `staged` alike.
    final label = [
      if (progress.semanticsLabel case final l? when l.isNotEmpty) l,
      if (line.isNotEmpty) line,
    ].join(', ');
    final semanticsLabel = label.isEmpty ? null : label;

    Widget track(double? shown, {String? semanticsLabel}) =>
        LinearProgressIndicator(
          key: barKey,
          value: shown,
          minHeight: KitTokens.progressBarHeight,
          borderRadius: BorderRadius.circular(KitTokens.progressBarRadius),
          color: color,
          semanticsLabel: semanticsLabel,
        );

    Widget bar;
    if (value == null) {
      // Waiting is indeterminate for every reader (STATE-7): one node with
      // the label and no value, whether or not motion is reduced. The
      // framework's bar keeps moving; under reduced motion (MOT-5, G8) it
      // is drawn as a still 30 % segment instead, with no ticker. That
      // drawing is a determinate bar to the framework, so it is kept out of
      // the semantics tree: its 0.3 must never be read as "30 percent" of
      // a job whose length nobody knows.
      bar = Semantics(
        container: true,
        label: semanticsLabel,
        role: SemanticsRole.loadingSpinner,
        child: ExcludeSemantics(
          child: track(reduceMotion ? _stillWaitingEnd : null),
        ),
      );
    } else {
      Widget determinate(double shown) =>
          track(shown, semanticsLabel: semanticsLabel);
      // A determinate value animates to its new value (paint only); under
      // reduced motion it jumps straight there. The container keeps the bar
      // its own node: without it the framework's label, role and value
      // fold into the host's node (KitStateView's title and body).
      bar = Semantics(
        container: true,
        child: reduceMotion
            ? determinate(value)
            : TweenAnimationBuilder<double>(
                tween: Tween<double>(end: value),
                duration: KitMotion.standard,
                curve: KitMotion.enter,
                builder: (context, shown, _) => determinate(shown),
              ),
      );
    }

    return _ForwardOnly(
      progress: progress,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          bar,
          if (line.isNotEmpty) ...[
            SizedBox(height: KitTokens.of(context).space2),
            // Already the bar's label: read there, not a second time here.
            ExcludeSemantics(
              child: KitText.rich(
                TextSpan(
                  text: line,
                  style: const TextStyle(
                    fontFeatures: [FontFeature.tabularFigures()],
                  ),
                ),
                role: KitTextRole.secondary,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// Whether [after] keeps [before]'s job moving forward (KitProgress.md
/// "Data safety and honest state"): within one staged job — the same `of`,
/// step and label — the value never drops. A step going back, a different
/// `of` or label, and any `known` or `waiting` progress are not checked:
/// the part has no job identity for them, and today's hosts draw a new job
/// on the same bar (SetupProgressView's re-run starts over).
bool _forwardWithinJob(KitProgress before, KitProgress after) {
  if (!before.isStaged || !after.isStaged) return true;
  if (before.of != after.of ||
      before.step != after.step ||
      before.label != after.label) {
    return true;
  }
  return (after.stepValue ?? 0) >= (before.stepValue ?? 0);
}

/// Holds the previous [KitProgress] so a debug build can assert that the
/// bar never goes backwards within a job. Paints nothing of its own.
class _ForwardOnly extends StatefulWidget {
  const _ForwardOnly({required this.progress, required this.child});

  final KitProgress progress;
  final Widget child;

  @override
  State<_ForwardOnly> createState() => _ForwardOnlyState();
}

class _ForwardOnlyState extends State<_ForwardOnly> {
  /// Debug only: the progress the last update replaced. Checked in [build],
  /// where a failed assertion is reported cleanly (an error thrown while
  /// the element is updating would leave the tree half-updated).
  KitProgress? _before;

  @override
  void didUpdateWidget(_ForwardOnly oldWidget) {
    super.didUpdateWidget(oldWidget);
    assert(() {
      _before = oldWidget.progress;
      return true;
    }());
  }

  @override
  Widget build(BuildContext context) {
    final before = _before, after = widget.progress;
    assert(
      before == null || _forwardWithinJob(before, after),
      'KitProgress: step ${after.step} of ${after.of} ("${after.label}") '
      'went back from ${before.value} to ${after.value}. A bar that shrinks '
      'reads as a lie; a new job, or a stage measured again from zero, is a '
      'new KitProgressView (give it a new key).',
    );
    return widget.child;
  }
}
