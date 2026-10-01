part of '../connection.dart';

// AI Team orchestration, team alerts and team progress notifications.

/// Re-post an unchanged line this often, well inside the native
/// 20-minute self-timeout, so a long step keeps its line. Rides on the
/// team's periodic refresh; no timer of its own.
const _teamProgressRepost = Duration(minutes: 10);

/// [ConnectionController]'s AI Team orchestration and alerts.
mixin _ConnectionControllerTeam on ChangeNotifier {
  ConnectionController get _self;

  /// The AI Team plugin's sibling controller for the connected profile;
  /// null while that profile's [ServerProfile.orchestration] is null. This
  /// controller only constructs and disposes it and adds its attention
  /// count (04-plugin-architecture §9).
  OrchestrationController? get orchestration => _orchestration;
  OrchestrationController? _orchestration;

  /// The plugin's per-profile store, for the discovery-offer memory and the
  /// cache size shown in Settings. Data writes stay with the controller.
  OrchestrationStore get orchestrationStore => _self._orchestrationStore;

  /// Re-evaluates the connected profile's plugin config after it changed:
  /// disposes a controller whose config is gone or different and builds
  /// one for a config that is new.
  void syncOrchestration() => _self._syncOrchestrationBody();

  /// The AI Team's alert decisions for the current plugin controller
  /// (TEAM-203); replaced with it, null without one.
  TeamAlertTracker? _teamAlerts;

  /// Test seam: adopts [controller] as the plugin controller the way a
  /// connect does — listener and alert tracker included — without a
  /// server. The caller owns the controller's lifecycle.
  @visibleForTesting
  void adoptOrchestrationForTesting(OrchestrationController controller) =>
      _self._adoptOrchestrationForTesting(controller);

  /// Posted team alerts by key, so a dismiss-all and a settled gate can
  /// take them down.
  final Set<String> _postedTeamAlerts = {};

  /// The one ongoing, silent "AI Team: TASK · step 3 of 5" line while
  /// tasks work, under the same toggle, quiet and background rules as every
  /// other alert. Removed when nothing works; the native side also drops it
  /// after twenty minutes without a refresh. Finishing or needing the
  /// person is the existing alert's job.
  String? _teamProgressKey;
  String? _teamProgressLine;
  DateTime? _teamProgressPostedAt;
}

extension _ConnectionControllerTeamImpl on ConnectionController {
  /// The body of [syncOrchestration].
  void _syncOrchestrationBody() {
    final connected = _connectedProfile;
    if (connected != null) {
      // The store is the source of truth for the config: an editor save
      // replaces the stored instance, so a stale connected copy would keep
      // the old config alive.
      for (final stored in store.profiles) {
        if (stored.id == connected.id) {
          connected.orchestration = stored.orchestration;
          connected.teamEngineAuth = stored.teamEngineAuth;
          break;
        }
      }
    }
    _syncOrchestration(connected);
    // The sibling changed hands (or went away); screens showing its state
    // rebuild from here, as they do for every other controller change.
    if (!_disposed) _notifyListeners();
  }

  void _phoneEngineReady(String profileId) {
    if (_lifecycleSuspended || !_phoneChatEligible(profileId) || isConnected) {
      return;
    }
    // Protected setup deliberately retires the old phone server. Recover a
    // retained failed transport through the ordinary generation-fenced path;
    // fresh status/SSE, not engine readiness, owns connection and chat truth.
    unawaited(retryConnection().catchError((Object _) {}));
  }

  void _phoneEngineAttached(String profileId) {
    if (_disposed || _connectedProfile?.id != profileId) return;
    // A native restart may rotate auth even when the nonsecret URL is unchanged.
    final current = _orchestration;
    if (current != null) {
      current.removeListener(_orchestrationChanged);
      current.dispose();
      _orchestration = null;
    }
    syncOrchestration();
  }

  void _syncOrchestration(ServerProfile? selected) {
    final config = selected?.orchestration;
    final current = _orchestration;
    if (current != null &&
        selected != null &&
        current.profileId == selected.id &&
        current.config == config) {
      return;
    }
    if (current != null) {
      current.removeListener(_orchestrationChanged);
      current.dispose();
      _orchestration = null;
      _dismissTeamAlerts();
      _teamAlerts = null;
    }
    if (config == null || selected == null || isIsolated || _disposed) return;
    final next = OrchestrationController(
      profile: selected,
      config: config,
      store: _orchestrationStore,
      probe: config.provider == OrchestrationProvider.phoneEngine
          ? (_) => phoneProjectEngine.orchestrationProbe(selected)
          : null,
      gatewayFactory: config.provider == OrchestrationProvider.phoneEngine
          ? (_, _) => phoneProjectEngine.orchestrationGateway(selected)
          : null,
    )..addListener(_orchestrationChanged);
    _orchestration = next;
    _teamAlerts = TeamAlertTracker(profileId: selected.id);
    unawaited(next.start());
  }

