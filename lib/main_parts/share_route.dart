part of '../main.dart';

extension _OcAppShareRoute on _OcAppState {
  /// Text shared from another app becomes the first prompt of a new session.
  /// Until a server is connected the text waits, and the user is told once
  /// where it went, so a share never silently disappears.
  void _scheduleShareRoute() {
    if (_shareRouteScheduled ||
        _share.pending.value == null ||
        _share.pending.value == _failedShareText) {
      return;
    }
    final connected =
        _controller.hasConnectedServer &&
        !_controller.connectionLoading &&
        !_controller.locationLoading;
    if (!connected) {
      if (!_shareWaitingNoticeShown) {
        _shareWaitingNoticeShown = true;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          final context = _navigatorKey.currentContext;
          if (!mounted || context == null) return;
          _showWaiting(
            _OcAppState._shareWaitingNotice,
            AppLocalizations.of(context).shareWaitingForServer,
          );
        });
      }
      return;
    }
    _shareRouteScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      _shareWaitingNoticeShown = false;
      _clearNotice(_OcAppState._shareWaitingNotice);
      if (!mounted) return;
      final navigator = _navigatorKey.currentState;
      if (navigator == null) {
        _shareRouteScheduled = false;
        _scheduleShareRoute();
        return;
      }
      if (_controller.connectionLoading || _controller.locationLoading) {
        _shareRouteScheduled = false;
        return;
      }
      final text = _share.pending.value;
      if (text == null) {
        _shareRouteScheduled = false;
        return;
      }
      final location = _controller.locationRevision;
      final api = _controller.api;
      final repository = _controller.repository;
      try {
        final session = await _controller.createSession();
        if (!mounted) return;
        if (location != _controller.locationRevision ||
            !identical(api, _controller.api) ||
            !identical(repository, _controller.repository)) {
          throw ProductException(
            AppLocalizations.of(navigator.context).shareConnectionChanged,
          );
        }
        unawaited(
          navigator.push(
            KitPageRoute<void>(
              builder: (_) =>
                  ChatScreen(sessionID: session.id, initialText: text),
            ),
          ),
        );
        if (_share.pending.value == text) _share.take();
        _failedShareText = null;
        _shareFailure = null;
        _shareFailures = 0;
        _clearNotice(_OcAppState._shareFailedNotice);
      } catch (error) {
        if (!mounted) return;
        // A newer share replaced this text meanwhile: that one goes next.
        if (_share.pending.value == text) {
          _failedShareText = text;
          _shareFailure = error;
          _shareFailures += 1;
          _showShareFailed(text);
        }
      } finally {
        _shareRouteScheduled = false;
        if (mounted) _scheduleShareRoute();
      }
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  /// The shared text could not open a conversation (map page
  /// share-session-failed-banner): it stays saved, the line says why in
  /// words, Try again opens it, and More copies or discards it, so the text
  /// is never lost behind a dismissed banner. A second failure says so.
  void _showShareFailed(String text) {
    final context = _navigatorKey.currentContext;
    if (context == null) return;
    final l10n = AppLocalizations.of(context);
    final failure = _shareFailure;
    _showNotice(
      KitStatus(
        kind: KitStatusKind.work,
        id: _OcAppState._shareFailedNotice,
        icon: AppIconography.outbox,
        tone: AppStatusTone.failure,
        message: _shareFailures > 1
            ? l10n.shareFailedAgainLine
            : l10n.shareFailedLine,
        supporting: failure == null
            ? null
            : productErrorText(failure, l10n: l10n),
        action: KitAction(
          key: const ValueKey('share-failed-retry'),
          label: l10n.commonRetry,
          onPressed: _retryShare,
        ),
        more: [
          KitAction(
            key: const ValueKey('share-failed-copy'),
            label: l10n.shareFailedCopy,
            icon: AppIconography.copy,
            onPressed: () {
              final target = _routeTracker.topContext;
              if (target == null) return;
              // The person's own words: copied as they are (SEC-13).
              unawaited(KitCopy.copy(target, text, redact: false));
            },
          ),
          KitAction(
            key: const ValueKey('share-failed-discard'),
            label: l10n.shareFailedDiscard,
            icon: AppIconography.delete,
            destructive: true,
            onPressed: () => _discardShare(text),
          ),
        ],
      ),
      lasting: true,
    );
  }

  void _retryShare() {
    if (!mounted) return;
    _failedShareText = null;
    _clearNotice(_OcAppState._shareFailedNotice);
    _scheduleShareRoute();
  }

  /// Discards the saved shared text with Undo: it is dropped only when the
  /// Undo bar goes.
  void _discardShare(String text) {
    // The top page's context: under the overlay the Undo bar goes into.
    final context = _routeTracker.topContext;
    if (context == null) return;
    _clearNotice(_OcAppState._shareFailedNotice);
    showKitUndo(
      context,
      key: const ValueKey('share-discarded'),
      message: AppLocalizations.of(context).shareDiscarded,
      onCommit: () {
        if (_share.pending.value == text) _share.take();
        if (_failedShareText == text) _failedShareText = null;
        _shareFailure = null;
        _shareFailures = 0;
      },
      onUndo: () {
        if (mounted && _share.pending.value == text) _showShareFailed(text);
      },
    );
  }
}
