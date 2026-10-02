import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/scheduler.dart';

import 'kit_bottom_inset.dart';
import 'kit_buttons.dart';
import 'kit_details_fold.dart';
import 'kit_jump_pill.dart';
import 'kit_layout.dart';
import 'kit_nav.dart';
import 'kit_page_route.dart';
import 'kit_progress.dart';
import 'kit_search_field.dart';
import 'kit_status_line.dart';
import 'kit_status_slot.dart';
import 'kit_tokens.dart';
import 'kit_top_bar.dart';
import 'motion/kit_motion_parts.dart';
import 'motion/kit_refresh.dart';

/// How wide a single-pane body may grow on medium and wider windows.
enum KitScreenWidth {
  /// Today's behaviour: the whole width (the default, KIT-43).
  full,

  /// Forms, settings, a page of prose: centred at KitLayout.readingWidth (720).
  reading,

  /// A list on its own: centred at KitLayout.listWidth (960).
  list,
}

/// The one screen frame (docs/ux-system/kit-api/KitScreen.md; kit-v2.md
/// §2.12, §8.2). A page (a route) is `KitScreen(topBar: …)`: the ground, the
/// top bar, the one status line, a pinned search, the one loading bar, the
/// body, a pinned bottom primary lifted above the keyboard, and the bottom
/// inset floating parts use. A body inside the shell is `KitScreen` without
/// a top bar. From expanded it can show two panes ([KitScreen.twoPane]:
/// list 296 · detail up to 700) and on large three ([KitScreen.threePane]:
/// … · side 340), with selection filling the detail instead of pushing a
/// route. `KitScreen(topBar:)` is K2 §9.2's "KitScaffold": there is no
/// separate class.
///
/// Order, top to bottom: top bar → status slot → search → [header] rows →
/// the one [KitLoadingBar] → [body] (with [jump] floating over it) →
/// [bottom]. The bottom block sits `space2` above whatever is published
/// below it (the dock, the system gesture inset) or the keyboard, and
/// KitScreen publishes its measured height through [KitBottomInset] to the
/// body, so [KitScreen.endPadding] and floating parts clear it.
///
/// One status line per window (KIT-35): a KitScreen with no
/// [KitStatusLineSlot] above it owns one; a KitScreen under a slot (a tab
/// body, a pane) contributes its [status] and draws none.
///
/// Debug builds check after each frame that the visible subtree holds at
/// most one primary [KitButton], one drawn [KitStatusLine], one
/// [KitRefresh] and one [KitDetailsFold] (LAY-12, K2 §2.7).
///
/// States: default, loading (the one bar under the header), status (a line
/// in the slot), search (pinned field), keyboard (bottom lifted), twoPane
/// empty detail, twoPane selected, threePane (large). Empty and error are
/// the body's `KitStateView`, not KitScreen's. No disabled.
class KitScreen extends StatelessWidget {
  /// Semantic boundary for independently traversed adaptive columns.
  static const paneSemanticsPrefix = 'kit-screen-pane-';

  /// v1 parameters unchanged; every v2 parameter is optional.
  const KitScreen({
    super.key,
    required this.body,
    this.header = const [],
    this.loading = false,
    this.loadingLabel = '',
    this.bottom,
    this.topBar,
    this.search,
    this.status,
    this.bodySays = const {},
    this.bodyQuiets = const {},
    this.jump,
    this.width = KitScreenWidth.full,
    this.bottomKey,
    this.page = false,
  }) : detail = null,
       emptyDetail = null,
       side = null,
       listPaneKey = null,
       detailPaneKey = null,
       sidePaneKey = null;

  /// From expanded: [list] at KitLayout.paneListWidth (296) and [detail]
  /// in the rest, its content capped at KitLayout.paneDetailMaxWidth (700).
  /// Below expanded (or a short window): [list] only; opening a row pushes
  /// the detail as a page (see [openDetail]).
  const KitScreen.twoPane({
    super.key,
    required Widget list,
    required this.detail,
    required Widget this.emptyDetail,
    this.topBar,
    this.search,
    this.status,
    this.bodySays = const {},
    this.bodyQuiets = const {},
    this.loading = false,
    this.loadingLabel = '',
    this.bottom,
    this.listPaneKey,
    this.detailPaneKey,
  }) : body = list,
       header = const [],
       jump = null,
       width = KitScreenWidth.full,
       bottomKey = null,
       page = false,
       side = null,
       sidePaneKey = null;

