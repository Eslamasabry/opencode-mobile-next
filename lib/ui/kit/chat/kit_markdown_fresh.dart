part of 'kit_markdown.dart';

// Words that fade in as they arrive (owner request, 2026-10-27): while a
// reply streams, each newly appended run of the text block being written
// starts transparent and reaches full strength over [KitMotion.standard] on
// [KitMotion.enter]. A block that begins while the reply streams fades in
// whole the same way.
//
// Cost: only the block being written ever animates, and only its colour.
// The span tree is the one the block already parsed; each frame copies it
// with the fresh runs' alpha lowered, cut at the same places on every frame
// between two deltas, so the paragraph compares as a paint-only change (no
// layout of the paragraph or of anything around it). The ticker runs only
// while a run is still fading, never for a finished or reloaded reply, and
// never under reduced motion (the words are simply there).

/// One appended run of a block's drawn text (offsets into its plain text),
/// and when it arrived on the run's clock.
class _KitMdRun {
  _KitMdRun(this.start, this.end, this.born);

  final int start;
  int end;
  final Duration born;
}

/// A block's rich text that fades its newly arrived words in.
class _KitMdFresh extends StatefulWidget {
  const _KitMdFresh({
    required this.span,
    required this.role,
    required this.tone,
    required this.textAlign,
    required this.live,
    required this.fadeIn,
    this.trailing,
  });

  /// The block's text, parsed (links, code, find marks already applied).
  final TextSpan span;

  /// After the text, never faded (the streaming caret).
  final InlineSpan? trailing;

  final KitTextRole role;
  final KitTextTone? tone;
  final TextAlign? textAlign;

  /// The reply is being written: words added from now on fade in.
  final bool live;

  /// The block began while the reply was being written: all of its first
  /// words fade in.
  final bool fadeIn;

  /// How long one run takes to reach full strength.
  static const fade = KitMotion.standard;

  @override
  State<_KitMdFresh> createState() => _KitMdFreshState();
}

class _KitMdFreshState extends State<_KitMdFresh>
    with SingleTickerProviderStateMixin {
  Ticker? _ticker;

  /// The runs still fading, oldest first; they never overlap.
  final List<_KitMdRun> _runs = [];

  /// Monotonic time on this block's clock: the ticker's elapsed time plus
  /// what earlier ticks had reached when it last stopped.
  Duration _clock = Duration.zero;
  Duration _clockAtStart = Duration.zero;

  int _length = 0;
  bool _began = false;

  @override
  void initState() {
    super.initState();
    _length = _plainLength(widget.span);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // The first words of a block begun mid-reply (inherited reads are not
    // allowed in initState: this runs once right after it).
    if (!_began && widget.fadeIn && _length > 0 && _moves(context)) {
      _add(0, _length);
    }
    _began = true;
    if (!_moves(context)) _settle();
  }

  @override
  void didUpdateWidget(_KitMdFresh old) {
    super.didUpdateWidget(old);
    final length = _plainLength(widget.span);
    if (length < _length) {
      // The text changed under the runs (an emphasis closed and took its
      // marks away): clip them, never fade old words again.
      _runs.removeWhere((run) => run.start >= length);
      for (final run in _runs) {
        if (run.end > length) run.end = length;
      }
    } else if (length > _length && widget.live && _moves(context)) {
      // Between two deltas the cuts stay put; here, where the text grows
      // anyway, finished runs are dropped.
      _runs.removeWhere((run) => _alphaOf(run) >= 1);
      _add(_length, length);
    }
    _length = length;
  }

  bool _moves(BuildContext context) => !KitMotion.reduced(context);

  void _add(int start, int end) {
    _runs.add(_KitMdRun(start, end, _clock));
    final ticker = _ticker ??= createTicker(_tick);
    if (!ticker.isActive) {
      _clockAtStart = _clock;
      unawaited(ticker.start());
    }
  }

  void _tick(Duration elapsed) {
    _clock = _clockAtStart + elapsed;
    if (_runs.every((run) => _alphaOf(run) >= 1)) {
      _settle();
    }
    setState(() {});
  }

  /// Every run done: the ticker stops and the text is one span again.
  void _settle() {
    _runs.clear();
    if (_ticker?.isActive ?? false) _ticker!.stop();
  }

  double _alphaOf(_KitMdRun run) {
    final t =
        (_clock - run.born).inMicroseconds / _KitMdFresh.fade.inMicroseconds;
    return t >= 1 ? 1 : KitMotion.enter.transform(t.clamp(0.0, 1.0));
  }

  @override
  void dispose() {
    _ticker?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    var span = widget.span;
    if (_runs.isNotEmpty) {
      final base = KitText.styleOf(context, widget.role, tone: widget.tone);
      span =
          _FreshSpans(_runs, _alphaOf).apply(span, base.color, 0).span
              as TextSpan;
    }
    final trailing = widget.trailing;
    if (trailing != null) span = TextSpan(children: [span, trailing]);
    return KitText.rich(
      span,
      role: widget.role,
      tone: widget.tone,
      textAlign: widget.textAlign,
    );
  }
}

