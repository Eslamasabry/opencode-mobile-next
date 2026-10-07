import 'dart:async';
import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter/material.dart';

import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_chip.dart';
import 'package:opencode_mobile/ui/kit/kit_date_time_picker.dart';
import 'package:opencode_mobile/ui/kit/kit_field.dart';
import 'package:opencode_mobile/ui/kit/kit_icon.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_notice.dart';
import 'package:opencode_mobile/ui/kit/kit_row.dart';
import 'package:opencode_mobile/ui/kit/kit_row_parts.dart';
import 'package:opencode_mobile/ui/kit/kit_sheet.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';
import 'package:opencode_mobile/ui/kit/motion/kit_reveal.dart';

import '../../domain/form_request.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import 'external_link.dart';
import 'product_states.dart'
    show productErrorDetails, productErrorKind, productErrorText;
import 'request_routes.dart';

/// Delivers the assembled answer payload (active fields only) to the caller.
/// Throwing keeps the form open and surfaces the message in the error notice
/// (`form-error-banner`); Send answers is the retry. Returning normally
/// closes the form.
typedef FormRendererSubmit = Future<void> Function(Map<String, dynamic> answer);

/// Called after the user confirms "Dismiss". Throwing keeps the question
/// open with the kit's error notice and Try again.
typedef FormRendererCancel = Future<void> Function();

/// True when [form] opens as a full-height sheet (an end-side sheet on a
/// wide window) instead of a content-height one: five or more DECLARED
/// fields, or any declared field whose description runs past ~140
/// characters. The count deliberately ignores `when` activity so a form
/// never jumps between presentations as conditions toggle.
bool formPrefersFullScreen(Api2FormInfo form) =>
    form.fields.length >= 5 ||
    form.fields.any((field) => (field.description?.length ?? 0) > 140);

/// Forgets every form's kept answers (tests only).
@visibleForTesting
void debugForgetFormAnswers() => _FormAnswers._kept.clear();

/// Presents [form] in the kit's one sheet frame ([showKitSheet]): content
/// height for four or fewer declared fields, full height otherwise, with
/// Send answers and Decline this request pinned under the fields.
/// Completes when the sheet closes.
///
/// Draft carry: what the person typed and chose is kept per form until it
/// is sent or declined, so swipe, back, Esc and close all close silently and reopening the form brings the answers back. With
/// [profileId], multiline answers are also saved as a [KitDraft]
/// scoped to the request and schema, and survive a restart.
Future<void> presentForm(
  BuildContext context, {
  required Api2FormInfo form,
  required FormRendererSubmit onSubmit,
  required FormRendererCancel onCancel,
  RequestRoutes? routes,
  String? profileId,
  String? requestKey,
}) async {
  final answers = _FormAnswers.open(
    form,
    profileId: profileId,
    requestKey: requestKey,
  );
  final l10n = _sharedCopy(context);
  var open = true;
  void close() {
    if (open && context.mounted) Navigator.of(context).pop();
  }

  void submit() =>
      unawaited(answers.submit(context, onSubmit: onSubmit, onClose: close));

  try {
    await showKitSheet<void>(
      context,
      title: form.title ?? l10n.e7SharedInputRequested,
      subtitle: _originOf(form, l10n),
      height: formPrefersFullScreen(form)
          ? KitSheetHeight.full
          : KitSheetHeight.content,
      sheetKey: const Key('form-sheet'),
      routes: routes,
      loading: answers.busy,
      primary: KitAction(
        key: const Key('form-submit'),
        label: l10n.e7SharedSendAnswers,
        onPressed: submit,
      ),
      secondary: KitAction(
        key: const Key('form-cancel'),
        label: l10n.formRendererDecline,
        onPressed: () => unawaited(
          answers.dismiss(
            context,
            onCancel: onCancel,
            onClose: close,
            routes: routes,
          ),
        ),
      ),
      body: (_) => _FormBody(answers: answers),
    );
  } finally {
    open = false;
  }
}

/// The one shared renderer for `Form.Info` (protocol notes §8), drawn in
/// the kit's sheet frame ([KitSheet]) for a host that places it itself:
/// pure presentation plus the kept answer state — no networking. Fields map
/// to kit parts, `when` conditions fold slots open and closed with
/// [KitReveal] (drafts of inactive fields are retained but excluded from
/// the payload and validation), and the pinned actions carry Send answers
/// and Decline this request; the header's close (with [onClose]) keeps the
/// answers for later.
class FormRenderer extends StatefulWidget {
  const FormRenderer({
    super.key,
    required this.form,
    required this.onSubmit,
    required this.onCancel,
    this.onClose,
    this.routes,
    this.profileId,
    this.requestKey,
  });

  final Api2FormInfo form;
  final FormRendererSubmit onSubmit;
  final FormRendererCancel onCancel;

  /// Invoked after a successful submit, a confirmed cancel and the header's
  /// close; the presenting surface pops itself here.
  final VoidCallback? onClose;