  /// twoPane plus, on large only, [side] at KitLayout.paneSideWidth (340)
  /// at the end (the changes pane). Below large, [side] is not shown; the
  /// screen offers it through a top bar action that opens it with
  /// showKitSheet(height: KitSheetHeight.full).
  const KitScreen.threePane({
    super.key,
    required Widget list,
    required this.detail,
    required Widget this.emptyDetail,
    required Widget this.side,
    this.topBar,
    this.search,
    this.status,
    this.bodySays = const {},
    this.bodyQuiets = const {},
    this.loading = false,
    this.loadingLabel = '',
    this.bottom,
    this.listPaneKey,
    this.detailPaneKey,
    this.sidePaneKey,
  }) : body = list,
       header = const [],
       jump = null,
       width = KitScreenWidth.full,
       bottomKey = null,
       page = false;

  /// The single scroll view; for the pane constructors, the list.
  final Widget body;

  /// The selected item's view (pane constructors); null shows [emptyDetail].
  final Widget? detail;

  /// Non-null for the pane constructors: a page-size KitStateView.
  final Widget? emptyDetail;

  /// threePane's side pane (large only).
  final Widget? side;

  /// Fixed rows above the loading bar: the screen's own header.
  final List<Widget> header;
  final bool loading;
  final String loadingLabel;

  /// The pinned primary block (a KitActionBlock).
  final Widget? bottom;

  /// A page: builds the frame (ground, bar, safe area).
  final KitTopBar? topBar;

  /// A page with no bar: the frame [topBar] builds (ground, top safe area,
  /// content above the keyboard, the snack bar host) without a [KitTopBar].
  /// The PC shell's content pane, where the sidebar beside it already
  /// names the destination and holds the shell controls (slice-R14).
  final bool page;

  /// Pinned under the bar and the status line.
  final KitSearchField? search;

  /// This screen's condition, into the one slot (an object, never words).
  final KitStatus? status;

  /// Kinds of app-wide condition this screen's [body] already says as the
  /// page itself (root-connecting's card *is* the connection condition):
  /// the slot does not repeat them, so nothing is said twice. Applies only
  /// to the slot this screen owns; under an outer slot it has no effect.
  final Set<KitStatusKind> bodySays;

  /// Kinds of app-wide condition that are about something other than this
  /// page (a saved server not answering, over the phone's own setup): the
  /// slot still says them, as one line with their actions behind More
  /// ([KitStatus.compact]), so the page is not pushed down by another
  /// thing's controls. [bodySays] wins for a kind in both. Applies only to
  /// the slot this screen owns; under an outer slot it has no effect.
  final Set<KitStatusKind> bodyQuiets;

  /// Floats over [body], above [bottom].
  final KitJumpPill? jump;
  final KitScreenWidth width;

  /// The bottom block's key; default `ValueKey('kit-screen-bottom')` (v1).
  final Key? bottomKey;
  final Key? listPaneKey;
  final Key? detailPaneKey;
  final Key? sidePaneKey;

  static const _defaultBottomKey = ValueKey('kit-screen-bottom');

  /// Space after the last row when nothing is pinned below the list (v1
  /// signature): `space4` plus everything published below this point
  /// (the dock, a pinned primary, the keyboard, the gesture inset).
  static double endPadding(BuildContext context) =>
      KitTokens.of(context).space4 + KitBottomInset.of(context).bottom;

  /// The body scroll view's padding: the gutter on each side and
  /// [endPadding] at the bottom (LAY-6).
  static EdgeInsetsDirectional padding(BuildContext context) {
    final gutter = KitTokens.of(context).gutter;
    return EdgeInsetsDirectional.only(
      start: gutter,
      end: gutter,
      bottom: endPadding(context),
    );
  }

  /// True when this window shows the detail beside the list (expanded or
  /// large, not short).
  static bool showsDetail(BuildContext context) =>
      KitLayout.windowOf(context).isWide && !KitLayout.isShort(context);

  /// True when this window shows threePane's side pane (large, not short).
  static bool showsSide(BuildContext context) =>
      KitLayout.windowOf(context) == KitWindow.large &&
      !KitLayout.isShort(context);

  /// True inside a twoPane/threePane detail or side pane. The seam for
  /// `KitTopBar(exit: auto)`, which shows no Back there (KitScreen.md
  /// "Panes").
  static bool inPane(BuildContext context) =>
      context.getInheritedWidgetOfExactType<_KitScreenScope>()?.pane ?? false;

