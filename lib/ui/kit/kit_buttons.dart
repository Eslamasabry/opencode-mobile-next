import 'dart:async';

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'kit_bidi.dart';
import 'kit_copy.dart';
import 'kit_layout.dart';
import 'kit_motion.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

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
       neutral = false;

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
/// (neutral `text1` words; `danger` when destructive, `text3` when disabled, so
/// an enabled inline action never looks disabled), all at least 48 dp tall, 50 dp at full width, with
/// 14 dp corners. A destructive primary is the one `dangerFill` button,
/// used only inside a confirmation.
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
       disabledReason = null,
       copied = false,
       redact = true;

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
  }) : role = KitButtonRole.primary,
       calm = false,
       copyText = null,
       disabledReason = null,
       copied = false,
       redact = true;

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
       copyText = null,
       disabledReason = null,
       copied = false,
       redact = true;

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
       working = false,
       expand = false,
       copyText = null,
       disabledReason = null,
       copied = false,
       redact = true;

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
  });

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

  /// Set only through [fromAction] from [KitAction.disabledReason].
  final String? disabledReason;

  /// Set only through [fromAction] from [KitAction.redact].
  final bool redact;

  /// Set only by [_KitCopyButton]: keys the check icon `kit-action-copied`
  /// instead of `kit-button-icon` while a copy button shows it.
  final bool copied;

  @override
  Widget build(BuildContext context) {
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
    final Widget labelWidget = _KitButtonLabel(
      label: label,
      shortcut: shortcut,
      role: role,
      maxLines: maxLines,
    );
    Widget? leading;
    Widget child = labelWidget;
    if (role != KitButtonRole.tertiary && icon != null) {
      // The icon and the spinner share one slot and crossfade.
      leading = SizedBox.square(
        dimension: tokens.smallIconSize,
        child: AnimatedSwitcher(
          duration: still ? Duration.zero : KitMotion.quick,
          switchInCurve: KitMotion.enter,
          switchOutCurve: KitMotion.exit,
          child: working
              ? const _Spinner(key: ValueKey('kit-button-working'))
              : Icon(
                  icon,
                  key: copied ? _copiedKey : const ValueKey('kit-button-icon'),
                  size: tokens.smallIconSize,
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
        size: tokens.smallIconSize,
      );
    }
    final minimum = expand
        ? Size.fromHeight(tokens.buttonHeight)
        : Size(tokens.minTarget, tokens.minTarget);
    final shape = RoundedRectangleBorder(
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
    // LOOK-21, LAY-10: keyboard focus draws a ring inside the button's own
    // shape, in a colour that reads on its fill (accent on the quiet
    // buttons; the fill's own foreground on a filled primary).
    final ringColor = role != KitButtonRole.primary
        ? roles.accent
        : destructive
        ? roles.onDangerFill
        : roles.onAccent;
    final ring = WidgetStateProperty.resolveWith<BorderSide?>(
      (states) => states.contains(WidgetState.focused)
          ? BorderSide(
              color: ringColor,
              width: KitTokens.focusRingWidth(context),
            )
          : null,
    );
    // The pressed state layer is the button's own foreground at 16 %
    // (Material's pressed overlay is 10 %; without its ripple on top that
    // is too faint to read as an answer on a phone), painted under the
    // label from the tracker; Material's splash and its delayed pressed
    // highlight are off.
    final pressedLayer = switch (role) {
      KitButtonRole.primary =>
        destructive ? roles.onDangerFill : roles.onAccent,
      KitButtonRole.secondary => destructive ? roles.danger : roles.text1,
      KitButtonRole.tertiary => destructive ? roles.danger : roles.text1,
    }.withValues(alpha: 0.16);
    final showPressed = press.shown && onPressed != null && !working;
    ButtonStyle pressable(ButtonStyle style) => style.copyWith(
      side: ring,
      splashFactory: NoSplash.splashFactory,
      overlayColor: WidgetStateProperty.resolveWith(
        (states) =>
            states.contains(WidgetState.pressed) ? Colors.transparent : null,
      ),
      backgroundBuilder: (context, states, child) => DecoratedBox(
        decoration: ShapeDecoration(
          shape: shape,
          color: showPressed ? pressedLayer : Colors.transparent,
        ),
        child: child,
      ),
    );
    Widget result;
    switch (role) {
      case KitButtonRole.primary:
        // Destructive is primary only where the whole sheet or screen is
        // that one confirmed act (design standard §2): the one red fill.
        final style = pressable(
          FilledButton.styleFrom(
            minimumSize: minimum,
            shape: shape,
            elevation: 0,
            backgroundColor: destructive
                ? roles.dangerFill
                : calm && Theme.of(context).brightness == Brightness.dark
                // Calm (dark mode): the accent eased toward the ground.
                ? Color.lerp(roles.accent, roles.surface1, .32)
                : roles.accent,
            foregroundColor: destructive ? roles.onDangerFill : roles.onAccent,
            disabledBackgroundColor: roles.surface3,
            disabledForegroundColor: roles.text3,
          ),
        );
        result = leading == null
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
      case KitButtonRole.secondary:
        final style = pressable(
          FilledButton.styleFrom(
            minimumSize: minimum,
            shape: shape,
            elevation: 0,
            backgroundColor: roles.surface3,
            foregroundColor: destructive ? roles.danger : roles.text1,
            disabledBackgroundColor: roles.surface3,
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
            foregroundColor: destructive ? roles.danger : roles.text1,
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
      label: inFlight ? label : null,
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
  });

  final String label;
  final String? shortcut;
  final KitButtonRole role;
  final int maxLines;

  @override
  Widget build(BuildContext context) {
    final text = Text(
      label,
      maxLines: maxLines,
      overflow: TextOverflow.ellipsis,
      textAlign: TextAlign.center,
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
    dimension: KitTokens.of(context).smallIconSize,
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
  });

  final KitAction? primary;
  final KitAction? secondary;
  final List<KitAction> tertiary;

  /// The caller's own overflow widget, where "More" goes.
  /// Retired by kit-KitAction-v2: pass the extra actions in [tertiary] (the
  /// block builds "More" itself). Kept working (KIT-43).
  final Widget? menu;

  bool get isEmpty =>
      primary == null && secondary == null && tertiary.isEmpty && menu == null;

  @override
  Widget build(BuildContext context) {
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

/// The "More" overflow menu (kit-actions-more): a destructive action beyond
/// the first two tertiary actions renders last, after a divider (§2.7). A
/// copy action copies through [KitCopy]; a disabled action shows its
/// [KitAction.disabledReason] as a second, muted line and as its hint
/// (STATE-8).
Widget? _buildMore(
  BuildContext context,
  List<KitAction> overflow,
  KitTokens tokens,
) {
  if (overflow.isEmpty) return null;
  final order = [
    for (var i = 0; i < overflow.length; i++)
      if (!overflow[i].destructive) i,
    for (var i = 0; i < overflow.length; i++)
      if (overflow[i].destructive) i,
  ];
  final firstDestructive = order.indexWhere((i) => overflow[i].destructive);
  return PopupMenuButton<int>(
    key: const ValueKey('kit-actions-more'),
    tooltip: lookupAppLocalizations(Localizations.localeOf(context)).kitMore,
    icon: const Icon(AppIconography.more),
    onSelected: (index) {
      final action = overflow[index];
      final copyText = action.copyText;
      if (copyText != null) {
        KitCopy.copy(context, copyText(), redact: action.redact);
      } else {
        action.onPressed?.call();
      }
    },
    itemBuilder: (menuContext) => [
      for (var position = 0; position < order.length; position++) ...[
        if (position == firstDestructive) const PopupMenuDivider(),
        _moreItem(menuContext, overflow[order[position]], order[position]),
      ],
    ],
  );
}

/// One "More" item: its label in the kit's body role (danger when
/// destructive, `text3` when disabled) and, when disabled with a reason,
/// that reason under it.
PopupMenuItem<int> _moreItem(BuildContext context, KitAction action, int id) {
  final enabled = action.enabled;
  final reason = enabled ? null : action.disabledReason;
  final label = Text(
    action.label,
    style: KitText.styleOf(
      context,
      KitTextRole.body,
      tone: !enabled
          ? KitTextTone.tertiary
          : action.destructive
          ? KitTextTone.danger
          : KitTextTone.primary,
    ),
  );
  return PopupMenuItem<int>(
    key: action.key,
    value: id,
    enabled: enabled,
    child: reason == null
        ? label
        : Semantics(
            hint: reason,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [label, _KitActionReason(reason)],
            ),
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
