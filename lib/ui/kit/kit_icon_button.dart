import 'dart:async';

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import 'kit_bidi.dart';
import 'kit_copy.dart';
import 'kit_ink_layer.dart';
import 'kit_layout.dart';
import 'kit_motion.dart';
import 'kit_surface_look.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// How a [KitIconButton]'s glyph answers a press, beyond the pressed fill.
enum KitIconMotion {
  /// The pressed fill only.
  none,

  /// The glyph turns a quarter (90°, [KitIconButton.turnDuration],
  /// ease-out) as the press opens a page, and turns back when that page
  /// is left and the button's screen is on top again. If nothing covered
  /// the screen, it turns back at once. No idle or looping movement; under
  /// reduced motion it does not turn. For the Settings gear.
  turn,
}

/// The one icon-only control (kit-v2.md §1.10).
///
/// States: disabled, working.
///
/// Its other looks (selected, copied, destructive, hover, focused) are not
/// KIT-12 states; they are covered by its gallery and contract test. The
/// frozen API line also lists selected and copied, which gate G4 rejects
/// (a PROC-20 contract problem in docs/qa/revamp-kit-KitIconButton-v2-*).
class KitIconButton extends StatefulWidget {
  const KitIconButton({
    super.key,
    required this.icon,
    String? tooltip,

    /// Retired by kit-KitIconButton-v2: use [tooltip]. Forwards to it.
    String? label,
    required this.onPressed,
    this.size = 24,
    this.destructive = false,
    this.working = false,
    this.selected,
    this.shortcut,
    this.disabledReason,
    this.motion = KitIconMotion.none,
    this.round = false,
    this.quiet = false,
  }) : assert(
         (tooltip ?? label ?? '').length > 0,
         'KitIconButton needs a non-empty tooltip (KIT-22)',
       ),
       assert(size == 20 || size == 22 || size == 24, 'LOOK-33 sizes only'),
       tooltip = tooltip ?? label,
       copyText = null,
       onCopyFailed = null,
       redact = true;

  /// Copies what [text] returns, read at tap time, through `KitCopy.copy`.
  /// The glyph turns into a check for `KitMotion.copiedHold`, and "Copied"
  /// is announced once per tap. It never shows a SnackBar.
  ///
  /// [redact] (default true) masks secrets on the way to the clipboard
  /// ([KitCopy.copy]); the person's own content (a message, a code block)
  /// passes false to copy verbatim (SEC-13).
  const KitIconButton.copy({
    super.key,
    required String Function() text,
    this.tooltip, // null: l10n.kitCopy ("Copy"); prefer a noun: "Copy command"
    this.size = 24,
    this.shortcut,
    this.redact = true,
    this.onCopyFailed,
    this.quiet = false,
  }) : copyText = text,
       motion = KitIconMotion.none,
       round = false,
       icon = AppIconography.copy,
       onPressed = null,
       destructive = false,
       working = false,
       selected = null,
       disabledReason = null;

  final IconData icon;

  /// What pressing it does, in the person's words: "Remove header". It is
  /// both the tooltip text and the semantic label. It is null only on
  /// [KitIconButton.copy], where it resolves to l10n.kitCopy.
  final String? tooltip;

  /// Null means disabled (KitButton-style dimming, never partial opacity).
  /// On [KitIconButton.copy] it is null, and the button is still enabled.
  final VoidCallback? onPressed;

  /// The glyph size: 20, 22 or 24 (LOOK-33); 24 by default. The touch
  /// target is 48 at every size.
  final double size;

  /// How the glyph answers a press ([KitIconMotion]); none by default.
  final KitIconMotion motion;

  /// The glyph sits on a circle of its own, the target's full 48: a resting
  /// surface the style draws (`control`, `circle: true`, surface2). Solid
  /// draws it like the shell's server pill beside it: surface2, the
  /// hairline and the one tight shadow. For the shell bar's search and
  /// Settings gear, which sit beside that pill (owner concepts,
  /// 2026-10-22).
  final bool round;

  /// The glyph in `text2` instead of `text1`: an act set among secondary
  /// words (a reply's Copy beside its model name), so it reads at their
  /// volume.
  final bool quiet;

  /// [KitIconMotion.turn]'s quarter turn.
  static const turnDuration = Duration(milliseconds: 200);

