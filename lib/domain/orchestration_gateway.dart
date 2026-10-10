export 'team_project_gateway.dart';
import '../orchestration/events/cursor.dart';
import '../orchestration/events/orchestration_event.dart';
import '../orchestration/models/activity_event.dart';
import '../orchestration/models/agent.dart';
import '../orchestration/models/agent_output.dart';
import '../orchestration/models/gate.dart';
import '../orchestration/models/merge.dart';
import '../orchestration/models/policy.dart';
import '../orchestration/models/project.dart';
import '../orchestration/models/run.dart';
import '../orchestration/models/scheduled_job.dart';
import '../orchestration/models/usage.dart';
import '../orchestration/models/work.dart';

export '../orchestration/events/cursor.dart';
export '../orchestration/events/orchestration_event.dart';
export '../orchestration/models/activity_event.dart';
export '../orchestration/models/agent.dart';
export '../orchestration/models/agent_output.dart';
export '../orchestration/models/dispatch_cycle.dart';
export '../orchestration/models/gate.dart';
export '../orchestration/models/merge.dart';
export '../orchestration/models/policy.dart';
export '../orchestration/models/project.dart';
export '../orchestration/models/run.dart';
export '../orchestration/models/scheduled_job.dart';
export '../orchestration/models/usage.dart';
export '../orchestration/models/work.dart';

/// Where the orchestration host runs relative to the OpenCode server.
enum OrchestrationHostMode { computer, phone }

/// Identity of the connected orchestration host, shown on the Workspace
/// card and in Settings. Never carries a secret.
class OrchestrationHostIdentity {
  const OrchestrationHostIdentity({
    required this.provider,
    required this.url,
    required this.hostMode,
    this.version,
    this.city,
  });

  /// `gascity` or `fixture`.
  final String provider;

  /// Host version string from its health endpoint, when known.
  final String? version;

  /// Gas City city name, when known.
  final String? city;

  /// Base URL the gateway talks to.
  final String url;
  final OrchestrationHostMode hostMode;

  @override
  bool operator ==(Object other) =>
      other is OrchestrationHostIdentity &&
      other.provider == provider &&
      other.version == version &&
      other.city == city &&
      other.url == url &&
      other.hostMode == hostMode;

  @override
  int get hashCode => Object.hash(provider, version, city, url, hostMode);

  @override
  String toString() =>
      'OrchestrationHostIdentity($provider ${version ?? '?'} '
      '${city ?? ''} $url ${hostMode.name})';
}

/// Feature switches for orchestration abilities, gated per adapter the way
/// `ServerCapabilities` gates the OpenCode gateway. Every widget in the
/// plugin checks one of these before it exists in the tree.
class OrchestrationCapabilities {
  const OrchestrationCapabilities({
    this.projects = false,
    this.projectLifecycle = false,
    this.livingSpec = false,
    this.projectLanes = false,
    this.projectPlacement = false,
    this.projectVerification = false,
    this.projectMergeQueue = false,
    this.projectPromotion = false,
    this.projectResume = false,
    this.projectBudgets = false,
    this.projectDigest = false,

    this.runs = false,
    this.runSteps = false,
    this.workGraph = false,
    this.workReady = false,
    this.agents = false,
    this.agentOutput = false,
    this.sessionLink = false,
    this.gatesInteractions = false,
    this.gatesBeads = false,
    this.usage = false,
    this.eventStream = false,
    this.eventReplay = false,
    this.controlRespond = false,
    this.controlMessage = false,
    this.controlAgent = false,
    this.controlCancelRun = false,
    this.controlAssign = false,
    this.controlCreateWork = false,
    this.controlProject = false,
    this.controlProjectRemove = false,
    this.controlAgentKill = false,
    this.scheduledJobs = false,
    this.controlScheduledJobs = false,
    this.changes = false,
    this.verification = false,
    this.mergeReadiness = false,
    this.phoneHost = false,
  });

