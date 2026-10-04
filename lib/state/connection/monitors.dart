part of '../connection.dart';

// Notification preferences and the background profile and quota monitors.

/// [ConnectionController]'s notification preferences and background monitors.
mixin _ConnectionControllerMonitors on ChangeNotifier {
  ConnectionController get _self;

  ProfileMonitor? _profileMonitor;
  ProviderQuotaMonitor? _quotaMonitor;
  ProviderQuotaMonitor get quotaMonitor => _self._quotaMonitorBody;

  /// The shared rules as they apply now: the migrated value, or what the
  /// legacy per-server records say while the migration has not run.
  SharedNotifyRules get sharedNotifyRules =>
      _self.notificationPreferences.shared ??
      _self.notificationPreferences.readLegacy(_self._notifyMigrationOrder);

  /// Folds the legacy per-server definitions into the shared one. Safe to
  /// call on every start: after the first run it changes nothing.
  Future<void> migrateNotificationPreferences() =>
      _self.notificationPreferences.migrate(_self._notifyMigrationOrder);

  Future<void> updateSharedNotifyRules(
    SharedNotifyRules Function(SharedNotifyRules current) change,
  ) => _self._updateSharedNotifyRules(change);

  Future<void> setNotifyFinishedRuns(bool value) =>
      _self._setNotifyFinishedRuns(value);

  Future<void> setNotifyRequests(bool value) => _self._setNotifyRequests(value);
  final _monitorAttentionReader = MonitorAttentionReader();
  ProfileMonitor get profileMonitor => _self._profileMonitorBody;
}

extension _ConnectionControllerMonitorsImpl on ConnectionController {
  /// The body of [quotaMonitor].
  ProviderQuotaMonitor get _quotaMonitorBody =>
      _quotaMonitor ??= ProviderQuotaMonitor(
        store: store,
        createGateway: (profile, provider) =>
            HttpProviderQuotaGateway(profile, provider: provider),
        isReadable: (id) => _ownsProfileServices && isProfileReadable(id),
        networkWifi: backgroundLive.monitorWifiAvailable,
        dismiss: backgroundLive.dismissCodingAlert,
        alert: ({required profileID, required key, required token}) =>
            backgroundLive.showCodingAlert(
              kind: CodingAlertKind.quota,
              sessionID: 'quota',
              profileID: profileID,
              key: key,
              monitorToken: token,
              allowActions: false,
            ),
      )..addListener(_quotaMonitorChanged);

  /// The connected server first, so its quiet hours win a disagreement.
  List<String> get _notifyMigrationOrder {
    final first = profile?.id ?? store.activeId;
    return [
      ?first,
      for (final saved in store.profiles)
        if (saved.id != first) saved.id,
    ];
  }

  /// The body of [updateSharedNotifyRules].
  Future<void> _updateSharedNotifyRules(
    SharedNotifyRules Function(SharedNotifyRules current) change,
  ) async {
    final before = await notificationPreferences.migrate(_notifyMigrationOrder);
    final after = change(before);
    if (after == before) return;
    await notificationPreferences.save(after);
    if (_disposed) return;
    await profileMonitor.sharedRulesChanged(
      checkInChanged: after.checkInAfterMinutes != before.checkInAfterMinutes,
    );
    await quotaMonitor.sharedRulesChanged();
    if (!_disposed) _notifyListeners();
  }

  /// The body of [setNotifyFinishedRuns].
  Future<void> _setNotifyFinishedRuns(bool value) async {
    await notificationPreferences.setFinishedRuns(value);
    if (_disposed) return;
    if (!value) {
      for (final sessionID in _alertedStatusSessions.toList()) {
        unawaited(
          backgroundLive.dismissCodingAlert(_statusAlertKey(sessionID)),
        );
      }
      _alertedStatusSessions.clear();
    }
    _notifyListeners();
  }

  /// The body of [setNotifyRequests].
  Future<void> _setNotifyRequests(bool value) async {
    await notificationPreferences.setRequests(value);
    if (_disposed) return;
    if (!value) {
      for (final sessionID in _alertedInputKinds.keys.toList()) {
        unawaited(backgroundLive.dismissCodingAlert(_inputAlertKey(sessionID)));
      }
      _alertedInputKinds.clear();
    }
    _notifyListeners();
  }

  /// The body of [profileMonitor].
  ProfileMonitor get _profileMonitorBody => _profileMonitor ??= ProfileMonitor(
    store: store,
    createGateway: _monitorGatewayFactory ?? _buildTransportPair,
    readAttention: _readMonitorAttention,
    isReadable: (id) => _ownsProfileServices && isProfileReadable(id),
    networkWifi: backgroundLive.monitorWifiAvailable,
    dismiss: backgroundLive.dismissCodingAlert,
    alertsAllowed: (id) => id != profile?.id,
    alert: (id, request, key, token) => backgroundLive.showCodingAlert(
      kind: switch (request.kind) {
        MonitoredRequestKind.permission => CodingAlertKind.permission,
        MonitoredRequestKind.question ||
        MonitoredRequestKind.form => CodingAlertKind.question,
        MonitoredRequestKind.checkIn => CodingAlertKind.checkIn,
      },
      profileID: id,
      sessionID: request.sessionID,
      key: key,
      allowActions: false,
      monitorToken: token,
    ),
  )..addListener(_monitorChanged);
  Future<MonitorAttentionDetails> _readMonitorAttention(
    ServerProfile saved,
    MonitorGatewayPair pair,
    List<Session> sessions,
    Map<String, String> statuses,
    bool Function() current,
  ) async {
    final generation = _generation;
    final began = DateTime.now();
    final details = await _monitorAttentionReader.read(
      saved,
      pair,
      sessions,
      statuses,
      current,
    );
    if (current() && generation == _generation && profile?.id == saved.id) {
      // A complete observation of a later turn may retire an SSE failure.
      // A failure arriving during this read remains authoritative.
      _failedAttentionSessions.removeWhere(
        (id, failure) =>
            details.checkedSessionIDs.contains(id) &&
            failure.at.isBefore(began),
      );
    }
    return details;
  }

  void _syncProfileServices() {
    if (!_ownsProfileServices || _disposed) return;
    ManagedServerRecovery.syncProfiles(
      store.prefs,
      store.profiles
          .where(
            (p) =>
                TermuxBridge.supported &&
                isProfileReadable(p.id) &&
                TermuxBridge.managesServerUrl(p.baseUrl),
          )
          .map((p) => p.id),
      runtimes: {
        for (final p in store.profiles)
          if (isProfileReadable(p.id) &&
              TermuxBridge.managesServerUrl(p.baseUrl))
            p.id: ManagedRuntimeFlavor.runtimeOf(p),
      },
      onRestart: ({required profileId, required eventId, required at}) =>
          recordServerAct(
            profileId: profileId,
            kind: AutomaticActKind.restart,
            eventId: eventId,
            at: at,
          ),
    );
  }
}
