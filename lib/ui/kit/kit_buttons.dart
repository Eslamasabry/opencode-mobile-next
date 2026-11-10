import 'dart:async';

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'kit_bidi.dart';
import 'kit_copy.dart';
import 'kit_icon_button.dart';
import 'kit_ink_layer.dart';
import 'kit_layout.dart';
import 'kit_max_width.dart';
import 'kit_menu.dart';
import 'kit_motion.dart';
import 'kit_surface_look.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'style/kit_signal.dart' show KitTrack, kitSignalColors;
import 'style/kit_signal_slip.dart' show kitSignalInSlip;

/// The waiting hero's fill (a hero [KitButton] that cannot be pressed
/// yet): a quiet mint, the accent at 14 % over `surface1`, so the screen
/// keeps its one accent place before the action is ready.
Color kitHeroWaitingFill(ThemeRoles roles) =>
    Color.alphaBlend(roles.accent.withValues(alpha: .14), roles.surface1);

/// The waiting hero's words: the accent at 55 % over `surface1`, moved
/// toward `text1` only as far as it takes to read at 4.5:1 on
/// [kitHeroWaitingFill] (the disabled words' floor, `ThemeRoles.text3`).
/// Muted against the ready hero's accent face and its own words, so it
/// still reads as not ready. Memoised per role set.
Color kitHeroWaitingWords(ThemeRoles roles) => _heroWaitingWords[roles] ??= () {
  final under = roles.ground.withValues(alpha: 1);
  final surface = Color.alphaBlend(roles.surface1, under);
  return readableOn(
    Color.alphaBlend(roles.accent.withValues(alpha: .55), surface),
    [Color.alphaBlend(kitHeroWaitingFill(roles), under)],
    4.6,
    toward: roles.text1.withValues(alpha: 1),
  );
}();

final _heroWaitingWords = Expando<Color>('kitHeroWaitingWords');

/// One action a kit part can show. Its place (primary, secondary, tertiary,
/// menu) is decided by the slot it is put in, never by the caller styling a
/// button (design standard §2).
///
/// A disabled action ([onPressed] null) should carry [disabledReason]: the
/// block or stack that shows it renders that reason as a visible line and
/// as the button's semantic hint (STATE-8; kit-KitAction-v2). Strict-mode
/// assertion of this rule waits on the `KitAsserts` seam (KitAction.md, Open
/// question 1), which has not landed; until then this is enforced by
/// rendering, not by a debug assert.
@immutable
class KitAction {
  const KitAction({
    required this.label,
    required this.onPressed,
    this.icon,
    this.key,
    this.destructive = false,
    this.working = false,
    this.disabledReason,
    this.shortcut,
    this.calm = false,
    this.neutral = false,
    this.semanticsLabel,
    this.visible = true,
  }) : copyText = null,
       redact = true;

  /// A text action that copies (KIT-23): "Copy details", "Copy all". Runs
  /// [KitCopy.copy] with [text]'s value at tap time; the button shows a
  /// check and "Copied" for [KitMotion.copiedHold], announced once; never a
  /// SnackBar. [redact] (default true) masks secrets on the way to the
  /// clipboard; the person's own content passes false (SEC-13).
  const KitAction.copy({
    required this.label,
    required String Function() text,
    this.icon = AppIconography.copy,
    this.key,
    this.shortcut,
    this.redact = true,
  }) : copyText = text,
       onPressed = null,
       destructive = false,
       working = false,
       disabledReason = null,
       calm = false,
       neutral = false,
       semanticsLabel = null,
       visible = true;

  /// A verb naming what happens (COPY-8): "Delete conversation".
  final String label;

  /// Null disables it; then [disabledReason] should say why.
  final VoidCallback? onPressed;
  final IconData? icon;
  final Key? key;

  /// Loses data or ends running work (LOOK-5). Never primary outside a
  /// confirmation. The act confirms first or offers Undo per the app's
  /// undo-or-confirm table; this flag decides neither.
  final bool destructive;

  /// This action's own tap is in flight (a second or two); never lasting
  /// status (STATE-7). A working action is never shown disabled.
  final bool working;

  /// Why it cannot run now, in one short sentence: "Fill in the server
  /// address first." Rendered as one muted line under the button by
  /// [KitActionBlock] and [KitActionStack], and as the button's semantic
  /// hint.
  final String? disabledReason;

  /// The key combination that does the same ("Ctrl+Enter"), as the
  /// shortcuts help writes it. Shown after the label on a fine pointer
  /// from [KitWindow.expanded] up (visual language §5 "keyboard hints on
  /// buttons"). Display only; the shortcut layer binds it.
  final String? shortcut;

  /// A quieter primary fill in dark mode: the accent eased toward the
  /// sheet's ground, so a sheet's one main action does not glare at night.
  /// Light mode is unchanged. Only meaningful on a primary action.
  final bool calm;

  /// A control that is not the sheet's main act (Stop): drawn as a neutral
  /// secondary button even in the primary slot, never filled with the accent.
  final bool neutral;

  /// What a screen reader says instead of [label], when a compact label
  /// leans on the words around it: "Resume" on a line about the background
  /// connection is read as "Resume background connection" (COPY-8: the
  /// action still names its target).
  final String? semanticsLabel;

