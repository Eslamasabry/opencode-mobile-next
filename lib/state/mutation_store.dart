/// The idempotency and receipt store of the AI Team plugin (TEAM-202,
/// 04-plugin-architecture §6): one persisted [MutationRecord] per write the
/// person asked for, saved under the preference key
/// `oc.orchestration.` + profile id + `.mutations.` + key BEFORE the
/// gateway is called, so a crash, a lost stream or an
/// app restart can never turn one tap into two sends.
///
/// The record's [MutationStatus] follows the write:
///
/// | Status | Meaning | Copy (02-ux §6) |
/// |---|---|---|
/// | sent | persisted; waiting for the receipt or the host's result event | "Sent · waiting for the host to confirm" |
/// | confirmed | the matching `request.result` (or the effect's own event) arrived | "Answered" |
/// | rejected | the host or the front refused | the receipt's message |
/// | unconfirmed | no result inside the wait window, or the app restarted while sent | "Sent, unconfirmed — check on the host before re-sending" |
///
/// Nothing here re-sends. A retry is a new record with a new key, created
/// only by [OrchestrationController.retryMutation] after a person's tap.
library;

import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../domain/orchestration_gateway.dart';
import 'team_storage_redaction.dart';

/// Which [OrchestrationControlGateway] verb a mutation is.
enum MutationKind {
  respond,
  message,
  controlAgent,
  cancelRun,
  assign,

  /// Approve a merge request (TEAM-205); the target is the request bead.
  approveMerge,

  /// Merge a run into the rig's default branch (TEAM-205); the target is
  /// the run.
  merge,

  /// Create one work item (TEAM-306); the target is the title, there is
  /// no id yet. The created id lands in the receipt
  /// ([MutationReceipt.createdId]).
  createWork,

  /// Suspend, resume or remove a project (Gas City rig); the target is the
  /// project, the action's name is the request's `choice`.
  controlProject,

  /// Switch a scheduled job (Gas City order) on or off; the target is the
  /// job's scoped name, [MutationRequest.confirmed] says on or off.
  controlScheduledJob,
}

/// Where a mutation stands (table in the library doc).
enum MutationStatus { sent, confirmed, rejected, unconfirmed }

/// Everything needed to send (or re-send under a new key) one write:
/// the verb, its target and its arguments. JSON-serialisable so a retry
/// after a restart has the saved content (credential-like text is redacted).
class MutationRequest {
  const MutationRequest._({
    required this.kind,
    required this.targetId,
    this.choice,
    this.confirmed,
    this.text,
    this.action,
    this.agentId,
    this.projectId,
  });

  /// Answer the gate [gateId] with [response].
  factory MutationRequest.respond(String gateId, GateResponse response) =>
      MutationRequest._(
        kind: MutationKind.respond,
        targetId: gateId,
        choice: response.choice,
        confirmed: response.confirmed,
        text: response.text,
      );

  /// Send [text] to the agent [agentId].
  factory MutationRequest.message(String agentId, String text) =>
      MutationRequest._(
        kind: MutationKind.message,
        targetId: agentId,
        text: text,
      );

  /// Apply [action] to the agent [agentId].
  factory MutationRequest.controlAgent(
    String agentId,
    AgentControlAction action,
  ) => MutationRequest._(
    kind: MutationKind.controlAgent,
    targetId: agentId,
    action: action,
  );

  /// Cancel the run [runId].
  factory MutationRequest.cancelRun(String runId) =>
      MutationRequest._(kind: MutationKind.cancelRun, targetId: runId);

  /// Assign the work item [workId] to the agent [agentId].
  factory MutationRequest.assign(String workId, {required String agentId}) =>
      MutationRequest._(
        kind: MutationKind.assign,
        targetId: workId,
        agentId: agentId,
      );

  /// Approve the merge request [mergeRequestId].
  factory MutationRequest.approveMerge(String mergeRequestId) =>
      MutationRequest._(
        kind: MutationKind.approveMerge,
        targetId: mergeRequestId,
      );

  /// Merge the run [runId] into the rig's default branch.
  factory MutationRequest.merge(String runId) =>
      MutationRequest._(kind: MutationKind.merge, targetId: runId);

  /// Create a work item titled [title] (the target: there is no id yet)
  /// with [description] as its text in the project [projectId].
  factory MutationRequest.createWork({
    required String title,
    String? description,
    String? projectId,
  }) => MutationRequest._(
    kind: MutationKind.createWork,
    targetId: title,
    text: description,
    projectId: projectId,
  );

  /// Suspend, resume or remove the project [projectId].
  factory MutationRequest.controlProject(
    String projectId,
    ProjectControlAction action,
  ) => MutationRequest._(
    kind: MutationKind.controlProject,
    targetId: projectId,
    choice: action.name,
  );

