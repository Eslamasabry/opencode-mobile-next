part of 'kit_sheet.dart';

/// What a confirmation asks about (§1.2, §4.2). It sets the tone, the
/// default icon and the cancel word.
enum KitConfirmKind {
  /// Accent: sharing, a harmless start that still needs a yes ("Share
  /// conversation?" says who can see it). Cancel: "Cancel".
  neutral,

  /// Error tone: ends running work. Cancel: "Keep running".
  stop,

  /// Error tone: deletes. Cancel: "Cancel".
  destructive,

  /// Error tone: drops unsaved input. Cancel: "Keep editing".
  discard;

  /// Loses data or ends running work: error-filled confirm, commit haptic.
  bool get isDanger => this != KitConfirmKind.neutral;
}

/// Asks before an act that cannot be undone (§1.2; use Undo for anything
/// reversible, and nothing for harmless acts, §4.1). True only when the
/// confirm action was chosen; back, swipe, Esc, a tap outside and cancel
/// are false.
///
/// - [title] is a question naming the thing ("Stop fox?"); [body] says
///   what happens and whether it can be undone, in at most two sentences.
/// - [confirmLabel] is a verb naming the act ("Delete conversation"),
///   never OK or Yes. [cancelLabel] defaults by [kind].
/// - [consequences] are counted facts that go with the act ("3 queued
///   prompts will be deleted"): for a stop, delete or discard the first is
///   marked lost and the rest info; for a neutral question every one is
///   info.
/// - [consequenceItems] are marked facts drawn as given, so a question can
///   say what is kept as well as what is lost ("2 edited files are kept").
///   When given, [consequences] must be empty.
/// - [typedName] (heavy deletes): the confirm stays disabled, with its
///   reason shown, until this exact name is typed.
/// - [alternative] is a safer path ("Export first"); choosing it closes
///   the question (false) and runs it. A destructive one (a heavier path
///   that asks again, "Delete everything") is drawn neutral like every act (never red).
/// - [details] are technical values, folded under Details, left to right.
/// - [action], when given, runs inside the question: the confirm shows it
///   is working, and a failure keeps the question open with a notice and
///   Try again, so it never closes on an error.
///
/// Raised from inside a [showKitSheet] (its body, or a pinned action), it
/// replaces that sheet's content in place and adds no route (§4.7).
/// Otherwise it adapts to the window (§8.2): a bottom sheet on a compact
/// window, capped at 560 dp on a medium one, a centred 480 dp dialog on an
/// expanded or large one. Esc cancels; Enter confirms only a [neutral]
/// question — a stop, delete or discard needs a click, or Tab to the
/// button.
///
/// A stop, delete or discard plays [KitHaptics.commit] when confirmed.
Future<bool> showKitConfirm(
  BuildContext context, {
  required String title,
  required String body,
  required String confirmLabel,
  KitConfirmKind kind = KitConfirmKind.neutral,
  String? cancelLabel,
  IconData? icon,
  List<String> consequences = const [],
  List<KitConsequence>? consequenceItems,
  String? typedName,
  KitAction? alternative,
  List<KitTechnicalValue> details = const [],
  String? code,
  String? codeLabel,
  String? codeCopyLabel,
  Future<void> Function()? action,
  RequestRoutes? routes,
  Key? sheetKey,
  Key? confirmKey,
}) async {
  assert(
    consequenceItems == null || consequences.isEmpty,
    'Pass consequences or consequenceItems, not both.',
  );
  if (routes?.isPending == false) return false;
  final spec = _KitConfirmSpec(
    title: title,
    body: body,
    confirmLabel: confirmLabel,
    kind: kind,
    cancelLabel: cancelLabel,
    icon: icon,
    consequences: consequences,
    consequenceItems: consequenceItems,
    typedName: typedName,
    alternative: alternative,
    details: details,
    code: code,
    codeLabel: codeLabel,
    codeCopyLabel: codeCopyLabel,
    action: action,
    sheetKey: sheetKey,
    confirmKey: confirmKey,
  );
  final host = _KitSheetScope.maybeOf(context);
  if (host != null && host.mounted) return host.ask(spec);
  final result = await presentKitConfirmFrame<bool>(
    context,
    routes: routes,
    builder: (sheetContext) {
      void close(bool value) => Navigator.of(sheetContext).pop(value);
      final question = _KitConfirmBody(
        spec: spec,
        onConfirm: () => close(true),
        onCancel: () => close(false),
      );
      return SingleChildScrollView(child: question);
    },
  );
  return result == true;
}

