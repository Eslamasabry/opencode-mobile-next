import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import 'glass/kit_glass.dart';
import 'kit_bottom_inset.dart';
import 'kit_buttons.dart';
import 'kit_icon.dart';
import 'kit_layout.dart';
import 'kit_motion.dart';
import 'kit_needs_you.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// One destination. The same list builds the dock, the rail and the sidebar
/// (docs/ux-system/kit-api/KitNav.md).
@immutable
class KitNavDestination {
  const KitNavDestination({
    required this.label,
    required this.icon,
    this.selectedIcon,
    this.needsYou = 0,
    this.pane,
    this.key,
  });

  /// "Work", "Inbox", "Project", "Settings": always visible (A11Y-1).
  final String label;

  /// An [AppIconography] glyph.
  final IconData icon;

  /// The fill glyph, dock only (LOOK-33).
  final IconData? selectedIcon;

  /// The [KitNeedsYou.badge] count (Inbox). KitNav never counts (AUTO-11).
  final int needsYou;

  /// Expanded and wider: this destination's list, shown in the sidebar.
  final WidgetBuilder? pane;

  /// The destination's hit area (a test handle, KIT-10).
  final Key? key;
}

enum KitNavLayout {
  /// compact: the floating glass dock at the bottom.
  dock,

  /// medium: a floating glass rail at the start, icons with labels.
  rail,

  /// expanded and large: the 296 dp sidebar at the start (wider with larger text,
  /// KitLayout.sidebarWidth).
  sidebar,
}

/// The shell's navigation frame (kit-v2.md §8.1, §8.2, §9.2; VL §4–§6).
/// [child] is the content (the shell's KitScreen with KitTopBar.shell on
/// compact/medium; the selected destination's detail pane on expanded+).
///
/// States: default (one selected), needs-you (a badge on a destination),
/// dock hidden (keyboard open), glass solid (Effects › Glass off, high
/// contrast, accessible navigation, remove animations: [KitGlass] falls
/// back). No disabled destination: an unavailable one is absent (LAY-15).
class KitNav extends StatelessWidget {
  const KitNav({
    super.key,
    required this.destinations,
    required this.selected,
    required this.onSelected,
    required this.child,
    this.sidebarHeader,
    this.sidebarPrimary,
    this.navKey,
  }) : assert(
         destinations.length >= 2 && destinations.length <= 5,
         'KitNav takes two to five destinations',
       ),
       assert(selected >= 0 && selected < destinations.length);

  /// Two to five, in the caller's order.
  final List<KitNavDestination> destinations;

  /// An index into [destinations].
  final int selected;
  final ValueChanged<int> onSelected;
  final Widget child;

  /// `KitShellControls(layout: sidebar)`; sidebar only.
  final Widget? sidebarHeader;

  /// The sidebar's pinned primary ("New conversation"); sidebar only.
  final KitAction? sidebarPrimary;

  /// A handle on the dock, rail or sidebar widget.
  final Key? navKey;

  /// The layout KitNav uses in this window (from [KitLayout.windowOf], with
  /// the short-window rule: < 480 dp tall keeps dock/rail).
  static KitNavLayout layoutOf(BuildContext context) {
    final window = KitLayout.windowOf(context);
    if (window == KitWindow.compact) return KitNavLayout.dock;
    if (window == KitWindow.medium || KitLayout.isShort(context)) {
      return KitNavLayout.rail;
    }
    return KitNavLayout.sidebar;
  }

