import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'kit_buttons.dart';
import 'kit_icon.dart';
import 'kit_icon_button.dart';
import 'kit_max_width.dart';
import 'kit_menu.dart';
import 'kit_motion.dart';
import 'kit_optical.dart';
import 'kit_row.dart';
import 'kit_status_line.dart';
import 'kit_status_slot.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_haptics.dart';
import 'motion/kit_motion_parts.dart';
import 'motion/kit_reveal.dart';

// KitMenuItem moved to kit_menu.dart (docs/ux-system/kit-api/KitMenu.md).
export 'kit_menu.dart' show KitMenuItem;

/// A [KitRow]'s leading icon with an optional current mark (design
/// standard §6, "state lives in the row"): the thing the person is using
/// right now (the server they are connected to) sits in a filled accent
/// circle, every other row keeps the plain muted icon. The row says the
/// same in words ("Connected · …") so the mark is never colour-only.
class KitRowIcon extends StatelessWidget {
  const KitRowIcon(
    this.icon, {
    super.key,
    this.current = false,
    this.color,
    this.mark,
  });

  final IconData icon;

  /// A bundled brand mark drawn in [icon]'s place (see [KitRow.icon]).
  final String? mark;

  /// The row is the one in use now.
  final bool current;

  /// The icon's colour when not current; muted by default.
  final Color? color;

  @override
  Widget build(BuildContext context) {
    if (!current) return KitRow.icon(context, icon, color: color, mark: mark);
    final tokens = KitTokens.of(context);
    // Flat lists (Solid): the one in use is the icon in the accent, with no
    // tile; the row's "Connected · " says it in words.
    if (KitSurfaceLook.flatLists(context)) {
      return KeyedSubtree(
        key: const ValueKey('kit-row-current-mark'),
        child: KitRow.icon(
          context,
          icon,
          color: tokens.roles.accent,
          mark: mark,
        ),
      );
    }
    return Container(
      key: const ValueKey('kit-row-current-mark'),
      width: tokens.iconTileSize,
      height: tokens.iconTileSize,
      decoration: BoxDecoration(
        color: tokens.roles.accent,
        borderRadius: BorderRadius.circular(tokens.iconTileRadius),
      ),
      child: mark == null
          ? Icon(
              icon,
              size: tokens.iconSize(context, tokens.smallIconSize),
              color: tokens.roles.onAccent,
            )
          : IconTheme(
              data: IconThemeData(color: tokens.roles.onAccent),
              child: KitIcon.mark(mark!, growsWithText: true),
            ),
    );
  }
}

/// The start of a row's supporting line that says it is the current one,
/// in the accent colour: "Connected · ".
TextSpan kitCurrentSpan(BuildContext context, String word) => TextSpan(
  text: '$word · ',
  // The same label role as KitNeedsYou.span, so both state words read alike.
  style: KitText.styleOf(context, KitTextRole.label, tone: KitTextTone.accent),
);

/// The start of a row's supporting line that marks the one choice to take
/// when unsure, in the accent colour: "Recommended · ". The word carries the
/// meaning, never the colour alone (STATE-9).
TextSpan kitRecommendedSpan(BuildContext context) => kitCurrentSpan(
  context,
  lookupAppLocalizations(Localizations.localeOf(context)).kitChoiceRecommended,
);

/// The one overflow button (⋮): opens [items] with [showKitMenu]. For a top
/// bar or a section; a row's menu is KitRow.menu (no per-row ⋮, KIT-28).
/// With [enabled] false or no [items] the button is not shown (STATE-8):
/// a dead ⋮ is never drawn.
///
/// States: default; focused; hidden when disabled or empty.
class KitRowMenu extends StatelessWidget {
  const KitRowMenu({
    super.key,
    required this.items,
    this.tooltip,
    this.enabled = true,
    this.menuLabel,
    this.menuKey,
  });

  final List<KitMenuItem> items;

  /// The button's name; "More" (l10n.kitMore) by default.
  final String? tooltip;
  final bool enabled;

  /// The opened menu's semantic name ("Conversation actions").
  final String? menuLabel;

