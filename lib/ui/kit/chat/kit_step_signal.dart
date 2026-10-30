// The step timeline under Signal (owner, 2026-10-30): the rail is the
// track, each node a signal, and the folded work line a stretch of track
// with its sleepers, its words in mono between two dashed rules and an
// engraved Show at the end. Part of kit_step_timeline.dart so it reads the
// rail's measures; the instruments themselves are style/kit_signal.dart.
part of 'kit_step_timeline.dart';

/// Whether a step in [state] keeps its timeline's track live (solid).
bool _signalLive(KitStepNodeState state) =>
    state == KitStepNodeState.running ||
    state == KitStepNodeState.background ||
    state == KitStepNodeState.waiting;

/// A step's node as a signal, [size] tall: a green head while it runs
/// (breathing; calmer in the background), a red one while it waits for
/// the person, an unlit one before it starts, the failed [mark] when it
/// failed, and a hollow ring once it is done or stopped.
Widget _signalNode(
  BuildContext context,
  KitStepNodeState state,
  double size,
  Widget? mark,
) {
  final Widget node = switch (state) {
    KitStepNodeState.running => kitSignalHead(
      aspect: KitSignalAspect.go,
      height: size,
      breathing: true,
    ),
    KitStepNodeState.background => kitSignalHead(
      aspect: KitSignalAspect.go,
      height: size,
    ),
    KitStepNodeState.waiting => kitSignalHead(
      aspect: KitSignalAspect.stop,
      height: size,
    ),
    KitStepNodeState.queued => kitSignalHead(
      aspect: KitSignalAspect.off,
      height: size,
    ),
    KitStepNodeState.failed when mark != null => mark,
    _ => kitSignalRing(size: math.min(12, size)),
  };
  return SizedBox.square(
    key: ValueKey('kit-step-signal-${state.name}'),
    dimension: size,
    child: Center(child: node),
  );
}

/// A sentence between steps: a short sleeper across the track.
Widget _signalSleeper(_Rail rail) => ExcludeSemantics(
  child: SizedBox(
    width: KitTrack.sleeper,
    height: KitTrack.sidingStroke,
    child: CustomPaint(painter: _SleeperPainter(rail.signal!.track)),
  ),
);

class _SleeperPainter extends CustomPainter {
  const _SleeperPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) =>
      canvas.drawRect(Offset.zero & size, Paint()..color = color);

  @override
  bool shouldRepaint(_SleeperPainter old) => old.color != color;
}

/// The folded work line's lead under Signal: the green head while a step
/// runs, the failed [mark], or the sleepers on a stretch of track.
Widget _signalLead(
  _Rail rail,
  double first, {
  required bool live,
  Widget? mark,
}) {
  if (live) {
    return kitSignalHead(
      aspect: KitSignalAspect.go,
      height: math.min(first + 4, rail.glyph + 6),
      breathing: true,
    );
  }
  if (mark != null) return mark;
  return kitSignalTies(width: rail.glyph * .7, height: first + 6);
}

/// The folded work line's words and its engraved Show (or Hide) under
/// Signal: the counts in mono, the word at the end in small capitals.
Widget _signalLineWords(
  BuildContext context, {
  required String label,
  required bool expanded,
  required bool wide,
}) {
  final tokens = KitTokens.of(context);
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  final word = expanded ? l10n.signalHide : l10n.signalShow;
  final engraved = KitText.styleOf(context, KitTextRole.caption).copyWith(
    fontWeight: FontWeight.w600,
    letterSpacing: 12 * .12,
    shadows: const [],
  );
  return Row(
    crossAxisAlignment: CrossAxisAlignment.center,
    children: [
      Expanded(
        child: wide
            ? KitText(label, role: KitTextRole.mono, tone: KitTextTone.primary)
            : KitWordText(
                label,
                role: KitTextRole.mono,
                tone: KitTextTone.primary,
                maxLines: 2,
              ),
      ),
      SizedBox(width: tokens.space2),
      ExcludeSemantics(child: Text(word.toUpperCase(), style: engraved)),
    ],
  );
}

/// The dashed rules above and below the folded work line, from where its
/// words start to its end (mirrored in RTL).
class _DashedRules extends CustomPainter {
  const _DashedRules({
    required this.color,
    required this.start,
    required this.rtl,
  });

  final Color color;
  final double start;
  final bool rtl;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = color;
    const dash = 3.0, gap = 3.0, weight = 1.5;
    final from = rtl ? 0.0 : start;
    final to = rtl ? size.width - start : size.width;
    for (final y in [0.0, size.height - weight]) {
      for (var x = from; x < to; x += dash + gap) {
        canvas.drawRect(
          Rect.fromLTRB(x, y, math.min(x + dash, to), y + weight),
          paint,
        );
      }
    }
  }

  @override
  bool shouldRepaint(_DashedRules old) =>
      old.color != color || old.start != start || old.rtl != rtl;
}
