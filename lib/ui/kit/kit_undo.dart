import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import 'kit_bottom_inset.dart';
import 'kit_buttons.dart';
import 'kit_icon_button.dart';
import 'kit_layout.dart';
import 'kit_motion.dart';
import 'kit_surface.dart' show kitSurfaceMaterial;
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// Shows the one Undo bar (docs/ux-system/kit-api/KitUndo.md; K2 §1.17,
/// §4.1, §4.8; STANDARDS KIT-34, DATA-11, MOT-1, MOT-11). Returns at once —
/// the one modal-family entry point that is not a `Future` (KIT-11).
///
/// Two ways to use it (DATA-11):
/// - act now, undo = inverse: do the act, then call this with [onUndo]
///   running the inverse call (only where the gateway exposes one);
/// - deferred commit (a local act): hide the thing, pass [onCommit] to do
///   the act for real and [onUndo] to put it back. [onCommit] runs exactly
///   once unless Undo is taken.
///
/// One bar at a time: a new call first commits the pending one (its
/// [onCommit] runs before this bar shows) — see [KitUndo.commitPending].
/// The window is [KitUndo.window]; never auto-dismissed under accessible
/// navigation (`MediaQuery.accessibleNavigationOf`), which shows a Dismiss
/// action instead. If [onUndo] throws or its future errors: with
/// [onUndoFailed], it receives the error and a `tryAgain` callback and the
/// host shows its own failure notice; without it, the bar itself switches
/// to its failure form (kit copy `kitUndoFailed`, action `kitTryAgain`, no
/// timeout, Dismiss).
void showKitUndo(
  BuildContext context, {
  required String message,
  required FutureOr<void> Function() onUndo,
  FutureOr<void> Function()? onCommit,
  void Function(Object error, VoidCallback tryAgain)? onUndoFailed,
  String? undoLabel,
  Key? key,
  Key? undoKey,
}) {
  _KitUndoHost.instance.show(
    context,
    message: message,
    onUndo: onUndo,
    onCommit: onCommit,
    onUndoFailed: onUndoFailed,
    undoLabel: undoLabel,
    barKey: key,
    undoKey: undoKey,
  );
}

/// The kit's one snackbar (KitUndo.md): "done, with Undo", one at a time,
/// floating above the dock, the composer and a pinned primary. Never
/// constructed directly — [showKitUndo] is the only entry point.
///
/// States: default (message + Undo), working (Undo tapped, an async
/// `onUndo` in flight), error (`onUndo` failed and no `onUndoFailed` was
/// given) and, under accessible navigation, a Dismiss variant of default.
/// No loading or empty; disabled does not exist (an Undo that cannot run is
/// not offered). KIT-12's fixed vocabulary ({loading, empty, error,
/// disabled, working, answered}) does not have words for "default" or the
/// accessible variant; KitUndo.md names them this way and this unit follows
/// it verbatim (PROC-20 note in the QA record).
abstract final class KitUndo {
  /// How long the bar stays when nothing is pressed. Not a motion; a named
  /// kit wait (MOT-1).
  static const Duration window = KitMotion.undoWindow;

  /// Commits a pending bar now (runs its `onCommit`, closes it). Called by
  /// the kit on route pop, when a route opens on top of the one that showed
  /// the bar, and on `AppLifecycleState.paused`; public for the
  /// app's lifecycle wiring and tests. No-op when nothing is pending.
  static void commitPending() => _KitUndoHost.instance.commitPending();

  /// Tests only.
  @visibleForTesting
  static bool get debugHasPending => _KitUndoHost.instance.hasPending;
}

/// One shown bar's request and its mutable, in-flight state (working, a
/// failed undo). A fresh instance per [showKitUndo] call.
class _PendingUndo extends ChangeNotifier {
  _PendingUndo({
    required this.message,
    required this.onUndo,
    required this.onCommit,
    required this.onUndoFailed,
    required this.undoLabel,
    required this.barKey,
    required this.undoKey,
    required this.clearance,
  });

  final String message;
  final FutureOr<void> Function() onUndo;
  final FutureOr<void> Function()? onCommit;
  final void Function(Object error, VoidCallback tryAgain)? onUndoFailed;
  final String? undoLabel;
  final Key? barKey;
  final Key? undoKey;

