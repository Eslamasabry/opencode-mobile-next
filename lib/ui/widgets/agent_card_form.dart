import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../domain/genui/gen_ui.dart';
import '../../l10n/app_localizations.dart';
import '../kit/kit.dart';
import 'agent_card_asks.dart' show AgentCardAnswer, agentCardSendBlock;

/// A form ask: one kit control per field, with required fields that hold
/// Send until they are filled. Nothing is autofilled: a field starts from the
/// agent's own default or empty, never from the clipboard, the account or the
/// phone. The agent sees what is typed and it stays in the conversation, and
/// the form says so once.
class AgentCardForm extends StatefulWidget {
  const AgentCardForm({
    super.key,
    required this.ask,
    required this.onAnswer,
    required this.blockedReason,
    required this.sending,
    required this.inList,
  });

  final GenUiFormAsk ask;
  final AgentCardAnswer onAnswer;
  final String? blockedReason;
  final bool sending;
  final bool inList;

  @override
  State<AgentCardForm> createState() => _AgentCardFormState();
}

class _AgentCardFormState extends State<AgentCardForm> {
  final Map<String, TextEditingController> _text = {};
  final Map<String, bool> _toggles = {};
  final Map<String, String?> _selects = {};
  final Map<String, DateTime?> _dates = {};

  /// Fields the person has changed: a required one shows its reason only
  /// after that, so a fresh form is not a wall of errors.
  final Set<String> _touched = {};

  @override
  void initState() {
    super.initState();
    for (final field in widget.ask.fields) {
      final value = field.defaultValue;
      switch (field.type) {
        case GenUiFieldType.text:
        case GenUiFieldType.multiline:
        case GenUiFieldType.number:
          _text[field.id] = TextEditingController(
            text: switch (value) {
              final String s => s,
              final num n => _numberText(n),
              _ => '',
            },
          );
        case GenUiFieldType.toggle:
          _toggles[field.id] = value is bool ? value : false;
        case GenUiFieldType.select:
          _selects[field.id] =
              value is String && field.options.any((o) => o.id == value)
              ? value
              : null;
        case GenUiFieldType.date:
          _dates[field.id] = value is String ? _parseDate(value) : null;
      }
    }
  }

  @override
  void dispose() {
    for (final controller in _text.values) {
      controller.dispose();
    }
    super.dispose();
  }

  static String _numberText(num n) =>
      n == n.truncate() ? n.truncate().toString() : n.toString();

  static DateTime? _parseDate(String s) {
    final match = RegExp(r'^(\d{4})-(\d{2})-(\d{2})$').firstMatch(s);
    if (match == null) return null;
    return DateTime(
      int.parse(match[1]!),
      int.parse(match[2]!),
      int.parse(match[3]!),
    );
  }