  final bool projectLifecycle;
  final bool livingSpec;
  final bool projectLanes;
  final bool projectPlacement;
  final bool projectVerification;
  final bool projectMergeQueue;
  final bool projectPromotion;

  /// The engine can resume interrupted work (resumeProject/resumeTask).
  final bool projectResume;
  final bool projectBudgets;
  final bool projectDigest;

  /// Project (rig) listing.
  final bool projects;

  /// Run listing and detail.
  final bool runs;

  /// Per-step detail inside a run.
  final bool runSteps;

  /// Work items with dependency edges.
  final bool workGraph;

  /// The provider reports a distinct ready set (`?ready=true`).
  final bool workReady;

  /// Agent listing and detail.
  final bool agents;

  /// Live agent transcript (`/session/{id}/stream`).
  final bool agentOutput;

  /// An agent's session can be opened in the OpenCode chat.
  final bool sessionLink;

  /// Pending interactions (choice / confirmation / free text).
  final bool gatesInteractions;

  /// Gate beads.
  final bool gatesBeads;

  /// Usage snapshot.
  final bool usage;

  /// Live event stream.
  final bool eventStream;

  /// Resume from a cursor (`Last-Event-ID` / `after_seq`).
  final bool eventReplay;

  /// Answer a gate.
  final bool controlRespond;

  /// Send a message to an agent.
  final bool controlMessage;

  /// Start / stop / restart an agent.
  final bool controlAgent;

  /// Cancel a run.
  final bool controlCancelRun;

  /// Assign work to an agent (sling).
  final bool controlAssign;

  /// Create one work item (bead) from the phone (TEAM-306), so a task can
  /// go straight to a project's agent pool when the planner is off. On for
  /// the loopback supervisor (`POST /beads`) and the fixture; off behind
  /// the host front, which has no create route of its own — a PC-hosted
  /// city keeps its planner and needs no direct path.
  final bool controlCreateWork;

  /// Suspend and resume a project (Gas City rig) from the phone, through
  /// [OrchestrationRigGateway].
  final bool controlProject;

  /// Remove a project (Gas City rig) from the team from the phone.
  final bool controlProjectRemove;

  /// Hard-kill an agent's session (Gas City `session/{id}/kill`), beside
  /// the normal stop.
  final bool controlAgentKill;

  /// The host's scheduled jobs (Gas City orders) can be listed.
  final bool scheduledJobs;

  /// A scheduled job can be switched on or off.
  final bool controlScheduledJobs;

  /// Change sets produced by work items.
  final bool changes;

  /// Verification results for change sets.
  final bool verification;

  /// Merge readiness of change sets.
  final bool mergeReadiness;

  /// The host runs on the phone (Termux) rather than a computer.
  final bool phoneHost;

  /// Adapter absent or disabled: nothing is shown.
  static const none = OrchestrationCapabilities();

  /// Gas City read path proven by the PC spike (`docs/qa/ai-team/
  /// spike-pc-2026-09.md`): reads, the `/usage` estimate and the SSE
  /// stream, no writes.
  static const gascityRead = OrchestrationCapabilities(
    projects: true,
    runs: true,
    runSteps: true,
    workGraph: true,
    workReady: true,
    agents: true,
    agentOutput: true,
    sessionLink: true,
    gatesInteractions: true,
    gatesBeads: true,
    usage: true,
    eventStream: true,
    eventReplay: true,
    scheduledJobs: true,
  );

  /// Gas City behind the host front: [gascityRead] plus every `control*`
  /// and the merge roles the front implements over git (TEAM-205).
  static const gascityFront = OrchestrationCapabilities(
    projects: true,
    runs: true,
    runSteps: true,
    workGraph: true,
    workReady: true,
    agents: true,
    agentOutput: true,
    sessionLink: true,
    gatesInteractions: true,
    gatesBeads: true,
    usage: true,
    eventStream: true,
    eventReplay: true,
    controlRespond: true,
    controlMessage: true,
    controlAgent: true,
    controlCancelRun: true,
    controlAssign: true,
    changes: true,
    verification: true,
    mergeReadiness: true,
    controlProject: true,
    controlProjectRemove: true,
    controlAgentKill: true,
    scheduledJobs: true,
    controlScheduledJobs: true,
  );

