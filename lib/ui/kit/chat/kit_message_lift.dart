part of 'kit_message.dart';

// The sent prompt lifting out of the composer (owner request, 2026-10-27):
// the bubble starts where the person typed it, a little smaller and
// transparent, and rises into its place in the transcript over
// [KitMotion.standard] on [KitMotion.enter]. It comes up from behind the
// composer, which stays drawn above the transcript (its glow unchanged).
//
// Paint and transform only: the bubble is laid out in its place from the
// first frame, so nothing around it moves for the lift. Under Animations:
// Calm it fades in at its place. Under reduced motion the bubble is simply
// there.

class _Lift extends StatefulWidget {
  const _Lift({required this.from, required this.child});

  /// Where the words were typed, in global coordinates; null: no lift.
  final Rect? from;
  final Widget child;

  /// How small the bubble starts.
  static const startScale = .92;

  @override
  State<_Lift> createState() => _LiftState();
}

class _LiftState extends State<_Lift> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: KitMotion.standard,
    value: 1,
  );
  late final CurvedAnimation _curve = CurvedAnimation(
    parent: _controller,
    curve: KitMotion.enter,
  );

  /// How far below its place the bubble starts, in dp.
  double _rise = 0;

  /// How small the bubble starts: [_Lift.startScale] under Animations:
  /// Full, its own size under Calm (it only fades in).
  double _from = _Lift.startScale;

  /// Built to lift (decided once): every other bubble is its child alone,
  /// and one that lifted keeps the same widgets when it lands, so it never
  /// builds again for it.
  late final bool _lifts = widget.from != null;

  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_lifts || _started) return;
    _started = true;
    if (KitMotion.reduced(context)) return;
    // Hidden for the frame that lays it out; the lift starts once its place
    // is known.
    _controller.value = 0;
    WidgetsBinding.instance.addPostFrameCallback((_) => _start());
  }

  void _start() {
    if (!mounted) return;
    final from = widget.from;
    final box = context.findRenderObject();
    if (from == null ||
        KitMotion.reduced(context) ||
        box is! RenderBox ||
        !box.hasSize) {
      _controller.value = 1;
      return;
    }
    final place = box.localToGlobal(Offset.zero) & box.size;
    final travels = KitMotion.travels(context);
    // From the line the words were typed on, never from above its place.
    // Calm: it fades in at its place.
    _rise = travels ? math.max(0.0, from.center.dy - place.center.dy) : 0;
    _from = travels ? _Lift.startScale : 1;
    _controller.forward(from: 0);
  }

  @override
  void dispose() {
    _curve.dispose();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_lifts) return widget.child;
    final end = Directionality.of(context) == TextDirection.rtl
        ? Alignment.bottomLeft
        : Alignment.bottomRight;
    return AnimatedBuilder(
      animation: _curve,
      builder: (context, child) {
        final t = _curve.value;
        final scale = _from + (1 - _from) * t;
        return Opacity(
          // In over the first half, so the words are readable as they rise.
          opacity: math.min(1.0, t * 2),
          child: Transform(
            alignment: end,
            transform: Matrix4.translationValues(0, _rise * (1 - t), 0)
              ..multiply(Matrix4.diagonal3Values(scale, scale, 1)),
            child: child,
          ),
        );
      },
      child: widget.child,
    );
  }
}
