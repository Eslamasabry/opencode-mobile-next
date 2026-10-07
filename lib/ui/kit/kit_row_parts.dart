import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'kit_buttons.dart';
import 'kit_icon_button.dart';
import 'kit_menu.dart';
import 'kit_row.dart';
import 'kit_status_line.dart';
import 'kit_status_slot.dart';
import 'kit_tokens.dart';
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
  const KitRowIcon(this.icon, {super.key, this.current = false, this.color});

  final IconData icon;

  /// The row is the one in use now.
  final bool current;

  /// The icon's colour when not current; muted by default.
  final Color? color;

  @override
  Widget build(BuildContext context) {
    if (!current) return KitRow.icon(context, icon, color: color);
    final tokens = KitTokens.of(context);
    return Container(
      key: const ValueKey('kit-row-current-mark'),
      width: tokens.iconTileSize,
      height: tokens.iconTileSize,
      decoration: BoxDecoration(
        color: tokens.roles.accent,
        borderRadius: BorderRadius.circular(tokens.iconTileRadius),
      ),
      child: Icon(
        icon,
        size: tokens.smallIconSize,
        color: tokens.roles.onAccent,
      ),
    );
  }
}

/// The start of a row's supporting line that says it is the current one,
/// in the accent colour: "Connected · ".
TextSpan kitCurrentSpan(BuildContext context, String word) => TextSpan(
  text: '$word · ',
  style: TextStyle(
    color: KitTokens.of(context).roles.accent,
    fontWeight: FontWeight.w600,
  ),
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
    return SizedBox.square(
      dimension: tokens.minTarget,
      child: Icon(
        AppIconography.chevronRight,
        size: tokens.smallIconSize,
        color: tokens.roles.text3,
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
    this.switchKey,
    this.below,
    this.risk,
    this.locked,
    this.disabledReason,
  }) : assert(locked == null || risk == null),
       assert(locked == null || value, 'locked means always on');

  final String title;
  final bool value;
  final ValueChanged<bool>? onChanged;
  final Widget? leading;
  final String? supporting;
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

  @override
  Widget build(BuildContext context) => _KitSwitchRowBody(row: this);
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
        hint: row.locked,
        child: KitRow(
          title: row.title,
          leading: row.leading,
          titleMaxLines: 2,
          supporting: row.supporting == null
              ? null
              : TextSpan(text: row.supporting),
          supportingMaxLines: 2,
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
    Widget switchRow = MergeSemantics(
      child: Semantics(
        hint: disabledHint,
        child: KitRow(
          title: row.title,
          leading: row.leading,
          titleMaxLines: 2,
          supporting: supporting == null ? null : TextSpan(text: supporting),
          supportingMaxLines: 2,
          below: row.below,
          onTap: onChanged == null ? null : _toggle,
          trailing: Padding(
            padding: EdgeInsetsDirectional.only(end: tokens.space2),
            child: Switch.adaptive(
              key: row.switchKey,
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
  Widget build(BuildContext context) {
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
              trailing: SizedBox.square(
                dimension: tokens.minTarget,
                child: Center(child: KitSpin.chevron(expanded: open)),
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