  /// Opens a row's detail the adaptive way: where [showsDetail], calls
  /// [select] (the caller stores the selection and rebuilds with `detail`)
  /// and returns null; otherwise pushes [page] with pushKitPage and returns
  /// its result.
  static Future<T?> openDetail<T>(
    BuildContext context, {
    required VoidCallback select,
    required WidgetBuilder page,
    RouteSettings? settings,
  }) {
    if (showsDetail(context)) {
      select();
      return Future<T?>.value();
    }
    return pushKitPage<T>(context, page, settings: settings);
  }

  bool get _hasPanes => emptyDetail != null;

  @override
  Widget build(BuildContext context) {
    assert(
      (this.topBar == null && !page) || _pageAllowedAt(context),
      'KitScreen: a KitScreen(topBar:) inside another KitScreen body — one '
      'bar per window area (KIT-36); only a twoPane/threePane detail or side '
      'may hold its own page',
    );
    final panes = _hasPanes && showsDetail(context);
    // Built under the page frame, so the keyboard the frame already lifts
    // the content above is not counted again.
    final Widget content = Builder(
      builder: (context) =>
          panes ? _panes(context) : _column(context, withBar: false),
    );
    final slotted = KitStatusLineSlot.existsAbove(context)
        ? KitStatusContribution(status: status, child: content)
        : KitStatusLineSlot(
            status: status,
            omit: bodySays,
            compact: bodyQuiets,
            child: content,
          );
    final topBar = this.topBar;
    final isPage = topBar != null || page;
    final Widget framed;
    if (!isPage) {
      framed = slotted;
    } else {
      framed = _PageFrame(topBar: panes ? null : topBar, child: slotted);
    }
    // A page with no Scaffold above it hosts one, so the screens that still
    // call ScaffoldMessenger.showSnackBar (until they move to KitUndo) show
    // their snack bar instead of queueing it with nowhere to draw.
    final hostsSnackBars = isPage && Scaffold.maybeOf(context) == null;
    return _KitScreenCheck(
      child: hostsSnackBars ? _SnackBarHost(child: framed) : framed,
    );
  }

  static bool _pageAllowedAt(BuildContext context) {
    final scope = context.getInheritedWidgetOfExactType<_KitScreenScope>();
    return scope == null || scope.pane;
  }

  /// The single-pane arrangement below the slot (or the list pane's, with
  /// its own bar when [withBar]).
  Widget _column(BuildContext context, {required bool withBar}) {
    final tokens = KitTokens.of(context);
    final window = KitLayout.windowOf(context);
    final maxWidth = window == KitWindow.compact
        ? null
        : switch (width) {
            KitScreenWidth.full => null,
            KitScreenWidth.reading => KitLayout.readingWidth,
            KitScreenWidth.list => KitLayout.listWidth,
          };
    Widget centred(Widget child, {bool fill = false}) =>
        maxWidth == null ? child : _Centred(maxWidth, fill: fill, child: child);

    final search = this.search;
    final bottom = this.bottom;
    final jump = this.jump;
    final bar = withBar ? topBar : null;
    final lift = math.max(
      KitBottomInset.of(context).bottom,
      MediaQuery.viewInsetsOf(context).bottom,
    );
    return _BottomBlockHost(
      bottom: bottom == null
          ? null
          : Padding(
              key: bottomKey ?? _defaultBottomKey,
              padding: EdgeInsetsDirectional.only(bottom: lift),
              child: centred(
                Padding(
                  padding: EdgeInsetsDirectional.fromSTEB(
                    tokens.gutter,
                    tokens.space2,
                    tokens.gutter,
                    tokens.space2,
                  ),
                  child: bottom,
                ),
              ),
            ),
      builder: (context, blockHeight, bottomBlock) {
        Widget inner = _KitScreenScope(pane: false, child: body);
        if (jump != null) {
          inner = KitJumpPillLayer(
            pill: jump,
            clearBottomInset: bottomBlock == null,
            child: inner,
          );
        }
        if (bottomBlock != null) {
          inner = KitBottomInset.add(extraBottom: blockHeight, child: inner);
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ?bar,
            // On the gutter rails like the rows below (KitSearchField.md
            // "Where it sits").
            if (search != null)
              centred(
                Padding(
                  padding: EdgeInsetsDirectional.symmetric(
                    horizontal: tokens.gutter,
                  ),
                  child: search,
                ),
              ),
            for (final row in header) centred(row),
            KitLoadingBar(loading: loading, label: loadingLabel),
            Expanded(child: centred(inner, fill: true)),
            ?bottomBlock,
          ],
        );
      },
    );
  }

  Widget _panes(BuildContext context) {
    final tokens = KitTokens.of(context);
    final hairline = ColoredBox(
      color: tokens.roles.hairline,
      child: SizedBox(width: KitTokens.hairlineWidth(context)),
    );
    final side = this.side;
    final showSide = side != null && showsSide(context);
    final hostsPane = KitNav.hostsPane(context);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (!hostsPane) ...[
          SizedBox(
            key: listPaneKey,
            width: KitLayout.paneListWidth,
            child: Semantics(
              container: true,
              identifier: '${paneSemanticsPrefix}list',
              child: _column(context, withBar: true),
            ),
          ),
          hairline,
        ],
        Expanded(
          child: _Pane(
            key: detailPaneKey,
            identifier: '${paneSemanticsPrefix}detail',
            child: _Centred(
              KitLayout.paneDetailMaxWidth,
              fill: true,
              child: detail ?? emptyDetail!,
            ),
          ),
        ),
        if (showSide) ...[
          hairline,
          SizedBox(
            width: KitLayout.paneSideWidth,
            child: _Pane(
              key: sidePaneKey,
              identifier: '${paneSemanticsPrefix}side',
              child: side,
            ),
          ),
        ],
      ],
    );
  }
}

