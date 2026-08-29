import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api2/models.dart';
import 'confirm_sheet.dart';
import 'external_link.dart';

/// Delivers the assembled answer payload (active fields only) to the caller.
/// Throwing keeps the form open and surfaces the message in the pinned error
/// banner (`form-error-banner`); returning normally closes the form.
typedef FormRendererSubmit = Future<void> Function(Map<String, dynamic> answer);

/// Called after the user confirms "Dismiss". Throwing keeps the form open.
typedef FormRendererCancel = Future<void> Function();

/// True when [form] must be presented as a full-screen dialog instead of a
/// modal bottom sheet: five or more DECLARED fields, or any declared field
/// whose description runs past ~140 characters. The count deliberately
/// ignores `when` activity so a form never jumps between presentations as
/// conditions toggle.
bool formPrefersFullScreen(Api2FormInfo form) =>
    form.fields.length >= 5 ||
    form.fields.any((field) => (field.description?.length ?? 0) > 140);

/// Presents [form] the locked way: a modal bottom sheet (top radius 24,
/// `surfaceContainerLow`) for four or fewer declared fields, otherwise a
/// full-screen dialog route. Completes when the surface closes.
Future<void> presentForm(
  BuildContext context, {
  required Api2FormInfo form,
  required FormRendererSubmit onSubmit,
  required FormRendererCancel onCancel,
}) {
  if (formPrefersFullScreen(form)) {
    return Navigator.of(context).push<void>(
      MaterialPageRoute<void>(
        fullscreenDialog: true,
        builder: (routeContext) => Scaffold(
          body: SafeArea(
            child: FormRenderer(
              form: form,
              onSubmit: onSubmit,
              onCancel: onCancel,
              onClose: () => Navigator.of(routeContext).maybePop(),
            ),
          ),
        ),
      ),
    );
  }
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    clipBehavior: Clip.antiAlias,
    constraints: const BoxConstraints(maxWidth: 720),
    backgroundColor: Theme.of(context).colorScheme.surfaceContainerLow,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
    ),
    builder: (sheetContext) => Padding(
      padding: EdgeInsets.only(
        bottom: MediaQuery.viewInsetsOf(sheetContext).bottom,
      ),
      child: FormRenderer(
        form: form,
        onSubmit: onSubmit,
        onCancel: onCancel,
        onClose: () => Navigator.of(sheetContext).maybePop(),
      ),
    ),
  );
}

/// The one shared renderer for `Form.Info` (protocol notes §8): pure
/// presentation plus local answer state — no networking. Fields map to their
/// locked M3 controls, `when` conditions animate slots open and closed with
/// [AnimatedSize] (drafts of inactive fields are retained but excluded from
/// the payload and validation), and the pinned apply bar carries the
/// Send answers / Dismiss pair.
class FormRenderer extends StatefulWidget {
  const FormRenderer({
    super.key,
    required this.form,
    required this.onSubmit,
    required this.onCancel,
    this.onClose,
    this.scrollController,
  });

  final Api2FormInfo form;
  final FormRendererSubmit onSubmit;
  final FormRendererCancel onCancel;

  /// Invoked after a successful submit or a confirmed cancel; the presenting
  /// surface pops itself here.
  final VoidCallback? onClose;

  final ScrollController? scrollController;

  @override
  State<FormRenderer> createState() => _FormRendererState();
}

/// Sentinel option value for the revealed "Other…" free-text choice.
const _kOtherChoice = '\u0000form-renderer-other';

class _FormRendererState extends State<FormRenderer> {
  /// One slot key per declared field, for scroll-to-first-error.
  final Map<String, GlobalKey> _slotKeys = {};

  /// Free string, date display, and number text.
  final Map<String, TextEditingController> _text = {};

  /// The revealed "Other…" free text of custom selects.
  final Map<String, TextEditingController> _otherText = {};

  /// The "Add your own" composer of custom multiselects.
  final Map<String, TextEditingController> _addText = {};

  /// Chosen option value of select (string-with-options) fields.
  final Map<String, String?> _choice = {};

