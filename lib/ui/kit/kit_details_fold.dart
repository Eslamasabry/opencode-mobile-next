// The one technical fold (docs/ux-system/kit-api/KitDetailsFold.md; kit-v2
// §1.8, §4.3, §4.10; KIT-32, KIT-33, SEC-4): addresses, paths, ids,
// branches, raw errors and engine words live here, folded, last, mono,
// isolated left to right and copyable, and never a secret.
import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import 'kit_buttons.dart';
import 'kit_code_surface.dart';
import 'kit_divider.dart';
import 'kit_icon_button.dart';
import 'kit_ink_layer.dart';
import 'kit_layout.dart';
import 'kit_max_width.dart';
import 'kit_redact.dart';
import 'kit_sheet.dart';
import 'kit_technical_value.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_motion_parts.dart';
import 'motion/kit_reveal.dart';

AppLocalizations _l10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// The one technical fold (K2 §1.8, §4.3; KIT-33). A 48 dp "Details" row
/// that unfolds, in this order: [notes], [values], [text], [child]. It
/// holds the technical truth a person may need but never reads first, sits
/// last on its page or sheet, and shows each value once, mono, left to
/// right and copyable.
///
/// States: empty.
///
/// Empty (no values, notes, text or child) draws nothing: a fold with
/// nothing inside is never a dead toggle. Its two looks, collapsed (the
/// default) and open, are the disclosure's expanded state, not KIT-12
/// states (KitDetailsFold.md "States").
///
/// Data safety (SEC-2, SEC-4): every value, note and the raw text pass
/// through [KitRedact.text] before they are shown or copied. In debug a
/// value the redactor matches fails an assert naming its label; the raw
/// text is masked, not refused, because error bodies may quote headers.
class KitDetailsFold extends StatefulWidget {
  const KitDetailsFold({
    super.key,
    this.values = const [],
    this.notes = const [],
    this.text,
    this.child,
    this.label,
    this.initiallyExpanded = false,
    this.expanded,
    this.onExpansionChanged,
    this.foldKey,
    this.textKey,
    this.copyAllKey,
  }) : assert(expanded == null || onExpansionChanged != null);

  /// The values, deduplicated by value: a value shows once, under the first
  /// label that carried it.
  final List<KitTechnicalValue> values;

  /// Plain lines above the values: what to check.
  final List<String> notes;

  /// Raw technical text (an error, a response): one mono block, its first
  /// [textPreviewLines] lines shown until "Show all" unfolds the rest.
  final String? text;

  /// Richer technical content, last: a `KitLogPanel`, or a `KitCodeBlock`
  /// when the host has code rather than an error.
  final Widget? child;

  /// The toggle's words; null reads "Details".
  final String? label;
  final bool initiallyExpanded;

  /// Controlled: the host owns the open state and rebuilds on
  /// [onExpansionChanged]. Null lets the fold keep its own.
  final bool? expanded;
  final ValueChanged<bool>? onExpansionChanged;

  /// The toggle; null is `ValueKey('kit-details-toggle')`.
  final Key? foldKey;

  /// The raw text block; null is `ValueKey('kit-details-text')`.
  final Key? textKey;

  /// "Copy all"; null is `ValueKey('kit-details-copy-all')`.
  final Key? copyAllKey;

  /// Lines of [text] shown before "Show all {count} lines" unfolds the rest
  /// in place.
  static const int textPreviewLines = 12;

  /// True when there is nothing to fold (no values, notes, text or child):
  /// hosts use it to leave the fold out.
  bool get isEmpty =>
      values.isEmpty &&
      notes.isEmpty &&
      (text == null || text!.trim().isEmpty) &&
      child == null;

  @override
  State<KitDetailsFold> createState() => _KitDetailsFoldState();
}

class _KitDetailsFoldState extends State<KitDetailsFold> {
  late bool _open = widget.initiallyExpanded;

