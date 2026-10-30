/// A word that explains itself (docs/ux-system/kit-api/KitTerm.md, K2 §1.20):
/// tap, long-press or hover shows a short explanation anchored to the word,
/// with an optional "Learn more" into the Guide. Replaces a separate page
/// and the old 24 dp `InfoLabel` hit area (R12).
library;

import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/gestures.dart' show PointerEnterEvent, PointerExitEvent;
import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart'
    show CustomSemanticsAction, SemanticsService;
import 'package:flutter/services.dart'
    show HardwareKeyboard, KeyDownEvent, KeyEvent, LogicalKeyboardKey;

import '../../l10n/app_localizations.dart';
import 'kit_buttons.dart';
import 'kit_layout.dart';
import 'kit_motion.dart';
import 'kit_sheet.dart';
import 'kit_surface.dart' show kitSurfaceMaterial;
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

AppLocalizations _l10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// A term that explains itself (K2 §1.20). A standalone widget at least
/// 48×48 dp: use it as a label, a heading word or at the end of a line,
/// never in the middle of a paragraph (a paragraph says it in words).
///
/// States: none — open (the explanation shows), hovered and focused are
/// looks, shown in its gallery, not KIT-12 states.
class KitTerm extends StatelessWidget {
  const KitTerm(
    this.term, {
    super.key,
    required this.explanation,
    this.learnMore,
    this.role,
    this.termKey,
  });

  final String term;
  final String explanation;
  final KitAction? learnMore;

  /// The [KitText] role to match the text around it; null keeps the
  /// ambient [DefaultTextStyle].
  final KitTextRole? role;
  final Key? termKey;

  @override
  Widget build(BuildContext context) => _KitTermCore(
    termKey: termKey ?? const ValueKey('kit-term'),
    term: term,
    explanation: explanation,
    learnMore: learnMore,
    role: role,
  );
}

/// Shows [explanation] for [term] without a [KitTerm] on screen (a toolbar
/// button that explains "MCP"). The bubble anchors to
/// [context]'s render box; when it cannot fit, it opens as a sheet (see
/// [KitTerm]'s Presentation). Completes when it closes.
///
/// The bubble is its own transparent route, so the system back gesture and
/// Esc close it rather than the screen under it; a tap outside closes it
/// too.
///
/// [bubbleKey] goes on the bubble's panel, or on the sheet when it opens as
/// one (KIT-10), so a test can find the open explanation.
Future<void> showKitTerm(
  BuildContext context, {
  required String term,
  required String explanation,
  KitAction? learnMore,
  Key? bubbleKey,
}) async {
  if (_needsSheet(
    context,
    term: term,
    explanation: explanation,
    hasLearnMore: learnMore != null,
  )) {
    return _showKitTermSheet(
      context,
      term: term,
      explanation: explanation,
      learnMore: learnMore,
      sheetKey: bubbleKey,
    );
  }
  final navigator = Navigator.of(context);
  final overlayBox = navigator.overlay?.context.findRenderObject();
  final box = context.findRenderObject();
  final anchor =
      box is RenderBox &&
          box.hasSize &&
          box.attached &&
          overlayBox is RenderBox &&
          overlayBox.attached
      ? MatrixUtils.transformRect(
          box.getTransformTo(overlayBox),
          Offset.zero & box.size,
        )
      : Rect.zero;
  final direction = Directionality.of(context);
  _announce(context, term: term, explanation: explanation);
  await navigator.push(
    _KitTermRoute(
      panelKey: bubbleKey,
      anchor: anchor,
      term: term,
      explanation: explanation,
      learnMore: learnMore,
      barrierLabel: _l10n(context).kitTermClose,
      direction: direction,
      themes: InheritedTheme.capture(from: context, to: navigator.context),
      duration: KitMotion.reduced(context) ? Duration.zero : KitMotion.quick,
    ),
  );
}

/// The one polite announcement when the bubble opens (K2 §1.20: "the
/// bubble is announced as a polite live region once"): the term, then its
/// explanation, as the bubble's own node reads them.
void _announce(
  BuildContext context, {
  required String term,
  required String explanation,
}) {
  unawaited(
    SemanticsService.sendAnnouncement(
      View.of(context),
      '$term\n$explanation',
      Directionality.of(context),
    ),
  );
}

