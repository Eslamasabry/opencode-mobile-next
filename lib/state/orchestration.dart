/// The AI Team plugin's controller: one per profile whose
/// [ServerProfile.orchestration] is set, constructed and disposed by
/// `ConnectionController` and never embedded in it (04-plugin-architecture
/// §1, §9).
///
/// Lifecycle: [start] probes the host, builds the adapter for the
/// configured provider, subscribes to its event stream and refreshes every
/// scope. Events do not carry data into the snapshot; they mark scopes
/// dirty and a short debounce refetches only those (§5). The timeline is
/// append-only from events and bounded. [stop] closes the gateway;
/// [remove] also sweeps the profile's store so turning the plugin off
/// leaves no trace.
///
/// Writes (TEAM-202, §6) go through [mutate] and its typed helpers
/// ([answerGate], [messageAgent], [controlAgent], [cancelRun],
/// [assignWork], [createWork] and the two-step [giveTask]): a [MutationRecord] with a fresh idempotency key is
/// persisted BEFORE the gateway is called, the receipt updates it, and the
/// host's matching `request.result` event (or the effect's own event)
/// marks it confirmed. A record still sent after [mutationTimeout] becomes
/// unconfirmed; so does every sent record found on restart. Nothing is
/// ever re-sent by the controller: [retryMutation] makes a new record
/// under a new key and only when a person asks.
///
/// Merge (TEAM-205): [mergeReadiness] reads the host front's readiness
/// document per run and caches it until [refresh]; [approveMergeRequest]
/// and [mergeRun] are ordinary mutations through the same path, allowed
/// only when the adapter implements [OrchestrationMergeGateway].
///
/// Policy (TEAM-207): [policy] is the host's read-only supervision level
/// and boundaries, read with the projects scope on every refresh when the
/// adapter implements [OrchestrationPolicyGateway] (a host front) and
/// null otherwise, so the run overview and the Start-a-run sheet show the
/// host's rules or nothing.
///
/// Dispatch cycle (TEAM-116): [cycleFor] derives where a work item is in
/// the host's dispatch chain (routed → agent starting → claimed → working
/// → pushed → handed to merge → merged) and why it waits, from the
/// snapshot, the timeline and the agent's transcript. Cycles are derived
/// on demand and cached until the next event, refresh or tick; the tick
/// ([cycleTick], 30 s) runs only while a strip watches ([watchCycles]) and
/// some cycle is still moving, and while watched the controller reads the
/// transcript of an agent that claimed but has not pushed, so a provider
/// usage limit surfaces as the stall reason without anyone opening the
/// output.
library;

import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';

import '../domain/orchestration_gateway.dart';
import '../orchestration/adapters/inapp/phone_engine_gateway.dart';
import '../orchestration/adapters/fixture/fixture_gateway.dart';
import '../orchestration/adapters/fixture/project_fixture_gateway.dart';
import '../orchestration/adapters/gascity/gascity_gateway.dart';
import '../orchestration/adapters/gascity/gascity_mappers.dart'
    show mapStreamFrame;
import '../orchestration/adapters/gascity/gascity_probe.dart';
import '../orchestration/client/sse.dart';
import '../orchestration/dispatch.dart';
import 'automation_policy.dart';
import 'orchestration_store.dart';
import 'profiles.dart';
import 'team_worker_start.dart';
import 'team_project_controller.dart';
import 'team_project_persistence.dart';

export '../orchestration/adapters/gascity/gascity_probe.dart'
    show
        ProbeCityNotRunning,
        ProbeFound,
        ProbeNotGasCity,
        ProbePlainHttpRefused,
        ProbeUnreachable,
        ProbeVerdict;
export '../orchestration/client/sse.dart' show OrchestrationStreamStatus;
export '../orchestration/dispatch.dart'
    show deriveDispatchCycle, isRefineryName, workHandedToMerge;
export 'mutation_store.dart'
    show MutationKind, MutationRecord, MutationRequest, MutationStatus;
