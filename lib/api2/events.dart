/// Typed v2 event union for `GET /api/event` and the per-session durable log.
///
/// The beta server adds event types between builds: parsing must never throw.
/// Anything unrecognized (or malformed) surfaces as [UnknownApi2Event] with
/// the raw JSON preserved.
library;

import 'models.dart';

int? _asInt(dynamic v) => v is num ? v.toInt() : null;
double? _asDouble(dynamic v) => v is num ? v.toDouble() : null;
String? _asString(dynamic v) => v is String ? v : null;
bool? _asBool(dynamic v) => v is bool ? v : null;

Map<String, dynamic>? _asMap(dynamic v) =>
    v is Map ? Map<String, dynamic>.from(v) : null;

List<T> _mapList<T>(dynamic v, T? Function(Map<String, dynamic>) parse) {
  final out = <T>[];
  if (v is List) {
    for (final item in v) {
      final map = _asMap(item);
      if (map == null) continue;
      try {
        final parsed = parse(map);
        if (parsed != null) out.add(parsed);
      } catch (_) {}
    }
  }
  return out;
}

/// `durable` marker present on event-sourced events; `seq` is the
/// per-session cursor usable as `?after=` on the durable log.
class Api2Durable {
  final String? aggregateID;
  final int? seq;
  final int? version;
  Api2Durable({this.aggregateID, this.seq, this.version});

  static Api2Durable? fromJson(dynamic v) {
    final j = _asMap(v);
    if (j == null) return null;
    return Api2Durable(
      aggregateID: _asString(j['aggregateID']),
      seq: _asInt(j['seq']),
      version: _asInt(j['version']),
    );
  }
}

/// Wire envelope `{id, created, type, location?, metadata?, durable?, data}`.
class Api2EventEnvelope {
  final String? id;
  final int? created;
  final String type;
  final Api2Location? location;
  final Map<String, dynamic>? metadata;
  final Api2Durable? durable;
  final Map<String, dynamic> data;
  final Api2Event event;
  Api2EventEnvelope({
    this.id,
    this.created,
    required this.type,
    this.location,
    this.metadata,
    this.durable,
    this.data = const {},
    required this.event,
  });

  bool get isDurable => durable != null;

  factory Api2EventEnvelope.fromJson(Map<String, dynamic> j) {
    final type = _asString(j['type']) ?? '';
    final data = _asMap(j['data']) ?? j;
    Api2Event event;
    try {
      event = Api2Event.parse(type, data);
    } catch (_) {
      event = UnknownApi2Event(type: type, raw: j);
    }
    return Api2EventEnvelope(
      id: _asString(j['id']),
      created: _asInt(j['created']),
      type: type,
      location: Api2Location.fromJson(j['location']),
      metadata: _asMap(j['metadata']),
      durable: Api2Durable.fromJson(j['durable']),
      data: data,
      event: event,
    );
  }
}

enum Api2Phase {
  started,
  delta,
  ended,
  streamed,
  succeeded,
  failed,
  interrupted,
  success,
  enqueued,
  delivered,
  cancelled,
  deliveryChanged,
  staged,
  cleared,
  committed,
}

sealed class Api2Event {
  const Api2Event();