  /// Passed to [showKitMenu].
  final Key? menuKey;

  /// The same menu without the button: what KitRow and KitTappable call on
  /// long-press, right-click, Shift+F10 and the context-menu key.
  static Future<KitMenuItem?> show(
    BuildContext context,
    List<KitMenuItem> items, {
    Offset? position,
    String? menuLabel,
    Key? menuKey,
  }) => showKitMenu(
    context,
    items: items,
    position: position,
    semanticsLabel: menuLabel,
    menuKey: menuKey,
  );

  @override
  Widget build(BuildContext context) {
    if (!enabled || items.isEmpty) return const SizedBox.shrink();
    return Builder(
      // The button's own context, so the menu anchors to the button.
      builder: (button) => KitIconButton(
        key: const ValueKey('kit-row-menu-button'),
        icon: AppIconography.more,
        tooltip:
            tooltip ??
            lookupAppLocalizations(Localizations.localeOf(context)).kitMore,
        onPressed: () =>
            show(button, items, menuLabel: menuLabel, menuKey: menuKey),
      ),
    );
  }
}

/// The trailing mark of a row that opens another screen.
class KitChevron extends StatelessWidget {
  const KitChevron({super.key});

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final size = tokens.iconSize(context, tokens.smallIconSize);
    final glyph = Icon(
      AppIconography.chevronRight,
      size: size,
      color: tokens.roles.text3,
    );
    // In a row, the caret's ink ends on the row's end rail (the gutter on
    // a page), not 6 dp short of it inside a centred target (impeccable
    // layout pass, 2026-10-28). Elsewhere it stays centred.
    final rail = KitRow.trailingRailOf(context);
    return SizedBox.square(
      dimension: tokens.minTarget,
      child: rail == null
          ? Center(child: glyph)
          : Align(
              alignment: AlignmentDirectional.centerEnd,
              child: Padding(
                padding: EdgeInsetsDirectional.only(
                  end: KitOptical.endPad(
                    context,
                    AppIconography.chevronRight,
                    size,
                    rail,
                  ),
                ),
                child: glyph,
              ),
            ),
    );
  }
}

/// How long a risky switch stays on (kit-v2.md §2.6).
enum KitUntil {
  /// "Until I turn it off" (l10n.kitUntilOff).
  off,

  /// "For this conversation" (l10n.kitUntilConversation).
  conversation,

  /// "For an hour" (l10n.kitUntilHour).
  hour;

  /// The words for this duration.
  String label(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return switch (this) {
      KitUntil.off => l10n.kitUntilOff,
      KitUntil.conversation => l10n.kitUntilConversation,
      KitUntil.hour => l10n.kitUntilHour,
    };
  }
}

/// A risky switch's scope (security vertical, KIT-30, SEC-9).
@immutable
class KitRisk {
  const KitRisk({
    required this.scope,
    required this.onLabel,
    required this.icon,
    required this.onUntil,
    this.until = const [KitUntil.off],
    this.current,
  }) : assert(until.length >= 1 && until.length <= 3);

  /// What turning it on covers: "Every conversation on this server".
  final String scope;

  /// The status-line words while on: "Auto-approve is on".
  final String onLabel;

  /// The switch's glyph, used on the status line (neutral tone).
  final IconData icon;

  /// The chosen duration, called before `onChanged(true)`.
  final ValueChanged<KitUntil> onUntil;

  /// The durations offered, 1 to 3.
  final List<KitUntil> until;

  /// While on: the duration in force, shown in the supporting line.
  final KitUntil? current;
}

