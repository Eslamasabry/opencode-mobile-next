import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import 'kit_bidi.dart';
import 'kit_buttons.dart';
import 'kit_divider.dart';
import 'kit_field.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_optical.dart';
import 'kit_progress.dart';
import 'kit_receipt.dart';
import 'kit_row_parts.dart';
import 'kit_sheet.dart';
import 'kit_state_view.dart';
import 'kit_surface.dart' show kitSurfaceMaterial;
import 'kit_surface_look.dart' show KitSurfaceLook;
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_haptics.dart';
import 'motion/kit_reveal.dart';

/// One way to pick from a list (docs/ux-system/kit-api/KitChoiceList.md;
/// kit-v2.md §1.7; KIT-25, KIT-26, STATE-9, STATE-10, AUTO-8, MOT-11,
/// DATA-2).

/// One choice of a [KitChoiceList], a [KitChoiceRow] or a [KitPickerRow].
@immutable
class KitChoice<T> {
  const KitChoice({
    required this.value,
    required this.title,
    this.supporting,
    this.leading,
    this.recommended = false,
    this.enabled = true,
    this.disabledReason,
    this.key,
    this.menu = const [],
    this.menuLabel,
  }) : assert(enabled || disabledReason != null);

  final T value;

  /// The choice itself; wraps, never truncated (the choice is the decision).
  final String title;

  /// Under the title; wraps.
  final String? supporting;

  /// A kit mark only: `KitRow.icon`, `KitStatusMark`, `KitIcon`.
  final Widget? leading;

  /// Adds "Recommended" to the supporting line: words, not colour.
  final bool recommended;
  final bool enabled;

  /// Why a disabled choice cannot be chosen; shown on its supporting line.
  final String? disabledReason;

  /// The row's key.
  final Key? key;

  /// Actions on this one choice ("Download again", "Delete Balanced"),
  /// opened by the row's trailing "More" button, long-press, right-click,
  /// Shift+F10 and the context-menu key: an action lives on the choice it
  /// acts on, never below the list. Empty: no button.
  final List<KitMenuItem> menu;

  /// The opened menu's name ("Balanced actions").
  final String? menuLabel;
}

/// "Something else": a free answer below the options (a question's custom
/// answer). Its text is a draft of the request it answers.
@immutable
class KitChoiceOther {
  const KitChoiceOther({
    required this.label,
    required this.fieldLabel,
    required this.onSubmitted,
    this.draft,
    this.fieldKey,
  });

  /// The row that opens the field: "Something else".
  final String label;

  /// The field's label: "Your answer".
  final String fieldLabel;

  /// Sends the typed answer (trimmed, never empty).
  final ValueChanged<String> onSubmitted;

  /// Required inside a sheet (G48); keyed by the request.
  final KitDraft? draft;
  final Key? fieldKey;
}

/// A list of choices in one `surface1` panel.
///
/// States: default, loading (skeleton rows), empty (inline [KitStateView]
/// saying why), choice-disabled (reason on its supporting line), multi
/// (several checked), other (the "Something else" field open), sending (the
/// receipt says Sending), answered (the receipt says Sent or Answered),
/// answered-elsewhere (the receipt says "Answered on the laptop").
///
/// One tap, one callback (G9): a tap calls [KitChoiceList.single]'s
/// `onSelected` or [KitChoiceList.multi]'s `onChanged` exactly once. With
/// [sends], taps after the first are ignored until the host passes a new
/// [receipt] or selection, and while [receipt] says the answer is on its
/// way or landed.
///
/// Keyboard (§8.2): the list is one Tab stop, entered on the selected row
/// (or the first); Arrow Up and Down move focus without selecting; Home and
/// End jump; Space and Enter select (single) or toggle (multi).
class KitChoiceList<T> extends StatelessWidget {
  /// One answer. [actsOnTap] (default): a tap chooses and the host closes
  /// its sheet, or the answer is sent; there is no Apply. false: the tap
  /// only selects, and a form's primary sends everything (an OC2 form).
  /// [sends]: the tap sends the person's answer to the agent
  /// (KitHaptics.send, the receipt on the chosen row, further taps
  /// ignored until the host passes a new [receipt] or `selected`).
  const KitChoiceList.single({
    super.key,
    required List<KitChoice<T>> choices,
    required T? selected,
    required ValueChanged<T> onSelected,
    T? current,
    this.actsOnTap = true,
    this.sends = false,
    this.receipt,
    this.other,
    this.loading = false,
    this.empty,
    this.semanticsLabel,
  }) : _choices = choices,
       _selected = selected,
       _current = current,
       _selectedSet = const <Never>{},
       _onSelected = onSelected,
       _onChanged = null,
       _multi = false,
       showCount = false;

