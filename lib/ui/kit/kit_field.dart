import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../../l10n/app_localizations_en.dart';
import '../app_iconography.dart';
import 'kit_buttons.dart';
import 'kit_icon_button.dart';
import 'kit_layout.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_redact.dart';
import 'kit_sheet.dart' show KitDraft;
import 'kit_since.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_reveal.dart';
import 'style/kit_style.dart' show kitStyleBoundary, kitStyleSpec;

/// What the field holds (docs/ux-system/kit-api/KitField.md). It decides the
/// keyboard, direction, face and input rules; never the look.
enum KitFieldKind {
  /// Words in the person's language; follows the locale.
  text,

  /// Grows from 3 to 8 lines (4 on a short window), then scrolls; Enter
  /// inserts a line and Ctrl/Cmd+Enter submits.
  multiline,

  /// A code, a command, an id: the mono face, left to right, no autocorrect.
  mono,

  /// A file or folder path: mono, left to right, no autocorrect or
  /// suggestions.
  path,

  /// An address: mono, left to right, the url keyboard, no autocorrect.
  url,

  /// Digits only (one decimal separator with `decimal`). Arabic-Indic digits
  /// typed are normalised to ASCII in the value.
  number,

  /// Only through [KitField.secret].
  secret,
}

/// The frame the kit's fields paint as their own look (Solid's): a [fill],
/// an [edge] inside (or, [outside], around) [radius] corners, a hairline or,
/// [thick], the focus ring's two pixels, and any [shadows]. [KitField],
/// [KitSearchField] and [KitAskField] pass it to the style resolver as
/// `solid:`, so every other style turns it into its field
/// (`KitSurfaceRole.field`). A null [edge] draws no line.
BoxDecoration kitFieldFrame(
  BuildContext context, {
  Color? fill,
  Color? edge,
  bool thick = false,
  required BorderRadius radius,
  bool outside = false,
  List<BoxShadow>? shadows,
}) => BoxDecoration(
  color: fill,
  borderRadius: radius,
  border: edge == null
      ? null
      : Border.all(
          color: edge,
          width: thick
              ? KitTokens.focusRingWidth(context)
              : KitTokens.hairlineWidth(context),
          strokeAlign: outside
              ? BorderSide.strokeAlignOutside
              : BorderSide.strokeAlignInside,
        ),
  boxShadow: shadows,
);

/// [child], a kit field painted in [look], with a ring of [color] (null:
/// none) the focus ring's width just outside it: keyboard focus, or an
/// error. Only once the style resolver made [look] out of the field's [own]
/// frame, which draws focus in its own edge; the ring follows [look]'s
/// corners. The ring is always in the tree, so focusing a field never
/// rebuilds what it holds.
Widget kitFieldRing(
  BuildContext context, {
  required Decoration own,
  required Decoration look,
  required Color? color,
  required Widget child,
}) => DecoratedBox(
  position: DecorationPosition.foreground,
  decoration: kitFieldFrame(
    context,
    edge: identical(look, own) ? null : color,
    thick: true,
    radius: KitSurfaceDecoration.radiusOf(look) ?? BorderRadius.zero,
    outside: true,
  ),
  child: child,
);

/// The halo a glowing field (the ask field, the start box) casts around
/// its face [look]: [halo] (the field's own shadows) where the style keeps a
/// field halo ([KitStyleTokens.fieldHalo]; by default as its glow's halo:
/// Solid and Soft), none elsewhere (Brutal's ink, Paper's print).
/// Outside a styled face only, so a clear face never takes its tint; a
/// plain face takes it as a plain shadow in [radius] corners. Paint it
/// behind the field, which paints [look] over it.
Decoration kitFieldHalo(
  BuildContext context, {
  required Decoration look,
  required BorderRadius radius,
  required List<BoxShadow> halo,
}) {
  final style = KitSurfaceLook.styleOf(context);
  final shadows = kitStyleSpec(style).tokens.fieldHaloShown
      ? halo
      : const <BoxShadow>[];
  return look is KitSurfaceDecoration
      ? KitSurfaceDecoration(
          borderRadius: radius,
          fill: Colors.transparent,
          shadows: shadows,
        )
      : kitFieldFrame(context, radius: radius, shadows: shadows);
}

enum _KitFieldMode { plain, composer, secret }

enum _CounterPhase { hidden, near, limit }

