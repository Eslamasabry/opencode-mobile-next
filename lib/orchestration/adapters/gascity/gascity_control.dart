/// The Gas City write adapter: [OrchestrationControlGateway] over the host
/// front (tool/host/cp_front), which forwards each mutation to the loopback
/// supervisor and answers a receipt keyed by the client's `Idempotency-Key`.
///
/// Every call sends `Idempotency-Key: <requestId>` (the key the controller
/// persisted before calling) and `X-GC-Request`, and maps the front's
/// answer onto a [MutationReceipt]:
///
/// | Front answer | Receipt |
/// |---|---|
/// | 2xx receipt `status: accepted` | accepted; `correlationId` is the 202 body's `request_id` when the supervisor gave one |
/// | non-2xx receipt `status: rejected` | rejected; message from the supervisor's problem detail |
/// | 502 `urn:opencode-mobile:front:upstream-unavailable` | pending, `retryable` (nothing stored on the host) |
/// | 409 `urn:opencode-mobile:front:idempotency-mismatch` | rejected |
/// | 403 `identity-not-allowed` / `peer-not-on-tailnet`, any other problem | rejected |
/// | `Idempotent-Replayed: true` | the stored receipt, `replayed` set |
/// | transport failure | pending, not retryable (the front may have stored it) |
///
/// Nothing here retries: a pending receipt is the controller's to show as
/// unconfirmed and a person's to retry (04-plugin-architecture §6).
///
/// Route choices per the pinned supervisor spec
/// (`contracts/gascity-supervisor-openapi-v0-3648ca2d499a.json`):
///
/// | Verb | Route |
/// |---|---|
/// | respond | `POST /session/{session}/respond` `{action, request_id, text?}`; the session comes from the pending interaction the gate id names |
/// | message, nudge | `POST /session/{session}/messages` `{message}` (202 with a correlation id) |
/// | pause / resume | `POST /agent/{dir}/{base}/suspend` / `resume` (keeps the daemon from waking the agent again); a pool instance without an agent entry uses `session/{id}/suspend` / `wake` |
/// | stop | `POST /session/{session}/stop` |
/// | restart | `stop`, then `wake` under the key `<requestId>:wake` |
/// | cancelRun | `POST /runs/{id}/cancel` for a formula run, `POST /convoy/{id}/close` for a batch |
/// | assign | `POST /sling` `{bead, target, reassign: true}` |
/// | createWork | `POST /beads` `{title, description?, rig?, type: 'task', priority: 2, labels: ['opencode-mobile']}` (201 with the bead; TEAM-306) |
///
/// Merge roles (TEAM-205) are the front's own routes, not the supervisor's
/// (Gas City v0 has no merge-request API; the front implements them over
/// git on the host, boundary-checked — tool/host/cp_front/README.md):
///
/// | Verb | Route |
/// |---|---|
/// | mergeReadiness | `GET /front/merge-readiness/{run}` → [MergeReadiness] |
/// | approveMerge | `POST /front/mr/{bead}/approve` (the front PATCHes the bead's `review.approved_by` metadata and clears `needs-review`) |
/// | merge | `POST /front/merge/{run}` (fast-forward or `--no-ff` into the rig's default branch, pushed to origin; never force, never deletes) |
/// | policy | `GET /front/policy[?rig=]` → [OrchestrationPolicy] (TEAM-207; read-only supervision level and boundary texts) |
library;

import '../../../domain/orchestration_gateway.dart';
import '../../client/http.dart';
import 'dto/dto.dart';
import 'gascity_mappers.dart' show mapOrders;

/// The pending interaction's session for a gate id, null when unknown.
typedef GateSessionResolver = Future<String?> Function(String gateId);

/// The agent the product model calls [agentId], null when unknown.
typedef AgentResolver = Future<OrchestrationAgent?> Function(String agentId);

/// The kind of run behind a run id, null when unknown.
typedef RunKindResolver = Future<RunKind?> Function(String runId);

/// URN prefix of the front's own problem types.
const frontProblemPrefix = 'urn:opencode-mobile:front:';

/// The message a nudge sends.
const nudgeMessage = 'please continue';