/// A setting that is on or off, as a row (design standard §6): the title,
/// what it does in the supporting line (two lines allowed), and the switch
/// at the end. The whole row toggles.
///
/// - [onOpen]: the row is a thing that can be switched on and off (a
///   scheduled prompt): a tap on the row opens it, only the switch toggles,
///   and the two are separate targets for screen readers (the switch keeps
///   the title as its name). The title and the supporting line stay one
///   line each, so a list of them keeps one height.
///
/// - A null [onChanged] disables it: [disabledReason] becomes the visible
///   supporting line and the hint (STATE-8); nothing is dimmed.
/// - [risk]: turning it on first unfolds a step under the row that states
///   the scope and asks for how long; while on, the row puts its condition
///   on the screen's one status line with a "Turn off" action.
/// - [locked]: always on; no switch, the [locked] word at the end instead.
///
/// States: off; on; disabled with its reason; risk step unfolded; risk on;
/// locked.
class KitSwitchRow extends StatelessWidget {
  const KitSwitchRow({
    super.key,
    required this.title,
    required this.value,
    required this.onChanged,
    this.leading,
    this.supporting,
    this.supportingMaxLines = 2,
    this.switchKey,
    this.below,
    this.risk,
    this.locked,
    this.disabledReason,
    this.onOpen,
    this.openLabel,
  }) : assert(locked == null || risk == null),
       assert(onOpen == null || (risk == null && locked == null)),
       assert(locked == null || value, 'locked means always on');

  final String title;
  final bool value;
  final ValueChanged<bool>? onChanged;
  final Widget? leading;
  final String? supporting;

  /// How many lines the supporting line may take before it is cut.
  final int supportingMaxLines;
  final Key? switchKey;

  /// Under the supporting line: what the setting has done so far (a
  /// counter) or an error to act on.
  final Widget? below;

  /// Makes this a risky switch (KIT-30).
  final KitRisk? risk;

  /// Always on, with this word at the end ("Always included").
  final String? locked;

  /// Shown as the supporting line, and as the hint, when [onChanged] is
  /// null.
  final String? disabledReason;

  /// A tap on the row opens what it stands for; only the switch toggles.
  final VoidCallback? onOpen;

  /// What opening does ("Edit schedule"), read as the row's hint.
  final String? openLabel;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) => _KitSwitchRowBody(row: this);
}

class _KitSwitchRowBody extends StatefulWidget {
  const _KitSwitchRowBody({required this.row});

  final KitSwitchRow row;

  @override
  State<_KitSwitchRowBody> createState() => _KitSwitchRowBodyState();
}

class _KitSwitchRowBodyState extends State<_KitSwitchRowBody> {
  bool _step = false;
  final _scopeFocus = FocusNode(debugLabel: 'kit-switch-risk-scope');

  @override
  void didUpdateWidget(_KitSwitchRowBody oldWidget) {
    super.didUpdateWidget(oldWidget);
    // Turned on elsewhere, or no longer risky: the step has nothing to ask.
    if (_step && (widget.row.value || widget.row.risk == null)) {
      _step = false;
    }
  }

  @override
  void dispose() {
    _scopeFocus.dispose();
    super.dispose();
  }