  /// False while the act has nothing to do right now (Collapse all steps
  /// with no step open). In a [KitTopBar] it leaves its slot with a short
  /// fade instead of vanishing, and never shows in the overflow.
  final bool visible;

  /// Set only by [KitAction.copy].
  final String Function()? copyText;

  /// Whether [KitAction.copy] masks secrets before copying (default true).
  final bool redact;

  /// True when it can be pressed: an [onPressed] or a copy.
  bool get enabled => onPressed != null || copyText != null;
}

enum KitButtonRole { primary, secondary, tertiary }

/// The only buttons a migrated screen uses (visual language §5): primary
/// (accent filled, `onAccent` words), secondary (`surface3`) and tertiary
/// (neutral `text1` words, also when destructive; `text3` when disabled, so
/// an enabled inline action never looks disabled), all at least 48 dp tall, 50 dp at full width, with
/// 14 dp corners. A destructive primary is the strong neutral
/// (`text1` fill, `ground` words), never red; used only inside a
/// confirmation, which is what makes it safe.
///
/// [working] swaps the icon for a small spinner while the button's own tap
/// is in flight (a second or two, e.g. creating a conversation): the icon
/// and the spinner crossfade over [KitMotion.quick], and a button without
/// an icon makes room for the spinner smoothly instead of jumping (§10).
/// It is never a status display: a state that lasts, like "Starting the
/// server…", is progress in a [KitStateView], not a disabled button (§2).
/// While [working] is true, taps are ignored: an enabled action keeps its
/// enabled fill (never read as disabled by sight, C21 f) and tells a screen
/// reader it cannot be pressed right now; a caller that passes
/// `onPressed: null` while working renders disabled, as before.
/// Under reduced motion the spinner holds still (MOT-7).
///
/// [shortcut] shows the key combination after the label on a fine pointer
/// from [KitWindow.expanded] up (kit-KitAction-v2), isolated left to right
/// ([KitBidi.ltr]). Keyboard focus draws a ring of
/// [KitTokens.focusRingWidth] (LOOK-21).
class KitButton extends StatelessWidget {
  const KitButton({
    super.key,
    required this.role,
    required this.label,
    required this.onPressed,
    this.icon,
    this.working = false,
    this.expand = true,
    this.destructive = false,
    this.maxLines = 2,
    this.shortcut,
  }) : copyText = null,
       calm = false,
       hero = false,
       disabledReason = null,
       copied = false,
       redact = true,
       semanticsLabel = null;

  const KitButton.primary({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.working = false,
    this.expand = true,
    this.maxLines = 2,
    this.destructive = false,
    this.shortcut,
    this.hero = false,
  }) : role = KitButtonRole.primary,
       calm = false,
       copyText = null,
       disabledReason = null,
       copied = false,
       redact = true,
       semanticsLabel = null;

  const KitButton.secondary({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.working = false,
    this.expand = true,
    this.destructive = false,
    this.maxLines = 2,
    this.shortcut,
  }) : role = KitButtonRole.secondary,
       calm = false,
       hero = false,
       copyText = null,
       disabledReason = null,
       copied = false,
       redact = true,
       semanticsLabel = null;

  const KitButton.tertiary({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.destructive = false,
    this.maxLines = 2,
    this.shortcut,
  }) : role = KitButtonRole.tertiary,
       calm = false,
       hero = false,
       working = false,
       expand = false,
       copyText = null,
       disabledReason = null,
       copied = false,
       redact = true,
       semanticsLabel = null;

  /// Used only by [fromAction] to carry what a plain [KitAction] cannot
  /// name as a public constructor parameter without widening the four
  /// constructors above (KIT-43 additive rule): the copy behaviour and the
  /// reason a disabled action names.
  const KitButton._derived({
    super.key,
    required this.role,
    required this.label,
    this.onPressed,
    this.icon,
    this.working = false,
    this.expand = true,
    this.destructive = false,
    this.maxLines = 2,
    this.shortcut,
    this.copyText,
    this.disabledReason,
    this.copied = false,
    this.redact = true,
    this.calm = false,
    this.semanticsLabel,
  }) : hero = false;

  factory KitButton.fromAction(
    KitAction action, {
    required KitButtonRole role,
    bool expand = true,
  }) => KitButton._derived(
    key: action.key,
    role: role == KitButtonRole.primary && action.neutral
        ? KitButtonRole.secondary
        : role,
    calm: action.calm,
    label: action.label,
    onPressed: action.onPressed,
    icon: action.icon,
    destructive: action.destructive,
    working: role != KitButtonRole.tertiary && action.working,
    expand: role != KitButtonRole.tertiary && expand,
    shortcut: action.shortcut,
    copyText: action.copyText,
    disabledReason: action.disabledReason,
    redact: action.redact,
    semanticsLabel: action.semanticsLabel,
  );

  /// A tertiary button's side padding; blocks pull the row back by it so
  /// the label lines up with the text above. Kept for callers (KIT-43); the
  /// kit itself reads [KitTokens.space2], which it equals.
  static const tertiaryInset = 8.0;

  final KitButtonRole role;
  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final bool working;
  final bool expand;
  final bool destructive;
  final int maxLines;
  final String? shortcut;

  /// Set only through [fromAction] from [KitAction.copyText].
  final String Function()? copyText;

