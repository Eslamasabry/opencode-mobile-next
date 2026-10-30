// Views that each keep their own state, switched from a strip of labelled
// tabs (docs/ux-system/kit-api/KitTabSwitcher.md): the body alone
// ([KitTabSwitcher], the shell's destinations under KitNav), the strip alone
// ([KitTabStrip], a board that pages its own lanes) and both together
// ([KitTabSwitcher.tabs]).
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart' show NumberFormat;

import '../../../l10n/app_localizations.dart';
import '../kit_motion.dart';
import '../kit_surface_look.dart';
import '../kit_needs_you.dart';
import '../kit_tappable.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';
import 'kit_motion_parts.dart';

/// One tab of a strip.
@immutable
class KitTab {
  const KitTab({
    required this.label,
    this.count,
    this.needsYou = 0,
    this.icon,
    this.key,
    this.countKey,
    this.needsYouKey,
  });

  /// The host's word ("Working"), shown as written.
  final String label;

  /// Null while the first answer comes: no number, never "0".
  final int? count;

  /// Above 0: `KitNeedsYou.badge` beside the label (LOOK-24).
  final int needsYou;

  /// An optional 20 dp glyph before the label.
  final IconData? icon;

  /// On the tab.
  final Key? key;

  /// On the count.
  final Key? countKey;

  /// On the needs-you badge (present only while [needsYou] is above 0).
  final Key? needsYouKey;
}

/// A strip of tabs: one selected, each with its words, count and needs-you
/// badge. Scrolls sideways when the tabs do not fit, and keeps the selected
/// tab in view. States: default, counts loading, needs you, overflowing.
///
/// Keyboard: the strip is one Tab stop, entered on the selected tab; Left
/// and Right (mirrored right to left) move focus, Home and End jump, Enter
/// and Space select. Arrows never select.
///
/// States: none — every tab can always be selected.
class KitTabStrip extends StatefulWidget {
  const KitTabStrip({
    super.key,
    required this.tabs,
    required this.selected,
    required this.onSelected,
    this.semanticsLabel,
    this.stripKey,
    this.accent = false,
  }) : assert(
         tabs.length >= 2 && tabs.length <= 8,
         'KitTabStrip takes 2 to 8 tabs',
       ),
       assert(selected >= 0 && selected < tabs.length);

  final List<KitTab> tabs;

  /// The index of the selected tab in [tabs].
  final int selected;

  /// Called once per activation of a different tab; never for the selected
  /// one.
  final ValueChanged<int> onSelected;

  /// The group's name ("Columns"); null: none.
  final String? semanticsLabel;

  /// On the strip's scroll view.
  final Key? stripKey;

  /// The selected tab is filled in the accent with its words in the
  /// on-accent colour (a studio's one strip of things to customise), not
  /// the quiet `surface3` pill.
  final bool accent;

  @override
  State<KitTabStrip> createState() => _KitTabStripState();
}

class _KitTabStripState extends State<KitTabStrip> {
  final _controller = ScrollController();
  List<GlobalKey> _keys = [];
  List<FocusNode> _nodes = [];

