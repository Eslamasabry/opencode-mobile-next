part of 'kit_top_bar.dart';

/// The subtitle line: the needs-you span, the words, and the
/// [KitTopBar.subtitleTail] only while the whole line fits on one line,
/// measured at the line's width, so it goes before the state itself is cut.
class _KitTopBarTailLine extends StatelessWidget {
  const _KitTopBarTailLine({
    super.key,
    required this.subtitle,
    required this.tail,
    required this.needsYou,
    required this.style,
  });

  final String subtitle;
  final String? tail;

  /// The needs-you count that leads the line; 0 draws none.
  final int needsYou;
  final TextStyle style;

  TextSpan _span(BuildContext context, String text) => TextSpan(
    children: [
      if (needsYou > 0) KitNeedsYou.span(context, count: needsYou),
      if (text.isNotEmpty) TextSpan(text: text, style: style),
    ],
  );

  Widget _line(BuildContext context, String text) => KitText.rich(
    _span(context, text),
    role: KitTextRole.secondary,
    maxLines: 1,
    overflow: TextOverflow.ellipsis,
  );

  @override
  Widget build(BuildContext context) {
    final tail = this.tail;
    if (tail == null || tail.isEmpty) return _line(context, subtitle);
    return LayoutBuilder(
      builder: (context, constraints) {
        final whole = '$subtitle · $tail';
        final painter = TextPainter(
          text: _span(context, whole),
          textDirection: Directionality.of(context),
          textScaler: MediaQuery.textScalerOf(context),
          maxLines: 1,
        )..layout(maxWidth: constraints.maxWidth);
        final fits = !painter.didExceedMaxLines;
        painter.dispose();
        return _line(context, fits ? whole : subtitle);
      },
    );
  }
}