  /// Several answers. The host's primary applies them ("Use 3 sources").
  const KitChoiceList.multi({
    super.key,
    required List<KitChoice<T>> choices,
    required Set<T> selected,
    required ValueChanged<Set<T>> onChanged,
    this.loading = false,
    this.empty,
    this.semanticsLabel,
    this.showCount = true,
  }) : _choices = choices,
       _selected = null,
       _current = null,
       _selectedSet = selected,
       _onSelected = null,
       _onChanged = onChanged,
       _multi = true,
       actsOnTap = false,
       sends = false,
       receipt = null,
       other = null;

  final List<KitChoice<T>> _choices;
  final T? _selected;

  /// The value in use now (applied, installed, saved), when it can differ
  /// from the selection: a form whose primary applies, or a choice that
  /// must download first. "Current" shows on that row only while the
  /// selection differs from it (a pending change); the selected row's
  /// radio already says which one is chosen, so "Current" never repeats
  /// it. Null: no row says "Current".
  final T? _current;
  final Set<T> _selectedSet;
  final ValueChanged<T>? _onSelected;
  final ValueChanged<Set<T>>? _onChanged;
  final bool _multi;

  final bool actsOnTap;
  final bool sends;

  /// Shown on the chosen row once sent (STATE-10).
  final KitReceipt? receipt;
  final KitChoiceOther? other;

  /// Skeleton rows while the choices are fetched.
  final bool loading;

  /// Shown when there are no choices and [loading] is false: why, and the
  /// step. Required then.
  final KitStateView? empty;

  /// The group's name.
  final String? semanticsLabel;

  /// Multi: the "2 selected" line above the panel. False where the host's
  /// primary already names the count ("Save 2 files and send").
  final bool showCount;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) => _KitChoiceGroup<T>(list: this);
}

class _KitChoiceGroup<T> extends StatefulWidget {
  const _KitChoiceGroup({super.key, required this.list});

  final KitChoiceList<T> list;

  @override
  State<_KitChoiceGroup<T>> createState() => _KitChoiceGroupState<T>();
}

class _KitChoiceGroupState<T> extends State<_KitChoiceGroup<T>> {
  final List<FocusNode> _nodes = [];
  TextEditingController? _ownOther;
  bool _locked = false;
  bool _otherOpen = false;

  KitChoiceList<T> get _list => widget.list;

  @override
  void initState() {
    super.initState();
    final draft = _list.other?.draft;
    if (draft != null) {
      _otherOpen = draft.controller.text.isNotEmpty;
      if (!_otherOpen) unawaited(_restoreDraft(draft));
    }
  }

  Future<void> _restoreDraft(KitDraft draft) async {
    await draft.restore();
    if (mounted && draft.controller.text.isNotEmpty) {
      setState(() => _otherOpen = true);
    }
  }

  @override
  void didUpdateWidget(covariant _KitChoiceGroup<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    final before = oldWidget.list;
    if (before._selected != _list._selected ||
        before.receipt?.state != _list.receipt?.state ||
        (before.receipt == null) != (_list.receipt == null)) {
      _locked = false;
    }
  }

