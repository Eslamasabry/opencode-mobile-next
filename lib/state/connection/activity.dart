part of '../connection.dart';

// Automatic activity: acts the app took without the person.

/// [ConnectionController]'s automatic activity record.
mixin _ConnectionControllerActivity on ChangeNotifier {
  ConnectionController get _self;

  /// The automatic acts the app did for the profile it shows (P6.2, AUTO-4):
  /// the one shared history per profile, or null with no profile. The
  /// Inbox's While you were away lists them; they never feed Needs you.
  AutomaticActivityController? get automaticActivity =>
      _self._automaticActivity;

  final Set<AutomaticActivityController> _watchedActivity = {};

  /// The automatic acts not yet dismissed for what the app shows: the
  /// server's own (a reconnect, a heat pause) and the open project's (a
  /// request allowed, a queued message sent), newest first.
  List<AutomaticAct> get automaticActsHere => _self._automaticActsHere;

  /// Where an act on the project the app shows is filed: the same identity
  /// as [returnBriefScope] (server address, folder, workspace).
  String get automaticActivityProject => _self.returnBriefScope.$2;

  /// Where an act on the server as a whole (a reconnect, a heat pause) is
  /// filed: the server's address alone, whatever project is open.
  String automaticActivityServer(ServerProfile owner) =>
      jsonEncode([owner.baseUrl]);

  /// Files one automatic act the app has CONFIRMED (never an attempt) in
  /// [profileId]'s history. [target] names what it was done to (a
  /// conversation's title, the server's name); the Inbox says what was done
  /// from [kind]. [eventId] identifies this occurrence, so a repeat of the
  /// same delivery is filed once. Profile, place and time are captured by
  /// the caller at the act. Isolated task connections file nothing: the
  /// app's own connection reports its server.
  Future<bool> recordAutomaticAct({
    required String profileId,
    required String location,
    required AutomaticActKind kind,
    required String target,
    required String eventId,
    required DateTime at,
    String? sessionId,
  }) => _self._recordAutomaticAct(
    profileId: profileId,
    location: location,
    kind: kind,
    target: target,
    eventId: eventId,
    at: at,
    sessionId: sessionId,
  );

  /// Files an automatic act on a saved server as a whole (a heat pause of
  /// the team on this phone): named by the server, filed under its address.
  /// Nothing for a profile that is gone.
  Future<bool> recordServerAct({
    required String profileId,
    required AutomaticActKind kind,
    required String eventId,
    required DateTime at,
  }) => _self._recordServerAct(
    profileId: profileId,
    kind: kind,
    eventId: eventId,
    at: at,
  );
}

extension _ConnectionControllerActivityImpl on ConnectionController {
  /// The body of [automaticActivity].
  AutomaticActivityController? get _automaticActivity {
    final owner = _connectedProfile ?? profile;
    return owner == null ? null : _automaticActivityFor(owner.id);
  }

  AutomaticActivityController? _automaticActivityFor(String profileId) {
    if (_disposed || profileId.isEmpty) return null;
    final history = AutomaticActivityController.forProfile(
      store.prefs,
      profileId,
      isProfilePresent: () => store.profiles.any((p) => p.id == profileId),
    );
    if (history != null && _watchedActivity.add(history)) {
      history.addListener(_automaticActivityChanged);
    }
    return history;
  }

  /// The body of [automaticActsHere].
  List<AutomaticAct> get _automaticActsHere {
    final owner = _connectedProfile ?? profile;
    final history = automaticActivity;
    if (owner == null || history == null) return const [];
    final places = {automaticActivityServer(owner), automaticActivityProject};
    return WhileAwaySnapshot.build(
      automaticActs: [
        for (final place in places) ...history.forLocation(place),
      ],
      sessions: const [],
      readStateKnown: supportsSessionReadState,
      inventoryPartial: false,
      isUnread: (_) => false,
      isBusy: (_) => false,
      blockerOf: (_) => null,
    ).automaticActs;
  }

  /// The body of [recordAutomaticAct].
  Future<bool> _recordAutomaticAct({
    required String profileId,
    required String location,
    required AutomaticActKind kind,
    required String target,
    required String eventId,
    required DateTime at,
    String? sessionId,
  }) async {
    if (isIsolated) return false;
    final history = _automaticActivityFor(profileId);
    if (history == null) return false;
    try {
      return await history.record(
        eventId: eventId,
        location: location,
        kind: kind,
        summary: target,
        occurredAt: at,
        sessionId: sessionId,
      );
    } catch (_) {
      return false;
    }
  }

  /// The body of [recordServerAct].
  Future<bool> _recordServerAct({
    required String profileId,
    required AutomaticActKind kind,
    required String eventId,
    required DateTime at,
  }) {
    for (final owner in store.profiles) {
      if (owner.id != profileId) continue;
      return recordAutomaticAct(
        profileId: owner.id,
        location: automaticActivityServer(owner),
        kind: kind,
        target: owner.name,
        eventId: eventId,
        at: at,
      );
    }
    return Future.value(false);
  }

  /// The conversation's title as the Inbox would name it, at the act.
  String _automaticActSessionTitle(String sessionID) {
    final title = displaySessionTitleText(sessionsById[sessionID]?.title);
    return title.isNotEmpty ? title : _shellStrings().globalSessionsUntitled;
  }

  /// Captures the connected server and the open project now, for an act
  /// whose confirmation arrives later.
  ({String profileId, String server, String project, String name})?
  _automaticActScope() {
    final owner = _connectedProfile ?? profile;
    if (owner == null || isIsolated) return null;
    return (
      profileId: owner.id,
      server: automaticActivityServer(owner),
      project: automaticActivityProject,
      name: owner.name,
    );
  }
}