  /// Set only through [fromAction] from [KitAction.calm].
  final bool calm;

  /// A screen's one hero action at the bottom (Start with Claude Code): a
  /// taller full-width pill. Primary only.
  final bool hero;

  /// Set only through [fromAction] from [KitAction.disabledReason].
  final String? disabledReason;

  /// Set only through [fromAction] from [KitAction.redact].
  final bool redact;

  /// Set only through [fromAction] from [KitAction.semanticsLabel].
  final String? semanticsLabel;

  /// Set only by [_KitCopyButton]: keys the check icon `kit-action-copied`
  /// instead of `kit-button-icon` while a copy button shows it.
  final bool copied;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.control(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final copyText = this.copyText;
    if (copyText != null) {
      return _KitCopyButton(
        role: role,
        label: label,
        icon: icon,
        shortcut: shortcut,
        expand: expand,
        maxLines: maxLines,
        copyText: copyText,
        redact: redact,
      );
    }
    // A touch shows the pressed state on the next frame (KitPressTracker),
    // not after Material's tap-or-scroll wait; a quick tap still shows it.
    //
    // The tracker's Listener is the outermost render object, so the
    // button's semantics are merged into one node here: the button's own
    // element resolves straight to its node (TEST-1/TEST-5 tooling:
    // `tester.getSemantics(find.byKey(...))` walks outward from the
    // element's render object), and a screen reader hears the same one
    // button as before.
    return MergeSemantics(
      child: _KitPressBuilder(
        enabled: onPressed != null && !working,
        builder: _buildButton,
      ),
    );
  }

