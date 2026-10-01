part of 'kit_sheet.dart';

enum _KitModalShape { bottom, panel, side }

/// Pushes a kit modal in the shape the window asks for (§8.2). The route
/// always goes on the caller's own navigator, so `Navigator.pop(context)`
/// from the caller closes it.
Future<T?> _presentKitModal<T>(
  BuildContext context, {
  required _KitModalShape shape,
  required double maxWidth,
  required bool dismissible,
  required bool enableDrag,
  required WidgetBuilder builder,
}) {
  final reduced = KitMotion.reduced(context);
  final tokens = KitTokens.of(context);
  final style = reduced
      ? AnimationStyle.noAnimation
      : AnimationStyle(
          duration: KitMotion.standard,
          reverseDuration: KitMotion.standard,
          curve: KitMotion.enter,
          reverseCurve: KitMotion.exit,
        );
  switch (shape) {
    case _KitModalShape.bottom:
      return showModalBottomSheet<T>(
        context: context,
        isScrollControlled: true,
        useSafeArea: true,
        isDismissible: dismissible,
        enableDrag: dismissible && enableDrag,
        showDragHandle: false,
        backgroundColor: tokens.sheetSurface,
        barrierColor: tokens.scrim,
        elevation: tokens.sheetElevation,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(
            top: Radius.circular(tokens.sheetRadius),
          ),
        ),
        constraints: BoxConstraints(maxWidth: maxWidth),
        sheetAnimationStyle: style,
        builder: builder,
      );
    case _KitModalShape.panel:
      return showDialog<T>(
        context: context,
        useRootNavigator: false,
        barrierDismissible: dismissible,
        barrierColor: tokens.scrim,
        animationStyle: style,
        builder: (dialogContext) => Dialog(
          insetPadding: EdgeInsets.all(tokens.panelInset),
          backgroundColor: tokens.panelSurface,
          elevation: tokens.panelElevation,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(tokens.panelRadius),
          ),
          clipBehavior: Clip.antiAlias,
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxWidth: maxWidth,
              maxHeight:
                  MediaQuery.sizeOf(dialogContext).height *
                  KitLayout.modalMaxHeight,
            ),
            child: builder(dialogContext),
          ),
        ),
      );
    case _KitModalShape.side:
      return showGeneralDialog<T>(
        context: context,
        useRootNavigator: false,
        barrierDismissible: dismissible,
        barrierLabel: MaterialLocalizations.of(
          context,
        ).modalBarrierDismissLabel,
        barrierColor: tokens.scrim,
        transitionDuration: reduced ? Duration.zero : KitMotion.standard,
        pageBuilder: (dialogContext, _, _) {
          final width =
              (MediaQuery.sizeOf(dialogContext).width *
                      KitLayout.sideSheetShare)
                  .clamp(
                    KitLayout.sideSheetMinWidth,
                    KitLayout.sideSheetMaxWidth,
                  );
          return Align(
            alignment: AlignmentDirectional.centerEnd,
            child: SizedBox(
              width: width,
              height: double.infinity,
              child: Material(
                color: tokens.sideSheetSurface,
                elevation: tokens.sideSheetElevation,
                child: SafeArea(child: builder(dialogContext)),
              ),
            ),
          );
        },
        transitionBuilder: (dialogContext, animation, _, child) {
          final rtl = Directionality.of(dialogContext) == TextDirection.rtl;
          return SlideTransition(
            position: Tween(begin: Offset(rtl ? -1 : 1, 0), end: Offset.zero)
                .animate(
                  CurvedAnimation(parent: animation, curve: KitMotion.enter),
                ),
            child: child,
          );
        },
      );
  }
}

/// A question asked in place of a sheet's content (§4.7).
class _KitAsk {
  _KitAsk(this.spec);

  final _KitConfirmSpec spec;
  final done = Completer<bool>();
}

/// Lets a [showKitConfirm] raised from inside a sheet find that sheet.
class _KitSheetScope extends InheritedWidget {
  const _KitSheetScope({required this.host, required super.child});

  final _KitSheetHostState host;

  /// The sheet [context] is inside; or, for a context outside it (a
  /// pinned action's callback uses the caller's context), the kit sheet
  /// that is the top route of [context]'s navigator.
  static _KitSheetHostState? maybeOf(BuildContext context) {
    final inside = context.getInheritedWidgetOfExactType<_KitSheetScope>();
    if (inside != null) return inside.host;
    final navigator = Navigator.maybeOf(context);
    if (navigator == null) return null;
    for (final host in _KitSheetHostState._live.reversed) {
      final route = host._route;
      if (route != null && route.isCurrent && route.navigator == navigator) {
        return host;
      }
    }
    return null;
  }