/// The drawn length of [span]: its text, one place per inline widget.
int _plainLength(InlineSpan span) {
  var length = 0;
  span.visitChildren((child) {
    if (child is TextSpan) {
      length += child.text?.length ?? 0;
    } else {
      length += 1;
    }
    return true;
  });
  return length;
}

/// Copies a span tree with the fading runs' words at their current alpha.
class _FreshSpans {
  _FreshSpans(this.runs, this.alphaOf);

  final List<_KitMdRun> runs;
  final double Function(_KitMdRun run) alphaOf;

  ({InlineSpan span, int end}) apply(
    InlineSpan span,
    Color? inherited,
    int at,
  ) {
    if (span is! TextSpan) {
      // An inline widget (a path chip) takes one place and stays as it is.
      return (span: span, end: at + 1);
    }
    final length = _plainLength(span);
    if (!runs.any((run) => run.start < at + length && run.end > at)) {
      // Nothing fading here (all the words before the newest): the very
      // same span, so nothing about it changes.
      return (span: span, end: at + length);
    }
    final style = span.style;
    final color = style?.color ?? inherited;
    final text = span.text;
    final pieces = <InlineSpan>[];
    var end = at;
    if (text != null && text.isNotEmpty) {
      end = at + text.length;
      pieces.addAll(_cut(span, text, at, color));
    }
    final children = span.children;
    if (children != null) {
      for (final child in children) {
        final result = apply(child, color, end);
        pieces.add(result.span);
        end = result.end;
      }
    }
    return (
      span: TextSpan(
        style: style,
        children: pieces,
        semanticsLabel: span.semanticsLabel,
        locale: span.locale,
        spellOut: span.spellOut,
      ),
      end: end,
    );
  }

  /// [text] of [span] (placed at [at]) cut where a fading run starts or
  /// ends, each piece at its run's alpha; the recognizer and the pointer
  /// handlers stay on every piece, so a link keeps working while it fades.
  List<TextSpan> _cut(TextSpan span, String text, int at, Color? color) {
    final end = at + text.length;
    final pieces = <TextSpan>[];
    var from = at;
    for (final run in runs) {
      if (run.end <= from || run.start >= end) continue;
      final start = math.max(run.start, from);
      if (start > from) {
        pieces.add(_piece(span, text.substring(from - at, start - at), 1));
      }
      final stop = math.min(run.end, end);
      pieces.add(
        _piece(
          span,
          text.substring(start - at, stop - at),
          alphaOf(run),
          color: color,
        ),
      );
      from = stop;
    }
    if (from < end || pieces.isEmpty) {
      pieces.add(_piece(span, text.substring(from - at), 1));
    }
    return pieces;
  }

  TextSpan _piece(TextSpan span, String text, double alpha, {Color? color}) {
    var style = span.style;
    // A painted foreground has no colour to fade; it stays as it is. A
    // run's piece keeps its own colour even once it is full, so the cuts
    // compare as a colour change only (paint, not layout) on every frame.
    if (color != null && style?.foreground == null) {
      style = (style ?? const TextStyle()).copyWith(
        color: color.withValues(alpha: color.a * alpha),
      );
    }
    return TextSpan(
      text: text,
      style: style,
      recognizer: span.recognizer,
      mouseCursor: span.mouseCursor,
      onEnter: span.onEnter,
      onExit: span.onExit,
      locale: span.locale,
      spellOut: span.spellOut,
    );
  }
}

// The reply growing smoothly (owner request, 2026-10-27): while it streams,
// a new line does not push the transcript up at once. The reply's box grows
// to its new height on a critically damped spring ([KitMotion.replyGrow],
// about 210 ms per line), from where it is and as fast as it is moving, so
// a line that lands mid-glide carries on without a surge. Its words are
// laid out at full size from the first frame and clipped at the box's
// bottom edge until it gets there, so everything above the reply glides up
// and the live line under it stays where it is. A reply that shrinks (a
// rewritten word that unwraps a line) takes its new height at once.
//
// Cost: one proxy box. While it grows, only the box's own size changes on
// each frame; the reply is not laid out again (same constraints), and the
// list moves the rows above as it does for a scroll.

/// [child] growing smoothly to each new, larger height while [enabled].
class _KitMdGrow extends StatefulWidget {
  const _KitMdGrow({
    required this.enabled,
    required this.fromZero,
    required this.child,
  });

  /// Grow smoothly (the reply streams and motion is on); otherwise every
  /// height is taken at once.
  final bool enabled;

  /// The reply began after the transcript was shown: its first lines grow
  /// in from nothing too.
  final bool fromZero;
  final Widget child;

