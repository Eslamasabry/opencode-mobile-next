// KitStepTimeline: a turn's work as one quiet line that opens in place to a
// timeline (owner request, 2026-10-09: "do something similar to the Claude
// app's grouping sheet, but without bottom sheets"). Closed it is an icon,
// plain words and a chevron, no frame; open, the steps hang off a thin rail
// under it, in the order they happened: a small grey dot for a sentence, a
// small tile with the step's glyph for a tool call, the live mark for the
// step in progress, and a compact preview of the change a file write or edit
// made. docs/ux-system/kit-api/KitStepTimeline.md.
//
// The steps are the host's widgets ([KitToolRow], [KitMessage.thought], any
// other). Each draws its own node on the rail through
// [KitStepTimeline.node], which does nothing outside a timeline, so the same
// rows keep their plain look anywhere else. A step that draws no node (plain
// text) still sits beside the rail; only its node is missing.
//
// No glass, no shadow. Opening and shutting fold the steps in and out
// (size and fade over [KitMotion.standard]); a step that
// arrives while the timeline is open slides and fades in under the ones
// before it (owner request, 2026-10-27). The rail mirrors under Arabic.
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../kit_diff_view.dart';
import '../kit_effects.dart';
import '../kit_max_width.dart';
import '../kit_motion.dart';
import '../kit_surface.dart';
import '../kit_surface_look.dart';
import '../kit_tappable.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';
import '../kit_word_text.dart';
import '../motion/kit_motion_parts.dart';
import '../style/kit_signal.dart';

part 'kit_step_light.dart';
part 'kit_step_node.dart';
part 'kit_step_signal.dart';

/// A few lines of what a file write or edit changed, for the timeline's
/// preview card: at most [maxLines] lines with their numbers, and whether
/// more follow (the card then fades its last line). Built lazily by the host
/// from the call's own text; the kit never sees lib/api types.
@immutable
class KitStepPreview {
  const KitStepPreview(this.lines, {this.more = false});

  /// A unified patch: its first change, with one line of context before it
  /// (hunk headers and the file preamble are skipped).
  factory KitStepPreview.fromPatch(String patch, {int maxLines = maxShown}) {
    final lines = <KitDiffLine>[];
    KitDiffLine? lead;
    var changed = false;
    var inHunk = false;
    var oldNo = 0;
    var newNo = 0;
    var more = false;
    var at = 0;
    while (at <= patch.length) {
      var end = patch.indexOf('\n', at);
      if (end < 0) end = patch.length;
      final line = patch.substring(at, end);
      at = end + 1;
      if (line.startsWith('@@')) {
        final match = _hunkHeader.firstMatch(line);
        if (match != null) {
          oldNo = int.parse(match.group(1)!);
          newNo = int.parse(match.group(2)!);
        }
        inHunk = true;
        continue;
      }
      if (!inHunk || line.startsWith('\\')) continue;
      KitDiffLine next;
      if (line.startsWith('+')) {
        next = KitDiffLine(
          line.substring(1),
          KitDiffLineKind.added,
          newNo: newNo++,
        );
        changed = true;
      } else if (line.startsWith('-')) {
        next = KitDiffLine(
          line.substring(1),
          KitDiffLineKind.removed,
          oldNo: oldNo++,
        );
        changed = true;
      } else {
        if (line.isEmpty) continue;
        next = KitDiffLine(
          line.startsWith(' ') ? line.substring(1) : line,
          KitDiffLineKind.context,
          oldNo: oldNo++,
          newNo: newNo++,
        );
        if (!changed) {
          lead = next;
          continue;
        }
      }
      if (lead != null && lines.isEmpty) lines.add(lead);
      if (lines.length >= maxLines) {
        more = true;
        break;
      }
      lines.add(next);
    }
    return KitStepPreview(lines, more: more);
  }

  /// A whole new file: every line added, numbered from 1.
  factory KitStepPreview.fromText(String text, {int maxLines = maxShown}) {
    final lines = <KitDiffLine>[];
    var more = false;
    var at = 0;
    var number = 1;
    while (at < text.length) {
      var end = text.indexOf('\n', at);
      if (end < 0) end = text.length;
      if (lines.length >= maxLines) {
        more = true;
        break;
      }
      lines.add(
        KitDiffLine(
          text.substring(at, end),
          KitDiffLineKind.added,
          newNo: number++,
        ),
      );
      at = end + 1;
    }
    return KitStepPreview(lines, more: more);
  }