  /// Also retires nested decisions when the presenting request is invalidated.
  final RequestRoutes? routes;

  /// Saves multiline answers as a [KitDraft] for this server profile.
  final String? profileId;

  /// Captured route identity, shared by the list and chat presenters.
  final String? requestKey;

  @override
  State<FormRenderer> createState() => _FormRendererState();
}

class _FormRendererState extends State<FormRenderer> {
  late _FormAnswers _answers = _FormAnswers.open(
    widget.form,
    profileId: widget.profileId,
    requestKey: widget.requestKey,
  );

  @override
  void didUpdateWidget(FormRenderer old) {
    super.didUpdateWidget(old);
    if (_answers.key !=
        _FormAnswers.keyFor(
          widget.form,
          profileId: widget.profileId,
          requestKey: widget.requestKey,
        )) {
      _answers = _FormAnswers.open(
        widget.form,
        profileId: widget.profileId,
        requestKey: widget.requestKey,
      );
    }
  }

  void _submit() => unawaited(
    _answers.submit(context, onSubmit: widget.onSubmit, onClose: _closed),
  );

  void _closed() {
    if (mounted) widget.onClose?.call();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _sharedCopy(context);
    final onClose = widget.onClose;
    return ValueListenableBuilder<bool>(
      valueListenable: _answers.busy,
      builder: (context, busy, _) => KitSheet(
        key: const Key('form-sheet'),
        handle: false,
        title: widget.form.title ?? l10n.e7SharedInputRequested,
        subtitle: _originOf(widget.form, l10n),
        loading: busy,
        onClose: onClose,
        primary: KitAction(
          key: const Key('form-submit'),
          label: l10n.e7SharedSendAnswers,
          working: busy,
          onPressed: _submit,
        ),
        secondary: KitAction(
          key: const Key('form-cancel'),
          label: l10n.formRendererDecline,
          onPressed: busy
              ? null
              : () => unawaited(
                  _answers.dismiss(
                    context,
                    onCancel: widget.onCancel,
                    onClose: _closed,
                    routes: widget.routes,
                  ),
                ),
          disabledReason: busy ? l10n.formRendererSending : null,
        ),
        child: _FormBody(answers: _answers),
      ),
    );
  }
}

String _originOf(Api2FormInfo form, AppLocalizations l10n) =>
    form.sessionID == 'global'
    ? l10n.e7SharedAskedByAnMCPServer
    : l10n.e7SharedAskedByTheAgentInThisSession;

/// Sentinel option value for the revealed "Other…" free-text choice.
const _kOtherChoice = '\u0000form-renderer-other';

/// The reveal target of the send-failure notice.
const _kBannerTarget = '\u0000form-renderer-banner';

/// One form's answers, kept per captured route and schema from the first time it
/// opens until it is sent or dismissed — the draft carry of P7.1.
class _FormAnswers extends ChangeNotifier {
  _FormAnswers(this.form, {required this.key, this.profileId}) {
    for (final field in form.fields) {
      _seedField(field);
    }
  }

  /// Kept answers by request identity and schema, oldest first.
  static final Map<String, _FormAnswers> _kept = {};

  /// More open forms than this at once is not a real situation; the oldest
  /// kept answers go first.
  static const _keepAtMost = 8;

  /// The answers kept for [form] since it was last closed, or fresh ones
  /// when there are none or the form's fields changed.
  static String keyFor(
    Api2FormInfo form, {
    String? profileId,
    String? requestKey,
  }) => sha256
      .convert(
        utf8.encode(
          jsonEncode([
            requestKey,
            profileId,
            form.sessionID,
            form.id,
            formSchemaRevision(form),
          ]),
        ),
      )
      .toString();

  static _FormAnswers open(
    Api2FormInfo form, {
    String? profileId,
    String? requestKey,
  }) {
    final key = keyFor(form, profileId: profileId, requestKey: requestKey);
    final answers =
        _kept.remove(key) ??
        _FormAnswers(snapshotForm(form), key: key, profileId: profileId);
    _kept[key] = answers;
    while (_kept.length > _keepAtMost) {
      _kept.remove(_kept.keys.first);
    }
    return answers;
  }

  final Api2FormInfo form;
  final String key;
  final String? profileId;

  /// Free string and number text.
  final Map<String, TextEditingController> text = {};

  /// The revealed "Other…" free text of custom selects.
  final Map<String, TextEditingController> otherText = {};

  /// The "Add your own" composer of custom multiselects.
  final Map<String, TextEditingController> addText = {};

  /// Chosen option value of select (string-with-options) fields.
  final Map<String, String?> choice = {};

  /// Select fields currently on their "Other…" choice.
  final Set<String> otherOn = {};

  /// Boolean switch values.
  final Map<String, bool> toggle = {};

  /// Selected option values per multiselect field.
  final Map<String, Set<String>> selected = {};