/// The one labelled text input for everything except search (kit-v2 §1.4,
/// KIT-20, KIT-21): a visible [label] above the field, a helper or error
/// line under it, a counter only near the limit, and the secret kind that is
/// the only way to type a credential. It also carries the [draft] and the
/// 8 s "Checking…" escalation, so no screen hand-builds either.
///
/// States: empty, error, disabled, working.
///
/// Field states (KitField.md): `default`, `focused`, `filled`, `error`,
/// `disabled`, `checking`, `checking-slow`, `counter` (from 80 % of
/// [maxLength]), `limit`. The secret kind adds `secret-masked`,
/// `secret-revealed` and `secret-saved`; the multiline kind adds
/// `multiline-draft-restored`.
///
/// - Loading: none of its own; a field whose value is being fetched is not
///   shown until its host has loaded.
/// - Empty: the label shows, and [hint] may show an example.
/// - Error: [error] (or a framework `Form` [validator] message) replaces the
///   helper under the field with a neutral glyph and the word, in a live
///   region announced once per new message.
/// - Disabled: the input is `text3`, [disabledReason] shows in the helper
///   line, and the trailing actions are disabled with the same reason.
/// - Working: [checkingSince] says "Checking…" in the helper line (never a
///   spinner in the field); after [KitMotion.escalateAfter] it says "Still
///   checking after 8 s" once, and offers [onSlow].
///
/// No styling parameters: the look comes from the theme roles, [KitText]
/// and [KitTokens].
class KitField extends StatefulWidget {
  const KitField({
    super.key,
    required this.label,
    this.controller,
    this.kind = KitFieldKind.text,
    this.hint,
    this.helper,
    this.error,
    this.maxLength,
    this.maxLines,
    this.minLines,
    this.decimal = false,
    this.draft,
    this.enabled = true,
    this.disabledReason,
    this.checkingSince,
    this.onSlow = const [],
    this.action,
    this.validator,
    this.inputFormatters = const [],
    this.onChanged,
    this.onSubmitted,
    this.textInputAction,
    this.focusNode,
    this.autofocus = false,
    this.selectAllOnFocus = false,
    this.fieldKey,
    this.actionKey,
    this.optional = false,
  }) : assert(enabled || disabledReason != null),
       assert(kind != KitFieldKind.secret, 'use KitField.secret'),
       saved = false,
       onReplace = null,
       contentInsertion = null,
       revealKey = null,
       pasteKey = null,
       replaceKey = null,
       _mode = _KitFieldMode.plain;

  /// The chat composer's field (KitComposer.md, decision D18): multiline,
  /// [label] is the accessible name but is not drawn, no helper, error or
  /// counter line, and no frame of its own (the composer draws it). A G2
  /// pattern allows `KitField.composer(` only in
  /// lib/ui/kit/chat/kit_composer.dart; it is KIT-20's one exception.
  const KitField.composer({
    super.key,
    required this.label,
    required TextEditingController this.controller,
    this.hint,
    this.focusNode,
    this.onChanged,
    this.onSubmitted,
    this.contentInsertion,
    this.maxLines,
    this.enabled = true,
    this.disabledReason,
    this.fieldKey,
  }) : assert(enabled || disabledReason != null),
       kind = KitFieldKind.multiline,
       minLines = null,
       helper = null,
       error = null,
       maxLength = null,
       decimal = false,
       draft = null,
       checkingSince = null,
       onSlow = const [],
       action = null,
       saved = false,
       onReplace = null,
       validator = null,
       inputFormatters = const [],
       textInputAction = null,
       autofocus = false,
       selectAllOnFocus = false,
       actionKey = null,
       revealKey = null,
       pasteKey = null,
       replaceKey = null,
       optional = false,
       _mode = _KitFieldMode.composer;

  /// Obscured, with a reveal toggle and a Paste button. Never prefilled:
  /// the field asserts an empty controller when it mounts. No suggestions,
  /// autocorrect, IME learning or autofill. Left to right and isolated. The
  /// value is never the semantics value while masked, never in this
  /// widget's diagnostics, and never handed to a copy part. [saved]: a value
  /// is stored already, so the field shows "Saved · Replace" and no input
  /// until the person chooses Replace.
  const KitField.secret({
    super.key,
    required this.label,
    this.controller,
    this.hint,
    this.helper,
    this.error,
    this.saved = false,
    this.onReplace,
    this.enabled = true,
    this.disabledReason,
    this.checkingSince,
    this.onSlow = const [],
    this.validator,
    this.inputFormatters = const [],
    this.onChanged,
    this.onSubmitted,
    this.textInputAction,
    this.focusNode,
    this.autofocus = false,
    this.fieldKey,
    this.revealKey,
    this.pasteKey,
    this.replaceKey,
  }) : assert(enabled || disabledReason != null),
       kind = KitFieldKind.secret,
       maxLength = null,
       maxLines = null,
       minLines = null,
       decimal = false,
       draft = null,
       action = null,
       selectAllOnFocus = false,
       contentInsertion = null,
       actionKey = null,
       optional = false,
       _mode = _KitFieldMode.secret;

  /// Shown above the field; the field's semantic label.
  final String label;

  /// Null: the field owns one (or uses [draft]'s controller).
  final TextEditingController? controller;
  final KitFieldKind kind;

  /// An example only ("my-app"); never instead of [label].
  final String? hint;

  /// At most two lines of guidance; wraps, never cut.
  final String? helper;

  /// Replaces [helper]; a live region. Words that say what to change
  /// ("Enter a port from 1 to 65535"), never exception or server text.
  final String? error;