  @override
  void dispose() {
    for (final node in _nodes) {
      node.dispose();
    }
    _ownOther?.dispose();
    super.dispose();
  }

  void _syncNodes(int count) {
    while (_nodes.length < count) {
      _nodes.add(FocusNode(debugLabel: 'kit-choice-${_nodes.length}'));
    }
    while (_nodes.length > count) {
      _nodes.removeLast().dispose();
    }
  }

  /// The answer is on its way or landed: every row is inert.
  bool get _receiptHolds => switch (_list.receipt?.state) {
    KitReceiptState.sending ||
    KitReceiptState.sent ||
    KitReceiptState.confirmed ||
    KitReceiptState.answeredElsewhere => true,
    _ => false,
  };

  bool _mayAct() {
    if (!_list.sends) return true;
    if (_locked || _receiptHolds) return false;
    _locked = true;
    KitHaptics.send(context);
    return true;
  }

  void _tap(T value) {
    if (_list._multi) {
      final next = {..._list._selectedSet};
      if (!next.remove(value)) next.add(value);
      _list._onChanged!(next);
      return;
    }
    if (!_mayAct()) return;
    if (_otherOpen) setState(() => _otherOpen = false);
    _list._onSelected!(value);
  }

  void _submitOther() {
    final other = _list.other!;
    final controller = other.draft?.controller ?? _ownOther!;
    final text = controller.text.trim();
    if (text.isEmpty || !_mayAct()) return;
    other.onSubmitted(text);
  }

