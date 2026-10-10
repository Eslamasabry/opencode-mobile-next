/// The Gas City adapter: [OrchestrationGateway] over the supervisor's HTTP
/// API and SSE stream, capabilities [OrchestrationCapabilities.gascityRead]
/// against a bare supervisor and [OrchestrationCapabilities.gascityFront]
/// behind a host front that allows this device to write.
///
/// Reads compose the DTO mappers of `gascity_mappers.dart`; nothing here
/// interprets provider data itself. Controls and the merge roles go
/// through [GasCityControl] when [GasCityGateway.front] is set and are
/// refused with a rejected receipt ("front required") otherwise (merge
/// readiness and the policy document answer null). No request ever
/// carries a credential (see `client/http.dart`).
///
/// One exception, the phone host (TEAM-301): a city the app itself runs on
/// this device is reached on loopback where the supervisor trusts the
/// `X-GC-Request` header alone, so [GasCityGateway.loopbackControl] turns
/// the controls on without a front — posted straight to the supervisor,
/// no `Idempotency-Key` (nothing on the host stores receipts; a receipt is
/// confirmed by the event stream only). The front-only merge roles and the
/// policy document stay absent ([OrchestrationCapabilities.gascityLoopback]).
library;

import 'dart:async';

import '../../../domain/loopback_host.dart';
import '../../../domain/orchestration_gateway.dart';
import '../../client/http.dart';
import '../../client/sse.dart';
import 'dto/dto.dart';
import 'gascity_control.dart';
import 'gascity_mappers.dart';
import 'gascity_probe.dart';

export '../../client/sse.dart' show HeadOnlyReplay, OrchestrationStreamStatus;

/// The host reported a list as incomplete (`partial: true`).
class PartialNotice {
  const PartialNotice({
    required this.scope,
    required this.reasons,
    required this.at,
  });

  /// `runs` or `work`.
  final String scope;

  /// Host-provided reasons (`run projection is warming`).
  final List<String> reasons;
  final DateTime at;

  @override
  String toString() => 'PartialNotice($scope: ${reasons.join('; ')})';
}