  static Api2Event parse(String type, Map<String, dynamic> d) {
    switch (type) {
      case 'server.connected':
        return const Api2ServerConnectedEvent();
      case 'shell.created':
      case 'shell.exited':
      case 'shell.deleted':
        return Api2ManagedShellEvent(type: type, data: d);
      case 'log.synced':
        return Api2LogSyncedEvent(
          aggregateID: _asString(d['aggregateID']),
          seq: _asInt(d['seq']),
        );
      case 'session.created':
        return Api2SessionCreatedEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          projectID: _asString(d['projectID']),
          parentID: _asString(d['parentID']),
          slug: _asString(d['slug']),
          title: _asString(d['title']),
          agent: _asString(d['agent']),
          model: Api2ModelRef.fromJson(d['model']),
          location: Api2Location.fromJson(d['location']),
          subpath: _asString(d['subpath']),
          version: _asString(d['version']),
        );
      case 'session.renamed':
        return Api2SessionRenamedEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          title: _asString(d['title']) ?? '',
        );
      case 'session.deleted':
        return Api2SessionDeletedEvent(
          sessionID: _asString(d['sessionID']) ?? '',
        );
      case 'session.moved':
        return Api2SessionMovedEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          location: Api2Location.fromJson(d['location']),
          projectID: _asString(d['projectID']),
          subpath: _asString(d['subpath']),
        );
      case 'session.forked':
        return Api2SessionForkedEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          parentID: _asString(d['parentID']),
          boundary: Api2ForkBoundary.fromJson(d['boundary']),
        );
      case 'session.viewed':
        return Api2SessionViewedEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          idle: _asInt(d['idle']),
        );
      case 'session.agent.selected':
        return Api2SessionAgentSelectedEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          agent: _asString(d['agent']) ?? '',
          previous: _asString(d['previous']),
        );
      case 'session.model.selected':
        return Api2SessionModelSelectedEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          model: Api2ModelRef.fromJson(d['model']),
        );
      case 'session.usage.updated':
        return Api2SessionUsageUpdatedEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          cost: _asDouble(d['cost']) ?? 0,
          tokens: Api2Tokens.fromJson(d['tokens']),
        );
      case 'session.execution.started':
        return _execution(Api2Phase.started, d);
      case 'session.execution.succeeded':
        return _execution(Api2Phase.succeeded, d);
      case 'session.execution.failed':
        return _execution(Api2Phase.failed, d);
      case 'session.execution.interrupted':
        return _execution(Api2Phase.interrupted, d);
      case 'session.inbox.enqueued':
        return _inbox(Api2Phase.enqueued, d);
      case 'session.inbox.delivered':
        return _inbox(Api2Phase.delivered, d);
      case 'session.inbox.cancelled':
        return _inbox(Api2Phase.cancelled, d);
      case 'session.inbox.delivery.changed':
        return _inbox(Api2Phase.deliveryChanged, d);
      case 'session.instructions.updated':
        return Api2SessionInstructionsUpdatedEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          delta: _asMap(d['delta']) ?? const {},
          text: _asString(d['text']),
        );
      case 'session.synthetic':
        return Api2SessionSyntheticEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          text: _asString(d['text']) ?? '',
          description: _asString(d['description']),
        );
      case 'session.skill.activated':
        return Api2SessionSkillActivatedEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          id: _asString(d['id']),
          name: _asString(d['name']),
          text: _asString(d['text']),
        );
      case 'session.shell.started':
        return Api2SessionShellEvent(
          phase: Api2Phase.started,
          sessionID: _asString(d['sessionID']) ?? '',
          shell: _asMap(d['shell']),
        );
      case 'session.shell.ended':
        return Api2SessionShellEvent(
          phase: Api2Phase.ended,
          sessionID: _asString(d['sessionID']) ?? '',
          shell: _asMap(d['shell']),
          output: _asMap(d['output']),
        );
      case 'session.step.started':
        return _step(Api2Phase.started, d);
      case 'session.step.streamed':
        return _step(Api2Phase.streamed, d);
      case 'session.step.ended':
        return _step(Api2Phase.ended, d);
      case 'session.step.failed':
        return _step(Api2Phase.failed, d);
      case 'session.text.started':
        return _text(Api2Phase.started, d);
      case 'session.text.delta':
        return _text(Api2Phase.delta, d);
      case 'session.text.ended':
        return _text(Api2Phase.ended, d);
      case 'session.reasoning.started':
        return _reasoning(Api2Phase.started, d);
      case 'session.reasoning.delta':
        return _reasoning(Api2Phase.delta, d);
      case 'session.reasoning.ended':
        return _reasoning(Api2Phase.ended, d);
      case 'session.tool.input.started':
        return _toolInput(Api2Phase.started, d);
      case 'session.tool.input.delta':
        return _toolInput(Api2Phase.delta, d);
      case 'session.tool.input.ended':
        return _toolInput(Api2Phase.ended, d);
      case 'session.tool.called':
        return Api2SessionToolCalledEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          assistantMessageID: _asString(d['assistantMessageID']) ?? '',
          callID: _asString(d['id']) ?? '',
          input: _asMap(d['input']) ?? const {},
          executed: _asBool(d['executed']),
        );
      case 'session.tool.progress':
        return Api2SessionToolProgressEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          assistantMessageID: _asString(d['assistantMessageID']) ?? '',
          callID: _asString(d['id']) ?? '',
          metadata: _asMap(d['metadata']) ?? const {},
        );
      case 'session.tool.success':
        return _toolResult(Api2Phase.success, d);
      case 'session.tool.failed':
        return _toolResult(Api2Phase.failed, d);
      case 'session.retry.scheduled':
        return Api2SessionRetryScheduledEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          assistantMessageID: _asString(d['assistantMessageID']),
          attempt: _asInt(d['attempt']),
          at: _asInt(d['at']),
          error: Api2StructuredError.fromJson(d['error']),
        );
      case 'session.compaction.started':
        return _compaction(Api2Phase.started, d);
      case 'session.compaction.delta':
        return _compaction(Api2Phase.delta, d);
      case 'session.compaction.ended':
        return _compaction(Api2Phase.ended, d);
      case 'session.compaction.failed':
        return _compaction(Api2Phase.failed, d);
      case 'session.revert.staged':
        return Api2SessionRevertEvent(
          phase: Api2Phase.staged,
          sessionID: _asString(d['sessionID']) ?? '',
          revert: _asMap(d['revert']),
        );
      case 'session.revert.cleared':
        return Api2SessionRevertEvent(
          phase: Api2Phase.cleared,
          sessionID: _asString(d['sessionID']) ?? '',
        );
      case 'session.revert.committed':
        return Api2SessionRevertEvent(
          phase: Api2Phase.committed,
          sessionID: _asString(d['sessionID']) ?? '',
          toMessageID: _asString(d['to']),
        );
      case 'session.message.content.updated':
        return Api2SessionMessageContentUpdatedEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          messageID: _asString(d['messageID']) ?? '',
          content: _mapList(
            d['content'],
            (m) => Api2AssistantContent.fromJson(m),
          ),
        );
      case 'session.status':
        final status = _asMap(d['status']) ?? const {};
        return Api2SessionStatusEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          status: Api2SessionRunStatus.parse(status['type']),
          attempt: _asInt(status['attempt']),
          message: _asString(status['message']),
          next: _asInt(status['next']),
          raw: status,
        );
      case 'session.idle':
        return Api2SessionStatusEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          status: Api2SessionRunStatus.idle,
        );
      case 'session.compacted':
        return Api2SessionCompactedEvent(
          sessionID: _asString(d['sessionID']) ?? '',
        );
      case 'permission.asked':
        final request = Api2PermissionRequest.fromJson(d);
        if (request == null) break;
        return Api2PermissionAskedEvent(request: request);
      case 'permission.replied':
        return Api2PermissionRepliedEvent(
          sessionID: _asString(d['sessionID']) ?? '',
          requestID: _asString(d['requestID']) ?? '',
          reply: _asString(d['reply']),
        );
      case 'form.created':
        final form = Api2FormInfo.fromJson(_asMap(d['form']) ?? d);
        if (form == null) break;
        return Api2FormCreatedEvent(form: form);
      case 'form.replied':
        return Api2FormRepliedEvent(
          id: _asString(d['id']) ?? '',
          sessionID: _asString(d['sessionID']) ?? '',
          answer: _asMap(d['answer']) ?? const {},
        );
      case 'form.cancelled':
        return Api2FormCancelledEvent(
          id: _asString(d['id']) ?? '',
          sessionID: _asString(d['sessionID']) ?? '',
        );
      case 'mcp.status.changed':
        return Api2McpChangedEvent(
          kind: 'status',
          server: _asString(d['server']),
        );
      case 'mcp.resources.changed':
        return Api2McpChangedEvent(
          kind: 'resources',
          server: _asString(d['server']),
        );
      case 'mcp.tools.changed':
        return Api2McpChangedEvent(
          kind: 'tools',
          server: _asString(d['server']),
        );
      case 'vcs.branch.updated':
        return Api2VcsBranchUpdatedEvent(branch: _asString(d['branch']));
      case 'filesystem.changed':
        return Api2FilesystemChangedEvent(
          file: _asString(d['file']),
          change: _asString(d['event']),
        );
      case 'tui.toast.show':
        return Api2TuiToastEvent(
          title: _asString(d['title']),
          message: _asString(d['message']) ?? '',
          variant: _asString(d['variant']),
          duration: _asInt(d['duration']),
        );
      case 'agent.updated':
      case 'command.updated':
      case 'config.updated':
      case 'skill.updated':
      case 'catalog.updated':
      case 'models-dev.refreshed':
      case 'integration.updated':
      case 'credential.updated':
      case 'credential.switched':
      case 'plugin.added':
      case 'plugin.updated':
      case 'reference.updated':
      case 'websearch.updated':
      case 'project.updated':
      case 'worktree.updated':
      case 'lsp.updated':
        return Api2RefreshHintEvent(topic: type);
    }
    return UnknownApi2Event(type: type, raw: d);
  }

  static Api2SessionExecutionEvent _execution(
    Api2Phase phase,
    Map<String, dynamic> d,
  ) => Api2SessionExecutionEvent(
    phase: phase,
    sessionID: _asString(d['sessionID']) ?? '',
    error: Api2StructuredError.fromJson(d['error']),
    reason: _asString(d['reason']),
  );

  static Api2SessionInboxEvent _inbox(Api2Phase phase, Map<String, dynamic> d) {
    final item = _asMap(d['item']);
    return Api2SessionInboxEvent(
      phase: phase,
      sessionID: _asString(d['sessionID']) ?? '',
      inboxID: _asString(d['inboxID']) ?? '',
      item: item == null
          ? null
          : Api2InboxItem.fromJson({
              'id': d['inboxID'],
              'sessionID': d['sessionID'],
              ...item,
            }),
      delivery: Api2Delivery.parse(d['delivery'] ?? item?['delivery']),
    );
  }

  static Api2SessionStepEvent _step(Api2Phase phase, Map<String, dynamic> d) =>
      Api2SessionStepEvent(
        phase: phase,
        sessionID: _asString(d['sessionID']) ?? '',
        assistantMessageID: _asString(d['assistantMessageID']) ?? '',
        agent: _asString(d['agent']),
        model: Api2ModelRef.fromJson(d['model']),
        finish: _asString(d['finish']),
        error: Api2StructuredError.fromJson(d['error']),
        cost: _asDouble(d['cost']),
        tokens: d['tokens'] != null ? Api2Tokens.fromJson(d['tokens']) : null,
        snapshot: _asString(d['snapshot']),
      );

  static Api2SessionTextEvent _text(Api2Phase phase, Map<String, dynamic> d) =>
      Api2SessionTextEvent(
        phase: phase,
        sessionID: _asString(d['sessionID']) ?? '',
        assistantMessageID: _asString(d['assistantMessageID']) ?? '',
        ordinal: _asInt(d['ordinal']) ?? 0,
        delta: _asString(d['delta']),
        text: _asString(d['text']),
      );

  static Api2SessionReasoningEvent _reasoning(
    Api2Phase phase,
    Map<String, dynamic> d,
  ) => Api2SessionReasoningEvent(
    phase: phase,
    sessionID: _asString(d['sessionID']) ?? '',
    assistantMessageID: _asString(d['assistantMessageID']) ?? '',
    ordinal: _asInt(d['ordinal']) ?? 0,
    delta: _asString(d['delta']),
    text: _asString(d['text']),
  );

  static Api2SessionToolInputEvent _toolInput(
    Api2Phase phase,
    Map<String, dynamic> d,
  ) => Api2SessionToolInputEvent(
    phase: phase,
    sessionID: _asString(d['sessionID']) ?? '',
    assistantMessageID: _asString(d['assistantMessageID']) ?? '',
    callID: _asString(d['id']) ?? '',
    name: _asString(d['name']),
    delta: _asString(d['delta']),
    text: _asString(d['text']),
  );

  static Api2SessionToolResultEvent _toolResult(
    Api2Phase phase,
    Map<String, dynamic> d,
  ) => Api2SessionToolResultEvent(
    phase: phase,
    sessionID: _asString(d['sessionID']) ?? '',
    assistantMessageID: _asString(d['assistantMessageID']) ?? '',
    callID: _asString(d['id']) ?? '',
    content: _mapList(d['content'], Api2ToolResultItem.fromJson),
    error: Api2StructuredError.fromJson(d['error']),
    metadata: _asMap(d['metadata']),
    executed: _asBool(d['executed']),
  );

  static Api2SessionCompactionEvent _compaction(
    Api2Phase phase,
    Map<String, dynamic> d,
  ) => Api2SessionCompactionEvent(
    phase: phase,
    sessionID: _asString(d['sessionID']) ?? '',
    reason: _asString(d['reason']),
    text: _asString(d['text']),
    error: Api2StructuredError.fromJson(d['error']),
  );
}