  /// Gas City on this phone (TEAM-301): [gascityRead] plus every
  /// `control*` posted straight to the loopback supervisor; the front-only
  /// merge roles stay off and [phoneHost] is on.
  static const gascityLoopback = OrchestrationCapabilities(
    projects: true,
    runs: true,
    runSteps: true,
    workGraph: true,
    workReady: true,
    agents: true,
    agentOutput: true,
    sessionLink: true,
    gatesInteractions: true,
    gatesBeads: true,
    usage: true,
    eventStream: true,
    eventReplay: true,
    controlRespond: true,
    controlMessage: true,
    controlAgent: true,
    controlCancelRun: true,
    controlAssign: true,
    controlCreateWork: true,
    phoneHost: true,
    controlProject: true,
    controlAgentKill: true,
    scheduledJobs: true,
    controlScheduledJobs: true,
  );

  /// Recorded fixture server: everything on, so every widget is testable.
  static const fixture = OrchestrationCapabilities(
    projects: true,
    runs: true,
    runSteps: true,
    workGraph: true,
    workReady: true,
    agents: true,
    agentOutput: true,
    sessionLink: true,
    gatesInteractions: true,
    gatesBeads: true,
    usage: true,
    eventStream: true,
    eventReplay: true,
    controlRespond: true,
    controlMessage: true,
    controlAgent: true,
    controlCancelRun: true,
    controlAssign: true,
    controlCreateWork: true,
    changes: true,
    verification: true,
    mergeReadiness: true,
    phoneHost: true,
    controlProject: true,
    controlProjectRemove: true,
    controlAgentKill: true,
    scheduledJobs: true,
    controlScheduledJobs: true,
  );

  /// Any write is possible.
  bool get anyControl =>
      controlRespond ||
      controlMessage ||
      controlAgent ||
      controlCancelRun ||
      controlAssign ||
      controlCreateWork ||
      controlProject ||
      controlProjectRemove ||
      controlAgentKill ||
      controlScheduledJobs;

  /// Every switch by name, for diagnostics and consistency tests.
  Map<String, bool> asMap() => {
    'projects': projects,
    'projectLifecycle': projectLifecycle,
    'livingSpec': livingSpec,
    'projectLanes': projectLanes,
    'projectPlacement': projectPlacement,
    'projectVerification': projectVerification,
    'projectMergeQueue': projectMergeQueue,
    'projectPromotion': projectPromotion,
    'projectResume': projectResume,
    'projectBudgets': projectBudgets,
    'projectDigest': projectDigest,

    'runs': runs,
    'runSteps': runSteps,
    'workGraph': workGraph,
    'workReady': workReady,
    'agents': agents,
    'agentOutput': agentOutput,
    'sessionLink': sessionLink,
    'gatesInteractions': gatesInteractions,
    'gatesBeads': gatesBeads,
    'usage': usage,
    'eventStream': eventStream,
    'eventReplay': eventReplay,
    'controlRespond': controlRespond,
    'controlMessage': controlMessage,
    'controlAgent': controlAgent,
    'controlCancelRun': controlCancelRun,
    'controlAssign': controlAssign,
    'controlCreateWork': controlCreateWork,
    'controlProject': controlProject,
    'controlProjectRemove': controlProjectRemove,
    'controlAgentKill': controlAgentKill,
    'scheduledJobs': scheduledJobs,
    'controlScheduledJobs': controlScheduledJobs,
    'changes': changes,
    'verification': verification,
    'mergeReadiness': mergeReadiness,
    'phoneHost': phoneHost,
  };
}

