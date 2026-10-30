// The AI Team board's columns (docs/ux-system/kit-api/KitBoardLane.md): a
// strip of column tabs with counts over lanes of task cards, paged one at a
// time with the neighbours peeking on a phone, side by side on a wide
// window.
import 'dart:async' show FutureOr;

import 'package:flutter/gestures.dart' show PointerDeviceKind;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import 'kit_layout.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_progress.dart';
import 'kit_surface_look.dart';
import 'kit_tokens.dart';
import 'motion/kit_animated_rows.dart';
import 'motion/kit_refresh.dart';
import 'motion/kit_tab_switcher.dart';

/// One column's tab in the strip.
@immutable
class KitBoardColumn {
  const KitBoardColumn({
    required this.label,
    this.count,
    this.needsYou = 0,
    this.tabKey,
  });

  /// The host's word ("Working"), shown as written (COPY-13).
  final String label;

  /// Null while the first answer comes: no number, never "0". Once known it
  /// equals the lane's card count (asserted in debug).
  final int? count;

  /// Cards in it that need the person: `KitNeedsYou.badge` beside the count.
  final int needsYou;

  /// On the column's tab, e.g. `ValueKey('team-board-tab-working')`.
  final Key? tabKey;
}

/// The board: the column strip and the lanes, kept in step.
///
/// [selected] drives both: the strip shows it selected, and the lanes page
/// to it (compact, medium, short) or scroll it into view (expanded, large).
/// A swipe or scroll that settles on another lane, a strip tap and Ctrl+→ /
/// Ctrl+← each call [onSelected]; the host rebuilds with the new
/// [selected]. The part never picks the opening column.
///
/// Compact, medium and short windows show one lane at a time, at most
/// [KitLayout.laneMaxWidth] wide, with the neighbours peeking
/// ([KitLayout.lanePeek] on a phone, shrinking to nothing at 320 dp). Wide
/// windows show as many lanes side by side as fit at
/// [KitLayout.laneMinWidth] or more, in a horizontal list with a visible
/// scrollbar on a fine pointer.
///
/// Stale is not dimming: last-known cards stay at full strength; the
/// screen's status line says how old they are (STATE-18, LOOK-14).
///
/// States: loading, loaded, empty (per lane). Not answering, failed and an
/// empty board are the host's page state, shown instead of this part.
class KitBoardLanes extends StatefulWidget {
  const KitBoardLanes({
    super.key,
    required this.columns,
    required this.selected,
    required this.onSelected,
    required this.laneBuilder,
    this.loading = false,
    this.pagesKey,
    this.stripKey,
  }) : assert(
         columns.length >= 2 && columns.length <= 8,
         'KitBoardLanes shows 2 to 8 columns.',
       );

  /// The columns, in order from the start edge; 2 to 8.
  final List<KitBoardColumn> columns;

  /// The column in view (paged) or scrolled to (side by side).
  final int selected;

  /// A swipe that settled, a strip tap or a keyboard move.
  final ValueChanged<int> onSelected;

  /// Builds column [index]'s [KitBoardLane].
  final Widget Function(BuildContext context, int index) laneBuilder;

  /// The first answer is on its way: the strip without counts and one
  /// [KitBoardLane.loading] in the selected column; nothing swipes yet.
  final bool loading;

  /// On the pages (paged) or the horizontal list (side by side), e.g.
  /// `ValueKey('team-board-pages')`.
  final Key? pagesKey;

  /// On the strip's scroll view.
  final Key? stripKey;

  @override
  State<KitBoardLanes> createState() => _KitBoardLanesState();
}

class _KitBoardLanesState extends State<KitBoardLanes> {
  /// The narrowest phone window: its lane runs edge to edge, no peek.
  static const double _narrowestWindow = 320;

  PageController? _pages;
  double? _fraction;
  final _wide = ScrollController();

  /// Lane width plus the gap, side by side (for the settle and the reveal).
  double _wideStep = 0;
  double _wideLane = 0;

  /// Scrolls the part starts itself; their end is not a person's choice.
  int _driving = 0;

  /// The lane a scroll the part started is heading to.
  int? _heading;

  bool _isWide = false;
  List<FocusNode> _laneNodes = [];

  @override
  void initState() {
    super.initState();
    _syncNodes();
  }

