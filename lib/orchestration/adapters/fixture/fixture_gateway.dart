/// In-process [OrchestrationGateway] over the recordings in
/// `tool/qa/gascity_fixture`, for widget tests. No network: it reads the
/// JSON recordings and the `normal-run.ndjson` event log from disk and
/// replays events on demand with [emit]. Capabilities are
/// [OrchestrationCapabilities.fixture] (everything on) so every widget can
/// be exercised; controls are accepted and recorded, never performed.
library;

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import '../../../domain/orchestration_gateway.dart';
import '../gascity/dto/dto.dart';
import '../gascity/gascity_mappers.dart';

/// One control call the double received.
class FixtureControlCall {
  const FixtureControlCall(this.verb, this.target, this.requestId, {this.arg});

  /// `respond`, `message`, `controlAgent`, `cancelRun`, `assign` or
  /// `createWork`.
  final String verb;
  final String target;
  final String requestId;
  final Object? arg;

  @override
  String toString() => 'FixtureControlCall($verb $target ${arg ?? ''})';
}

/// Test double over the recorded Gas City fixture. Reads answer from the
/// recordings synchronously-loaded at construction; [emit] pushes the next
/// `n` frames of the recorded run into every open [events] subscription
/// and advances what [activity] returns.
class FixtureOrchestrationGateway
    implements
        OrchestrationGateway,
        OrchestrationAgentOutputGateway,
        OrchestrationRigGateway {
  /// Loads `recordings/*.json` and `events/*.ndjson` from [fixturePath]
  /// (the `tool/qa/gascity_fixture` directory).
  FixtureOrchestrationGateway({
    required String fixturePath,
    this.hostMode = OrchestrationHostMode.computer,
    this.url = 'fixture://gascity',
  }) : _root = Directory(fixturePath) {
    if (!_root.existsSync()) {
      throw ArgumentError.value(fixturePath, 'fixturePath', 'not a directory');
    }
    _log = [
      for (final line in _readLines('events/normal-run.ndjson'))
        GcStreamFrame.fromJson(readMap(jsonDecode(line))),
    ];
    // The snapshot overlaps the start of the run; like the Python fixture,
    // only events before the run count as history.
    final firstSeq = _log.isEmpty ? 0 : (_log.first.seq ?? 0);
    _history = [
      for (final line in _readLines('events/events-snapshot.ndjson'))
        if (GcEvent.fromJson(readMap(jsonDecode(line))) case final event
            when (event.seq ?? 0) < firstSeq)
          event,
    ];
    final health = GcHealth.fromJson(_recording('health'));
    _status = GcStatus.fromJson(_recording('status'));
    _host = mapHostIdentity(
      url: url,
      hostMode: hostMode,
      health: health,
      status: _status,
      provider: 'fixture',
    );
  }

  final Directory _root;
  final OrchestrationHostMode hostMode;
  final String url;
  late final List<GcStreamFrame> _log;
  late final List<GcEvent> _history;
  late final GcStatus _status;
  late final OrchestrationHostIdentity _host;
  final _events = StreamController<OrchestrationEvent>.broadcast();
  final _controls = <FixtureControlCall>[];
  final _cache = <String, Map<String, Object?>>{};
  final _outputs = <String, StreamController<AgentOutputEvent>>{};
  final _outputHeads = <String, int>{};
  final _suspendedProjects = <String>{};
  final _resumedProjects = <String>{};
  final _removedProjects = <String>{};
  int _head = 0;
  bool _closed = false;

  /// When set, every control is refused with this message, as a host that
  /// answers 409 would.
  String? refuseControlsWith;

  /// The scheduled jobs [scheduledJobs] answers; tests replace them.
  List<ScheduledJob> jobs = const [];

  /// Sessions with a recorded transcript, as the Python fixture maps them.
  static const _transcripts = {
    'bl-5qc': 'events/session-polecat.ndjson',
    'bl-48k': 'events/session-polecat.ndjson',
    'bl-wisp-qqpj': 'events/session-refinery.ndjson',
  };

  /// How many recorded `turn` frames [agentOutput] replays on listen; null
  /// replays the whole transcript. Tests lower it and feed the rest with
  /// [emitOutput].
  int? outputReplay;

  /// Frames replayed so far by [emit].
  int get head => _head;

  /// Frames the recorded log holds.
  int get logLength => _log.length;

  /// Every control call received, oldest first.
  List<FixtureControlCall> get controlCalls => List.unmodifiable(_controls);

  @override
  OrchestrationCapabilities get capabilities =>
      OrchestrationCapabilities.fixture;

  @override
  OrchestrationHostIdentity? get host => _host;

  @override
  bool get isClosed => _closed;

  @override
  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    await _events.close();
    for (final output in _outputs.values) {
      await output.close();
    }
    _outputs.clear();
  }

  // -------------------------------------------------------------------------
  // Replay
  // -------------------------------------------------------------------------

  /// Replays the next [n] frames of the recorded run to every listener and
  /// returns the events emitted. Frames past the end are ignored.
  List<OrchestrationEvent> emit(int n) {
    final out = <OrchestrationEvent>[];
    while (n-- > 0 && _head < _log.length) {
      final event = mapStreamFrame(_log[_head++]);
      out.add(event);
      if (!_events.isClosed) _events.add(event);
    }
    return out;
  }

  /// Pushes one heartbeat, as the server does while idle.
  StreamHeartbeat emitHeartbeat() {
    final beat = StreamHeartbeat(timestamp: DateTime.now());
    if (!_events.isClosed) _events.add(beat);
    return beat;
  }

  /// Pushes any event, for scenarios the recording does not contain.
  void emitEvent(OrchestrationEvent event) {
    if (!_events.isClosed) _events.add(event);
  }

  /// Rewinds the replay head.
  void rewind() => _head = 0;

  // -------------------------------------------------------------------------
  // Agent output
  // -------------------------------------------------------------------------

  /// The recorded `turn` texts of a session, in order; empty when the
  /// fixture has no transcript for it.
  List<AgentOutputText> recordedOutput(String sessionId) {
    final file = _transcripts[sessionId];
    if (file == null) return const [];
    return [
      for (final line in _readLines(file))
        ?mapTurnFrame(GcStreamFrame.fromJson(readMap(jsonDecode(line)))),
    ];
  }

  /// Replays the recorded transcript (all of it, or the first
  /// [outputReplay] turns) on listen, then stays open for [emitOutput],
  /// [emitOutputText] and [endOutput]. Sessions without a recording end at
  /// once with [AgentOutputEnded], as the server answers 404.
  @override
  Stream<AgentOutputEvent> agentOutput(String sessionId) {
    if (_closed) return const Stream.empty();
    final recorded = recordedOutput(sessionId);
    if (recorded.isEmpty) {
      return Stream.value(
        AgentOutputEnded(reason: 'session $sessionId not found'),
      );
    }
    final live = _outputs.putIfAbsent(
      sessionId,
      StreamController<AgentOutputEvent>.broadcast,
    );
    late StreamController<AgentOutputEvent> controller;
    StreamSubscription<AgentOutputEvent>? forward;
    controller = StreamController<AgentOutputEvent>(
      onListen: () {
        final head = outputReplay == null
            ? recorded.length
            : outputReplay!.clamp(0, recorded.length);
        _outputHeads[sessionId] = head;
        recorded.take(head).forEach(controller.add);
        forward = live.stream.listen((event) {
          controller.add(event);
          if (event is AgentOutputEnded) unawaited(controller.close());
        }, onDone: controller.close);
      },
      onCancel: () => forward?.cancel(),
    );
    return controller.stream;
  }

  /// Pushes the next [n] recorded turns of [sessionId] to its listeners.
  void emitOutput(String sessionId, int n) {
    final recorded = recordedOutput(sessionId);
    var head = _outputHeads[sessionId] ?? 0;
    final live = _outputs[sessionId];
    while (n-- > 0 && head < recorded.length) {
      live?.add(recorded[head++]);
    }
    _outputHeads[sessionId] = head;
  }

  /// Pushes [text] as one capture, for scenarios the recording lacks.
  void emitOutputText(String sessionId, String text) {
    _outputs[sessionId]?.add(AgentOutputText(text));
  }

  /// Ends the session's output the way a 404 does.
  void endOutput(String sessionId, {String? reason}) {
    _outputs[sessionId]?.add(AgentOutputEnded(reason: reason));
  }

  @override
  Stream<OrchestrationEvent> events({
    EventCursor resumeFrom = EventCursor.none,
  }) {
    if (_closed) return const Stream.empty();
    final after = resumeFrom.seq;
    if (after == null) return _events.stream;
    // Resume: replay what the listener missed, then go live.
    final missed = [
      for (final frame in _log.take(_head))
        if ((frame.seq ?? 0) > after) mapStreamFrame(frame),
    ];
    late StreamController<OrchestrationEvent> controller;
    StreamSubscription<OrchestrationEvent>? live;
    controller = StreamController<OrchestrationEvent>(
      onListen: () {
        missed.forEach(controller.add);
        live = _events.stream.listen(
          controller.add,
          onError: controller.addError,
          onDone: controller.close,
        );
      },
      onCancel: () => live?.cancel(),
    );
    return controller.stream;
  }

  // -------------------------------------------------------------------------
  // Reads
  // -------------------------------------------------------------------------

  @override
  Future<List<OrchestrationProject>> projects() async => [
    for (final project in mapRigs(_status))
      if (!_removedProjects.contains(project.id))
        OrchestrationProject(
          id: project.id,
          name: project.name,
          directory: project.directory,
          rig: project.rig,
          suspended: _suspendedProjects.contains(project.id)
              ? true
              : _resumedProjects.contains(project.id)
              ? false
              : project.suspended,
          raw: project.raw,
        ),
  ];

  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async {
    final runs = mapRunsList(GcRunsList.fromJson(_recording('runs')));
    final convoys = GcList<GcConvoy>.fromJson(
      _recording('convoys'),
      GcConvoy.fromJson,
    );
    final work = await this.work();
    final all = [
      ...runs.items,
      ...mapConvoys(convoys.items, work: work, context: _workContext()),
    ];
    if (projectId == null) return all;
    return [
      for (final run in all)
        if (run.projectId == null || run.projectId == projectId) run,
    ];
  }

  @override
  Future<OrchestrationRun?> run(String id) async {
    for (final run in await runs()) {
      if (run.id == id) return run;
    }
    return null;
  }

  @override
  Future<List<WorkItem>> work({String? projectId}) async {
    final list = GcList<GcBead>.fromJson(_recording('beads'), GcBead.fromJson);
    final current = _currentRunBead();
    final beads = [?current, ...list.items];
    final items = mapBeads(beads, context: _workContext());
    if (projectId == null) return items;
    return [
      for (final item in items)
        if (item.projectId == projectId) item,
    ];
  }

  @override
  Future<List<WorkItem>> readyWork({String? projectId}) async {
    final ready = _workContext().readyIds;
    return [
      for (final item in await work(projectId: projectId))
        if (ready.contains(item.id)) item,
    ];
  }

  @override
  Future<WorkItem?> workItem(String id) async {
    for (final item in await work()) {
      if (item.id == id) return item;
    }
    return null;
  }

  @override
  Future<List<OrchestrationAgent>> agents() async {
    final agents = GcList<GcAgent>.fromJson(
      _recording('agents'),
      GcAgent.fromJson,
    );
    final sessions = GcList<GcSession>.fromJson(
      _recording('sessions'),
      GcSession.fromJson,
    );
    return mapAgents(
      agents.items,
      sessions.items,
      context: GcAgentContext.from(pending: _pending(), waits: _waits()),
    );
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

  @override
  Future<List<OrchestrationGate>> gates() async {
    final list = GcList<GcBead>.fromJson(_recording('beads'), GcBead.fromJson);
    final current = _currentRunBead();
    return mapGates(
      pending: _pending(),
      beads: [?current, ...list.items],
      runs: await runs(),
    );
  }

  @override
  Future<OrchestrationUsage?> usage() async =>
      mapUsage(GcUsage.fromJson(_recording('usage')), status: _status);

  @override
  Future<List<ActivityEvent>> activity({int? afterSeq, int limit = 100}) async {
    final events = [
      ..._history,
      for (final frame in _log.take(_head))
        if (frame.isCityEvent) frame.toEvent(),
    ];
    final filtered = [
      for (final event in events)
        if (afterSeq == null || (event.seq ?? 0) > afterSeq) event,
    ];
    final tail = filtered.length > limit
        ? filtered.sublist(filtered.length - limit)
        : filtered;
    return [for (final event in tail) mapActivity(event)];
  }

  // -------------------------------------------------------------------------
  // Controls: accepted and recorded
  // -------------------------------------------------------------------------

  MutationReceipt _accept(
    FixtureControlCall call, {
    Map<String, Object?> raw = const {},
    int? upstreamStatus,
  }) {
    _controls.add(call);
    final refusal = refuseControlsWith;
    if (refusal != null) {
      return MutationReceipt.rejected(call.requestId, refusal);
    }
    return MutationReceipt(
      id: call.requestId,
      status: MutationReceiptStatus.accepted,
      raw: raw,
      upstreamStatus: upstreamStatus,
    );
  }

  @override
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  }) async =>
      _accept(FixtureControlCall('respond', gateId, requestId, arg: response));

  @override
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  }) async =>
      _accept(FixtureControlCall('message', agentId, requestId, arg: text));

  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) async => _accept(
    FixtureControlCall('controlAgent', agentId, requestId, arg: action),
  );

  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) async => _accept(FixtureControlCall('cancelRun', runId, requestId));

  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) async =>
      _accept(FixtureControlCall('assign', workId, requestId, arg: agentId));

  /// Answers like the Python fixture's `POST /beads`: the new bead, whose
  /// id the caller reads back as [MutationReceipt.createdId].
  @override
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  }) async => _accept(
    FixtureControlCall('createWork', title, requestId, arg: projectId),
    raw: {'id': 'fx-new-1', 'status': 'open', 'title': title},
  );

  @override
  Future<MutationReceipt> controlProject(
    String projectId,
    ProjectControlAction action, {
    required String requestId,
  }) async {
    switch (action) {
      case ProjectControlAction.suspend:
        _suspendedProjects.add(projectId);
        _resumedProjects.remove(projectId);
      case ProjectControlAction.resume:
        _resumedProjects.add(projectId);
        _suspendedProjects.remove(projectId);
      case ProjectControlAction.remove:
        _removedProjects.add(projectId);
    }
    return _accept(
      FixtureControlCall('controlProject', projectId, requestId, arg: action),
      upstreamStatus: 200,
    );
  }

  @override
  Future<List<ScheduledJob>> scheduledJobs() async => jobs;

  @override
  Future<MutationReceipt> controlScheduledJob(
    String jobId, {
    required bool enabled,
    required String requestId,
  }) async {
    jobs = [
      for (final job in jobs)
        if (job.id == jobId)
          ScheduledJob(
            id: job.id,
            name: job.name,
            enabled: enabled,
            trigger: job.trigger,
            projectId: job.projectId,
            schedule: job.schedule,
            interval: job.interval,
            onEvent: job.onEvent,
            description: job.description,
            rawTrigger: job.rawTrigger,
            raw: job.raw,
          )
        else
          job,
    ];
    return _accept(
      FixtureControlCall('controlScheduledJob', jobId, requestId, arg: enabled),
      upstreamStatus: 200,
    );
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  Map<String, Object?> _recording(String name) => _cache.putIfAbsent(
    name,
    () => readMap(
      jsonDecode(
        File('${_root.path}/recordings/$name.json').readAsStringSync(),
      ),
    ),
  );

  List<String> _readLines(String relative) => File(
    '${_root.path}/$relative',
  ).readAsLinesSync().where((line) => line.trim().isNotEmpty).toList();

  List<GcPendingInteraction> _pending() =>
      GcList<GcPendingInteraction>.fromJson(
        _recording('pending'),
        GcPendingInteraction.fromJson,
      ).items;

  List<GcWait> _waits() => GcList<GcWait>.fromJson(
    _recording('waits'),
    GcWait.fromJson,
    itemsKey: 'waits',
  ).items;

  GcWorkContext _workContext() => GcWorkContext.from(
    ready: GcList<GcBead>.fromJson(
      _recording('beads_ready'),
      GcBead.fromJson,
    ).items,
    waits: _waits(),
    pending: _pending(),
    convoys: GcList<GcConvoy>.fromJson(
      _recording('convoys'),
      GcConvoy.fromJson,
    ).items,
  );

  /// The run's bead (`oc-loy`) as of the replay head: the last `bead.*`
  /// payload emitted so far, the recorded end state once the log is
  /// exhausted, or the first version before any replay.
  GcBead? _currentRunBead() {
    if (_head >= _log.length) {
      return GcBead.fromJson(_recording('bead_handed_to_refinery'));
    }
    Map<String, Object?>? first;
    Map<String, Object?>? latest;
    for (var i = 0; i < _log.length; i++) {
      final frame = _log[i];
      if (!frame.isCityEvent) continue;
      final bead = frame.toEvent().payloadBead;
      if (bead == null || readText(bead, 'id') != 'oc-loy') continue;
      first ??= bead;
      if (i < _head) latest = bead;
    }
    final chosen = latest ?? first;
    return chosen == null ? null : GcBead.fromJson(chosen);
  }
}