  @override
  bool updateShouldNotify(_KitSheetScope old) => old.host != host;
}

/// The live sheet: its draft, its unsaved-input guard, Esc, and the
/// question asked in place.
class _KitSheetHost extends StatefulWidget {
  const _KitSheetHost({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.tone,
    required this.body,
    required this.itemCount,
    required this.itemBuilder,
    required this.height,
    required this.shape,
    required this.primary,
    required this.primaryListenable,
    required this.secondary,
    required this.secondaryListenable,
    required this.secondaryDismisses,
    required this.tertiary,
    required this.footer,
    required this.dirty,
    required this.draft,
    required this.loading,
    required this.dismissible,
    required this.sheetKey,
  });

  final String title;
  final String? subtitle;
  final IconData? icon;
  final KitSheetTone tone;
  final WidgetBuilder? body;
  final int? itemCount;
  final IndexedWidgetBuilder? itemBuilder;
  final KitSheetHeight height;
  final _KitModalShape shape;
  final KitAction? primary;
  final ValueListenable<KitAction?>? primaryListenable;
  final KitAction? secondary;
  final ValueListenable<KitAction?>? secondaryListenable;
  final bool secondaryDismisses;
  final List<KitAction> tertiary;
  final WidgetBuilder? footer;
  final ValueListenable<bool>? dirty;
  final KitDraft? draft;
  final ValueListenable<bool>? loading;
  final bool dismissible;
  final Key? sheetKey;

  @override
  State<_KitSheetHost> createState() => _KitSheetHostState();
}

class _KitSheetHostState extends State<_KitSheetHost> {
  /// Open kit sheets, newest last.
  static final _live = <_KitSheetHostState>[];

  final _focus = FocusNode(debugLabel: 'kit-sheet');
  _KitAsk? _ask;
  ModalRoute<Object?>? _route;

  @override
  void initState() {
    super.initState();
    final draft = widget.draft;
    if (draft != null) {
      unawaited(draft.restore());
      draft.controller.addListener(_saveDraft);
    }
    widget.dirty?.addListener(_rebuild);
    widget.loading?.addListener(_rebuild);
    widget.primaryListenable?.addListener(_rebuild);
    widget.secondaryListenable?.addListener(_rebuild);
    _live.add(this);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _route = ModalRoute.of(context);
  }

  @override
  void dispose() {
    _live.remove(this);
    widget.draft?.controller.removeListener(_saveDraft);
    widget.dirty?.removeListener(_rebuild);
    widget.loading?.removeListener(_rebuild);
    widget.primaryListenable?.removeListener(_rebuild);
    widget.secondaryListenable?.removeListener(_rebuild);
    final ask = _ask;
    if (ask != null && !ask.done.isCompleted) ask.done.complete(false);
    _focus.dispose();
    super.dispose();
  }

  void _rebuild() {
    if (mounted) setState(() {});
  }

  void _saveDraft() => unawaited(widget.draft?.save());

  /// Unsaved input that no draft keeps.
  bool get _unsaved => widget.draft == null && (widget.dirty?.value ?? false);

  /// Replaces the content with [spec]'s question until it is answered.
  Future<bool> ask(_KitConfirmSpec spec) {
    final previous = _ask;
    if (previous != null && !previous.done.isCompleted) {
      previous.done.complete(false);
    }
    final ask = _KitAsk(spec);
    setState(() => _ask = ask);
    return ask.done.future;
  }

  void _answer(bool confirmed) {
    final ask = _ask;
    if (ask == null) return;
    setState(() => _ask = null);
    if (!ask.done.isCompleted) ask.done.complete(confirmed);
    _focus.requestFocus();
  }

  /// Back, Esc, the close button, a swipe or a tap outside, when the route
  /// may not simply pop.
  Future<void> _blockedPop() async {
    if (!widget.dismissible) return;
    if (_ask != null) {
      _answer(false);
      return;
    }
    if (!_unsaved) return;
    final l10n = _l10n(context);
    final discard = await ask(
      _KitConfirmSpec(
        title: l10n.kitDiscardTitle,
        body: l10n.kitDiscardBody,
        confirmLabel: l10n.kitDiscardConfirm,
        kind: KitConfirmKind.discard,
      ),
    );
    if (discard && mounted) Navigator.of(context).pop();
  }