  /// Marks the act (the glyph stays neutral, never red). Use it only for an act that loses data or
  /// ends running work (LOOK-5); the act itself confirms or offers Undo
  /// (DATA-11).
  final bool destructive;

  /// This tap is in flight (at most a second or two). The glyph becomes the
  /// small spinner and taps are ignored. It is never lasting status
  /// (STATE-7).
  final bool working;

  /// A toggle (follow output, wrap lines). Null means it is not a toggle;
  /// true or false maps to toggled semantics.
  final bool? selected;

  /// The key combination that does the same thing, as the shortcuts help
  /// sheet writes it ("Ctrl+R"). It is shown after the label in the hover
  /// tooltip on a fine pointer. The button does not bind it; the shortcut
  /// layer does.
  final String? shortcut;

  /// Why it is disabled. It goes into the semantic hint and the tooltip.
  /// STATE-8 still requires the host to show it as visible text nearby, or
  /// to hide the button (Appendix A #19).
  final String? disabledReason;

  /// Set only by [KitIconButton.copy].
  final String Function()? copyText;

  /// Whether [KitIconButton.copy] masks secrets before copying (default
  /// true). Always true on a plain button, which copies nothing.
  final bool redact;

  /// On [KitIconButton.copy]: called with the text it tried to copy when the
  /// clipboard refused it. The glyph then stays the copy glyph (no check);
  /// the host says so in words and offers to try again.
  final ValueChanged<String>? onCopyFailed;

  @override
  State<KitIconButton> createState() => _KitIconButtonState();
}