  bool get _shownOpen => widget.expanded ?? _open;

  void _toggle() {
    final next = !_shownOpen;
    if (widget.expanded == null) setState(() => _open = next);
    widget.onExpansionChanged?.call(next);
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    if (widget.isEmpty) return const SizedBox.shrink();
    _assertNoSecret(widget.values);
    final open = _shownOpen;
    final l10n = _l10n(context);
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _KitFoldToggle(
          controlKey: widget.foldKey ?? const ValueKey('kit-details-toggle'),
          label: widget.label ?? l10n.kitDetails,
          hint: open ? l10n.kitDetailsHide : l10n.kitDetails,
          expanded: open,
          onTap: _toggle,
        ),
        KitReveal(
          child: !open
              ? null
              : _KitDetailsContent(
                  values: widget.values,
                  notes: widget.notes,
                  text: widget.text,
                  textKey: widget.textKey,
                  copyAllKey: widget.copyAllKey,
                  wholeText: false,
                  child: widget.child,
                ),
        ),
      ],
    );
  }
}

/// SEC-4: a technical value is never a secret. Debug fails naming the
/// label; release masks it where it is shown.
void _assertNoSecret(List<KitTechnicalValue> values) {
  assert(() {
    for (final value in values) {
      if (KitRedact.containsSecret(value.value)) {
        throw FlutterError(
          'KitDetailsFold refuses a secret: the value of "${value.label}" '
          'looks like a credential (SEC-4). Never pass keys, tokens or '
          'passwords as technical values.',
        );
      }
    }
    return true;
  }());
}

/// A standalone raw error (a chat error's details, a failed request): a
/// [showKitSheet] titled [title] with the fold's content already open, the
/// [text] whole (no preview cap), the [values], and "Copy all". Returns
/// when the sheet closes. Replaces every raw-error AlertDialog (§4.3).
///
/// [title] names the failure in words ("Couldn't send"); [text] is the raw
/// technical text, redacted before it is shown or copied.
Future<void> showKitTechnicalDetails(
  BuildContext context, {
  required String title,
  required String text,
  List<KitTechnicalValue> values = const [],
  List<String> notes = const [],
  Key? sheetKey,
}) async {
  _assertNoSecret(values);
  await showKitSheet<void>(
    context,
    title: title,
    sheetKey: sheetKey,
    body: (_) => _KitDetailsContent(
      values: values,
      notes: notes,
      text: text,
      wholeText: true,
    ),
  );
}

/// The fold's 48 dp tertiary row: the words in `text2` at the start, the
/// kit's one disclosure chevron at the end. Focusable with the visible
/// ring; Enter and Space toggle it; hover fills it `surface3`.
class _KitFoldToggle extends StatefulWidget {
  const _KitFoldToggle({
    required this.controlKey,
    required this.label,
    required this.hint,
    required this.expanded,
    required this.onTap,
  });

  /// On the pressable control itself, so a test (or a still-motion
  /// sample) finds and presses it as a tap would.
  final Key controlKey;
  final String label;
  final String hint;
  final bool expanded;
  final VoidCallback onTap;

  @override
  State<_KitFoldToggle> createState() => _KitFoldToggleState();
}