  /// Select fields currently on their "Other…" choice.
  final Set<String> _otherOn = {};

  /// Boolean switch values.
  final Map<String, bool> _toggle = {};

  /// Selected option values per multiselect field.
  final Map<String, Set<String>> _selected = {};

  /// User-added custom values per multiselect field, in add order.
  final Map<String, List<String>> _added = {};

  /// ISO value backing date / date-time fields.
  final Map<String, String> _dateIso = {};

  final Map<String, String> _errors = {};
  bool _busy = false;
  String? _bannerError;

  @override
  void initState() {
    super.initState();
    for (final field in widget.form.fields) {
      _slotKeys[field.key] = GlobalKey();
      _seedField(field);
    }
  }

  void _seedField(Api2FormField field) {
    final key = field.key;
    final fallback = field.defaultValue;
    switch (field.type) {
      case Api2FormFieldType.string:
        if (field.options.isNotEmpty) {
          if (field.custom) _otherText[key] = TextEditingController();
          if (fallback is String) {
            if (field.options.any((option) => option.value == fallback)) {
              _choice[key] = fallback;
            } else if (field.custom) {
              _otherOn.add(key);
              _otherText[key]!.text = fallback;
            }
          }
        } else if (_isDate(field)) {
          final controller = TextEditingController();
          if (fallback is String) {
            final parsed = DateTime.tryParse(fallback);
            if (parsed != null) {
              _dateIso[key] = _isoOf(parsed, withTime: _isDateTime(field));
              controller.text = _displayOf(
                parsed,
                withTime: _isDateTime(field),
              );
            }
          }
          _text[key] = controller;
        } else {
          _text[key] = TextEditingController(
            text: fallback is String ? fallback : '',
          );
        }
      case Api2FormFieldType.number:
      case Api2FormFieldType.integer:
        _text[key] = TextEditingController(
          text: fallback is num ? _formatNum(fallback) : '',
        );
      case Api2FormFieldType.boolean:
        _toggle[key] = fallback is bool ? fallback : false;
      case Api2FormFieldType.multiselect:
        final selected = <String>{};
        final added = <String>[];
        if (fallback is List) {
          for (final value in fallback.whereType<String>()) {
            if (field.options.any((option) => option.value == value)) {
              selected.add(value);
            } else if (field.custom && !added.contains(value)) {
              added.add(value);
            }
          }
        }
        _selected[key] = selected;
        _added[key] = added;
        if (field.custom) _addText[key] = TextEditingController();
      case Api2FormFieldType.external:
      case Api2FormFieldType.unknown:
        break;
    }
  }

  @override
  void dispose() {
    for (final controller in _text.values) {
      controller.dispose();
    }
    for (final controller in _otherText.values) {
      controller.dispose();
    }
    for (final controller in _addText.values) {
      controller.dispose();
    }
    super.dispose();
  }

  // ---------------- Answer + activity evaluation ----------------

  static bool _isDate(Api2FormField field) =>
      field.format == 'date' || field.format == 'date-time';

  static bool _isDateTime(Api2FormField field) => field.format == 'date-time';

  /// Walks the declared fields once, accumulating the live answers of active
  /// fields. `when` references only ever point at earlier fields, so a single
  /// forward pass settles activity: a field whose controller went inactive
  /// drops out of the map, deactivating its own dependents in cascade
  /// ("unanswered reference ⇒ condition false").
  ({Set<String> active, Map<String, dynamic> answers}) _evaluate() {
    final active = <String>{};
    final answers = <String, dynamic>{};
    for (final field in widget.form.fields) {
      if (!field.activeFor(answers)) continue;
      active.add(field.key);
      final value = _answerOf(field);
      if (value != null) answers[field.key] = value;
    }
    return (active: active, answers: answers);
  }