  Widget _buildButton(BuildContext context, KitPressTracker press) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final still = KitMotion.reduced(context);
    final spoken = semanticsLabel;
    // A tertiary action's underline is the style's link underline
    // (KitStyleTokens.linkUnderline; by default as its line is drawn):
    // none in Solid and Soft; Brutal's 2 dp ink and Paper's rule
    // underline the words, in the words' own colour.
    final underline = role == KitButtonRole.tertiary && this.onPressed != null
        ? KitSurfaceLook.linkUnderline(context)
        : null;
    final Widget shownLabel = _KitButtonLabel(
      label: label,
      shortcut: shortcut,
      role: role,
      maxLines: maxLines,
      underline: underline,
    );
    final Widget labelWidget = spoken == null
        ? shownLabel
        : Semantics(label: spoken, excludeSemantics: true, child: shownLabel);
    Widget? leading;
    Widget child = labelWidget;
    if (role != KitButtonRole.tertiary && icon != null) {
      // The icon and the spinner share one slot and crossfade.
      leading = SizedBox.square(
        dimension: tokens.iconSize(context, tokens.smallIconSize),
        child: AnimatedSwitcher(
          duration: still ? Duration.zero : KitMotion.quick,
          switchInCurve: KitMotion.enter,
          switchOutCurve: KitMotion.exit,
          child: working
              ? const _Spinner(key: ValueKey('kit-button-working'))
              : Icon(
                  icon,
                  key: copied ? _copiedKey : const ValueKey('kit-button-icon'),
                  size: tokens.iconSize(context, tokens.smallIconSize),
                ),
        ),
      );
    } else if (role != KitButtonRole.tertiary) {
      // No icon: the spinner opens its own room before the words. (Under
      // reduced motion no AnimatedSize at all: at zero duration it would
      // re-dirty itself during layout.)
      final slot = AnimatedSwitcher(
        duration: still ? Duration.zero : KitMotion.quick,
        child: working
            ? Padding(
                key: const ValueKey('kit-button-working'),
                padding: EdgeInsetsDirectional.only(end: tokens.space2),
                child: const _Spinner(),
              )
            : const SizedBox.shrink(),
      );
      child = Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (still)
            slot
          else
            AnimatedSize(
              duration: KitMotion.quick,
              curve: KitMotion.enter,
              child: slot,
            ),
          Flexible(child: labelWidget),
        ],
      );
    } else if (working) {
      leading = const _Spinner();
    } else if (icon != null) {
      leading = Icon(
        icon,
        key: copied ? _copiedKey : null,
        size: tokens.iconSize(context, tokens.smallIconSize),
      );
    }
    final minimum = hero
        ? Size.fromHeight(tokens.minTarget + tokens.space2)
        : expand
        ? Size.fromHeight(tokens.buttonHeight)
        : Size(tokens.minTarget, tokens.minTarget);
    final OutlinedBorder shape = hero
        ? const StadiumBorder()
        : RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(tokens.buttonRadius),
          );
    // Taps never fire while the action's own tap is in flight (STATE-7):
    // an enabled callback becomes a no-op so the fill stays the enabled
    // colour (never partial opacity, C21 f) instead of reading as disabled.
    // A caller that passes null while working stays disabled, as before.
    final onPressed = this.onPressed;
    final inFlight = working && onPressed != null;
    final VoidCallback? effectiveOnPressed = inFlight
        ? () {}
        : onPressed == null
        ? null
        : () {
            press.confirm();
            onPressed();
          };
    final Color primaryFill = destructive
        ? roles.text1
        : calm && Theme.of(context).brightness == Brightness.dark
        // Calm (dark mode): the accent eased toward the ground.
        ? Color.lerp(roles.accent, roles.surface1, .32)!
        : roles.accent;
    // Hook H3: a style may say the words on its accent (Brutal: ink on
    // mint). The strong neutral keeps its ground words.
    final Color primaryWords = destructive
        ? roles.ground
        : KitSurfaceLook.wordsOn(
            context,
            fallback: roles.onAccent,
            color: primaryFill,
            pressed: press.shown,
            action: true,
          );
    // LOOK-21, LAY-10: keyboard focus draws a ring inside the button's own
    // shape, in a colour that reads on its fill (accent on the quiet
    // buttons; the fill's own foreground on a filled primary).
    final ringColor = role != KitButtonRole.primary
        ? roles.accent
        : primaryWords;
    final ring = WidgetStateProperty.resolveWith<BorderSide?>(
      (states) => states.contains(WidgetState.focused)
          ? BorderSide(
              color: ringColor,
              width: KitTokens.focusRingWidth(context),
            )
          : null,
    );
    // Off Android (or under reduced motion) the pressed state layer is the
    // button's own foreground at 16 % (Material's pressed overlay is 10 %;
    // without a ripple on top that is too faint to read as an answer on a
    // phone), painted under the label from the tracker. Material's own
    // splash and its delayed pressed highlight stay off everywhere: the
    // tracker's ripple layer answers on the next frame.
    final pressedLayer = switch (role) {
      KitButtonRole.primary => destructive ? roles.ground : roles.onAccent,
      KitButtonRole.secondary => roles.text1,
      KitButtonRole.tertiary => roles.text1,
    }.withValues(alpha: 0.16);
    final enabled = onPressed != null;
    // On Android the native ripple answers the press (KitInk), drawn over
    // the face in the button's own foreground; the flat pressed layer is
    // for the other platforms and reduced motion.
    final showPressed =
        press.shown && enabled && !working && !KitInk.on(context);
    final Color inkColor = role == KitButtonRole.primary
        ? primaryWords
        : roles.text1;
    // Solid: a hero that cannot be pressed yet is a plain disabled button
    // (surface3, text3), so the accent appears only once it can be pressed.
    // Every other style keeps its quiet mint wait.
    final heroWaits =
        hero && KitSurfaceLook.styleOf(context) != KitSurfaceStyle.solid;
    final heroDisabledFill = kitHeroWaitingFill(roles);
    // The style resolver (Settings › Appearance › Style): Solid at its
    // defaults keeps the button's own Material fills, exactly as always.
    // Any other look paints a filled button's fill as a control surface
    // under the label (the primary keeps its own fill as the face) and the
    // button's own fill turns clear. A tertiary button has no surface; its
    // style is its words and their underline.
    final surfaceStyle = KitSurfaceLook.styleOf(context);
    final plain = KitSurfaceLook.isPlain(context);
    final surfaced = !plain && role != KitButtonRole.tertiary;
    final Color ownFill = !enabled
        ? (heroWaits ? heroDisabledFill : roles.surface3)
        : role == KitButtonRole.primary
        ? primaryFill
        : roles.surface3;
    // The primary keeps its accent as the face, and the hero its quiet
    // mint while it waits; any other button, a disabled one too, is the
    // style's own control (its words say it is off).
    final ownFace =
        (enabled && role == KitButtonRole.primary) || (!enabled && heroWaits);
    // Signal: on a printed slip the quiet answer (Reject) is a raised key
    // too, in the slip's own key colour; elsewhere only the primary is.
    final Color? slipKey =
        enabled &&
            role == KitButtonRole.secondary &&
            KitTrack.on(context) &&
            kitSignalInSlip(context)
        ? kitSignalColors(context).key
        : null;
    final Decoration surface = surfaced
        ? KitSurfaceLook.decorate(
            context,
            solid: ShapeDecoration(shape: shape, color: ownFill),
            level: KitSurfaceLevel.surface3,
            role: KitSurfaceRole.control,
            color: ownFace ? ownFill : slipKey,
            pressed: showPressed,
            action: true,
            // A style may draw a control that cannot be used flat, so it
            // never reads as a secondary (its words already say it is off).
            disabled: !enabled,
          )
        : const KitSurfaceDecoration(fill: Colors.transparent);
    // Held: the style's `stateLayer`; on a face of its own (the accent)
    // asked with that fill, which keeps the button's own layer unless the
    // style answers it apart (KitStyleSpec.resolveFilledLayer), so the
    // accent stays the accent. Either sits inside the surface's edge, so a
    // press never greys an ink edge.
    final layer = ShapeDecoration(
      shape: shape,
      color: showPressed ? pressedLayer : Colors.transparent,
    );
    final Decoration pressedLook = showPressed
        ? KitSurfaceLook.decorate(
            context,
            solid: layer,
            role: KitSurfaceRole.stateLayer,
            color: ownFace ? ownFill : null,
            pressed: true,
          )
        : layer;
    final edge = switch (KitSurfaceDecoration.from(surface)) {
      final KitSurfaceDecoration d when d.edgeStart.a > 0 || d.edgeEnd.a > 0 =>
        d.edgeWidth,
      _ => 0.0,
    };
    ButtonStyle pressable(ButtonStyle style) => style.copyWith(
      side: ring,
      // The words' colour eases between enabled and disabled on the kit's
      // quick pace (at once under reduced motion), not Material's own.
      animationDuration: still ? Duration.zero : KitMotion.quick,
      splashFactory: NoSplash.splashFactory,
      overlayColor: WidgetStateProperty.resolveWith(
        (states) =>
            states.contains(WidgetState.pressed) ? Colors.transparent : null,
      ),
      backgroundBuilder: (context, states, child) => KitInkLayer(
        key: press.inkKey,
        shape: shape,
        color: inkColor,
        child: kitSurfaceBox(
          style: surfaceStyle,
          decoration: surface,
          child: edge == 0 || child == null
              ? DecoratedBox(decoration: pressedLook, child: child)
              : Stack(
                  fit: StackFit.passthrough,
                  children: [
                    Positioned.fill(
                      child: Padding(
                        padding: EdgeInsets.all(edge),
                        child: DecoratedBox(decoration: pressedLook),
                      ),
                    ),
                    child,
                  ],
                ),
        ),
      ),
    );
    Widget result;
    switch (role) {
      case KitButtonRole.primary:
        // Destructive is primary only where the whole sheet or screen is
        // that one confirmed act (design standard §2): the strong neutral fill, never red.
        // The hero waits in a quiet mint of its own, so the screen keeps
        // its one accent place before it can be pressed.
        final disabledFill = heroWaits ? heroDisabledFill : roles.surface3;
        Widget primary(Color? easedFill) {
          final style = pressable(
            FilledButton.styleFrom(
              minimumSize: minimum,
              shape: shape,
              elevation: 0,
              // Each slot keeps its own meaning; only the one in use eases.
              backgroundColor: surfaced
                  ? Colors.transparent
                  : enabled
                  ? easedFill ?? primaryFill
                  : primaryFill,
              foregroundColor: primaryWords,
              disabledBackgroundColor: surfaced
                  ? Colors.transparent
                  : enabled
                  ? disabledFill
                  : easedFill ?? disabledFill,
              disabledForegroundColor: heroWaits
                  ? kitHeroWaitingWords(roles)
                  : roles.text3,
            ),
          );
          return leading == null
              ? FilledButton(
                  onPressed: effectiveOnPressed,
                  style: style,
                  child: child,
                )
              : FilledButton.icon(
                  onPressed: effectiveOnPressed,
                  style: style,
                  icon: leading,
                  label: labelWidget,
                );
        }

        // Solid paints the fill through Material, which does not ease a
        // colour: the fill eases here between enabled and disabled (a Save
        // that wakes up as the form becomes valid). Other styles paint it
        // in their surface box, which already cross-fades. Always in the
        // tree, so a change never rebuilds the button under its focus.
        result = TweenAnimationBuilder<Color?>(
          tween: ColorTween(end: enabled ? primaryFill : disabledFill),
          duration: still || surfaced ? Duration.zero : KitMotion.quick,
          curve: KitMotion.enter,
          builder: (context, fill, _) => primary(fill),
        );
      case KitButtonRole.secondary:
        final style = pressable(
          FilledButton.styleFrom(
            minimumSize: minimum,
            shape: shape,
            elevation: 0,
            backgroundColor: surfaced ? Colors.transparent : roles.surface3,
            // Hook H3: a style may say the words' colour on its surface.
            foregroundColor: KitSurfaceLook.wordsOn(
              context,
              fallback: roles.text1,
              role: KitSurfaceRole.control,
              pressed: press.shown,
            ),
            disabledBackgroundColor: surfaced
                ? Colors.transparent
                : roles.surface3,
            disabledForegroundColor: roles.text3,
          ),
        );
        result = leading == null
            ? FilledButton.tonal(
                onPressed: effectiveOnPressed,
                style: style,
                child: child,
              )
            : FilledButton.tonalIcon(
                onPressed: effectiveOnPressed,
                style: style,
                icon: leading,
                label: labelWidget,
              );
      case KitButtonRole.tertiary:
        final style = pressable(
          TextButton.styleFrom(
            minimumSize: minimum,
            shape: shape,
            padding: EdgeInsets.symmetric(horizontal: tokens.space2),
            // Green is the one accent for the primary action only: a
            // tertiary (cancel, dismiss, inline) action is neutral text1,
            // which still reads apart from disabled text3 (R5).
            foregroundColor: roles.text1,
            disabledForegroundColor: roles.text3,
          ),
        );
        result = leading == null
            ? TextButton(
                onPressed: effectiveOnPressed,
                style: style,
                child: labelWidget,
              )
            : TextButton.icon(
                onPressed: effectiveOnPressed,
                style: style,
                icon: leading,
                label: labelWidget,
              );
    }
    // Honest while in flight: the button looks enabled but ignores taps,
    // so a screen reader hears it as not pressable now, not as an enabled
    // button that does nothing. The wrapper is always there (empty when
    // idle) so starting or finishing work keeps the same button element:
    // the icon and the spinner crossfade and the width eases, instead of a
    // rebuilt button jumping to its new state.
    result = Semantics(
      container: inFlight,
      button: inFlight ? true : null,
      enabled: inFlight ? false : null,
      label: inFlight ? (semanticsLabel ?? label) : null,
      excludeSemantics: inFlight,
      child: result,
    );
    final reason = disabledReason;
    if (reason != null && onPressed == null) {
      // STATE-8: a disabled control's reason is also its semantic hint.
      // MergeSemantics folds the hint into the button's own (boundary)
      // node instead of leaving it stranded on a separate one.
      result = MergeSemantics(
        child: Semantics(hint: reason, child: result),
      );
    }
    return result;
  }
}