class _KitIconButtonState extends State<KitIconButton>
    with SingleTickerProviderStateMixin {
  Timer? _copiedTimer;

  /// [KitIconMotion.turn]: 0 at rest, 1 a quarter turn.
  AnimationController? _turnController;
  AnimationController get _turn => _turnController ??= AnimationController(
    vsync: this,
    duration: KitIconButton.turnDuration,
  );
  bool _copied = false;
  bool _focused = false;
  bool _hovered = false;
  // The pressed fill answers a touch on the next frame (KitPressTracker);
  // the InkWell's own highlight waits for the tap-or-scroll timeout.
  late final _press = KitPressTracker(() {
    if (mounted) setState(() {});
  });

  bool get _isCopy => widget.copyText != null;

  @override
  void dispose() {
    _copiedTimer?.cancel();
    _turnController?.dispose();
    _press.dispose();
    super.dispose();
  }

  void _handlePointerTap() {
    _press.confirm();
    _handleTap();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (widget.motion != KitIconMotion.turn) return;
    // Back on top (the page it opened was left): the glyph turns back.
    final current = ModalRoute.isCurrentOf(context) ?? true;
    final turn = _turnController;
    if (current && turn != null && turn.value > 0 && !turn.isAnimating) {
      unawaited(turn.reverse());
    }
  }

  void _handleTap() {
    if (_isCopy) {
      final value = widget.copyText!();
      unawaited(_copy(value));
      return;
    }
    if (widget.motion == KitIconMotion.turn && !KitMotion.reduced(context)) {
      unawaited(
        _turn.forward().then((_) {
          // Nothing covered this screen: no page to come back from.
          if (mounted && (ModalRoute.isCurrentOf(context) ?? true)) {
            unawaited(_turn.reverse());
          }
        }),
      );
    }
    widget.onPressed?.call();
  }

  Future<void> _copy(String value) async {
    try {
      await KitCopy.copy(context, value, redact: widget.redact);
    } on Exception {
      // The clipboard refused (the platform denied it, or there is none):
      // no check, and the host says so in words. A host without words for
      // it keeps the error as before.
      final failed = widget.onCopyFailed;
      if (failed == null) rethrow;
      if (mounted) failed(value);
      return;
    }
    if (!mounted) return;
    setState(() => _copied = true);
    _copiedTimer?.cancel();
    _copiedTimer = Timer(KitMotion.copiedHold, () {
      if (mounted) setState(() => _copied = false);
    });
  }

  /// The button's own circle in Solid: the pressed fill, or [round]'s
  /// resting surface. Both reach the screen through the style resolver.
  static BoxDecoration _circle(
    Color? color, {
    BoxBorder? border,
    List<BoxShadow>? shadows,
  }) => BoxDecoration(
    color: color,
    shape: BoxShape.circle,
    border: border,
    boxShadow: shadows,
  );

  /// [solid] while it rests; held, the style's `stateLayer` for it.
  static Decoration _pressedLook(
    BuildContext context,
    Decoration solid, {
    required bool pressed,
  }) => pressed
      ? KitSurfaceLook.decorate(
          context,
          solid: solid,
          role: KitSurfaceRole.stateLayer,
          circle: true,
          pressed: true,
        )
      : solid;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final active = !widget.working && (_isCopy || widget.onPressed != null);
    final tooltipText = widget.tooltip ?? l10n.kitCopy;
    final still = KitMotion.reduced(context);

    final Color glyphColor;
    if (!active && !_isCopy) {
      glyphColor = roles.text3;
    } else if (widget.selected == true) {
      glyphColor = roles.accent;
    } else {
      glyphColor = widget.quiet ? roles.text2 : roles.text1;
    }

    final Widget glyph;
    if (widget.working) {
      glyph = still
          ? Icon(
              key: const ValueKey('kit-icon-button-working'),
              AppIconography.statusDot,
              size: widget.size,
              color: roles.accent,
            )
          : SizedBox.square(
              key: const ValueKey('kit-icon-button-working'),
              dimension: widget.size,
              child: CircularProgressIndicator(
                strokeWidth: KitTokens.spinnerStroke,
                color: roles.accent,
              ),
            );
    } else if (_copied) {
      glyph = Icon(
        key: const ValueKey('kit-icon-button-copied'),
        AppIconography.check,
        size: widget.size,
        color: glyphColor,
      );
    } else {
      // Keyed by the glyph, so a toggle that changes the icon (show and
      // hide, play and pause) cross-fades instead of jumping; a change of
      // its colour (selected, enabled) eases in place.
      glyph = KeyedSubtree(
        key: ValueKey(('kit-icon-button-icon', widget.icon)),
        child: TweenAnimationBuilder<Color?>(
          tween: ColorTween(end: glyphColor),
          duration: still ? Duration.zero : KitMotion.quick,
          curve: KitMotion.enter,
          builder: (context, color, _) => Icon(
            key: const ValueKey('kit-icon-button-icon'),
            widget.icon,
            size: widget.size,
            color: color,
          ),
        ),
      );
    }

    Widget swapped = AnimatedSwitcher(
      duration: still ? Duration.zero : KitMotion.quick,
      switchInCurve: KitMotion.enter,
      switchOutCurve: KitMotion.exit,
      child: glyph,
    );
    if (widget.motion == KitIconMotion.turn) {
      // Clockwise, or the other way in a right-to-left layout.
      final rtl = Directionality.of(context) == TextDirection.rtl;
      swapped = RotationTransition(
        key: const ValueKey('kit-icon-button-turn'),
        turns: Tween<double>(begin: 0, end: rtl ? -.25 : .25).animate(
          CurvedAnimation(
            parent: _turn,
            curve: KitMotion.enter,
            reverseCurve: KitMotion.enter.flipped,
          ),
        ),
        child: swapped,
      );
    }

    // The focus ring is always built and only its border switches: the
    // tree keeps the same shape whether or not the button is focused, so the
    // focused InkWell (and its FocusNode) is never rebuilt out from under the
    // keyboard (LAY-10, G14). It paints in the foreground, so a hover
    // fill on the Material below never covers it (§8.3).
    // On Android a press draws the native ripple over the circle (KitInk);
    // the flat pressed fill is for the other platforms and reduced motion.
    final filledPress = active && _press.shown && !KitInk.on(context);
    final target = SizedBox.square(
      dimension: tokens.minTarget,
      child: DecoratedBox(
        // Pressed is surface3; a hovered one under a mouse already
        // shows surface3 and steps back down to surface2, the
        // KitTappable rule for surface3. Selected is the accent glyph
        // alone: a filled circle read as a menu button. The pressed
        // fill is the style's state layer (a press sinks in Soft).
        decoration: _pressedLook(
          context,
          _circle(
            filledPress ? (_hovered ? roles.surface2 : roles.surface3) : null,
          ),
          pressed: filledPress,
        ),
        child: InkWell(
          onTap: active ? _handlePointerTap : null,
          onTapCancel: active ? _press.cancel : null,
          onHover: (value) {
            if (value != _hovered) setState(() => _hovered = value);
          },
          customBorder: const CircleBorder(),
          hoverColor: roles.surface3,
          focusColor: Colors.transparent,
          highlightColor: Colors.transparent,
          splashFactory: NoSplash.splashFactory,
          // InkWell reports every focus notification, also an unchanged
          // one (it re-reports when the button turns disabled); only a
          // real change rebuilds.
          onFocusChange: (value) {
            if (value != _focused) setState(() => _focused = value);
          },
          child: Center(child: swapped),
        ),
      ),
    );
    // The resting circle, under the press layer, from the style; outside
    // the press layer's clip, so its shadow is whole.
    Widget surface(Widget child) => !widget.round
        ? child
        : kitSurfaceBox(
            key: const ValueKey('kit-icon-button-round'),
            style: KitSurfaceLook.styleOf(context),
            decoration: KitSurfaceLook.decorate(
              context,
              solid: _circle(
                roles.surface2,
                border: Border.all(
                  color: roles.hairline,
                  width: KitTokens.hairlineWidth(context),
                ),
                shadows: tokens.floatingShadows(context),
              ),
              level: KitSurfaceLevel.surface2,
              role: KitSurfaceRole.control,
              circle: true,
              disabled: !active && !_isCopy,
            ),
            child: child,
          );

    final control = DecoratedBox(
      position: DecorationPosition.foreground,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: _focused
            ? Border.all(
                color: roles.accent,
                width: KitTokens.focusRingWidth(context),
              )
            : null,
      ),
      child: _press.listen(
        context: context,
        enabled: active,
        child: surface(
          Material(
            type: MaterialType.transparency,
            shape: const CircleBorder(),
            clipBehavior: Clip.antiAlias,
            child: KitInkLayer(
              key: _press.inkKey,
              shape: const CircleBorder(),
              color: glyphColor,
              child: target,
            ),
          ),
        ),
      ),
    );

    final String? hint = widget.working
        ? l10n.kitWorking
        : (!active ? widget.disabledReason : widget.shortcut);

    final spans = <InlineSpan>[
      TextSpan(
        text: tooltipText,
        style: KitText.styleOf(
          context,
          KitTextRole.secondary,
          tone: KitTextTone.primary,
        ),
      ),
    ];
    // The second span says why a disabled button is disabled (the same text
    // as the semantic hint), on any pointer; otherwise it is the shortcut,
    // on a fine pointer only.
    final reason = !active && !widget.working ? widget.disabledReason : null;
    final shortcut = widget.shortcut;
    if (reason != null) {
      spans
        ..add(const TextSpan(text: '\n'))
        ..add(
          TextSpan(
            text: reason,
            style: KitText.styleOf(
              context,
              KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
          ),
        );
    } else if (shortcut != null && KitLayout.finePointer(context)) {
      spans
        ..add(const TextSpan(text: '  '))
        ..add(
          TextSpan(
            text: KitBidi.ltr(shortcut),
            style: KitText.styleOf(
              context,
              KitTextRole.mono,
              tone: KitTextTone.secondary,
            ),
          ),
        );
    }

    // Semantics is the outermost widget so the button's own element resolves
    // straight to its RenderSemanticsAnnotations (TEST-5/TEST-1 tooling:
    // `tester.getSemantics(find.byType(KitIconButton))` walks the render
    // tree from the element's own render object outward, and a bare
    // Tooltip's MouseRegion in between would hide this node behind it).
    //
    // A container: the button is always its own node, never merged into a
    // tappable parent (a KitRow's trailing action). Merged, its tap and the
    // row's collided and left the row's tap on a node with no label.
    return Semantics(
      container: true,
      button: true,
      label: tooltipText,
      hint: hint,
      enabled: active,
      toggled: widget.selected,
      onTap: active ? _handleTap : null,
      excludeSemantics: true,
      child: Tooltip(
        richMessage: TextSpan(children: spans),
        excludeFromSemantics: true,
        // A long label at 2.0 text wraps inside the screen gutter instead
        // of running edge to edge (the framework's default margin is 0).
        margin: EdgeInsets.symmetric(horizontal: tokens.gutter),
        // A small floating piece in its own opaque fill, from the style
        // resolver (an ink box, a raise); Solid keeps its plain bubble.
        decoration: KitSurfaceLook.decorate(
          context,
          solid: BoxDecoration(
            color: tokens.panelSurface,
            borderRadius: BorderRadius.circular(tokens.popoverCorner),
          ),
          role: KitSurfaceRole.floating,
          color: tokens.panelSurface,
        ),
        child: control,
      ),
    );
  }
}