  /// Read at show time (KitBottomInset.md, KitUndo.md §Adaptive), then
  /// again after each frame while the bar shows ([updateClearance]): a
  /// composer that grows under the bar (a restored prompt) lifts it too.
  KitClearance clearance;

  void updateClearance(KitClearance next) {
    if (_disposed || next == clearance) return;
    clearance = next;
    notifyListeners();
  }

  Timer? _timer;

  /// Removes the "something opened on top" listener (see [_KitUndoHost.show]).
  VoidCallback? detach;
  bool working = false;
  Object? error;
  bool _disposed = false;

  void startTimer(VoidCallback onElapsed) {
    _timer?.cancel();
    _timer = Timer(KitUndo.window, onElapsed);
  }

  void cancelTimer() {
    _timer?.cancel();
    _timer = null;
  }

  void setWorking(bool value) {
    if (_disposed || working == value) return;
    working = value;
    notifyListeners();
  }

  void setError(Object value) {
    if (_disposed) return;
    error = value;
    working = false;
    notifyListeners();
  }

  /// Idempotent: a request can be finished from two sides (a commit
  /// trigger and the in-flight undo's outcome); the second call is a no-op
  /// rather than a disposed-notifier assertion.
  @override
  void dispose() {
    cancelTimer();
    detach?.call();
    detach = null;
    if (_disposed) return;
    _disposed = true;
    super.dispose();
  }
}

/// The app-wide (one at a time, KIT-34) controller behind [showKitUndo]: one
/// [OverlayEntry] inserted lazily into the root overlay on first show, the
/// route-pop and app-pause commit triggers, and the Ctrl+Z accelerator.
class _KitUndoHost {
  _KitUndoHost._();

  static final _KitUndoHost instance = _KitUndoHost._();

  final ValueNotifier<_PendingUndo?> current = ValueNotifier(null);
  OverlayEntry? _entry;
  bool _lifecycleObserved = false;

  bool get hasPending => current.value != null;

  void show(
    BuildContext context, {
    required String message,
    required FutureOr<void> Function() onUndo,
    FutureOr<void> Function()? onCommit,
    void Function(Object error, VoidCallback tryAgain)? onUndoFailed,
    String? undoLabel,
    Key? barKey,
    Key? undoKey,
  }) {
    final previous = current.value;
    if (previous != null) _commit(previous);

    final overlay = Overlay.of(context, rootOverlay: true);
    final clearance = KitBottomInset.read(context);
    final route = ModalRoute.of(context);
    // Item 6: never auto-dismissed under accessible navigation. Read once,
    // like the clearance above: the bar's own build reads it again (live)
    // to decide whether to show Dismiss.
    final accessible = MediaQuery.accessibleNavigationOf(context);

    final pending = _PendingUndo(
      message: message,
      onUndo: onUndo,
      onCommit: onCommit,
      onUndoFailed: onUndoFailed,
      undoLabel: undoLabel,
      barKey: barKey,
      undoKey: undoKey,
      clearance: clearance,
    );
    route?.popped.then((_) {
      if (identical(current.value, pending)) _commit(pending);
    });
    // A sheet, dialog or page opened on top of the route that showed the bar
    // is a new act: commit then, so the bar never sits over the new route's
    // bottom rows for the rest of its window.
    final cover = route?.secondaryAnimation;
    if (cover != null) {
      void onCovered(AnimationStatus status) {
        if (status != AnimationStatus.forward &&
            status != AnimationStatus.completed) {
          return;
        }
        cover.removeStatusListener(onCovered);
        if (identical(current.value, pending)) _commit(pending);
      }

      cover.addStatusListener(onCovered);
      pending.detach = () => cover.removeStatusListener(onCovered);
    }
    // Sheets and dialogs are popup routes, which do not drive the route
    // below's secondary animation: watch the route itself stop being the
    // current one (something opened on top of it) and commit then too.
    if (route != null) _commitWhenCovered(route, pending);
    _trackClearance(context, pending);
    current.value = pending;
    _ensureOverlayEntry(overlay);
    _ensureLifecycleObserver();
    _ensureKeyHandler();
    if (!accessible) pending.startTimer(() => _commit(pending));
  }