  /// User-added custom values per multiselect field, in add order.
  final Map<String, List<String>> added = {};

  /// ISO value backing date / date-time fields.
  final Map<String, String> dateIso = {};

  final Map<String, KitDraft> _drafts = {};

  final Map<String, String> errors = {};
  final ValueNotifier<bool> busy = ValueNotifier(false);
  String? bannerError;
  Object? bannerCause;

  /// A slot (a field key or [_kBannerTarget]) the body scrolls into view.
  String? pendingReveal;

  void _seedField(Api2FormField field) {
    final key = field.key;
    final fallback = field.defaultValue;
    switch (field.type) {
      case Api2FormFieldType.string:
        if (field.options.isNotEmpty) {
          if (field.custom) otherText[key] = TextEditingController();
          if (fallback is String) {
            if (field.options.any((option) => option.value == fallback)) {
              choice[key] = fallback;
            } else if (field.custom) {
              otherOn.add(key);
              otherText[key]!.text = fallback;
            }
          }
        } else if (isDate(field)) {
          if (fallback is String) {
            final parsed = DateTime.tryParse(fallback);
            if (parsed != null) {
              dateIso[key] = _isoOf(parsed, withTime: isDateTime(field));
            }
          }
        } else {
          text[key] = TextEditingController(
            text: fallback is String ? fallback : '',
          );
        }
      case Api2FormFieldType.number:
      case Api2FormFieldType.integer:
        text[key] = TextEditingController(
          text: fallback is num ? formatNum(fallback) : '',
        );
      case Api2FormFieldType.boolean:
        toggle[key] = fallback is bool ? fallback : false;
      case Api2FormFieldType.multiselect:
        final chosen = <String>{};
        final own = <String>[];
        if (fallback is List) {
          for (final value in fallback.whereType<String>()) {
            if (field.options.any((option) => option.value == value)) {
              chosen.add(value);
            } else if (field.custom && !own.contains(value)) {
              own.add(value);
            }
          }
        }
        selected[key] = chosen;
        added[key] = own;
        if (field.custom) addText[key] = TextEditingController();
      case Api2FormFieldType.external:
      case Api2FormFieldType.unknown:
        break;
    }
  }

  /// The [KitDraft] of a multiline answer, when a profile is known.
  KitDraft? draftFor(Api2FormField field) {
    final profileId = this.profileId;
    final controller = text[field.key];
    if (profileId == null || profileId.isEmpty || controller == null) {
      return null;
    }
    return _drafts.putIfAbsent(
      field.key,
      () => KitDraft(
        target: 'form.$key.${field.key}',
        profileId: profileId,
        controller: controller,
      ),
    );
  }

  /// The answers were sent or the request dismissed: nothing is kept.
  void forget() {
    if (identical(_kept[key], this)) _kept.remove(key);
    for (final draft in _drafts.values) {
      unawaited(draft.clear());
    }
  }

  // ---------------- Edits ----------------

  void _changed(String key) {
    errors.remove(key);
    notifyListeners();
  }

  /// Typed text changed (the controller already holds it).
  void edited(String key) => _changed(key);

  void choose(String key, String value) {
    if (value == _kOtherChoice) {
      otherOn.add(key);
    } else {
      otherOn.remove(key);
      choice[key] = value;
    }
    _changed(key);
  }

  void setToggle(String key, bool value) {
    toggle[key] = value;
    _changed(key);
  }

  void setDate(Api2FormField field, DateTime value) {
    dateIso[field.key] = _isoOf(value, withTime: isDateTime(field));
    _changed(field.key);
  }

  void toggleOption(String key, String value) {
    final set = selected.putIfAbsent(key, () => <String>{});
    if (!set.remove(value)) set.add(value);
    _changed(key);
  }

  void addCustom(String key) {
    final controller = addText[key];
    final value = controller?.text.trim() ?? '';
    if (value.isEmpty) return;
    final own = added.putIfAbsent(key, () => <String>[]);
    final duplicate =
        own.contains(value) || (selected[key]?.contains(value) ?? false);
    if (!duplicate) own.add(value);
    controller?.clear();
    _changed(key);
  }

  void removeCustom(String key, String value) {
    added[key]?.remove(value);
    _changed(key);
  }

  String? takeReveal() {
    final target = pendingReveal;
    pendingReveal = null;
    return target;
  }

  // ---------------- Answer + activity evaluation ----------------

  static bool isDate(Api2FormField field) =>
      field.format == 'date' || field.format == 'date-time';

  static bool isDateTime(Api2FormField field) => field.format == 'date-time';