  /// The body of [adoptOrchestrationForTesting].
  void _adoptOrchestrationForTesting(OrchestrationController controller) {
    _orchestration?.removeListener(_orchestrationChanged);
    _orchestration = controller..addListener(_orchestrationChanged);
    _teamAlerts = TeamAlertTracker(profileId: controller.profileId);
  }

  /// Posts one alert per new decision, failed run, review and completed
  /// run the plugin controller reports (04 §6, 06 decision 8), under the
  /// same toggle and quiet rules as every other coding alert. Ids only:
  /// the platform copy is fixed and the saved server's name is the only
  /// user-chosen text. Nothing here answers anything.
  void _syncTeamAlerts() {
    final team = _orchestration;
    final tracker = _teamAlerts;
    if (team == null || tracker == null) return;
    _syncTeamProgress(team);
    final diff = tracker.observe(team.snapshot);
    if (diff.isEmpty) return;
    for (final key in diff.settled) {
      if (_postedTeamAlerts.remove(key)) {
        unawaited(backgroundLive.dismissCodingAlert(key));
      }
    }
    if (!_canShowCodingAlert) return;
    for (final alert in diff.alerts) {
      _postedTeamAlerts.add(alert.key);
      unawaited(
        _postTeamNotification(
          kind: alert.kind,
          profileID: team.profileId,
          sessionID: alert.id,
          key: alert.key,
          subtext: team.profile.name,
        ).then((shown) {
          if (!shown && !_disposed) _postedTeamAlerts.remove(alert.key);
        }),
      );
    }
  }

  void _syncTeamProgress(OrchestrationController team) {
    final glance = teamGlanceFromSnapshot(team.snapshot);
    final working = glance.top.where((task) => !task.needsYou).toList();
    if (glance.working == 0 || !_canShowCodingAlert) {
      _clearTeamProgress();
      return;
    }
    final l10n = _shellStrings();
    TeamGlanceTask? one;
    if (glance.working == 1 && working.isNotEmpty) one = working.first;
    final line = one == null
        ? l10n.teamProgressMany(glance.working)
        : one.stepsTotal > 0
        ? l10n.teamProgressStep(
            one.title,
            (one.stepsDone + 1).clamp(1, one.stepsTotal),
            one.stepsTotal,
          )
        : l10n.teamProgressOne(one.title);
    final key = 'team:${team.profileId}:progress';
    final sessionID = one?.id ?? working.firstOrNull?.id ?? '';
    final signature = '$sessionID|$line';
    final now = DateTime.now();
    final postedAt = _teamProgressPostedAt;
    if (signature == _teamProgressLine &&
        key == _teamProgressKey &&
        postedAt != null &&
        now.difference(postedAt) < _teamProgressRepost) {
      return;
    }
    if (sessionID.isEmpty) {
      _clearTeamProgress();
      return;
    }
    _teamProgressLine = signature;
    _teamProgressKey = key;
    _teamProgressPostedAt = now;
    unawaited(
      _postTeamNotification(
        kind: CodingAlertKind.teamProgress,
        profileID: team.profileId,
        sessionID: sessionID,
        key: key,
        text: line,
      ).then((shown) {
        if (!shown && _teamProgressLine == signature) {
          _teamProgressLine = null;
        }
      }),
    );
  }

  /// The one posting site for AI Team notifications (alerts and the ongoing
  /// progress line): no actions, ids only.
  Future<bool> _postTeamNotification({
    required CodingAlertKind kind,
    required String profileID,
    required String sessionID,
    required String key,
    String subtext = '',
    String text = '',
  }) => backgroundLive.showCodingAlert(
    kind: kind,
    profileID: profileID,
    sessionID: sessionID,
    key: key,
    allowActions: false,
    subtext: subtext,
    text: text,
  );

  void _clearTeamProgress() {
    final key = _teamProgressKey;
    if (key == null) return;
    _teamProgressKey = null;
    _teamProgressLine = null;
    _teamProgressPostedAt = null;
    unawaited(backgroundLive.dismissCodingAlert(key));
  }

  void _dismissTeamAlerts() {
    _clearTeamProgress();
    for (final key in _postedTeamAlerts.toList()) {
      unawaited(backgroundLive.dismissCodingAlert(key));
    }
    _postedTeamAlerts.clear();
    _teamAlerts?.clearOpen();
  }
}