/// Read + event gateway for one Gas City city.
///
/// Construct with the host [url] and [city]; [connect] fetches `/health`
/// and `/status` to fill [host] (every read works without it). The URL is
/// checked against the plain-HTTP rule at construction so a refused
/// address never gets a client.
class GasCityGateway
    implements
        OrchestrationGateway,
        OrchestrationAgentOutputGateway,
        OrchestrationMergeGateway,
        OrchestrationPolicyGateway,
        OrchestrationRigGateway {
  GasCityGateway({
    required String url,
    required this.city,
    this.hostMode = OrchestrationHostMode.computer,
    this.front = false,
    Map<String, String> defaultQuery = const {},
    Duration connectTimeout = const Duration(seconds: 8),
    Duration receiveTimeout = const Duration(seconds: 30),
    Duration stallTimeout = const Duration(seconds: 90),
    Duration backoffCap = const Duration(seconds: 30),
    Duration backoffBase = const Duration(milliseconds: 500),
    this.finishedRunsWindow = defaultFinishedRunsWindow,
    this.finishedRunsLimit = defaultFinishedRunsLimit,
    DateTime Function()? clock,
    OrchestrationHttpClient? http,
  }) : _stallTimeout = stallTimeout,
       _backoffCap = backoffCap,
       _backoffBase = backoffBase,
       _clock = clock ?? DateTime.now {
    final parsed = Uri.tryParse(url.trim());
    if (parsed == null || !isOrchestrationUrlAllowed(parsed)) {
      throw ArgumentError.value(
        url,
        'url',
        'https:// anywhere, http:// only to loopback or tailnet hosts',
      );
    }
    _http =
        http ??
        OrchestrationHttpClient(
          baseUrl: url,
          city: city,
          connectTimeout: connectTimeout,
          receiveTimeout: receiveTimeout,
          defaultQuery: defaultQuery,
        );
    _control = front || loopbackControl
        ? GasCityControl(
            http: _http,
            sessionForGate: _sessionForGate,
            agentFor: agent,
            runKindFor: _runKindFor,
            idempotency: front,
          )
        : null;
  }

  static const _frontRequired = 'front required';

  /// How far back finished runs are listed: a week.
  static const defaultFinishedRunsWindow = Duration(days: 7);

  /// At most this many finished runs are listed.
  static const defaultFinishedRunsLimit = 20;

  final String city;

  /// Finished runs (closed convoys) older than this are not listed.
  final Duration finishedRunsWindow;

  /// The most finished runs one read lists (and the page size it asks for).
  final int finishedRunsLimit;
  final DateTime Function() _clock;

  /// `GET /convoy/{id}` answers of finished convoys, by id: a closed
  /// convoy does not change, so each is fetched once. Pruned to the ids
  /// the latest read listed.
  final _finishedConvoys = <String, GcConvoy>{};
  final OrchestrationHostMode hostMode;

  /// [url] is a host front that allows this device to write: controls are
  /// on and every mutation carries an `Idempotency-Key`.
  final bool front;
  late final GasCityControl? _control;
  final Duration _stallTimeout;
  final Duration _backoffCap;
  final Duration _backoffBase;
  late final OrchestrationHttpClient _http;
  final _partial = <String, PartialNotice>{};
  final _streams = <OrchestrationSseClient>{};
  OrchestrationHostIdentity? _host;
  bool _closed = false;

  /// The HTTP client, for diagnostics (`lastRequestId`).
  OrchestrationHttpClient get http => _http;
  String get url => _http.baseUrl;

  /// Controls without a front: the host is this phone
  /// ([OrchestrationHostMode.phone]) and [url] is loopback, where the
  /// supervisor honours `X-GC-Request` on its own (03-onboarding §3).
  bool get loopbackControl =>
      !front &&
      hostMode == OrchestrationHostMode.phone &&
      isLoopbackHost(Uri.parse(_http.baseUrl).host);

  @override
  OrchestrationCapabilities get capabilities => front
      ? OrchestrationCapabilities.gascityFront
      : loopbackControl
      ? OrchestrationCapabilities.gascityLoopback
      : OrchestrationCapabilities.gascityRead;

  @override
  OrchestrationHostIdentity? get host => _host;

  @override
  bool get isClosed => _closed;

  /// Partial-result notices from the latest reads, by scope (`runs`,
  /// `work`); a scope disappears once a read comes back complete.
  Map<String, PartialNotice> get partialNotices => Map.unmodifiable(_partial);

  /// Fetches `/health` and `/status` and fills [host]. Throws on transport
  /// or HTTP failure so the caller can show why.
  Future<OrchestrationHostIdentity> connect() async {
    final results = await Future.wait([
      _http.getCity('/health'),
      _http.getCity('/status').catchError((Object _) => <String, Object?>{}),
    ]);
    final health = GcHealth.fromJson(results[0]);
    final statusJson = results[1];
    final identity = mapHostIdentity(
      url: _http.baseUrl,
      hostMode: hostMode,
      health: health,
      status: statusJson.isEmpty ? null : GcStatus.fromJson(statusJson),
    );
    _host = identity;
    return identity;
  }

  @override
  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    for (final stream in _streams.toList()) {
      await stream.close();
    }
    _streams.clear();
    _http.close();
  }

  // -------------------------------------------------------------------------
  // Reads
  // -------------------------------------------------------------------------

  @override
  Future<List<OrchestrationProject>> projects() async {
    final status = GcStatus.fromJson(await _http.getCity('/status'));
    _host ??= mapHostIdentity(
      url: _http.baseUrl,
      hostMode: hostMode,
      status: status,
    );
    return mapRigs(status);
  }

  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async {
    final all = (await _runs()).runs;
    if (projectId == null) return all;
    return [
      for (final run in all)
        if (run.projectId == null || run.projectId == projectId) run,
    ];
  }

  @override
  Future<OrchestrationRun?> run(String id) async {
    for (final run in (await _runs()).runs) {
      if (run.id == id) return run;
    }
    return null;
  }

  /// `/runs` (formula runs) merged with `/convoys` (batches) and the
  /// recently finished batches ([_finishedConvoyHistory]); the work
  /// snapshot supplies the tracked items convoys derive their state from.
  /// A `partial` `/runs` answer is surfaced in [partialNotices].
  Future<_RunsSnapshot> _runs() async {
    final results = await Future.wait<Object>([
      _http.getCity('/runs'),
      _http.getCity('/convoys'),
      _workSnapshot(),
      _finishedConvoyHistory(),
    ]);
    final runsList = mapRunsList(
      GcRunsList.fromJson(results[0] as Map<String, Object?>),
    );
    _notePartial('runs', runsList);
    final convoys = GcList<GcConvoy>.fromJson(
      results[1] as Map<String, Object?>,
      GcConvoy.fromJson,
    );
    final snapshot = results[2] as _WorkSnapshot;
    final history = results[3] as _FinishedConvoys;
    final finished = await _finishedConvoyDetails(
      history,
      openIds: {for (final c in convoys.items) c.id},
    );
    return _RunsSnapshot([
      ...runsList.items,
      ...mapConvoys(
        [...convoys.items, ...finished],
        work: snapshot.items,
        context: snapshot.context,
        closedAt: history.closedAt,
      ),
    ], snapshot);
  }

  /// Gas City's `/convoys` lists open convoys only, so a batch vanished
  /// the moment it finished. The finished ones come from
  /// `GET /beads?status=closed&type=convoy&limit=N` (newest first) and
  /// their close times from `GET /events?type=convoy.closed&since=W`,
  /// bounded by [finishedRunsLimit] and [finishedRunsWindow]. Both reads
  /// are optional: history never blanks the open runs.
  Future<_FinishedConvoys> _finishedConvoyHistory() async {
    if (finishedRunsLimit <= 0) return const _FinishedConvoys();
    try {
      final results = await Future.wait([
        _optional(
          '/beads',
          query: {
            'status': 'closed',
            'type': 'convoy',
            'limit': finishedRunsLimit,
          },
        ),
        _optional(
          '/events',
          query: {
            'type': 'convoy.closed',
            'since': '${finishedRunsWindow.inMinutes}m',
            'limit': finishedRunsLimit,
          },
        ),
      ]);
      return _FinishedConvoys(
        closed: GcList<GcBead>.fromJson(results[0], GcBead.fromJson).items,
        closedAt: convoyClosedTimes(GcEventsPage.fromJson(results[1]).items),
      );
    } on Object {
      return const _FinishedConvoys();
    }
  }

  /// The finished convoys to list ([selectFinishedConvoys]), each with its
  /// tracked items from `GET /convoy/{id}` (cached: a closed convoy does
  /// not change). A detail that cannot be read falls back to the list
  /// bead, which still shows the run as done.
  Future<List<GcConvoy>> _finishedConvoyDetails(
    _FinishedConvoys history, {
    required Set<String> openIds,
  }) async {
    final picked = selectFinishedConvoys(
      history.closed,
      now: _clock(),
      window: finishedRunsWindow,
      limit: finishedRunsLimit,
      closedAt: history.closedAt,
      openIds: openIds,
    );
    _finishedConvoys.removeWhere(
      (id, _) => !picked.any((bead) => bead.id == id),
    );
    return Future.wait([
      for (final bead in picked)
        () async {
          final cached = _finishedConvoys[bead.id];
          if (cached != null) return cached;
          try {
            final json = await _http.getCity(
              '/convoy/${Uri.encodeComponent(bead.id)}',
            );
            final detail = GcConvoy.fromJson(json);
            if (detail.id != bead.id || !detail.bead.isClosed) {
              return GcConvoy(bead: bead);
            }
            return _finishedConvoys[bead.id] = detail;
          } on Object {
            return GcConvoy(bead: bead);
          }
        }(),
    ]);
  }

  @override
  Future<List<WorkItem>> work({String? projectId}) async {
    final results = await Future.wait<Object>([
      _workSnapshot(projectId: projectId),
      _finishedForWork(),
    ]);
    final snapshot = results[0] as _WorkSnapshot;
    final finished = results[1] as List<GcConvoy>;
    return [
      ...snapshot.items,
      ...finishedConvoyWork(
        finished,
        listed: {for (final item in snapshot.items) item.id},
        projectId: projectId,
      ),
    ];
  }

  Future<List<GcConvoy>>? _finishedWorkLoad;
  DateTime? _finishedWorkLoadedAt;

  /// The recently finished convoys with their tracked items, for [work]:
  /// `/beads` lists open work only, so a finished run's Work tab was empty.
  /// Read at most every 30 s (the details themselves are cached).
  Future<List<GcConvoy>> _finishedForWork() {
    final now = _clock();
    final at = _finishedWorkLoadedAt;
    final load = _finishedWorkLoad;
    if (load != null &&
        at != null &&
        now.difference(at) < const Duration(seconds: 30)) {
      return load;
    }
    _finishedWorkLoadedAt = now;
    return _finishedWorkLoad = () async {
      try {
        final history = await _finishedConvoyHistory();
        return await _finishedConvoyDetails(history, openIds: const {});
      } on Object {
        return const <GcConvoy>[];
      }
    }();
  }

  @override
  Future<List<WorkItem>> readyWork({String? projectId}) async {
    final snapshot = await _workSnapshot(projectId: projectId);
    return [
      for (final item in snapshot.items)
        if (snapshot.context.readyIds.contains(item.id)) item,
    ];
  }

  @override
  Future<WorkItem?> workItem(String id) async {
    final Map<String, Object?> json;
    try {
      json = await _http.getCity('/bead/${Uri.encodeComponent(id)}');
    } on OrchestrationHttpException catch (e) {
      if (e.isNotFound) return null;
      rethrow;
    }
    final bead = GcBead.fromJson(json);
    final context = await _workContext();
    return mapBead(bead, context: context);
  }

  /// `/beads` (+ `?rig=`) mapped with the cross-resource context.
  Future<_WorkSnapshot> _workSnapshot({String? projectId}) async {
    final results = await Future.wait<Object>([
      _http.getCity(
        '/beads',
        query: projectId == null ? null : {'rig': projectId},
      ),
      _workContext(rig: projectId),
    ]);
    final list = GcList<GcBead>.fromJson(
      results[0] as Map<String, Object?>,
      GcBead.fromJson,
    );
    final context = results[1] as GcWorkContext;
    final mapped = mapBeadList(list, context: context);
    _notePartial('work', mapped);
    return _WorkSnapshot(mapped.items, context, list.items);
  }

  /// Ready set, waits, pending interactions and convoys: what a bead's
  /// product state depends on beyond the bead itself. Each source that
  /// fails is treated as empty so one warming backend never blanks the
  /// list.
  Future<GcWorkContext> _workContext({String? rig}) async {
    final results = await Future.wait([
      _optional('/beads', query: {'ready': 'true', 'rig': ?rig}),
      _optional('/waits'),
      _optional('/pending'),
      _optional('/convoys'),
    ]);
    return GcWorkContext.from(
      ready: GcList<GcBead>.fromJson(results[0], GcBead.fromJson).items,
      waits: GcList<GcWait>.fromJson(
        results[1],
        GcWait.fromJson,
        itemsKey: 'waits',
      ).items,
      pending: GcList<GcPendingInteraction>.fromJson(
        results[2],
        GcPendingInteraction.fromJson,
      ).items,
      convoys: GcList<GcConvoy>.fromJson(results[3], GcConvoy.fromJson).items,
    );
  }

  @override
  Future<List<OrchestrationAgent>> agents() async {
    final results = await Future.wait([
      _http.getCity('/agents'),
      _http.getCity('/sessions'),
      _optional('/pending'),
      _optional('/waits'),
    ]);
    final agents = GcList<GcAgent>.fromJson(results[0], GcAgent.fromJson);
    final sessions = GcList<GcSession>.fromJson(results[1], GcSession.fromJson);
    final context = GcAgentContext.from(
      pending: GcList<GcPendingInteraction>.fromJson(
        results[2],
        GcPendingInteraction.fromJson,
      ).items,
      waits: GcList<GcWait>.fromJson(
        results[3],
        GcWait.fromJson,
        itemsKey: 'waits',
      ).items,
    );
    return mapAgents(agents.items, sessions.items, context: context);
  }

  @override
  Future<OrchestrationAgent?> agent(String id) async {
    for (final agent in await agents()) {
      if (agent.id == id || agent.name == id || agent.sessionId == id) {
        return agent;
      }
    }
    return null;
  }

  /// Pending interactions, gate and review beads, failed runs.
  @override
  Future<List<OrchestrationGate>> gates() async {
    final results = await Future.wait<Object>([
      _http.getCity('/pending'),
      _runs(),
    ]);
    final pending = GcList<GcPendingInteraction>.fromJson(
      results[0] as Map<String, Object?>,
      GcPendingInteraction.fromJson,
    );
    final snapshot = results[1] as _RunsSnapshot;
    return mapGates(
      pending: await _withQuestions(pending.items),
      beads: snapshot.work.beads,
      runs: snapshot.runs,
    );
  }

  /// `GET /pending` names only the kind, the request and the session; the
  /// question and its options are on `GET /session/{id}/pending`. Each entry
  /// that has no question is read there, once per session; a session the host
  /// does not answer for keeps the bare entry.
  Future<List<GcPendingInteraction>> _withQuestions(
    List<GcPendingInteraction> items,
  ) async {
    final bySession = <String, Future<Map<String, Object?>>>{};
    final out = <GcPendingInteraction>[];
    for (final item in items) {
      final session = item.sessionId;
      if (item.prompt != null || session == null || session.isEmpty) {
        out.add(item);
        continue;
      }
      final detail = await (bySession[session] ??= _optional(
        '/session/${Uri.encodeComponent(session)}/pending',
      ));
      final pending = detail['pending'];
      if (pending is! Map || pending['request_id'] != item.requestId) {
        out.add(item);
        continue;
      }
      out.add(
        GcPendingInteraction.fromJson({
          ...item.raw,
          ...pending.cast<String, Object?>(),
          'session_id': session,
        }),
      );
    }
    return out;
  }

  @override
  Future<OrchestrationUsage?> usage() async {
    final results = await Future.wait([
      _http.getCity('/usage'),
      _optional('/status'),
    ]);
    final usage = GcUsage.fromJson(results[0]);
    final statusJson = results[1];
    return mapUsage(
      usage,
      status: statusJson.isEmpty ? null : GcStatus.fromJson(statusJson),
    );
  }

  @override
  Future<List<ActivityEvent>> activity({int? afterSeq, int limit = 100}) async {
    final page = GcEventsPage.fromJson(
      await _http.getCity('/events', query: {'limit': limit.clamp(1, 500)}),
    );
    final events = [
      for (final event in page.items)
        if (afterSeq == null || (event.seq ?? 0) > afterSeq) mapActivity(event),
    ];
    // The host lists newest first; the timeline wants oldest first.
    events.sort((a, b) => (a.seq ?? 0).compareTo(b.seq ?? 0));
    return events;
  }

  /// A city read that may legitimately be missing on this host: HTTP
  /// errors become an empty map; transport errors still propagate.
  Future<Map<String, Object?>> _optional(
    String tail, {
    Map<String, Object?>? query,
  }) async {
    try {
      return await _http.getCity(tail, query: query);
    } on OrchestrationHttpException {
      return const {};
    }
  }

  void _notePartial(String scope, GcMapped<Object> mapped) {
    if (mapped.partial) {
      _partial[scope] = PartialNotice(
        scope: scope,
        reasons: mapped.partialErrors,
        at: DateTime.now(),
      );
    } else {
      _partial.remove(scope);
    }
  }

  // -------------------------------------------------------------------------
  // Events
  // -------------------------------------------------------------------------

  /// Opens `/events/stream` resumed from [resumeFrom] and maps every frame
  /// through [mapStreamFrame]. Heartbeats arrive as [StreamHeartbeat]; a
  /// resume the host did not honour arrives as [StreamHeadOnlyReplay]
  /// before the first replayed event. The stream reconnects on its own and
  /// ends only when the subscription is cancelled or the gateway closes.
  @override
  Stream<OrchestrationEvent> events({
    EventCursor resumeFrom = EventCursor.none,
  }) {
    if (_closed) return const Stream.empty();
    final client = openStream(resumeFrom: resumeFrom);
    late StreamController<OrchestrationEvent> controller;
    StreamSubscription<GcStreamFrame>? frames;
    StreamSubscription<HeadOnlyReplay>? replays;
    controller = StreamController<OrchestrationEvent>(
      onListen: () {
        replays = client.headOnlyReplay.listen((replay) {
          controller.add(
            StreamHeadOnlyReplay(
              requestedSeq: replay.requestedSeq,
              firstSeq: replay.firstSeq,
            ),
          );
        });
        frames = client.frames.listen(
          (frame) => controller.add(mapStreamFrame(frame)),
          onError: controller.addError,
          onDone: controller.close,
        );
      },
      onCancel: () async {
        await replays?.cancel();
        await frames?.cancel();
        await client.close();
      },
    );
    return controller.stream;
  }

  /// `GET /session/{id}/stream`: every `turn` frame as [AgentOutputText];
  /// a 404 (the session stopped or was recycled) as [AgentOutputEnded],
  /// after which the stream closes. Heartbeats are folded away; drops
  /// reconnect with `Last-Event-ID` like the city stream.
  @override
  Stream<AgentOutputEvent> agentOutput(String sessionId) {
    if (_closed) return const Stream.empty();
    final client = OrchestrationSseClient(
      http: _http,
      path:
          '${_http.cityPath}/session/${Uri.encodeComponent(sessionId)}/stream',
      stallTimeout: _stallTimeout,
      backoffCap: _backoffCap,
      backoffBase: _backoffBase,
      stopOnNotFound: true,
    );
    _streams.add(client);
    client.status.listen(null, onDone: () => _streams.remove(client));
    late StreamController<AgentOutputEvent> controller;
    StreamSubscription<GcStreamFrame>? frames;
    controller = StreamController<AgentOutputEvent>(
      onListen: () {
        frames = client.frames.listen(
          (frame) {
            final text = mapTurnFrame(frame);
            if (text != null) controller.add(text);
          },
          onError: (Object error) {
            if (error is OrchestrationHttpException && error.isNotFound) {
              controller.add(AgentOutputEnded(reason: error.problem.message));
              unawaited(controller.close());
            } else {
              controller.addError(error);
            }
          },
          onDone: () {
            if (!controller.isClosed) unawaited(controller.close());
          },
        );
      },
      onCancel: () async {
        await frames?.cancel();
        await client.close();
      },
    );
    return controller.stream;
  }

  /// The raw SSE client for `/events/stream`, for callers that want the
  /// connection status and cursor alongside the frames. Closed with the
  /// gateway.
  OrchestrationSseClient openStream({
    EventCursor resumeFrom = EventCursor.none,
  }) {
    final client = OrchestrationSseClient(
      http: _http,
      path: '${_http.cityPath}/events/stream',
      resumeFrom: resumeFrom,
      stallTimeout: _stallTimeout,
      backoffCap: _backoffCap,
      backoffBase: _backoffBase,
    );
    _streams.add(client);
    client.status.listen(null, onDone: () => _streams.remove(client));
    return client;
  }

  // -------------------------------------------------------------------------
  // Controls: through the front, else refused
  // -------------------------------------------------------------------------

  /// The session behind a pending interaction id, from `/pending`.
  Future<String?> _sessionForGate(String gateId) async {
    final pending = GcList<GcPendingInteraction>.fromJson(
      await _http.getCity('/pending'),
      GcPendingInteraction.fromJson,
    );
    for (final item in pending.items) {
      if (item.requestId == gateId) return item.sessionId;
    }
    return null;
  }

  Future<RunKind?> _runKindFor(String runId) async => (await run(runId))?.kind;

  @override
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  }) async {
    final control = _control;
    if (control == null || _closed) {
      return MutationReceipt.rejected(requestId, _frontRequired);
    }
    return control.respond(gateId, response, requestId: requestId);
  }

  @override
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  }) async {
    final control = _control;
    if (control == null || _closed) {
      return MutationReceipt.rejected(requestId, _frontRequired);
    }
    return control.message(agentId, text, requestId: requestId);
  }

  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) async {
    final control = _control;
    if (control == null || _closed) {
      return MutationReceipt.rejected(requestId, _frontRequired);
    }
    return control.controlAgent(agentId, action, requestId: requestId);
  }

  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) async {
    final control = _control;
    if (control == null || _closed) {
      return MutationReceipt.rejected(requestId, _frontRequired);
    }
    return control.cancelRun(runId, requestId: requestId);
  }

  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) async {
    final control = _control;
    if (control == null || _closed) {
      return MutationReceipt.rejected(requestId, _frontRequired);
    }
    return control.assign(workId, agentId: agentId, requestId: requestId);
  }

  @override
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  }) async {
    final control = _control;
    if (control == null || _closed) {
      return MutationReceipt.rejected(requestId, _frontRequired);
    }
    return control.createWork(
      title: title,
      description: description,
      projectId: projectId,
      requestId: requestId,
    );
  }

  // -------------------------------------------------------------------------
  // Projects (rigs) and scheduled jobs (orders)
  // -------------------------------------------------------------------------

  @override
  Future<MutationReceipt> controlProject(
    String projectId,
    ProjectControlAction action, {
    required String requestId,
  }) async {
    final control = _control;
    if (control == null || _closed) {
      return MutationReceipt.rejected(requestId, _frontRequired);
    }
    return control.controlProject(projectId, action, requestId: requestId);
  }

  /// `/orders`: readable without a front.
  @override
  Future<List<ScheduledJob>> scheduledJobs() async {
    final control = _control;
    if (control != null) return control.scheduledJobs();
    return mapOrders(
      readList(await _http.getCity('/orders'), 'orders', GcOrder.fromJson),
    );
  }

  @override
  Future<MutationReceipt> controlScheduledJob(
    String jobId, {
    required bool enabled,
    required String requestId,
  }) async {
    final control = _control;
    if (control == null || _closed) {
      return MutationReceipt.rejected(requestId, _frontRequired);
    }
    return control.controlScheduledJob(
      jobId,
      enabled: enabled,
      requestId: requestId,
    );
  }

  // -------------------------------------------------------------------------
  // Merge roles (TEAM-205): through the front, else absent
  // -------------------------------------------------------------------------

  /// The front's own routes: never on loopback, where there is no front.
  GasCityControl? get _frontControl => front ? _control : null;

  @override
  Future<MergeReadiness?> mergeReadiness(String runId) async {
    final control = _frontControl;
    if (control == null || _closed) return null;
    return control.mergeReadiness(runId);
  }

  @override
  Future<MutationReceipt> approveMerge(
    String mergeRequestId, {
    required String requestId,
  }) async {
    final control = _frontControl;
    if (control == null || _closed) {
      return MutationReceipt.rejected(requestId, _frontRequired);
    }
    return control.approveMerge(mergeRequestId, requestId: requestId);
  }

  @override
  Future<MutationReceipt> merge(
    String runId, {
    required String requestId,
  }) async {
    final control = _frontControl;
    if (control == null || _closed) {
      return MutationReceipt.rejected(requestId, _frontRequired);
    }
    return control.merge(runId, requestId: requestId);
  }

  // -------------------------------------------------------------------------
  // Policy (TEAM-207): through the front, else absent
  // -------------------------------------------------------------------------

  @override
  Future<OrchestrationPolicy?> policy({String? projectId}) async {
    final control = _frontControl;
    if (control == null || _closed) return null;
    return control.policy(projectId: projectId);
  }
}

class _RunsSnapshot {
  const _RunsSnapshot(this.runs, this.work);

  final List<OrchestrationRun> runs;
  final _WorkSnapshot work;
}

class _FinishedConvoys {
  const _FinishedConvoys({this.closed = const [], this.closedAt = const {}});

  /// Closed convoy beads, newest first.
  final List<GcBead> closed;

  /// Convoy id → close time.
  final Map<String, DateTime> closedAt;
}

class _WorkSnapshot {
  const _WorkSnapshot(this.items, this.context, this.beads);

  final List<WorkItem> items;
  final GcWorkContext context;

  /// Raw beads, including the host's internal ones.
  final List<GcBead> beads;
}