  /// Walks the declared fields once, accumulating the live answers of active
  /// fields. `when` references only ever point at earlier fields, so a single
  /// forward pass settles activity: a field whose controller went inactive
  /// drops out of the map, deactivating its own dependents in cascade
  /// ("unanswered reference ⇒ condition false").
  ({Set<String> active, Map<String, dynamic> answers}) evaluate() {
    final active = <String>{};
    final answers = <String, dynamic>{};
    for (final field in form.fields) {
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
          if (otherOn.contains(key)) {
            final value = otherText[key]?.text.trim() ?? '';
            return value.isEmpty ? null : value;
          }
          return choice[key];
        }
        if (isDate(field)) return dateIso[key];
        final value = text[key]?.text.trim() ?? '';
        return value.isEmpty ? null : value;
      case Api2FormFieldType.number:
        return num.tryParse(text[key]?.text.trim() ?? '');
      case Api2FormFieldType.integer:
        return int.tryParse(text[key]?.text.trim() ?? '');
      case Api2FormFieldType.boolean:
        return toggle[key] ?? false;
      case Api2FormFieldType.multiselect:
        final values = multiselectValues(field);
        return values.isEmpty ? null : values;
      case Api2FormFieldType.external:
      case Api2FormFieldType.unknown:
        return null;
    }
  }

  /// Selected option values in declaration order, then custom additions.
  List<String> multiselectValues(Api2FormField field) {
    final chosen = selected[field.key] ?? const <String>{};
    return [
      for (final option in field.options)
        if (chosen.contains(option.value)) option.value,
      ...?added[field.key],
    ];
  }

  // ---------------- Validation ----------------

  /// Local mirror of the schema, applied to ACTIVE fields only — inactive
  /// fields are neither required nor answerable. `external` never blocks.
  Map<String, String> _validate(
    AppLocalizations l10n,
    Set<String> active,
    Map<String, dynamic> answers,
  ) {
    final found = <String, String>{};
    for (final field in form.fields) {
      if (!active.contains(field.key)) continue;
      final error = _validateField(l10n, field, answers[field.key]);
      if (error != null) found[field.key] = error;
    }
    return found;
  }

  String? _validateField(
    AppLocalizations l10n,
    Api2FormField field,
    dynamic answer,
  ) {
    switch (field.type) {
      case Api2FormFieldType.string:
        final value = answer as String?;
        if (value == null) {
          return field.required ? l10n.e7SharedRequired : null;
        }
        final minLength = field.minLength;
        if (minLength != null && value.length < minLength) {
          return l10n.e7SharedDetail714(minLength);
        }
        final maxLength = field.maxLength;
        if (maxLength != null && value.length > maxLength) {
          return l10n.e7SharedDetail715(maxLength);
        }
        final pattern = field.pattern;
        if (pattern != null && !RegExp(pattern).hasMatch(value)) {
          return l10n.e7SharedDoesNotMatchTheExpectedFormat;
        }
        return null;
      case Api2FormFieldType.number:
      case Api2FormFieldType.integer:
        final raw = text[field.key]?.text.trim() ?? '';
        if (raw.isEmpty) {
          return field.required ? l10n.e7SharedRequired : null;
        }
        if (answer is! num) {
          return field.type == Api2FormFieldType.integer
              ? l10n.e7SharedEnterAWholeNumber
              : l10n.e7SharedEnterANumber;
        }
        final minimum = field.minimum;
        final maximum = field.maximum;
        if ((minimum != null && answer < minimum) ||
            (maximum != null && answer > maximum)) {
          if (minimum != null && maximum != null) {
            return l10n.e7SharedDetail721(
              formatNum(minimum),
              formatNum(maximum),
            );
          }
          return minimum != null
              ? l10n.e7SharedDetail722(formatNum(minimum))
              : l10n.e7SharedDetail723(formatNum(maximum!));
        }
        return null;
      case Api2FormFieldType.multiselect:
        final count = multiselectValues(field).length;
        if (count == 0) {
          return field.required ? l10n.e7SharedRequired : null;
        }
        final minItems = field.minItems;
        if (minItems != null && count < minItems) {
          return l10n.e7SharedDetail754(minItems);
        }
        final maxItems = field.maxItems;
        if (maxItems != null && count > maxItems) {
          return l10n.e7SharedDetail726(maxItems);
        }
        return null;
      case Api2FormFieldType.boolean:
      case Api2FormFieldType.external:
      case Api2FormFieldType.unknown:
        return null;
    }
  }

  // ---------------- Submit / cancel ----------------

  Future<void> submit(
    BuildContext context, {
    required FormRendererSubmit onSubmit,
    required VoidCallback onClose,
  }) async {
    if (busy.value) return;
    final eval = evaluate();
    final found = _validate(_sharedCopy(context), eval.active, eval.answers);
    errors
      ..clear()
      ..addAll(found);
    bannerError = null;
    bannerCause = null;
    if (found.isNotEmpty) {
      pendingReveal = form.fields
          .firstWhere((field) => found.containsKey(field.key))
          .key;
      notifyListeners();
      return;
    }
    notifyListeners();
    busy.value = true;
    try {
      await onSubmit(Map<String, dynamic>.of(eval.answers));
      forget();
      onClose();
    } catch (error) {
      bannerError = _messageOf(error);
      bannerCause = error;
      pendingReveal = _kBannerTarget;
      notifyListeners();
    } finally {
      busy.value = false;
    }
  }

  Future<void> dismiss(
    BuildContext context, {
    required FormRendererCancel onCancel,
    required VoidCallback onClose,
    RequestRoutes? routes,
  }) async {
    if (busy.value) return;
    final l10n = _sharedCopy(context);
    final confirmed = await showKitConfirm(
      context,
      title: l10n.e7SharedDismissThisRequest,
      body: l10n.e7SharedTheAgentContinuesWithoutYourAnswers,
      confirmLabel: l10n.formRendererDecline,
      kind: KitConfirmKind.destructive,
      icon: AppIconography.blocked,
      sheetKey: const Key('form-dismiss-confirm'),
      confirmKey: const Key('form-dismiss-confirm-button'),
      routes: routes,
      action: onCancel,
    );
    if (!confirmed) return;
    forget();
    onClose();
  }

  /// The failure in words; the raw text stays in [bannerCause] for the
  /// notice's Copy details and Report, never as the words.
  static String _messageOf(Object error) => productErrorText(error);

  static String _two(int value) => value.toString().padLeft(2, '0');

  static String _isoOf(DateTime value, {required bool withTime}) => withTime
      ? value.toIso8601String()
      : '${value.year}-${_two(value.month)}-${_two(value.day)}';

  static String formatNum(num value) {
    if (value is int || value == value.roundToDouble()) {
      return value.toInt().toString();
    }
    return value.toString();
  }
}

