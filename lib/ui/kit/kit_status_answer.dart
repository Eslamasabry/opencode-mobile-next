import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../app_theme.dart';
import 'kit_buttons.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_status_mark.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// What [KitStatusAnswer] says about the thing it answers for.
enum KitAnswerTone {
  /// It works: a mint dot with a soft halo.
  ok,

  /// It rests and comes back by itself (a heat pause, a restart that is
  /// scheduled): the muted pause glyph. Nobody has to act, so never amber.
  paused,

  /// Something is under way (starting, stopping, setting up): the small
  /// spinner, a still dot under reduced motion.
  working,

  /// It waits for the person: the amber dot (the needs-you colour, the one
  /// place amber is used).
  needsYou,

  /// It is off, as the person left it: a hollow hairline ring.
  off,
}

/// How large [KitStatusAnswer] sets its answer.
enum KitAnswerScale {
  /// 40/46: a status page's one answer ("Everything is running").
  large,

  /// 28/34: an empty or offline state's calm line ("Nothing running"),
  /// smaller than the page's own title so the page keeps its name
  /// (`KitStateView.calm`).
  calm,

  /// 24/30: the same calm line inside a list or a sheet (a search with no
  /// result), where the page already has its own big words.
  compact,
}

/// The one big plain answer a status page opens on, centred: "Is it OK?"
/// answered in large type ("Everything is running"), one small line under
/// it ("Uses about 410 MB of memory"), and at most one quiet text action.
/// The mark above the words carries the state ([KitAnswerTone]); the words
/// always say it too, so nothing is told by colour alone.
///
/// [countdownTo] makes the line a live countdown: [countdownLine] is asked
/// for the line once a second with the time left (zero once it has passed),
/// so "It restarts by itself in about 40 s" stays true while it is shown.
/// Only for something the app really scheduled.
///
/// A new answer cross-fades in over [KitMotion.standard] with a short rise;
/// under reduced motion it swaps at once. The answer is one live region, so
/// a screen reader hears a change without moving focus.
///
/// States: none — its tones and countdown are variants of one answer.
/// (Tones: ok, paused, working, needs-you, off. No loading, empty, error
/// or disabled state of its own.)
class KitStatusAnswer extends StatefulWidget {
  const KitStatusAnswer({
    super.key,
    this.tone,
    required this.answer,
    this.scale = KitAnswerScale.large,
    this.line,
    this.countdownTo,
    this.countdownLine,
    this.action,
    this.answerKey,
    this.lineKey,
  }) : assert(
         countdownTo == null || countdownLine != null,
         'KitStatusAnswer: a countdown needs countdownLine',
       );

  /// The mark above the answer; null draws none (an empty list has no
  /// state to mark: its words say it).
  final KitAnswerTone? tone;

  /// The answer, in a few plain words ("Everything is running").
  final String answer;

  /// How large the answer is set.
  final KitAnswerScale scale;

  /// One small line under it: a cost, a reason, what happens next.
  final String? line;

  /// When the thing the line counts down to happens; with [countdownLine].
  final DateTime? countdownTo;

  /// The line for the time left until [countdownTo]; wins over [line].
  final String Function(Duration left)? countdownLine;

  /// One quiet text action under the line ("Use this phone's server"). The
  /// screen's main action, when there is one, is its pinned bottom button.
  final KitAction? action;

  final Key? answerKey;
  final Key? lineKey;

  @override
  State<KitStatusAnswer> createState() => _KitStatusAnswerState();
}

/// The answer's display size: larger than a screen title, tight tracking.
const double _answerSize = 40;
const double _calmSize = 28;
const double _maxAnswerScale = 1.5;

const double _compactSize = 24;

double _sizeOf(KitAnswerScale scale) => switch (scale) {
  KitAnswerScale.large => _answerSize,
  KitAnswerScale.calm => _calmSize,
  KitAnswerScale.compact => _compactSize,
};

/// Solid's large answer: 32 at the large title's weight, so "Everything
/// is running" fits one line on a phone; a Style with type of its own
/// ([KitText.firmType]) keeps the 40 it was drawn with.
const double _quietAnswerSize = 32;