/// Per-adapter constants under the names used by the architecture plan
/// (04 §3); identical to the static members of [OrchestrationCapabilities].
const noneCapabilities = OrchestrationCapabilities.none;
const gascityReadCapabilities = OrchestrationCapabilities.gascityRead;
const gascityFrontCapabilities = OrchestrationCapabilities.gascityFront;
const gascityLoopbackCapabilities = OrchestrationCapabilities.gascityLoopback;
const fixtureCapabilities = OrchestrationCapabilities.fixture;

/// Outcome of a control request as far as the host has told us.
enum MutationReceiptStatus {
  /// Sent, but the host front could not say whether the supervisor got it
  /// (a transport failure) or said it did not (`502 upstream-unavailable`,
  /// nothing stored). [MutationReceipt.retryable] tells the two apart.
  pending,
  accepted,
  rejected,

  /// No result inside the wait window; never auto-retried.
  timedOut,
}

/// What came back from one control request. [id] is the client
/// idempotency key so a later `request.result` event can be matched.
///
/// Serialisable ([toJson] / [fromJson]) because the controller persists
/// the receipt beside its idempotency key (04-plugin-architecture §6).
class MutationReceipt {
  const MutationReceipt({
    required this.id,
    required this.status,
    this.message,
    this.raw = const {},
    this.retryable = false,
    this.replayed = false,
    this.hostRequestId,
    this.correlationId,
    this.upstreamStatus,
  });

  /// Receipt for a write the gateway cannot perform.
  const MutationReceipt.rejected(this.id, this.message)
    : status = MutationReceiptStatus.rejected,
      raw = const {},
      retryable = false,
      replayed = false,
      hostRequestId = null,
      correlationId = null,
      upstreamStatus = null;

  /// Decodes a receipt persisted with [toJson]; null for anything else.
  static MutationReceipt? fromJson(Object? json) {
    if (json is! Map) return null;
    final id = json['id'];
    final status = json['status'];
    if (id is! String || status is! String) return null;
    final raw = json['raw'];
    return MutationReceipt(
      id: id,
      status: MutationReceiptStatus.values.firstWhere(
        (s) => s.name == status,
        orElse: () => MutationReceiptStatus.pending,
      ),
      message: json['message'] is String ? json['message'] as String : null,
      raw: raw is Map ? raw.cast<String, Object?>() : const {},
      retryable: json['retryable'] == true,
      replayed: json['replayed'] == true,
      hostRequestId: json['hostRequestId'] is String
          ? json['hostRequestId'] as String
          : null,
      correlationId: json['correlationId'] is String
          ? json['correlationId'] as String
          : null,
      upstreamStatus: json['upstreamStatus'] is int
          ? json['upstreamStatus'] as int
          : null,
    );
  }

  final String id;
  final MutationReceiptStatus status;

  /// Human explanation for [MutationReceiptStatus.rejected],
  /// [MutationReceiptStatus.pending] or [MutationReceiptStatus.timedOut];
  /// null otherwise.
  final String? message;

  /// Untouched provider response.
  final Map<String, Object?> raw;

  /// The host said nothing was stored (the front answered `502
  /// upstream-unavailable`), so sending again is safe. Never acted on by
  /// the gateway or the controller: only a person retries.
  final bool retryable;

  /// The front answered from its receipt store (`Idempotent-Replayed`):
  /// the supervisor was not called again.
  final bool replayed;

  /// The host's `X-GC-Request-Id` for this HTTP exchange, for bug reports.
  final String? hostRequestId;

  /// The supervisor's asynchronous correlation id (the `request_id` of a
  /// 202 body) that the matching `request.result.*` / `request.failed`
  /// event carries; null for synchronous or rejected writes.
  final String? correlationId;

  /// The supervisor's HTTP status as the front relayed it, when known.
  final int? upstreamStatus;

  bool get isAccepted => status == MutationReceiptStatus.accepted;

  /// The id of the resource a create answered with (`raw['id']`, or the
  /// front receipt's inner `body.id`) when it is a non-empty string; null
  /// otherwise. Set by [OrchestrationControlGateway.createWork].
  String? get createdId {
    final id = raw['id'];
    if (id is String && id.isNotEmpty) return id;
    final inner = raw['body'];
    if (inner is Map) {
      final innerId = inner['id'];
      if (innerId is String && innerId.isNotEmpty) return innerId;
    }
    return null;
  }