/// The frame [showKitConfirm] opens in, for the kit's other short
/// questions (kit_dialog.dart's input dialog): a bottom sheet with a
/// handle on a compact window, capped at 560 dp on a medium one, a centred
/// 480 dp panel on an expanded or large one (§8.2). The route goes on the
/// caller's navigator; `Navigator.pop` from [builder]'s context closes it.
///
/// [builder]'s widget is given the room left under the handle and above
/// the keyboard, and scrolls itself (it may pin its own actions).
///
/// With [ownsDrag] the route's own swipe is off and a pull on the handle
/// asks the route to pop through `maybePop`, so the content's `PopScope`
/// decides (unsaved text, work in flight). Kit-internal: nothing outside
/// lib/ui/kit/ calls it.
Future<T?> presentKitConfirmFrame<T>(
  BuildContext context, {
  required WidgetBuilder builder,
  bool ownsDrag = false,
  RequestRoutes? routes,
}) {
  final window = KitLayout.modalWindowOf(context);
  final shape = window.isWide ? _KitModalShape.panel : _KitModalShape.bottom;
  return _presentKitModal<T>(
    context,
    shape: shape,
    maxWidth: switch (window) {
      KitWindow.compact => KitLayout.sheetMaxWidth,
      KitWindow.medium => KitLayout.confirmMediumWidth,
      _ => KitLayout.confirmDialogWidth,
    },
    dismissible: true,
    enableDrag: !ownsDrag,
    builder: (sheetContext) {
      routes?.own(ModalRoute.of(sheetContext));
      final content = builder(sheetContext);
      if (shape != _KitModalShape.bottom) {
        // A panel has no handle above the content: the same air instead.
        return Padding(
          padding: EdgeInsets.only(top: KitTokens.of(sheetContext).space3),
          child: content,
        );
      }
      void pop() => unawaited(Navigator.of(sheetContext).maybePop());
      Widget handle = _KitHandle(onDismiss: pop);
      if (ownsDrag) handle = _PullDown(onPullDown: pop, child: handle);
      return Padding(
        padding: EdgeInsets.only(
          bottom: MediaQuery.viewInsetsOf(sheetContext).bottom,
        ),
        child: SafeArea(
          top: false,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              handle,
              Flexible(child: content),
            ],
          ),
        ),
      );
    },
  );
}

class _KitConfirmSpec {
  const _KitConfirmSpec({
    required this.title,
    required this.body,
    required this.confirmLabel,
    required this.kind,
    this.cancelLabel,
    this.icon,
    this.consequences = const [],
    this.consequenceItems,
    this.typedName,
    this.alternative,
    this.details = const [],
    this.code,
    this.codeLabel,
    this.codeCopyLabel,
    this.action,
    this.sheetKey,
    this.confirmKey,
  });

  final String title;
  final String body;
  final String confirmLabel;
  final KitConfirmKind kind;
  final String? cancelLabel;
  final IconData? icon;
  final List<String> consequences;
  final List<KitConsequence>? consequenceItems;
  final String? typedName;
  final KitAction? alternative;
  final List<KitTechnicalValue> details;
  final String? code;
  final String? codeLabel;
  final String? codeCopyLabel;
  final Future<void> Function()? action;
  final Key? sheetKey;
  final Key? confirmKey;
}

class _KitConfirmBody extends StatelessWidget {
  const _KitConfirmBody({
    required this.spec,
    required this.onConfirm,
    required this.onCancel,
  });

  final _KitConfirmSpec spec;
  final VoidCallback onConfirm;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) => KitConfirmSheet(
    key: spec.sheetKey,
    title: spec.title,
    body: spec.body,
    confirmLabel: spec.confirmLabel,
    kind: spec.kind,
    cancelLabel: spec.cancelLabel,
    icon: spec.icon,
    consequences: spec.consequences,
    consequenceItems: spec.consequenceItems,
    typedName: spec.typedName,
    alternative: spec.alternative,
    details: spec.details,
    code: spec.code,
    codeLabel: spec.codeLabel,
    codeCopyLabel: spec.codeCopyLabel,
    action: spec.action,
    confirmKey: spec.confirmKey,
    onConfirm: onConfirm,
    onCancel: onCancel,
  );
}

/// The confirmation itself (§1.2), drawn by [showKitConfirm]; also used on
/// its own for goldens. Start-aligned on the rails: the icon on the one
/// 44 dp header tile (always neutral: `surface3`), the title, the body, the consequences, the typed name, then the
/// answers: the confirm, the alternative on a line of its own, and Cancel
/// last (full width on a phone; one end-aligned row from medium, Cancel,
/// alternative, confirm), and Details last of all.
class KitConfirmSheet extends StatefulWidget {
  const KitConfirmSheet({
    super.key,
    required this.title,
    required this.body,
    required this.confirmLabel,
    required this.onConfirm,
    required this.onCancel,
    this.kind = KitConfirmKind.neutral,
    this.cancelLabel,
    this.icon,
    this.consequences = const [],
    this.consequenceItems,
    this.typedName,
    this.alternative,
    this.details = const [],
    this.code,
    this.codeLabel,
    this.codeCopyLabel,
    this.action,
    this.confirmKey,
    this.working = false,
    this.failed = false,
  }) : assert(
         consequenceItems == null || consequences.length == 0,
         'Pass consequences or consequenceItems, not both.',
       );