  /// A before and after pair with no file around it (an edit that names the
  /// old and the new text): the changed lines with a sign and no number,
  /// since the call does not say where in the file they sit.
  factory KitStepPreview.fromTexts({
    String? before,
    String? after,
    int maxLines = maxShown,
  }) {
    final file = KitDiffFile.fromTexts('', before: before, after: after);
    final all = <KitDiffLine>[
      for (final segment in file.segments)
        if (segment is KitDiffLine && segment.kind != KitDiffLineKind.hunk)
          KitDiffLine(segment.text, segment.kind),
    ];
    final firstChange = all.indexWhere(
      (line) => line.kind != KitDiffLineKind.context,
    );
    final from = firstChange > 0 ? firstChange - 1 : 0;
    final rest = all.sublist(from);
    return KitStepPreview(
      rest.take(maxLines).toList(),
      more: rest.length > maxLines,
    );
  }

  /// The most lines a card shows.
  static const int maxShown = 6;

  final List<KitDiffLine> lines;

  /// More lines follow the last one shown.
  final bool more;

  bool get isEmpty => lines.isEmpty;

  static final _hunkHeader = RegExp(r'^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@');
}

/// How a step's node looks (KitStepTimeline.md "Node states").
enum KitStepNodeState {
  /// A plain node: the glyph in its tile.
  none,

  /// Not started: a dim neutral outline, no light.
  queued,

  /// In progress: the glyph in accent and one light round the node and down
  /// the rail.
  running,

  /// A long-running background process: the same light, calmer.
  background,

  /// Waiting on the person: the outline holds filled in the attention role
  /// and breathes; nothing travels.
  waiting,

  /// Finished: a tick flash when it was in progress, then the glyph.
  done,

  /// Failed: the outline flashes once (neutral, never red), then [mark].
  failed,

  /// Stopped by the person: the light fades out, the glyph stays, no tick.
  stopped,
}

/// A turn's work as one quiet line that opens in place to a timeline.
///
/// The line is [icon], [label] and a chevron (or the live [mark] in the
/// icon's place while the work runs); tapping it calls [onPressed]. Open
/// ([expanded]), [steps] hang off the rail under it, [head] first (the
/// "Show earlier steps" row). The host decides everything else: the words,
/// the glyph, the state, which steps to build, the fade ([fade]).
///
/// States: none — it lays out the line and the steps it is given.
class KitStepTimeline extends StatefulWidget {
  const KitStepTimeline({
    super.key,
    required this.label,
    required this.icon,
    required this.expanded,
    required this.onPressed,
    required this.steps,
    this.spoken,
    this.mark,
    this.live = false,
    this.head,
    this.fade,
    this.lineKey,
    this.stepsKey,
    this.stalled = false,
  });

  /// The line's words ("Read 3 files · ran 8 commands").
  final String label;

  /// The group's glyph, in `text2`.
  final IconData icon;

  /// Whether the steps are shown.
  final bool expanded;

  /// The line was tapped (open or close).
  final VoidCallback onPressed;

  /// The steps, oldest first: [KitToolRow], [KitMessage.thought], any widget.
  final List<Widget> steps;

  /// What a screen reader says for the line; null reads [label].
  final String? spoken;

  /// In the glyph's place: the live mark while the work runs, or the failed
  /// mark when it ended on a failure. It is decoration here; its word is in
  /// [spoken].
  final Widget? mark;

  /// A step is running and the line is shut: the line's [icon] carries the
  /// step light instead of a ring.
  final bool live;

  /// A row above the first step, outside the steps ("Show 12 earlier steps").
  final Widget? head;

  /// Paints the opened steps in (paint only; no size change).
  final Animation<double>? fade;

  /// On the line's tap target (today's `Key('work-group-header')`).
  final Key? lineKey;

  /// On the opened steps (today's `Key('work-group-steps')`).
  final Key? stepsKey;

  /// The connection is down while steps run: their lights hold where they
  /// are, dimmed, and go on when it is back.
  final bool stalled;