export 'orchestration_store.dart' show TeamLastKnown;

part 'orchestration/types.dart';
part 'orchestration/stream.dart';
part 'orchestration/mutations.dart';
part 'orchestration/cycles.dart';

/// Per-profile orchestration state: host identity, capabilities, the
/// snapshot, stream liveness, the dirty-scope refresh loop, the timeline
/// and the attention contribution.
class OrchestrationController extends ChangeNotifier {
  OrchestrationController({
    required this.profile,
    required this.config,
    required OrchestrationStore store,
    OrchestrationGatewayFactory? gatewayFactory,
    OrchestrationProbe? probe,
    DateTime Function()? now,
    MutationKeyMinter? mintKey,
    this.refreshDebounce = const Duration(milliseconds: 400),
    this.staleAfter = const Duration(seconds: 60),
    this.timelineLimit = 500,
    this.mutationTimeout = const Duration(seconds: 60),
    this.cycleTick = const Duration(seconds: 30),
  }) : _store = store,
       _gatewayFactory =
           gatewayFactory ??
           ((config, found) =>
               config.provider == OrchestrationProvider.fixture &&
                   config.url == 'fixture://project-demo'
               ? ProjectFixtureGateway(
                   persistence: SharedPreferencesTeamProjectPersistence(
                     store.prefs,
                     profile.id,
                   ),
                   now: now,
                   tickInterval: const Duration(seconds: 8),
                 )
               : defaultGatewayFactory(
                   config,
                   found,
                   profileId: profile.id,
                   bearerToken: profile.teamEngineAuth,
                 )),
       _probe =
           probe ??
           ((config) => defaultProbe(
             config,
             profileId: profile.id,
             bearerToken: profile.teamEngineAuth,
           )),
       _now = now ?? DateTime.now,
       _mintKey = mintKey ?? mintMutationKey;

  final ServerProfile profile;
  final OrchestrationConfig config;
  final OrchestrationStore _store;
  final OrchestrationGatewayFactory _gatewayFactory;
  final OrchestrationProbe _probe;
  final DateTime Function() _now;

  /// How long dirty scopes accumulate before one refetch.
  final Duration refreshDebounce;

  /// Data older than this counts as stale (with a live stream or not).
  final Duration staleAfter;

  /// Timeline entries kept; older ones are dropped from the front.
  final int timelineLimit;

  /// How long a sent mutation waits for the host's result before it is
  /// shown as unconfirmed (02-ux §6). A late result still confirms it.
  final Duration mutationTimeout;

  /// How often a watched, still-moving dispatch cycle is re-derived so a
  /// stall window elapsing shows without an event (TEAM-116).
  final Duration cycleTick;
  final MutationKeyMinter _mintKey;

  OrchestrationGateway? _gateway;
  TeamProjectController? _projectController;

  /// Project lifecycle support is optional; older engines keep their task UI.
  TeamProjectController? get projectController => _projectController;
  OrchestrationSseClient? _sse;
  StreamSubscription<OrchestrationEvent>? _events;
  StreamSubscription<OrchestrationStreamStatus>? _status;
  StreamSubscription<HeadOnlyReplay>? _replays;
  Timer? _debounce;
  Future<void>? _refresh;
  bool _refreshQueued = false;
  bool _started = false;
  bool _disposed = false;
  final _dirty = <String>{};
  final _timeline = <OrchestrationEvent>[];
  final _outputs = <String, AgentOutputTail>{};
  final _outputWatchers = <String, int>{};
  final _outputSubscriptions = <String, StreamSubscription<AgentOutputEvent>>{};
  final _mutations = <String, MutationRecord>{};
  final _mutationTimers = <String, Timer>{};
  final _mergeReadiness = <String, MergeReadiness>{};
  final _mergeReadinessErrors = <String, Object>{};
  final _mergeReadinessLoads = <String, Future<MergeReadiness?>>{};
  List<ScheduledJob>? _scheduledJobs;
  Object? _scheduledJobsError;
  Future<List<ScheduledJob>?>? _scheduledJobsLoad;
  OrchestrationPolicy? _policy;
  final _cycles = <String, DispatchCycle>{};
  final _cycleProbes = <String, StreamSubscription<AgentOutputEvent>>{};
  final _cycleTranscripts = <String, String>{};

