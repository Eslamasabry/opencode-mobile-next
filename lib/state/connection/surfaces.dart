part of '../connection.dart';

// Launcher surfaces: home-screen widget, pinned shortcuts, attention tile and the live status notification.

/// [ConnectionController]'s launcher surfaces.
mixin _ConnectionControllerSurfaces on ChangeNotifier {
  ConnectionController get _self;

  /// The most recent home-screen widget write started by [notifyListeners].
  Future<void>? _pendingWidgetSnapshotWrite;

  /// The most recent launcher shortcut / tile writes started by
  /// [notifyListeners], so deletion can wait for them instead of racing.
  Future<void>? _pendingLauncherWrite;

  /// Set while [deleteProfileAndLocalData] runs, so a notification cannot
  /// republish the sessions of the profile being erased — to the widget, the
  /// launcher shortcuts, or the tile.
  bool _widgetSnapshotSuspended = false;

  /// The ongoing "OpenCode is connected" notification's content, derived
  /// from the same truth the Activity tab shows: how many sessions run, how
  /// many requests wait, the most recently active busy session's title, and
  /// a generic sentence for the tool it is running.
  @visibleForTesting
  LiveStatus liveStatus() => _self._liveStatus();
}

extension _ConnectionControllerSurfacesImpl on ConnectionController {
  /// Keeps the launcher's pinned-session shortcuts and the Quick Settings
  /// tile's count in step with the connected profile. Both writers skip
  /// unchanged payloads. Nothing is published while connecting or while the
  /// lifecycle has suspended the transport: the entries stay as the last
  /// connected state left them, and only an explicit [disconnect] or a
  /// profile deletion withdraws them.
  void _publishLaunchSurfaces(List<Session> sortedSessions) {
    if (status != StreamStatus.connected) return;
    final profileID = _connectedProfile?.id ?? store.activeId ?? '';
    if (profileID.isEmpty) return;
    final pins = pinnedSessionIDs;
    final write = Future.wait<void>([
      _pinnedShortcuts.update(
        sessions: [
          for (final session in sortedSessions)
            if (pins.contains(session.id)) session,
        ],
        profileID: profileID,
        untitledLabel: _shellStrings().launchUiPinnedUntitled,
      ),
      _attentionTile.update(
        pendingCount: permissions.length + questions.length + forms.length,
        profileID: profileID,
      ),
    ]);
    _pendingLauncherWrite = write;
    unawaited(write);
  }

  /// Localized copy for surfaces the controller writes without a widget tree
  /// (launcher shortcut labels). Follows the in-app language choice, then the
  /// device locale, and falls back to English for a language the app does
  /// not ship.
  AppLocalizations _shellStrings() {
    final locale = appLocale.value ?? PlatformDispatcher.instance.locale;
    final supported = AppLocalizations.supportedLocales.any(
      (candidate) => candidate.languageCode == locale.languageCode,
    );
    return lookupAppLocalizations(
      supported ? Locale(locale.languageCode) : const Locale('en'),
    );
  }

  /// The body of [liveStatus].
  LiveStatus _liveStatus() {
    Session? current;
    for (final id in busySessions) {
      final session = sessionsById[id];
      if (session == null) continue;
      if (current == null ||
          (session.time?.updated ?? 0) > (current.time?.updated ?? 0)) {
        current = session;
      }
    }
    final title = current == null
        ? null
        : displaySessionTitleText(current.title);
    final detail = current == null ? null : _runningToolDetail[current.id];
    return LiveStatus(
      runningCount: busySessions.length,
      pendingCount: awaitingPermissionCount + questions.length + forms.length,
      title: title == null || title.isEmpty ? null : title,
      detail: detail,
    );
  }

  void _publishLiveStatus() {
    if (_disposed || !keepLiveInBackground || !backgroundLive.active) return;
    _runningToolDetail.removeWhere((id, _) => !busySessions.contains(id));
    unawaited(backgroundLive.publishLiveStatus(liveStatus()));
  }
}