  /// The field's current answer, or null when it is effectively unanswered.
  /// `external` and unknown fields never contribute an answer.
  dynamic _answerOf(Api2FormField field) {
    final key = field.key;
    switch (field.type) {
      case Api2FormFieldType.string:
        if (field.options.isNotEmpty) {
          if (_otherOn.contains(key)) {
            final text = _otherText[key]?.text.trim() ?? '';
            return text.isEmpty ? null : text;
          }
          return _choice[key];
        }
        if (_isDate(field)) return _dateIso[key];
        final text = _text[key]?.text.trim() ?? '';
        return text.isEmpty ? null : text;
      case Api2FormFieldType.number:
        return num.tryParse(_text[key]?.text.trim() ?? '');
      case Api2FormFieldType.integer:
        return int.tryParse(_text[key]?.text.trim() ?? '');
      case Api2FormFieldType.boolean:
        return _toggle[key] ?? false;
      case Api2FormFieldType.multiselect:
        final values = _multiselectValues(field);
        return values.isEmpty ? null : values;
      case Api2FormFieldType.external:
      case Api2FormFieldType.unknown:
        return null;
    }
  }

  /// Selected option values in declaration order, then custom additions.
  List<String> _multiselectValues(Api2FormField field) {
    final selected = _selected[field.key] ?? const <String>{};
    return [
      for (final option in field.options)
        if (selected.contains(option.value)) option.value,
      ...?_added[field.key],
    ];
  }

  // ---------------- Validation ----------------

  /// Local mirror of the schema, applied to ACTIVE fields only — inactive
  /// fields are neither required nor answerable. `external` never blocks.
  Map<String, String> _validate(
    Set<String> active,
    Map<String, dynamic> answers,
  ) {
    final errors = <String, String>{};
    for (final field in widget.form.fields) {
      if (!active.contains(field.key)) continue;
      final error = _validateField(field, answers[field.key]);
      if (error != null) errors[field.key] = error;
    }
    return errors;
  }

  String? _validateField(Api2FormField field, dynamic answer) {
    switch (field.type) {
      case Api2FormFieldType.string:
        final value = answer as String?;
        if (value == null) return field.required ? 'Required' : null;
        final minLength = field.minLength;
        if (minLength != null && value.length < minLength) {
          return 'Must be at least $minLength characters';
        }
        final maxLength = field.maxLength;
        if (maxLength != null && value.length > maxLength) {
          return 'Must be at most $maxLength characters';
        }
        final pattern = field.pattern;
        if (pattern != null && !RegExp(pattern).hasMatch(value)) {
          return 'Does not match the expected format';
        }
        return null;
      case Api2FormFieldType.number:
      case Api2FormFieldType.integer:
        final raw = _text[field.key]?.text.trim() ?? '';
        if (raw.isEmpty) return field.required ? 'Required' : null;
        if (answer is! num) {
          return field.type == Api2FormFieldType.integer
              ? 'Enter a whole number'
              : 'Enter a number';
        }
        final minimum = field.minimum;
        final maximum = field.maximum;
        if ((minimum != null && answer < minimum) ||
            (maximum != null && answer > maximum)) {
          if (minimum != null && maximum != null) {
            return 'Must be between ${_formatNum(minimum)} '
                'and ${_formatNum(maximum)}';
          }
          return minimum != null
              ? 'Must be at least ${_formatNum(minimum)}'
              : 'Must be at most ${_formatNum(maximum!)}';
        }
        return null;
      case Api2FormFieldType.multiselect:
        final count = _multiselectValues(field).length;
        if (count == 0) return field.required ? 'Required' : null;
        final minItems = field.minItems;
        if (minItems != null && count < minItems) {
          return 'Pick at least $minItems';
        }
        final maxItems = field.maxItems;
        if (maxItems != null && count > maxItems) {
          return 'Pick at most $maxItems';
        }
        return null;
      case Api2FormFieldType.boolean:
      case Api2FormFieldType.external:
      case Api2FormFieldType.unknown:
        return null;
    }
  }

  // ---------------- Submit / cancel ----------------