  /// Checks after each frame while [pending] shows: once [route] was the
  /// current route and something is pushed on top of it (it is still in
  /// the stack but no longer current), the bar commits. A bar shown while
  /// [route] is covered (a sheet closing with the act's result) waits for
  /// the route to come back first. Post-frame checks schedule no frames.
  void _commitWhenCovered(ModalRoute<dynamic> route, _PendingUndo pending) {
    var wasCurrent = route.isCurrent;
    void check(Duration _) {
      if (!identical(current.value, pending)) return;
      final isCurrent = route.isCurrent;
      if (wasCurrent && !isCurrent && route.isActive) {
        _commit(pending);
        return;
      }
      wasCurrent = isCurrent;
      SchedulerBinding.instance.addPostFrameCallback(check);
    }

    SchedulerBinding.instance.addPostFrameCallback(check);
  }

  /// Re-reads what the bar must stay clear of from the context that showed
  /// it, after each frame while [pending] shows: what sits below (the
  /// composer, a pinned primary) may change size after the act that showed
  /// the bar. Post-frame checks schedule no frames; a change rebuilds the
  /// bar in place.
  void _trackClearance(BuildContext context, _PendingUndo pending) {
    void check(Duration _) {
      if (!identical(current.value, pending)) return;
      if (context.mounted) {
        pending.updateClearance(KitBottomInset.read(context));
      }
      SchedulerBinding.instance.addPostFrameCallback(check);
    }

    SchedulerBinding.instance.addPostFrameCallback(check);
  }

  void _ensureOverlayEntry(OverlayState overlay) {
    final entry = _entry;
    if (entry != null && entry.mounted) return;
    _entry = OverlayEntry(builder: (context) => const _KitUndoOverlay());
    overlay.insert(_entry!);
  }

  void _ensureLifecycleObserver() {
    if (_lifecycleObserved) return;
    _lifecycleObserved = true;
    WidgetsBinding.instance.addObserver(_KitUndoLifecycleObserver(this));
  }

  // Ctrl+Z (item 7) goes to the focused widgets first: it is bound as a
  // FocusManager *late* handler, which runs only when no focus node on the
  // primary focus's path handled the key — so a focused terminal (Ctrl+Z is
  // SIGTSTP there) or any other key-consuming editor keeps it. With no
  // primary focus at all the focus system never sees the key, so a
  // HardwareKeyboard handler covers that one case.
  //
  // Re-armed on every show(), not guarded by an "added once" flag: a test
  // harness clears HardwareKeyboard's own handler list between tests to
  // keep them hermetic (HardwareKeyboard.clearState), which would otherwise
  // silently drop it after the first test that shows a bar. Removing first
  // keeps this idempotent (never more than one registration).
  void _ensureKeyHandler() {
    final focus = FocusManager.instance;
    focus.removeLateKeyEventHandler(_handleLateKey);
    focus.addLateKeyEventHandler(_handleLateKey);
    HardwareKeyboard.instance.removeHandler(_handleUnfocusedKey);
    HardwareKeyboard.instance.addHandler(_handleUnfocusedKey);
  }

  KeyEventResult _handleLateKey(KeyEvent event) =>
      _takeUndoKey(event) ? KeyEventResult.handled : KeyEventResult.ignored;

  bool _handleUnfocusedKey(KeyEvent event) {
    if (FocusManager.instance.primaryFocus != null) return false;
    return _takeUndoKey(event);
  }

  /// Ctrl+Z (Cmd+Z) exactly — Shift (redo) or Alt with it is not Undo.
  bool _takeUndoKey(KeyEvent event) {
    final pending = current.value;
    if (pending == null || event is! KeyDownEvent) return false;
    if (event.logicalKey != LogicalKeyboardKey.keyZ) return false;
    final keyboard = HardwareKeyboard.instance;
    if (!keyboard.isControlPressed && !keyboard.isMetaPressed) return false;
    if (keyboard.isShiftPressed || keyboard.isAltPressed) return false;
    // A text field keeps Ctrl+Z even when its own undo history is empty
    // (its undo action is then disabled and lets the key through). The
    // focused node's context sits inside EditableText's own build output
    // (a Focus wrapper), so an ancestor lookup is what finds it.
    final focusContext = FocusManager.instance.primaryFocus?.context;
    final typing =
        focusContext?.findAncestorWidgetOfExactType<EditableText>() != null;
    if (typing) return false;
    attemptUndo(pending);
    return true;
  }