/// Marks a KitScreen body ([pane] false) or a detail/side pane ([pane]
/// true): the nested-page assert and [KitScreen.inPane] read it.
class _KitScreenScope extends InheritedWidget {
  const _KitScreenScope({required this.pane, required super.child});

  final bool pane;

  @override
  bool updateShouldNotify(_KitScreenScope oldWidget) => pane != oldWidget.pane;
}

/// A detail or side pane: a semantics container, the pane scope, and its
/// own one-of-each check.
class _Pane extends StatelessWidget {
  const _Pane({super.key, required this.child, required this.identifier});

  final String identifier;

  final Widget child;

  @override
  Widget build(BuildContext context) => Semantics(
    container: true,
    identifier: identifier,
    child: _KitScreenScope(pane: true, child: _KitScreenCheck(child: child)),
  );
}

/// A page: the ground, the bar in the top safe area, and the content
/// resized above the keyboard.
class _PageFrame extends StatelessWidget {
  const _PageFrame({required this.topBar, required this.child});

  final KitTopBar? topBar;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final keyboard = MediaQuery.viewInsetsOf(context).bottom;
    final topBar = this.topBar;
    // One flat ground on every page, root tabs included (critique §4).
    return Material(
      color: tokens.roles.ground,
      child: SafeArea(
        bottom: false,
        child: Padding(
          padding: EdgeInsetsDirectional.only(bottom: keyboard),
          child: MediaQuery.removeViewInsets(
            context: context,
            removeBottom: true,
            child: topBar == null
                ? child
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      topBar,
                      Expanded(child: child),
                    ],
                  ),
          ),
        ),
      ),
    );
  }
}

/// A transparent [Scaffold] around a page so [ScaffoldMessenger] snack bars
/// have a place to draw. The snack bar floats above whatever
/// [KitBottomInset] publishes below the page (the floating dock, a pinned
/// primary, the gesture inset, the keyboard): only the Scaffold sees that
/// clearance as its bottom padding; the page below it keeps its own
/// [MediaQuery]. The Scaffold resizes nothing and paints nothing.
class _SnackBarHost extends StatelessWidget {
  const _SnackBarHost({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    final clearance = KitBottomInset.of(context).bottom;
    final lifted = media.copyWith(
      padding: media.padding.copyWith(bottom: clearance),
      viewPadding: media.viewPadding.copyWith(bottom: clearance),
    );
    return MediaQuery(
      data: lifted,
      child: Scaffold(
        backgroundColor: Colors.transparent,
        resizeToAvoidBottomInset: false,
        body: MediaQuery(data: media, child: child),
      ),
    );
  }
}

/// Centres [child] at most [maxWidth] wide; [fill] keeps the full height.
class _Centred extends StatelessWidget {
  const _Centred(this.maxWidth, {required this.child, this.fill = false});

  final double maxWidth;
  final bool fill;
  final Widget child;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, box) => Align(
      alignment: AlignmentDirectional.topCenter,
      child: SizedBox(
        width: math.min(maxWidth, box.maxWidth),
        height: fill ? box.maxHeight : null,
        child: child,
      ),
    ),
  );
}