  /// Whether [context] sits inside a timeline's rail.
  static bool inside(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_RailScope>() != null;

  /// [child] with its node on the rail: a small tile with [icon], the state
  /// [mark] centred in the node's place (the running step's spinner, a failed
  /// mark), or (neither) a quiet dot. Outside a timeline it is [child]
  /// unchanged.
  ///
  /// [lineHeight] is the height of the first line of [child] at 1.0 text
  /// scale and [top] is where that line starts inside [child]; the node
  /// centres on it. [state] says how the step stands (see [KitStepNodeState]); a
  /// running step keeps its glyph and gets the light. [boxHeight], when given, is the first line's height as it
  /// is (already at the person's text size) and replaces [lineHeight].
  static Widget node(
    BuildContext context, {
    required Widget child,
    double lineHeight = 32,
    double top = 0,
    IconData? icon,
    Widget? mark,
    KitStepNodeState state = KitStepNodeState.none,
    double? boxHeight,
  }) {
    if (!inside(context)) return child;
    return _StepNode(
      lineHeight: lineHeight,
      boxHeight: boxHeight,
      top: top,
      icon: icon,
      mark: mark,
      state: state,
      scope: context.dependOnInheritedWidgetOfExactType<_RailScope>()!,
      child: child,
    );
  }

  /// A step's node outside a timeline (a row that stands alone): the glyph in
  /// its tile with the same light round it, and no rail.
  static Widget alone(
    BuildContext context, {
    required IconData icon,
    required KitStepNodeState state,
  }) {
    final rail = _Rail(context);
    return _StepLight(
      icon: icon,
      size: rail.glyph,
      state: state,
      mark: null,
      registry: _RailRegistry(),
      index: 0,
      last: true,
      stalled: false,
      lone: true,
    );
  }

  /// [child] (a running step's name) with a soft light sweeping over it while
  /// [active]; still when the light does not move.
  static Widget sweep(
    BuildContext context, {
    required bool active,
    required Widget child,
  }) {
    final scope = context.dependOnInheritedWidgetOfExactType<_RailScope>();
    return _StepSweep(
      active: active && !(scope?.stalled ?? false),
      child: child,
    );
  }

  /// The height of a step's one line: 32 dp, or the first text line and its
  /// padding when the person's text is larger. Its node centres in it.
  static double stepLineHeight(BuildContext context) {
    final tokens = KitTokens.of(context);
    final scaler = MediaQuery.textScalerOf(context);
    return math.max(32.0, scaler.scale(20) + 2 * tokens.space1);
  }

  /// A sentence between steps (prose the agent said while it worked) with a
  /// dot on the rail. Outside a timeline it is [child] unchanged.
  static Widget sentence(BuildContext context, {required Widget child}) {
    if (!inside(context)) return child;
    final tokens = KitTokens.of(context);
    return _StepNode(
      scope: context.dependOnInheritedWidgetOfExactType<_RailScope>()!,
      lineHeight: 24,
      top: tokens.space1,
      child: Padding(
        padding: EdgeInsetsDirectional.symmetric(vertical: tokens.space1),
        child: child,
      ),
    );
  }

  /// The compact preview card of a file write or edit: [preview]'s lines in
  /// mono with their numbers, fading when more follow; a tap calls [onOpen]
  /// (the existing full view). [label] names the file for a screen reader.
  static Widget previewCard(
    BuildContext context, {
    required KitStepPreview preview,
    required VoidCallback onOpen,
    required String label,
    Key? key,
  }) => _PreviewCard(key: key, preview: preview, onOpen: onOpen, label: label);

  /// The glyph to lead a work line with, by what the work mostly did.
  /// [read], [searched]…: the host's counts.
  static IconData iconFor({
    int read = 0,
    int searched = 0,
    int listed = 0,
    int edited = 0,
    int ran = 0,
    int fetched = 0,
    int delegated = 0,
    int other = 0,
  }) {
    final counts = <(int, IconData)>[
      (edited, AppIconography.editNote),
      (ran, AppIconography.terminal),
      (fetched, AppIconography.globe),
      (delegated, AppIconography.agent),
      (searched, AppIconography.search),
      (read, AppIconography.fileText),
      (listed, AppIconography.folderOpen),
      (other, AppIconography.tools),
    ];
    var best = counts.first;
    for (final entry in counts) {
      if (entry.$1 > best.$1) best = entry;
    }
    return best.$1 == 0 ? AppIconography.tools : best.$2;
  }

  @override
  State<KitStepTimeline> createState() => _KitStepTimelineState();
}

class _KitStepTimelineState extends State<KitStepTimeline> {
  final _RailRegistry _registry = _RailRegistry();