  KeyEventResult _onKey(FocusNode _, KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }
    final key = event.logicalKey;
    final focusable = [
      for (var i = 0; i < _nodes.length; i++)
        if (_nodes[i].canRequestFocus) i,
    ];
    if (focusable.isEmpty) return KeyEventResult.ignored;
    final at = _nodes.indexWhere((node) => node.hasPrimaryFocus);
    if (at < 0) return KeyEventResult.ignored;
    final position = focusable.indexOf(at);
    int? target;
    if (key == LogicalKeyboardKey.arrowDown) {
      target = focusable[(position + 1).clamp(0, focusable.length - 1)];
    } else if (key == LogicalKeyboardKey.arrowUp) {
      target = focusable[(position - 1).clamp(0, focusable.length - 1)];
    } else if (key == LogicalKeyboardKey.home) {
      target = focusable.first;
    } else if (key == LogicalKeyboardKey.end) {
      target = focusable.last;
    }
    if (target == null) return KeyEventResult.ignored;
    _nodes[target].requestFocus();
    return KeyEventResult.handled;
  }

  bool _isSelected(T value) => _list._multi
      ? _list._selectedSet.contains(value)
      : _list._selected == value && !_otherOpen;

  @override
  Widget build(BuildContext context) {
    final list = _list;
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final choices = list._choices;

    if (list.loading) return const KitSkeletonRows(count: 3);
    if (choices.isEmpty) {
      assert(
        list.empty != null,
        'KitChoiceList: pass `empty` (why there is nothing to choose) '
        'when choices is empty and loading is false',
      );
      return list.empty ?? const SizedBox.shrink();
    }

    final other = list.other;
    if (other != null && other.draft == null) {
      _ownOther ??= TextEditingController();
    }
    final rowCount = choices.length + (other == null ? 0 : 1);
    _syncNodes(rowCount);

    // The one Tab stop: the selected row, or the first that can be chosen.
    var entry = -1;
    for (var i = 0; i < choices.length; i++) {
      if (choices[i].enabled && _isSelected(choices[i].value)) {
        entry = i;
        break;
      }
    }
    if (entry < 0 && _otherOpen && other != null) entry = choices.length;
    if (entry < 0) entry = choices.indexWhere((choice) => choice.enabled);
    if (entry < 0 && other != null) entry = choices.length;

    // "Current" marks the applied value only while a different one is
    // selected (a pending change); never beside the radio that marks it.
    final current = list._current;
    final pending =
        !list._multi && !list.sends && current != null && !_isSelected(current);
    final mark = list._multi ? KitChoiceMark.check : KitChoiceMark.radio;
    final rows = <Widget>[
      for (var i = 0; i < choices.length; i++)
        _traversal(
          i == entry,
          KitChoiceRow<T>(
            key: choices[i].key,
            choice: choices[i],
            selected: _isSelected(choices[i].value),
            mark: mark,
            current: pending && choices[i].value == current,
            receipt: !list._multi && list._selected == choices[i].value
                ? list.receipt
                : null,
            focusNode: _nodes[i],
            onTap: choices[i].enabled ? () => _tap(choices[i].value) : null,
          ),
        ),
      if (other != null)
        _traversal(
          entry == choices.length,
          KitChoiceRow<Object?>(
            choice: KitChoice<Object?>(value: null, title: other.label),
            selected: _otherOpen,
            focusNode: _nodes[choices.length],
            onTap: () {
              if (list.sends && _receiptHolds) return;
              setState(() => _otherOpen = !_otherOpen);
            },
          ),
        ),
    ];

    // Flat lists (Solid): the rows sit on the page or sheet itself.
    Widget surface({required Widget child}) => KitSurfaceLook.flatLists(context)
        ? Material(type: MaterialType.transparency, child: child)
        : kitSurfaceMaterial(
            context,
            level: KitSurfaceLevel.surface1,
            fill: tokens.roles.surface1,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(tokens.panelCornerRadius),
            ),
            child: child,
          );
    final panel = surface(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var i = 0; i < rows.length; i++) ...[
            if (i > 0)
              Padding(
                padding: EdgeInsetsDirectional.only(
                  start:
                      tokens.gutter +
                      tokens
                          .iconSize(context, tokens.smallIconSize)
                          .roundToDouble() +
                      tokens.space3,
                ),
                child: const KitDivider(),
              ),
            rows[i],
          ],
        ],
      ),
    );

    Widget? field;
    if (other != null && _otherOpen) {
      final draft = other.draft;
      field = Padding(
        padding: EdgeInsets.only(top: tokens.space3),
        child: KitField(
          label: other.fieldLabel,
          kind: KitFieldKind.multiline,
          controller: draft == null ? _ownOther : null,
          draft: draft,
          fieldKey: other.fieldKey,
          textInputAction: TextInputAction.send,
          onSubmitted: (_) => _submitOther(),
          action: KitAction(
            label: l10n.kitChoiceOtherSend,
            icon: AppIconography.send,
            onPressed: _submitOther,
          ),
        ),
      );
    }

    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: list.semanticsLabel,
      child: Focus(
        canRequestFocus: false,
        skipTraversal: true,
        onKeyEvent: _onKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (list._multi && list.showCount)
              Padding(
                padding: EdgeInsetsDirectional.only(
                  start: tokens.space1,
                  bottom: tokens.space2,
                ),
                child: KitText(
                  l10n.kitChoiceSelectedCount(list._selectedSet.length),
                  role: KitTextRole.caption,
                  tone: KitTextTone.secondary,
                ),
              ),
            panel,
            if (other != null) KitReveal(child: field),
          ],
        ),
      ),
    );
  }

  /// Only the entry row is a Tab stop; the others are reached by arrows.
  Widget _traversal(bool entry, Widget row) => Focus(
    canRequestFocus: false,
    skipTraversal: true,
    descendantsAreTraversable: entry,
    child: row,
  );
}

/// The selection mark: a filled radio or a check box with a check.
enum KitChoiceMark { radio, check }