  @override
  State<_KitMdGrow> createState() => _KitMdGrowState();
}

class _KitMdGrowState extends State<_KitMdGrow>
    with SingleTickerProviderStateMixin {
  // Unbounded: it carries the shown height in dp, on a spring.
  late final AnimationController _controller = AnimationController.unbounded(
    vsync: this,
  );

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => _KitMdGrowBox(
    controller: _controller,
    enabled: widget.enabled && !KitMotion.reduced(context),
    fromZero: widget.fromZero,
    child: widget.child,
  );
}

class _KitMdGrowBox extends SingleChildRenderObjectWidget {
  const _KitMdGrowBox({
    required this.controller,
    required this.enabled,
    required this.fromZero,
    required super.child,
  });

  final AnimationController controller;
  final bool enabled;
  final bool fromZero;

  @override
  _RenderKitMdGrow createRenderObject(BuildContext context) =>
      _RenderKitMdGrow(controller: controller, enabled: enabled)
        .._startFromZero = enabled && fromZero;

  @override
  void updateRenderObject(BuildContext context, _RenderKitMdGrow box) {
    box.enabled = enabled;
  }
}

class _RenderKitMdGrow extends RenderProxyBox {
  _RenderKitMdGrow({required this.controller, required bool enabled})
    : _enabled = enabled;

  /// Runs the shown height (unbounded, in dp) on [KitMotion.replyGrow].
  final AnimationController controller;

  bool _enabled;
  bool get enabled => _enabled;
  set enabled(bool value) {
    if (value == _enabled) return;
    _enabled = value;
    if (!value) {
      controller.stop();
      markNeedsLayout();
    }
  }

  /// The first layout grows from zero (set once, at creation).
  bool _startFromZero = false;

  /// The height being grown towards: the words' own.
  double _to = 0;

  /// The height drawn now: the spring's while it runs, else the words'.
  double get _shown =>
      controller.isAnimating ? controller.value.clamp(0.0, _to) : _to;

  /// How far glyphs may hang below their line box (the tails of g, y, p
  /// sit lower than a tight line height): the clip leaves this much room,
  /// so the line being revealed is not cut at its descenders.
  static const double _overhang = 4;

  /// The box is really shorter than its words (by more than the overhang,
  /// which is painted anyway): the bottom is clipped.
  bool get _clipped =>
      hasSize && child != null && size.height + _overhang < _to;

  Rect get _clipRect =>
      Rect.fromLTRB(0, 0, size.width, math.min(size.height + _overhang, _to));

  /// Inside [performLayout]: starting the glide there notifies the
  /// controller's listeners, and the box is being laid out anyway.
  bool _laying = false;

  void _tick() {
    if (!_laying) markNeedsLayout();
  }

  @override
  void attach(PipelineOwner owner) {
    super.attach(owner);
    controller.addListener(_tick);
  }

  @override
  void detach() {
    controller.removeListener(_tick);
    super.detach();
  }

  @override
  void dispose() {
    _clip.layer = null;
    super.dispose();
  }

  @override
  void performLayout() {
    final child = this.child;
    if (child == null) {
      size = constraints.smallest;
      return;
    }
    child.layout(constraints, parentUsesSize: true);
    final height = child.size.height;
    _laying = true;
    if (!hasSize && !_startFromZero) _to = height;
    if (height > _to && _enabled) {
      // Grows: from where the box is now and as fast as it is moving, so
      // a line that lands mid-glide carries on without a surge.
      final from = hasSize ? size.height : 0.0;
      final speed = controller.isAnimating ? controller.velocity : 0.0;
      _to = height;
      controller.animateWith(
        SpringSimulation(
          KitMotion.replyGrow,
          from,
          height,
          speed,
          tolerance: const Tolerance(distance: .5, velocity: 20),
        ),
      );
    } else if (height != _to || !_enabled) {
      _to = height;
      if (controller.isAnimating) controller.stop();
    }
    _laying = false;
    _startFromZero = false;
    size = constraints.constrain(Size(child.size.width, _shown));
  }

  final LayerHandle<ClipRectLayer> _clip = LayerHandle<ClipRectLayer>();

  @override
  void paint(PaintingContext context, Offset offset) {
    if (child == null) return;
    if (!_clipped) {
      _clip.layer = null;
      context.paintChild(child!, offset);
      return;
    }
    _clip.layer = context.pushClipRect(
      needsCompositing,
      offset,
      _clipRect,
      (context, offset) => context.paintChild(child!, offset),
      oldLayer: _clip.layer,
    );
  }

  @override
  Rect? describeApproximatePaintClip(RenderObject child) =>
      _clipped ? _clipRect : null;

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) {
    if (_clipped && !_clipRect.contains(position)) return false;
    return super.hitTestChildren(result, position: position);
  }
}