  /// The steps the open timeline has shown, by their keys: one not among
  /// them arrives with [_StepArrival]. Opening shows the steps it finds
  /// without an arrival each (the fold already brings them in).
  Set<Object> _seen = const {};
  bool _wasOpen = false;

  static Object _identity(Widget step, int index) => step.key ?? index;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final hasSteps = widget.steps.isNotEmpty || widget.head != null;
    final registry = _registry
      ..nodes.clear()
      ..live = widget.live;
    final line = _StepLine(
      registry: registry,
      label: widget.label,
      icon: widget.icon,
      mark: widget.mark,
      live: widget.live,
      expanded: widget.expanded,
      railBelow: widget.expanded && hasSteps,
      onPressed: widget.onPressed,
      spoken: widget.spoken ?? widget.label,
      lineKey: widget.lineKey,
    );
    final open = widget.expanded && hasSteps;
    final count = widget.steps.length + (widget.head == null ? 0 : 1);
    registry.count = open ? count : 0;
    // A step the open timeline had not shown yet arrives; one shown when it
    // opened (or before) is already there.
    final ids = [
      for (var i = 0; i < widget.steps.length; i++)
        _identity(widget.steps[i], i),
    ];
    final arrivals = [
      for (final id in ids) open && _wasOpen && !_seen.contains(id) ? id : null,
    ];
    _seen = ids.toSet();
    _wasOpen = open;
    // One shape open or shut: the line never remounts, so keyboard focus
    // stays on it when it toggles.
    Widget? rail = open
        ? Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (widget.head != null)
                _RailSlot(
                  key: registry.keyFor(0),
                  index: 0,
                  registry: registry,
                  stalled: widget.stalled,
                  last: count == 1,
                  child: widget.head!,
                ),
              for (var i = 0; i < widget.steps.length; i++)
                _StepArrival(
                  arrival: arrivals[i],
                  child: _RailSlot(
                    key: registry.keyFor(i + (widget.head == null ? 0 : 1)),
                    index: i + (widget.head == null ? 0 : 1),
                    registry: registry,
                    stalled: widget.stalled,
                    last: i == widget.steps.length - 1,
                    child: widget.steps[i],
                  ),
                ),
            ],
          )
        : null;
    if (rail != null && widget.fade != null) {
      rail = FadeTransition(opacity: widget.fade!, child: rail);
    }
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        line,
        // Opening unfolds the steps and fades them in; shutting folds them
        // away (the steps as they were, while they leave). Under reduced
        // motion both are instant. Without a host fade the fold fades.
        _RailFold(
          fade: widget.fade == null,
          child: rail == null
              ? null
              : KeyedSubtree(key: widget.stepsKey, child: rail),
        ),
      ],
    );
  }
}

/// A step that arrived while its timeline was open: it unfolds into place,
/// fading in and, under Animations: Full, rising a few dp, over
/// [KitMotion.standard]. Every other step (and every step under reduced
/// motion) is simply there. The same widgets moving or still, so a step
/// that settles keeps its state.
class _StepArrival extends StatefulWidget {
  const _StepArrival({required this.arrival, required this.child});

  /// Non-null for the one build in which the step arrives (its identity);
  /// a new value plays the arrival again.
  final Object? arrival;
  final Widget child;

  /// How far an arriving step rises into place, in dp.
  static const rise = 6.0;

  @override
  State<_StepArrival> createState() => _StepArrivalState();
}

class _StepArrivalState extends State<_StepArrival>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: KitMotion.standard,
    value: 1,
  );
  late final CurvedAnimation _curve = CurvedAnimation(
    parent: _controller,
    curve: KitMotion.enter,
  );

  @override
  void initState() {
    super.initState();
    if (widget.arrival != null) _controller.value = 0;
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (KitMotion.reduced(context)) {
      _controller.value = 1;
    } else if (_controller.value == 0 && !_controller.isAnimating) {
      _controller.forward();
    }
  }

  @override
  void didUpdateWidget(_StepArrival old) {
    super.didUpdateWidget(old);
    final arrival = widget.arrival;
    if (arrival != null &&
        arrival != old.arrival &&
        !KitMotion.reduced(context)) {
      _controller.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _curve.dispose();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Calm: it unfolds and fades without rising (KitMotion.travels).
    final rise = KitMotion.travels(context) ? _StepArrival.rise : 0.0;
    return AnimatedBuilder(
      animation: _curve,
      builder: (context, child) => _folded(
        _curve.value,
        Opacity(
          opacity: _curve.value,
          child: Transform.translate(
            offset: Offset(0, rise * (1 - _curve.value)),
            child: child,
          ),
        ),
      ),
      child: widget.child,
    );
  }
}