  void _close() => unawaited(Navigator.of(context).maybePop());

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is KeyDownEvent &&
        event.logicalKey == LogicalKeyboardKey.escape) {
      _close();
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) {
    final shape = widget.shape;
    final ask = _ask;
    final size = MediaQuery.sizeOf(context);
    final fill = widget.height != KitSheetHeight.content;
    final bottom = shape == _KitModalShape.bottom;
    final secondary = widget.secondaryListenable?.value ?? widget.secondary;
    final primary = widget.primaryListenable?.value ?? widget.primary;
    final onClose = widget.dismissible ? _close : null;
    final onPullDown = bottom && widget.dismissible && widget.dirty != null
        ? _close
        : null;
    // One close control: a secondary that only dismisses replaces the X.
    final showClose = !(widget.secondaryDismisses && secondary != null);
    final loading = widget.loading?.value ?? false;
    final frameFill = fill || shape == _KitModalShape.side;
    final footer = widget.footer == null
        ? null
        : Builder(builder: widget.footer!);
    final itemBuilder = widget.itemBuilder;
    Widget content = itemBuilder != null
        ? KitSheet.list(
            key: widget.sheetKey,
            title: widget.title,
            subtitle: widget.subtitle,
            icon: widget.icon,
            tone: widget.tone,
            itemCount: widget.itemCount!,
            itemBuilder: itemBuilder,
            primary: primary,
            secondary: secondary,
            tertiary: widget.tertiary,
            footer: footer,
            loading: loading,
            handle: bottom,
            fill: frameFill,
            onClose: onClose,
            showClose: showClose,
            onPullDown: onPullDown,
          )
        : KitSheet(
            key: widget.sheetKey,
            title: widget.title,
            subtitle: widget.subtitle,
            icon: widget.icon,
            tone: widget.tone,
            primary: primary,
            secondary: secondary,
            tertiary: widget.tertiary,
            footer: footer,
            loading: loading,
            handle: bottom,
            fill: frameFill,
            onClose: onClose,
            showClose: showClose,
            onPullDown: onPullDown,
            child: Builder(builder: widget.body!),
          );
    // The question takes the content's place and the sheet's whole height
    // (up to the frame's cap), never a band beside an empty slot. The
    // content keeps its state (typed text) offstage, out of sight and out
    // of focus, at the same place in the tree, so nothing in it is rebuilt
    // from scratch.
    content = Stack(
      fit: fill ? StackFit.expand : StackFit.loose,
      children: [
        KeyedSubtree(
          key: const ValueKey('kit-sheet-content'),
          child: ExcludeFocus(
            excluding: ask != null,
            child: Visibility(
              visible: ask == null,
              maintainState: true,
              child: content,
            ),
          ),
        ),
        if (ask != null)
          Column(
            key: const ValueKey('kit-sheet-question'),
            mainAxisSize: fill ? MainAxisSize.max : MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (bottom) _KitHandle(onDismiss: () => _answer(false)),
              Flexible(
                child: SingleChildScrollView(
                  child: KitEntrance(
                    child: _KitConfirmBody(
                      spec: ask.spec,
                      onConfirm: () => _answer(true),
                      onCancel: () => _answer(false),
                    ),
                  ),
                ),
              ),
            ],
          ),
      ],
    );
    final maxHeight = size.height;
    final double? fixed = switch ((shape, widget.height)) {
      (_KitModalShape.side, _) => null,
      (_, KitSheetHeight.half) => maxHeight * KitLayout.sheetHalfHeight,
      (_KitModalShape.bottom, KitSheetHeight.full) =>
        maxHeight * KitLayout.sheetFullHeight,
      (_, KitSheetHeight.full) => maxHeight * KitLayout.modalMaxHeight,
      _ => null,
    };
    content = fixed != null
        ? SizedBox(height: fixed, child: content)
        : ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: maxHeight * KitLayout.modalMaxHeight,
            ),
            child: content,
          );
    if (bottom) {
      // The pinned actions ride above the keyboard.
      content = Padding(
        padding: EdgeInsets.only(
          bottom: MediaQuery.viewInsetsOf(context).bottom,
        ),
        child: SafeArea(top: false, child: content),
      );
    }
    return _KitSheetScope(
      host: this,
      child: PopScope<Object?>(
        canPop: widget.dismissible && ask == null && !_unsaved,
        onPopInvokedWithResult: (didPop, _) {
          if (!didPop) unawaited(_blockedPop());
        },
        child: Focus(
          focusNode: _focus,
          autofocus: true,
          onKeyEvent: _onKey,
          child: content,
        ),
      ),
    );
  }
}