/// A button's label, with [shortcut] appended on a fine pointer
/// (kit-KitAction-v2, visual language §5): mono, `text3` on a tertiary
/// button or the button's own foreground otherwise, isolated left to right.
class _KitButtonLabel extends StatelessWidget {
  const _KitButtonLabel({
    required this.label,
    required this.shortcut,
    required this.role,
    required this.maxLines,
    this.underline,
  });

  final String label;
  final String? shortcut;
  final KitButtonRole role;
  final int maxLines;

  /// The underline's thickness (a multiple of the face's own), in the
  /// words' colour; null draws none.
  final double? underline;

  @override
  Widget build(BuildContext context) {
    final underline = this.underline;
    // A Style with type of its own keeps its keys' firm 600 words (the
    // theme's button role is Solid's 500), and so does Arabic: the system
    // Arabic face at 500 reads too thin on a filled button (under 4.5:1 in
    // the contrast gate).
    final arabic = Localizations.maybeLocaleOf(context)?.languageCode == 'ar';
    final firm = KitText.firmType(context) || arabic ? FontWeight.w600 : null;
    final text = Text(
      label,
      maxLines: maxLines,
      overflow: TextOverflow.ellipsis,
      textAlign: TextAlign.center,
      style: underline == null
          ? (firm == null ? null : TextStyle(fontWeight: firm))
          : TextStyle(
              fontWeight: firm,
              decoration: TextDecoration.underline,
              decorationThickness: underline,
            ),
    );
    final shortcut = this.shortcut;
    // Visual language §5: on a fine pointer, from expanded up.
    if (shortcut == null ||
        !KitLayout.finePointer(context) ||
        !KitLayout.windowOf(context).isWide) {
      return text;
    }
    final tokens = KitTokens.of(context);
    final hintStyle = role == KitButtonRole.tertiary
        ? KitText.styleOf(context, KitTextRole.mono, tone: KitTextTone.tertiary)
        : KitText.styleFor(KitTextRole.mono);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Flexible(child: text),
        SizedBox(width: tokens.space1),
        KitBidi.ltrText(shortcut, style: hintStyle),
      ],
    );
  }
}