/// The fields and the send-failure notice, laid out for a [KitSheet] body.
class _FormBody extends StatefulWidget {
  const _FormBody({required this.answers});

  final _FormAnswers answers;

  @override
  State<_FormBody> createState() => _FormBodyState();
}

class _FormBodyState extends State<_FormBody> {
  /// One slot key per declared field, for scroll-to-first-error.
  final Map<String, GlobalKey> _slotKeys = {};
  final GlobalKey _bannerKey = GlobalKey();

  _FormAnswers get _answers => widget.answers;

  @override
  void initState() {
    super.initState();
    _answers.addListener(_onChange);
  }

  @override
  void didUpdateWidget(_FormBody old) {
    super.didUpdateWidget(old);
    if (!identical(old.answers, widget.answers)) {
      old.answers.removeListener(_onChange);
      widget.answers.addListener(_onChange);
    }
  }

  @override
  void dispose() {
    _answers.removeListener(_onChange);
    super.dispose();
  }

  void _onChange() {
    if (!mounted) return;
    setState(() {});
    final target = _answers.takeReveal();
    if (target == null) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final key = target == _kBannerTarget ? _bannerKey : _slotKeys[target];
      final slot = key?.currentContext;
      if (slot == null) return;
      unawaited(
        Scrollable.ensureVisible(
          slot,
          alignment: .1,
          duration: KitMotion.reduced(context)
              ? Duration.zero
              : KitMotion.standard,
          curve: KitMotion.enter,
        ),
      );
    });
  }

  GlobalKey _slotKey(String key) => _slotKeys.putIfAbsent(key, GlobalKey.new);

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final active = _answers.evaluate().active;
    final banner = _answers.bannerError;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final field in _answers.form.fields)
          KitReveal(
            child: active.contains(field.key)
                ? KeyedSubtree(
                    key: _slotKey(field.key),
                    child: Padding(
                      padding: EdgeInsetsDirectional.only(
                        bottom: tokens.space5,
                      ),
                      child: KeyedSubtree(
                        key: Key('form-field-${field.key}'),
                        child: _field(context, field),
                      ),
                    ),
                  )
                : null,
          ),
        KitReveal(
          child: banner == null
              ? null
              : KeyedSubtree(
                  key: _bannerKey,
                  child: KitNotice.error(
                    key: const Key('form-error-banner'),
                    message: banner,
                    error: _answers.bannerCause,
                    errorKind: productErrorKind(_answers.bannerCause),
                    details: productErrorDetails(_answers.bannerCause),
                    reportSource: 'form-sheet',
                  ),
                ),
        ),
      ],
    );
  }

  Widget _field(BuildContext context, Api2FormField field) =>
      switch (field.type) {
        Api2FormFieldType.string when field.options.isNotEmpty =>
          field.options.length <= 4
              ? _choiceRows(context, field)
              : _pickerRow(context, field),
        Api2FormFieldType.string when _FormAnswers.isDate(field) => _dateRow(
          context,
          field,
        ),
        Api2FormFieldType.string => _stringField(context, field),
        Api2FormFieldType.number ||
        Api2FormFieldType.integer => _numberField(context, field),
        Api2FormFieldType.boolean => _booleanRow(context, field),
        Api2FormFieldType.multiselect =>
          field.options.length >= 9 ||
                  field.options.any((option) => option.description != null)
              ? _checklist(context, field)
              : _chips(context, field),
        Api2FormFieldType.external => _externalRow(context, field),
        Api2FormFieldType.unknown => KitText(
          _sharedCopy(context).e7SharedDetail765(field.key),
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
      };

  String _label(Api2FormField field) =>
      '${field.title ?? field.key}${field.required ? ' *' : ''}';

  /// Description and inline error for the controls that are not a
  /// [KitField] (which draws both itself).
  List<Widget> _helperAndError(
    BuildContext context,
    Api2FormField field, {
    bool description = true,
  }) {
    final tokens = KitTokens.of(context);
    final error = _answers.errors[field.key];
    final helper = field.description;
    return [
      if (description && helper != null)
        Padding(
          padding: EdgeInsetsDirectional.only(top: tokens.space2),
          child: KitText(
            helper,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
        ),
      if (error != null)
        Semantics(
          liveRegion: true,
          child: Padding(
            padding: EdgeInsetsDirectional.only(top: tokens.space2),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const KitIcon(
                  AppIconography.error,
                  size: KitIconSize.small,
                  tone: KitTextTone.primary,
                ),
                SizedBox(width: tokens.space2),
                Expanded(
                  child: KitText(
                    error,
                    role: KitTextRole.secondary,
                    tone: KitTextTone.primary,
                  ),
                ),
              ],
            ),
          ),
        ),
    ];
  }

  /// A row that is one choice of a set: a radio or a check mark at the
  /// start, the state spoken.
  Widget _markRow({
    required Key key,
    required String title,
    required bool selected,
    required bool exclusive,
    required VoidCallback onTap,
    String? description,
  }) {
    final mark = exclusive
        ? (selected ? AppIconography.radioSelected : AppIconography.radioEmpty)
        : (selected
              ? AppIconography.checkboxChecked
              : AppIconography.checkboxEmpty);
    return MergeSemantics(
      child: Semantics(
        inMutuallyExclusiveGroup: exclusive,
        checked: selected,
        child: KitRow(
          key: key,
          title: title,
          titleMaxLines: 3,
          supporting: description == null ? null : TextSpan(text: description),
          supportingMaxLines: 4,
          leading: KitIcon(
            mark,
            size: KitIconSize.large,
            tone: selected ? KitTextTone.primary : KitTextTone.secondary,
          ),
          onTap: onTap,
        ),
      ),
    );
  }

  // ---------------- string ----------------

  Widget _stringField(BuildContext context, Api2FormField field) {
    final multiline = field.maxLength == null || field.maxLength! > 120;
    final kind = multiline
        ? KitFieldKind.multiline
        : field.format == 'uri'
        ? KitFieldKind.url
        : KitFieldKind.text;
    final controller = _answers.text[field.key];
    return KitField(
      label: _label(field),
      controller: controller,
      kind: kind,
      hint: field.placeholder,
      helper: field.description,
      error: _answers.errors[field.key],
      maxLength: field.maxLength,
      draft: multiline ? _answers.draftFor(field) : null,
      onChanged: (_) => _answers.edited(field.key),
    );
  }

  // ---------------- string · date / date-time ----------------

  String _humanDate(BuildContext context, Api2FormField field) {
    final iso = _answers.dateIso[field.key];
    final value = iso == null ? null : DateTime.tryParse(iso);
    final l10n = _sharedCopy(context);
    if (value == null) {
      return _FormAnswers.isDateTime(field)
          ? l10n.formRendererChooseDateTime
          : l10n.formRendererChooseDate;
    }
    final material = MaterialLocalizations.of(context);
    final date = material.formatShortDate(value);
    if (!_FormAnswers.isDateTime(field)) return date;
    final time = material.formatTimeOfDay(
      TimeOfDay.fromDateTime(value),
      alwaysUse24HourFormat: MediaQuery.alwaysUse24HourFormatOf(context),
    );
    return l10n.formRendererDateAndTime(date, time);
  }

  Widget _dateRow(BuildContext context, Api2FormField field) {
    final dateTime = _FormAnswers.isDateTime(field);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitRowGroup(
          margin: EdgeInsets.zero,
          children: [
            KitRow(
              key: Key('form-field-${field.key}-pick'),
              leading: KitRow.icon(
                context,
                dateTime ? AppIconography.clock : AppIconography.calendar,
              ),
              title: _label(field),
              titleMaxLines: 3,
              supporting: TextSpan(text: _humanDate(context, field)),
              supportingMaxLines: 2,
              trailing: const KitChevron(),
              onTap: () => unawaited(_pickDate(field)),
            ),
          ],
        ),
        ..._helperAndError(context, field),
      ],
    );
  }

  /// The kit's date sheet (the calendar, then the time entry for a
  /// date-time field), never the stock Material dialogs.
  Future<void> _pickDate(Api2FormField field) async {
    final l10n = _sharedCopy(context);
    final current = DateTime.tryParse(_answers.dateIso[field.key] ?? '');
    final value = _FormAnswers.isDateTime(field)
        ? await showKitDateTimePicker(
            context,
            title: _label(field),
            initial: current,
            first: DateTime(1900),
            last: DateTime(2100),
            sheetKey: Key('form-field-${field.key}-picker'),
          )
        : await showKitDatePicker(
            context,
            title: _label(field),
            initial: current,
            first: DateTime(1900),
            last: DateTime(2100),
            confirmLabel: l10n.formRendererUseDate,
            sheetKey: Key('form-field-${field.key}-picker'),
          );
    if (value == null || !mounted) return;
    _answers.setDate(field, value);
  }

  // ---------------- string · options ----------------

  String _optionLabel(Api2FormOption option) => option.label ?? option.value;

  Widget _choiceRows(BuildContext context, Api2FormField field) {
    final key = field.key;
    final other = _answers.otherOn.contains(key);
    final chosen = other ? _kOtherChoice : _answers.choice[key];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitRowGroup(
          label: _label(field),
          leadingIcons: false,
          margin: EdgeInsets.zero,
          children: [
            for (final option in field.options)
              _markRow(
                key: Key('form-option-$key-${option.value}'),
                title: _optionLabel(option),
                description: option.description,
                selected: chosen == option.value,
                exclusive: true,
                onTap: () => _answers.choose(key, option.value),
              ),
            if (field.custom)
              _markRow(
                key: Key('form-option-$key-other'),
                title: _sharedCopy(context).e7SharedOther,
                selected: other,
                exclusive: true,
                onTap: () => _answers.choose(key, _kOtherChoice),
              ),
          ],
        ),
        if (field.custom) _otherReveal(context, field),
        ..._helperAndError(context, field),
      ],
    );
  }

  /// Five or more options: one row naming the choice, opening the kit menu.
  Widget _pickerRow(BuildContext context, Api2FormField field) {
    final key = field.key;
    final l10n = _sharedCopy(context);
    final other = _answers.otherOn.contains(key);
    final chosen = _answers.choice[key];
    String? chosenLabel;
    if (other) {
      chosenLabel = l10n.e7SharedOther;
    } else {
      for (final option in field.options) {
        if (option.value == chosen) chosenLabel = _optionLabel(option);
      }
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitRowGroup(
          leadingIcons: false,
          margin: EdgeInsets.zero,
          children: [
            Builder(
              builder: (anchor) => KitRow(
                key: Key('form-field-$key-pick'),
                title: _label(field),
                titleMaxLines: 3,
                supporting: TextSpan(
                  text: chosenLabel ?? l10n.formRendererChoose,
                ),
                trailing: const KitChevron(),
                onTap: () => unawaited(
                  showKitMenu(
                    anchor,
                    semanticsLabel: _label(field),
                    items: [
                      for (final option in field.options)
                        KitMenuItem(
                          key: Key('form-option-$key-${option.value}'),
                          label: _optionLabel(option),
                          checked: !other && chosen == option.value,
                          onSelected: () => _answers.choose(key, option.value),
                        ),
                      if (field.custom)
                        KitMenuItem(
                          key: Key('form-option-$key-other'),
                          label: l10n.e7SharedOther,
                          checked: other,
                          onSelected: () => _answers.choose(key, _kOtherChoice),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
        if (field.custom) _otherReveal(context, field),
        ..._helperAndError(context, field),
      ],
    );
  }

  /// The "Other…" free-text reveal, folded like a `when` slot.
  Widget _otherReveal(BuildContext context, Api2FormField field) {
    final tokens = KitTokens.of(context);
    return KitReveal(
      child: _answers.otherOn.contains(field.key)
          ? Padding(
              padding: EdgeInsetsDirectional.only(top: tokens.space3),
              child: KitField(
                key: Key('form-field-${field.key}-other'),
                label: _sharedCopy(context).e7SharedYourAnswer,
                controller: _answers.otherText[field.key],
                onChanged: (_) => _answers.edited(field.key),
              ),
            )
          : null,
    );
  }

  // ---------------- number / integer ----------------

  Widget _numberField(BuildContext context, Api2FormField field) {
    return KitField(
      label: _label(field),
      controller: _answers.text[field.key],
      kind: KitFieldKind.number,
      decimal: field.type == Api2FormFieldType.number,
      hint: field.placeholder,
      helper: field.description,
      error: _answers.errors[field.key],
      onChanged: (_) => _answers.edited(field.key),
    );
  }

  // ---------------- boolean ----------------

  Widget _booleanRow(BuildContext context, Api2FormField field) {
    return KitRowGroup(
      leadingIcons: false,
      margin: EdgeInsets.zero,
      children: [
        KitSwitchRow(
          title: _label(field),
          supporting: field.description,
          value: _answers.toggle[field.key] ?? false,
          onChanged: (value) => _answers.setToggle(field.key, value),
        ),
      ],
    );
  }

  // ---------------- multiselect ----------------

  String? _pickCaption(BuildContext context, Api2FormField field) {
    final l10n = _sharedCopy(context);
    final minItems = field.minItems;
    final maxItems = field.maxItems;
    if (minItems == null && maxItems == null) return null;
    final range = minItems != null && maxItems != null
        ? l10n.e7SharedDetail753(minItems, maxItems)
        : minItems != null
        ? l10n.e7SharedDetail754(minItems)
        : l10n.e7SharedDetail755(maxItems!);
    final count = _answers.multiselectValues(field).length;
    return count > 0 ? l10n.e7SharedDetail756(range, count) : range;
  }

  List<Widget> _ownValues(BuildContext context, Api2FormField field) {
    final tokens = KitTokens.of(context);
    final l10n = _sharedCopy(context);
    final own = _answers.added[field.key] ?? const <String>[];
    return [
      if (own.isNotEmpty)
        Padding(
          padding: EdgeInsetsDirectional.only(top: tokens.space3),
          child: KitChipWrap(
            children: [
              for (final value in own)
                KitChip.removable(
                  key: Key('form-own-${field.key}-$value'),
                  label: value,
                  onRemove: () => _answers.removeCustom(field.key, value),
                ),
            ],
          ),
        ),
      if (field.custom)
        Padding(
          padding: EdgeInsetsDirectional.only(top: tokens.space3),
          child: KitField(
            key: Key('form-field-${field.key}-add'),
            label: l10n.e7SharedAddYourOwn,
            controller: _answers.addText[field.key],
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _answers.addCustom(field.key),
            action: KitAction(
              label: l10n.e7SharedAddAnswer,
              icon: AppIconography.add,
              onPressed: () => _answers.addCustom(field.key),
            ),
            actionKey: Key('form-field-${field.key}-add-button'),
          ),
        ),
    ];
  }

  Widget _chips(BuildContext context, Api2FormField field) {
    final tokens = KitTokens.of(context);
    final chosen = _answers.selected[field.key] ?? const <String>{};
    final caption = _pickCaption(context, field);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Semantics(
          header: true,
          child: KitText(_label(field), role: KitTextRole.label),
        ),
        if (caption != null)
          Padding(
            padding: EdgeInsetsDirectional.only(top: tokens.space1),
            child: KitText(
              caption,
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
          ),
        SizedBox(height: tokens.space2),
        KitChipWrap(
          children: [
            for (final option in field.options)
              KitChip.action(
                key: Key('form-option-${field.key}-${option.value}'),
                label: _optionLabel(option),
                selected: chosen.contains(option.value),
                onPressed: () => _answers.toggleOption(field.key, option.value),
              ),
          ],
        ),
        ..._ownValues(context, field),
        ..._helperAndError(context, field),
      ],
    );
  }

  Widget _checklist(BuildContext context, Api2FormField field) {
    final chosen = _answers.selected[field.key] ?? const <String>{};
    final caption = _pickCaption(context, field);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitRowGroup(
          label: _label(field),
          labelTrailing: caption == null
              ? null
              : KitText(
                  caption,
                  role: KitTextRole.caption,
                  tone: KitTextTone.secondary,
                ),
          leadingIcons: false,
          margin: EdgeInsets.zero,
          children: [
            for (final option in field.options)
              _markRow(
                key: Key('form-option-${field.key}-${option.value}'),
                title: _optionLabel(option),
                description: option.description,
                selected: chosen.contains(option.value),
                exclusive: false,
                onTap: () => _answers.toggleOption(field.key, option.value),
              ),
          ],
        ),
        ..._ownValues(context, field),
        ..._helperAndError(context, field),
      ],
    );
  }

  // ---------------- external ----------------

  /// The URL is server-supplied, so it goes through the same gate as a
  /// markdown link: https (or confirmed http) only, no embedded credentials,
  /// and the real host shown before anything opens. The row names that host,
  /// and a link the policy refuses says so on the row rather than presenting
  /// a tap target that silently does nothing.
  Widget _externalRow(BuildContext context, Api2FormField field) {
    final l10n = _sharedCopy(context);
    final destination = safeExternalLinkUri(field.url);
    return KitRowGroup(
      margin: EdgeInsets.zero,
      children: [
        KitRow(
          key: const Key('form-external-card'),
          leading: KitRow.icon(context, AppIconography.externalLink),
          title: field.title ?? l10n.e7SharedOpenLink,
          titleMaxLines: 3,
          supporting: field.description == null
              ? null
              : TextSpan(text: field.description),
          supportingMaxLines: 4,
          below: KitText(
            destination == null
                ? l10n.e7SharedThisServerSentALinkThisApp
                : l10n.e7SharedDetail764(externalLinkHost(destination)),
            role: KitTextRole.caption,
            tone: KitTextTone.secondary,
          ),
          trailing: const KitChevron(),
          onTap: () => unawaited(openExternalLink(context, field.url)),
        ),
      ],
    );
  }
}

AppLocalizations _sharedCopy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));