/// [child] at [factor] of its height from the top, clipped only while it
/// is cut short: a settled step's light and glow may reach past its box.
/// The same widgets at every factor, so nothing remounts as it settles.
Widget _folded(double factor, Widget child) => ClipRect(
  clipBehavior: factor < 1 ? Clip.hardEdge : Clip.none,
  child: Align(
    alignment: AlignmentDirectional.topStart,
    heightFactor: factor.clamp(0.0, 1.0),
    child: child,
  ),
);

/// The opened steps folding in and out (owner request, 2026-10-27): like
/// [KitReveal] (size and fade, in over [KitMotion.standard] and out over
/// [KitMotion.quick]; the first build shows what is there; reduced motion
/// is instant; a leaving rail ignores touches and screen readers), but
/// clipped only while it moves, so the nodes' lights are never cut at the
/// rail's edge once it has settled.
class _RailFold extends StatefulWidget {
  const _RailFold({required this.child, required this.fade});

  final Widget? child;
  final bool fade;

  @override
  State<_RailFold> createState() => _RailFoldState();
}

class _RailFoldState extends State<_RailFold>
    with SingleTickerProviderStateMixin {
  // Made at once (not on first use): it starts where the first build is.
  late final AnimationController _controller;
  late final CurvedAnimation _curve;
  late Widget? _shown = widget.child;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: KitMotion.standard,
      reverseDuration: KitMotion.quick,
      value: widget.child == null ? 0 : 1,
    )..addStatusListener(_onStatus);
    _curve = CurvedAnimation(
      parent: _controller,
      curve: KitMotion.enter,
      reverseCurve: KitMotion.exit,
    );
  }

  void _onStatus(AnimationStatus status) {
    if (status == AnimationStatus.dismissed && widget.child == null) {
      setState(() => _shown = null);
    }
  }

  @override
  void didUpdateWidget(_RailFold old) {
    super.didUpdateWidget(old);
    final reduced = KitMotion.reduced(context);
    if (widget.child != null) {
      _shown = widget.child;
      if (reduced) {
        _controller.value = 1;
      } else if (old.child == null ||
          _controller.status == AnimationStatus.reverse) {
        _controller.forward();
      }
    } else if (old.child != null) {
      if (reduced) {
        _controller.value = 0;
        _shown = null;
      } else {
        _controller.reverse();
      }
    }
  }

  @override
  void dispose() {
    _curve.dispose();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final shown = _shown;
    if (shown == null) return const SizedBox.shrink();
    final leaving = widget.child == null;
    return IgnorePointer(
      ignoring: leaving,
      child: ExcludeSemantics(
        excluding: leaving,
        child: AnimatedBuilder(
          animation: _curve,
          builder: (context, child) => _folded(
            _curve.value,
            widget.fade ? Opacity(opacity: _curve.value, child: child) : child!,
          ),
          child: shown,
        ),
      ),
    );
  }
}

// ── Geometry ──────────────────────────────────────────────────────────────

/// The rail's measures, all from the kit's tokens and the person's text size:
/// the node column is one glyph wide plus a gap, the rail runs down the
/// middle of the glyph, and the text of the line and of every step starts
/// where the column ends.
class _Rail {
  _Rail(BuildContext context)
    : tokens = KitTokens.of(context),
      scaler = MediaQuery.textScalerOf(context),
      dpr = MediaQuery.devicePixelRatioOf(context) {
    glyph = tokens.iconSize(context, tokens.smallIconSize).roundToDouble();
    signal = KitTrack.on(context) ? kitSignalColors(context) : null;
  }

  /// Signal's palette while the rail is the track; null otherwise.
  late final KitSignalColors? signal;

  final KitTokens tokens;
  final TextScaler scaler;
  final double dpr;
  late final double glyph;