/// The small spinner a working button shows, in the button's own
/// foreground. Under reduced motion it holds still as a three-quarter arc
/// (MOT-7: no running ticker after one pump).
class _Spinner extends StatelessWidget {
  const _Spinner({super.key});

  @override
  Widget build(BuildContext context) => SizedBox.square(
    dimension: KitTokens.of(
      context,
    ).iconSize(context, KitTokens.of(context).smallIconSize),
    child: CircularProgressIndicator(
      // The kit's one small-spinner stroke, shared with KitStatusMark and
      // KitIconButton.
      strokeWidth: KitTokens.spinnerStroke,
      value: KitMotion.reduced(context) ? .75 : null,
      color: IconTheme.of(context).color,
      // No track on a button: the arc alone, so the still arc reads as
      // one (a track would close it into a ring).
      backgroundColor: Colors.transparent,
    ),
  );
}

/// Holds a [KitButton]'s press (KitPressTracker) and rebuilds the button
/// when it shows or clears; KitButton itself stays stateless.
class _KitPressBuilder extends StatefulWidget {
  const _KitPressBuilder({required this.enabled, required this.builder});

  final bool enabled;
  final Widget Function(BuildContext context, KitPressTracker press) builder;

  @override
  State<_KitPressBuilder> createState() => _KitPressBuilderState();
}

class _KitPressBuilderState extends State<_KitPressBuilder> {
  late final _press = KitPressTracker(() {
    if (mounted) setState(() {});
  });

  @override
  void dispose() {
    _press.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => _press.listen(
    context: context,
    enabled: widget.enabled,
    child: widget.builder(context, _press),
  );
}

/// The private stateful body of a [KitAction.copy] button (kit-KitAction-v2,
/// KIT-23): runs [KitCopy.copy] at tap time, then swaps its icon and label
/// for a check and "Copied" for [KitMotion.copiedHold] before reverting.
class _KitCopyButton extends StatefulWidget {
  const _KitCopyButton({
    required this.role,
    required this.label,
    required this.copyText,
    this.icon,
    this.shortcut,
    this.expand = true,
    this.maxLines = 2,
    this.redact = true,
  });

  final KitButtonRole role;
  final String label;
  final String Function() copyText;
  final bool redact;
  final IconData? icon;
  final String? shortcut;
  final bool expand;
  final int maxLines;

  @override
  State<_KitCopyButton> createState() => _KitCopyButtonState();
}

class _KitCopyButtonState extends State<_KitCopyButton> {
  Timer? _revert;
  bool _copied = false;

  @override
  void dispose() {
    _revert?.cancel();
    super.dispose();
  }

