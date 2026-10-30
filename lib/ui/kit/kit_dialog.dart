// KitDialog (docs/ux-system/kit-api/KitDialog.md; kit-v2.md §1.3, §4.6,
// §8.2): the two jobs a dialog still has. One short text entry
// ([showKitInputDialog]) and a blocking alert with at most one action
// ([showKitAlert]). There is no general-purpose dialog and no public widget:
// the frames below are private and open only through the two functions.
//
// Declared states: `input-default`, `input-invalid` (after the first edit:
// the reason under the field, the primary disabled), `input-error` (a
// submit error under the field),
// `input-working`, `input-discard` (the discard question in place),
// `alert`, `alert-with-action`, `alert-with-details`.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import 'kit_buttons.dart';
import 'kit_field.dart';
import 'kit_layout.dart';
import 'kit_motion.dart';
import 'kit_sheet.dart';
import 'kit_surface.dart';
import 'kit_surface_look.dart';
import 'kit_technical_value.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_reveal.dart';

/// One short text entry: a rename, a folder name, a code, a budget.
/// Returns the submitted text, or null on cancel, back, Esc or a tap
/// outside. The field is a [KitField] of [kind].
///
/// - [label] is the field's visible label (never placeholder-only); [hint]
///   is an example; [helper] wraps and is never cut.
/// - [confirmLabel] is a verb naming the act ("Rename", COPY-8).
/// - [initial] is prefilled and selected on open (never with
///   [KitFieldKind.secret], which is never prefilled).
/// - [maxLines] over 1 lets a long value (a shell command) wrap: the field
///   starts at one line and grows to [maxLines], so the whole value reads
///   at once. Enter and the IME action still submit; Shift+Enter starts a
///   new line.
/// - [validate] returns null when the text is valid, the reason otherwise.
///   Nothing is judged before the first edit (the primary stays enabled; a
///   tap on it with invalid text shows the reason instead of submitting).
///   The reason always sits under the field, never under the button, and
///   the primary is disabled while it shows.
/// - [onSubmit] runs with the text: the primary shows it is working, the
///   field and Cancel are disabled, and Esc, back and a tap outside are
///   ignored. A non-null result is an error shown under the field; the text
///   is kept and the dialog stays open. The dialog returns the text only
///   after [onSubmit] returns null.
/// - [alternative] ("Remove budget") sits on its own line; a destructive
///   one stacks the actions on every window. Choosing it closes the dialog
///   (null) and runs it.
/// - [draft] keeps the text across dismissal and a process kill: back, Esc,
///   a swipe, a tap outside and Cancel close silently and reopening
///   restores it. The caller clears it once the text is used. Without a
///   draft, changed text makes back, Esc, a swipe on the handle and a tap
///   outside ask the discard question inside the dialog ("Keep editing" is
///   the default); an explicit Cancel closes.
///
/// It opens in the same frame as [showKitConfirm]: a bottom sheet with a
/// handle on a compact window (the actions stacked full width: primary,
/// alternative, Cancel), capped on a medium one, and a centred panel of at
/// most [KitLayout.confirmDialogWidth] on an expanded or large one, the
/// actions in one end-aligned row. The field and the pinned actions ride
/// above the keyboard. Enter or the IME action submits when valid.
Future<String?> showKitInputDialog(
  BuildContext context, {
  required String title,
  required String label,
  required String confirmLabel,
  String? initial,
  String? hint,
  String? helper,
  KitFieldKind kind = KitFieldKind.text,
  int? maxLength,
  int maxLines = 1,
  String? Function(String value)? validate,
  Future<String?> Function(String value)? onSubmit,
  KitAction? alternative,
  KitDraft? draft,
  String? cancelLabel,
  Key? dialogKey,
  Key? fieldKey,
  Key? confirmKey,
}) async {
  assert(
    kind != KitFieldKind.secret || initial == null,
    'showKitInputDialog: a secret is never prefilled (SEC-3)',
  );
  assert(
    kind != KitFieldKind.secret || draft == null,
    'showKitInputDialog: a secret is never kept in a draft',
  );
  final controller = draft?.controller ?? TextEditingController();
  if (draft != null) {
    await draft.restore();
    if (!context.mounted) return null;
  }
  if (controller.text.isEmpty && initial != null) controller.text = initial;
  if (kind != KitFieldKind.secret && controller.text.isNotEmpty) {
    // Selected on open, before the field takes focus, so no selection
    // handles are raised over the helper (a programmatic select after
    // focus shows them).
    controller.selection = TextSelection(
      baseOffset: 0,
      extentOffset: controller.text.length,
    );
  }
  final spec = _KitInputSpec(
    title: title,
    label: label,
    confirmLabel: confirmLabel,
    initial: initial ?? '',
    hint: hint,
    helper: helper,
    kind: kind,
    maxLength: maxLength,
    maxLines: maxLines,
    validate: validate,
    onSubmit: onSubmit,
    alternative: alternative,
    draft: draft,
    cancelLabel: cancelLabel,
    fieldKey: fieldKey,
    confirmKey: confirmKey,
  );
  return presentKitConfirmFrame<String>(
    context,
    // The dialog decides what a swipe, a tap outside, back and Esc do
    // (unsaved text asks first), so the route's own swipe is off.
    ownsDrag: true,
    builder: (dialogContext) => _KitInputDialog(
      key: dialogKey,
      spec: spec,
      controller: controller,
      // The dialog's state disposes a controller it was lent, after the
      // route's exit fade; a draft's controller stays the caller's.
      ownsController: draft == null,
    ),
  );
}