  /// The limit; the counter appears from 80 % of it.
  final int? maxLength;

  /// Null: one line, or 3→8 growing for [KitFieldKind.multiline].
  final int? maxLines;

  /// A one-line kind with [maxLines] over 1: 1 lets the field start at one
  /// line and grow to [maxLines] as the value wraps (a long command read
  /// whole), where null keeps it [maxLines] tall. Ignored for
  /// [KitFieldKind.multiline], which grows from 3.
  final int? minLines;

  /// [KitFieldKind.number] only: allow one decimal separator.
  final bool decimal;

  /// Restores and saves the typed text (DATA-1, DATA-2); required for a
  /// multiline field inside a sheet (G48).
  final KitDraft? draft;
  final bool enabled;

  /// Why it is disabled, shown in the helper line (STATE-8); required when
  /// not [enabled].
  final String? disabledReason;

  /// Non-null while the value is being checked; escalates after 8 s.
  final DateTime? checkingSince;

  /// At most two ways out offered once a check has run 8 s.
  final List<KitAction> onSlow;

  /// One icon action at the end (Browse, Paste); needs an icon.
  final KitAction? action;

  /// [KitField.secret]: a value is stored already.
  final bool saved;

  /// [KitField.secret]: called when the person chooses Replace.
  final VoidCallback? onReplace;

  /// For use inside a framework `Form`; its message shows as [error].
  final FormFieldValidator<String>? validator;
  final List<TextInputFormatter> inputFormatters;
  final ValueChanged<String>? onChanged;

  /// Single-line: Enter or the IME action. Multiline: Ctrl/Cmd+Enter.
  final ValueChanged<String>? onSubmitted;
  final TextInputAction? textInputAction;
  final FocusNode? focusNode;
  final bool autofocus;

  /// Selects the whole text when the field gains focus (a prefilled name).
  final bool selectAllOnFocus;

  /// [KitField.composer] only: images from the keyboard.
  final ContentInsertionConfiguration? contentInsertion;

  /// On the inner editable.
  final Key? fieldKey;
  final Key? actionKey;

  /// A field the person may leave empty ("Anything to add?"): a quiet
  /// "Optional" at the end of the label's line. Only the plain field.
  final bool optional;
  final Key? revealKey;
  final Key? pasteKey;
  final Key? replaceKey;

  final _KitFieldMode _mode;

  bool get _isSecret => _mode == _KitFieldMode.secret;

  @override
  State<KitField> createState() => _KitFieldState();

  @override
  void debugFillProperties(DiagnosticPropertiesBuilder properties) {
    super.debugFillProperties(properties);
    // Never the controller or its text: a secret's value stays out of
    // diagnostics (SEC-2).
    properties
      ..add(StringProperty('label', label))
      ..add(EnumProperty<KitFieldKind>('kind', kind))
      ..add(FlagProperty('enabled', value: enabled, ifFalse: 'disabled'));
  }
}

class _KitFieldState extends State<KitField> {
  TextEditingController? _ownController;
  FocusNode? _ownFocus;
  TextEditingController? _listening;

  /// The secret modes' editable controller, kept in step with [_controller]
  /// both ways; its diagnostics never carry the text, so element dumps and
  /// the inspector show no secret (SEC-2).
  _RedactedController? _secretEdit;
  FocusNode? _focusListening;
  String _lastText = '';
  bool _revealed = false;
  bool _hovered = false;
  bool _replacing = false;
  _CounterPhase _phase = _CounterPhase.hidden;
  DateTime? _slowAnnouncedFor;

  /// This field's place in a framework `Form` when it has a [validator].
  FormFieldState<String>? _formField;

  TextEditingController get _controller =>
      widget.draft?.controller ??
      widget.controller ??
      (_ownController ??= TextEditingController());

  /// What the editable edits: the host's controller, or in the secret modes
  /// a redacted twin of it.
  TextEditingController get _editController =>
      widget._isSecret ? _secretEdit! : _controller;

  FocusNode get _focus => widget.focusNode ?? (_ownFocus ??= FocusNode());

  bool get _showsSaved => widget.saved && !_replacing;

  @override
  void initState() {
    super.initState();
    _checkStructure();
    assert(
      widget._mode != _KitFieldMode.secret ||
          (widget.controller?.text.isEmpty ?? true),
      'KitField.secret is never prefilled: pass an empty controller and '
      'saved: true when a value is stored (SEC-3, G9)',
    );
    _attach();
    _phase = _phaseFor(_controller.text.characters.length);
    final draft = widget.draft;
    if (draft != null) unawaited(draft.restore());
  }

  // Asserts the constructor cannot make (not constant expressions).
  void _checkStructure() {
    assert(
      widget.draft == null ||
          widget.controller == null ||
          identical(widget.controller, widget.draft!.controller),
      'KitField: a draft brings its own controller',
    );
    assert(
      widget.onSlow.length <= 2,
      'KitField offers at most two ways out of a slow check (STATE-5)',
    );
    assert(
      widget.action == null || widget.action!.icon != null,
      'KitField.action is an icon button: give the action an icon',
    );
  }