  Map<String, Object?> toJson() => {
    'id': id,
    'status': status.name,
    if (message != null) 'message': message,
    if (raw.isNotEmpty) 'raw': raw,
    if (retryable) 'retryable': true,
    if (replayed) 'replayed': true,
    if (hostRequestId != null) 'hostRequestId': hostRequestId,
    if (correlationId != null) 'correlationId': correlationId,
    if (upstreamStatus != null) 'upstreamStatus': upstreamStatus,
  };

  @override
  String toString() => 'MutationReceipt($id, ${status.name})';
}

/// A person's answer to a gate: exactly one of [choice], [confirmed] or
/// [text] is expected, matching the gate's [GateKind].
class GateResponse {
  const GateResponse.choice(String this.choice) : confirmed = null, text = null;
  const GateResponse.confirmation({required bool this.confirmed})
    : choice = null,
      text = null;
  const GateResponse.text(String this.text) : choice = null, confirmed = null;

  final String? choice;
  final bool? confirmed;
  final String? text;
}

/// Lifecycle verbs the app may send to an agent (02-ux §5: Nudge,
/// Pause/Resume, Stop, Restart). [start] is the older name for [resume]
/// and maps the same way.
enum AgentControlAction {
  /// "Please continue" as a message to the agent's session.
  nudge,

  /// Keep the daemon from waking the agent again (Gas City: agent
  /// `suspend`; session `suspend` for a pool instance without an agent
  /// entry).
  pause,

  /// Undo [pause] (Gas City: agent `resume`, else session `wake`).
  resume,

  /// Same as [resume].
  start,

  /// Stop the agent's session (Gas City: session `stop`).
  stop,

  /// [stop], then wake the session again.
  restart,

  /// End the session by force (Gas City: session `kill`), for an agent a
  /// normal [stop] did not end. Work in its session may be lost.
  kill,
}

/// What a person asks of a project (Gas City rig).
enum ProjectControlAction {
  /// Switch the project off: its agents stop picking up work.
  suspend,

  /// Switch it on again.
  resume,

  /// Take the project out of the team (Gas City: `DELETE /rig/{name}`).
  remove,
}

/// Read side: inventories for the selected host. Adapters return empty
/// lists (never throw) for reads their capabilities do not cover.
abstract interface class OrchestrationReadGateway {
  Future<List<OrchestrationProject>> projects();
  Future<List<OrchestrationRun>> runs({String? projectId});
  Future<OrchestrationRun?> run(String id);

  /// Every work item, ready or not, optionally scoped to one project.
  Future<List<WorkItem>> work({String? projectId});

  /// Only items the provider reports as ready to pick up.
  Future<List<WorkItem>> readyWork({String? projectId});
  Future<WorkItem?> workItem(String id);
  Future<List<OrchestrationAgent>> agents();
  Future<OrchestrationAgent?> agent(String id);
  Future<List<OrchestrationGate>> gates();
  Future<OrchestrationUsage?> usage();

  /// Activity page, oldest first, strictly after [afterSeq] when given.
  Future<List<ActivityEvent>> activity({int? afterSeq, int limit = 100});
}

/// Live agent output, behind [OrchestrationCapabilities.agentOutput].
/// Adapters that can follow a session's transcript implement this beside
/// [OrchestrationGateway]; the controller checks for it at runtime so a
/// read-only double need not.
abstract interface class OrchestrationAgentOutputGateway {
  /// The session's output as it grows: [AgentOutputText] captures, then
  /// [AgentOutputEnded] once the host stops serving the session (404),
  /// after which the stream closes. Transport failures reconnect inside
  /// the adapter; the stream ends only on cancel, close or session end.
  Stream<AgentOutputEvent> agentOutput(String sessionId);
}