  /// True below a KitNav whose sidebar is showing the selected destination's
  /// [KitNavDestination.pane]. KitScreen.twoPane reads it and leaves its own
  /// list out (the list is already in the sidebar).
  static bool hostsPane(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_KitNavScope>()?.hostsPane ??
      false;

  /// True below a KitNav: the page sits under the floating glass
  /// navigation layer, so its ground carries the theme's ambient fields
  /// (visual language §6: the glass has something to bend).
  static bool hosts(BuildContext context) =>
      context.getInheritedWidgetOfExactType<_KitNavScope>() != null;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final media = MediaQuery.of(context);
    final layout = layoutOf(context);
    final Widget frame;
    switch (layout) {
      case KitNavLayout.dock:
        // Hidden while the keyboard is open, at once (the keyboard moves).
        final keyboardOpen = media.viewInsets.bottom > 0;
        final dockWidth = media.size.width - 2 * tokens.gutter;
        final dockHeight = keyboardOpen
            ? 0.0
            : _dockHeight(context, destinations, dockWidth);
        frame = Stack(
          fit: StackFit.expand,
          children: [
            KitBottomInset.add(
              extraBottom: keyboardOpen ? 0 : tokens.space2 + dockHeight,
              // The top controls join while the page is scrolled.
              child: KitGlass.trackScroll(
                resetOn: selected,
                child: _KitNavScope(hostsPane: false, child: child),
              ),
            ),
            if (!keyboardOpen)
              PositionedDirectional(
                start: tokens.gutter,
                end: tokens.gutter,
                bottom: media.padding.bottom + tokens.space2,
                child: KitNavBar(
                  key: navKey,
                  destinations: destinations,
                  selected: selected,
                  onSelected: onSelected,
                ),
              ),
          ],
        );
      case KitNavLayout.rail:
        final railBand = KitLayout.railWidth + tokens.space2;
        frame = Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SizedBox(
              width: railBand,
              child: Padding(
                padding: EdgeInsetsDirectional.only(
                  start: tokens.space2,
                  top: media.padding.top + tokens.space2,
                  bottom: media.padding.bottom + tokens.space2,
                ),
                child: KitNavRail(
                  key: navKey,
                  destinations: destinations,
                  selected: selected,
                  onSelected: onSelected,
                ),
              ),
            ),
            Expanded(
              child: KitBottomInset.add(
                extraBottom: 0,
                start: railBand,
                child: KitGlass.trackScroll(
                  resetOn: selected,
                  child: _KitNavScope(hostsPane: false, child: child),
                ),
              ),
            ),
          ],
        );
      case KitNavLayout.sidebar:
        frame = Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitNavRail(
              key: navKey,
              destinations: destinations,
              selected: selected,
              onSelected: onSelected,
              extended: true,
              header: sidebarHeader,
              primary: sidebarPrimary,
            ),
            Expanded(
              child: KitBottomInset.add(
                extraBottom: 0,
                start: KitLayout.sidebarWidth(context),
                child: _KitNavScope(
                  hostsPane: destinations[selected].pane != null,
                  child: child,
                ),
              ),
            ),
          ],
        );
    }
    // One backdrop read for the dock, the rail and the top controls
    // (LOOK-28).
    return BackdropGroup(child: frame);
  }
}

class _KitNavScope extends InheritedWidget {
  const _KitNavScope({required this.hostsPane, required super.child});

  final bool hostsPane;

  @override
  bool updateShouldNotify(_KitNavScope oldWidget) =>
      hostsPane != oldWidget.hostsPane;
}

/// The floating dock (compact). Public for galleries and tests; the app
/// uses [KitNav].
///
/// States: none — its destinations always open (see [KitNav]).
class KitNavBar extends StatelessWidget {
  const KitNavBar({
    super.key,
    required this.destinations,
    required this.selected,
    required this.onSelected,
  });

  final List<KitNavDestination> destinations;
  final int selected;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth.isFinite
            ? constraints.maxWidth
            : MediaQuery.sizeOf(context).width - 2 * tokens.gutter;
        final metrics = _labelMetrics(
          context,
          destinations,
          width / destinations.length,
        );
        return KitGlass(
          borderRadius: BorderRadius.circular(tokens.navRadius),
          child: SizedBox(
            height: _dockHeightFor(tokens, metrics),
            width: width,
            child: _KitNavItems(
              axis: Axis.horizontal,
              destinations: destinations,
              selected: selected,
              onSelected: onSelected,
              metrics: metrics,
              slotExtent: width / destinations.length,
            ),
          ),
        );
      },
    );
  }
}

/// The rail (medium) or, with [extended], the sidebar column (expanded+).
/// Public for galleries and tests; the app uses [KitNav].
///
/// States: none — its destinations always open (see [KitNav]).
class KitNavRail extends StatelessWidget {
  const KitNavRail({
    super.key,
    required this.destinations,
    required this.selected,
    required this.onSelected,
    this.extended = false,
    this.header,
    this.primary,
  });

  final List<KitNavDestination> destinations;
  final int selected;
  final ValueChanged<int> onSelected;

  /// The sidebar (296 dp, wider with larger text) instead of the glass rail.
  final bool extended;

  /// Sidebar only: the top controls (glass, VL §6).
  final Widget? header;

  /// Sidebar only: the pinned full-width primary.
  final KitAction? primary;