/// The bubble's width in a window [windowWidth] wide: the shared popover
/// width, less a gutter on each side on a narrow window.
double _bubbleWidth(double windowWidth, double gutter) =>
    math.max(0, math.min(KitLayout.popoverMaxWidth, windowWidth - gutter * 2));

/// Whether [explanation] would make the bubble taller than
/// [KitTokens.termBubbleMaxHeight] of the window, so it opens as a sheet.
bool _needsSheet(
  BuildContext context, {
  required String term,
  required String explanation,
  required bool hasLearnMore,
}) =>
    _estimatedBubbleHeight(
      context,
      KitTokens.of(context),
      term: term,
      explanation: explanation,
      hasLearnMore: hasLearnMore,
    ) >
    MediaQuery.sizeOf(context).height * KitTokens.termBubbleMaxHeight;

/// A rough height for the bubble at [tokens]' spacing, wrapped at the
/// width [_KitTermBubbleLayout] gives it: enough to decide bubble vs. sheet
/// before building either (KitTerm.md "Sheet (long text)").
double _estimatedBubbleHeight(
  BuildContext context,
  KitTokens tokens, {
  required String term,
  required String explanation,
  required bool hasLearnMore,
}) {
  final maxWidth = math.max(
    0.0,
    _bubbleWidth(MediaQuery.sizeOf(context).width, tokens.gutter) -
        tokens.space3 * 2,
  );
  double lineHeightOf(TextStyle style, String text) {
    final painter = TextPainter(
      text: TextSpan(text: text, style: style),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
    )..layout(maxWidth: maxWidth);
    final height = painter.height;
    painter.dispose();
    return height;
  }

  var height = tokens.space2 * 2;
  height += lineHeightOf(KitText.styleOf(context, KitTextRole.label), term);
  height += tokens.space1;
  height += lineHeightOf(
    KitText.styleOf(context, KitTextRole.secondary),
    explanation,
  );
  if (hasLearnMore) height += tokens.space2 + tokens.minTarget;
  return height;
}

/// The long-text fallback (KitTerm.md "Sheet (long text)"): the same
/// content as a sheet titled by the term, with no buttons of its own. Learn
/// more closes the sheet first, then runs, as it does in the bubble.
Future<void> _showKitTermSheet(
  BuildContext context, {
  required String term,
  required String explanation,
  required KitAction? learnMore,
  Key? sheetKey,
}) {
  BuildContext? sheetContext;
  final onLearnMore = learnMore?.onPressed;
  return showKitSheet<void>(
    context,
    title: term,
    height: KitSheetHeight.content,
    sheetKey: sheetKey ?? const ValueKey('kit-term-sheet'),
    tertiary: [
      if (learnMore != null)
        KitAction(
          key: learnMore.key ?? const ValueKey('kit-term-learn-more'),
          label: learnMore.label,
          icon: learnMore.icon,
          onPressed: onLearnMore == null
              ? null
              : () {
                  final inner = sheetContext;
                  if (inner != null && inner.mounted) KitSheet.close(inner);
                  onLearnMore();
                },
        ),
    ],
    body: (inner) {
      sheetContext = inner;
      return KitText(explanation, role: KitTextRole.secondary);
    },
  );
}

/// Places the bubble under [anchor] (above it when there is no room),
/// aligned to its start edge, and keeps it inside the window with at least
/// the gutter from each edge (KitTerm.md "Adaptive").
class _KitTermBubbleLayout extends SingleChildLayoutDelegate {
  const _KitTermBubbleLayout({
    required this.anchor,
    required this.gutter,
    required this.gap,
    required this.rtl,
  });

  /// The term's box in the overlay's coordinates.
  final Rect anchor;
  final double gutter;
  final double gap;
  final bool rtl;

  @override
  BoxConstraints getConstraintsForChild(BoxConstraints constraints) =>
      BoxConstraints(
        maxWidth: _bubbleWidth(constraints.maxWidth, gutter),
        maxHeight: math.max(0, constraints.maxHeight - gutter * 2),
      );

  @override
  Offset getPositionForChild(Size size, Size childSize) {
    final spaceBelow = size.height - gutter - anchor.bottom - gap;
    final spaceAbove = anchor.top - gap - gutter;
    final below = childSize.height <= spaceBelow || spaceBelow >= spaceAbove;
    final y = below ? anchor.bottom + gap : anchor.top - gap - childSize.height;
    final x = rtl ? anchor.right - childSize.width : anchor.left;
    return Offset(
      _clampInto(x, gutter, size.width - gutter - childSize.width),
      _clampInto(y, gutter, size.height - gutter - childSize.height),
    );
  }

