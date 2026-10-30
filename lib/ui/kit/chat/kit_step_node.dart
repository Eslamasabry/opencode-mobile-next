// A step's node on a timeline's rail (docs/ux-system/kit-api/KitStepTimeline.md
// "Nodes"). Part of kit_step_timeline.dart so it reads the rail's measures.
// Every node is the same size (the glyph's 20 dp, grown with text) and
// centred on the one rail; a running step keeps its glyph and one light
// travels (kit_step_light.dart).
part of 'kit_step_timeline.dart';

/// Draws a node in the free column at the start of its slot, beside the
/// first line of [child]. The node sits outside [child]'s own box (it is
/// decoration: no semantics, no taps), so [child] keeps its full width.
class _StepNode extends StatelessWidget {
  const _StepNode({
    required this.child,
    required this.lineHeight,
    required this.top,
    required this.scope,
    this.icon,
    this.mark,
    this.state = KitStepNodeState.none,
    this.boxHeight,
  });

  final Widget child;

  /// The first line's height at 1.0 text scale.
  final double lineHeight;

  /// The first line's height as it is, when the host measured it.
  final double? boxHeight;
  final double top;
  final IconData? icon;
  final Widget? mark;

  /// How the step stands: a running step keeps its glyph and gets the light.
  final KitStepNodeState state;
  final _RailScope scope;

  static const double _dot = 6;

  @override
  Widget build(BuildContext context) {
    final rail = _Rail(context);
    final roles = rail.tokens.roles;
    final box = boxHeight ?? rail.line(lineHeight);
    final Widget node;
    if (icon != null) {
      node = _StepLight(
        icon: icon!,
        size: rail.glyph,
        state: state,
        mark: mark,
        registry: scope.registry,
        index: scope.index,
        last: scope.last,
        stalled: scope.stalled,
      );
    } else if (mark != null) {
      node = mark!;
    } else if (rail.signal != null) {
      // Signal: a sentence between steps is a sleeper across the track.
      node = _signalSleeper(rail);
    } else {
      node = SizedBox.square(
        dimension: _dot,
        child: DecoratedBox(
          decoration: BoxDecoration(color: roles.text3, shape: BoxShape.circle),
        ),
      );
    }
    if (_signalLive(state)) scope.registry.live = true;
    // Where this node's centre is, so the last slot's rail can end on it.
    scope.registry.centres[scope.index] = top + box / 2;
    final nodeTop = top + (box - rail.glyph) / 2;
    scope.registry.nodes[scope.index] = (
      top: nodeTop,
      bottom: nodeTop + (icon != null ? rail.glyph : _dot),
    );
    return Stack(
      clipBehavior: Clip.none,
      fit: StackFit.passthrough,
      children: [
        child,
        PositionedDirectional(
          start: -rail.column,
          top: top,
          width: rail.glyph,
          height: box,
          child: ExcludeSemantics(child: Center(child: node)),
        ),
      ],
    );
  }
}