  /// Sessions whose probe stream ended (the host stopped serving them):
  /// never opened again; their text stays.
  final _cycleProbesEnded = <String>{};
  final _cycleLimitSeen = <String>{};
  int _cycleWatchers = 0;
  Timer? _cycleTimer;
  bool _cyclesMoving = false;

  /// Results that arrived before their receipt did (a fast host answers
  /// the stream before the HTTP response lands), by correlation id.
  final _earlyResults = <String, RequestResult>{};
  bool _mutationsLoaded = false;
  int? _timelineSeq;
  EventCursor _cursor = EventCursor.none;

  OrchestrationPhase _phase = OrchestrationPhase.idle;
  OrchestrationHostIdentity? _host;
  OrchestrationCapabilities _capabilities = OrchestrationCapabilities.none;
  String _boundaryTier = '';
  OrchestrationSnapshot _snapshot = const OrchestrationSnapshot();
  OrchestrationStreamStatus _streamStatus = OrchestrationStreamStatus.closed;
  OrchestrationError? _lastError;
  DateTime? _lastEventAt;

  String get profileId => profile.id;

  /// The last worker start measured on this phone for this profile.
  TeamWorkerStartStore get workerStarts => TeamWorkerStartStore(_store.prefs);

  /// This server's local automation choices (Settings › What runs by
  /// itself): the shared controller, never a second writer.
  AutomationPolicyController get automation =>
      AutomationPolicyController.forProfile(_store.prefs, profileId);
  OrchestrationPhase get phase => _phase;

  /// Identity of the host: from the probe, then the gateway once it
  /// connected. Null until the probe answered.
  OrchestrationHostIdentity? get host => _host;

  /// [OrchestrationCapabilities.none] until the gateway is built.
  OrchestrationCapabilities get capabilities => _capabilities;

  /// The proven file boundary of the phone engine (`proot`, `landlock`) once
  /// it can run work; empty when unknown or the engine is read-only.
  String get boundaryTier => _boundaryTier;
  OrchestrationSnapshot get snapshot => _snapshot;

  /// The team as the app last read it (this run or an earlier one, from
  /// the device), for a page that shows it dimmed while the team is
  /// stopped; null before any read. Never acted on.
  TeamLastKnown? get lastKnown {
    if (!_lastKnownRead) {
      _lastKnownRead = true;
      _lastKnown = _store.readLastKnown(profile.id);
    }
    return _lastKnown;
  }

  TeamLastKnown? _lastKnown;
  bool _lastKnownRead = false;
  OrchestrationStreamStatus get streamStatus => _streamStatus;
  OrchestrationError? get lastError => _lastError;

  /// Resume position, advanced by every numbered event.
  EventCursor get cursor => _cursor;

  /// When the last event (heartbeats included) arrived.
  DateTime? get lastEventAt => _lastEventAt;
  DateTime? get lastRefreshedAt => _snapshot.refreshedAt;

  /// The adapter, for screens that need a per-item read (agent output).
  OrchestrationGateway? get gateway => _gateway;

  /// The host's supervision policy (TEAM-207), cached with the snapshot:
  /// refreshed with the projects scope, null until a host front answered
  /// it and always null against a bare supervisor or the fixture.
  OrchestrationPolicy? get policy => _policy;

  /// Scopes marked dirty by events and not yet refetched.
  Set<String> get dirtyScopes => Set.unmodifiable(_dirty);

  /// Every non-heartbeat event received, oldest first, at most
  /// [timelineLimit].
  List<OrchestrationEvent> get timeline => List.unmodifiable(_timeline);

