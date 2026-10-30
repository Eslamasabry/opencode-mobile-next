part of '../connection.dart';

// Notification preferences and the background profile and quota monitors.

/// [ConnectionController]'s notification preferences and background monitors.
mixin _ConnectionControllerMonitors on ChangeNotifier {
  ConnectionController get _self;

  ProfileMonitor? _profileMonitor;
  ProviderQuotaMonitor? _quotaMonitor;
  PaseoUsageMonitor? _paseoUsageMonitor;
  UsageSubscriptionsController? _usageSubscriptions;
  ComputerUsageSource? _computerUsage;

  /// Every subscription across every connection, with its alert state. See
  /// docs/design/usage-subscriptions-contract-2026-10-13.md.
  UsageSubscriptionsController get usageSubscriptions =>
      _self._usageSubscriptionsBody;
  ProviderQuotaMonitor get quotaMonitor => _self._quotaMonitorBody;

  /// The one-shot usage check on saved OpenCode computers (opt-in per server).
  ComputerUsageSource get computerUsage => _self._computerUsageBody;

  /// Plan usage from opted-in Paseo servers, for the Usage widget.
  PaseoUsageMonitor get paseoUsageMonitor => _self._paseoUsageMonitorBody;

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
  /// Something is set up to be read, so the subscriptions refresh may run on
  /// its own. Without it the controller waits for the Usage page to ask.
  bool get _hasUsageReaders =>
      quotaMonitor.sources.isNotEmpty ||
      paseoUsageMonitor.optedIn(UsageConnection.phoneId) ||
      paseoUsageMonitor.paseoProfiles.any(
        (p) => paseoUsageMonitor.optedIn(p.id),
      ) ||
      store.profiles.any(
        (p) =>
            looksLikeInAppServer(p) &&
            (store.prefs.getBool(InAppQuotaCollector.enabledKey(p.id)) ??
                false),
      ) ||
      store.profiles.any((p) => computerUsage.isEnabled(p.id));

  /// The body of [computerUsage].
  ComputerUsageSource get _computerUsageBody =>
      _computerUsage ??= ComputerUsageSource(
        prefs: store.prefs,
        profiles: () => store.profiles,
        isReadable: (id) => _ownsProfileServices && isProfileReadable(id),
        isInApp: looksLikeInAppServer,
        createPort: (profile) {
          final pair = (_monitorGatewayFactory ?? _buildTransportPair)(profile);
          return GatewayComputerTerminalPort(
            pair.operations,
            onClose: pair.gateway.close,
          );
        },
        loadSource: () => rootBundle.loadString(computerUsageAsset),
      );

  /// The body of [usageSubscriptions].
  UsageSubscriptionsController get _usageSubscriptionsBody {
    final existing = _usageSubscriptions;
    if (existing != null) return existing;
    final collector = QuotaCollectorSource(
      profiles: () => store.profiles,
      isReadable: (id) => _ownsProfileServices && isProfileReadable(id),
      isInApp: looksLikeInAppServer,
      inAppEnabled: (id) =>
          store.prefs.getBool(InAppQuotaCollector.enabledKey(id)) ?? false,
      createGateway: (profile, provider) => HttpProviderQuotaGateway(
        profile,
        provider: provider,
        inAppToken: InAppQuotaCollector.tokenReaderFor(profile),
      ),
      monitor: quotaMonitor,
      sharedRules: () => sharedNotifyRules,
    );
    final controller = UsageSubscriptionsController(
      prefs: store.prefs,
      connections: () => [
        for (final p in store.profiles)
          UsageConnection(
            id: p.id,
            name: p.name,
            kind: usageConnectionKindFor(p, inApp: looksLikeInAppServer(p)),
          ),
        if (phoneUsageSupported)
          const UsageConnection(
            id: UsageConnection.phoneId,
            name: '',
            kind: UsageConnectionKind.phoneAgents,
          ),
      ],
      sources: [
        collector,
        computerUsage,
        PaseoUsageSource(
          monitor: paseoUsageMonitor,
          paseoProfileIds: () => [
            for (final p in store.profiles)
              if (p.backend == ServerBackend.paseo) p.id,
          ],
        ),
        AgentAccountSource(
          current: () {
            final gateway = api;
            final saved = profile;
            if (!isConnected ||
                saved == null ||
                !capabilities.agentAccount ||
                gateway is! AgentAccountGateway) {
              return null;
            }
            return (
              connection: UsageConnection(
                id: saved.id,
                name: saved.name,
                kind: usageConnectionKindFor(saved, inApp: false),
              ),
              gateway: gateway as AgentAccountGateway,
            );
          },
        ),
        OpenCodeSignInSource(
          profiles: () => store.profiles,
          isReadable: (id) => _ownsProfileServices && isProfileReadable(id),
          providers: (profile) async {
            final pair = (_monitorGatewayFactory ?? _buildTransportPair)(
              profile,
            );
            try {
              return await pair.gateway.providers();
            } finally {
              pair.gateway.close();
            }
          },
        ),
      ],
      sharedRules: () => sharedNotifyRules,
      setQuotaAlerts: (value) => updateSharedNotifyRules(
        (rules) => rules.copyWith(quotaAlerts: value),
      ),
      alertBridge: collector,
      readerNotes: computerUsage,
      subscriptionAlerts: SubscriptionAlertDelivery(
        prefs: store.prefs,
        sharedRules: () => sharedNotifyRules,
        wifi: backgroundLive.monitorWifiAvailable,
        dismiss: (key) async {
          await backgroundLive.dismissCodingAlert(key);
        },
        show: (note) => _postQuotaAlert(
          kind: note.reset ? CodingAlertKind.quotaReset : CodingAlertKind.quota,
          profileID: note.profileId,
          key: note.key,
          token: note.token,
          agentName: note.providerName,
        ),
      ),
      widget: UsageWidgetSnapshot(prefs: store.prefs),
    )..addListener(_quotaMonitorChanged);
    return _usageSubscriptions = controller;
  }

  /// The body of [paseoUsageMonitor].
  PaseoUsageMonitor get _paseoUsageMonitorBody =>
      _paseoUsageMonitor ??= PaseoUsageMonitor(
        store: store,
        widget: UsageWidgetSnapshot(prefs: store.prefs),
        createGateway: PaseoProviderUsageGateway.new,
        phoneSupported: () => _self.phoneUsageSupported,
        phoneRead: _self._paReadUsage,
        autoRead: true,
        isReadable: (id) => _ownsProfileServices && isProfileReadable(id),
      )..addListener(_quotaMonitorChanged);

  /// The body of [quotaMonitor].
  ProviderQuotaMonitor get _quotaMonitorBody =>
      _quotaMonitor ??= ProviderQuotaMonitor(
        store: store,
        widget: UsageWidgetSnapshot(prefs: store.prefs),
        createGateway: (profile, provider) => HttpProviderQuotaGateway(
          profile,
          provider: provider,
          inAppToken: InAppQuotaCollector.tokenReaderFor(profile),
        ),
        isReadable: (id) => _ownsProfileServices && isProfileReadable(id),
        networkWifi: backgroundLive.monitorWifiAvailable,
        dismiss: backgroundLive.dismissCodingAlert,
        alert: ({required profileID, required key, required token}) =>
            _postQuotaAlert(
              kind: CodingAlertKind.quota,
              profileID: profileID,
              key: key,
              token: token,
            ),
      )..addListener(_quotaMonitorChanged);

  /// The one place a usage alert is posted; both monitors come through here.
  Future<bool> _postQuotaAlert({
    required CodingAlertKind kind,
    required String profileID,
    required String key,
    required String token,
    String agentName = '',
  }) => backgroundLive.showCodingAlert(
    kind: kind,
    sessionID: 'quota',
    profileID: profileID,
    key: key,
    monitorToken: token,
    allowActions: false,
    agentName: agentName,
  );

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
      // A watched server's poll keeps no command and no reply path: the
      // alert asks in general words and opens the chat.
      allowActions: false,
      monitorToken: token,
      subtext: notificationSubtitle(
        store.profiles.where((saved) => saved.id == id).firstOrNull?.name,
        _projectNameOfDirectory(request.directory),
      ),
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
