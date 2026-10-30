// KitPageRoute — the one way to push a page (docs/ux-system/kit-api/
// KitPageRoute.md; kit-v2 §9.1 "Routes" row, §9.2; design standard §10).
import 'package:flutter/material.dart';

import 'kit_motion.dart';
import 'motion/kit_page_transitions.dart';

/// A pushed page with the kit's transition (design standard §10), whatever
/// the ambient `pageTransitionsTheme` says: on Android the platform's own,
/// with the predictive back gesture ([KitAndroidPageTransitionsBuilder],
/// owner rule 2026-10-26); elsewhere the shared-axis fade-through of
/// [KitPageTransitionsBuilder]. Screens never
/// choose a transition — construct this (or, from inside a widget, call
/// [pushKitPage] / [replaceWithKitPage]) instead of `MaterialPageRoute` or
/// `PageRouteBuilder` (KIT-7 retires both from the kit-only allowlist).
///
/// Everything about the transition is fixed by the kit: [transitionDuration]
/// and [reverseTransitionDuration] are the Android transition's own on
/// Android and [KitMotion.standard] elsewhere with motion on (a page that
/// grew out of a row closes in at most [KitMotion.rowReturn]), and zero when
/// the person asks for reduced motion (the back gesture then pops at once,
/// with no preview). The route is
/// always [opaque], has no [barrierColor] or [barrierLabel] and is never
/// [barrierDismissible]. Only [maintainState], [fullscreenDialog] and
/// [allowSnapshotting] are a call site's to choose.
class KitPageRoute<T> extends PageRoute<T> {
  /// Pushes [builder] as a page. [fullscreenDialog] gives the page a Close
  /// action instead of Back (see `KitTopBar`), for a page that ends a flow
  /// rather than continuing it.
  KitPageRoute({
    required this.builder,
    super.settings,
    this.maintainState = true,
    super.fullscreenDialog = false,
    super.allowSnapshotting = true,
  });

  /// Builds the page's contents.
  final WidgetBuilder builder;

  @override
  final bool maintainState;

  @override
  Color? get barrierColor => null;

  @override
  String? get barrierLabel => null;

  static const _kit = KitPageTransitionsBuilder();
  static const _android = KitAndroidPageTransitionsBuilder();

  bool get _reduced {
    final context = navigator?.context;
    return context != null && KitMotion.reduced(context);
  }

  bool get _isAndroid {
    final context = navigator?.context;
    return context != null &&
        KitAndroidPageTransitionsBuilder.appliesTo(context);
  }

  PageTransitionsBuilder get _transitions => _isAndroid ? _android : _kit;

  @override
  Duration get transitionDuration => _reduced
      ? Duration.zero
      : _isAndroid
      ? _android.transitionDuration
      : KitMotion.standard;

  /// The predictive back gesture previews the page underneath only with
  /// motion on; under reduced motion a back gesture pops at once.
  @override
  bool get popGestureEnabled => !_reduced && super.popGestureEnabled;

  /// A page that grew out of a row closes faster than it opened
  /// ([KitMotion.rowReturn]); every other page closes as it opened.
  @override
  Duration get reverseTransitionDuration {
    final open = transitionDuration;
    if (!KitPageOrigin.grewFromRow(this)) return open;
    return open < KitMotion.rowReturn ? open : KitMotion.rowReturn;
  }

  @override
  bool didPop(T? result) {
    // The preference can change while this page is open. Refresh the
    // controller's exit duration before reversing it so a reduced-motion
    // exit finishes immediately, including when it arrived with motion on.
    controller?.reverseDuration = reverseTransitionDuration;
    return super.didPop(result);
  }

  @override
  DelegatedTransitionBuilder? get delegatedTransition =>
      _transitions.delegatedTransition;

  /// The app's HeroController holds a newly pushed page offstage for its
  /// first frame, to measure where the page's heroes land once the
  /// transition ends. Under reduced motion the kit transition draws the
  /// arriving page whole and in place from its first frame, so that frame
  /// has nothing to measure and would only hide the page: it stays onstage
  /// and shows after one pump (MOT-7, G8x). With motion on, the measuring
  /// frame stays as the framework has it.
  @override
  set offstage(bool value) {
    final context = navigator?.context;
    if (value && context != null && KitMotion.reduced(context)) return;
    super.offstage = value;
  }

  @override
  Widget buildPage(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
  ) {
    return Semantics(
      scopesRoute: true,
      explicitChildNodes: true,
      child: builder(context),
    );
  }

  @override
  Widget buildTransitions(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    return _transitions.buildTransitions<T>(
      this,
      context,
      animation,
      secondaryAnimation,
      child,
    );
  }
}

/// Pushes [builder] as a [KitPageRoute] on the nearest [Navigator] (the
/// root one, with [rootNavigator]).
Future<T?> pushKitPage<T>(
  BuildContext context,
  WidgetBuilder builder, {
  RouteSettings? settings,
  bool fullscreenDialog = false,
  bool rootNavigator = false,
}) {
  return Navigator.of(context, rootNavigator: rootNavigator).push<T>(
    KitPageRoute<T>(
      builder: builder,
      settings: settings,
      fullscreenDialog: fullscreenDialog,
    ),
  );
}

/// Replaces the current route with [builder] as a [KitPageRoute] — the
/// one-way hand-offs (setup finished → Work) that never come back with Back.
Future<T?> replaceWithKitPage<T, TO>(
  BuildContext context,
  WidgetBuilder builder, {
  RouteSettings? settings,
  TO? result,
}) {
  return Navigator.of(context).pushReplacement<T, TO>(
    KitPageRoute<T>(builder: builder, settings: settings),
    result: result,
  );
}