  /// True while ready and either the data is older than [staleAfter] or
  /// the event stream is not live: the card shows "Showing data from
  /// HH:MM" and disables everything but Refresh.
  bool get isStale {
    if (_phase != OrchestrationPhase.ready) return false;
    if (_streamStatus != OrchestrationStreamStatus.live) return true;
    final at = _snapshot.refreshedAt;
    return at == null || _now().difference(at) > staleAfter;
  }

  /// The controller's clock (host time; tests pin it), for lines that age
  /// against the team's own evidence, e.g. a task row's stall.
  DateTime now() => _now();

  /// What needs the person: pending interactions (choice, confirmation,
  /// free text and unrecognised kinds), open gate beads and failed runs.
  /// Review-ready items are informational and not counted.
  int get attentionCount {
    var count = 0;
    for (final gate in _snapshot.gates) {
      switch (gate.kind) {
        case GateKind.choice:
        case GateKind.confirmation:
        case GateKind.freeText:
        case GateKind.unknown:
        case GateKind.gateBead:
        case GateKind.runFailed:
          count += 1;
        case GateKind.reviewReady:
          break;
      }
    }
    return count;
  }

  // -------------------------------------------------------------------------
  // Lifecycle
  // -------------------------------------------------------------------------

  /// Probes, builds the adapter, subscribes and refreshes every scope.
  /// Idempotent; a failed probe leaves [phase] failed and [lastError] set.
  Future<void>? _starting;

  Future<void> start() => _starting ??= _start();

  Future<void> _start() async {
    if (_started || _disposed) return;
    _started = true;
    _cursor = _store.readCursor(profile.id);
    await _loadMutations();
    if (_stoppedMeanwhile) return;
    _setPhase(OrchestrationPhase.probing);

    final ProbeVerdict verdict;
    try {
      verdict = await _probe(config);
    } catch (error) {
      _fail(OrchestrationErrorKind.unreachable, '$error');
      return;
    }
    if (_stoppedMeanwhile) return;
    final error = OrchestrationError.fromVerdict(verdict);
    if (error != null || verdict is! ProbeFound) {
      _lastError = error;
      _setPhase(OrchestrationPhase.failed);
      return;
    }
    _host = verdict.host;
    _boundaryTier = verdict.readOnly ? '' : verdict.boundaryTier;
    _setPhase(OrchestrationPhase.connecting);

    final OrchestrationGateway gateway;
    try {
      gateway = await _gatewayFactory(config, verdict);
      if (gateway is GasCityGateway) {
        try {
          await gateway.connect();
        } catch (_) {
          // The probe already identified the host; reads decide from here.
        }
      }
    } catch (error) {
      _fail(OrchestrationErrorKind.unreachable, '$error');
      return;
    }
    if (_stoppedMeanwhile) {
      await gateway.close();
      return;
    }
    _gateway = gateway;
    _capabilities = gateway.capabilities;
    _host = gateway.host ?? _host;
    if (gateway is OrchestrationProjectGateway &&
        _capabilities.projectLifecycle) {
      final projects = TeamProjectController(
        gateway as OrchestrationProjectGateway,
        profileId: profile.id,
        ownsGateway: false,
        preferences: _store.prefs,
      )..addListener(_notify);
      _projectController = projects;
      await projects.load();
      if (_stoppedMeanwhile) return;
    }
    _subscribe(gateway);
    _setPhase(OrchestrationPhase.ready);
    await refresh();
  }

  /// After a failed probe or connect (the card's Retry): probes and
  /// connects again. A no-op in every other phase.
  Future<void> retry() {
    if (_phase != OrchestrationPhase.failed || _disposed) {
      return Future.value();
    }
    _started = false;
    _starting = null;
    _lastError = null;
    return start();
  }

  bool get _stoppedMeanwhile =>
      _disposed || _phase == OrchestrationPhase.stopped;