  final String title;
  final String body;
  final String confirmLabel;

  /// Called once the act is confirmed (and [action], if any, finished).
  final VoidCallback onConfirm;
  final VoidCallback onCancel;
  final KitConfirmKind kind;
  final String? cancelLabel;
  final IconData? icon;
  final List<String> consequences;

  /// Marked facts (lost, kept, info), drawn as given. When given,
  /// [consequences] must be empty.
  final List<KitConsequence>? consequenceItems;
  final String? typedName;
  final KitAction? alternative;
  final List<KitTechnicalValue> details;

  /// A one-time code the person types elsewhere (a device code): shown
  /// under the body, large and monospaced, with Copy ([KitOneTimeCode]).
  final String? code;
  final String? codeLabel;
  final String? codeCopyLabel;
  final Future<void> Function()? action;
  final Key? confirmKey;

  /// Opens already working, or already failed (goldens).
  final bool working;
  final bool failed;

  static IconData iconFor(KitConfirmKind kind) => switch (kind) {
    KitConfirmKind.stop => AppIconography.stop,
    KitConfirmKind.destructive => AppIconography.delete,
    KitConfirmKind.discard => AppIconography.editOff,
    KitConfirmKind.neutral => AppIconography.info,
  };

  static String cancelFor(BuildContext context, KitConfirmKind kind) {
    final l10n = _l10n(context);
    return switch (kind) {
      KitConfirmKind.stop => l10n.kitConfirmKeepRunning,
      KitConfirmKind.discard => l10n.kitConfirmKeepEditing,
      _ => l10n.kitConfirmCancel,
    };
  }

  @override
  State<KitConfirmSheet> createState() => _KitConfirmSheetState();
}

class _KitConfirmSheetState extends State<KitConfirmSheet> {
  final _focus = FocusNode(debugLabel: 'kit-confirm');
  TextEditingController? _typed;
  late bool _working = widget.working;
  late bool _failed = widget.failed;

  @override
  void initState() {
    super.initState();
    if (widget.typedName != null) {
      _typed = TextEditingController()..addListener(_rebuild);
    }
  }