TextStyle _answerStyle(BuildContext context, KitAnswerScale scale) {
  final quiet = scale == KitAnswerScale.large && !KitText.firmType(context);
  final size = quiet ? _quietAnswerSize : _sizeOf(scale);
  return KitText.styleOf(context, KitTextRole.largeTitle).copyWith(
    fontSize: size,
    height: switch (scale) {
      _ when quiet => 38 / size,
      KitAnswerScale.large => 46 / _answerSize,
      KitAnswerScale.calm => 34 / _calmSize,
      KitAnswerScale.compact => 30 / _compactSize,
    },
    letterSpacing: switch (scale) {
      _ when quiet => -0.64,
      KitAnswerScale.large => -1.1,
      KitAnswerScale.calm => -0.6,
      KitAnswerScale.compact => -0.4,
    },
  );
}

class _KitStatusAnswerState extends State<KitStatusAnswer> {
  Timer? _tick;

  @override
  void initState() {
    super.initState();
    _schedule();
  }

  @override
  void didUpdateWidget(KitStatusAnswer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.countdownTo != widget.countdownTo) _schedule();
  }

  void _schedule() {
    _tick?.cancel();
    _tick = null;
    final until = widget.countdownTo;
    if (until == null || !until.isAfter(DateTime.now())) return;
    _tick = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      setState(() {});
      if (!until.isAfter(DateTime.now())) {
        _tick?.cancel();
        _tick = null;
      }
    });
  }

  @override
  void dispose() {
    _tick?.cancel();
    super.dispose();
  }

  String? get _line {
    final until = widget.countdownTo;
    final words = widget.countdownLine;
    if (until == null || words == null) return widget.line;
    final left = until.difference(DateTime.now());
    return words(left.isNegative ? Duration.zero : left);
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final reduced = KitMotion.reduced(context);
    final line = _line;
    final action = widget.action;
    final tone = widget.tone;
    final calm = widget.scale != KitAnswerScale.large;
    final body = Column(
      key: ValueKey((widget.tone, widget.answer)),
      mainAxisSize: MainAxisSize.min,
      children: [
        if (tone != null) ...[
          ExcludeSemantics(child: _Mark(tone: tone)),
          SizedBox(height: tokens.space4),
        ],
        Semantics(
          container: true,
          header: true,
          child: _AnswerText(
            widget.answer,
            textKey: widget.answerKey,
            scale: widget.scale,
          ),
        ),
        if (line != null) ...[
          SizedBox(height: calm ? tokens.space2 : tokens.space3),
          _AnswerText(
            line,
            textKey: widget.lineKey,
            line: true,
            scale: widget.scale,
          ),
        ],
        if (action != null) ...[
          SizedBox(height: tokens.space4),
          KitButton.tertiary(
            key: action.key,
            label: action.label,
            icon: action.icon,
            onPressed: action.onPressed,
          ),
        ],
      ],
    );
    final answer = Semantics(
      container: true,
      liveRegion: true,
      child: AnimatedSwitcher(
        duration: reduced ? Duration.zero : KitMotion.standard,
        switchInCurve: KitMotion.enter,
        switchOutCurve: KitMotion.exit,
        layoutBuilder: (current, previous) => Stack(
          alignment: Alignment.center,
          children: [...previous, ?current],
        ),
        transitionBuilder: (child, animation) => FadeTransition(
          opacity: animation,
          child: SlideTransition(
            position: Tween(
              begin: const Offset(0, .04),
              end: Offset.zero,
            ).animate(animation),
            child: child,
          ),
        ),
        child: body,
      ),
    );
    // Wide margins keep the answer to a calm column; a narrow phone gives
    // the words the room instead.
    return LayoutBuilder(
      builder: (context, constraints) {
        final margin = constraints.maxWidth < _narrowWidth
            ? tokens.gutter
            : tokens.gutter + tokens.gutter;
        return Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: margin),
          child: answer,
        );
      },
    );
  }
}

/// Below this width the answer keeps only the gutter as its margin.
const double _narrowWidth = 360;

/// The answer in display type, set the way a headline is: grown with the
/// person's text up to [_maxAnswerScale], never split inside a word (the
/// size steps down until the longest word fits), and with its lines
/// balanced so no word is left alone on the last line.
class _AnswerText extends StatelessWidget {
  const _AnswerText(
    this.text, {
    this.textKey,
    this.line = false,
    this.scale = KitAnswerScale.large,
  });

  final String text;
  final Key? textKey;
  final KitAnswerScale scale;

  /// The small line under the answer: body size, grown with text as body
  /// text is, balanced the same way.
  final bool line;

