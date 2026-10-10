/// The end of a conversation list while the server has older pages: the
/// Work tab's list and the "Runs in" choice of a command. The list pages
/// itself (target-ia §1.4): the old "Load more" footer (map page
/// `embedded-session-inventory-footer`) was removed by slice-P3.1.
///
/// - More pages: two skeleton rows stand at the end, and the next page is
///   asked for once they come near the screen.
/// - A page loading: the same two skeleton rows.
/// - A page that failed, a list that changed on the server, a refresh
///   that failed or pinned conversations that could not be read: one error
///   notice in place with the one way on (Try again, or Refresh recent
///   conversations). A failed page or a changed list stops the paging
///   until the person acts; the others let it go on.
/// - Nothing more: nothing.
/// - The server not answering: no notice at all; the connection's status
///   line is the one place that says so (F5).
library;

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import 'product_states.dart' show productErrorDetails;
import '../kit/kit.dart';

class OlderSessionsPager extends StatelessWidget {
  const OlderSessionsPager({
    super.key,
    required this.controller,
    this.inset = true,
  });

  final ConnectionController controller;

  /// Puts the error notice on the screen's rails; false where the host
  /// (a sheet) already has them.
  final bool inset;

  /// Whether the pager has anything to show for [controller]'s list.
  static bool showsFor(ConnectionController controller) =>
      controller.sessionsLoadingMore ||
      (!controller.sessionsLoading &&
          (controller.hasMoreSessions ||
              controller.sessionsError != null ||
              controller.sessionsMoreError != null ||
              controller.pinnedSessionsLoadFailed));

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) => _build(context),
  );

  Widget _build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    if (controller.sessionsLoadingMore) {
      return const KitSkeletonRows(
        key: ValueKey('sessions-older-loading'),
        count: 2,
      );
    }
    // The first page has its own loading state on the host.
    if (controller.sessionsLoading) return const SizedBox.shrink();
    final listError = controller.sessionsError;
    final pageError = controller.sessionsMoreError;
    final pinError = controller.pinnedSessionsLoadFailed;
    final changed = controller.sessionsNeedReload;
    Widget? notice;
    // While the server is not answering, the connection's own status line
    // already says so with its Reconnect: a second panel for the same
    // cause would say it twice and offer "Report a problem" for an outage
    // (F5). The rows already listed stay, as last known.
    final outage = !controller.isConnected;
    if (!outage && (listError != null || pageError != null || pinError)) {
      // The list itself, or where it continues, is lost: start again from
      // the newest page. Only a failed older page is asked for again.
      final refresh = changed || pageError == null;
      final tokens = KitTokens.of(context);
      final error = KitNotice.error(
        key: const ValueKey('sessions-older-error'),
        message: changed
            ? l10n.sessionsListChanged
            : pageError != null
            ? l10n.sessionsOlderLoadFailed
            : listError != null
            ? l10n.sessionsLoadFailed
            : l10n.sessionPinsLoadFailed,
        details: _technical(
          controller.sessionsMoreFailure ??
              pageError ??
              controller.sessionsFailure ??
              listError,
        ),
        retry: KitAction(
          key: const ValueKey('sessions-older-retry'),
          label: changed ? l10n.sessionsReload : l10n.refreshRetry,
          onPressed: refresh
              ? controller.refreshSessions
              : controller.loadMoreSessions,
        ),
      );
      notice = inset
          ? Padding(
              padding: EdgeInsetsDirectional.symmetric(
                horizontal: tokens.gutter,
                vertical: tokens.space2,
              ),
              child: error,
            )
          : error;
    }
    // Older pages keep coming while the list can continue; a failed page
    // or a list that changed waits for the person, so a broken server is
    // never asked again and again.
    final more = controller.hasMoreSessions && pageError == null && !changed;
    if (!more) return notice ?? const SizedBox.shrink();
    final pager = _LoadWhenShown(
      key: const ValueKey('sessions-older-more'),
      onShown: controller.loadMoreSessions,
    );
    if (notice == null) return pager;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [notice, pager],
    );
  }
}

/// Two skeleton rows that ask for the next page once they are laid out
/// within [_reach] of their scroll view's viewport (or at once outside a
/// scroll view). They ask once per build of the list, so a list shorter
/// than the screen keeps filling it and a long one stops until it is
/// scrolled; the controller ignores a request while a page is loading.
class _LoadWhenShown extends StatefulWidget {
  const _LoadWhenShown({super.key, required this.onShown});

  final VoidCallback onShown;

  @override
  State<_LoadWhenShown> createState() => _LoadWhenShownState();
}

class _LoadWhenShownState extends State<_LoadWhenShown> {
  /// How far off screen the end may be and still load: a page arrives
  /// before the person reaches it.
  static const _reach = 240.0;

  ScrollPosition? _position;
  bool _asked = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final position = Scrollable.maybeOf(context)?.position;
    if (!identical(position, _position)) {
      _position?.removeListener(_check);
      _position = position?..addListener(_check);
    }
    WidgetsBinding.instance.addPostFrameCallback((_) => _check());
  }

  /// The list changed (a page arrived, even an empty one): ask again if
  /// the end is still in reach.
  @override
  void didUpdateWidget(covariant _LoadWhenShown oldWidget) {
    super.didUpdateWidget(oldWidget);
    _asked = false;
    WidgetsBinding.instance.addPostFrameCallback((_) => _check());
  }

  @override
  void dispose() {
    _position?.removeListener(_check);
    super.dispose();
  }

  void _check() {
    if (_asked || !mounted) return;
    // A hidden tab waits until it is shown again.
    if (!TickerMode.valuesOf(context).enabled) return;
    final box = context.findRenderObject();
    if (box is! RenderBox || !box.attached || !box.hasSize) return;
    final viewport = Scrollable.maybeOf(context)?.context.findRenderObject();
    if (viewport is RenderBox && viewport.attached && viewport.hasSize) {
      final top = box.localToGlobal(Offset.zero).dy;
      final viewTop = viewport.localToGlobal(Offset.zero).dy;
      final viewBottom = viewTop + viewport.size.height;
      if (top > viewBottom + _reach ||
          top + box.size.height < viewTop - _reach) {
        return;
      }
    }
    _asked = true;
    widget.onShown();
  }

  @override
  Widget build(BuildContext context) => const KitSkeletonRows(count: 2);
}

/// What failed, redacted, for a Details fold; null when nothing did.
String? _technical(Object? failure) =>
    failure == null ? null : productErrorDetails(failure);