  @override
  void didUpdateWidget(KitField old) {
    super.didUpdateWidget(old);
    _checkStructure();
    _attach();
    if (old.checkingSince != widget.checkingSince) _slowAnnouncedFor = null;
    if (old.saved != widget.saved) _replacing = false;
  }

  void _attach() {
    final controller = _controller;
    if (!identical(controller, _listening)) {
      _listening
        ?..removeListener(_onText)
        ..removeListener(_hostToEdit);
      _listening = controller
        ..addListener(_onText)
        ..addListener(_hostToEdit);
      _lastText = controller.text;
    }
    if (widget._isSecret && _secretEdit == null) {
      _secretEdit = _RedactedController()..addListener(_editToHost);
    }
    _hostToEdit();
    final focus = _focus;
    if (!identical(focus, _focusListening)) {
      _focusListening?.removeListener(_onFocus);
      _focusListening = focus..addListener(_onFocus);
    }
  }

  @override
  void dispose() {
    _listening
      ?..removeListener(_onText)
      ..removeListener(_hostToEdit);
    _focusListening?.removeListener(_onFocus);
    _secretEdit?.dispose();
    _ownController?.dispose();
    _ownFocus?.dispose();
    super.dispose();
  }

  void _hostToEdit() {
    final edit = _secretEdit;
    if (edit != null && edit.value != _controller.value) {
      edit.value = _controller.value;
    }
  }

  void _editToHost() {
    final edit = _secretEdit!;
    // Register before notifying the host controller or field callbacks.
    KitRedact.registerKnownSecret(edit.text);
    if (_controller.value != edit.value) _controller.value = edit.value;
  }