/// Freshness only: shell lifecycle events are not a durable job log.
class Api2ManagedShellEvent extends Api2Event {
  const Api2ManagedShellEvent({required this.type, required this.data});
  final String type;
  final Map<String, dynamic> data;
}

class Api2ServerConnectedEvent extends Api2Event {
  const Api2ServerConnectedEvent();
}

/// Replay/live boundary marker on the durable session log.
class Api2LogSyncedEvent extends Api2Event {
  final String? aggregateID;
  final int? seq;
  const Api2LogSyncedEvent({this.aggregateID, this.seq});
}

class Api2SessionCreatedEvent extends Api2Event {
  final String sessionID;
  final String? projectID;
  final String? parentID;
  final String? slug;
  final String? title;
  final String? agent;
  final Api2ModelRef? model;
  final Api2Location? location;
  final String? subpath;
  final String? version;
  const Api2SessionCreatedEvent({
    required this.sessionID,
    this.projectID,
    this.parentID,
    this.slug,
    this.title,
    this.agent,
    this.model,
    this.location,
    this.subpath,
    this.version,
  });
}

class Api2SessionRenamedEvent extends Api2Event {
  final String sessionID;
  final String title;
  const Api2SessionRenamedEvent({required this.sessionID, required this.title});
}

class Api2SessionDeletedEvent extends Api2Event {
  final String sessionID;
  const Api2SessionDeletedEvent({required this.sessionID});
}