class _KitFoldToggleState extends State<_KitFoldToggle> {
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final radius = BorderRadius.circular(tokens.detailsRadius);
    return Semantics(
      button: true,
      expanded: widget.expanded,
      label: widget.label,
      onTap: widget.onTap,
      onTapHint: widget.hint,
      excludeSemantics: true,
      child: DecoratedBox(
        // Always built, only the border switches, so the focused InkWell is
        // never rebuilt out from under the keyboard (LAY-10).
        position: DecorationPosition.foreground,
        decoration: BoxDecoration(
          borderRadius: radius,
          border: _focused
              ? Border.all(
                  color: roles.accent,
                  width: KitTokens.focusRingWidth(context),
                )
              : null,
        ),
        child: Material(
          type: MaterialType.transparency,
          child: InkWell(
            key: widget.controlKey,
            onTap: widget.onTap,
            borderRadius: radius,
            hoverColor: roles.surface3,
            focusColor: Colors.transparent,
            highlightColor: Colors.transparent,
            // The native ripple on Android (KitInk); none elsewhere.
            splashFactory: KitInk.splashFactory(context),
            splashColor: KitInk.color(context),
            onFocusChange: (value) {
              if (value != _focused) setState(() => _focused = value);
            },
            child: ConstrainedBox(
              constraints: BoxConstraints(minHeight: tokens.minTarget),
              child: Row(
                children: [
                  Expanded(
                    child: KitText(
                      widget.label,
                      role: KitTextRole.secondary,
                      tone: KitTextTone.secondary,
                    ),
                  ),
                  SizedBox(width: tokens.space2),
                  KitSpin.chevron(expanded: widget.expanded),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// What the fold unfolds (and what [showKitTechnicalDetails] shows open):
/// notes, values, text and child on the details surface, then "Copy all".
class _KitDetailsContent extends StatefulWidget {
  const _KitDetailsContent({
    required this.values,
    required this.notes,
    required this.text,
    required this.wholeText,
    this.textKey,
    this.copyAllKey,
    this.child,
  });

  final List<KitTechnicalValue> values;
  final List<String> notes;
  final String? text;
  final bool wholeText;
  final Key? textKey;
  final Key? copyAllKey;
  final Widget? child;

  @override
  State<_KitDetailsContent> createState() => _KitDetailsContentState();
}

class _KitDetailsContentState extends State<_KitDetailsContent> {
  bool _showAll = false;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = _l10n(context);
    final seen = <String>{};
    final values = [
      for (final value in widget.values)
        if (seen.add(value.value)) value,
    ];
    final raw = widget.text;
    final text = raw == null || raw.trim().isEmpty ? null : KitRedact.text(raw);
    final wide = KitLayout.windowOf(context).isWide;

    final copyable = [
      for (final value in values)
        if (value.copyable) value,
    ];
    final showCopyAll = copyable.length >= 2 || text != null;
    String copyAll() {
      final lines = [
        for (final value in copyable)
          '${value.label}: ${KitRedact.text(value.value)}',
      ];
      return KitRedact.text(
        [if (lines.isNotEmpty) lines.join('\n'), ?text].join('\n\n'),
      );
    }

    final sections = <Widget>[
      if (widget.notes.isNotEmpty)
        Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (final (index, note) in widget.notes.indexed) ...[
              if (index > 0) SizedBox(height: tokens.space1),
              KitText(KitRedact.text(note), role: KitTextRole.secondary),
            ],
          ],
        ),
      if (values.isNotEmpty)
        Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (final (index, value) in values.indexed) ...[
              if (index > 0) const KitDivider(),
              _KitValueRow(
                key: ValueKey('kit-details-value-$index'),
                index: index,
                value: value,
                wide: wide,
              ),
            ],
          ],
        ),
      if (text != null) _textBlock(context, text),
      ?widget.child,
    ];

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (sections.isNotEmpty)
          kitCodeSurface(
            context,
            fill: tokens.detailsSurface,
            borderRadius: BorderRadius.circular(tokens.detailsRadius),
            child: Padding(
              padding: EdgeInsets.all(tokens.space3),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  for (final (index, section) in sections.indexed) ...[
                    if (index > 0) SizedBox(height: tokens.space3),
                    section,
                  ],
                ],
              ),
            ),
          ),
        if (showCopyAll) ...[
          SizedBox(height: tokens.space1),
          KitInset(
            child: KitButton.fromAction(
              KitAction.copy(
                key:
                    widget.copyAllKey ?? const ValueKey('kit-details-copy-all'),
                label: l10n.kitCopyAll,
                text: copyAll,
              ),
              role: KitButtonRole.tertiary,
            ),
          ),
        ],
      ],
    );
  }

  Widget _textBlock(BuildContext context, String text) {
    final tokens = KitTokens.of(context);
    final lines = text.split('\n');
    final capped =
        !widget.wholeText && lines.length > KitDetailsFold.textPreviewLines;
    final head = capped
        ? lines.take(KitDetailsFold.textPreviewLines).join('\n')
        : text;
    final tail = capped
        ? lines.skip(KitDetailsFold.textPreviewLines).join('\n')
        : null;
    return Column(
      key: widget.textKey ?? const ValueKey('kit-details-text'),
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _KitLtrValue(text: head),
        if (tail != null) ...[
          KitReveal(child: _showAll ? _KitLtrValue(text: tail) : null),
          if (!_showAll) ...[
            SizedBox(height: tokens.space1),
            KitInset(
              child: KitButton.tertiary(
                key: const ValueKey('kit-details-show-all'),
                label: _l10n(context).kitDetailsShowAll(lines.length),
                onPressed: () => setState(() => _showAll = true),
              ),
            ),
          ],
        ],
      ],
    );
  }
}