  void _syncNodes() {
    final n = widget.columns.length;
    if (_laneNodes.length == n) return;
    for (final node in _laneNodes.skip(n)) {
      node.dispose();
    }
    _laneNodes = [
      for (var i = 0; i < n; i++)
        i < _laneNodes.length
            ? _laneNodes[i]
            : FocusNode(
                debugLabel: 'KitBoardLane $i',
                canRequestFocus: false,
                skipTraversal: true,
              ),
    ];
  }

  @override
  void didUpdateWidget(KitBoardLanes oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncNodes();
    if (oldWidget.selected != widget.selected ||
        oldWidget.loading != widget.loading) {
      final index = widget.selected;
      WidgetsBinding.instance.addPostFrameCallback((_) => _reveal(index));
    }
  }

  @override
  void dispose() {
    _pages?.dispose();
    _wide.dispose();
    for (final node in _laneNodes) {
      node.dispose();
    }
    super.dispose();
  }

  /// Brings lane [index] into view: pages to it, or scrolls it in when it
  /// is not already wholly shown. Reduced motion jumps.
  void _reveal(int index) {
    if (!mounted || widget.loading) return;
    final reduced = KitMotion.reduced(context);
    if (!_isWide) {
      final pages = _pages;
      if (pages == null || !pages.hasClients) return;
      final page = pages.page?.round() ?? pages.initialPage;
      if (page == index || _heading == index) return;
      if (reduced) {
        _drive(index, () => pages.jumpToPage(index));
      } else {
        _drive(
          index,
          () => pages.animateToPage(
            index,
            duration: KitMotion.standard,
            curve: KitMotion.emphasized,
          ),
        );
      }
      return;
    }
    if (!_wide.hasClients || _wideStep <= 0 || _heading == index) return;
    final position = _wide.position;
    if (_fullyShown(index, position)) return;
    final target = (index * _wideStep).clamp(
      position.minScrollExtent,
      position.maxScrollExtent,
    );
    if (reduced) {
      _drive(index, () => _wide.jumpTo(target));
    } else {
      _drive(
        index,
        () => _wide.animateTo(
          target,
          duration: KitMotion.standard,
          curve: KitMotion.emphasized,
        ),
      );
    }
  }

  /// Runs a scroll the part starts, so its end does not select anything.
  void _drive(int index, FutureOr<void> Function() scroll) {
    _driving++;
    _heading = index;
    void done() {
      _driving--;
      if (_heading == index) _heading = null;
    }

    final running = scroll();
    if (running is Future<void>) {
      running.whenComplete(done);
    } else {
      done();
    }
  }

  /// A strip tap or a keyboard move: the lanes start moving at once (a
  /// jump under reduced motion shows on the next frame), then the host
  /// hears of it and rebuilds with [index] selected.
  void _select(int index) {
    if (index == widget.selected) return;
    _reveal(index);
    widget.onSelected(index);
  }

  bool _fullyShown(int index, ScrollPosition position) {
    // Lane [index] starts at gutter + index * step in the list's content;
    // the view shows [pixels, pixels + viewport].
    final gutter = KitTokens.of(context).gutter;
    final start = gutter + index * _wideStep;
    return start >= position.pixels - .5 &&
        start + _wideLane <= position.pixels + position.viewportDimension + .5;
  }

  /// A scroll the person made has come to rest: the lane it rests on is
  /// the selected one.
  bool _onScrollEnd(ScrollEndNotification notification) {
    if (notification.depth != 0 || _driving > 0 || widget.loading) {
      return false;
    }
    int? settled;
    if (_isWide) {
      if (_wideStep > 0 && _wide.hasClients) {
        final position = _wide.position;
        if (!_fullyShown(widget.selected, position)) {
          settled = (position.pixels / _wideStep).round();
        }
      }
    } else {
      final page = _pages?.page;
      if (page != null) settled = page.round();
    }
    if (settled == null) return false;
    settled = settled.clamp(0, widget.columns.length - 1);
    if (settled != widget.selected) widget.onSelected(settled);
    return false;
  }