class Api2SessionMovedEvent extends Api2Event {
  final String sessionID;
  final Api2Location? location;
  final String? projectID;
  final String? subpath;
  const Api2SessionMovedEvent({
    required this.sessionID,
    this.location,
    this.projectID,
    this.subpath,
  });
}

class Api2SessionForkedEvent extends Api2Event {
  final String sessionID;
  final String? parentID;
  final Api2ForkBoundary? boundary;
  const Api2SessionForkedEvent({
    required this.sessionID,
    this.parentID,
    this.boundary,
  });
}

class Api2SessionViewedEvent extends Api2Event {
  final String sessionID;
  final int? idle;
  const Api2SessionViewedEvent({required this.sessionID, this.idle});
}

class Api2SessionAgentSelectedEvent extends Api2Event {
  final String sessionID;
  final String agent;
  final String? previous;
  const Api2SessionAgentSelectedEvent({
    required this.sessionID,
    required this.agent,
    this.previous,
  });
}

class Api2SessionModelSelectedEvent extends Api2Event {
  final String sessionID;
  final Api2ModelRef? model;
  const Api2SessionModelSelectedEvent({required this.sessionID, this.model});
}

class Api2SessionUsageUpdatedEvent extends Api2Event {
  final String sessionID;
  final double cost;
  final Api2Tokens tokens;
  const Api2SessionUsageUpdatedEvent({
    required this.sessionID,
    required this.cost,
    required this.tokens,
  });
}