  Future<void> _handleTap() async {
    await KitCopy.copy(context, widget.copyText(), redact: widget.redact);
    if (!mounted) return;
    setState(() => _copied = true);
    _revert?.cancel();
    _revert = Timer(KitMotion.copiedHold, () {
      if (mounted) setState(() => _copied = false);
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    // The caller's key stays on the outer KitButton only (one widget per
    // key, so find.byKey and a GlobalKey both work).
    return KitButton._derived(
      role: widget.role,
      label: _copied ? l10n.kitCopied : widget.label,
      onPressed: _copied ? () {} : _handleTap,
      icon: _copied ? AppIconography.check : widget.icon,
      shortcut: widget.shortcut,
      expand: widget.expand,
      maxLines: widget.maxLines,
      copied: _copied,
    );
  }
}

/// The check icon shown while a copy button reads "Copied"; a distinct
/// value lets tests find `kit-action-copied` by key when the text alone
/// (also findable, per TEST-5) is ambiguous.
const _copiedKey = ValueKey('kit-action-copied');

/// A disabled action's reason (STATE-8, KitAction.md): one muted line,
/// found in tests by its text first (TEST-5). The key sits on the inner
/// [Text], the only child of this widget, so every line has the same key
/// without two keyed siblings. Kept identical to the copy in
/// kit_action_stack.dart (each file is its own library).
class _KitActionReason extends StatelessWidget {
  const _KitActionReason(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Text(
    text,
    key: const ValueKey('kit-action-reason'),
    style: KitTokens.of(context).note,
  );
}

/// A block of actions in the one hierarchy (design standard §2): primary,
/// then secondary, then up to two tertiary actions, with the rest in
/// "More" (kit-actions-more). A disabled action's [KitAction.disabledReason]
/// shows as a muted line under its button (STATE-8). A destructive tertiary
/// makes the whole block lay out as [KitActionStack] on every window
/// (LAY-14, §2.7), so a destructive act never sits beside a frequent one.
/// From [KitWindow.medium] up the block is one end-aligned row with the
/// primary at the end. (A short window keeps today's row: KitAction.md's
/// "short window stacks" waits on KitRequestCard, see the unit's QA record,
/// contract problem 4.)
class KitActionBlock extends StatelessWidget {
  const KitActionBlock({
    super.key,
    this.primary,
    this.secondary,
    this.tertiary = const [],
    this.menu,
    this.pair = false,
  });

  final KitAction? primary;
  final KitAction? secondary;
  final List<KitAction> tertiary;

  /// A primary with one short secondary beside it on a phone ("Send as a
  /// GitHub issue" · "Copy"): one row, the primary taking the room, the
  /// secondary its own width, while both fit at the text size; otherwise
  /// the usual stack. Only with exactly a primary and a secondary and no
  /// disabled reason; every other block, and every wider window, is
  /// unchanged.
  final bool pair;

  /// The caller's own overflow widget, where "More" goes.
  /// Retired by kit-KitAction-v2: pass the extra actions in [tertiary] (the
  /// block builds "More" itself). Kept working (KIT-43).
  final Widget? menu;

  bool get isEmpty =>
      primary == null && secondary == null && tertiary.isEmpty && menu == null;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    if (isEmpty) return const SizedBox.shrink();
    final tokens = KitTokens.of(context);
    final shown = tertiary.take(2).toList();
    final overflow = tertiary.skip(2).toList();
    final more = _buildMore(context, overflow, tokens);
    final hasDestructiveTertiary = tertiary.any((a) => a.destructive);
    // LAY-14, §2.7: a destructive tertiary forces the stacked layout, on
    // every window, so it is never beside a frequent action. PROC-20: the
    // spec's "a short window stacks" (rule 1, LAY-3) is left at today's
    // behaviour (the window's width decides) until KitRequestCard keeps its
    // actions inside its 45 % large-text cap; stacking there overflows it
    // (G6, 915x412 at text 2.0).
    final stacked =
        hasDestructiveTertiary ||
        KitLayout.windowOf(context) == KitWindow.compact;

    final first = primary;
    final second = secondary;
    if (pair &&
        stacked &&
        first != null &&
        second != null &&
        tertiary.isEmpty &&
        menu == null &&
        first.disabledReason == null &&
        second.disabledReason == null) {
      // Large text gets the stack: two labels side by side would wrap.
      final large = MediaQuery.textScalerOf(context).scale(16) > 16 * 1.3;
      return LayoutBuilder(
        builder: (context, constraints) {
          if (large || constraints.maxWidth < KitLayout.pairRowMinWidth) {
            return Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                KitButton.fromAction(first, role: KitButtonRole.primary),
                SizedBox(height: tokens.space2),
                KitButton.fromAction(second, role: KitButtonRole.secondary),
              ],
            );
          }
          return Row(
            children: [
              Expanded(
                child: KitButton.fromAction(first, role: KitButtonRole.primary),
              ),
              SizedBox(width: tokens.space2),
              KitButton.fromAction(
                second,
                role: KitButtonRole.secondary,
                expand: false,
              ),
            ],
          );
        },
      );
    }

    if (!stacked) {
      final row = Wrap(
        alignment: WrapAlignment.end,
        crossAxisAlignment: WrapCrossAlignment.center,
        spacing: tokens.space2,
        runSpacing: tokens.space2,
        children: [
          for (final action in shown)
            KitButton.fromAction(action, role: KitButtonRole.tertiary),
          ?more,
          ?menu,
          if (secondary case final secondary?)
            KitButton.fromAction(
              secondary,
              role: KitButtonRole.secondary,
              expand: false,
            ),
          if (primary case final primary?)
            KitButton.fromAction(
              primary,
              role: KitButtonRole.primary,
              expand: false,
            ),
        ],
      );
      // STATE-8: reasons collect under the row, end-aligned, in slot order.
      final reasons = [
        if (primary?.disabledReason case final reason?)
          _KitActionReason(reason),
        if (secondary?.disabledReason case final reason?)
          _KitActionReason(reason),
        for (final action in shown)
          if (action.disabledReason case final reason?)
            _KitActionReason(reason),
      ];
      if (reasons.isEmpty) return row;
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          row,
          Padding(
            padding: EdgeInsetsDirectional.only(top: tokens.space1),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                for (final reason in reasons)
                  Align(
                    alignment: AlignmentDirectional.centerEnd,
                    child: reason,
                  ),
              ],
            ),
          ),
        ],
      );
    }

    // Stacked (compact, a short window, or forced by a destructive
    // tertiary): primary, then secondary, full width, then the tertiary
    // actions. A non-forced stack still wraps up to two tertiary actions
    // side by side; a forced stack (or a reason to show) puts each tertiary
    // action, and its reason, on its own line so nothing sits beside a
    // destructive target (LAY-9).
    final tertiaryOneOnEachLine =
        hasDestructiveTertiary || shown.any((a) => a.disabledReason != null);
    final hasTertiaryLine = shown.isNotEmpty || more != null || menu != null;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (primary case final primary?) ...[
          KitButton.fromAction(primary, role: KitButtonRole.primary),
          if (primary.disabledReason case final reason?) ...[
            SizedBox(height: tokens.space1),
            _KitActionReason(reason),
          ],
        ],
        if (primary != null && secondary != null)
          SizedBox(height: tokens.space2),
        if (secondary case final secondary?) ...[
          KitButton.fromAction(secondary, role: KitButtonRole.secondary),
          if (secondary.disabledReason case final reason?) ...[
            SizedBox(height: tokens.space1),
            _KitActionReason(reason),
          ],
        ],
        if (hasTertiaryLine && (primary != null || secondary != null))
          SizedBox(height: tokens.space2),
        if (hasTertiaryLine)
          tertiaryOneOnEachLine
              ? Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    for (final action in shown) ...[
                      KitInset(
                        child: KitButton.fromAction(
                          action,
                          role: KitButtonRole.tertiary,
                        ),
                      ),
                      if (action.disabledReason case final reason?) ...[
                        SizedBox(height: tokens.space1),
                        _KitActionReason(reason),
                      ],
                      if (action != shown.last || more != null || menu != null)
                        SizedBox(height: tokens.space2),
                    ],
                    if (more case final more?) KitInset(child: more),
                    if (menu case final menu?) KitInset(child: menu),
                  ],
                )
              : KitInset(
                  child: Wrap(
                    crossAxisAlignment: WrapCrossAlignment.center,
                    spacing: tokens.space1,
                    children: [
                      for (final action in shown)
                        KitButton.fromAction(
                          action,
                          role: KitButtonRole.tertiary,
                        ),
                      ?more,
                      ?menu,
                    ],
                  ),
                ),
      ],
    );
  }
}