/// Write side of [GasCityGateway] when a front is in use.
class GasCityControl
    implements
        OrchestrationControlGateway,
        OrchestrationMergeGateway,
        OrchestrationPolicyGateway,
        OrchestrationRigGateway {
  GasCityControl({
    required OrchestrationHttpClient http,
    required this.sessionForGate,
    required this.agentFor,
    required this.runKindFor,
    this.idempotency = true,
  }) : _http = http;

  final OrchestrationHttpClient _http;

  /// Whether mutations carry `Idempotency-Key` (a front stores receipts by
  /// it). False on the phone's loopback supervisor, which has no receipt
  /// store: the write is confirmed by the event stream instead.
  final bool idempotency;
  final GateSessionResolver sessionForGate;
  final AgentResolver agentFor;
  final RunKindResolver runKindFor;

  String _session(String id) =>
      '${_http.cityPath}/session/${Uri.encodeComponent(id)}';

  @override
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  }) async {
    final session = await _resolve(() => sessionForGate(gateId));
    if (session == null) {
      return MutationReceipt.rejected(
        requestId,
        'no pending interaction $gateId on the host',
      );
    }
    final body = <String, Object?>{
      'action': respondAction(response),
      'request_id': gateId,
      if (response.text != null) 'text': response.text,
    };
    return _post('${_session(session)}/respond', requestId, body: body);
  }

  @override
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  }) async {
    if (text.trim().isEmpty) {
      return MutationReceipt.rejected(requestId, 'message is empty');
    }
    final session = await _sessionOf(agentId);
    return _post(
      '${_session(session)}/messages',
      requestId,
      body: {'message': text},
    );
  }

  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) async {
    switch (action) {
      case AgentControlAction.nudge:
        return message(agentId, nudgeMessage, requestId: requestId);
      case AgentControlAction.pause:
        return _agentOrSession(
          agentId,
          requestId,
          agentAction: 'suspend',
          sessionAction: 'suspend',
        );
      case AgentControlAction.resume:
      case AgentControlAction.start:
        return _agentOrSession(
          agentId,
          requestId,
          agentAction: 'resume',
          sessionAction: 'wake',
        );
      case AgentControlAction.stop:
        final session = await _sessionOf(agentId);
        return _post('${_session(session)}/stop', requestId);
      case AgentControlAction.kill:
        final session = await _sessionOf(agentId);
        return _post('${_session(session)}/kill', requestId);
      case AgentControlAction.restart:
        final session = await _sessionOf(agentId);
        final stopped = await _post('${_session(session)}/stop', requestId);
        if (!stopped.isAccepted) return stopped;
        final woke = await _post(
          '${_session(session)}/wake',
          requestId,
          idempotencyKey: '$requestId:wake',
        );
        return MutationReceipt(
          id: requestId,
          status: woke.status,
          message: woke.message,
          retryable: woke.retryable,
          replayed: woke.replayed,
          hostRequestId: woke.hostRequestId,
          correlationId: woke.correlationId,
          upstreamStatus: woke.upstreamStatus,
          raw: {'stop': stopped.raw, 'wake': woke.raw},
        );
    }
  }

  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) async {
    final kind = await _resolve(() => runKindFor(runId));
    final id = Uri.encodeComponent(runId);
    final path = kind == RunKind.batch
        ? '${_http.cityPath}/convoy/$id/close'
        : '${_http.cityPath}/runs/$id/cancel';
    return _post(path, requestId);
  }

  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) => _post(
    '${_http.cityPath}/sling',
    requestId,
    body: {'bead': workId, 'target': agentId, 'reassign': true},
  );

  /// `POST /beads`: the body the write proof used live
  /// (tool/qa/gascity_write_proof.dart). The 201 answer is the bead itself,
  /// so the receipt's `raw['id']` is the new work item's id.
  @override
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  }) {
    if (title.trim().isEmpty) {
      return Future.value(
        MutationReceipt.rejected(requestId, 'title is empty'),
      );
    }
    return _post(
      '${_http.cityPath}/beads',
      requestId,
      body: {
        'title': title.trim(),
        if (description != null && description.trim().isNotEmpty)
          'description': description.trim(),
        if (projectId != null && projectId.isNotEmpty) 'rig': projectId,
        'type': 'task',
        'priority': 2,
        'labels': const ['opencode-mobile'],
      },
    );
  }

  // -------------------------------------------------------------------------
  // Projects (rigs) and scheduled jobs (orders)
  // -------------------------------------------------------------------------

  /// `POST /rig/{name}/suspend` or `resume`; `DELETE /rig/{name}` removes
  /// the rig from the city.
  @override
  Future<MutationReceipt> controlProject(
    String projectId,
    ProjectControlAction action, {
    required String requestId,
  }) {
    final rig = '${_http.cityPath}/rig/${Uri.encodeComponent(projectId)}';
    return switch (action) {
      ProjectControlAction.suspend => _post('$rig/suspend', requestId),
      ProjectControlAction.resume => _post('$rig/resume', requestId),
      ProjectControlAction.remove => _delete(rig, requestId),
    };
  }

  /// `GET /orders`.
  @override
  Future<List<ScheduledJob>> scheduledJobs() async {
    final json = await _http.getCity('/orders');
    return mapOrders(readList(json, 'orders', GcOrder.fromJson));
  }

  /// `POST /order/{name}/enable` or `disable`.
  @override
  Future<MutationReceipt> controlScheduledJob(
    String jobId, {
    required bool enabled,
    required String requestId,
  }) => _post(
    '${_http.cityPath}/order/${Uri.encodeComponent(jobId)}/${enabled ? 'enable' : 'disable'}',
    requestId,
  );

  // -------------------------------------------------------------------------
  // Merge roles (TEAM-205): the front's own routes
  // -------------------------------------------------------------------------

  String get _frontPath => '${_http.cityPath}/front';

  /// `GET /front/merge-readiness/{run}`. A 404 (unknown run) or 422 (the
  /// rig has no origin or default branch, or the front has no merge
  /// roles) answers null; other failures propagate.
  @override
  Future<MergeReadiness?> mergeReadiness(String runId) async {
    final Map<String, Object?> json;
    try {
      json = await _http.getJson(
        '$_frontPath/merge-readiness/${Uri.encodeComponent(runId)}',
      );
    } on OrchestrationHttpException catch (e) {
      if (e.isNotFound || e.statusCode == 422) return null;
      rethrow;
    }
    return MergeReadiness.fromJson(json, runId: runId);
  }

  /// `POST /front/mr/{bead}/approve`.
  @override
  Future<MutationReceipt> approveMerge(
    String mergeRequestId, {
    required String requestId,
  }) => _post(
    '$_frontPath/mr/${Uri.encodeComponent(mergeRequestId)}/approve',
    requestId,
  );

  /// `POST /front/merge/{run}`.
  @override
  Future<MutationReceipt> merge(String runId, {required String requestId}) =>
      _post('$_frontPath/merge/${Uri.encodeComponent(runId)}', requestId);

  // -------------------------------------------------------------------------
  // Policy (TEAM-207): the front's read-only policy document
  // -------------------------------------------------------------------------

  /// `GET /front/policy[?rig=]`. A 404 (a front without the route) or 422
  /// (unusable rig name) answers null; other failures propagate.
  @override
  Future<OrchestrationPolicy?> policy({String? projectId}) async {
    final Map<String, Object?> json;
    try {
      json = await _http.getJson(
        '$_frontPath/policy',
        query: projectId == null || projectId.isEmpty
            ? null
            : {'rig': projectId},
      );
    } on OrchestrationHttpException catch (e) {
      if (e.isNotFound || e.statusCode == 422) return null;
      rethrow;
    }
    return OrchestrationPolicy.fromJson(json);
  }

  // -------------------------------------------------------------------------
  // Plumbing
  // -------------------------------------------------------------------------

  /// Agent-level action when the agent has an entry (`dir/base` or `base`
  /// per the spec's two routes), else the session-level equivalent.
  Future<MutationReceipt> _agentOrSession(
    String agentId,
    String requestId, {
    required String agentAction,
    required String sessionAction,
  }) async {
    final agent = await _resolve(() => agentFor(agentId));
    final name = agent?.name ?? agentId;
    // A pool instance mapped from its session alone carries the session id
    // as its id; only an agent with its own entry has the agent routes.
    final hasAgentEntry = agent != null && agent.id != agent.sessionId;
    if (hasAgentEntry) {
      final segments = name.split('/').map(Uri.encodeComponent).join('/');
      return _post('${_http.cityPath}/agent/$segments/$agentAction', requestId);
    }
    final session = agent?.sessionId ?? agentId;
    return _post('${_session(session)}/$sessionAction', requestId);
  }

  /// The session id behind an agent id: the agent's current session, else
  /// the id itself (the supervisor also accepts an alias or session name).
  Future<String> _sessionOf(String agentId) async {
    final agent = await _resolve(() => agentFor(agentId));
    return agent?.sessionId ?? agentId;
  }

  /// A resolver that fails answers null: the write goes ahead with the id
  /// as given and the host decides.
  Future<T?> _resolve<T>(Future<T?> Function() lookup) async {
    try {
      return await lookup();
    } on Object {
      return null;
    }
  }

  Future<MutationReceipt> _delete(String path, String requestId) =>
      _post(path, requestId, method: 'DELETE');

  Future<MutationReceipt> _post(
    String path,
    String requestId, {
    Map<String, Object?>? body,
    String? idempotencyKey,
    String method = 'POST',
  }) async {
    final OrchestrationHttpResponse response;
    try {
      response = await _http.postForReceipt(
        path,
        method: method,
        requestId: requestId,
        idempotencyKey: idempotency ? (idempotencyKey ?? requestId) : null,
        body: body ?? const {},
      );
    } on OrchestrationTransportException catch (e) {
      return MutationReceipt(
        id: requestId,
        status: MutationReceiptStatus.pending,
        message: e.message,
        raw: {'error': e.toString()},
      );
    }
    return receiptFromFront(requestId, response);
  }
}