/// Event side: one live stream. Implementations own reconnect and backoff;
/// the returned stream ends only when the caller cancels or the gateway
/// closes.
abstract interface class OrchestrationEventGateway {
  Stream<OrchestrationEvent> events({
    EventCursor resumeFrom = EventCursor.none,
  });
}

/// Write side, only when the matching `control*` capability is on. Every
/// call takes the client-generated [requestId] (persisted before send) and
/// resolves to a [MutationReceipt]; none is retried by the gateway.
abstract interface class OrchestrationControlGateway {
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  });
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  });
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  });
  Future<MutationReceipt> cancelRun(String runId, {required String requestId});
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  });

  /// Creates one work item (bead) titled [title] in the project
  /// [projectId] (a rig name; the host's default when null), behind
  /// [OrchestrationCapabilities.controlCreateWork]. The created id comes
  /// back in [MutationReceipt.createdId] (`receipt.raw['id']`); the caller
  /// assigns it with [assign].
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  });
}

/// Merge roles (TEAM-205, 02-ux §8a), behind
/// [OrchestrationCapabilities.mergeReadiness]. Like
/// [OrchestrationAgentOutputGateway] this sits beside the gateway rather
/// than inside it: adapters that can answer merge readiness implement it
/// and the controller checks for it at runtime, so a read-only double
/// need not. Force-merge, branch reset and worktree deletion have no verb
/// here on purpose.
abstract interface class OrchestrationMergeGateway {
  /// The host's readiness document for [runId]; null when the host has no
  /// merge roles for this run (a bare supervisor, no front).
  Future<MergeReadiness?> mergeReadiness(String runId);

  /// Approves the merge request [mergeRequestId] (a merge-request bead or
  /// the run bead itself) as this device's identity.
  Future<MutationReceipt> approveMerge(
    String mergeRequestId, {
    required String requestId,
  });

  /// Merges the run's branches into the rig's default branch. The host
  /// refuses unless readiness passes and its boundaries allow.
  Future<MutationReceipt> merge(String runId, {required String requestId});
}

/// Project and scheduled-job controls (OD1), behind
/// [OrchestrationCapabilities.controlProject], [scheduledJobs] and the
/// matching switches. Sits beside the gateway like [OrchestrationMergeGateway]:
/// adapters that can answer implement it and the controller checks for it at
/// runtime, so a read-only double need not.
abstract interface class OrchestrationRigGateway {
  /// Suspends, resumes or removes the project [projectId] (a rig name).
  Future<MutationReceipt> controlProject(
    String projectId,
    ProjectControlAction action, {
    required String requestId,
  });

  /// The host's scheduled jobs, every project's and the team's own.
  Future<List<ScheduledJob>> scheduledJobs();

  /// Switches the scheduled job [jobId] (its scoped name) on or off.
  Future<MutationReceipt> controlScheduledJob(
    String jobId, {
    required bool enabled,
    required String requestId,
  });
}

/// The host's supervision policy (TEAM-207, 02-ux §7), read-only. Sits
/// beside the gateway like [OrchestrationMergeGateway]: only a host front
/// answers it, so the controller checks for it at runtime and shows
/// nothing when the adapter lacks it.
abstract interface class OrchestrationPolicyGateway {
  /// The host's policy for [projectId] (a rig), or the host's default rig
  /// when null; null when the host has no policy route (a bare
  /// supervisor, an older front).
  Future<OrchestrationPolicy?> policy({String? projectId});
}

/// The provider-neutral transport for one orchestration host: reads, live
/// events and controls behind [capabilities]. A sibling of `ServerGateway`,
/// never embedded in it.
abstract interface class OrchestrationGateway
    implements
        OrchestrationReadGateway,
        OrchestrationEventGateway,
        OrchestrationControlGateway {
  OrchestrationCapabilities get capabilities;

  /// Identity of the connected host; null until the probe has answered.
  OrchestrationHostIdentity? get host;
  bool get isClosed;
  Future<void> close();
}