  static double _clampInto(double value, double min, double max) =>
      max < min ? min : value.clamp(min, max);

  @override
  bool shouldRelayout(_KitTermBubbleLayout oldDelegate) =>
      anchor != oldDelegate.anchor ||
      gutter != oldDelegate.gutter ||
      gap != oldDelegate.gap ||
      rtl != oldDelegate.rtl;
}

/// The bubble's content: the term, its explanation and an optional "Learn
/// more", shared by [KitTerm]'s anchored bubble and the [showKitTerm]
/// route. One semantics node reads the term and the explanation and offers
/// the dismiss action ([AppLocalizations.kitTermClose]).
class _KitTermPanel extends StatelessWidget {
  const _KitTermPanel({
    super.key,
    required this.term,
    required this.explanation,
    required this.learnMore,
    required this.learnMoreFocusNode,
    required this.onLearnMore,
    required this.onDismiss,
  });

  final String term;
  final String explanation;
  final KitAction? learnMore;
  final FocusNode? learnMoreFocusNode;
  final VoidCallback onLearnMore;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = _l10n(context);
    return Semantics(
      container: true,
      onDismiss: onDismiss,
      customSemanticsActions: {
        CustomSemanticsAction(label: l10n.kitTermClose): onDismiss,
      },
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: KitLayout.popoverMaxWidth),
        // A floating piece: the style paints it.
        child: KeyedSubtree(
          key: const ValueKey('kit-term-bubble'),
          child: kitSurfaceMaterial(
            context,
            level: KitSurfaceLevel.surface2,
            role: KitSurfaceRole.floating,
            fill: roles.surface2,
            clip: false,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(tokens.popoverCorner),
              side: BorderSide(
                color: roles.hairline,
                width: KitTokens.hairlineWidth(context),
              ),
            ),
            child: Padding(
              padding: EdgeInsetsDirectional.symmetric(
                horizontal: tokens.space3,
                vertical: tokens.space2,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // The bubble's title is text1 (KitTerm.md Tokens), not the
                  // label role's own secondary tone.
                  KitText(
                    term,
                    role: KitTextRole.label,
                    tone: KitTextTone.primary,
                  ),
                  SizedBox(height: tokens.space1),
                  KitText(explanation, role: KitTextRole.secondary),
                  if (learnMore case final action?) ...[
                    SizedBox(height: tokens.space2),
                    Align(
                      alignment: AlignmentDirectional.centerStart,
                      child: Focus(
                        focusNode: learnMoreFocusNode,
                        child: KitButton.fromAction(
                          KitAction(
                            key: const ValueKey('kit-term-learn-more'),
                            label: action.label,
                            icon: action.icon,
                            onPressed: onLearnMore,
                          ),
                          role: KitButtonRole.tertiary,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// How [KitTerm] was opened: it decides how a mouse leaving it behaves and
/// where focus goes once it closes.
enum _OpenedBy { pointer, hover, keyboard }

class _KitTermCore extends StatefulWidget {
  const _KitTermCore({
    required this.term,
    required this.explanation,
    required this.learnMore,
    required this.role,
    required this.termKey,
  });

  final String term;
  final String explanation;
  final KitAction? learnMore;
  final KitTextRole? role;

  /// Placed on the interactive [Semantics] node itself (TEST-5), so
  /// `find.byKey` resolves to the render object that owns the term's
  /// semantics and hit-tests normally.
  final Key termKey;

  @override
  State<_KitTermCore> createState() => _KitTermCoreState();
}

class _KitTermCoreState extends State<_KitTermCore>
    with SingleTickerProviderStateMixin {
  final _overlay = OverlayPortalController();
  final _focusNode = FocusNode(debugLabel: 'kit-term');
  final _learnMoreFocusNode = FocusNode(debugLabel: 'kit-term-learn-more');

  /// The term and its bubble are one tap region: a tap on either is not a
  /// tap outside.
  final _tapGroup = Object();
  late final AnimationController _fade;
  Timer? _hoverTimer;
  Timer? _leaveTimer;
  bool _overTerm = false;
  bool _overBubble = false;
  bool _closing = false;
  bool _listening = false;
  _OpenedBy? _openedBy;
  ScrollPosition? _scrollPosition;
  ModalRoute<Object?>? _route;

  /// No spec states a value for the hover-open delay (KitTerm.md); this
  /// seam picks one short enough to feel responsive, long enough that a
  /// pointer passing over the word does not flash it open.
  static const _hoverDelay = Duration(milliseconds: 400);

  /// How long a hover-opened bubble waits once the mouse has left both the
  /// term and the bubble: long enough to cross the gap between them.
  static const _hoverLeaveGrace = Duration(milliseconds: 200);

  bool get _open => _overlay.isShowing && !_closing;

  @override
  void initState() {
    super.initState();
    _fade = AnimationController(vsync: this, duration: KitMotion.quick)
      ..addStatusListener(_onFadeStatus);
    _focusNode.addListener(_rebuild);
    FocusManager.instance.addHighlightModeListener(_onHighlightMode);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _route = ModalRoute.of(context);
  }

  @override
  void dispose() {
    _hoverTimer?.cancel();
    _leaveTimer?.cancel();
    _stopListening();
    FocusManager.instance.removeHighlightModeListener(_onHighlightMode);
    _focusNode.removeListener(_rebuild);
    _focusNode.dispose();
    _learnMoreFocusNode.dispose();
    _fade.dispose();
    super.dispose();
  }

  void _rebuild() {
    if (mounted) setState(() {});
  }

  void _onHighlightMode(FocusHighlightMode mode) => _rebuild();

  void _onFadeStatus(AnimationStatus status) {
    if (status != AnimationStatus.dismissed || !_closing || !mounted) return;
    _overlay.hide();
    setState(() {
      _closing = false;
      _openedBy = null;
    });
  }

  void _closeForScroll() => _close(returnFocus: false);

  /// Esc closes the bubble wherever focus is: a tap-opened bubble moves no
  /// focus (so a text field keeps its keyboard), and Esc still reaches it.
  bool _onHardwareKey(KeyEvent event) {
    if (event is! KeyDownEvent ||
        event.logicalKey != LogicalKeyboardKey.escape ||
        !_open ||
        !(_route?.isCurrent ?? true)) {
      return false;
    }
    _close();
    return true;
  }

  void _startListening() {
    if (_listening) return;
    _listening = true;
    _scrollPosition = Scrollable.maybeOf(context)?.position;
    _scrollPosition?.addListener(_closeForScroll);
    HardwareKeyboard.instance.addHandler(_onHardwareKey);
  }

  void _stopListening() {
    if (!_listening) return;
    _listening = false;
    _scrollPosition?.removeListener(_closeForScroll);
    _scrollPosition = null;
    HardwareKeyboard.instance.removeHandler(_onHardwareKey);
  }

  /// A tap or long-press. On a bubble the mouse opened by hovering, it keeps
  /// the bubble open once the mouse leaves instead of closing it.
  void _onPointerActivate() {
    if (_open && _openedBy == _OpenedBy.hover) {
      _leaveTimer?.cancel();
      setState(() => _openedBy = _OpenedBy.pointer);
      return;
    }
    _toggle(_OpenedBy.pointer);
  }

  void _toggle(_OpenedBy by) {
    if (_open) {
      _close();
    } else {
      _requestOpen(by);
    }
  }

  /// Decides between the bubble and the sheet (KitTerm.md "Sheet (long
  /// text)"): estimated ahead of building either, so the person never sees
  /// one flash before the other.
  void _requestOpen(_OpenedBy by) {
    _hoverTimer?.cancel();
    _leaveTimer?.cancel();
    if (_needsSheet(
      context,
      term: widget.term,
      explanation: widget.explanation,
      hasLearnMore: widget.learnMore != null,
    )) {
      unawaited(_openAsSheet(by));
      return;
    }
    final wasShowing = _overlay.isShowing;
    setState(() {
      _openedBy = by;
      _closing = false;
    });
    if (!wasShowing) _overlay.show();
    if (KitMotion.reduced(context)) {
      _fade.value = 1;
    } else {
      unawaited(_fade.forward());
    }
    _startListening();
    if (!wasShowing) {
      _announce(context, term: widget.term, explanation: widget.explanation);
    }
    if (by == _OpenedBy.keyboard && widget.learnMore != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && _open) _learnMoreFocusNode.requestFocus();
      });
    }
  }

  /// Closes the bubble. Focus goes back to the term only when the keyboard
  /// opened it or focus is inside it; a tap never moves focus.
  void _close({bool returnFocus = true}) {
    if (!_open) return;
    _hoverTimer?.cancel();
    _leaveTimer?.cancel();
    _stopListening();
    final focusBack =
        returnFocus &&
        (_openedBy == _OpenedBy.keyboard || _learnMoreFocusNode.hasFocus);
    if (KitMotion.reduced(context)) {
      _overlay.hide();
      setState(() => _openedBy = null);
      _fade.value = 0;
    } else {
      setState(() => _closing = true);
      unawaited(_fade.reverse());
    }
    if (focusBack) _focusNode.requestFocus();
  }

  Future<void> _openAsSheet(_OpenedBy by) async {
    await _showKitTermSheet(
      context,
      term: widget.term,
      explanation: widget.explanation,
      learnMore: widget.learnMore,
    );
    if (mounted && by == _OpenedBy.keyboard) _focusNode.requestFocus();
  }

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent) return KeyEventResult.ignored;
    final key = event.logicalKey;
    if (key == LogicalKeyboardKey.enter ||
        key == LogicalKeyboardKey.numpadEnter ||
        key == LogicalKeyboardKey.space) {
      _toggle(_OpenedBy.keyboard);
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  void _onLearnMore() {
    final action = widget.learnMore;
    _close();
    action?.onPressed?.call();
  }

  void _onTermEnter(PointerEnterEvent _) {
    if (!KitLayout.finePointer(context)) return;
    _leaveTimer?.cancel();
    setState(() => _overTerm = true);
    if (_open) return;
    _hoverTimer?.cancel();
    _hoverTimer = Timer(_hoverDelay, () {
      if (mounted && _overTerm && !_open) _requestOpen(_OpenedBy.hover);
    });
  }

  void _onTermExit(PointerExitEvent _) {
    _hoverTimer?.cancel();
    if (_overTerm) setState(() => _overTerm = false);
    _scheduleHoverClose();
  }

  void _onBubbleEnter(PointerEnterEvent _) {
    _overBubble = true;
    _leaveTimer?.cancel();
  }

  void _onBubbleExit(PointerExitEvent _) {
    _overBubble = false;
    _scheduleHoverClose();
  }

  /// The term and the bubble are one hover region: a hover-opened bubble
  /// closes only once the mouse has left both (after a short grace, so it
  /// can cross the gap between them).
  void _scheduleHoverClose() {
    if (!_open || _openedBy != _OpenedBy.hover) return;
    _leaveTimer?.cancel();
    _leaveTimer = Timer(_hoverLeaveGrace, () {
      if (mounted &&
          !_overTerm &&
          !_overBubble &&
          _open &&
          _openedBy == _OpenedBy.hover) {
        _close(returnFocus: false);
      }
    });
  }

  TextStyle _baseStyle(BuildContext context) {
    final role = widget.role;
    if (role == null) return DefaultTextStyle.of(context).style;
    return KitText.styleOf(context, role, tone: KitTextTone.primary);
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = _l10n(context);
    final base = _baseStyle(context);
    // The term itself is always text1 (K2 §1.20 Look), whatever role or
    // ambient style set its size and weight; only the underline is text2.
    final textStyle = base.copyWith(
      color: roles.text1,
      decoration: TextDecoration.underline,
      decorationColor: roles.text2,
      decorationStyle: TextDecorationStyle.dotted,
    );
    final hoverFill = roles.isDark ? roles.surface2 : roles.surface3;
    // The ring is for the keyboard only (KitTappable's rule): a tap never
    // draws it, and it stays while a keyboard-opened bubble is up.
    final showFocusRing =
        FocusManager.instance.highlightMode == FocusHighlightMode.traditional &&
        (_focusNode.hasFocus || (_open && _openedBy == _OpenedBy.keyboard));
    final rtl = Directionality.of(context) == TextDirection.rtl;

    final body = ConstrainedBox(
      constraints: BoxConstraints(
        minWidth: tokens.minTarget,
        minHeight: tokens.minTarget,
      ),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: _overTerm ? hoverFill : null,
          borderRadius: BorderRadius.circular(tokens.buttonRadius),
          border: showFocusRing
              ? Border.all(
                  color: roles.accent,
                  width: KitTokens.focusRingWidth(context),
                )
              : null,
        ),
        child: Padding(
          // Enough clearance that the focus ring and the hover fill never
          // sit hard against the glyph's own paint bounds.
          padding: EdgeInsetsDirectional.symmetric(
            horizontal: tokens.space2,
            vertical: tokens.space1,
          ),
          child: Align(
            alignment: AlignmentDirectional.center,
            widthFactor: 1,
            heightFactor: 1,
            child: Text(widget.term, style: textStyle),
          ),
        ),
      ),
    );

    return PopScope(
      canPop: !_open,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _close();
      },
      child: OverlayPortal.overlayChildLayoutBuilder(
        controller: _overlay,
        overlayChildBuilder: (overlayContext, info) {
          final anchor = MatrixUtils.transformRect(
            info.childPaintTransform,
            Offset.zero & info.childSize,
          );
          return CustomSingleChildLayout(
            delegate: _KitTermBubbleLayout(
              anchor: anchor,
              gutter: tokens.gutter,
              gap: tokens.space1,
              rtl: rtl,
            ),
            child: TapRegion(
              groupId: _tapGroup,
              onTapOutside: (_) => _close(returnFocus: false),
              child: MouseRegion(
                onEnter: _onBubbleEnter,
                onExit: _onBubbleExit,
                child: FadeTransition(
                  opacity: _fade,
                  child: _KitTermPanel(
                    term: widget.term,
                    explanation: widget.explanation,
                    learnMore: widget.learnMore,
                    learnMoreFocusNode: _learnMoreFocusNode,
                    onLearnMore: _onLearnMore,
                    onDismiss: _close,
                  ),
                ),
              ),
            ),
          );
        },
        child: TapRegion(
          groupId: _tapGroup,
          child: MouseRegion(
            onEnter: _onTermEnter,
            onExit: _onTermExit,
            child: Focus(
              focusNode: _focusNode,
              onKeyEvent: _onKey,
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: _onPointerActivate,
                onLongPress: _onPointerActivate,
                child: Semantics(
                  key: widget.termKey,
                  button: true,
                  label: widget.term,
                  hint: l10n.kitTermHint,
                  onTapHint: l10n.kitTermShow,
                  excludeSemantics: true,
                  child: body,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// The [showKitTerm] bubble as a transparent route: the same panel as
/// [KitTerm]'s bubble, placed once from [anchor] (there is no word on
/// screen to track). Back, Esc and a tap outside pop it; it fades on
/// [KitMotion.quick], instantly under reduced motion.
class _KitTermRoute extends PopupRoute<void> {
  _KitTermRoute({
    this.panelKey,
    required this.anchor,
    required this.term,
    required this.explanation,
    required this.learnMore,
    required this.barrierLabel,
    required this.direction,
    required this.themes,
    required Duration duration,
  }) : _duration = duration;

  final Key? panelKey;
  final Rect anchor;
  final String term;
  final String explanation;
  final KitAction? learnMore;
  final TextDirection direction;
  final CapturedThemes themes;
  final Duration _duration;

  @override
  final String barrierLabel;

  @override
  Color? get barrierColor => null;

  @override
  bool get barrierDismissible => true;

  @override
  Duration get transitionDuration => _duration;

  @override
  Duration get reverseTransitionDuration => _duration;

  @override
  Widget buildPage(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
  ) => themes.wrap(
    Directionality(
      textDirection: direction,
      child: Builder(
        builder: (context) {
          final tokens = KitTokens.of(context);
          return CustomSingleChildLayout(
            delegate: _KitTermBubbleLayout(
              anchor: anchor,
              gutter: tokens.gutter,
              gap: tokens.space1,
              rtl: direction == TextDirection.rtl,
            ),
            child: _KitTermPanel(
              key: panelKey,
              term: term,
              explanation: explanation,
              learnMore: learnMore,
              learnMoreFocusNode: null,
              onLearnMore: () {
                final action = learnMore;
                if (isCurrent) {
                  navigator?.pop();
                } else if (isActive) {
                  navigator?.removeRoute(this);
                }
                action?.onPressed?.call();
              },
              onDismiss: () {
                if (isCurrent) navigator?.pop();
              },
            ),
          );
        },
      ),
    ),
  );

  @override
  Widget buildTransitions(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) => FadeTransition(opacity: animation, child: child);
}