/// The "More" overflow menu (kit-actions-more): the actions beyond the
/// first two tertiary ones in a [KitMenu] (`showKitMenu`), so the menu takes
/// the person's style like every other menu. KitMenu puts a destructive
/// action last, after a divider (§2.7); a copy action copies through
/// [KitCopy] after the menu closes; a disabled action shows its
/// [KitAction.disabledReason] as a second, muted line (STATE-8).
Widget? _buildMore(
  BuildContext context,
  List<KitAction> overflow,
  KitTokens tokens,
) {
  if (overflow.isEmpty) return null;
  final items = [
    for (final action in overflow)
      if (action.copyText case final copyText?)
        KitMenuItem.copy(
          key: action.key,
          label: action.label,
          text: copyText,
          icon: action.icon,
          redact: action.redact,
        )
      else
        KitMenuItem(
          key: action.key,
          label: action.label,
          icon: action.icon,
          onSelected: action.onPressed ?? () {},
          enabled: action.enabled,
          disabledReason: action.enabled ? null : action.disabledReason,
          destructive: action.destructive,
        ),
  ];
  return Builder(
    // The button's own context, so the menu anchors to the button.
    builder: (button) => KitIconButton(
      key: const ValueKey('kit-actions-more'),
      icon: AppIconography.more,
      // The theme's own icon size, as the overflow glyph has always drawn.
      size: 22,
      tooltip: lookupAppLocalizations(Localizations.localeOf(context)).kitMore,
      onPressed: () => showKitMenu(button, items: items),
    ),
  );
}

/// Pulls a row of tertiary buttons back by their padding, so their labels
/// line up with the text above them (start edge, either direction).
class KitInset extends StatelessWidget {
  const KitInset({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    // The tertiary button's side padding (KitButton.tertiaryInset).
    final inset = KitTokens.of(context).space2;
    return Align(
      alignment: AlignmentDirectional.centerStart,
      child: Transform.translate(
        offset: Offset(
          Directionality.of(context) == TextDirection.rtl ? inset : -inset,
          0,
        ),
        child: child,
      ),
    );
  }
}