  @override
  void dispose() {
    _typed?.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _rebuild() => setState(() {});

  bool get _nameMatches =>
      widget.typedName == null || _typed?.text == widget.typedName;

  bool get _enabled => _nameMatches && !_working;

  Future<void> _confirm() async {
    if (!_enabled) return;
    if (widget.kind.isDanger) KitHaptics.commit(context);
    final action = widget.action;
    if (action == null) {
      widget.onConfirm();
      return;
    }
    setState(() {
      _working = true;
      _failed = false;
    });
    try {
      await action();
    } catch (_) {
      // The error's text may carry a server's words or a credential; the
      // notice says only that it did not finish.
      if (mounted) {
        setState(() {
          _working = false;
          _failed = true;
        });
      }
      return;
    }
    if (!mounted) return;
    setState(() => _working = false);
    widget.onConfirm();
  }

  void _cancel() {
    if (_working) return;
    widget.onCancel();
  }

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent) return KeyEventResult.ignored;
    final key = event.logicalKey;
    if (key == LogicalKeyboardKey.escape) {
      _cancel();
      return KeyEventResult.handled;
    }
    // Enter confirms only a neutral question, and only while nothing else
    // (a focused button, the typed name) has taken the key (§8.2).
    if ((key == LogicalKeyboardKey.enter ||
            key == LogicalKeyboardKey.numpadEnter) &&
        widget.kind == KitConfirmKind.neutral &&
        FocusManager.instance.primaryFocus == _focus) {
      unawaited(_confirm());
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.confirm(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = _l10n(context);
    final heavy = widget.kind.isDanger;
    final consequences = <KitConsequence>[
      ...?widget.consequenceItems,
      for (final (index, fact) in widget.consequences.indexed)
        KitConsequence(
          fact,
          mark: index == 0 && heavy
              ? KitConsequenceMark.lost
              : KitConsequenceMark.info,
        ),
    ];
    final typedName = widget.typedName;
    final alternative = widget.alternative;
    final wide = KitLayout.modalWindowOf(context).isWide;
    final confirm = KitButton.primary(
      key: widget.confirmKey,
      label: widget.confirmLabel,
      destructive: heavy,
      working: _working,
      expand: !wide,
      // Keeps its fill while working; a second tap is ignored.
      onPressed: _nameMatches ? () => unawaited(_confirm()) : null,
    );
    final cancel = KitButton.secondary(
      key: const ValueKey('kit-confirm-cancel'),
      label:
          widget.cancelLabel ?? KitConfirmSheet.cancelFor(context, widget.kind),
      expand: !wide,
      onPressed: _working ? null : _cancel,
    );
    // The safer path sits between the act and Cancel: the person reads
    // the act, then the way round it, then the way out.
    final alternativeButton = alternative == null
        ? null
        : KitButton.fromAction(
            KitAction(
              key: alternative.key,
              label: alternative.label,
              icon: alternative.icon,
              // A heavier path than the confirm ("Delete everything",
              // which opens its own typed-name question) reads like any other act.
              destructive: alternative.destructive,
              onPressed: alternative.onPressed == null || _working
                  ? null
                  : () {
                      _cancel();
                      alternative.onPressed!();
                    },
            ),
            role: KitButtonRole.tertiary,
          );
    final reason = _nameMatches
        ? null
        : Padding(
            padding: EdgeInsets.only(top: tokens.space2),
            child: KitText(
              l10n.kitConfirmTypeNameReason,
              key: const ValueKey('kit-confirm-reason'),
              role: KitTextRole.secondary,
            ),
          );
    return PopScope<Object?>(
      canPop: !_working,
      child: Focus(
        focusNode: _focus,
        autofocus: true,
        onKeyEvent: _onKey,
        child: Padding(
          padding: EdgeInsetsDirectional.fromSTEB(
            tokens.rail,
            tokens.space3,
            tokens.rail,
            tokens.space4,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Align(
                alignment: AlignmentDirectional.centerStart,
                // The accent is never a mark colour (LOOK-6); the tile is
                // neutral for every kind: nothing is painted red.
                child: _KitIconTile(
                  icon: widget.icon ?? KitConfirmSheet.iconFor(widget.kind),
                  tone: _KitTileTone.neutral,
                ),
              ),
              SizedBox(height: tokens.space3),
              Semantics(
                header: true,
                namesRoute: true,
                // Wraps; never cut (A11Y-8).
                child: KitText(widget.title, role: KitTextRole.title),
              ),
              SizedBox(height: tokens.space2),
              KitText(
                widget.body,
                role: KitTextRole.body,
                tone: KitTextTone.secondary,
              ),
              if (widget.code case final code?) ...[
                SizedBox(height: tokens.space4),
                KitOneTimeCode(
                  code: code,
                  label: widget.codeLabel,
                  copyLabel: widget.codeCopyLabel,
                ),
              ],
              if (consequences.isNotEmpty) ...[
                SizedBox(height: tokens.space4),
                KitConsequences(items: consequences),
              ],
              if (typedName != null) ...[
                SizedBox(height: tokens.space4),
                TextField(
                  key: const ValueKey('kit-confirm-typed-name'),
                  controller: _typed,
                  enabled: !_working,
                  autocorrect: false,
                  enableSuggestions: false,
                  textDirection: TextDirection.ltr,
                  style: tokens.typedName,
                  decoration: InputDecoration(
                    // The name is isolated left to right inside the
                    // sentence, so a branch or path never reorders.
                    labelText: l10n.kitConfirmTypeName(KitBidi.ltr(typedName)),
                  ),
                  onSubmitted: (_) => unawaited(_confirm()),
                ),
              ],
              if (_failed) ...[
                SizedBox(height: tokens.space3),
                KitNotice(
                  key: const ValueKey('kit-confirm-failed'),
                  message: l10n.kitConfirmFailed,
                  tone: AppStatusTone.failure,
                  actions: [
                    KitAction(
                      label: l10n.kitTryAgain,
                      onPressed: () => unawaited(_confirm()),
                    ),
                  ],
                ),
              ],
              SizedBox(height: tokens.space5),
              if (wide) ...[
                // A PC or tablet (§5): the answers in a row at the end,
                // the confirm last, the alternative between it and Cancel.
                if (alternativeButton != null)
                  // Three answers keep their words on one line each and
                  // wrap to a second row rather than squeeze a label.
                  Wrap(
                    alignment: WrapAlignment.end,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    spacing: tokens.space3,
                    runSpacing: tokens.space2,
                    children: [cancel, alternativeButton, confirm],
                  )
                else
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      Flexible(child: cancel),
                      SizedBox(width: tokens.space3),
                      Flexible(child: confirm),
                    ],
                  ),
                ?reason,
              ] else ...[
                confirm,
                // The reason sits under the button it explains.
                ?reason,
                if (alternativeButton != null) ...[
                  SizedBox(height: tokens.space2),
                  KitInset(child: alternativeButton),
                ],
                SizedBox(height: tokens.space3),
                cancel,
              ],
              if (widget.details.isNotEmpty) ...[
                SizedBox(height: tokens.space2),
                KitDetailsFold(values: widget.details),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