/// `session.execution.{started|succeeded|failed|interrupted}`.
class Api2SessionExecutionEvent extends Api2Event {
  final Api2Phase phase;
  final String sessionID;
  final Api2StructuredError? error;
  final String? reason;
  const Api2SessionExecutionEvent({
    required this.phase,
    required this.sessionID,
    this.error,
    this.reason,
  });

  bool get finished => phase != Api2Phase.started;
}

/// `session.inbox.{enqueued|delivered|cancelled|delivery.changed}`.
class Api2SessionInboxEvent extends Api2Event {
  final Api2Phase phase;
  final String sessionID;
  final String inboxID;
  final Api2InboxItem? item;
  final Api2Delivery? delivery;
  const Api2SessionInboxEvent({
    required this.phase,
    required this.sessionID,
    required this.inboxID,
    this.item,
    this.delivery,
  });
}

class Api2SessionInstructionsUpdatedEvent extends Api2Event {
  final String sessionID;
  final Map<String, dynamic> delta;
  final String? text;
  const Api2SessionInstructionsUpdatedEvent({
    required this.sessionID,
    this.delta = const {},
    this.text,
  });
}

class Api2SessionSyntheticEvent extends Api2Event {
  final String sessionID;
  final String text;
  final String? description;
  const Api2SessionSyntheticEvent({
    required this.sessionID,
    required this.text,
    this.description,
  });
}