  Future<void> _submit() async {
    if (_busy) return;
    final eval = _evaluate();
    final errors = _validate(eval.active, eval.answers);
    setState(() {
      _errors
        ..clear()
        ..addAll(errors);
      _bannerError = null;
    });
    if (errors.isNotEmpty) {
      _revealFirstError(errors);
      return;
    }
    setState(() => _busy = true);
    try {
      await widget.onSubmit(Map<String, dynamic>.of(eval.answers));
      if (mounted) widget.onClose?.call();
    } catch (error) {
      if (mounted) setState(() => _bannerError = _messageOf(error));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _revealFirstError(Map<String, String> errors) {
    for (final field in widget.form.fields) {
      if (!errors.containsKey(field.key)) continue;
      final slotContext = _slotKeys[field.key]?.currentContext;
      if (slotContext != null) {
        final reduceMotion = MediaQuery.disableAnimationsOf(context);
        unawaited(
          Scrollable.ensureVisible(
            slotContext,
            alignment: .1,
            duration: reduceMotion
                ? Duration.zero
                : const Duration(milliseconds: 240),
            curve: Curves.easeOutCubic,
          ),
        );
      }
      return;
    }
  }

  Future<void> _dismiss() async {
    if (_busy) return;
    final confirmed = await showConfirmSheet(
      context,
      title: 'Dismiss this request?',
      message: 'The agent continues without your answers.',
      confirmLabel: 'Dismiss',
      icon: Icons.block_rounded,
      destructive: true,
      sheetKey: const Key('form-dismiss-confirm'),
      confirmKey: const Key('form-dismiss-confirm-button'),
    );
    if (!confirmed || !mounted) return;
    setState(() {
      _busy = true;
      _bannerError = null;
    });
    try {
      await widget.onCancel();
      if (mounted) widget.onClose?.call();
    } catch (error) {
      if (mounted) setState(() => _bannerError = _messageOf(error));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  static String _messageOf(Object error) {
    final text = error.toString();
    const prefix = 'Exception: ';
    return text.startsWith(prefix) ? text.substring(prefix.length) : text;
  }

  // ---------------- Build ----------------

  @override
  Widget build(BuildContext context) {
    final eval = _evaluate();
    final reduceMotion = MediaQuery.disableAnimationsOf(context);
    return Column(
      key: const Key('form-sheet'),
      mainAxisSize: MainAxisSize.min,
      children: [
        _header(context),
        const Divider(height: 1),
        Flexible(
          child: SingleChildScrollView(
            controller: widget.scrollController,
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            padding: const EdgeInsets.fromLTRB(20, 14, 20, 10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                for (final field in widget.form.fields)
                  _slot(context, field, eval.active, reduceMotion),
              ],
            ),
          ),
        ),
        if (_bannerError != null) _errorBanner(context),
        _applyBar(context),
      ],
    );
  }

  Widget _header(BuildContext context) {
    final theme = Theme.of(context);
    final origin = widget.form.sessionID == 'global'
        ? 'Asked by an MCP server'
        : 'Asked by the agent in this session';
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            widget.form.title ?? 'Input requested',
            key: const Key('form-title'),
            style: theme.textTheme.titleMedium,
          ),
          const SizedBox(height: 3),
          Text(
            origin,
            style: theme.textTheme.labelSmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }

  /// One animated slot per declared field. Inactive fields collapse to a
  /// zero-height box inside [AnimatedSize] so they slide open and closed in
  /// place (240 ms easeOutCubic; instant under reduced motion) while their
  /// drafts stay in state.
  Widget _slot(
    BuildContext context,
    Api2FormField field,
    Set<String> active,
    bool reduceMotion,
  ) {
    return AnimatedSize(
      duration: reduceMotion
          ? Duration.zero
          : const Duration(milliseconds: 240),
      curve: Curves.easeOutCubic,
      alignment: Alignment.topCenter,
      child: active.contains(field.key)
          ? KeyedSubtree(
              key: _slotKeys[field.key],
              child: Padding(
                padding: const EdgeInsets.only(bottom: 18),
                child: _field(context, field, reduceMotion),
              ),
            )
          : const SizedBox(width: double.infinity, height: 0),
    );
  }

  Widget _field(BuildContext context, Api2FormField field, bool reduceMotion) {
    final content = switch (field.type) {
      Api2FormFieldType.string when field.options.isNotEmpty =>
        field.options.length <= 4
            ? _radioSelect(context, field, reduceMotion)
            : _dropdownSelect(context, field, reduceMotion),
      Api2FormFieldType.string when _isDate(field) => _dateField(
        context,
        field,
      ),
      Api2FormFieldType.string => _stringField(context, field),
      Api2FormFieldType.number ||
      Api2FormFieldType.integer => _numberField(context, field),
      Api2FormFieldType.boolean => _booleanField(context, field),
      Api2FormFieldType.multiselect =>
        field.options.length >= 9
            ? _multiselectChecklist(context, field)
            : _multiselectChips(context, field),
      Api2FormFieldType.external => _externalCard(context, field),
      Api2FormFieldType.unknown => _unknownField(context, field),
    };
    return KeyedSubtree(key: Key('form-field-${field.key}'), child: content);
  }

  String _label(Api2FormField field) =>
      '${field.title ?? field.key}${field.required ? ' *' : ''}';

  /// Description helper + inline error for controls that do not render their
  /// own `errorText` (TextField-based controls handle both natively).
  List<Widget> _helperAndError(BuildContext context, Api2FormField field) {
    final theme = Theme.of(context);
    final error = _errors[field.key];
    return [
      if (field.description != null)
        Padding(
          padding: const EdgeInsets.only(top: 6),
          child: Text(
            field.description!,
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
      if (error != null)
        Padding(
          padding: const EdgeInsets.only(top: 6),
          child: Text(
            error,
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.error,
            ),
          ),
        ),
    ];
  }

  Widget _groupLabel(BuildContext context, Api2FormField field) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: Text(_label(field), style: Theme.of(context).textTheme.labelLarge),
  );

  /// Rounded `surfaceContainerHigh` surface for grouped list controls. A
  /// [Material] (not a decorated box) so tile backgrounds and ink render.
  Widget _groupSurface(BuildContext context, Widget child) => Material(
    color: Theme.of(context).colorScheme.surfaceContainerHigh,
    borderRadius: BorderRadius.circular(16),
    clipBehavior: Clip.antiAlias,
    child: child,
  );

  void _clearError(String key) => setState(() => _errors.remove(key));

  // ---------------- string ----------------

  Widget _stringField(BuildContext context, Api2FormField field) {
    final multiline = field.maxLength == null || field.maxLength! > 120;
    final keyboardType = switch (field.format) {
      'email' => TextInputType.emailAddress,
      'uri' => TextInputType.url,
      _ => multiline ? TextInputType.multiline : TextInputType.text,
    };
    return TextField(
      controller: _text[field.key],
      keyboardType: keyboardType,
      minLines: 1,
      maxLines: multiline ? 5 : 1,
      maxLength: field.maxLength,
      onChanged: (_) => _clearError(field.key),
      decoration: InputDecoration(
        border: const OutlineInputBorder(),
        labelText: _label(field),
        hintText: field.placeholder,
        helperText: field.description,
        helperMaxLines: 4,
        errorText: _errors[field.key],
        errorMaxLines: 3,
      ),
    );
  }

  // ---------------- string · date / date-time ----------------

  Widget _dateField(BuildContext context, Api2FormField field) {
    return TextField(
      controller: _text[field.key],
      readOnly: true,
      onTap: _busy ? null : () => _pickDate(field),
      decoration: InputDecoration(
        border: const OutlineInputBorder(),
        labelText: _label(field),
        hintText: field.placeholder,
        helperText: field.description,
        helperMaxLines: 4,
        errorText: _errors[field.key],
        errorMaxLines: 3,
        suffixIcon: Icon(
          _isDateTime(field)
              ? Icons.schedule_rounded
              : Icons.calendar_today_rounded,
        ),
      ),
    );
  }

  Future<void> _pickDate(Api2FormField field) async {
    final current =
        DateTime.tryParse(_dateIso[field.key] ?? '') ?? DateTime.now();
    final date = await showDatePicker(
      context: context,
      initialDate: current,
      firstDate: DateTime(1900),
      lastDate: DateTime(2100),
    );
    if (date == null || !mounted) return;
    var value = date;
    if (_isDateTime(field)) {
      final time = await showTimePicker(
        context: context,
        initialTime: TimeOfDay.fromDateTime(current),
      );
      if (!mounted) return;
      if (time != null) {
        value = DateTime(
          date.year,
          date.month,
          date.day,
          time.hour,
          time.minute,
        );
      }
    }
    setState(() {
      _errors.remove(field.key);
      _dateIso[field.key] = _isoOf(value, withTime: _isDateTime(field));
      _text[field.key]?.text = _displayOf(value, withTime: _isDateTime(field));
    });
  }

  static String _two(int value) => value.toString().padLeft(2, '0');

  static String _isoOf(DateTime value, {required bool withTime}) => withTime
      ? value.toIso8601String()
      : '${value.year}-${_two(value.month)}-${_two(value.day)}';

  static String _displayOf(DateTime value, {required bool withTime}) {
    final date = '${value.year}-${_two(value.month)}-${_two(value.day)}';
    return withTime ? '$date ${_two(value.hour)}:${_two(value.minute)}' : date;
  }

  // ---------------- string · options ----------------

  Widget _radioSelect(
    BuildContext context,
    Api2FormField field,
    bool reduceMotion,
  ) {
    final key = field.key;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _groupLabel(context, field),
        _groupSurface(
          context,
          RadioGroup<String>(
            groupValue: _otherOn.contains(key) ? _kOtherChoice : _choice[key],
            onChanged: (value) {
              if (value == null) return;
              setState(() {
                _errors.remove(key);
                if (value == _kOtherChoice) {
                  _otherOn.add(key);
                } else {
                  _otherOn.remove(key);
                  _choice[key] = value;
                }
              });
            },
            child: Column(
              children: [
                for (final option in field.options)
                  RadioListTile<String>(
                    value: option.value,
                    title: Text(option.label ?? option.value),
                    subtitle: option.description == null
                        ? null
                        : Text(option.description!),
                  ),
                if (field.custom)
                  const RadioListTile<String>(
                    value: _kOtherChoice,
                    title: Text('Other…'),
                  ),
              ],
            ),
          ),
        ),
        if (field.custom) _otherReveal(context, field, reduceMotion),
        ..._helperAndError(context, field),
      ],
    );
  }

  Widget _dropdownSelect(
    BuildContext context,
    Api2FormField field,
    bool reduceMotion,
  ) {
    final theme = Theme.of(context);
    final key = field.key;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        DropdownMenu<String>(
          initialSelection: _otherOn.contains(key)
              ? _kOtherChoice
              : _choice[key],
          expandedInsets: EdgeInsets.zero,
          requestFocusOnTap: false,
          label: Text(_label(field)),
          errorText: _errors[key],
          onSelected: (value) {
            if (value == null) return;
            setState(() {
              _errors.remove(key);
              if (value == _kOtherChoice) {
                _otherOn.add(key);
              } else {
                _otherOn.remove(key);
                _choice[key] = value;
              }
            });
          },
          dropdownMenuEntries: [
            for (final option in field.options)
              DropdownMenuEntry(
                value: option.value,
                label: option.label ?? option.value,
                labelWidget: option.description == null
                    ? null
                    : Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(option.label ?? option.value),
                          Text(
                            option.description!,
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
              ),
            if (field.custom)
              const DropdownMenuEntry(value: _kOtherChoice, label: 'Other…'),
          ],
        ),
        if (field.custom) _otherReveal(context, field, reduceMotion),
        ..._helperAndError(context, field),
      ],
    );
  }