/// A blocking alert with at most one action. Completes when it closes.
/// Esc, back and Close dismiss it; a tap outside does not (it blocks).
///
/// - [title] is at most four fixed words (COPY-10); [body] at most two
///   sentences.
/// - [details] are technical values in one [KitDetailsFold], last and
///   collapsed.
/// - [action] ("Open Files") closes the alert and then runs. Enter never
///   runs it (it may navigate away): Enter and Esc close.
/// - [closeLabel] defaults to "Close"; [icon] shows in an icon tile beside
///   the title.
Future<void> showKitAlert(
  BuildContext context, {
  required String title,
  required String body,
  List<KitTechnicalValue> details = const [],
  KitAction? action,
  String? closeLabel,
  IconData? icon,
  Key? alertKey,
  Key? closeKey,
}) async {
  await _presentKitDialog<void>(
    context,
    builder: (dialogContext) => _KitAlert(
      key: alertKey,
      title: title,
      body: body,
      details: details,
      action: action,
      closeLabel: closeLabel,
      icon: icon,
      closeKey: closeKey,
    ),
  );
}

AppLocalizations _l10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// Pushes the alert's frame: the scrim (no blur, LOOK-22), a centred panel
/// on `surface2` with the VL dialog radius, cross-faded on
/// [KitMotion.standard] with no scale (MOT-2). A tap outside does nothing:
/// the alert blocks until it is answered.
Future<T?> _presentKitDialog<T>(
  BuildContext context, {
  required Widget Function(BuildContext context) builder,
}) {
  final tokens = KitTokens.of(context);
  final reduced = KitMotion.reduced(context);
  return showGeneralDialog<T>(
    context: context,
    barrierDismissible: false,
    barrierColor: tokens.scrim,
    transitionDuration: reduced ? Duration.zero : KitMotion.standard,
    pageBuilder: (dialogContext, _, _) => _KitDialogFrame(child: builder),
    transitionBuilder: (dialogContext, animation, _, child) => FadeTransition(
      opacity: CurvedAnimation(
        parent: animation,
        curve: KitMotion.enter,
        reverseCurve: KitMotion.exit,
      ),
      child: child,
    ),
  );
}

class _KitDialogFrame extends StatelessWidget {
  const _KitDialogFrame({required this.child});