/// Measures the bottom block (without its lift) and hands its height to
/// [builder], which publishes it to the body through [KitBottomInset].
class _BottomBlockHost extends StatefulWidget {
  const _BottomBlockHost({required this.bottom, required this.builder});

  final Widget? bottom;
  final Widget Function(
    BuildContext context,
    double blockHeight,
    Widget? bottomBlock,
  )
  builder;

  @override
  State<_BottomBlockHost> createState() => _BottomBlockHostState();
}

class _BottomBlockHostState extends State<_BottomBlockHost> {
  double _height = 0;

  void _measured(double height) {
    if (height == _height || !mounted) return;
    setState(() => _height = height);
  }

  @override
  Widget build(BuildContext context) {
    final bottom = widget.bottom;
    return widget.builder(
      context,
      bottom == null ? 0 : _height,
      bottom == null ? null : _Measure(onHeight: _measured, child: bottom),
    );
  }
}

/// Reports its child's height after layout, minus the lift below it (the
/// block's bottom padding, read from its [Padding]).
class _Measure extends SingleChildRenderObjectWidget {
  const _Measure({required this.onHeight, required Widget super.child});

  final ValueChanged<double> onHeight;

  @override
  RenderObject createRenderObject(BuildContext context) =>
      _RenderMeasure(onHeight, _liftOf(child));

  @override
  void updateRenderObject(BuildContext context, _RenderMeasure renderObject) {
    renderObject
      ..onHeight = onHeight
      ..lift = _liftOf(child);
  }

  static double _liftOf(Widget? child) {
    if (child is Padding) {
      final padding = child.padding;
      if (padding is EdgeInsetsDirectional) return padding.bottom;
      if (padding is EdgeInsets) return padding.bottom;
    }
    return 0;
  }
}

class _RenderMeasure extends RenderProxyBox {
  _RenderMeasure(this.onHeight, this.lift);

  ValueChanged<double> onHeight;
  double lift;
  double? _reported;

  @override
  void performLayout() {
    super.performLayout();
    final height = math.max(0.0, size.height - lift);
    if (height == _reported) return;
    _reported = height;
    SchedulerBinding.instance.addPostFrameCallback((_) => onHeight(height));
  }
}

/// Debug only (G37, LAY-12, K2 §2.7): after each frame, the visible subtree
/// (skipping offstage and TickerMode-disabled parts, the state a [KitSwap]
/// is fading out, and nested panes, which check themselves) holds at most
/// one primary [KitButton], one drawn [KitStatusLine], one [KitRefresh] and
/// one [KitDetailsFold].
class _KitScreenCheck extends StatefulWidget {
  const _KitScreenCheck({required this.child});

  final Widget child;

  @override
  State<_KitScreenCheck> createState() => _KitScreenCheckState();
}

class _KitScreenCheckState extends State<_KitScreenCheck> {
  bool _scheduled = false;

  @override
  Widget build(BuildContext context) {
    assert(() {
      if (!_scheduled) {
        _scheduled = true;
        SchedulerBinding.instance.addPostFrameCallback((_) {
          _scheduled = false;
          if (mounted) _check();
        });
      }
      return true;
    }());
    return widget.child;
  }

  void _check() {
    var primaries = 0;
    var lines = 0;
    var refreshes = 0;
    var folds = 0;
    void visit(Element element) {
      final widget = element.widget;
      if (widget is Offstage && widget.offstage) return;
      if (widget is TickerMode && !widget.enabled) return;
      if (widget is Visibility && !widget.visible) return;
      if (widget is _KitScreenScope && widget.pane) return;
      // The state a KitSwap is fading out is on its way off the page.
      if (KitSwap.isLeaving(element)) return;
      if (widget is KitButton && widget.role == KitButtonRole.primary) {
        primaries++;
      } else if (widget is KitStatusLine) {
        lines++;
      } else if (widget is KitRefresh) {
        refreshes++;
      } else if (widget is KitDetailsFold) {
        folds++;
      }
      element.visitChildElements(visit);
    }

    context.visitChildElements(visit);
    assert(
      primaries <= 1,
      'KitScreen: $primaries visible primary buttons; one per screen '
      '(K2 §2.7, LAY-12)',
    );
    assert(
      lines <= 1,
      'KitScreen: $lines status lines drawn; one per window (KIT-35)',
    );
    assert(
      refreshes <= 1,
      'KitScreen: $refreshes KitRefresh; one per screen (LAY-12)',
    );
    assert(
      folds <= 1,
      'KitScreen: $folds KitDetailsFold; one per screen (LAY-12)',
    );
  }
}