  void _fail(OrchestrationErrorKind kind, String message) {
    if (_stoppedMeanwhile) return;
    _lastError = OrchestrationError(kind, message);
    _setPhase(OrchestrationPhase.failed);
  }

  /// Closes the stream and the gateway and persists the cursor. Idempotent.
  Future<void>? _stopping;

  Future<void> stop() => _stopping ??= _stop();

  Future<void> _stop() async {
    if (_phase == OrchestrationPhase.stopped) return;
    _phase = OrchestrationPhase.stopped;
    _debounce?.cancel();
    _debounce = null;
    _dirty.clear();
    for (final timer in _mutationTimers.values) {
      timer.cancel();
    }
    _mutationTimers.clear();
    for (final subscription in _outputSubscriptions.values) {
      unawaited(subscription.cancel());
    }
    _outputSubscriptions.clear();
    _outputWatchers.clear();
    for (final tail in _outputs.values) {
      tail._watching = false;
    }
    _mergeReadiness.clear();
    _mergeReadinessErrors.clear();
    _scheduledJobs = null;
    _policy = null;
    _cycleTimer?.cancel();
    _cycleTimer = null;
    for (final probe in _cycleProbes.values) {
      unawaited(probe.cancel());
    }
    _cycleProbes.clear();
    _cycles.clear();
    final projects = _projectController;
    _projectController = null;
    projects?.removeListener(_notify);
    projects?.dispose();
    await projects?.drainEditorDrafts();
    await _unsubscribe();
    final gateway = _gateway;
    _gateway = null;
    if (gateway != null) await gateway.close();
    await _starting;
    _setStreamStatus(OrchestrationStreamStatus.closed);
    await _store.saveCursor(profile.id, _cursor);
    _notify();
  }

  /// The Work tab view (`list` / `graph`) last chosen for [runId] on this
  /// profile, or null when the screen should pick its default.
  String? workView(String runId) => _store.readWorkView(profile.id, runId);

  /// Remembers the Work tab view for [runId] under the profile's prefix
  /// (`workView.` + run id); swept with the rest when the plugin is off.
  Future<void> rememberWorkView(String runId, String? view) =>
      _store.saveWorkView(profile.id, runId, view);

  /// True when the person dismissed the Start-a-run request under the
  /// mutation [key] (TEAM-204); its "Planning…" card stays hidden.
  bool isPlanningDismissed(String key) =>
      _store.readPlanningDismissed(profile.id).contains(key);

  /// Hides the Start-a-run request [key]'s card for good; persisted under
  /// the profile's prefix (`planningDismissed`), swept with the rest.
  Future<void> dismissPlanning(String key) async {
    await _store.savePlanningDismissed(profile.id, key);
    _notify();
  }

  /// Turns the plugin off for this profile: stops, then deletes every
  /// `oc.orchestration.<profileId>.` key and secret. Returns the keys the
  /// store refused to drop.
  Future<Set<String>> remove() async {
    final failures = <String>{};
    final projects = _projectController;
    if (projects != null) {
      try {
        await projects.deleteLocalData();
      } catch (_) {
        failures.add('oc.teamWorkspace.${profile.id}');
      }
    }
    await stop();
    await _store.drain(profile.id);
    for (final key in [
      'oc.teamWorkspace.${profile.id}',
      'oc.teamEditorDrafts.${profile.id}',
    ]) {
      try {
        if (!await _store.prefs.remove(key)) {
          failures.add(key);
        } else {
          failures.remove(key);
        }
      } catch (_) {
        failures.add(key);
      }
    }
    failures.addAll(await _store.sweep(profile.id));
    return failures;
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    unawaited(stop());
    super.dispose();
  }

  /// Refetches every scope now (the Refresh action). Cached merge
  /// readiness is fetched again for the runs that had it.
  Future<void> refresh() {
    _dirty.addAll(OrchestrationScope.all);
    _debounce?.cancel();
    _debounce = null;
    for (final runId in _mergeReadiness.keys.toList()) {
      unawaited(mergeReadiness(runId, force: true));
    }
    if (_scheduledJobs != null) unawaited(scheduledJobs(force: true));
    return _refetchDirty();
  }