  final Widget Function(BuildContext context) child;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final media = MediaQuery.of(context);
    final window = KitLayout.modalWindowOf(context);
    final available = media.size.width - 2 * tokens.gutter;
    final width = window == KitWindow.compact
        ? available
        : available.clamp(0.0, KitLayout.confirmDialogWidth);
    final solid = KitSurfaceLook.isPlain(context);
    final radius = BorderRadius.circular(tokens.panelRadius);
    return Stack(
      children: [
        // A tap outside is caught and ignored: the alert blocks.
        PositionedDirectional(
          start: 0,
          end: 0,
          top: 0,
          bottom: 0,
          child: ExcludeSemantics(
            child: GestureDetector(
              key: const ValueKey('kit-dialog-barrier'),
              behavior: HitTestBehavior.opaque,
              onTap: () {},
            ),
          ),
        ),
        Padding(
          padding: EdgeInsets.only(bottom: media.viewInsets.bottom),
          child: SafeArea(
            child: Center(
              child: Padding(
                padding: EdgeInsets.all(tokens.gutter),
                child: SizedBox(
                  width: width.floorToDouble(),
                  child: _surface(
                    context,
                    tokens,
                    Material(
                      color: solid ? tokens.panelSurface : Colors.transparent,
                      elevation: solid ? tokens.panelElevation : 0,
                      clipBehavior: Clip.antiAlias,
                      shape: RoundedRectangleBorder(borderRadius: radius),
                      child: Semantics(
                        scopesRoute: true,
                        explicitChildNodes: true,
                        child: Builder(builder: child),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// The alert's surface for the person's Style (Settings › Appearance):
/// nothing of its own under Solid (the Material paints the panel), the
/// style's sheet surface otherwise (the ground's colour under Soft).
Widget _surface(BuildContext context, KitTokens tokens, Widget material) {
  final style = KitSurfaceLook.styleOf(context);
  final radius = BorderRadius.circular(tokens.panelRadius);
  return kitSurfaceBox(
    style: style,
    decoration: KitSurfaceLook.isPlain(context)
        ? BoxDecoration(borderRadius: radius)
        : KitSurfaceLook.decorate(
            context,
            solid: BoxDecoration(
              color: tokens.panelSurface,
              borderRadius: radius,
            ),
            level: KitSurfaceLevel.surface2,
            role: KitSurfaceRole.sheet,
            borderRadius: radius,
          ),
    child: material,
  );
}

/// The panel's layout: a scrolling body over pinned actions, on the VL
/// dialog inset, so the field and the actions stay reachable on a short
/// window and at 200 % text.
class _KitDialogLayout extends StatelessWidget {
  const _KitDialogLayout({
    required this.body,
    required this.actions,
    this.sheet = false,
  });

  final List<Widget> body;
  final Widget actions;

  /// Inside the confirm frame ([presentKitConfirmFrame]): the confirm's
  /// rails and air, under the handle.
  final bool sheet;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final side = sheet ? tokens.rail : tokens.panelInset;
    final top = sheet ? tokens.space3 : tokens.panelInset;
    final bottom = sheet ? tokens.space4 : tokens.panelInset;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Flexible(
          child: SingleChildScrollView(
            padding: EdgeInsetsDirectional.fromSTEB(
              side,
              top,
              side,
              tokens.space4,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: body,
            ),
          ),
        ),
        Padding(
          padding: EdgeInsetsDirectional.only(
            start: side,
            end: side,
            bottom: bottom,
          ),
          child: actions,
        ),
      ],
    );
  }
}

/// The dialog's title: it names the route and wraps, never cut (A11Y-8).
class _KitDialogTitle extends StatelessWidget {
  const _KitDialogTitle(this.title);

  final String title;

  @override
  Widget build(BuildContext context) => Semantics(
    header: true,
    namesRoute: true,
    child: KitText(title, role: KitTextRole.title),
  );
}

class _KitInputSpec {
  const _KitInputSpec({
    required this.title,
    required this.label,
    required this.confirmLabel,
    required this.initial,
    required this.kind,
    this.hint,
    this.helper,
    this.maxLength,
    this.maxLines = 1,
    this.validate,
    this.onSubmit,
    this.alternative,
    this.draft,
    this.cancelLabel,
    this.fieldKey,
    this.confirmKey,
  });

  final String title;
  final String label;
  final String confirmLabel;
  final String initial;
  final String? hint;
  final String? helper;
  final KitFieldKind kind;
  final int? maxLength;
  final int maxLines;
  final String? Function(String value)? validate;
  final Future<String?> Function(String value)? onSubmit;
  final KitAction? alternative;
  final KitDraft? draft;
  final String? cancelLabel;
  final Key? fieldKey;
  final Key? confirmKey;
}

class _KitInputDialog extends StatefulWidget {
  const _KitInputDialog({
    super.key,
    required this.spec,
    required this.controller,
    required this.ownsController,
  });

  final _KitInputSpec spec;
  final TextEditingController controller;
  final bool ownsController;

  @override
  State<_KitInputDialog> createState() => _KitInputDialogState();
}

class _KitInputDialogState extends State<_KitInputDialog> {
  final _focus = FocusNode(debugLabel: 'kit-dialog-field');
  final _keys = FocusNode(debugLabel: 'kit-dialog', skipTraversal: true);

  /// The text as last seen; set in [initState], so the very first edit
  /// already differs from it (a lazy initialiser would read the edited
  /// text on that first change and miss it).
  late String _lastText;

  /// The person has edited, or tried to submit: the reason may show.
  bool _edited = false;
  bool _working = false;
  bool _discarding = false;
  String? _submitError;

  _KitInputSpec get _spec => widget.spec;

  String get _text => widget.controller.text;

  bool get _dirty => _spec.draft == null && _text != _spec.initial;

  String? get _invalid => _spec.validate?.call(_text);

  @override
  void initState() {
    super.initState();
    _lastText = widget.controller.text;
    widget.controller.addListener(_onText);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onText);
    if (widget.ownsController) widget.controller.dispose();
    _focus.dispose();
    _keys.dispose();
    super.dispose();
  }

  void _onText() {
    if (_text == _lastText) return;
    _lastText = _text;
    setState(() {
      _edited = true;
      _submitError = null;
    });
  }

  void _close([String? value]) {
    if (!mounted) return;
    Navigator.of(context).pop(value);
  }

  /// Back, Esc, a swipe on the handle or a tap outside.
  void _requestClose() {
    if (_working) return;
    if (_discarding) {
      _keepEditing();
      return;
    }
    if (_dirty) {
      _focus.unfocus();
      setState(() => _discarding = true);
      return;
    }
    _close();
  }

  void _keepEditing() {
    setState(() => _discarding = false);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _focus.requestFocus();
    });
  }

  Future<void> _submit() async {
    if (_working || _discarding) return;
    if (_invalid != null) {
      // Nothing was judged yet: show the reason under the field.
      if (!_edited) setState(() => _edited = true);
      _focus.requestFocus();
      return;
    }
    final value = _text;
    final onSubmit = _spec.onSubmit;
    if (onSubmit == null) {
      _close(value);
      return;
    }
    setState(() {
      _working = true;
      _submitError = null;
    });
    String? error;
    try {
      error = await onSubmit(value);
    } finally {
      if (mounted) setState(() => _working = false);
    }
    if (!mounted) return;
    if (error != null) {
      setState(() => _submitError = error);
      _focus.requestFocus();
      return;
    }
    _close(value);
  }

  /// Shift+Enter in a wrapping field: a line break at the caret.
  void _newLine() {
    if (_working) return;
    final value = widget.controller.value;
    final selection = value.selection.isValid
        ? value.selection
        : TextSelection.collapsed(offset: value.text.length);
    widget.controller.value = value.replaced(
      TextRange(start: selection.start, end: selection.end),
      '\n',
    );
  }

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent) return KeyEventResult.ignored;
    if (event.logicalKey == LogicalKeyboardKey.escape) {
      // The discard question answers its own Esc (Keep editing).
      if (_discarding) return KeyEventResult.ignored;
      _requestClose();
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    return PopScope<Object?>(
      canPop: !_working && !_discarding && !_dirty,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _requestClose();
      },
      child: Focus(
        focusNode: _keys,
        // A key handler only: the text inside keeps its own nodes.
        includeSemantics: false,
        onKeyEvent: _onKey,
        child: Stack(
          children: [
            KitReveal(child: _discarding ? null : _input(context, l10n)),
            KitReveal(
              child: _discarding
                  ? SingleChildScrollView(
                      child: KitConfirmSheet(
                        key: const ValueKey('kit-dialog-discard'),
                        title: l10n.kitDiscardTitle,
                        body: l10n.kitDiscardBody,
                        confirmLabel: l10n.kitDiscardConfirm,
                        cancelLabel: l10n.kitConfirmKeepEditing,
                        kind: KitConfirmKind.discard,
                        onConfirm: _close,
                        onCancel: _keepEditing,
                      ),
                    )
                  : null,
            ),
          ],
        ),
      ),
    );
  }

  Widget _input(BuildContext context, AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    final spec = _spec;
    final invalid = _invalid;
    // Nothing is judged before the first edit; after it the reason sits
    // under the field, never under the button (so never twice), and the
    // primary waits for valid text.
    final fieldError = _submitError ?? (_edited ? invalid : null);
    final blocked = _edited && invalid != null;
    final working = _working;
    final workingReason = working ? l10n.kitWorking : null;
    void submitted(String _) => unawaited(_submit());
    final Widget field = spec.kind == KitFieldKind.secret
        ? KitField.secret(
            label: spec.label,
            controller: widget.controller,
            hint: spec.hint,
            helper: spec.helper,
            error: fieldError,
            enabled: !working,
            disabledReason: workingReason,
            focusNode: _focus,
            autofocus: true,
            textInputAction: TextInputAction.done,
            onSubmitted: submitted,
            fieldKey: spec.fieldKey,
          )
        : KitField(
            label: spec.label,
            controller: spec.draft == null ? widget.controller : null,
            draft: spec.draft,
            kind: spec.kind,
            hint: spec.hint,
            helper: spec.helper,
            error: fieldError,
            maxLength: spec.maxLength,
            maxLines: spec.maxLines,
            minLines: spec.maxLines > 1 ? 1 : null,
            enabled: !working,
            disabledReason: workingReason,
            focusNode: _focus,
            autofocus: true,
            textInputAction: TextInputAction.done,
            onSubmitted: submitted,
            fieldKey: spec.fieldKey,
          );
    final alternative = spec.alternative;
    return _KitDialogLayout(
      sheet: true,
      body: [
        _KitDialogTitle(spec.title),
        SizedBox(height: tokens.space4),
        if (spec.maxLines > 1)
          // Enter submits (the IME action and a hardware Enter alike);
          // Shift+Enter is the one way to start a new line.
          CallbackShortcuts(
            bindings: {
              const SingleActivator(LogicalKeyboardKey.enter, shift: true):
                  _newLine,
            },
            child: field,
          )
        else
          field,
      ],
      actions: KitActionBlock(
        primary: KitAction(
          key: spec.confirmKey,
          label: spec.confirmLabel,
          working: working,
          onPressed: blocked ? null : () => unawaited(_submit()),
        ),
        tertiary: [
          if (alternative != null)
            KitAction(
              key: alternative.key,
              label: alternative.label,
              icon: alternative.icon,
              destructive: alternative.destructive,
              disabledReason: alternative.disabledReason,
              onPressed: alternative.onPressed == null || working
                  ? null
                  : () {
                      _close();
                      alternative.onPressed!();
                    },
            ),
          KitAction(
            key: const ValueKey('kit-dialog-cancel'),
            label: spec.cancelLabel ?? l10n.kitConfirmCancel,
            // An explicit Cancel closes without asking; a draft keeps the
            // text anyway.
            onPressed: working ? null : _close,
          ),
        ],
      ),
    );
  }
}

class _KitAlert extends StatefulWidget {
  const _KitAlert({
    super.key,
    required this.title,
    required this.body,
    required this.details,
    this.action,
    this.closeLabel,
    this.icon,
    this.closeKey,
  });

  final String title;
  final String body;
  final List<KitTechnicalValue> details;
  final KitAction? action;
  final String? closeLabel;
  final IconData? icon;
  final Key? closeKey;

  @override
  State<_KitAlert> createState() => _KitAlertState();
}

class _KitAlertState extends State<_KitAlert> {
  final _focus = FocusNode(debugLabel: 'kit-alert');

  @override
  void dispose() {
    _focus.dispose();
    super.dispose();
  }

  void _close() => Navigator.of(context).pop();

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent) return KeyEventResult.ignored;
    final key = event.logicalKey;
    // Esc and Enter close; Enter never runs the action, which may navigate
    // away (KitDialog.md, Keyboard).
    if (key == LogicalKeyboardKey.escape ||
        key == LogicalKeyboardKey.enter ||
        key == LogicalKeyboardKey.numpadEnter) {
      _close();
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = _l10n(context);
    final action = widget.action;
    final icon = widget.icon;
    final close = KitAction(
      key: widget.closeKey ?? const ValueKey('kit-alert-close'),
      label: widget.closeLabel ?? l10n.kitSheetClose,
      onPressed: _close,
    );
    return Focus(
      focusNode: _focus,
      autofocus: true,
      includeSemantics: false,
      onKeyEvent: _onKey,
      child: _KitDialogLayout(
        body: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (icon != null) ...[
                _KitDialogIconTile(icon),
                SizedBox(width: tokens.space3),
              ],
              Expanded(child: _KitDialogTitle(widget.title)),
            ],
          ),
          SizedBox(height: tokens.space2),
          KitText(
            widget.body,
            role: KitTextRole.body,
            tone: KitTextTone.secondary,
          ),
          if (widget.details.isNotEmpty) ...[
            SizedBox(height: tokens.space3),
            KitDetailsFold(values: widget.details),
          ],
        ],
        actions: action == null
            ? KitActionBlock(secondary: close)
            : KitActionBlock(
                primary: KitAction(
                  key: action.key,
                  label: action.label,
                  icon: action.icon,
                  disabledReason: action.disabledReason,
                  onPressed: action.onPressed == null
                      ? null
                      : () {
                          _close();
                          action.onPressed!();
                        },
                ),
                secondary: close,
              ),
      ),
    );
  }
}

/// The alert's icon tile beside the title: `surface3`, the VL tile size and
/// radius, the glyph in `text1`, made for the person's Style like every
/// other tile (`KitSurface.tile`'s control surface).
class _KitDialogIconTile extends StatelessWidget {
  const _KitDialogIconTile(this.icon);

  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final glyph = tokens.iconSize(context, tokens.smallIconSize);
    return ExcludeSemantics(
      child: kitSurfaceMaterial(
        context,
        level: KitSurfaceLevel.surface3,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(tokens.iconTileRadius),
        ),
        fill: roles.surface3,
        role: KitSurfaceRole.control,
        clip: false,
        child: SizedBox.square(
          dimension: tokens.iconTileSize,
          child: Center(
            child: OverflowBox(
              maxWidth: double.infinity,
              maxHeight: double.infinity,
              child: Icon(icon, size: glyph, color: roles.text1),
            ),
          ),
        ),
      ),
    );
  }
}