  void onAppPaused() {
    final pending = current.value;
    if (pending != null) _commit(pending);
  }

  void commitPending() {
    final pending = current.value;
    if (pending != null) _commit(pending);
  }

  void dismiss(_PendingUndo pending) => _commit(pending);

  void _commit(_PendingUndo pending) {
    pending.cancelTimer();
    if (pending.working) {
      // Undo is taken (item 3, DATA-11): an async onUndo is in flight, so
      // this request is never committed. A commit trigger (a new bar, the
      // route popping, the app pausing, commitPending, Dismiss) only closes
      // the bar; attemptUndo still owns the outcome — it disposes on
      // success and brings the failure back on error (never silent).
      if (identical(current.value, pending)) current.value = null;
      return;
    }
    if (!identical(current.value, pending)) return;
    current.value = null;
    final onCommit = pending.onCommit;
    pending.dispose();
    if (onCommit == null) return;
    Future.sync(onCommit).catchError((Object error, StackTrace stack) {
      // Item 5: onCommit owns its own errors' presentation; an error that
      // escapes it is never swallowed, and the bar is already gone.
      FlutterError.reportError(
        FlutterErrorDetails(
          exception: error,
          stack: stack,
          library: 'opencode_mobile kit',
          context: ErrorDescription('while committing a KitUndo bar'),
        ),
      );
    });
  }

  /// The Undo tap and every "Try again": guarded against a second tap while
  /// [_PendingUndo.working] (item 9), then routes success (bar closes) or
  /// failure (hands off to `onUndoFailed`, or the bar's own failure form).
  void attemptUndo(_PendingUndo pending) {
    if (pending.working) return;
    pending.cancelTimer();
    pending.setWorking(true);
    Future.sync(pending.onUndo).then(
      (_) {
        if (identical(current.value, pending)) current.value = null;
        pending.dispose();
      },
      onError: (Object error, StackTrace stack) {
        final onUndoFailed = pending.onUndoFailed;
        if (onUndoFailed != null) {
          if (identical(current.value, pending)) current.value = null;
          // Handed off entirely (the host shows its own KitNotice failure):
          // reset (not dispose) so a later `tryAgain` can still run
          // `attemptUndo` on this same request.
          pending.setWorking(false);
          onUndoFailed(error, () => attemptUndo(pending));
          return;
        }
        pending.setError(error);
        if (identical(current.value, pending)) return;
        // A commit trigger closed the bar while this undo was in flight. A
        // failed undo is never silent (item 4): the failure form takes the
        // one slot back. Whatever bar holds it now is committed first, as a
        // new bar would commit it (item 1); the failure form has no timeout.
        final other = current.value;
        if (other != null) _commit(other);
        current.value = pending;
      },
    );
  }
}

class _KitUndoLifecycleObserver with WidgetsBindingObserver {
  _KitUndoLifecycleObserver(this._host);

  final _KitUndoHost _host;

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused) _host.onAppPaused();
  }
}

/// The overlay's one persistent widget: rebuilds whenever the pending bar
/// changes identity or its own working/error state notifies.
class _KitUndoOverlay extends StatelessWidget {
  const _KitUndoOverlay();

  @override
  Widget build(BuildContext context) => ValueListenableBuilder<_PendingUndo?>(
    valueListenable: _KitUndoHost.instance.current,
    builder: (context, pending, _) => _KitUndoTransition(pending: pending),
  );
}

/// Motion (KitUndo.md §Motion): in, slides up `space2` and fades over
/// `KitMotion.standard` on `KitMotion.enter`; out, fades only (no slide)
/// over `KitMotion.quick` on `KitMotion.exit`. Under `KitMotion.reduced`,
/// appears and disappears at once and settles in one `pump()` (G8x).
class _KitUndoTransition extends StatefulWidget {
  const _KitUndoTransition({required this.pending});

  final _PendingUndo? pending;

  @override
  State<_KitUndoTransition> createState() => _KitUndoTransitionState();
}