/// A row with a leading selection mark (a filled radio or a check) and, for
/// the value in use now while another one is selected, the word "Current"
/// first on its supporting line. Used by KitChoiceList, KitSegmented's
/// stacked form and the request card.
///
/// A non-empty [menu] (the choice's own, [KitChoice.menu], by default)
/// adds a trailing "More" button and opens on long-press, right-click,
/// Shift+F10 and the context-menu key.
///
/// States: default, selected, current (only when not selected), disabled
/// (reason on the supporting line), sending / answered (the [receipt]
/// under the words), with a menu, hovered, focused.
class KitChoiceRow<T> extends StatelessWidget {
  const KitChoiceRow({
    super.key,
    required this.choice,
    required this.selected,
    required this.onTap,
    this.mark = KitChoiceMark.radio,
    this.current = false,
    this.receipt,
    this.rowKey,
    this.focusNode,
    this.menu,
    this.menuLabel,
  });

  final KitChoice<T> choice;
  final bool selected;

  /// Null: disabled (the choice's reason shows).
  final VoidCallback? onTap;
  final KitChoiceMark mark;

  /// "Current · …" (STATE-9): this row is the value in use now. Shown only
  /// when the row is not [selected]: the selected mark already says it.
  final bool current;
  final KitReceipt? receipt;

  /// A test handle on the row's gesture and semantics node.
  final Key? rowKey;

  /// The row's focus node, for a list that moves focus with the arrows.
  final FocusNode? focusNode;

  /// Actions on this choice; [KitChoice.menu] when null.
  final List<KitMenuItem>? menu;

  /// The opened menu's name; [KitChoice.menuLabel] when null.
  final String? menuLabel;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final enabled = onTap != null && choice.enabled;
    final reason = choice.enabled ? null : choice.disabledReason;
    final supporting = [
      ?reason,
      if (choice.supporting case final text? when text.isNotEmpty) text,
      if (choice.recommended) l10n.kitChoiceRecommended,
    ].join(' · ');
    final showCurrent = current && !selected;
    final hasSupporting = showCurrent || supporting.isNotEmpty;
    final items = menu ?? choice.menu;
    final itemsLabel = menuLabel ?? choice.menuLabel;
    final secondary = KitText.styleOf(context, KitTextRole.secondary);

    final words = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          choice.title,
          style: tokens.rowTitle.copyWith(
            color: enabled ? roles.text1 : roles.text3,
          ),
        ),
        if (hasSupporting) ...[
          const SizedBox(height: KitTokens.rowLineGap),
          Text.rich(
            TextSpan(
              children: [
                if (showCurrent)
                  supporting.isEmpty
                      ? TextSpan(
                          text: l10n.kitChoiceCurrent,
                          style: TextStyle(
                            color: roles.accent,
                            fontWeight: FontWeight.w600,
                          ),
                        )
                      : kitCurrentSpan(context, l10n.kitChoiceCurrent),
                if (supporting.isNotEmpty) TextSpan(text: supporting),
              ],
            ),
            style: secondary.copyWith(color: roles.text2),
          ),
        ],
        if (receipt case final receipt?) ...[
          SizedBox(height: tokens.space1),
          receipt,
        ],
      ],
    );

    final body = ConstrainedBox(
      // KitTokens.choiceRowMinHeight (56) is not on the kit tokens yet
      // (_new-tokens.md); the two-line row height is the floor until then.
      constraints: BoxConstraints(minHeight: tokens.rowHeightTwoLine),
      child: Padding(
        padding: EdgeInsetsDirectional.fromSTEB(
          tokens.gutter,
          tokens.space2,
          items.isEmpty ? tokens.gutter : tokens.space1,
          tokens.space2,
        ),
        child: Row(
          children: [
            _KitChoiceMarkView(
              mark: mark,
              selected: selected,
              enabled: enabled,
            ),
            SizedBox(width: tokens.space3),
            if (choice.leading case final leading?) ...[
              leading,
              SizedBox(width: tokens.space3),
            ],
            Expanded(child: words),
          ],
        ),
      ),
    );

    final row = MergeSemantics(
      child: Semantics(
        checked: mark == KitChoiceMark.check ? selected : null,
        inMutuallyExclusiveGroup: mark == KitChoiceMark.radio ? true : null,
        child: KitTappable(
          onTap: enabled ? onTap : null,
          disabledReason: choice.disabledReason ?? '',
          selected: mark == KitChoiceMark.radio ? selected : null,
          focusNode: focusNode,
          tappableKey: rowKey,
          menu: enabled ? items : const [],
          child: body,
        ),
      ),
    );
    if (items.isEmpty) return row;
    // The choice's own actions sit on its row, after the words; the button
    // is its own node, outside the row's merged selection node.
    return Row(
      children: [
        Expanded(child: row),
        Padding(
          padding: EdgeInsetsDirectional.only(end: tokens.space1),
          child: KitRowMenu(items: items, menuLabel: itemsLabel),
        ),
      ],
    );
  }
}