  /// Where the words start.
  double get column => glyph + tokens.space3;

  /// The rail's distance from the start edge.
  double get centre => glyph / 2;

  /// Two physical pixels, pixel-aligned.
  double get stroke => dpr > 0 ? 2 / dpr : 1;

  /// The rail's colour: `text3` at half strength, over the ground or any
  /// surface. The hairline role is too faint for a line the eye follows.
  Color get color => tokens.roles.text3.withValues(alpha: .5);

  /// A first line's height at the person's text size.
  double line(double height) => scaler.scale(height);
}

double _snap(double value, double dpr) =>
    dpr > 0 ? (value * dpr).roundToDouble() / dpr : value;

class _RailPainter extends CustomPainter {
  _RailPainter({
    required this.color,
    required this.stroke,
    required this.centre,
    required this.dpr,
    required this.rtl,
    this.top = 0,
    this.bottomOf,
    this.signal,
    this.liveOf,
  });

  /// Signal's palette: the rail is the track (solid while [liveOf] says
  /// a step runs, else the dashed siding).
  final KitSignalColors? signal;
  final bool Function()? liveOf;

  final Color color;
  final double stroke;
  final double centre;
  final double dpr;
  final bool rtl;

  /// Where the stroke starts; the slot's top by default.
  final double top;

  /// Where it ends, read as it paints (the node's centre may be written
  /// after this widget builds); the bottom by default.
  final double Function()? bottomOf;

  @override
  void paint(Canvas canvas, Size size) {
    final end = math.min(bottomOf?.call() ?? size.height, size.height);
    if (end <= top) return;
    final x = rtl ? size.width - centre : centre;
    if (signal case final palette?) {
      kitPaintTrack(
        canvas,
        colors: palette,
        x: _snap(x, dpr),
        top: top,
        bottom: end,
        live: liveOf?.call() ?? true,
      );
      return;
    }
    final left = _snap(x - stroke / 2, dpr);
    canvas.drawRect(
      Rect.fromLTRB(left, top, left + stroke, end),
      Paint()..color = color,
    );
  }

  // A rectangle: cheap, and where the last rail ends is read as it paints.
  @override
  bool shouldRepaint(_RailPainter old) => true;
}

/// Marks the subtree as sitting on a rail (what [KitStepTimeline.inside]
/// reads). It carries nothing: the measures come from the tokens.
class _RailScope extends InheritedWidget {
  const _RailScope({
    required this.last,
    required this.index,
    required this.registry,
    required this.stalled,
    required super.child,
  });

  /// The step is the last of its timeline (its rail ends at its node).
  final bool last;

  /// The step's place in the timeline, and the timeline's geometry book.
  final int index;
  final _RailRegistry registry;
  final bool stalled;

  @override
  bool updateShouldNotify(_RailScope old) =>
      old.last != last ||
      old.index != index ||
      old.registry != registry ||
      old.stalled != stalled;
}

/// Where each step's node centre sits in its slot, written as the nodes
/// build, so the last slot's rail can end on its node.
class _RailRegistry {
  final Map<int, double> centres = {};
  final List<GlobalKey> _slotKeys = [];

  /// Each node's top and bottom in its slot, for the lights.
  final Map<int, ({double top, double bottom})> nodes = {};
  final GlobalKey headerKey = GlobalKey();

  /// Where the header's rail stub starts, in the header's own box.
  double headerStart = 0;
  int count = 0;

  /// A step on this timeline runs or waits (written as the nodes build):
  /// under Signal its track is solid, else the dashed siding.
  bool live = false;

  GlobalKey keyFor(int index) {
    while (_slotKeys.length <= index) {
      _slotKeys.add(GlobalKey());
    }
    return _slotKeys[index];
  }

  RenderBox? box(GlobalKey key) {
    final object = key.currentContext?.findRenderObject();
    return object is RenderBox && object.attached && object.hasSize
        ? object
        : null;
  }
}

/// One step's place on the rail: the rail's stroke behind it and the node
/// column kept free at the start. Every slot but the last runs to its bottom
/// so the strokes join; the last ends at its node's centre.
class _RailSlot extends StatelessWidget {
  const _RailSlot({
    super.key,
    required this.index,
    required this.registry,
    required this.stalled,
    required this.last,
    required this.child,
  });