class _KitUndoTransitionState extends State<_KitUndoTransition>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: KitMotion.standard,
    reverseDuration: KitMotion.quick,
  );

  /// The one curve over [_controller] (disposed with it): `enter` going
  /// forward, `exit` in reverse.
  late final CurvedAnimation _curve;
  _PendingUndo? _shown;
  bool _ranInitialEntrance = false;

  @override
  void initState() {
    super.initState();
    _curve = CurvedAnimation(
      parent: _controller,
      curve: KitMotion.enter,
      reverseCurve: KitMotion.exit,
    );
    _shown = widget.pending;
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_ranInitialEntrance) return;
    _ranInitialEntrance = true;
    if (widget.pending != null) {
      if (KitMotion.reduced(context)) {
        _controller.value = 1;
      } else {
        _controller.forward(from: 0);
      }
    }
  }

  @override
  void didUpdateWidget(covariant _KitUndoTransition old) {
    super.didUpdateWidget(old);
    final next = widget.pending;
    final reduced = KitMotion.reduced(context);
    if (next == null) {
      if (old.pending == null) return;
      if (reduced) {
        setState(() => _shown = null);
        _controller.value = 0;
        return;
      }
      final leaving = old.pending;
      _controller.reverse().whenCompleteOrCancel(() {
        if (mounted && widget.pending == null && identical(_shown, leaving)) {
          setState(() => _shown = null);
        }
      });
      return;
    }
    final sameBar = identical(old.pending, next);
    setState(() => _shown = next);
    if (sameBar) return;
    if (reduced) {
      _controller.value = 1;
    } else {
      _controller.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _curve.dispose();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final shown = _shown;
    if (shown == null) return const SizedBox.shrink();
    // The placement follows the bar's clearance as it is re-read.
    return ListenableBuilder(
      listenable: shown,
      builder: (context, _) => _placed(context, shown),
    );
  }

  Widget _placed(BuildContext context, _PendingUndo shown) {
    final tokens = KitTokens.of(context);
    final media = MediaQuery.of(context);
    final isShort = KitLayout.isShort(context);
    final window = isShort ? KitWindow.compact : KitLayout.windowOf(context);
    final keyboardBottom = media.viewInsets.bottom + media.padding.bottom;
    final bottom =
        math.max(shown.clearance.bottom, keyboardBottom) + tokens.space2;

    Widget bar = AnimatedBuilder(
      animation: _curve,
      builder: (context, child) {
        final t = _curve.value.clamp(0.0, 1.0);
        // The slide belongs to the entrance only; the exit is a fade.
        final entering = _controller.status == AnimationStatus.forward;
        return Opacity(
          opacity: t,
          child: Transform.translate(
            offset: Offset(0, entering ? (1 - t) * tokens.space2 : 0),
            child: child,
          ),
        );
      },
      child: ListenableBuilder(
        listenable: shown,
        builder: (context, _) => _KitUndoBar(pending: shown),
      ),
    );

    if (window == KitWindow.compact) {
      return PositionedDirectional(
        start: tokens.gutter,
        end: tokens.gutter,
        bottom: bottom,
        child: bar,
      );
    }
    return PositionedDirectional(
      start: shown.clearance.start + tokens.gutter,
      bottom: bottom,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: KitLayout.undoMaxWidth),
        child: bar,
      ),
    );
  }
}

/// The bar's content for [pending]'s current state (default, working, error,
/// or the accessible-navigation Dismiss variant of default).
class _KitUndoBar extends StatelessWidget {
  const _KitUndoBar({required this.pending});

  final _PendingUndo pending;