class Api2SessionSkillActivatedEvent extends Api2Event {
  final String sessionID;
  final String? id;
  final String? name;
  final String? text;
  const Api2SessionSkillActivatedEvent({
    required this.sessionID,
    this.id,
    this.name,
    this.text,
  });
}

/// `session.shell.{started|ended}` — shell/output kept raw (Phase-2 surface).
class Api2SessionShellEvent extends Api2Event {
  final Api2Phase phase;
  final String sessionID;
  final Map<String, dynamic>? shell;
  final Map<String, dynamic>? output;
  const Api2SessionShellEvent({
    required this.phase,
    required this.sessionID,
    this.shell,
    this.output,
  });
}

/// `session.step.{started|streamed|ended|failed}`.
class Api2SessionStepEvent extends Api2Event {
  final Api2Phase phase;
  final String sessionID;
  final String assistantMessageID;
  final String? agent;
  final Api2ModelRef? model;
  final String? finish;
  final Api2StructuredError? error;
  final double? cost;
  final Api2Tokens? tokens;
  final String? snapshot;
  const Api2SessionStepEvent({
    required this.phase,
    required this.sessionID,
    required this.assistantMessageID,
    this.agent,
    this.model,
    this.finish,
    this.error,
    this.cost,
    this.tokens,
    this.snapshot,
  });
}

/// `session.text.{started|delta|ended}`; `ended` carries the full [text].
class Api2SessionTextEvent extends Api2Event {
  final Api2Phase phase;
  final String sessionID;
  final String assistantMessageID;
  final int ordinal;
  final String? delta;
  final String? text;
  const Api2SessionTextEvent({
    required this.phase,
    required this.sessionID,
    required this.assistantMessageID,
    required this.ordinal,
    this.delta,
    this.text,
  });
}

/// `session.reasoning.{started|delta|ended}`.
class Api2SessionReasoningEvent extends Api2Event {
  final Api2Phase phase;
  final String sessionID;
  final String assistantMessageID;
  final int ordinal;
  final String? delta;
  final String? text;
  const Api2SessionReasoningEvent({
    required this.phase,
    required this.sessionID,
    required this.assistantMessageID,
    required this.ordinal,
    this.delta,
    this.text,
  });
}

/// `session.tool.input.{started|delta|ended}` — raw JSON-argument text.
class Api2SessionToolInputEvent extends Api2Event {
  final Api2Phase phase;
  final String sessionID;
  final String assistantMessageID;
  final String callID;
  final String? name;
  final String? delta;
  final String? text;
  const Api2SessionToolInputEvent({
    required this.phase,
    required this.sessionID,
    required this.assistantMessageID,
    required this.callID,
    this.name,
    this.delta,
    this.text,
  });
}

class Api2SessionToolCalledEvent extends Api2Event {
  final String sessionID;
  final String assistantMessageID;
  final String callID;
  final Map<String, dynamic> input;
  final bool? executed;
  const Api2SessionToolCalledEvent({
    required this.sessionID,
    required this.assistantMessageID,
    required this.callID,
    this.input = const {},
    this.executed,
  });
}

class Api2SessionToolProgressEvent extends Api2Event {
  final String sessionID;
  final String assistantMessageID;
  final String callID;
  final Map<String, dynamic> metadata;
  const Api2SessionToolProgressEvent({
    required this.sessionID,
    required this.assistantMessageID,
    required this.callID,
    this.metadata = const {},
  });
}

/// `session.tool.{success|failed}` with the tool-result content array.
class Api2SessionToolResultEvent extends Api2Event {
  final Api2Phase phase;
  final String sessionID;
  final String assistantMessageID;
  final String callID;
  final List<Api2ToolResultItem> content;
  final Api2StructuredError? error;
  final Map<String, dynamic>? metadata;
  final bool? executed;
  const Api2SessionToolResultEvent({
    required this.phase,
    required this.sessionID,
    required this.assistantMessageID,
    required this.callID,
    this.content = const [],
    this.error,
    this.metadata,
    this.executed,
  });

  bool get succeeded => phase == Api2Phase.success;
}

class Api2SessionRetryScheduledEvent extends Api2Event {
  final String sessionID;
  final String? assistantMessageID;
  final int? attempt;
  final int? at;
  final Api2StructuredError? error;
  const Api2SessionRetryScheduledEvent({
    required this.sessionID,
    this.assistantMessageID,
    this.attempt,
    this.at,
    this.error,
  });
}