class _KitChoiceMarkView extends StatelessWidget {
  const _KitChoiceMarkView({
    required this.mark,
    required this.selected,
    required this.enabled,
  });

  final KitChoiceMark mark;
  final bool selected;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final size = tokens.iconSize(context, tokens.smallIconSize).roundToDouble();
    final duration = KitMotion.reduced(context)
        ? Duration.zero
        : KitMotion.quick;
    final on = enabled ? roles.accent : roles.text3;
    final off = enabled ? roles.text2 : roles.text3;
    final ring = selected ? on : off;
    final radio = mark == KitChoiceMark.radio;
    final state = selected ? 'selected' : 'empty';
    return ExcludeSemantics(
      child: SizedBox.square(
        key: ValueKey('kit-choice-mark-${mark.name}-$state'),
        dimension: size,
        child: AnimatedContainer(
          duration: duration,
          curve: KitMotion.enter,
          decoration: BoxDecoration(
            shape: radio ? BoxShape.circle : BoxShape.rectangle,
            borderRadius: radio
                ? null
                : BorderRadius.circular(KitTokens.choiceMarkRadius),
            color: !radio && selected ? on : null,
            border: Border.all(color: ring, width: KitTokens.choiceMarkStroke),
          ),
          child: Center(
            child: AnimatedScale(
              scale: selected ? 1 : 0,
              duration: duration,
              curve: KitMotion.enter,
              child: radio
                  ? Container(
                      width: (size / 2).roundToDouble(),
                      height: (size / 2).roundToDouble(),
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: on,
                      ),
                    )
                  : Icon(
                      AppIconography.check,
                      size: size - 4,
                      color: roles.onAccent,
                    ),
            ),
          ),
        ),
      ),
    );
  }
}

/// A setting row that shows its value and opens a sheet with
/// KitChoiceList.single: "Language · English ›". Replaces DropdownButton.
/// With one choice it is a plain row showing that value, with no chevron
/// and no sheet (AUTO-8: a picker with one option is skipped).
///
/// States: default, single-option, disabled (reason on the supporting line).
class KitPickerRow<T> extends StatelessWidget {
  const KitPickerRow({
    super.key,
    required this.title,
    required this.choices,
    required this.selected,
    required this.onSelected,
    this.sheetTitle,
    this.leading,
    this.valueLabel,
    this.supporting,
    this.disabledReason,
    this.rowKey,
    this.sheetKey,
  }) : assert(onSelected != null || disabledReason != null);

  final String title;
  final List<KitChoice<T>> choices;
  final T? selected;

  /// Null: disabled ([disabledReason] required).
  final ValueChanged<T>? onSelected;

  /// The sheet's title; [title] by default.
  final String? sheetTitle;

  /// A kit mark.
  final Widget? leading;

  /// Overrides the shown value ("Follow the system").
  final String? valueLabel;
  final String? supporting;
  final String? disabledReason;
  final Key? rowKey;
  final Key? sheetKey;