  @override
  void initState() {
    super.initState();
    _sync();
    // The selected tab starts in view (an overflowing strip whose last tab
    // is selected), without motion.
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => _reveal(widget.selected, animate: false),
    );
  }

  void _sync() {
    final n = widget.tabs.length;
    if (_keys.length == n) return;
    for (final node in _nodes.skip(n)) {
      node.dispose();
    }
    _keys = [
      for (var i = 0; i < n; i++) i < _keys.length ? _keys[i] : GlobalKey(),
    ];
    _nodes = [
      for (var i = 0; i < n; i++)
        i < _nodes.length ? _nodes[i] : FocusNode(debugLabel: 'KitTab $i'),
    ];
  }

  @override
  void didUpdateWidget(KitTabStrip oldWidget) {
    super.didUpdateWidget(oldWidget);
    _sync();
    if (oldWidget.selected != widget.selected) {
      final index = widget.selected;
      WidgetsBinding.instance.addPostFrameCallback((_) => _reveal(index));
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    for (final node in _nodes) {
      node.dispose();
    }
    super.dispose();
  }

  void _reveal(int index, {bool animate = true}) {
    if (!mounted || index >= _keys.length) return;
    final context = _keys[index].currentContext;
    if (context == null) return;
    final duration = !animate || KitMotion.reduced(this.context)
        ? Duration.zero
        : KitMotion.standard;
    // Scrolls only when the tab is cut off at either edge; a tab already in
    // view stays where it is.
    for (final policy in const [
      ScrollPositionAlignmentPolicy.keepVisibleAtEnd,
      ScrollPositionAlignmentPolicy.keepVisibleAtStart,
    ]) {
      Scrollable.ensureVisible(
        context,
        alignmentPolicy: policy,
        duration: duration,
        curve: KitMotion.enter,
      );
    }
  }

  KeyEventResult _onKey(FocusNode _, KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }
    final current = _nodes.indexWhere((node) => node.hasPrimaryFocus);
    if (current < 0) return KeyEventResult.ignored;
    final forward = Directionality.of(context) == TextDirection.ltr ? 1 : -1;
    final last = _nodes.length - 1;
    final key = event.logicalKey;
    final int target;
    if (key == LogicalKeyboardKey.arrowRight) {
      target = (current + forward).clamp(0, last);
    } else if (key == LogicalKeyboardKey.arrowLeft) {
      target = (current - forward).clamp(0, last);
    } else if (key == LogicalKeyboardKey.home) {
      target = 0;
    } else if (key == LogicalKeyboardKey.end) {
      target = last;
    } else {
      return KeyEventResult.ignored;
    }
    if (target != current) {
      _nodes[target].requestFocus();
      _reveal(target);
    }
    return KeyEventResult.handled;
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final tabs = widget.tabs;
    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: widget.semanticsLabel,
      child: Focus(
        canRequestFocus: false,
        skipTraversal: true,
        onKeyEvent: _onKey,
        child: ScrollConfiguration(
          // A horizontal strip keeps no permanent scrollbar (the desktop
          // rule for strips).
          behavior: ScrollConfiguration.of(context).copyWith(scrollbars: false),
          child: SingleChildScrollView(
            key: widget.stripKey,
            controller: _controller,
            scrollDirection: Axis.horizontal,
            padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                for (var i = 0; i < tabs.length; i++) ...[
                  if (i > 0) SizedBox(width: tokens.space2),
                  KeyedSubtree(
                    key: _keys[i],
                    child: ExcludeFocusTraversal(
                      excluding: i != widget.selected,
                      child: _KitTabView(
                        key: tabs[i].key,
                        tab: tabs[i],
                        selected: i == widget.selected,
                        accent: widget.accent,
                        focusNode: _nodes[i],
                        onTap: i == widget.selected
                            ? _none
                            : () => widget.onSelected(i),
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  static void _none() {}
}

/// One tab: a pill that cross-fades in when selected, its words, count and
/// needs-you badge, read as one button.
class _KitTabView extends StatelessWidget {
  const _KitTabView({
    super.key,
    required this.tab,
    required this.selected,
    required this.focusNode,
    required this.onTap,
    this.accent = false,
  });

  final KitTab tab;
  final bool selected;
  final bool accent;
  final FocusNode focusNode;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final locale = Localizations.localeOf(context);
    final l10n = lookupAppLocalizations(locale);
    final reduced = KitMotion.reduced(context);
    final count = tab.count;
    final filled = selected && accent;
    final tone = filled
        ? KitTextTone.onAccent
        : selected
        ? KitTextTone.primary
        : KitTextTone.secondary;
    final spoken = count == null
        ? tab.label
        : l10n.kitTabLabel(tab.label, count);
    final icon = tab.icon;
    // The chosen tab's words, icon and count in the colour the style gives
    // a chosen control (hook H3: Paper inverts it).
    final chosenInk = selected
        ? KitSurfaceLook.wordsOn(
            context,
            fallback: roles.text1,
            role: KitSurfaceRole.control,
            color: filled ? roles.accent : null,
            selected: true,
          )
        : roles.text2;

    Widget words = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (icon != null) ...[
          Icon(icon, size: tokens.smallIconSize, color: chosenInk),
          SizedBox(width: tokens.space1),
        ],
        _tabWords(
          context,
          tab.label,
          tone: tone,
          selected: selected,
          filled: filled,
        ),
        if (count != null) ...[
          SizedBox(width: tokens.space1),
          KeyedSubtree(
            key: tab.countKey,
            child: KitSwap.count(
              count: count,
              child: chosenInk == roles.text1 || !selected
                  ? KitText(
                      NumberFormat.decimalPattern(
                        locale.toString(),
                      ).format(count),
                      key: ValueKey(count),
                      role: KitTextRole.label,
                      tone: KitTextTone.primary,
                      tabular: true,
                      maxLines: 1,
                    )
                  : Text(
                      NumberFormat.decimalPattern(
                        locale.toString(),
                      ).format(count),
                      key: ValueKey(count),
                      maxLines: 1,
                      style:
                          KitText.styleOf(
                            context,
                            KitTextRole.label,
                            tone: KitTextTone.primary,
                          ).copyWith(
                            color: chosenInk,
                            fontFeatures: const [FontFeature.tabularFigures()],
                          ),
                    ),
            ),
          ),
        ],
      ],
    );
    words = Semantics(label: spoken, excludeSemantics: true, child: words);
    if (tab.needsYou > 0) {
      // Room after the words for the badge, which sits at its child's
      // top-end corner.
      words = Padding(
        padding: const EdgeInsetsDirectional.only(end: KitTokens.badgeMinWidth),
        child: words,
      );
    }

    final own = ShapeDecoration(
      color: filled
          ? roles.accent
          : selected
          ? roles.surface3
          : roles.surface3.withValues(alpha: 0),
      shape: tokens.shapeOf(KitShape.pill),
    );
    // The chosen tab is a chosen control (in mint when [accent]); the
    // others are clear in every style.
    final fill = selected
        ? KitSurfaceLook.decorate(
            context,
            solid: own,
            role: KitSurfaceRole.control,
            level: KitSurfaceLevel.surface3,
            color: filled ? roles.accent : null,
            selected: true,
          )
        : own;
    return MergeSemantics(
      child: AnimatedContainer(
        duration: reduced ? Duration.zero : KitMotion.quick,
        curve: KitMotion.enter,
        decoration: fill,
        child: KitTappable(
          onTap: onTap,
          selected: selected,
          shape: KitShape.pill,
          surface: selected ? KitSurfaceLevel.surface3 : KitSurfaceLevel.ground,
          focusNode: focusNode,
          child: Padding(
            padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space3),
            child: KitNeedsYou.badge(
              key: tab.needsYou > 0 ? tab.needsYouKey : null,
              count: tab.needsYou,
              child: words,
            ),
          ),
        ),
      ),
    );
  }
}

/// The destinations, kept alive, switched with a fade-through (design
/// standard §10): the one being left fades out in the first third of
/// [KitMotion.standard], then the chosen one fades in. The two are never
/// both half-visible, so the switch reads as one clean change. Nothing
/// scales (MOT-2).
///
/// Every destination keeps its state (a draft, a scroll position). The
/// chosen one responds at once: it takes touches, focus and screen-reader
/// traversal from the first frame. The one being left is picture only: no
/// focus, no touches, no semantics, and its tickers stop as soon as the
/// selection changes. A quick change of mind starts from what is painted
/// now, so nothing queues or flashes.
///
/// Paint only (opacity over each destination's own repaint boundary). With
/// [reduceMotion] or the system's "remove animations" the chosen destination
/// shows in the next frame.
///
/// With [lazy], a destination is built the first time it is chosen (or
/// from the start when it is in [preload]) and kept from then on, so an
/// unseen destination costs nothing at startup: no build, no reads.
///
/// [KitTabSwitcher.new] is the body alone (the shell's destinations, whose
/// strip is KitNav); [KitTabSwitcher.tabs] puts a [KitTabStrip] over it.
class KitTabSwitcher extends StatefulWidget {
  const KitTabSwitcher({
    super.key,
    required this.index,
    required this.children,
    this.reduceMotion = false,
    this.lazy = false,
    this.preload = const {},
    this.shrinkWrap = false,
    this.onSwipe,
  }) : _tabs = null,
       _onSelected = null,
       _semanticsLabel = null,
       _stripKey = null,
       assert(index >= 0 && index < children.length);

  /// The strip over the kept-alive body. [tabs] and [children] have the same
  /// length.
  const KitTabSwitcher.tabs({
    super.key,
    required List<KitTab> tabs,
    required this.index,
    required ValueChanged<int> onSelected,
    required this.children,
    this.reduceMotion = false,
    this.lazy = false,
    this.preload = const {},
    this.shrinkWrap = false,
    this.onSwipe,
    String? semanticsLabel,
    Key? stripKey,
  }) : _tabs = tabs,
       _onSelected = onSelected,
       _semanticsLabel = semanticsLabel,
       _stripKey = stripKey,
       assert(
         tabs.length == children.length,
         'KitTabSwitcher.tabs: tabs and children have the same length',
       ),
       assert(index >= 0 && index < children.length);

  final int index;
  final List<Widget> children;
  final bool reduceMotion;

  /// Build each destination on its first visit instead of all at once;
  /// a visited destination keeps its state as before.
  final bool lazy;

  /// With [lazy]: the destinations built from the start anyway (the home
  /// destination others return to), besides the selected one.
  final Set<int> preload;

  /// Take the chosen destination's own height (inside a scrolling page)
  /// instead of filling the space it is given.
  final bool shrinkWrap;

  /// A horizontal swipe across the body asks for the next (1) or the
  /// previous (-1) destination, in reading direction, so a body can be
  /// swiped between its destinations. Null: no swipe.
  final ValueChanged<int>? onSwipe;

  final List<KitTab>? _tabs;
  final ValueChanged<int>? _onSelected;
  final String? _semanticsLabel;
  final Key? _stripKey;

  /// How fast a swipe must be (dp per second) to change destination.
  static const swipeVelocity = 300.0;

  /// The share of the switch the old destination takes to fade out.
  static const fadeOutShare = .35;

  /// Retired by kit-KitTabSwitcher-v2: the body no longer scales (MOT-2,
  /// Appendix A #23). Kept so existing references compile (KIT-43).
  static const settleFrom = .97;

  @override
  State<KitTabSwitcher> createState() => _KitTabSwitcherState();
}

class _KitTabSwitcherState extends State<KitTabSwitcher>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: KitMotion.standard,
    value: 1,
  );
  late List<double> _starts = _target(widget.index);
  late int _targetIndex = widget.index;

  /// With [KitTabSwitcher.lazy]: the destinations built so far.
  final Set<int> _visited = {};

  @override
  void initState() {
    super.initState();
    _visited
      ..add(widget.index)
      ..addAll(widget.preload);
  }

  List<double> _target(int index) => [
    for (var i = 0; i < widget.children.length; i++) i == index ? 1 : 0,
  ];

  double _opacity(int index) {
    final t = _controller.value;
    final start = _starts[index];
    if (index == _targetIndex) {
      // Waits for the old destination to clear, unless it was already
      // partly showing (a quick change of mind), then fades in.
      final begin = KitTabSwitcher.fadeOutShare * (1 - start);
      final p = t <= begin
          ? 0.0
          : KitMotion.enter.transform(((t - begin) / (1 - begin)).clamp(0, 1));
      return start + (1 - start) * p;
    }
    final p = KitMotion.enter.transform(
      (t / KitTabSwitcher.fadeOutShare).clamp(0.0, 1.0),
    );
    return start * (1 - p);
  }

  @override
  void didUpdateWidget(KitTabSwitcher oldWidget) {
    super.didUpdateWidget(oldWidget);
    _visited
      ..add(widget.index)
      ..addAll(widget.preload);
    // Turning lazy on never drops what is already built.
    if (!oldWidget.lazy) {
      _visited.addAll([for (var i = 0; i < oldWidget.children.length; i++) i]);
    }
    final reduced = widget.reduceMotion || KitMotion.reduced(context);
    if (reduced || oldWidget.children.length != widget.children.length) {
      _starts = _target(widget.index);
      _targetIndex = widget.index;
      _controller.value = 1;
    } else if (oldWidget.index != widget.index) {
      _starts = [for (var i = 0; i < widget.children.length; i++) _opacity(i)];
      _targetIndex = widget.index;
      _controller.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    Widget body = AnimatedBuilder(
      animation: _controller,
      builder: (context, _) => Stack(
        fit: widget.shrinkWrap ? StackFit.loose : StackFit.expand,
        children: [
          for (var i = 0; i < widget.children.length; i++)
            _destination(i, _opacity(i)),
        ],
      ),
    );
    final onSwipe = widget.onSwipe;
    if (onSwipe != null) {
      final rtl = Directionality.of(context) == TextDirection.rtl;
      body = GestureDetector(
        behavior: HitTestBehavior.translucent,
        onHorizontalDragEnd: (details) {
          final velocity = details.primaryVelocity ?? 0;
          if (velocity.abs() < KitTabSwitcher.swipeVelocity) return;
          onSwipe((velocity < 0) != rtl ? 1 : -1);
        },
        child: body,
      );
    }
    final tabs = widget._tabs;
    final onSelected = widget._onSelected;
    if (tabs == null || onSelected == null) return body;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitTabStrip(
          tabs: tabs,
          selected: widget.index,
          onSelected: onSelected,
          semanticsLabel: widget._semanticsLabel,
          stripKey: widget._stripKey,
        ),
        Expanded(child: body),
      ],
    );
  }

  /// A destination that is itself a scroll view scrolls up under what
  /// sits above the switcher (the Appearance tab row): its top edge fades
  /// out over a short band once it scrolls, so a line is never cut in half
  /// there (owner, 2026-10-22). At rest it is drawn exactly as it is.
  Widget _fading(Widget child) => !widget.shrinkWrap && child is ScrollView
      ? _KitTopFade(
          extent: KitTokens.of(context).space5,
          // Only its alpha counts: the mask keeps what lies under it.
          mask: KitTokens.of(context).roles.text1.withValues(alpha: 1),
          child: child,
        )
      : child;

  Widget _destination(int i, double opacity) {
    final selected = i == widget.index;
    if (widget.lazy && !_visited.contains(i)) {
      // Not chosen yet: nothing is built until it is.
      return const Offstage(child: SizedBox.shrink());
    }
    return Offstage(
      offstage: !selected && opacity == 0,
      child: TickerMode(
        enabled: selected,
        child: ExcludeFocus(
          excluding: !selected,
          child: ExcludeSemantics(
            excluding: !selected,
            child: IgnorePointer(
              ignoring: !selected,
              child: Opacity(
                opacity: opacity,
                alwaysIncludeSemantics: selected,
                child: RepaintBoundary(child: _fading(widget.children[i])),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// A tab's words: [KitText] in [tone], or the colour a style asks for on a
/// chosen tab (hook H3, [KitSurfaceLook.wordsOn]). Solid always takes the
/// first, so its tabs are exactly as before.
Widget _tabWords(
  BuildContext context,
  String label, {
  required KitTextTone tone,
  required bool selected,
  required bool filled,
}) {
  final roles = KitTokens.of(context).roles;
  if (!selected) {
    return KitText(label, role: KitTextRole.label, tone: tone, maxLines: 1);
  }
  final own = KitText.toneColor(roles, tone);
  final ink = KitSurfaceLook.wordsOn(
    context,
    fallback: own,
    role: KitSurfaceRole.control,
    color: filled ? roles.accent : null,
    selected: true,
  );
  if (ink == own) {
    return KitText(label, role: KitTextRole.label, tone: tone, maxLines: 1);
  }
  return Text(
    label,
    maxLines: 1,
    style: KitText.styleOf(
      context,
      KitTextRole.label,
      tone: tone,
    ).copyWith(color: ink),
  );
}

/// Fades the top [extent] of a vertical scroll view out as it scrolls away
/// from its start: by as much as it has scrolled, so the band grows in with
/// the finger and is gone at rest. Paint only; nothing is drawn over the
/// content, so any ground (a live background under glass) shows through.
class _KitTopFade extends StatefulWidget {
  const _KitTopFade({
    required this.extent,
    required this.mask,
    required this.child,
  });

  final double extent;
  final Color mask;
  final Widget child;

  @override
  State<_KitTopFade> createState() => _KitTopFadeState();
}

class _KitTopFadeState extends State<_KitTopFade> {
  double _amount = 0;

  bool _onScroll(Notification notification) {
    final metrics = switch (notification) {
      ScrollNotification(:final metrics, :final depth) when depth == 0 =>
        metrics,
      ScrollMetricsNotification(:final metrics, :final depth) when depth == 0 =>
        metrics,
      _ => null,
    };
    if (metrics == null || metrics.axis != Axis.vertical) return false;
    final scrolled = metrics.pixels - metrics.minScrollExtent;
    final amount = widget.extent <= 0
        ? 0.0
        : (scrolled / widget.extent).clamp(0.0, 1.0);
    if (amount != _amount) setState(() => _amount = amount);
    return false;
  }

  @override
  Widget build(BuildContext context) => NotificationListener<Notification>(
    onNotification: _onScroll,
    child: _TopFadeMask(
      amount: _amount,
      extent: widget.extent,
      mask: widget.mask,
      child: widget.child,
    ),
  );
}

class _TopFadeMask extends SingleChildRenderObjectWidget {
  const _TopFadeMask({
    required this.amount,
    required this.extent,
    required this.mask,
    required super.child,
  });

  final double amount;
  final double extent;
  final Color mask;

  @override
  _RenderTopFadeMask createRenderObject(BuildContext context) =>
      _RenderTopFadeMask(amount: amount, extent: extent, mask: mask);

  @override
  void updateRenderObject(
    BuildContext context,
    _RenderTopFadeMask renderObject,
  ) {
    renderObject
      ..amount = amount
      ..extent = extent
      ..mask = mask;
  }
}

/// [RenderShaderMask] that steps aside at rest ([amount] 0): no layer, no
/// save, the child painted as it is.
class _RenderTopFadeMask extends RenderProxyBox {
  _RenderTopFadeMask({
    required double amount,
    required double extent,
    required Color mask,
  }) : _amount = amount,
       _extent = extent,
       _mask = mask;

  Color _mask;
  set mask(Color value) {
    if (value == _mask) return;
    _mask = value;
    markNeedsPaint();
  }

  double _amount;
  set amount(double value) {
    if (value == _amount) return;
    final was = _amount > 0;
    _amount = value;
    if (was != (value > 0)) markNeedsCompositingBitsUpdate();
    markNeedsPaint();
  }

  double _extent;
  set extent(double value) {
    if (value == _extent) return;
    _extent = value;
    markNeedsPaint();
  }

  @override
  ShaderMaskLayer? get layer => super.layer as ShaderMaskLayer?;

  @override
  bool get alwaysNeedsCompositing => child != null && _amount > 0;

  @override
  void paint(PaintingContext context, Offset offset) {
    if (child == null || _amount <= 0 || size.height <= 0) {
      layer = null;
      super.paint(context, offset);
      return;
    }
    final band = (_extent / size.height).clamp(0.0, 1.0);
    final shader = LinearGradient(
      begin: Alignment.topCenter,
      end: Alignment.bottomCenter,
      colors: [
        _mask.withValues(alpha: 1 - _amount),
        _mask,
      ],
      stops: [0, band],
    ).createShader(Offset.zero & size);
    layer ??= ShaderMaskLayer();
    layer!
      ..shader = shader
      ..maskRect = offset & size
      ..blendMode = BlendMode.dstIn;
    context.pushLayer(layer!, super.paint, offset);
  }
}