  @override
  Widget build(BuildContext context) {
    final base = line
        ? KitText.styleOf(
            context,
            KitTextRole.body,
            tone: KitTextTone.secondary,
          )
        : _answerStyle(context, scale);
    final size = line ? _sizeOf(scale) : base.fontSize ?? _sizeOf(scale);
    final scaler = MediaQuery.textScalerOf(context);
    final scaled = line
        ? scaler.scale(base.fontSize ?? 16)
        : scaler.clamp(maxScaleFactor: _maxAnswerScale).scale(size);
    // On a narrow window large text would stack one word per line.
    final narrowSize = scaler.clamp(maxScaleFactor: 1.2).scale(size);
    final direction = Directionality.of(context);
    return LayoutBuilder(
      builder: (context, constraints) {
        final max = constraints.maxWidth;
        var style = base.copyWith(
          fontSize: !line && max < 300 ? narrowSize : scaled,
        );
        TextPainter paint(TextStyle style, double width) => TextPainter(
          text: TextSpan(text: text, style: style),
          textDirection: direction,
          textAlign: TextAlign.center,
        )..layout(maxWidth: width);
        double widestWord(TextStyle style) {
          var widest = 0.0;
          for (final word in text.split(' ')) {
            final painter = TextPainter(
              text: TextSpan(text: word, style: style),
              textDirection: direction,
            )..layout();
            if (painter.width > widest) widest = painter.width;
            painter.dispose();
          }
          return widest;
        }

        // The longest word at the size finally used: the balanced width
        // never goes under it, or the word would break.
        var widest = 0.0;
        if (max.isFinite) {
          widest = widestWord(style);
          if (widest > max) {
            style = style.copyWith(
              fontSize: (style.fontSize! * max / widest).floorToDouble(),
            );
            widest = widestWord(style);
          }
        }
        var width = max;
        if (max.isFinite) {
          final full = paint(style, max);
          final lines = full.computeLineMetrics().length;
          full.dispose();
          if (lines > 1) {
            // The narrowest width that keeps the same number of lines.
            var high = max;
            var low = math.min(high, math.max(max / 2, widest.ceilToDouble()));
            for (var i = 0; i < 12; i++) {
              final mid = (low + high) / 2;
              final probe = paint(style, mid);
              final fits = probe.computeLineMetrics().length <= lines;
              probe.dispose();
              if (fits) {
                high = mid;
              } else {
                low = mid;
              }
            }
            width = high.ceilToDouble();
          }
        }
        final words = Text(
          text,
          key: textKey,
          textAlign: TextAlign.center,
          style: style,
          textScaler: TextScaler.noScaling,
        );
        return SizedBox(width: width.isFinite ? width : null, child: words);
      },
    );
  }
}

/// The mark above the answer: 14 dp, in a 32 dp slot so every tone sits on
/// the same line.
class _Mark extends StatelessWidget {
  const _Mark({required this.tone});

  final KitAnswerTone tone;

  static const double _dot = 14;
  static const double _bar = 4;
  static const double _slot = 32;

  @override
  Widget build(BuildContext context) {
    final roles = AppTheme.rolesOf(Theme.of(context));
    // Solid: the lamp is one flat dot, no glow (its signature, and nothing
    // that is not working glows). Other styles keep its soft halo.
    final flat = KitSurfaceLook.styleOf(context) == KitSurfaceStyle.solid;
    Widget dot(Color color) => Container(
      width: _dot,
      height: _dot,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: color,
        boxShadow: flat
            ? null
            : [
                // A soft halo of its own colour: the dot reads as lit.
                BoxShadow(
                  color: color.withValues(alpha: roles.isDark ? .45 : .30),
                  blurRadius: 18,
                  spreadRadius: 2,
                ),
              ],
      ),
    );
    final Widget mark = switch (tone) {
      KitAnswerTone.ok => dot(roles.success),
      KitAnswerTone.needsYou => dot(roles.attention),
      // Two short bars, the pause sign drawn at the dot's size: calm and
      // neutral, nobody has to act.
      KitAnswerTone.paused => Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          for (var i = 0; i < 2; i++) ...[
            if (i == 1) SizedBox(width: KitTokens.of(context).space1),
            Container(
              width: _bar,
              height: _dot,
              decoration: BoxDecoration(
                color: roles.text2,
                borderRadius: BorderRadius.circular(_bar / 2),
              ),
            ),
          ],
        ],
      ),
      KitAnswerTone.working => const KitStatusMark(state: KitMarkState.working),
      KitAnswerTone.off => Container(
        width: _dot,
        height: _dot,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          border: Border.all(
            color: roles.text3,
            width: KitTokens.focusRingWidth(context),
          ),
        ),
      ),
    };
    return SizedBox.square(
      dimension: _slot,
      child: Center(child: mark),
    );
  }
}