  final int index;
  final _RailRegistry registry;
  final bool stalled;
  final bool last;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final rail = _Rail(context);
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final fallback = KitStepTimeline.stepLineHeight(context) / 2;
    return _RailScope(
      last: last,
      index: index,
      registry: registry,
      stalled: stalled,
      child: CustomPaint(
        painter: _RailPainter(
          color: rail.color,
          stroke: rail.stroke,
          centre: rail.centre,
          dpr: rail.dpr,
          rtl: rtl,
          bottomOf: last ? () => registry.centres[index] ?? fallback : null,
          signal: rail.signal,
          liveOf: () => registry.live,
        ),
        child: Padding(
          padding: EdgeInsetsDirectional.only(start: rail.column),
          child: child,
        ),
      ),
    );
  }
}

// ── The line ──────────────────────────────────────────────────────────────

class _StepLine extends StatelessWidget {
  const _StepLine({
    required this.registry,
    required this.label,
    required this.icon,
    required this.mark,
    required this.live,
    required this.expanded,
    required this.railBelow,
    required this.onPressed,
    required this.spoken,
    required this.lineKey,
  });

  final _RailRegistry registry;
  final String label;
  final IconData icon;
  final Widget? mark;
  final bool live;
  final bool expanded;

  /// The rail starts under the glyph (the steps are shown).
  final bool railBelow;
  final VoidCallback onPressed;
  final String spoken;
  final Key? lineKey;

  @override
  Widget build(BuildContext context) {
    final rail = _Rail(context);
    final tokens = rail.tokens;
    final roles = tokens.roles;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final first = rail.line(20);
    final wide = MediaQuery.textScalerOf(context).scale(1) >= 1.3;
    // The glyph sits in the middle of the 48 dp target whatever the words
    // do; the target grows downward when they wrap.
    final pad = math.max(tokens.space1, (tokens.minTarget - first) / 2);

    final signal = rail.signal;
    final Widget lead = SizedBox(
      width: rail.glyph,
      height: first,
      child: Center(
        child: signal != null
            ? OverflowBox(
                maxWidth: double.infinity,
                maxHeight: double.infinity,
                child: _signalLead(rail, first, live: live, mark: mark),
              )
            : mark ??
                  (live
                      ? KitStepTimeline.alone(
                          context,
                          icon: icon,
                          state: KitStepNodeState.running,
                        )
                      : Icon(icon, size: rail.glyph, color: roles.text2)),
      ),
    );

    final Widget content = Padding(
      padding: EdgeInsetsDirectional.symmetric(vertical: pad),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ExcludeSemantics(child: lead),
          // The rail's column: the words start where the steps' do.
          SizedBox(width: rail.column - rail.glyph),
          if (signal != null)
            Expanded(
              child: _signalLineWords(
                context,
                label: label,
                expanded: expanded,
                wide: wide,
              ),
            )
          else ...[
            Flexible(
              // Two lines, then cut at a whole word; from 1.3x text the
              // words wrap in full (A11Y-8).
              child: wide
                  ? KitText(
                      label,
                      role: KitTextRole.secondary,
                      tone: KitTextTone.secondary,
                      overflow: TextOverflow.ellipsis,
                    )
                  : KitWordText(
                      label,
                      role: KitTextRole.secondary,
                      tone: KitTextTone.secondary,
                      maxLines: 2,
                    ),
            ),
            SizedBox(width: tokens.space1),
            ExcludeSemantics(
              child: SizedBox(
                height: first,
                child: Center(
                  child: SizedBox.square(
                    dimension: tokens.smallIconSize,
                    child: KitSpin.chevron(expanded: expanded),
                  ),
                ),
              ),
            ),
          ],
        ],
      ),
    );

    final Widget tappable = Align(
      alignment: AlignmentDirectional.centerStart,
      child: Semantics(
        container: true,
        button: true,
        expanded: expanded,
        label: spoken,
        onTap: onPressed,
        excludeSemantics: true,
        child: KitTappable(
          tappableKey: lineKey,
          onTap: onPressed,
          label: spoken,
          shape: KitShape.tile,
          surface: KitSurfaceLevel.ground,
          child: content,
        ),
      ),
    );

    // Always wrapped, so opening never remounts the line (keyboard focus
    // stays on it); the rail's stub is drawn only while the steps show.
    final startY = pad + first / 2 + rail.glyph / 2 + tokens.space1;
    registry.headerStart = startY;
    return CustomPaint(
      key: registry.headerKey,
      // Signal: the folded line sits between two dashed rules.
      foregroundPainter: signal == null
          ? null
          : _DashedRules(color: roles.hairline, start: rail.column, rtl: rtl),
      painter: railBelow
          ? _RailPainter(
              color: rail.color,
              stroke: rail.stroke,
              centre: rail.centre,
              dpr: rail.dpr,
              rtl: rtl,
              top: startY,
              signal: rail.signal,
              liveOf: () => registry.live,
            )
          : null,
      child: tappable,
    );
  }
}