  /// The "Other…" free-text reveal, animated exactly like a `when` slot.
  Widget _otherReveal(
    BuildContext context,
    Api2FormField field,
    bool reduceMotion,
  ) {
    return AnimatedSize(
      duration: reduceMotion
          ? Duration.zero
          : const Duration(milliseconds: 240),
      curve: Curves.easeOutCubic,
      alignment: Alignment.topCenter,
      child: _otherOn.contains(field.key)
          ? Padding(
              padding: const EdgeInsets.only(top: 10),
              child: TextField(
                key: Key('form-field-${field.key}-other'),
                controller: _otherText[field.key],
                onChanged: (_) => _clearError(field.key),
                decoration: const InputDecoration(
                  border: OutlineInputBorder(),
                  labelText: 'Your answer',
                ),
              ),
            )
          : const SizedBox(width: double.infinity, height: 0),
    );
  }

  // ---------------- number / integer ----------------

  Widget _numberField(BuildContext context, Api2FormField field) {
    final isInteger = field.type == Api2FormFieldType.integer;
    return TextField(
      controller: _text[field.key],
      keyboardType: TextInputType.numberWithOptions(
        decimal: !isInteger,
        signed: true,
      ),
      inputFormatters: [
        if (isInteger)
          FilteringTextInputFormatter.digitsOnly
        else
          FilteringTextInputFormatter.allow(RegExp(r'[0-9.\-]')),
      ],
      onChanged: (_) => _clearError(field.key),
      decoration: InputDecoration(
        border: const OutlineInputBorder(),
        labelText: _label(field),
        hintText: field.placeholder,
        helperText: field.description,
        helperMaxLines: 4,
        errorText: _errors[field.key],
        errorMaxLines: 3,
      ),
    );
  }