  void _onFocus() {
    if (!mounted) return;
    setState(() {});
    if (widget.selectAllOnFocus && _focus.hasFocus) {
      // After the tap that focused it has placed the caret.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || !_focus.hasFocus) return;
        final text = _controller.text;
        _controller.selection = TextSelection(
          baseOffset: 0,
          extentOffset: text.length,
        );
      });
    }
  }

  void _onText() {
    final text = _controller.text;
    if (widget._isSecret) KitRedact.registerKnownSecret(text);
    if (text == _lastText) return;
    _lastText = text;
    final draft = widget.draft;
    if (draft != null) unawaited(draft.save());
    _formField?.didChange(text);
    final phase = _phaseFor(text.characters.length);
    if (phase != _phase) {
      final rising = phase.index > _phase.index;
      _phase = phase;
      if (rising && mounted) {
        _announce(_counterText(context, text.characters.length));
      }
    }
    if (mounted) setState(() {});
  }

  _CounterPhase _phaseFor(int length) {
    final max = widget.maxLength;
    if (max == null || max <= 0) return _CounterPhase.hidden;
    if (length >= max) return _CounterPhase.limit;
    // From 80 % of the limit (KIT-21), in whole characters.
    if (length * 5 >= max * 4) return _CounterPhase.near;
    return _CounterPhase.hidden;
  }

  AppLocalizations _l10n(BuildContext context) => _kitFieldWords(context);

  String _counterText(BuildContext context, int length) {
    final l10n = _l10n(context);
    final max = widget.maxLength!;
    return length >= max
        ? l10n.kitFieldLimitReached
        : l10n.kitFieldCount(length, max);
  }

  void _announce(String message) {
    final view = View.maybeOf(context);
    if (view == null) return;
    unawaited(
      SemanticsService.sendAnnouncement(
        view,
        message,
        Directionality.maybeOf(context) ?? TextDirection.ltr,
      ),
    );
  }

  void _submit() {
    if (!widget.enabled) return;
    widget.onSubmitted?.call(_controller.text);
  }

  Future<void> _paste() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final pasted = data?.text;
    if (pasted == null || pasted.isEmpty || !mounted) return;
    final value = _controller.value;
    final selection = value.selection;
    final String text;
    final int caret;
    if (selection.isValid) {
      text = value.text.replaceRange(selection.start, selection.end, pasted);
      caret = selection.start + pasted.length;
    } else {
      text = value.text + pasted;
      caret = text.length;
    }
    // The same fold EditableText applies to a keyboard or toolbar paste:
    // every formatter sees the value before the paste as the old value.
    final next = _formatters.fold<TextEditingValue>(
      TextEditingValue(
        text: text,
        selection: TextSelection.collapsed(offset: caret),
      ),
      (next, formatter) => formatter.formatEditUpdate(value, next),
    );
    if (next == value) return;
    _controller.value = next;
    if (next.text != value.text) widget.onChanged?.call(next.text);
  }

  void _replace() {
    _controller.clear();
    setState(() => _replacing = true);
    widget.onReplace?.call();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _focus.requestFocus();
    });
  }

  bool get _technical => switch (widget.kind) {
    KitFieldKind.mono ||
    KitFieldKind.path ||
    KitFieldKind.url ||
    KitFieldKind.secret => true,
    _ => false,
  };

  TextInputType get _keyboard => switch (widget.kind) {
    KitFieldKind.multiline => TextInputType.multiline,
    KitFieldKind.url => TextInputType.url,
    KitFieldKind.number => TextInputType.numberWithOptions(
      decimal: widget.decimal,
    ),
    KitFieldKind.secret => TextInputType.visiblePassword,
    _ => TextInputType.text,
  };

  bool get _multiline => widget.kind == KitFieldKind.multiline;

  /// The editable's input rules, in order; Paste runs through the same
  /// chain so it lands exactly as a keyboard paste would.
  List<TextInputFormatter> get _formatters => [
    if (widget.kind == KitFieldKind.number)
      KitNumberFormatter(decimal: widget.decimal),
    ...widget.inputFormatters,
    if (widget.maxLength != null)
      LengthLimitingTextInputFormatter(widget.maxLength),
  ];

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    if (widget.validator == null) {
      _formField = null;
      return _buildField(context, null);
    }
    // The validator runs here, not in the inner TextFormField, so its
    // message lands in this field's own error line and the inner
    // decorator never animates an error of its own (MOT-7).
    return FormField<String>(
      validator: widget.validator,
      initialValue: _controller.text,
      enabled: widget.enabled,
      builder: (field) {
        _formField = field;
        return _buildField(context, field.errorText);
      },
    );
  }

  Widget _buildField(BuildContext context, String? formError) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = _l10n(context);
    final ambient = Directionality.of(context);
    final composer = widget._mode == _KitFieldMode.composer;
    final error = composer ? null : (widget.error ?? formError);

    // ── The editable ──────────────────────────────────────────────────────
    final baseRole = _technical ? KitTextRole.mono : KitTextRole.body;
    final style = KitText.styleOf(
      context,
      baseRole,
      tone: widget.enabled ? KitTextTone.primary : KitTextTone.tertiary,
    );
    final hintStyle = KitText.styleOf(
      context,
      baseRole,
      tone: KitTextTone.tertiary,
    );
    final int? minLines;
    final int? maxLines;
    if (composer) {
      minLines = 1;
      maxLines = widget.maxLines ?? 8;
    } else if (_multiline) {
      final cap = widget.maxLines ?? (KitLayout.isShort(context) ? 4 : 8);
      maxLines = cap;
      minLines = cap < 3 ? cap : 3;
    } else {
      maxLines = widget.maxLines ?? 1;
      minLines = maxLines > 1 ? widget.minLines : null;
    }
    final formatters = _formatters;
    final secret = widget._isSecret;
    final hasTrailing = (secret && !_showsSaved) || widget.action != null;
    // A single line is laid out on a line box at least [minTarget] tall, so
    // the editable itself (its semantics node and its touch area) is a
    // 48 dp target (LAY-9) and its text sits centred in the frame.
    final singleLine = !composer && !_multiline && maxLines == 1;
    final scaler = MediaQuery.textScalerOf(context);
    final fontSize = style.fontSize!;
    final scaledFont = scaler.scale(fontSize);
    final scaledLine = scaler.scale(fontSize * style.height!);
    final lineBox = scaledLine > tokens.minTarget
        ? scaledLine
        : tokens.minTarget;
    final vertical = singleLine
        ? ((tokens.buttonHeight - tokens.minTarget) / 2).floorToDouble()
        : ((tokens.buttonHeight - fontSize * style.height!) / 2)
              .floorToDouble();
    // The composer's one line sits centred on its row's 48 dp controls.
    final composerPad = math.max(0.0, (tokens.minTarget - scaledLine) / 2);
    final plainWords =
        widget.kind == KitFieldKind.text ||
        widget.kind == KitFieldKind.multiline;
    // A TextFormField (a TextField inside a FormField), so the field takes
    // part in a framework Form and [fieldKey] finds a TextFormField.
    Widget editable = TextFormField(
      key: widget.fieldKey,
      controller: _editController,
      focusNode: _focus,
      enabled: widget.enabled,
      autofocus: widget.autofocus,
      obscureText: secret && !_revealed,
      autocorrect: plainWords && !secret,
      enableSuggestions: plainWords && !secret,
      enableIMEPersonalizedLearning: !secret,
      smartDashesType: plainWords
          ? SmartDashesType.enabled
          : SmartDashesType.disabled,
      smartQuotesType: plainWords
          ? SmartQuotesType.enabled
          : SmartQuotesType.disabled,
      textCapitalization: plainWords
          ? TextCapitalization.sentences
          : TextCapitalization.none,
      keyboardType: _keyboard,
      textInputAction:
          widget.textInputAction ??
          (_multiline ? TextInputAction.newline : null),
      textDirection: _technical ? TextDirection.ltr : null,
      // A technical value is laid out left to right but sits at the start
      // of the field in either reading direction (COPY-30, KIT-32).
      textAlign: _technical && ambient == TextDirection.rtl
          ? TextAlign.end
          : TextAlign.start,
      minLines: minLines,
      maxLines: maxLines,
      inputFormatters: formatters,
      onChanged: widget.onChanged,
      onFieldSubmitted: _multiline ? null : (_) => _submit(),
      contentInsertionConfiguration: widget.contentInsertion,
      style: style,
      strutStyle: singleLine
          ? StrutStyle(
              fontFamily: style.fontFamily,
              fontFamilyFallback: style.fontFamilyFallback,
              fontSize: fontSize,
              height: lineBox / scaledFont,
              forceStrutHeight: true,
              leadingDistribution: TextLeadingDistribution.even,
            )
          : null,
      cursorHeight: singleLine ? scaledLine : null,
      cursorColor: roles.accent,
      decoration:
          InputDecoration.collapsed(
            hintText: widget.hint,
            hintStyle: hintStyle,
          ).copyWith(
            // The composer's hint is one line beside "+" and Send: at a large
            // text size it ends in an ellipsis instead of growing the row.
            hintMaxLines: composer ? 1 : (_multiline ? 3 : 1),
            hintFadeDuration: KitMotion.reduced(context) ? Duration.zero : null,
            // No label is drawn inside; this keeps the decorator from running
            // its label ticker when the field turns non-empty (MOT-7).
            floatingLabelBehavior: FloatingLabelBehavior.never,
            // The frame is the kit's; nothing from the app's input theme
            // draws a second border or fill inside it.
            filled: false,
            hoverColor: Colors.transparent,
            border: InputBorder.none,
            enabledBorder: InputBorder.none,
            focusedBorder: InputBorder.none,
            disabledBorder: InputBorder.none,
            errorBorder: InputBorder.none,
            focusedErrorBorder: InputBorder.none,
            // The composer's field sits on one row with 48 dp controls:
            // its one line is centred on them and the padding is part of the
            // field's own target (LAY-9).
            contentPadding: composer
                ? EdgeInsets.symmetric(vertical: composerPad)
                : hasTrailing
                // The trailing buttons bring their own 48 dp padding.
                ? EdgeInsetsDirectional.only(
                    start: tokens.space4,
                    top: vertical,
                    bottom: vertical,
                  )
                : EdgeInsetsDirectional.only(
                    start: tokens.space4,
                    end: tokens.space4,
                    top: vertical,
                    bottom: vertical,
                  ),
          ),
    );

    if (_multiline) {
      final finePointer = composer && KitLayout.finePointer(context);
      editable = CallbackShortcuts(
        bindings: {
          const SingleActivator(LogicalKeyboardKey.enter, control: true):
              _submit,
          const SingleActivator(LogicalKeyboardKey.enter, meta: true): _submit,
          const SingleActivator(LogicalKeyboardKey.numpadEnter, control: true):
              _submit,
          if (finePointer)
            const SingleActivator(LogicalKeyboardKey.enter): _submit,
        },
        child: editable,
      );
    }

    final hintForSemantics = composer
        ? (widget.enabled ? null : widget.disabledReason)
        : _semanticHint(context, error) ?? widget.hint;
    // The visible label is the field's accessible name (TalkBack reads it
    // before the value), with the example, helper or error as its hint.
    editable = Semantics(
      label: widget.label,
      hint: hintForSemantics,
      child: editable,
    );

    if (composer) return editable;

    // ── The frame ─────────────────────────────────────────────────────────
    final focused = _focus.hasFocus && widget.enabled && !_showsSaved;
    final Color borderColor;
    if (focused) {
      borderColor = roles.accent;
    } else if (error != null) {
      // LOOK-5 interim (owner question B2): the error is neutral text1.
      borderColor = roles.text1;
    } else if (_hovered && widget.enabled) {
      borderColor = roles.text3;
    } else {
      // An empty field has nothing else to show where it is: its edge reads
      // at 3:1 at rest against the page and its own fill (WCAG 1.4.11; a
      // box of several lines since the owner's 2026-10-21 call, one line
      // since audit P2-6, 2026-10-28).
      borderColor = kitStyleBoundary(roles);
    }

    final trailing = <Widget>[
      if (secret && !_showsSaved) ...[
        KitIconButton(
          key: widget.revealKey,
          icon: _revealed ? AppIconography.hidden : AppIconography.visible,
          size: 20,
          tooltip: _revealed
              ? l10n.kitFieldHideNamed(widget.label)
              : l10n.kitFieldShowNamed(widget.label),
          selected: _revealed,
          disabledReason: widget.enabled ? null : widget.disabledReason,
          onPressed: widget.enabled
              ? () => setState(() => _revealed = !_revealed)
              : null,
        ),
        if (widget._mode == _KitFieldMode.secret)
          KitIconButton(
            key: widget.pasteKey,
            icon: AppIconography.paste,
            size: 20,
            tooltip: l10n.kitFieldPaste,
            disabledReason: widget.enabled ? null : widget.disabledReason,
            onPressed: widget.enabled ? () => unawaited(_paste()) : null,
          ),
      ],
      if (widget.action case final action?)
        KitIconButton(
          key: widget.actionKey ?? action.key,
          icon: action.icon!,
          size: 20,
          tooltip: action.label,
          disabledReason: widget.enabled
              ? action.disabledReason
              : widget.disabledReason,
          onPressed: widget.enabled ? action.onPressed : null,
        ),
    ];

    final Widget inside;
    if (secret && _showsSaved) {
      // No editable carries the label here, so the row does: one node that
      // reads "API key, Saved, Replace" with its hint, and whose tap is
      // Replace, so every saved key on a screen sounds different (KIT-20).
      // A disabled Replace brings its own reason as its hint (STATE-8), so
      // the row adds only an error or the helper.
      final hint = _semanticHint(context, error);
      inside = MergeSemantics(
        child: Semantics(
          label: widget.label,
          hint: !widget.enabled && error == null ? null : hint,
          child: Row(
            children: [
              Expanded(
                child: Padding(
                  padding: EdgeInsetsDirectional.only(start: tokens.space4),
                  child: KitText(
                    l10n.kitFieldSaved,
                    role: KitTextRole.body,
                    tone: KitTextTone.secondary,
                  ),
                ),
              ),
              KitButton.fromAction(
                KitAction(
                  key: widget.replaceKey,
                  label: l10n.kitFieldReplace,
                  onPressed: widget.enabled ? _replace : null,
                  disabledReason: widget.enabled ? null : widget.disabledReason,
                ),
                role: KitButtonRole.tertiary,
              ),
              SizedBox(width: tokens.space1),
            ],
          ),
        ),
      );
    } else {
      inside = Row(
        crossAxisAlignment: _multiline
            ? CrossAxisAlignment.start
            : CrossAxisAlignment.center,
        children: [
          Expanded(child: editable),
          ...trailing,
        ],
      );
    }

    // The field's own frame is Solid's; the style resolver turns it into
    // the style's field (a tint and rim, a well, an ink box), with a
    // boundary that reads at 3:1 at rest and its focused answer.
    final ownFrame = kitFieldFrame(
      context,
      fill: roles.surface1,
      edge: borderColor,
      // The 3:1 edge is two pixels wide, so it reads as a box at a glance
      // and keeps its contrast where it falls between two pixel rows (one
      // pixel there blurs to about 1.3:1). Focus answers in its colour.
      thick: true,
      radius: BorderRadius.circular(tokens.fieldCorner),
    );
    final look = KitSurfaceLook.decorate(
      context,
      solid: ownFrame,
      role: KitSurfaceRole.field,
      selected: focused,
      // Solid's own frame shows hover in its edge; a style may answer it.
      hovered: _hovered && widget.enabled && !focused,
    );
    final frame = MouseRegion(
      cursor: widget.enabled && !_showsSaved
          ? SystemMouseCursors.text
          : MouseCursor.defer,
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: kitFieldRing(
        context,
        own: ownFrame,
        look: look,
        // A style draws its own frame, so keyboard focus (and an error)
        // also rings it outside, as the school guides ask (F6).
        color: focused
            ? roles.accent
            : error != null
            ? roles.text1
            : null,
        child: kitSurfaceBox(
          style: KitSurfaceLook.styleOf(context),
          decoration: look,
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: tokens.buttonHeight),
            child: inside,
          ),
        ),
      ),
    );

    // ── The lines under it ────────────────────────────────────────────────
    final length = _controller.text.characters.length;
    final counter = _phaseFor(length) == _CounterPhase.hidden || _showsSaved
        ? null
        : KitText(
            _counterText(context, length),
            role: KitTextRole.caption,
            tabular: true,
          );

    Widget? errorLine;
    if (error != null) {
      errorLine = Semantics(
        container: true,
        liveRegion: true,
        label: '${l10n.kitFieldErrorLabel}: $error',
        child: ExcludeSemantics(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                AppIconography.error,
                size: tokens.iconSize(context, tokens.smallIconSize),
                color: roles.text1,
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
      );
    }

    Widget? statusLine;
    if (error == null) {
      final checking = widget.checkingSince;
      if (checking != null) {
        statusLine = KitSince(
          since: checking,
          builder: (context, status) {
            if (status.isSlow && _slowAnnouncedFor != checking) {
              _slowAnnouncedFor = checking;
              final words = l10n.kitFieldStillChecking(
                KitMotion.escalateAfter.inSeconds,
              );
              WidgetsBinding.instance.addPostFrameCallback((_) {
                if (mounted) _announce(words);
              });
            }
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                ExcludeSemantics(
                  child: KitText(
                    status.isSlow
                        ? l10n.kitFieldStillChecking(
                            KitMotion.escalateAfter.inSeconds,
                          )
                        : l10n.kitFieldChecking,
                    role: KitTextRole.secondary,
                  ),
                ),
                if (status.isSlow && widget.onSlow.isNotEmpty)
                  Wrap(
                    spacing: tokens.space2,
                    children: [
                      for (final way in widget.onSlow)
                        KitButton.fromAction(way, role: KitButtonRole.tertiary),
                    ],
                  ),
              ],
            );
          },
        );
      } else {
        final words = widget.enabled ? widget.helper : widget.disabledReason;
        if (words != null && words.isNotEmpty) {
          statusLine = ExcludeSemantics(
            child: KitText(words, role: KitTextRole.secondary),
          );
        }
      }
    }

    Widget? under;
    if (statusLine != null || counter != null) {
      under = Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(child: statusLine ?? const SizedBox.shrink()),
          if (counter != null) ...[SizedBox(width: tokens.space3), counter],
        ],
      );
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // The label is the field's semantic label, so it is not read twice;
        // tapping it focuses the field.
        ExcludeSemantics(
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: widget.enabled && !_showsSaved ? _focus.requestFocus : null,
            child: widget.optional
                ? Row(
                    crossAxisAlignment: CrossAxisAlignment.baseline,
                    textBaseline: TextBaseline.alphabetic,
                    children: [
                      Expanded(
                        child: KitText(widget.label, role: KitTextRole.label),
                      ),
                      SizedBox(width: tokens.space3),
                      KitText(
                        _l10n(context).kitFieldOptional,
                        key: const ValueKey('kit-field-optional'),
                        role: KitTextRole.secondary,
                        tone: KitTextTone.tertiary,
                      ),
                    ],
                  )
                : KitText(widget.label, role: KitTextRole.label),
          ),
        ),
        SizedBox(height: tokens.space2),
        frame,
        KitReveal(
          child: errorLine == null
              ? null
              : Padding(
                  padding: EdgeInsetsDirectional.only(top: tokens.space1),
                  child: errorLine,
                ),
        ),
        KitReveal(
          child: under == null
              ? null
              : Padding(
                  padding: EdgeInsetsDirectional.only(top: tokens.space1),
                  child: under,
                ),
        ),
      ],
    );
  }

  String? _semanticHint(BuildContext context, String? error) {
    if (error != null) return error;
    if (!widget.enabled) return widget.disabledReason;
    if (widget.checkingSince != null) {
      final l10n = _l10n(context);
      return KitSince.statusOf(widget.checkingSince).isSlow
          ? l10n.kitFieldStillChecking(KitMotion.escalateAfter.inSeconds)
          : l10n.kitFieldChecking;
    }
    return widget.helper;
  }
}