// ── Nodes ─────────────────────────────────────────────────────────────────

// ── The preview card ──────────────────────────────────────────────────────

class _PreviewCard extends StatelessWidget {
  const _PreviewCard({
    super.key,
    required this.preview,
    required this.onOpen,
    required this.label,
  });

  final KitStepPreview preview;
  final VoidCallback onOpen;
  final String label;

  static String _gutter(KitDiffLine line) => switch (line.kind) {
    KitDiffLineKind.added => '+${line.newNo ?? ''}',
    KitDiffLineKind.removed => '−${line.oldNo ?? ''}',
    _ => '${line.newNo ?? line.oldNo ?? ''}',
  };

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final scale = MediaQuery.textScalerOf(context).scale(1);
    final gutters = [for (final line in preview.lines) _gutter(line)];
    final width = gutters.fold<int>(0, (a, g) => math.max(a, g.length));
    // One mono digit is about 0.62 em of the 13 dp mono role.
    final gutterWidth = (width * 8.1 * scale).ceilToDouble();

    final rows = <Widget>[
      for (var i = 0; i < preview.lines.length; i++)
        _PreviewRow(
          line: preview.lines[i],
          gutter: gutters[i],
          gutterWidth: gutterWidth,
        ),
    ];
    Widget body = KitLtr(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: rows,
      ),
    );
    if (preview.more) {
      // The last lines melt into the card: the preview says there is more
      // without a word (the tap opens it).
      body = ShaderMask(
        blendMode: BlendMode.dstIn,
        shaderCallback: (bounds) => LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          // Only the alpha matters to the mask.
          colors: [roles.text1, roles.text1, roles.text1.withValues(alpha: 0)],
          stops: const [0, .45, 1],
        ).createShader(bounds),
        child: body,
      );
    }
    return Padding(
      padding: EdgeInsetsDirectional.only(
        top: tokens.space1,
        bottom: tokens.space1,
      ),
      child: Semantics(
        container: true,
        button: true,
        label: label,
        hint: l10n.kitToolOpenDetails,
        onTap: onOpen,
        excludeSemantics: true,
        child: KitTappable(
          onTap: onOpen,
          label: label,
          shape: KitShape.code,
          surface: KitSurfaceLevel.surface1,
          child: KitSurface(
            level: KitSurfaceLevel.surface1,
            shape: KitShape.code,
            padding: KitSurfacePadding.none,
            child: Padding(
              padding: EdgeInsetsDirectional.symmetric(vertical: tokens.space2),
              child: body,
            ),
          ),
        ),
      ),
    );
  }
}

class _PreviewRow extends StatelessWidget {
  const _PreviewRow({
    required this.line,
    required this.gutter,
    required this.gutterWidth,
  });

  final KitDiffLine line;
  final String gutter;
  final double gutterWidth;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final (Color? tint, KitTextTone numberTone) = switch (line.kind) {
      KitDiffLineKind.added => (roles.codeAddedSurface, KitTextTone.success),
      KitDiffLineKind.removed => (
        roles.codeRemovedSurface,
        KitTextTone.secondary,
      ),
      _ => (null, KitTextTone.tertiary),
    };
    final row = Padding(
      padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: gutterWidth,
            child: KitText(
              gutter,
              role: KitTextRole.mono,
              tone: numberTone,
              maxLines: 1,
              softWrap: false,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.end,
            ),
          ),
          SizedBox(width: tokens.space2),
          Expanded(
            child: KitText(
              line.text,
              role: KitTextRole.mono,
              tone: KitTextTone.primary,
              maxLines: 1,
              softWrap: false,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
    return tint == null ? row : ColoredBox(color: tint, child: row);
  }
}