  /// Switch the scheduled job [jobId] on ([enabled]) or off.
  factory MutationRequest.controlScheduledJob(
    String jobId, {
    required bool enabled,
  }) => MutationRequest._(
    kind: MutationKind.controlScheduledJob,
    targetId: jobId,
    confirmed: enabled,
  );

  /// The project action, for [MutationKind.controlProject].
  ProjectControlAction? get projectAction {
    if (kind != MutationKind.controlProject) return null;
    for (final a in ProjectControlAction.values) {
      if (a.name == choice) return a;
    }
    return null;
  }

  /// Decodes a request persisted with [toJson]; null for anything else.
  static MutationRequest? fromJson(Object? json) {
    if (json is! Map) return null;
    final kindName = json['kind'];
    final targetId = json['targetId'];
    if (kindName is! String || targetId is! String) return null;
    MutationKind? kind;
    for (final k in MutationKind.values) {
      if (k.name == kindName) kind = k;
    }
    if (kind == null) return null;
    final actionName = json['action'];
    AgentControlAction? action;
    if (actionName is String) {
      for (final a in AgentControlAction.values) {
        if (a.name == actionName) action = a;
      }
    }
    return MutationRequest._(
      kind: kind,
      targetId: targetId,
      choice: json['choice'] is String ? json['choice'] as String : null,
      confirmed: json['confirmed'] is bool ? json['confirmed'] as bool : null,
      text: json['text'] is String ? json['text'] as String : null,
      action: action,
      agentId: json['agentId'] is String ? json['agentId'] as String : null,
      projectId: json['projectId'] is String
          ? json['projectId'] as String
          : null,
    );
  }

  final MutationKind kind;

  /// The gate, agent, run or work item the verb applies to; the title for
  /// [MutationKind.createWork].
  final String targetId;
  final String? choice;
  final bool? confirmed;
  final String? text;
  final AgentControlAction? action;

  /// The agent a work item is assigned to ([MutationKind.assign]).
  final String? agentId;

  /// The project (rig) a work item is created in
  /// ([MutationKind.createWork]); null for the host's default.
  final String? projectId;

  /// The gate answer, for [MutationKind.respond]; null for other kinds.
  GateResponse? get response {
    if (kind != MutationKind.respond) return null;
    final c = choice;
    if (c != null) return GateResponse.choice(c);
    final ok = confirmed;
    if (ok != null) return GateResponse.confirmation(confirmed: ok);
    return GateResponse.text(text ?? '');
  }

  Map<String, Object?> toJson() => {
    'kind': kind.name,
    'targetId': targetId,
    if (choice != null) 'choice': choice,
    if (confirmed != null) 'confirmed': confirmed,
    if (text != null) 'text': text,
    if (action != null) 'action': action!.name,
    if (agentId != null) 'agentId': agentId,
    if (projectId != null) 'projectId': projectId,
  };

  @override
  String toString() => 'MutationRequest(${kind.name} $targetId)';
}

/// One write as the store keeps it: the idempotency [key], what was
/// asked, when, where it stands and what the host answered.
class MutationRecord {
  const MutationRecord({
    required this.key,
    required this.request,
    required this.createdAt,
    required this.status,
    DateTime? updatedAt,
    this.receipt,
    this.retryOf,
    this.retriedBy,
  }) : updatedAt = updatedAt ?? createdAt;

  /// Decodes a record persisted with [toJson]; null for anything else.
  static MutationRecord? fromJson(Object? json) {
    if (json is! Map) return null;
    final key = json['key'];
    final request = MutationRequest.fromJson(json['request']);
    final createdAt = json['createdAt'];
    final statusName = json['status'];
    if (key is! String ||
        request == null ||
        createdAt is! String ||
        statusName is! String) {
      return null;
    }
    final created = DateTime.tryParse(createdAt);
    if (created == null) return null;
    final updatedAt = json['updatedAt'];
    return MutationRecord(
      key: key,
      request: request,
      createdAt: created,
      updatedAt: updatedAt is String ? DateTime.tryParse(updatedAt) : null,
      status: MutationStatus.values.firstWhere(
        (s) => s.name == statusName,
        orElse: () => MutationStatus.unconfirmed,
      ),
      receipt: MutationReceipt.fromJson(json['receipt']),
      retryOf: json['retryOf'] is String ? json['retryOf'] as String : null,
      retriedBy: json['retriedBy'] is String
          ? json['retriedBy'] as String
          : null,
    );
  }