  @override
  Widget build(BuildContext context) =>
      extended ? _buildSidebar(context) : _buildRail(context);

  Widget _buildRail(BuildContext context) {
    final tokens = KitTokens.of(context);
    final metrics = _labelMetrics(context, destinations, KitLayout.railWidth);
    return SizedBox(
      width: KitLayout.railWidth,
      child: KitGlass(
        borderRadius: BorderRadius.circular(tokens.navRadius),
        child: Padding(
          padding: EdgeInsets.symmetric(vertical: tokens.space2),
          child: Align(
            alignment: AlignmentDirectional.topCenter,
            child: _KitNavItems(
              axis: Axis.vertical,
              destinations: destinations,
              selected: selected,
              onSelected: onSelected,
              metrics: metrics,
              slotExtent: _itemExtent(tokens, metrics),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildSidebar(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final media = MediaQuery.of(context);
    final pane = destinations[selected].pane;
    final action = primary;
    return Container(
      // 296 dp, wider with larger text (KitLayout.sidebarWidth).
      width: KitLayout.sidebarWidth(context),
      decoration: BoxDecoration(
        color: roles.ground,
        border: BorderDirectional(
          end: BorderSide(
            color: roles.hairline,
            width: KitTokens.hairlineWidth(context),
          ),
        ),
      ),
      padding: EdgeInsetsDirectional.fromSTEB(
        tokens.space3,
        media.padding.top + tokens.space3,
        tokens.space3,
        media.padding.bottom + tokens.space3,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ?header,
          if (header != null) SizedBox(height: tokens.space3),
          _KitNavItems(
            axis: Axis.vertical,
            sidebar: true,
            destinations: destinations,
            selected: selected,
            onSelected: onSelected,
            metrics: const _LabelMetrics(maxScale: 1, labelHeight: 0),
            slotExtent: tokens.minTarget,
          ),
          if (pane != null) ...[
            SizedBox(height: tokens.sectionGap),
            Expanded(child: Builder(builder: pane)),
          ] else
            const Spacer(),
          if (action != null) ...[
            SizedBox(height: tokens.space3),
            KitButton.fromAction(action, role: KitButtonRole.primary),
          ],
        ],
      ),
    );
  }
}

// ── Metrics ─────────────────────────────────────────────────────────────

/// The lens: a clear pill behind the glyph, the chip's visual height and a
/// target-and-a-half wide.
double _lensHeight() => KitTokens.chipHeight;
double _lensWidth(KitTokens tokens) => tokens.minTarget + tokens.space2;

@immutable
class _LabelMetrics {
  const _LabelMetrics({required this.maxScale, required this.labelHeight});

  /// The label clamp here: up to [KitTokens.navLabelMaxScale], never beyond
  /// what fits one label per destination (A11Y-8).
  final double maxScale;

  /// One label line at the clamped scale, rounded up to a whole dp.
  final double labelHeight;
}

_LabelMetrics _labelMetrics(
  BuildContext context,
  List<KitNavDestination> destinations,
  double slotWidth,
) {
  final tokens = KitTokens.of(context);
  final style = KitText.styleOf(context, KitTextRole.label);
  final direction = Directionality.of(context);
  final room = slotWidth - tokens.space2;
  var maxScale = KitTokens.navLabelMaxScale;
  for (final destination in destinations) {
    final painter = TextPainter(
      text: TextSpan(text: destination.label, style: style),
      textDirection: direction,
      maxLines: 1,
    )..layout();
    if (painter.width > 0) maxScale = math.min(maxScale, room / painter.width);
    painter.dispose();
  }
  maxScale = maxScale.clamp(1.0, KitTokens.navLabelMaxScale);
  final scaler = MediaQuery.textScalerOf(
    context,
  ).clamp(maxScaleFactor: maxScale);
  final line = TextPainter(
    text: TextSpan(text: 'Ag', style: style),
    textDirection: direction,
    textScaler: scaler,
    maxLines: 1,
  )..layout();
  // Large text: the dock grows a step so a label never touches its border
  // (60 dp at 1.0 is unchanged).
  final height =
      line.height.ceilToDouble() + (scaler.scale(1) > 1.2 ? tokens.space2 : 0);
  line.dispose();
  return _LabelMetrics(maxScale: maxScale, labelHeight: height);
}

/// Lens + label with [KitTokens.space1] above and below: 60 dp at 1.0, taller
/// when the labels grow.
double _dockHeightFor(KitTokens tokens, _LabelMetrics metrics) => math.max(
  tokens.navHeight,
  _lensHeight() + metrics.labelHeight + 2 * tokens.space1,
);

double _dockHeight(
  BuildContext context,
  List<KitNavDestination> destinations,
  double width,
) => _dockHeightFor(
  KitTokens.of(context),
  _labelMetrics(context, destinations, width / destinations.length),
);

/// Above the dock's lens: the lens and label centred in the bar.
double _dockTopPad(KitTokens tokens, _LabelMetrics metrics) =>
    ((_dockHeightFor(tokens, metrics) - _lensHeight() - metrics.labelHeight) /
            2)
        .floorToDouble();

/// One rail destination: lens, label and [KitTokens.space1] around both.
double _itemExtent(KitTokens tokens, _LabelMetrics metrics) => math.max(
  tokens.minTarget,
  _lensHeight() + metrics.labelHeight + 3 * tokens.space1,
);

// ── Destinations ────────────────────────────────────────────────────────

/// The destinations as one focus group: Tab enters at the selected one,
/// arrows move (Left/Right in the dock, Up/Down in the rail and sidebar),
/// Enter/Space selects (KitTappable).
class _KitNavItems extends StatefulWidget {
  const _KitNavItems({
    required this.axis,
    required this.destinations,
    required this.selected,
    required this.onSelected,
    required this.metrics,
    required this.slotExtent,
    this.sidebar = false,
  });

  final Axis axis;
  final List<KitNavDestination> destinations;
  final int selected;
  final ValueChanged<int> onSelected;
  final _LabelMetrics metrics;

  /// A dock slot's width, a rail item's height, a sidebar row's minimum.
  final double slotExtent;
  final bool sidebar;

  @override
  State<_KitNavItems> createState() => _KitNavItemsState();
}

class _KitNavItemsState extends State<_KitNavItems>
    with SingleTickerProviderStateMixin {
  final List<FocusNode> _nodes = [];

  // The lens (fluid glass, visual language §6): its two edges along the bar
  // ride separate springs, so it stretches towards a new tab, leading with
  // its front edge, and settles on it; dragged along the bar it lifts and
  // follows the finger. Positions are along the main axis in visual order
  // (left to right, or top to bottom). Only the lens moves: it is laid out
  // alone, the destinations never relayout or rebuild. One ticker steps
  // the springs, and a finger moves their targets without restarting them.
  final _LensMotion _motion = _LensMotion();
  late final Ticker _ticker = createTicker(_tick);
  Duration _lastTick = Duration.zero;

  /// The track the lens was last placed on; null before the first build.
  _LensTrack? _track;

  /// The destination the lens rests on or is heading to.
  int? _heading;
  bool _dragging = false;
  bool _reduced = false;

  void _syncNodes() {
    while (_nodes.length < widget.destinations.length) {
      _nodes.add(FocusNode(debugLabel: 'KitNav ${_nodes.length}'));
    }
    while (_nodes.length > widget.destinations.length) {
      _nodes.removeLast().dispose();
    }
  }

  @override
  void dispose() {
    for (final node in _nodes) {
      node.dispose();
    }
    _ticker.dispose();
    _motion.dispose();
    super.dispose();
  }

  KeyEventResult _onKey(FocusNode _, KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }
    final current = _nodes.indexWhere((node) => node.hasFocus);
    if (current < 0) return KeyEventResult.ignored;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final key = event.logicalKey;
    int? step;
    if (widget.axis == Axis.horizontal) {
      if (key == LogicalKeyboardKey.arrowRight) step = rtl ? -1 : 1;
      if (key == LogicalKeyboardKey.arrowLeft) step = rtl ? 1 : -1;
    } else {
      if (key == LogicalKeyboardKey.arrowDown) step = 1;
      if (key == LogicalKeyboardKey.arrowUp) step = -1;
    }
    if (step == null) return KeyEventResult.ignored;
    final next = (current + step).clamp(0, _nodes.length - 1);
    _nodes[next].requestFocus();
    return KeyEventResult.handled;
  }

  // ── The lens ──

  void _tick(Duration elapsed) {
    final dt =
        (elapsed - _lastTick).inMicroseconds / Duration.microsecondsPerSecond;
    _lastTick = elapsed;
    _motion.step(dt);
    if (!_motion.moving) _ticker.stop();
  }

  void _run() {
    if (_ticker.isActive) return;
    _lastTick = Duration.zero;
    _ticker.start();
  }

  /// Both edges to [start]–[end]: the edge in front leads on the stiffer
  /// spring, the one behind follows, so the lens stretches on the way.
  void _springEdges(
    double start,
    double end, {
    required SpringDescription lead,
    required SpringDescription trail,
  }) {
    final forward = start + end > _motion.start.x + _motion.end.x;
    _motion.start.aim(start, forward ? trail : lead);
    _motion.end.aim(end, forward ? lead : trail);
    _run();
  }

  void _lift(double to) {
    _motion.lift.aim(to, KitMotion.lensLift);
    _run();
  }

  void _snap(double start, double end) {
    _ticker.stop();
    _motion.snap(start, end);
  }

  /// Puts the lens on the selected destination: at once the first time,
  /// when the track changed (a new window size or text size) or under
  /// reduced motion; on springs when the selection moved.
  void _place(_LensTrack track) {
    final (start, end) = track.rest(widget.selected);
    final moved = _track != track;
    _track = track;
    if (_dragging) return;
    if (moved || _reduced) {
      _heading = widget.selected;
      _snap(start, end);
      return;
    }
    if (_heading == widget.selected) return;
    _heading = widget.selected;
    _springEdges(
      start,
      end,
      lead: KitMotion.lensLead,
      trail: KitMotion.lensTrail,
    );
  }

  double _along(Offset local) =>
      widget.axis == Axis.horizontal ? local.dx : local.dy;

  void _dragStart(DragStartDetails details) {
    _dragging = true;
    if (!_reduced) _lift(1);
    _dragTo(_along(details.localPosition));
  }

  void _dragUpdate(DragUpdateDetails details) =>
      _dragTo(_along(details.localPosition));

  /// The lens follows the finger, a little wider while it floats, and
  /// resists past the first and last destinations.
  void _dragTo(double position) {
    final track = _track;
    if (track == null) return;
    final first = track.centre(0);
    final last = track.centre(track.count - 1);
    var at = position;
    if (at < first) at = first - (first - at) * _LensTrack.overscroll;
    if (at > last) at = last + (at - last) * _LensTrack.overscroll;
    final half = track.length * (_reduced ? 1 : _LensTrack.floating) / 2;
    if (_reduced) {
      _motion.snap(at - half, at + half);
      return;
    }
    _springEdges(
      at - half,
      at + half,
      lead: KitMotion.lensDragLead,
      trail: KitMotion.lensDragTrail,
    );
  }

  /// Let go: the lens settles on the destination under it, which opens.
  void _dragEnd() {
    final track = _track;
    if (!_dragging || track == null) return;
    _dragging = false;
    // The destination under the finger (the springs' target), not under
    // the lens, which may still be catching up.
    final index = track.indexAt(
      (_motion.start.target + _motion.end.target) / 2,
    );
    _heading = index;
    final (start, end) = track.rest(index);
    if (_reduced) {
      _snap(start, end);
    } else {
      _lift(0);
      _springEdges(
        start,
        end,
        lead: KitMotion.lensLead,
        trail: KitMotion.lensTrail,
      );
    }
    if (index != widget.selected) widget.onSelected(index);
  }

  @override
  Widget build(BuildContext context) {
    _syncNodes();
    final tokens = KitTokens.of(context);
    _reduced = KitMotion.reduced(context);
    final metrics = widget.metrics;
    final items = <Widget>[
      for (var i = 0; i < widget.destinations.length; i++)
        // Roving tab stop: only the selected destination is a Tab stop; the
        // arrows reach the others.
        ExcludeFocusTraversal(
          excluding: i != widget.selected,
          child: _KitNavItem(
            destination: widget.destinations[i],
            selected: i == widget.selected,
            onTap: () => widget.onSelected(i),
            focusNode: _nodes[i],
            axis: widget.axis,
            sidebar: widget.sidebar,
            metrics: metrics,
            extent: widget.slotExtent,
            topPad: widget.axis == Axis.horizontal
                ? _dockTopPad(tokens, metrics)
                : tokens.space1,
          ),
        ),
    ];

    Widget body;
    if (widget.sidebar) {
      body = Column(mainAxisSize: MainAxisSize.min, children: items);
    } else {
      final lensWidth = _lensWidth(tokens);
      final lensHeight = _lensHeight();
      final horizontal = widget.axis == Axis.horizontal;
      final track = horizontal
          ? _LensTrack(
              axis: Axis.horizontal,
              count: widget.destinations.length,
              slot: widget.slotExtent,
              length: lensWidth,
              lead: (widget.slotExtent - lensWidth) / 2,
              crossStart: _dockTopPad(tokens, metrics),
              crossLength: lensHeight,
              rtl: Directionality.of(context) == TextDirection.rtl,
            )
          : _LensTrack(
              axis: Axis.vertical,
              count: widget.destinations.length,
              slot: widget.slotExtent,
              length: lensHeight,
              lead: tokens.space1,
              crossStart: (KitLayout.railWidth - lensWidth) / 2,
              crossLength: lensWidth,
              rtl: false,
            );
      _place(track);
      final stack = Stack(
        children: [
          Positioned.fill(
            child: CustomSingleChildLayout(
              delegate: _LensLayout(
                motion: _motion,
                track: track,
                grow: tokens.space2,
                devicePixelRatio: MediaQuery.devicePixelRatioOf(context),
              ),
              child: _KitNavLens(motion: _motion),
            ),
          ),
          if (horizontal)
            Row(children: items)
          else
            Column(mainAxisSize: MainAxisSize.min, children: items),
        ],
      );
      // Drag along the bar to move the lens; a tap still opens at once.
      // Screen readers keep the destinations' own actions.
      body = GestureDetector(
        excludeFromSemantics: true,
        onHorizontalDragStart: horizontal ? _dragStart : null,
        onHorizontalDragUpdate: horizontal ? _dragUpdate : null,
        onHorizontalDragEnd: horizontal ? (_) => _dragEnd() : null,
        onHorizontalDragCancel: horizontal ? _dragEnd : null,
        onVerticalDragStart: horizontal ? null : _dragStart,
        onVerticalDragUpdate: horizontal ? null : _dragUpdate,
        onVerticalDragEnd: horizontal ? null : (_) => _dragEnd(),
        onVerticalDragCancel: horizontal ? null : _dragEnd,
        child: stack,
      );
    }
    return FocusTraversalGroup(
      child: Focus(
        canRequestFocus: false,
        skipTraversal: true,
        onKeyEvent: _onKey,
        child: body,
      ),
    );
  }
}

/// Where the lens can rest along the dock or rail: one slot per
/// destination, in visual order.
@immutable
class _LensTrack {
  const _LensTrack({
    required this.axis,
    required this.count,
    required this.slot,
    required this.length,
    required this.lead,
    required this.crossStart,
    required this.crossLength,
    required this.rtl,
  });

  /// How far a drag past the first or last destination moves the lens.
  static const double overscroll = .25;

  /// The lens's length while it floats under a finger, as a share of rest.
  static const double floating = 1.08;

  final Axis axis;
  final int count;

  /// Along the main axis: a destination's extent, the lens's length and
  /// the space before the lens in its slot.
  final double slot;
  final double length;
  final double lead;

  /// Across: where the lens starts and how thick it is at rest.
  final double crossStart;
  final double crossLength;

  /// A right-to-left dock: the first destination is the rightmost slot.
  final bool rtl;

  int _visual(int index) => rtl ? count - 1 - index : index;

  /// The lens's edges on destination [index].
  (double, double) rest(int index) {
    final start = _visual(index) * slot + lead;
    return (start, start + length);
  }

  /// The middle of visual slot [slotIndex].
  double centre(int slotIndex) => slotIndex * slot + lead + length / 2;

  /// The destination whose slot holds [position].
  int indexAt(double position) => _visual(
    ((position - lead - length / 2) / slot).round().clamp(0, count - 1),
  );

  @override
  bool operator ==(Object other) =>
      other is _LensTrack &&
      other.axis == axis &&
      other.count == count &&
      other.slot == slot &&
      other.length == length &&
      other.lead == lead &&
      other.crossStart == crossStart &&
      other.crossLength == crossLength &&
      other.rtl == rtl;

  @override
  int get hashCode => Object.hash(
    axis,
    count,
    slot,
    length,
    lead,
    crossStart,
    crossLength,
    rtl,
  );
}

/// One value on a spring whose target may move every frame without
/// restarting it (a finger dragging the lens).
class _Spring {
  double x = 0;
  double v = 0;
  double target = 0;
  SpringDescription spring = KitMotion.lensLead;

  /// Close enough to rest to stop: a hundredth of a dp.
  static const double _rest = .01;

  bool get moving => (x - target).abs() > _rest || v.abs() > _rest;

  void aim(double to, SpringDescription description) {
    target = to;
    spring = description;
  }

  void snap(double value) {
    x = target = value;
    v = 0;
  }

  void step(double dt) {
    final a =
        (spring.stiffness * (target - x) - spring.damping * v) / spring.mass;
    v += a * dt;
    x += v * dt;
  }
}

/// The lens's two edges and its lift, stepped together once per frame.
class _LensMotion extends ChangeNotifier {
  final _Spring start = _Spring();
  final _Spring end = _Spring();
  final _Spring lift = _Spring();

  List<_Spring> get _all => [start, end, lift];

  bool get moving => _all.any((spring) => spring.moving);

  /// Longest step of the integration, seconds: small enough that the
  /// stiffest spring stays stable at any frame rate.
  static const double _substep = .004;

  /// A frame longer than this (the app paused) moves no further.
  static const double _longestFrame = 1 / 30;

  void step(double dt) {
    final frame = dt.clamp(0.0, _longestFrame);
    final steps = math.max(1, (frame / _substep).ceil());
    for (final spring in _all) {
      if (!spring.moving) continue;
      for (var i = 0; i < steps; i++) {
        spring.step(frame / steps);
      }
      // Exactly at rest: the edges land on the pixel grid.
      if (!spring.moving) spring.snap(spring.target);
    }
    notifyListeners();
  }

  void snap(double startAt, double endAt) {
    start.snap(startAt);
    end.snap(endAt);
    lift.snap(0);
    notifyListeners();
  }
}

/// Lays the lens out alone from its springs: it stretches along the track,
/// thins a little while stretched, grows by [grow] while lifted, and its
/// edges sit on physical pixels (crisp at rest and in motion).
class _LensLayout extends SingleChildLayoutDelegate {
  _LensLayout({
    required this.motion,
    required this.track,
    required this.grow,
    required this.devicePixelRatio,
  }) : super(relayout: motion);

  final _LensMotion motion;
  final _LensTrack track;
  final double grow;
  final double devicePixelRatio;

  /// How much the lens thins per dp of stretch, and at most.
  static const double _thinning = .12;
  static const double _thinnest = .2;

  Rect get _rect {
    final start = motion.start.x;
    final end = motion.end.x;
    final lifted = grow * motion.lift.x;
    final stretch = math.max(0.0, end - start - track.length);
    final cross = math.max(
      0.0,
      track.crossLength -
          math.min(track.crossLength * _thinnest, stretch * _thinning) +
          lifted,
    );
    final middle = track.crossStart + track.crossLength / 2;
    double snap(double value) =>
        (value * devicePixelRatio).roundToDouble() / devicePixelRatio;
    final a = snap(start - lifted / 2);
    final b = math.max(a, snap(end + lifted / 2));
    final c = snap(middle - cross / 2);
    final d = math.max(c, snap(middle + cross / 2));
    return track.axis == Axis.horizontal
        ? Rect.fromLTRB(a, c, b, d)
        : Rect.fromLTRB(c, a, d, b);
  }

  @override
  BoxConstraints getConstraintsForChild(BoxConstraints constraints) =>
      BoxConstraints.tight(_rect.size);

  @override
  Offset getPositionForChild(Size size, Size childSize) => _rect.topLeft;

  @override
  bool shouldRelayout(_LensLayout old) =>
      old.motion != motion ||
      old.track != track ||
      old.grow != grow ||
      old.devicePixelRatio != devicePixelRatio;
}

/// The selected tab's lens: a solid pill with a one physical pixel rim.
class _KitNavLens extends StatelessWidget {
  const _KitNavLens({required this.motion});

  final _LensMotion motion;

  @override
  Widget build(BuildContext context) {
    final roles = KitTokens.of(context).roles;
    return CustomPaint(
      painter: _LensPainter(
        motion: motion,
        fill: roles.surface3,
        rim: roles.hairline,
        width: KitTokens.hairlineWidth(context),
      ),
    );
  }
}

class _LensPainter extends CustomPainter {
  _LensPainter({
    required this.motion,
    required this.fill,
    required this.rim,
    required this.width,
  }) : super(repaint: motion);

  final _LensMotion motion;
  final Color fill;
  final Color rim;
  final double width;

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final pill = RRect.fromRectAndRadius(
      Offset.zero & size,
      Radius.circular(size.shortestSide / 2),
    );
    canvas
      ..drawRRect(pill, Paint()..color = fill)
      ..drawDRRect(pill, pill.deflate(width), Paint()..color = rim);
  }

  @override
  bool shouldRepaint(_LensPainter old) =>
      old.motion != motion ||
      old.fill != fill ||
      old.rim != rim ||
      old.width != width;
}

class _KitNavItem extends StatefulWidget {
  const _KitNavItem({
    required this.destination,
    required this.selected,
    required this.onTap,
    required this.focusNode,
    required this.axis,
    required this.sidebar,
    required this.metrics,
    required this.extent,
    required this.topPad,
  });

  final KitNavDestination destination;
  final bool selected;
  final VoidCallback onTap;
  final FocusNode focusNode;
  final Axis axis;
  final bool sidebar;
  final _LabelMetrics metrics;
  final double extent;

  /// Above the lens, so glyph and lens line up (the lens is laid out by the
  /// group, not by the item).
  final double topPad;

  @override
  State<_KitNavItem> createState() => _KitNavItemState();
}

class _KitNavItemState extends State<_KitNavItem> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final reduced = KitMotion.reduced(context);
    final destination = widget.destination;
    final selected = widget.selected;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final count = destination.needsYou;
    final semanticsLabel = count > 0
        ? '${destination.label}${l10n.kitNeedsYouBadgeSuffix(count)}'
        : destination.label;
    final strong = selected || (_hovered && KitLayout.finePointer(context));
    final tone = strong ? KitTextTone.primary : KitTextTone.secondary;

    final Widget content;
    if (widget.sidebar) {
      content = Padding(
        padding: EdgeInsets.symmetric(
          horizontal: tokens.space3,
          vertical: tokens.space2,
        ),
        child: Row(
          children: [
            KitNeedsYou.badge(
              count: count,
              child: KitIcon(
                destination.icon,
                size: KitIconSize.medium,
                tone: tone,
              ),
            ),
            SizedBox(width: tokens.space3),
            Expanded(
              child: KitText(
                destination.label,
                role: KitTextRole.rowTitle,
                tone: tone,
              ),
            ),
          ],
        ),
      );
    } else {
      final glyph = selected
          ? destination.selectedIcon ?? destination.icon
          : destination.icon;
      final label = MediaQuery.withClampedTextScaling(
        maxScaleFactor: widget.metrics.maxScale,
        child: AnimatedSwitcher(
          duration: reduced ? Duration.zero : KitMotion.quick,
          child: KitText(
            destination.label,
            key: ValueKey(tone),
            role: KitTextRole.label,
            tone: tone,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
          ),
        ),
      );
      final stack = Column(
        children: [
          SizedBox(height: widget.topPad),
          SizedBox(
            height: _lensHeight(),
            child: Center(
              child: KitNeedsYou.badge(
                count: count,
                child: KitIcon(glyph, tone: tone),
              ),
            ),
          ),
          Padding(
            padding: EdgeInsets.symmetric(horizontal: tokens.space1),
            child: label,
          ),
        ],
      );
      content = widget.axis == Axis.horizontal
          ? SizedBox(
              width: widget.extent,
              height: double.infinity,
              child: stack,
            )
          : SizedBox(
              width: KitLayout.railWidth,
              height: widget.extent,
              child: stack,
            );
    }

    Widget tappable = KitTappable(
      onTap: widget.onTap,
      label: semanticsLabel,
      selected: selected,
      focusNode: widget.focusNode,
      tappableKey: destination.key,
      shape: widget.sidebar ? KitShape.button : KitShape.pill,
      surface: widget.sidebar
          ? (selected ? KitSurfaceLevel.surface3 : KitSurfaceLevel.ground)
          : KitSurfaceLevel.surface2,
      child: content,
    );
    if (widget.sidebar) {
      // The Desktop canvas: the selected row is a solid surface3 step.
      tappable = DecoratedBox(
        decoration: ShapeDecoration(
          color: selected ? tokens.roles.surface3 : Colors.transparent,
          shape: tokens.shapeOf(KitShape.button),
        ),
        child: tappable,
      );
    }
    return MouseRegion(
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: tappable,
    );
  }
}