  // -------------------------------------------------------------------------
  // Plumbing
  // -------------------------------------------------------------------------

  void _setPhase(OrchestrationPhase next) {
    if (_phase == next) return;
    _phase = next;
    _notify();
  }

  void _setStreamStatus(OrchestrationStreamStatus next) {
    if (_streamStatus == next) return;
    _streamStatus = next;
    _notify();
  }

  void _notify() {
    // Cycles are derived from what just changed: drop the cache and let
    // the next [cycleFor] say whether anything still moves.
    _cycles.clear();
    _cyclesMoving = false;
    if (!_disposed) notifyListeners();
  }

  // -------------------------------------------------------------------------
  // Defaults
  // -------------------------------------------------------------------------

  /// Gas City: [GasCityProbe] on the config's URL and city. Fixture: found
  /// at once, the recordings answer for the host.
  static Future<ProbeVerdict> defaultProbe(
    OrchestrationConfig config, {
    String profileId = '',
    String bearerToken = '',
  }) {
    switch (config.provider) {
      case OrchestrationProvider.phoneEngine:
        return _probePhoneEngine(config, profileId, bearerToken);
      case OrchestrationProvider.gascity:
        return GasCityProbe(
          hostMode: config.hostMode,
        ).probe(config.url, city: config.city.isEmpty ? null : config.city);
      case OrchestrationProvider.fixture:
        return Future.value(
          ProbeFound(
            host: OrchestrationHostIdentity(
              provider: 'fixture',
              url: config.url,
              hostMode: config.hostMode,
            ),
            city: config.city.isEmpty ? null : config.city,
          ),
        );
    }
  }

  static Future<ProbeVerdict> _probePhoneEngine(
    OrchestrationConfig config,
    String profileId,
    String bearerToken,
  ) async {
    PhoneEngineGateway? gateway;
    try {
      gateway = PhoneEngineGateway(
        baseUrl: config.url,
        profileId: profileId,
        bearerToken: bearerToken,
      );
      final health = await gateway.probe();
      return ProbeFound(
        host: gateway.host,
        version: health.engineVersion,
        readOnly: !health.canExecute,
        capabilities: gateway.capabilities,
        boundaryTier: health.boundaryTier,
      );
    } catch (_) {
      return const ProbeUnreachable(error: 'Phone engine unavailable');
    } finally {
      await gateway?.close();
    }
  }

  /// [GasCityGateway] for Gas City (the city from the config, else the one
  /// the probe reported; the front's `supervisorUrl` with controls on when
  /// the probe found a front that allows this device to write);
  /// [FixtureOrchestrationGateway] over the directory in `url` for the
  /// fixture.
  static OrchestrationGateway defaultGatewayFactory(
    OrchestrationConfig config,
    ProbeFound found, {
    String profileId = '',
    String bearerToken = '',
  }) {
    switch (config.provider) {
      case OrchestrationProvider.phoneEngine:
        return PhoneEngineGateway(
          baseUrl: config.url,
          profileId: profileId,
          bearerToken: bearerToken,
          probedCapabilities: found.capabilities,
        );
      case OrchestrationProvider.gascity:
        final front = found.front && found.identityAllowed;
        return GasCityGateway(
          url: front ? found.host.url : config.url,
          city: config.city.isEmpty ? (found.city ?? '') : config.city,
          hostMode: config.hostMode,
          front: front,
        );
      case OrchestrationProvider.fixture:
        const scheme = 'fixture://';
        final path = config.url.startsWith(scheme)
            ? config.url.substring(scheme.length)
            : config.url;
        return FixtureOrchestrationGateway(
          fixturePath: path,
          hostMode: config.hostMode,
        );
    }
  }
}