  /// The client UUID sent as `Idempotency-Key` and as the gateway's
  /// `requestId`.
  final String key;
  final MutationRequest request;
  final DateTime createdAt;
  final DateTime updatedAt;
  final MutationStatus status;

  /// What the gateway answered; null until it did.
  final MutationReceipt? receipt;

  /// The key of the record this one retries, when it is a retry.
  final String? retryOf;

  /// The key of the retry that superseded this record, when any.
  final String? retriedBy;

  MutationKind get kind => request.kind;
  String get targetId => request.targetId;

  /// True once the host's answer is final.
  bool get isSettled =>
      status == MutationStatus.confirmed || status == MutationStatus.rejected;

  /// True while the write is on its way: the row shows "Sent · waiting for
  /// the host to confirm".
  bool get isSent => status == MutationStatus.sent;

  /// A person may retry: the host refused, or nobody knows whether it
  /// happened. [MutationReceipt.retryable] says the host is known not to
  /// have stored it.
  bool get canRetry =>
      retriedBy == null &&
      (status == MutationStatus.rejected ||
          status == MutationStatus.unconfirmed);

  /// The supervisor's correlation id, when the write got one.
  String? get correlationId => receipt?.correlationId;

  MutationRecord copyWith({
    MutationStatus? status,
    MutationReceipt? receipt,
    DateTime? updatedAt,
    String? retriedBy,
  }) => MutationRecord(
    key: key,
    request: request,
    createdAt: createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
    status: status ?? this.status,
    receipt: receipt ?? this.receipt,
    retryOf: retryOf,
    retriedBy: retriedBy ?? this.retriedBy,
  );

  Map<String, Object?> toJson() => {
    'key': key,
    'request': request.toJson(),
    'createdAt': createdAt.toUtc().toIso8601String(),
    'updatedAt': updatedAt.toUtc().toIso8601String(),
    'status': status.name,
    if (receipt != null) 'receipt': receipt!.toJson(),
    if (retryOf != null) 'retryOf': retryOf,
    if (retriedBy != null) 'retriedBy': retriedBy,
  };

  @override
  String toString() =>
      'MutationRecord($key ${request.kind.name} ${request.targetId} '
      '${status.name})';
}

/// `oc.orchestration.<profileId>.mutations.` — every mutation key of a
/// profile starts with this, under the plugin prefix so the profile
/// deletion sweep and `OrchestrationStore.sweep` already drop them.
String mutationKeyPrefix(String profileId) =>
    'oc.orchestration.$profileId.mutations.';

/// Per-profile persistence of [MutationRecord]s, one preference each.
class MutationStore {
  MutationStore(this.prefs, {this.keep = 200});

  final SharedPreferences prefs;

  /// How many records [prune] keeps per profile (newest first).
  final int keep;

  /// The preference key of one record.
  static String keyFor(String profileId, String key) =>
      '${mutationKeyPrefix(profileId)}$key';

  /// Every record of [profileId], oldest first. Unreadable entries are
  /// skipped.
  List<MutationRecord> read(String profileId) {
    if (profileId.isEmpty) return const [];
    final prefix = mutationKeyPrefix(profileId);
    final records = <MutationRecord>[];
    for (final key in prefs.getKeys()) {
      if (!key.startsWith(prefix)) continue;
      final record = _decode(prefs.getString(key));
      if (record != null) records.add(record);
    }
    records.sort((a, b) => a.createdAt.compareTo(b.createdAt));
    return records;
  }

  /// One record by idempotency key, null when absent or unreadable.
  MutationRecord? get(String profileId, String key) => profileId.isEmpty
      ? null
      : _decode(prefs.getString(keyFor(profileId, key)));

  /// Writes [record]; the returned future completes once the preference
  /// holds its redacted copy. Callers send the original request only after
  /// awaiting this; a later explicit retry uses the redacted saved text.
  Future<void> save(String profileId, MutationRecord record) async {
    if (profileId.isEmpty) return;
    await prefs.setString(
      keyFor(profileId, record.key),
      jsonEncode(redactTeamStoredValue(record.toJson())),
    );
  }

  /// Drops settled records beyond [keep] (oldest first); sent and
  /// unconfirmed ones are never pruned, a person has to see them.
  Future<void> prune(String profileId) async {
    final records = read(profileId);
    if (records.length <= keep) return;
    var excess = records.length - keep;
    for (final record in records) {
      if (excess <= 0) break;
      if (!record.isSettled) continue;
      await prefs.remove(keyFor(profileId, record.key));
      excess -= 1;
    }
  }

  static MutationRecord? _decode(String? raw) {
    if (raw == null) return null;
    try {
      return MutationRecord.fromJson(jsonDecode(raw));
    } catch (_) {
      return null;
    }
  }
}