  Future<void> _open(BuildContext context) async {
    final chosen = await showKitChoiceSheet<T>(
      context,
      title: sheetTitle ?? title,
      choices: choices,
      selected: selected,
      sheetKey: sheetKey,
    );
    if (chosen is T && context.mounted) onSelected?.call(chosen);
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    String? chosenTitle;
    for (final choice in choices) {
      if (choice.value == selected) chosenTitle = choice.title;
    }
    final value = valueLabel ?? chosenTitle ?? '';
    final disabled = onSelected == null;
    final picks = !disabled && choices.length > 1;
    // At large text the value moves under the title instead of truncating.
    final stacked = MediaQuery.textScalerOf(context).scale(1) >= 1.3;
    final secondary = KitText.styleOf(context, KitTextRole.secondary);
    final supportingText = disabled ? disabledReason : supporting;

    final words = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          title,
          style: tokens.rowTitle.copyWith(
            color: disabled ? roles.text3 : roles.text1,
          ),
        ),
        if (stacked && value.isNotEmpty) ...[
          const SizedBox(height: KitTokens.rowLineGap),
          Text(
            KitBidi.auto(value),
            style: tokens.rowValue.copyWith(color: roles.text3),
          ),
        ],
        if (supportingText != null && supportingText.isNotEmpty) ...[
          const SizedBox(height: KitTokens.rowLineGap),
          Text(supportingText, style: secondary.copyWith(color: roles.text2)),
        ],
      ],
    );

    final body = ConstrainedBox(
      constraints: BoxConstraints(minHeight: tokens.rowHeightTwoLine),
      child: Padding(
        // The caret's ink ends on the gutter, as a KitRow's chevron does,
        // so a picker row and the rows beside it share one end edge.
        padding: EdgeInsetsDirectional.fromSTEB(
          tokens.gutter,
          tokens.space2,
          picks
              ? KitOptical.endPad(
                  context,
                  AppIconography.chevronRight,
                  tokens.smallIconSize,
                  tokens.gutter,
                )
              : tokens.gutter,
          tokens.space2,
        ),
        child: LayoutBuilder(
          builder: (context, constraints) => Row(
            children: [
              if (leading case final leading?) ...[
                leading,
                SizedBox(width: tokens.space3),
              ],
              Expanded(child: words),
              if (!stacked && value.isNotEmpty)
                ConstrainedBox(
                  // The value takes at most half the row; the title the rest.
                  constraints: BoxConstraints(
                    maxWidth: (constraints.maxWidth / 2).floorToDouble(),
                  ),
                  child: Padding(
                    padding: EdgeInsetsDirectional.only(start: tokens.space3),
                    child: Text(
                      KitBidi.auto(value),
                      textAlign: TextAlign.end,
                      style: tokens.rowValue.copyWith(color: roles.text3),
                    ),
                  ),
                ),
              if (picks)
                Icon(
                  key: const ValueKey('kit-picker-chevron'),
                  AppIconography.chevronRight,
                  size: tokens.smallIconSize,
                  color: roles.text3,
                ),
            ],
          ),
        ),
      ),
    );

    if (!picks && !disabled) {
      return MergeSemantics(
        child: Semantics(key: rowKey, container: true, child: body),
      );
    }
    return MergeSemantics(
      child: KitTappable(
        onTap: picks ? () => _open(context) : null,
        disabledReason: disabledReason,
        tappableKey: rowKey,
        child: body,
      ),
    );
  }
}

/// The picker sheet: showKitSheet + KitChoiceList.single(actsOnTap: true).
/// Returns the chosen value, or null when dismissed. Choosing the current
/// value returns it too.
Future<T?> showKitChoiceSheet<T>(
  BuildContext context, {
  required String title,
  required List<KitChoice<T>> choices,
  T? selected,
  String? subtitle,
  Key? sheetKey,
}) => showKitSheet<T>(
  context,
  title: title,
  subtitle: subtitle,
  sheetKey: sheetKey,
  body: (sheetContext) => KitChoiceList<T>.single(
    choices: choices,
    selected: selected,
    semanticsLabel: title,
    onSelected: (value) => Navigator.of(sheetContext).pop(value),
  ),
);