/// `session.compaction.{started|delta|ended|failed}`.
class Api2SessionCompactionEvent extends Api2Event {
  final Api2Phase phase;
  final String sessionID;
  final String? reason;
  final String? text;
  final Api2StructuredError? error;
  const Api2SessionCompactionEvent({
    required this.phase,
    required this.sessionID,
    this.reason,
    this.text,
    this.error,
  });
}

/// `session.revert.{staged|cleared|committed}`.
class Api2SessionRevertEvent extends Api2Event {
  final Api2Phase phase;
  final String sessionID;
  final Map<String, dynamic>? revert;
  final String? toMessageID;
  const Api2SessionRevertEvent({
    required this.phase,
    required this.sessionID,
    this.revert,
    this.toMessageID,
  });
}

class Api2SessionMessageContentUpdatedEvent extends Api2Event {
  final String sessionID;
  final String messageID;
  final List<Api2AssistantContent> content;
  const Api2SessionMessageContentUpdatedEvent({
    required this.sessionID,
    required this.messageID,
    this.content = const [],
  });
}

enum Api2SessionRunStatus {
  idle,
  busy,
  retry,
  unknown;

  static Api2SessionRunStatus parse(dynamic v) =>
      switch (v is String ? v : null) {
        'idle' => idle,
        'busy' => busy,
        'retry' => retry,
        _ => unknown,
      };
}

/// `session.status` (and the deprecated `session.idle`).
class Api2SessionStatusEvent extends Api2Event {
  final String sessionID;
  final Api2SessionRunStatus status;
  final int? attempt;
  final String? message;
  final int? next;
  final Map<String, dynamic> raw;
  const Api2SessionStatusEvent({
    required this.sessionID,
    required this.status,
    this.attempt,
    this.message,
    this.next,
    this.raw = const {},
  });
}

class Api2SessionCompactedEvent extends Api2Event {
  final String sessionID;
  const Api2SessionCompactedEvent({required this.sessionID});
}

class Api2PermissionAskedEvent extends Api2Event {
  final Api2PermissionRequest request;
  const Api2PermissionAskedEvent({required this.request});
}

class Api2PermissionRepliedEvent extends Api2Event {
  final String sessionID;
  final String requestID;
  final String? reply;
  const Api2PermissionRepliedEvent({
    required this.sessionID,
    required this.requestID,
    this.reply,
  });
}

class Api2FormCreatedEvent extends Api2Event {
  final Api2FormInfo form;
  const Api2FormCreatedEvent({required this.form});
}

class Api2FormRepliedEvent extends Api2Event {
  final String id;
  final String sessionID;
  final Map<String, dynamic> answer;
  const Api2FormRepliedEvent({
    required this.id,
    required this.sessionID,
    this.answer = const {},
  });
}

class Api2FormCancelledEvent extends Api2Event {
  final String id;
  final String sessionID;
  const Api2FormCancelledEvent({required this.id, required this.sessionID});
}

/// `mcp.{status|resources|tools}.changed`.
class Api2McpChangedEvent extends Api2Event {
  final String kind;
  final String? server;
  const Api2McpChangedEvent({required this.kind, this.server});
}

class Api2VcsBranchUpdatedEvent extends Api2Event {
  final String? branch;
  const Api2VcsBranchUpdatedEvent({this.branch});
}

class Api2FilesystemChangedEvent extends Api2Event {
  final String? file;
  final String? change;
  const Api2FilesystemChangedEvent({this.file, this.change});
}

class Api2TuiToastEvent extends Api2Event {
  final String? title;
  final String message;
  final String? variant;
  final int? duration;
  const Api2TuiToastEvent({
    this.title,
    required this.message,
    this.variant,
    this.duration,
  });
}

/// Catalog/config change ping meaning "refetch [topic]".
class Api2RefreshHintEvent extends Api2Event {
  final String topic;
  const Api2RefreshHintEvent({required this.topic});
}

/// Fallback for event types this client does not know; carries the raw JSON
/// so nothing on the stream can ever crash the client.
class UnknownApi2Event extends Api2Event {
  final String type;
  final Map<String, dynamic> raw;
  const UnknownApi2Event({required this.type, this.raw = const {}});
}