/// One value: its label and its mono value as one semantic node ("Address:
/// 100.64.0.3, port 4096"), and its copy button as its own. Compact and
/// medium windows stack the label over the value; expanded and large ones
/// put the label in a [KitTokens.detailsLabelColumn] column.
class _KitValueRow extends StatelessWidget {
  const _KitValueRow({
    super.key,
    required this.index,
    required this.value,
    required this.wide,
  });

  final int index;
  final KitTechnicalValue value;
  final bool wide;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = _l10n(context);
    final shown = KitRedact.text(value.value);
    final spoken = KitBidi.ltr(KitRedact.text(value.spoken ?? value.value));
    final label = KitText(value.label, role: KitTextRole.secondary);
    final text = _KitLtrValue(text: shown, valueKey: value.key);
    final words = Semantics(
      container: true,
      label: l10n.kitDetailsValueSpoken(value.label, spoken),
      excludeSemantics: true,
      child: wide
          ? Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(width: KitTokens.detailsLabelColumn, child: label),
                SizedBox(width: tokens.space3),
                Expanded(child: text),
              ],
            )
          : Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [label, text],
            ),
    );
    return ConstrainedBox(
      constraints: BoxConstraints(minHeight: tokens.minTarget),
      child: Padding(
        padding: EdgeInsets.symmetric(vertical: tokens.space1),
        child: Row(
          children: [
            Expanded(child: words),
            if (value.copyable) ...[
              SizedBox(width: tokens.space2),
              KitIconButton.copy(
                key: ValueKey('kit-details-copy-$index'),
                text: () => shown,
                tooltip: l10n.kitCopyValue(_lowerFirst(value.label)),
                size: tokens.smallIconSize,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// A technical value laid out left to right (COPY-30, LAY-8) whatever the
/// reading direction, sitting at the start of its row in both directions,
/// wrapping anywhere and selectable.
class _KitLtrValue extends StatelessWidget {
  const _KitLtrValue({required this.text, this.valueKey});

  final String text;
  final Key? valueKey;

  @override
  Widget build(BuildContext context) => Align(
    alignment: AlignmentDirectional.centerStart,
    child: Directionality(
      textDirection: TextDirection.ltr,
      child: KitText.mono(text, key: valueKey, selectable: true),
    ),
  );
}

/// "Address" → "address" for "Copy address"; an acronym ("URL", "PID")
/// keeps its case.
String _lowerFirst(String s) {
  if (s.isEmpty) return s;
  final first = s.substring(0, 1);
  if (s.length > 1) {
    final second = s.substring(1, 2);
    if (second != second.toLowerCase()) return s;
  }
  return '${first.toLowerCase()}${s.substring(1)}';
}