/// A controller whose diagnostics name it but never its text.
class _RedactedController extends TextEditingController {
  @override
  String toString() => '${describeIdentity(this)}(redacted)';
}

/// [KitFieldKind.number]'s input rule: digits only (one `.` with
/// [decimal]); Arabic-Indic and Extended Arabic-Indic digits become ASCII,
/// and the Arabic decimal separator and a comma become `.`.
@visibleForTesting
class KitNumberFormatter extends TextInputFormatter {
  KitNumberFormatter({this.decimal = false});

  final bool decimal;

  static String? _normalise(int rune) {
    if (rune >= 0x30 && rune <= 0x39) return String.fromCharCode(rune);
    if (rune >= 0x0660 && rune <= 0x0669) {
      return String.fromCharCode(0x30 + rune - 0x0660);
    }
    if (rune >= 0x06F0 && rune <= 0x06F9) {
      return String.fromCharCode(0x30 + rune - 0x06F0);
    }
    if (rune == 0x2E || rune == 0x2C || rune == 0x066B) return '.';
    return null;
  }

  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    final source = newValue.text;
    final out = StringBuffer();
    var seenPoint = false;
    // Maps each source offset to its offset in [out], for the selection.
    final map = List<int>.filled(source.length + 1, 0);
    var i = 0;
    for (final unit in source.codeUnits) {
      map[i] = out.length;
      final normal = _normalise(unit);
      if (normal == '.') {
        if (decimal && !seenPoint) {
          seenPoint = true;
          out.write(normal);
        }
      } else if (normal != null) {
        out.write(normal);
      }
      i++;
    }
    map[source.length] = out.length;
    final text = out.toString();
    if (text == source) return newValue;
    int at(int offset) =>
        offset < 0 ? offset : map[offset.clamp(0, source.length)];
    return TextEditingValue(
      text: text,
      selection: newValue.selection.copyWith(
        baseOffset: at(newValue.selection.baseOffset),
        extentOffset: at(newValue.selection.extentOffset),
      ),
    );
  }
}

/// The kit's words: the app's bound [AppLocalizations], else the locale's
/// lookup, else English, so the part never throws in a bare harness with no
/// localization delegates (R8).
AppLocalizations _kitFieldWords(BuildContext context) {
  final bound = Localizations.of<AppLocalizations>(context, AppLocalizations);
  if (bound != null) return bound;
  final locale = Localizations.maybeLocaleOf(context);
  if (locale != null && AppLocalizations.delegate.isSupported(locale)) {
    return lookupAppLocalizations(locale);
  }
  return AppLocalizationsEn();
}