/// The `action` a [GateResponse] sends: the chosen option for a choice,
/// `allow` / `deny` for a confirmation, `text` for a free-text answer
/// (the answer itself travels in `text`).
String respondAction(GateResponse response) {
  final choice = response.choice;
  if (choice != null) return choice;
  final confirmed = response.confirmed;
  if (confirmed != null) return confirmed ? 'allow' : 'deny';
  return 'text';
}

/// The front's answer to one mutation as a [MutationReceipt] (table in the
/// library doc). A bare supervisor answer (no front) maps the same way:
/// 2xx accepted, otherwise rejected with the problem's message.
MutationReceipt receiptFromFront(
  String requestId,
  OrchestrationHttpResponse response,
) {
  final body = response.body;
  final isReceipt =
      body.containsKey('upstream_status') &&
      (readText(body, 'status') == 'accepted' ||
          readText(body, 'status') == 'rejected');
  if (isReceipt) {
    final inner = readMapField(body, 'body');
    final accepted = readText(body, 'status') == 'accepted';
    final upstream = readInt(body, 'upstream_status');
    return MutationReceipt(
      id: requestId,
      status: accepted
          ? MutationReceiptStatus.accepted
          : MutationReceiptStatus.rejected,
      message: accepted ? null : _problemMessage(inner, upstream),
      raw: body,
      replayed: response.replayed,
      hostRequestId: readText(body, 'request_id') ?? response.requestId,
      correlationId: accepted ? readText(inner, 'request_id') : null,
      upstreamStatus: upstream,
    );
  }
  if (response.isSuccess) {
    return MutationReceipt(
      id: requestId,
      status: MutationReceiptStatus.accepted,
      raw: body,
      replayed: response.replayed,
      hostRequestId: response.requestId,
      correlationId: readText(body, 'request_id'),
      upstreamStatus: response.statusCode,
    );
  }
  final problem = GcProblem.looksLikeProblem(body)
      ? GcProblem.fromJson(body)
      : GcProblem(
          status: response.statusCode,
          title: 'HTTP ${response.statusCode}',
        );
  final fromFront = problem.type?.startsWith(frontProblemPrefix) ?? false;
  final retryable = fromFront && problem.slug == 'upstream-unavailable';
  return MutationReceipt(
    id: requestId,
    status: retryable
        ? MutationReceiptStatus.pending
        : MutationReceiptStatus.rejected,
    message: problem.message,
    raw: body,
    retryable: retryable,
    hostRequestId: response.requestId,
    upstreamStatus: fromFront ? null : response.statusCode,
  );
}

String _problemMessage(Map<String, Object?> inner, int? status) {
  if (GcProblem.looksLikeProblem(inner)) {
    return GcProblem.fromJson(inner).message;
  }
  return readText(inner, 'detail') ??
      readText(inner, 'title') ??
      readText(inner, 'error') ??
      'HTTP ${status ?? '?'}';
}