  KeyEventResult _onKey(FocusNode _, KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }
    if (widget.loading || !HardwareKeyboard.instance.isControlPressed) {
      return KeyEventResult.ignored;
    }
    final key = event.logicalKey;
    final forward = Directionality.of(context) == TextDirection.ltr ? 1 : -1;
    final int step;
    if (key == LogicalKeyboardKey.arrowRight) {
      step = forward;
    } else if (key == LogicalKeyboardKey.arrowLeft) {
      step = -forward;
    } else {
      return KeyEventResult.ignored;
    }
    final target = widget.selected + step;
    if (target < 0 || target >= widget.columns.length) {
      return KeyEventResult.handled;
    }
    _select(target);
    // The host rebuilds with the new column in the next frame; its first
    // card joins the focus order then.
    WidgetsBinding.instance.addPostFrameCallback((_) => _focusLane(target));
    WidgetsBinding.instance.scheduleFrame();
    return KeyEventResult.handled;
  }

  /// Focuses the first card (top, then start edge) of lane [index].
  void _focusLane(int index) {
    if (!mounted || index >= _laneNodes.length) return;
    final ltr = Directionality.of(context) == TextDirection.ltr;
    final nodes =
        _laneNodes[index].traversalDescendants
            .where((node) => node.context != null)
            .toList()
          ..sort((a, b) {
            final ra = a.rect, rb = b.rect;
            final byTop = ra.top.compareTo(rb.top);
            if (byTop != 0) return byTop;
            return ltr
                ? ra.left.compareTo(rb.left)
                : rb.right.compareTo(ra.right);
          });
    if (nodes.isNotEmpty) nodes.first.requestFocus();
  }

  String _laneLabel(AppLocalizations l10n, KitBoardColumn column) {
    final count = column.count;
    return count == null
        ? column.label
        : l10n.kitBoardLane(column.label, count);
  }

  /// Lane [index] as the host built it, labelled for screen readers, in its
  /// own focus group (Tab runs through its cards before the next lane's).
  Widget _lane(BuildContext context, int index, {required bool inFocusOrder}) {
    final lane = widget.laneBuilder(context, index);
    assert(
      () {
        final count = widget.columns[index].count;
        if (lane is KitBoardLane && !lane.isLoading && count != null) {
          return count == lane.cards.length;
        }
        return true;
      }(),
      'KitBoardLanes: column $index says ${widget.columns[index].count} but its lane shows ${lane is KitBoardLane ? lane.cards.length : '?'} cards.',
    );
    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: _laneLabel(AppLocalizations.of(context), widget.columns[index]),
      child: ExcludeFocusTraversal(
        excluding: !inFocusOrder,
        child: Focus(
          focusNode: _laneNodes[index],
          canRequestFocus: false,
          skipTraversal: true,
          child: FocusTraversalGroup(child: lane),
        ),
      ),
    );
  }

  /// A lane's width when one shows at a time in a view [width] wide.
  double _pagedLaneWidth(double width) {
    final peek = ((width - _narrowestWindow) / 2).clamp(
      0.0,
      KitLayout.lanePeek,
    );
    final lane = width - 2 * peek;
    return lane < KitLayout.laneMaxWidth ? lane : KitLayout.laneMaxWidth;
  }

  PageController _pagesFor(double fraction) {
    final existing = _pages;
    if (existing != null && _fraction == fraction) return existing;
    if (existing != null) {
      // Rotated or resized: a new fraction needs a new controller.
      WidgetsBinding.instance.addPostFrameCallback((_) => existing.dispose());
    }
    _fraction = fraction;
    return _pages = PageController(
      initialPage: widget.selected,
      viewportFraction: fraction,
    );
  }

  Widget _paged(BuildContext context, double width) {
    final tokens = KitTokens.of(context);
    final laneHalfGap = tokens.space1 / 2;
    final lane = _pagedLaneWidth(width);
    final pages = _pagesFor(width <= 0 ? 1 : lane / width);
    return NotificationListener<ScrollEndNotification>(
      onNotification: _onScrollEnd,
      child: ScrollConfiguration(
        // A mouse drag never pages; the strip does (§8.3).
        behavior: ScrollConfiguration.of(context).copyWith(
          dragDevices: const {
            PointerDeviceKind.touch,
            PointerDeviceKind.stylus,
            PointerDeviceKind.invertedStylus,
            PointerDeviceKind.trackpad,
            PointerDeviceKind.unknown,
          },
        ),
        child: PageView.builder(
          key: widget.pagesKey,
          controller: pages,
          itemCount: widget.columns.length,
          itemBuilder: (context, index) => Padding(
            // Half of space1 on each side: space1 between two paged lanes.
            padding: EdgeInsetsDirectional.only(
              start: laneHalfGap,
              end: laneHalfGap,
              bottom: tokens.space2,
            ),
            child: _lane(
              context,
              index,
              inFocusOrder: index == widget.selected,
            ),
          ),
        ),
      ),
    );
  }

  Widget _sideBySide(BuildContext context, double width) {
    final tokens = KitTokens.of(context);
    final room = width - 2 * tokens.gutter;
    final fit =
        ((room + tokens.space3) / (KitLayout.laneMinWidth + tokens.space3))
            .floor()
            .clamp(1, widget.columns.length);
    final lane = ((room - (fit - 1) * tokens.space3) / fit).floorToDouble();
    _wideLane = lane;
    _wideStep = lane + tokens.space3;
    final fine = KitLayout.finePointer(context);
    return NotificationListener<ScrollEndNotification>(
      onNotification: _onScrollEnd,
      child: Scrollbar(
        controller: _wide,
        thumbVisibility: fine,
        child: ScrollConfiguration(
          behavior: ScrollConfiguration.of(context).copyWith(
            scrollbars: false,
            dragDevices: const {
              PointerDeviceKind.touch,
              PointerDeviceKind.stylus,
              PointerDeviceKind.invertedStylus,
              PointerDeviceKind.trackpad,
              PointerDeviceKind.unknown,
            },
          ),
          child: SingleChildScrollView(
            key: widget.pagesKey,
            controller: _wide,
            scrollDirection: Axis.horizontal,
            padding: EdgeInsetsDirectional.only(
              start: tokens.gutter,
              end: tokens.gutter,
              bottom: tokens.space2,
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                for (var i = 0; i < widget.columns.length; i++) ...[
                  if (i > 0) SizedBox(width: tokens.space3),
                  SizedBox(
                    width: lane,
                    child: _lane(context, i, inFocusOrder: true),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// The first answer is on its way: one skeleton lane where the selected
  /// column will be, nothing to swipe.
  Widget _loading(BuildContext context, double width) {
    final tokens = KitTokens.of(context);
    final l10n = AppLocalizations.of(context);
    final column = widget.columns[widget.selected];
    final double lane;
    final AlignmentDirectional align;
    if (_isWide) {
      final room = width - 2 * tokens.gutter;
      lane = room < KitLayout.laneMaxWidth ? room : KitLayout.laneMaxWidth;
      align = AlignmentDirectional.topStart;
    } else {
      lane = _pagedLaneWidth(width) - tokens.space1;
      align = AlignmentDirectional.topCenter;
    }
    return Padding(
      padding: EdgeInsetsDirectional.only(
        bottom: tokens.space2,
      ).copyWith(start: _isWide ? tokens.gutter : null),
      child: Align(
        alignment: align,
        child: SizedBox(
          width: lane,
          child: Semantics(
            container: true,
            label: l10n.kitBoardLaneLoading(column.label),
            child: const KitBoardLane.loading(),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    // A phone in landscape keeps the paged board (the short row of §8.2).
    _isWide = KitLayout.modalWindowOf(context).isWide;
    return Focus(
      canRequestFocus: false,
      skipTraversal: true,
      onKeyEvent: _onKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitTabStrip(
            stripKey: widget.stripKey,
            tabs: [
              for (final column in widget.columns)
                KitTab(
                  label: column.label,
                  count: widget.loading ? null : column.count,
                  needsYou: widget.loading ? 0 : column.needsYou,
                  key: column.tabKey,
                ),
            ],
            selected: widget.selected,
            onSelected: _select,
          ),
          SizedBox(height: tokens.labelGap),
          Expanded(
            child: LayoutBuilder(
              builder: (context, constraints) {
                final width = constraints.maxWidth;
                if (widget.loading) return _loading(context, width);
                return _isWide
                    ? _sideBySide(context, width)
                    : _paged(context, width);
              },
            ),
          ),
        ],
      ),
    );
  }
}

/// One column's cards: a vertical list on the lane surface (`ground` with a
/// one-physical-pixel hairline, so a peeking neighbour reads as another
/// column), the cards [KitTokens.space2] apart, arriving and leaving with
/// [KitAnimatedRows]. With no cards it shows its inline [empty] state, never
/// a blank lane. Pull to refresh with [onRefresh] (KitRefresh).
///
/// States: loading ([KitBoardLane.loading]), loaded, empty.
class KitBoardLane extends StatelessWidget {
  const KitBoardLane({
    super.key,
    required this.cards,
    this.empty,
    this.onRefresh,
    this.rowsKey,
    this.laneKey,
    this.listKey,
  }) : isLoading = false;

  /// Skeleton cards while the first answer comes (STATE-4).
  const KitBoardLane.loading({super.key, this.laneKey})
    : cards = const [],
      empty = null,
      onRefresh = null,
      rowsKey = null,
      listKey = null,
      isLoading = true;

  /// The cards, normally `KitTaskCard`s, each with its own key.
  final List<Widget> cards;

  /// The inline `KitStateView` shown when [cards] is empty; required then.
  final Widget? empty;

  /// Pull to refresh on this lane.
  final Future<void> Function()? onRefresh;

  /// A new key restarts the arrival animation (a new filter or page of
  /// data shows at once).
  final Key? rowsKey;

  /// On the lane surface, e.g. `ValueKey('team-board-column-working')`.
  final Key? laneKey;

  /// On the lane's list, e.g. `ValueKey('team-board-list-working')`.
  final Key? listKey;

  /// The skeleton lane.
  final bool isLoading;

  /// Skeleton cards in a loading lane.
  static const skeletonCards = 4;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.lane(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    assert(
      isLoading || cards.isNotEmpty || empty != null,
      'KitBoardLane: a lane with no cards needs its empty state.',
    );
    assert(
      cards.every((card) => card.key != null),
      'KitBoardLane: every card needs its own key.',
    );
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final radius = BorderRadius.circular(tokens.panelCornerRadius);
    final Widget body;
    if (isLoading) {
      body = ListView(
        physics: const NeverScrollableScrollPhysics(),
        padding: EdgeInsetsDirectional.all(tokens.space2),
        children: [
          for (var i = 0; i < skeletonCards; i++)
            Padding(
              padding: EdgeInsetsDirectional.only(bottom: tokens.space2),
              // A skeleton card: a content card in the style.
              child: kitSurfaceBox(
                style: KitSurfaceLook.styleOf(context),
                decoration: KitSurfaceLook.decorate(
                  context,
                  solid: BoxDecoration(
                    color: roles.surface1,
                    borderRadius: BorderRadius.circular(tokens.cardRadius),
                  ),
                ),
                child: Padding(
                  padding: EdgeInsetsDirectional.symmetric(
                    vertical: tokens.space1,
                  ),
                  child: const KitSkeletonRows(count: 1),
                ),
              ),
            ),
        ],
      );
    } else {
      final content = cards.isEmpty
          ? empty!
          : KitAnimatedRows(
              key: rowsKey,
              children: [
                for (final card in cards)
                  Padding(
                    key: ValueKey<Key?>(card.key),
                    padding: EdgeInsetsDirectional.only(bottom: tokens.space2),
                    child: card,
                  ),
              ],
            );
      final list = ListView(
        key: listKey,
        physics: const AlwaysScrollableScrollPhysics(),
        padding: EdgeInsetsDirectional.only(
          start: tokens.space2,
          top: tokens.space2,
          end: tokens.space2,
        ),
        children: [content],
      );
      final refresh = onRefresh;
      body = refresh == null
          ? list
          : KitRefresh(onRefresh: refresh, child: list);
    }
    // The lane is the bed its cards sit in: a track in the style (a well
    // under Soft, an ink box under Brutal and Paper).
    return kitSurfaceBox(
      key: laneKey,
      style: KitSurfaceLook.styleOf(context),
      decoration: KitSurfaceLook.decorate(
        context,
        solid: BoxDecoration(
          color: roles.ground,
          borderRadius: radius,
          border: Border.all(
            color: roles.hairline,
            width: KitTokens.hairlineWidth(context),
          ),
        ),
        role: KitSurfaceRole.track,
        borderRadius: radius,
      ),
      child: ClipRRect(borderRadius: radius, child: body),
    );
  }
}