  static String _dateText(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-'
      '${d.month.toString().padLeft(2, '0')}-'
      '${d.day.toString().padLeft(2, '0')}';

  /// ASCII digits for the number the person typed (the kit field already
  /// normalises Arabic-Indic ones), or null when it is not a number.
  static num? _number(String raw) => num.tryParse(raw.trim());

  /// The reason a field cannot be sent yet, or null.
  String? _problem(GenUiField field, AppLocalizations l10n) {
    switch (field.type) {
      case GenUiFieldType.text:
      case GenUiFieldType.multiline:
        final text = _text[field.id]!.text;
        return field.required && text.trim().isEmpty
            ? l10n.agentCardFieldRequired
            : null;
      case GenUiFieldType.number:
        final raw = _text[field.id]!.text.trim();
        if (raw.isEmpty) {
          return field.required ? l10n.agentCardFieldRequired : null;
        }
        final value = _number(raw);
        if (value == null || !value.isFinite) return l10n.agentCardFieldNumber;
        final min = field.min;
        final max = field.max;
        if (min != null && value < min) {
          return l10n.agentCardFieldMin(_numberText(min));
        }
        if (max != null && value > max) {
          return l10n.agentCardFieldMax(_numberText(max));
        }
        return null;
      case GenUiFieldType.toggle:
        return null;
      case GenUiFieldType.select:
        return field.required && _selects[field.id] == null
            ? l10n.agentCardFieldRequired
            : null;
      case GenUiFieldType.date:
        return field.required && _dates[field.id] == null
            ? l10n.agentCardFieldRequired
            : null;
    }
  }

  bool _valid(AppLocalizations l10n) =>
      widget.ask.fields.every((field) => _problem(field, l10n) == null);

  GenUiFormAnswer _answer() {
    final values = <String, Object>{};
    for (final field in widget.ask.fields) {
      switch (field.type) {
        case GenUiFieldType.text:
        case GenUiFieldType.multiline:
          final text = _text[field.id]!.text;
          if (text.trim().isNotEmpty) values[field.id] = text;
        case GenUiFieldType.number:
          final value = _number(_text[field.id]!.text);
          if (value != null) {
            values[field.id] = value == value.truncate() && value.abs() < 1e15
                ? value.truncate()
                : value;
          }
        case GenUiFieldType.toggle:
          values[field.id] = _toggles[field.id] ?? false;
        case GenUiFieldType.select:
          final id = _selects[field.id];
          if (id != null) values[field.id] = id;
        case GenUiFieldType.date:
          final day = _dates[field.id];
          if (day != null) values[field.id] = _dateText(day);
      }
    }
    return GenUiFormAnswer(values);
  }

  /// A required field's reason shows once the person has touched it.
  String? _shown(GenUiField field, AppLocalizations l10n) =>
      _touched.contains(field.id) ? _problem(field, l10n) : null;

  void _touch(String id) => setState(() => _touched.add(id));

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final ask = widget.ask;
    final valid = _valid(l10n);
    final reason =
        widget.blockedReason ?? (valid ? null : l10n.agentCardFormFix);
    final hasText = ask.fields.any(
      (f) =>
          f.type == GenUiFieldType.text || f.type == GenUiFieldType.multiline,
    );
    final send = KitAction(
      label: ask.submitLabel ?? l10n.agentCardSend,
      key: const Key('agent-card-send'),
      working: widget.sending,
      onPressed: reason == null && !widget.sending
          ? () => unawaited(widget.onAnswer(_answer()))
          : null,
      disabledReason: reason == null || widget.sending ? null : reason,
    );
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final field in ask.fields) ...[
          _field(context, field, l10n),
          SizedBox(height: tokens.space3),
        ],
        if (hasText)
          Padding(
            padding: EdgeInsetsDirectional.only(bottom: tokens.space3),
            child: KitText(
              l10n.agentCardTextNote,
              key: const Key('agent-card-text-note'),
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
          ),
        agentCardSendBlock(context, send: send, inList: widget.inList),
      ],
    );
  }

  Widget _field(BuildContext context, GenUiField field, AppLocalizations l10n) {
    final label = KitBidi.auto(field.label);
    final error = _shown(field, l10n);
    final key = Key('agent-card-field-${field.id}');
    switch (field.type) {
      case GenUiFieldType.text:
      case GenUiFieldType.multiline:
      case GenUiFieldType.number:
        return KitField(
          label: label,
          fieldKey: key,
          controller: _text[field.id],
          kind: switch (field.type) {
            GenUiFieldType.multiline => KitFieldKind.multiline,
            GenUiFieldType.number => KitFieldKind.number,
            _ => KitFieldKind.text,
          },
          decimal: field.type == GenUiFieldType.number,
          hint: field.placeholder,
          maxLength: field.type == GenUiFieldType.number ? null : 2000,
          helper: field.required ? l10n.agentCardFieldRequiredHint : null,
          error: error,
          onChanged: (_) => _touch(field.id),
        );
      case GenUiFieldType.toggle:
        return KitRowGroup(
          margin: EdgeInsets.zero,
          leadingIcons: false,
          children: [
            KitSwitchRow(
              title: label,
              value: _toggles[field.id] ?? false,
              switchKey: key,
              onChanged: (on) => setState(() => _toggles[field.id] = on),
            ),
          ],
        );
      case GenUiFieldType.select:
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            KitText(label, role: KitTextRole.label, tone: KitTextTone.primary),
            SizedBox(height: KitTokens.of(context).space1),
            KitChoiceList<String>.single(
              key: key,
              semanticsLabel: label,
              actsOnTap: false,
              selected: _selects[field.id],
              choices: [
                for (final option in field.options)
                  KitChoice<String>(
                    value: option.id,
                    title: KitBidi.auto(option.label),
                    supporting: option.detail == null
                        ? null
                        : KitBidi.auto(option.detail!),
                  ),
              ],
              onSelected: (id) => setState(() {
                _selects[field.id] = id;
                _touched.add(field.id);
              }),
            ),
            if (error != null)
              KitText(
                error,
                role: KitTextRole.secondary,
                tone: KitTextTone.danger,
              ),
          ],
        );
      case GenUiFieldType.date:
        return KitRowGroup(
          margin: EdgeInsets.zero,
          leadingIcons: false,
          children: [
            KitDateTimeRow.date(
              title: label,
              rowKey: key,
              value: _dates[field.id],
              clearable: !field.required,
              supporting: field.required
                  ? l10n.agentCardFieldRequiredHint
                  : null,
              error: error,
              onChanged: (day) => setState(() {
                _dates[field.id] = day;
                _touched.add(field.id);
              }),
            ),
          ],
        );
    }
  }
}