  static String _formatNum(num value) {
    if (value is int || value == value.roundToDouble()) {
      return value.toInt().toString();
    }
    return value.toString();
  }

  // ---------------- boolean ----------------

  Widget _booleanField(BuildContext context, Api2FormField field) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _groupSurface(
          context,
          SwitchListTile(
            value: _toggle[field.key] ?? false,
            title: Text(_label(field)),
            subtitle: field.description == null
                ? null
                : Text(field.description!),
            onChanged: (value) => setState(() {
              _errors.remove(field.key);
              _toggle[field.key] = value;
            }),
          ),
        ),
      ],
    );
  }

  // ---------------- multiselect ----------------

  String? _pickCaption(Api2FormField field) {
    final minItems = field.minItems;
    final maxItems = field.maxItems;
    if (minItems == null && maxItems == null) return null;
    final range = minItems != null && maxItems != null
        ? 'Pick $minItems–$maxItems'
        : minItems != null
        ? 'Pick at least $minItems'
        : 'Pick up to $maxItems';
    final count = _multiselectValues(field).length;
    return count > 0 ? '$range · $count selected' : range;
  }

  void _toggleOption(Api2FormField field, String value, bool selected) {
    setState(() {
      _errors.remove(field.key);
      final set = _selected.putIfAbsent(field.key, () => <String>{});
      selected ? set.add(value) : set.remove(value);
    });
  }

  void _addCustomValue(Api2FormField field) {
    final controller = _addText[field.key];
    final value = controller?.text.trim() ?? '';
    if (value.isEmpty) return;
    setState(() {
      _errors.remove(field.key);
      final added = _added.putIfAbsent(field.key, () => <String>[]);
      final duplicate =
          added.contains(value) ||
          (_selected[field.key]?.contains(value) ?? false);
      if (!duplicate) added.add(value);
      controller?.clear();
    });
  }

  Widget _customChips(Api2FormField field) {
    final added = _added[field.key] ?? const <String>[];
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        for (final value in added)
          InputChip(
            label: Text(value),
            onDeleted: () => setState(() {
              _errors.remove(field.key);
              _added[field.key]?.remove(value);
            }),
          ),
      ],
    );
  }

  Widget _addYourOwn(Api2FormField field) => Padding(
    padding: const EdgeInsets.only(top: 10),
    child: TextField(
      key: Key('form-field-${field.key}-add'),
      controller: _addText[field.key],
      onSubmitted: (_) => _addCustomValue(field),
      decoration: InputDecoration(
        border: const OutlineInputBorder(),
        labelText: 'Add your own',
        suffixIcon: IconButton(
          tooltip: 'Add answer',
          icon: const Icon(Icons.add_rounded),
          onPressed: () => _addCustomValue(field),
        ),
      ),
    ),
  );

  Widget _multiselectChips(BuildContext context, Api2FormField field) {
    final theme = Theme.of(context);
    final selected = _selected[field.key] ?? const <String>{};
    final caption = _pickCaption(field);
    final added = _added[field.key] ?? const <String>[];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _groupLabel(context, field),
        if (caption != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              caption,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final option in field.options)
              FilterChip(
                label: Text(option.label ?? option.value),
                tooltip: option.description,
                selected: selected.contains(option.value),
                onSelected: (value) =>
                    _toggleOption(field, option.value, value),
              ),
          ],
        ),
        if (added.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: _customChips(field),
          ),
        if (field.custom) _addYourOwn(field),
        ..._helperAndError(context, field),
      ],
    );
  }

  Widget _multiselectChecklist(BuildContext context, Api2FormField field) {
    final theme = Theme.of(context);
    final selected = _selected[field.key] ?? const <String>{};
    final caption = _pickCaption(field);
    final added = _added[field.key] ?? const <String>[];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _groupLabel(context, field),
        if (caption != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              caption,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        _groupSurface(
          context,
          Column(
            children: [
              for (final option in field.options)
                CheckboxListTile(
                  value: selected.contains(option.value),
                  title: Text(option.label ?? option.value),
                  subtitle: option.description == null
                      ? null
                      : Text(option.description!),
                  controlAffinity: ListTileControlAffinity.leading,
                  onChanged: (value) =>
                      _toggleOption(field, option.value, value ?? false),
                ),
            ],
          ),
        ),
        if (added.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: _customChips(field),
          ),
        if (field.custom) _addYourOwn(field),
        ..._helperAndError(context, field),
      ],
    );
  }

  // ---------------- external ----------------

  /// The URL is server-supplied, so it goes through the same gate as a
  /// markdown link: https (or confirmed http) only, no embedded credentials,
  /// and the real host shown before anything opens. The caption names that
  /// host instead of the old generic "Opens in your browser", and a link the
  /// policy refuses says so on the card rather than presenting a tap target
  /// that silently does nothing.
  Widget _externalCard(BuildContext context, Api2FormField field) {
    final theme = Theme.of(context);
    final destination = safeExternalLinkUri(field.url);
    return Material(
      key: const Key('form-external-card'),
      color: theme.colorScheme.surfaceContainerHigh,
      borderRadius: BorderRadius.circular(16),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => unawaited(openExternalLink(context, field.url)),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      field.title ?? 'Open link',
                      style: theme.textTheme.titleSmall,
                    ),
                    if (field.description != null) ...[
                      const SizedBox(height: 2),
                      Text(
                        field.description!,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                    const SizedBox(height: 6),
                    Text(
                      destination == null
                          ? 'This server sent a link this app will not open.'
                          : 'Opens ${externalLinkHost(destination)} in your '
                                'browser',
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: destination == null
                            ? theme.colorScheme.error
                            : theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              Icon(Icons.open_in_new_rounded, color: theme.colorScheme.primary),
            ],
          ),
        ),
      ),
    );
  }

  Widget _unknownField(BuildContext context, Api2FormField field) {
    final theme = Theme.of(context);
    return Text(
      'This server sent a field type this app does not understand '
      '("${field.key}").',
      style: theme.textTheme.bodySmall?.copyWith(
        color: theme.colorScheme.onSurfaceVariant,
      ),
    );
  }

  // ---------------- banner + apply bar ----------------

  Widget _errorBanner(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      key: const Key('form-error-banner'),
      color: scheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              Icons.error_outline_rounded,
              size: 18,
              color: scheme.onErrorContainer,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                _bannerError ?? '',
                style: Theme.of(
                  context,
                ).textTheme.bodySmall?.copyWith(color: scheme.onErrorContainer),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _applyBar(BuildContext context) {
    final theme = Theme.of(context);
    return Material(
      key: const Key('form-apply-bar'),
      color: theme.colorScheme.surfaceContainerHigh,
      elevation: 6,
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
          child: Row(
            children: [
              TextButton(
                key: const Key('form-cancel'),
                onPressed: _busy ? null : _dismiss,
                child: const Text('Dismiss'),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: FilledButton(
                  key: const Key('form-submit'),
                  onPressed: _busy ? null : _submit,
                  child: _busy
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('Send answers'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