  @override
  Widget build(BuildContext context) {
    final host = _KitUndoHost.instance;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final accessible = MediaQuery.accessibleNavigationOf(context);
    final failed = pending.error;
    final showDismiss = accessible || failed != null;
    final message = failed == null
        ? pending.message
        : l10n.kitUndoFailed(pending.message);
    final actionLabel = failed == null
        ? (pending.undoLabel ?? l10n.kitUndoAction)
        : l10n.kitTryAgain;
    final semanticLabel = '$actionLabel, $message';
    final working = pending.working;

    // While working the action ignores taps (item 9), so its node says so:
    // not enabled, no tap action, and the "Undoing" value.
    final action = Semantics(
      key: pending.undoKey,
      // Its own node, never merged into the live region's label.
      container: true,
      button: true,
      enabled: !working,
      label: semanticLabel,
      value: working ? l10n.kitUndoWorking : null,
      onTap: working ? null : () => host.attemptUndo(pending),
      excludeSemantics: true,
      child: KitButton(
        role: KitButtonRole.tertiary,
        label: actionLabel,
        working: working,
        expand: false,
        onPressed: () => host.attemptUndo(pending),
      ),
    );
    // Its own node too: KitIconButton v2's node is not a container, so
    // without this its label would merge into the live region's.
    final dismiss = !showDismiss
        ? null
        : Semantics(
            container: true,
            child: KitIconButton(
              icon: AppIconography.close,
              tooltip: l10n.kitSheetDismiss,
              onPressed: () => host.dismiss(pending),
            ),
          );

    // One polite live region (A11Y-3) whose own label is the message: the
    // platform bridges re-announce a live region only when its label
    // changes, so the failure form (a new message) is announced once. The
    // visible text is excluded below so it is not read twice.
    return Semantics(
      key: pending.barKey,
      container: true,
      liveRegion: true,
      label: message,
      // A floating piece: the style paints it.
      child: kitSurfaceMaterial(
        context,
        level: KitSurfaceLevel.surface3,
        role: KitSurfaceRole.floating,
        fill: roles.surface3,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(tokens.panelCornerRadius),
          side: BorderSide(
            color: roles.hairline,
            width: KitTokens.hairlineWidth(context),
          ),
        ),
        child: Padding(
          padding: EdgeInsetsDirectional.symmetric(
            horizontal: tokens.space4,
            vertical: tokens.space3,
          ),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final stacked = _stacks(
                context,
                constraints.maxWidth,
                message,
                actionLabel,
                tokens,
                working: working,
                hasDismiss: dismiss != null,
              );
              final text = ExcludeSemantics(
                child: KitText(message, role: KitTextRole.body),
              );
              if (!stacked) {
                return Row(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    Expanded(child: text),
                    SizedBox(width: tokens.space3),
                    action,
                    if (dismiss != null) ...[
                      SizedBox(width: tokens.space2),
                      dismiss,
                    ],
                  ],
                );
              }
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  text,
                  SizedBox(height: tokens.space2),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      action,
                      if (dismiss != null) ...[
                        SizedBox(width: tokens.space2),
                        dismiss,
                      ],
                    ],
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }

  /// The action (and Dismiss) move under the message when both would not
  /// fit on one line (long words, large text): the same stacking rule as
  /// `KitStatusLine` (Accessibility, A11Y-8). Measured in the faces the bar
  /// renders with — the message's `KitText.styleOf(body)` and the tertiary
  /// button's theme text style — so the theme's font decides, not the
  /// framework default.
  bool _stacks(
    BuildContext context,
    double width,
    String message,
    String actionLabel,
    KitTokens tokens, {
    required bool working,
    required bool hasDismiss,
  }) {
    final scaler = MediaQuery.textScalerOf(context);
    double measure(String text, TextStyle? style) {
      final painter = TextPainter(
        text: TextSpan(text: text, style: style),
        textDirection: Directionality.of(context),
        textScaler: scaler,
        maxLines: 1,
      )..layout();
      final result = painter.width;
      painter.dispose();
      return result;
    }

    final buttonStyle =
        Theme.of(context).textButtonTheme.style?.textStyle?.resolve(const {}) ??
        KitText.styleOf(context, KitTextRole.button);
    final messageWidth = measure(
      message,
      KitText.styleOf(context, KitTextRole.body),
    );
    // A working tertiary button leads with its spinner (`_spinnerSize`) and
    // the icon gap of a text button with an icon.
    final spinner = working ? _spinnerSize + tokens.space2 : 0.0;
    final actionWidth = math.max(
      tokens.minTarget,
      measure(actionLabel, buttonStyle) + 2 * KitButton.tertiaryInset + spinner,
    );
    final reserved =
        tokens.space3 + (hasDismiss ? tokens.space2 + tokens.minTarget : 0);
    return messageWidth + actionWidth + reserved > width;
  }

  /// KitButton's working spinner (`_Spinner`, kit_buttons.dart).
  static const double _spinnerSize = 18;
}