  void _openStep() {
    setState(() => _step = true);
    // Focus moves to the scope sentence (Accessibility).
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && _step) _scopeFocus.requestFocus();
    });
  }

  void _fold() => setState(() => _step = false);

  void _choose(KitUntil until) {
    final row = widget.row;
    row.risk!.onUntil(until);
    row.onChanged?.call(true);
    _fold();
  }

  void _toggle() {
    final row = widget.row;
    final onChanged = row.onChanged;
    if (onChanged == null) return;
    if (row.risk != null && !row.value) {
      if (_step) {
        _fold();
      } else {
        _openStep();
      }
      return;
    }
    KitHaptics.select(context);
    onChanged(!row.value);
  }

  String? _supporting(BuildContext context) {
    final row = widget.row;
    if (row.onChanged == null && row.disabledReason != null) {
      return row.disabledReason;
    }
    final risk = row.risk;
    if (risk != null && row.value) {
      final current =
          risk.current ?? (risk.until.length == 1 ? risk.until.single : null);
      return [
        risk.scope,
        if (current != null) current.label(context),
        ?row.supporting,
      ].join(' · ');
    }
    return row.supporting;
  }

  Widget _locked(BuildContext context, KitTokens tokens) {
    final row = widget.row;
    return MergeSemantics(
      child: Semantics(
        toggled: true,
        enabled: false,
        // The word is on screen at the row's end, so it is in the label.
        child: KitRow(
          title: row.title,
          leading: row.leading,
          titleMaxLines: 2,
          supporting: row.supporting == null
              ? null
              : TextSpan(text: row.supporting),
          supportingMaxLines: row.supportingMaxLines,
          below: row.below,
          trailing: Padding(
            key: const ValueKey('kit-switch-locked'),
            padding: EdgeInsetsDirectional.only(
              start: tokens.space2,
              end: tokens.space3,
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  AppIconography.locked,
                  size: tokens.smallIconSize,
                  color: tokens.roles.text3,
                ),
                SizedBox(width: tokens.space1),
                Flexible(child: Text(row.locked!, style: tokens.rowValue)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _riskStep(BuildContext context, KitTokens tokens, KitRisk risk) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final gap = SizedBox(height: tokens.space2);
    return Padding(
      key: const ValueKey('kit-switch-risk-step'),
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        end: tokens.gutter,
        bottom: tokens.space3,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Focus(
            focusNode: _scopeFocus,
            child: Text(risk.scope, style: tokens.rowSupporting),
          ),
          SizedBox(height: tokens.space3),
          // Each duration acts on tap (KIT-25). KitChoiceList
          // (kit-KitChoiceList, D7) has not merged; full-width buttons
          // carry the same one-tap choice until it does.
          if (risk.until.length == 1)
            KitButton.secondary(
              label: l10n.kitRiskTurnOn,
              onPressed: () => _choose(risk.until.single),
            )
          else
            for (final (i, until) in risk.until.indexed) ...[
              if (i > 0) gap,
              KitButton.secondary(
                label: until.label(context),
                onPressed: () => _choose(until),
              ),
            ],
          gap,
          KitButton.tertiary(label: l10n.kitRiskNotNow, onPressed: _fold),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final row = widget.row;
    final tokens = KitTokens.of(context);
    if (row.locked != null) return _locked(context, tokens);
    final onChanged = row.onChanged;
    final supporting = _supporting(context);
    final disabledHint = onChanged == null ? row.disabledReason : null;
    final onOpen = row.onOpen;
    if (onOpen != null) {
      return Semantics(
        hint: row.openLabel,
        child: KitRow(
          title: row.title,
          leading: row.leading,
          supporting: supporting == null ? null : TextSpan(text: supporting),
          below: row.below,
          onTap: onOpen,
          trailing: Padding(
            padding: EdgeInsetsDirectional.only(end: tokens.space2),
            child: Semantics(
              label: row.title,
              hint: disabledHint,
              // Quiet: a row of these is a list, not a row of buttons; the
              // screen's one solid accent stays its main action.
              child: _KitSwitch(
                switchKey: row.switchKey,
                value: row.value,
                onChanged: onChanged == null
                    ? null
                    : (value) {
                        KitHaptics.select(context);
                        onChanged(value);
                      },
                quiet: true,
              ),
            ),
          ),
        ),
      );
    }
    Widget switchRow = MergeSemantics(
      child: Semantics(
        hint: disabledHint,
        child: KitRow(
          title: row.title,
          leading: row.leading,
          titleMaxLines: 2,
          supporting: supporting == null ? null : TextSpan(text: supporting),
          supportingMaxLines: row.supportingMaxLines,
          below: row.below,
          onTap: onChanged == null ? null : _toggle,
          trailing: Padding(
            padding: EdgeInsetsDirectional.only(end: tokens.space2),
            child: _KitSwitch(
              switchKey: row.switchKey,
              value: row.value,
              onChanged: onChanged == null ? null : (_) => _toggle(),
            ),
          ),
        ),
      ),
    );
    final risk = row.risk;
    if (risk == null) return switchRow;
    switchRow = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        switchRow,
        KitReveal(
          child: _step && !row.value ? _riskStep(context, tokens, risk) : null,
        ),
      ],
    );
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return KitStatusContribution(
      status: row.value
          ? KitStatus(
              kind: KitStatusKind.riskySwitch,
              id: 'risk:${row.switchKey ?? row.title}',
              icon: risk.icon,
              message: risk.onLabel,
              action: KitAction(
                label: l10n.kitRiskTurnOff,
                onPressed: onChanged == null ? null : () => onChanged(false),
              ),
            )
          : null,
      child: switchRow,
    );
  }
}

/// The switch at the end of a [KitSwitchRow]. Where the style answers the
/// switch with its own look (Solid, at its defaults and under every
/// Fine-tune knob, none of which names a switch) it keeps the platform
/// switch, exactly as it always was; any other look draws the same switch
/// from the style resolver: the track is a `toggle` surface (on: [value],
/// with the on fill) and the thumb a round `control`
/// (docs/design/design-schools/briefs/README.md, "How a part asks for the
/// style").
///
/// [quiet] is the switch of a row that opens on a tap: an accent thumb on a
/// faint accent track, so a list of them stays a list, not a row of main
/// actions.
class _KitSwitch extends StatelessWidget {
  const _KitSwitch({
    required this.value,
    required this.onChanged,
    this.switchKey,
    this.quiet = false,
  });

  final bool value;
  final ValueChanged<bool>? onChanged;
  final Key? switchKey;
  final bool quiet;

  @override
  Widget build(BuildContext context) {
    final roles = KitTokens.of(context).roles;
    final looks = _kitSwitchLooks(
      context,
      on: value,
      enabled: onChanged != null,
      quiet: quiet,
    );
    if (looks.own) {
      return Switch.adaptive(
        key: switchKey,
        value: value,
        onChanged: onChanged,
        activeThumbColor: quiet ? roles.accent : null,
        activeTrackColor: quiet ? roles.accent.withValues(alpha: 0.24) : null,
        trackOutlineColor: quiet
            ? WidgetStateProperty.resolveWith(
                (states) => states.contains(WidgetState.selected)
                    ? roles.accent.withValues(alpha: 0.5)
                    : null,
              )
            : null,
      );
    }
    return _KitStyledSwitch(
      key: switchKey,
      value: value,
      onChanged: onChanged,
      quiet: quiet,
    );
  }
}

/// The styled switch's track and thumb here, and whether the style
/// answered both with the part's own look ([own]: keep the platform
/// switch). The fills are those the platform switch takes from the theme
/// (app_theme.dart switchTheme), or the quiet accent pair: what Solid
/// paints, and the part's own colours the styles keep.
({Decoration track, Decoration thumb, bool own}) _kitSwitchLooks(
  BuildContext context, {
  required bool on,
  required bool enabled,
  required bool quiet,
}) {
  final roles = KitTokens.of(context).roles;
  final Color onFill = !enabled
      ? roles.text3.withValues(alpha: .35)
      : quiet
      ? Color.alphaBlend(roles.accent.withValues(alpha: .24), roles.surface1)
      : roles.accent;
  final trackColor = on ? onFill : roles.surface3;
  final Color thumbColor = !enabled
      ? (on ? roles.text2 : roles.text3)
      : on
      ? (quiet ? roles.accent : roles.onAccent)
      : roles.text2;
  final outline = on
      ? (quiet ? roles.accent.withValues(alpha: .5) : Colors.transparent)
      : roles.hairline;
  final trackOwn = KitSurfaceDecoration(
    borderRadius: KitSurfaceLook.stadium,
    fill: trackColor,
    edgeStart: outline,
    edgeEnd: outline,
    edgeWidth: outline.a > 0 ? 2 : 0,
  );
  final track = KitSurfaceLook.decorate(
    context,
    solid: trackOwn,
    role: KitSurfaceRole.toggle,
    level: KitSurfaceLevel.surface3,
    borderRadius: KitSurfaceLook.stadium,
    selected: on,
    color: on ? onFill : null,
  );
  // The thumb is a round control that keeps its own colour (it is what
  // says on or off); the style gives it its edge and depth.
  final thumbOwn = KitSurfaceDecoration(circle: true, fill: thumbColor);
  final thumb = KitSurfaceLook.decorate(
    context,
    solid: thumbOwn,
    role: KitSurfaceRole.control,
    level: KitSurfaceLevel.surface2,
    circle: true,
    color: thumbColor,
  );
  return (
    track: track,
    thumb: thumb,
    own: identical(track, trackOwn) && identical(thumb, thumbOwn),
  );
}

/// [_KitSwitch] drawn by the style: the platform switch's size (60 × 48,
/// a 52 × 32 track) so a row never reflows between styles, a 24 dp thumb
/// that slides over [KitMotion.quick] (at once under reduced motion), and
/// the same semantics, focus and keyboard toggle.
class _KitStyledSwitch extends StatefulWidget {
  const _KitStyledSwitch({
    super.key,
    required this.value,
    required this.onChanged,
    required this.quiet,
  });

  final bool value;
  final ValueChanged<bool>? onChanged;
  final bool quiet;

  static const size = Size(60, 48);
  static const track = Size(52, 32);
  static const thumb = 24.0;

  @override
  State<_KitStyledSwitch> createState() => _KitStyledSwitchState();
}

class _KitStyledSwitchState extends State<_KitStyledSwitch> {
  bool _focused = false;

  void _toggle() => widget.onChanged?.call(!widget.value);

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final on = widget.value;
    final enabled = widget.onChanged != null;
    final style = KitSurfaceLook.styleOf(context);
    final looks = _kitSwitchLooks(
      context,
      on: on,
      enabled: enabled,
      quiet: widget.quiet,
    );
    final trackLook = looks.track;
    final thumbLook = looks.thumb;
    final still = KitMotion.reduced(context);
    final radius = KitSurfaceDecoration.radiusOf(trackLook)?.topLeft.x ?? 0;
    final inset = (_KitStyledSwitch.track.height - _KitStyledSwitch.thumb) / 2;
    final Widget drawn = SizedBox.fromSize(
      size: _KitStyledSwitch.size,
      child: Center(
        child: CustomPaint(
          foregroundPainter: _focused
              ? _KitSwitchFocusPainter(
                  color: roles.accent,
                  width: KitTokens.focusRingWidth(context),
                  radius: radius,
                )
              : null,
          child: SizedBox.fromSize(
            size: _KitStyledSwitch.track,
            child: kitSurfaceBox(
              style: style,
              decoration: trackLook,
              child: Padding(
                padding: EdgeInsets.all(inset),
                child: AnimatedAlign(
                  duration: still ? Duration.zero : KitMotion.quick,
                  curve: KitMotion.enter,
                  alignment: on
                      ? AlignmentDirectional.centerEnd
                      : AlignmentDirectional.centerStart,
                  child: SizedBox.square(
                    dimension: _KitStyledSwitch.thumb,
                    child: kitSurfaceBox(
                      style: style,
                      decoration: thumbLook,
                      child: const SizedBox.expand(),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    return Semantics(
      toggled: on,
      enabled: enabled,
      onTap: enabled ? _toggle : null,
      child: FocusableActionDetector(
        enabled: enabled,
        mouseCursor: enabled ? SystemMouseCursors.click : MouseCursor.defer,
        onShowFocusHighlight: (value) {
          if (value != _focused) setState(() => _focused = value);
        },
        actions: <Type, Action<Intent>>{
          ActivateIntent: CallbackAction<ActivateIntent>(
            onInvoke: (_) {
              _toggle();
              return null;
            },
          ),
        },
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          excludeFromSemantics: true,
          onTap: enabled ? _toggle : null,
          child: drawn,
        ),
      ),
    );
  }
}

/// The keyboard focus ring just outside a styled switch's track, in its
/// corners (LOOK-21).
class _KitSwitchFocusPainter extends CustomPainter {
  _KitSwitchFocusPainter({
    required this.color,
    required this.width,
    required this.radius,
  });

  final Color color;
  final double width;
  final double radius;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = (Offset.zero & size).inflate(width / 2 + 1);
    final r = radius.clamp(0, rect.shortestSide / 2).toDouble();
    canvas.drawRRect(
      RRect.fromRectAndRadius(rect, Radius.circular(r == 0 ? 0 : r + 1)),
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = width
        ..color = color,
    );
  }

  @override
  bool shouldRepaint(_KitSwitchFocusPainter old) =>
      old.color != color || old.width != width || old.radius != radius;
}

/// A row that unfolds what it stands for in place (§6): a group of rows
/// (the built-in plugins) or a rare choice (other OpenCode versions). Its
/// chevron points down when folded and turns up when open, while the rows
/// unfold under it ([KitReveal], §10); reduced motion makes both instant.
///
/// Uncontrolled ([initiallyExpanded]), or controlled with [expanded] and
/// [onExpansionChanged]: then a tap only reports, and the row shows what
/// [expanded] says.
///
/// States: folded; open; focused; with a two-line supporting line.
class KitExpandRow extends StatefulWidget {
  const KitExpandRow({
    super.key,
    required this.title,
    required this.children,
    this.leading,
    this.supporting,
    this.initiallyExpanded = false,
    this.headerKey,
    this.expanded,
    this.onExpansionChanged,
    this.supportingMaxLines = 1,
    this.maintainState = false,
    this.titleMaxLines = 1,
    this.menu = const [],
  }) : assert(
         expanded == null || !initiallyExpanded,
         'controlled rows take expanded, not initiallyExpanded',
       );

  final String title;
  final Widget? leading;
  final InlineSpan? supporting;
  final List<Widget> children;
  final bool initiallyExpanded;

  /// The tappable header, for tests.
  final Key? headerKey;

  /// Non-null makes the row controlled.
  final bool? expanded;

  /// Called with the new state on every fold and unfold request.
  final ValueChanged<bool>? onExpansionChanged;

  /// 2 where the fold's line explains itself (§6).
  final int supportingMaxLines;

  /// One line by default (§6); 2 where the title names a failure in words
  /// ("Couldn't reach the server at the office") that one line would cut
  /// (slice-R4). From 1.3× text [KitRow] gives it two anyway.
  final int titleMaxLines;

  /// Keeps folded children alive (a form inside). The fold is then instant:
  /// the children stay mounted, only hidden.
  final bool maintainState;

  /// The header's long-press menu ([KitRow.menu]): actions on what the row
  /// stands for (a diff's file: Comment, Add to prompt, Copy).
  final List<KitMenuItem> menu;

  @override
  State<KitExpandRow> createState() => _KitExpandRowState();
}

class _KitExpandRowState extends State<KitExpandRow> {
  late bool _own = widget.initiallyExpanded;

  bool get _open => widget.expanded ?? _own;

  void _set(bool open) {
    if (open == _open) return;
    if (widget.expanded == null) setState(() => _own = open);
    widget.onExpansionChanged?.call(open);
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final open = _open;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final children = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: widget.children,
    );
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        CallbackShortcuts(
          // Right opens and Left closes (mirrored in RTL) when focused.
          bindings: {
            const SingleActivator(LogicalKeyboardKey.arrowRight): () =>
                _set(!rtl),
            const SingleActivator(LogicalKeyboardKey.arrowLeft): () =>
                _set(rtl),
          },
          child: Semantics(
            button: true,
            expanded: open,
            child: KitRow(
              key: widget.headerKey,
              title: widget.title,
              leading: widget.leading,
              supporting: widget.supporting,
              supportingMaxLines: widget.supportingMaxLines,
              titleMaxLines: widget.titleMaxLines,
              onTap: () => _set(!open),
              menu: widget.menu,
              // The caret's ink on the row's end rail, like KitChevron's.
              trailing: SizedBox.square(
                dimension: tokens.minTarget,
                child: Builder(
                  builder: (context) => Align(
                    alignment: AlignmentDirectional.centerEnd,
                    child: Padding(
                      padding: EdgeInsetsDirectional.only(
                        end: KitOptical.endPad(
                          context,
                          AppIconography.chevronDown,
                          tokens.smallIconSize,
                          KitRow.trailingRailOf(context) ?? tokens.space3,
                        ),
                      ),
                      child: KitSpin.chevron(expanded: open),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
        if (widget.maintainState)
          Visibility(visible: open, maintainState: true, child: children)
        else
          KitReveal(child: open ? children : null),
      ],
    );
  }
}
