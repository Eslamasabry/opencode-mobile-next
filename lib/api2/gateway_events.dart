/// Adapts the OpenCode 2 event stream onto the v1-shaped [EventEnvelope]
/// dispatch the app's state layer (`lib/state/connection.dart`) and chat
/// screen already understand.
///
/// The v2 stream is a projection: nothing sends full message objects, so the
/// adapter rebuilds the v1 `message.updated` / `message.part.updated` /
/// `message.part.delta` shapes from the fine-grained `session.step/text/
/// reasoning/tool.*` events. Event types with no v1 analogue (forms, inbox
/// management, shells, and other v2 surfaces keep explicit event names when
/// a product screen consumes them. Shell events are freshness hints, never
/// synthetic messages or a retained job history. Unused control events are ignored.
library;

import '../api/models.dart';
import '../domain/server_gateway.dart';
import 'events.dart';
import 'models.dart';
import 'sse2.dart';
import 'transport.dart';

/// Stateful v2 → v1 event adapter.
///
/// Keeps a bounded memory of live tool calls (name + parsed input) and step
/// start times so later events (`tool.progress`, `tool.success`,
/// `step.ended`) can emit complete v1 part/message shapes.
class Api2EventAdapter {
  static const _maxTracked = 256;

  final _toolCalls = <String, _ToolCall>{};
  final _stepCreated = <String, int>{};

  /// Adapts one v2 envelope into zero or more v1 envelopes.
  List<EventEnvelope> adapt(Api2EventEnvelope envelope) {
    final event = envelope.event;
    final created = envelope.created ?? DateTime.now().millisecondsSinceEpoch;
    switch (event) {
      case Api2ServerConnectedEvent():
        return [_env('server.connected', const {})];

      case Api2ManagedShellEvent():
        // Visible running-work surfaces reconcile from the scoped shell API.
        // Never turn these hints into synthetic messages or retained jobs.
        return [_env(event.type, event.data)];

      case Api2SessionShellEvent():
        return [
          _env('session.shell.changed', {'sessionID': event.sessionID}),
        ];

      case Api2SessionCreatedEvent():
        return [
          _env('session.created', {
            'info': {
              'id': event.sessionID,
              if (event.title != null) 'title': event.title,
              if (event.projectID != null) 'projectID': event.projectID,
              if (event.location?.directory != null)
                'directory': event.location!.directory,
              if (event.parentID != null) 'parentID': event.parentID,
              'workspaceID': event.location?.workspaceID,
              'path': event.subpath,
              'serverSelection': true,
              'model': event.model?.toJson(),
              'agent': event.agent,
              'time': {'created': created, 'updated': created},
            },
          }),
        ];

      case Api2SessionRenamedEvent():
        return [
          _env('session.metadata.updated', {
            'info': {'id': event.sessionID, 'title': event.title},
          }),
        ];

      case Api2SessionDeletedEvent():
        return [
          _env('session.deleted', {
            'info': {'id': event.sessionID},
          }),
        ];

      case Api2SessionMovedEvent():
        return [
          _env('session.metadata.updated', {
            'info': {
              'id': event.sessionID,
              'directory': event.location?.directory,
              'workspaceID': event.location?.workspaceID,
              'projectID': event.projectID,
              'path': event.subpath,
            },
          }),
        ];

      case Api2SessionModelSelectedEvent():
        if (event.model == null) return const [];
        return [
          _env('session.model.selected', {
            'sessionID': event.sessionID,
            'model': event.model?.toJson(),
          }),
        ];

      case Api2SessionAgentSelectedEvent():
        if (event.agent.isEmpty) return const [];
        return [
          _env('session.agent.selected', {
            'sessionID': event.sessionID,
            'agent': event.agent,
          }),
        ];

      case Api2SessionStatusEvent():
        if (envelope.type == 'session.idle' ||
            (envelope.type == 'session.status' &&
                event.status == Api2SessionRunStatus.idle)) {
          return [
            _env('session.idle', {'sessionID': event.sessionID}),
          ];
        }
        return [
          _env('session.status', {
            'sessionID': event.sessionID,
            'status': event.raw.isNotEmpty
                ? event.raw
                : {'type': event.status.name},
          }),
        ];

      case Api2SessionUsageUpdatedEvent():
        // No v1 analogue: v1 folds usage into `session.updated`, but that
        // would reset every other field of the stored session. The
        // controller merges this into the matching session in place.
        return [
          _env('session.usage.updated', {
            'sessionID': event.sessionID,
            'cost': event.cost,
            'tokens': _tokensJson(event.tokens),
          }),
        ];

      case Api2SessionRetryScheduledEvent():
        // Same shape the v1 status endpoint/event uses for retry backoff,
        // so the controller's `session.status` retry handling applies.
        return [
          _env('session.status', {
            'sessionID': event.sessionID,
            'status': {
              'type': 'retry',
              'attempt': event.attempt ?? 0,
              if (event.error?.message != null)
                'message': event.error!.message
              else if (event.error?.type != null)
                'message': event.error!.type,
              if (event.at != null) 'next': event.at,
            },
          }),
        ];

      case Api2SessionExecutionEvent():
        switch (event.phase) {
          case Api2Phase.started:
            return [_status(event.sessionID, 'busy')];
          case Api2Phase.failed:
            // session.error alone clears the busy flag and settles the
            // attention alert as an error; adding an idle event would
            // overwrite that settle with a "complete" one.
            return [
              _env('session.error', {
                'sessionID': event.sessionID,
                'error': {
                  'message':
                      event.error?.message ??
                      event.error?.type ??
                      'The session run failed',
                },
              }),
            ];
          default:
            // succeeded / interrupted → the v1 idle transition.
            return [
              _env('session.idle', {'sessionID': event.sessionID}),
            ];
        }

      case Api2SessionCompactedEvent():
        return [
          _env('session.compacted', {'sessionID': event.sessionID}),
        ];

      case Api2SessionInboxEvent():
        // Every inbox phase passes through under its v2 wire type
        // (`session.inbox.enqueued|delivered|cancelled|delivery.changed`,
        // props = the raw v2 data payload) so the pending-sends state in
        // `connection.dart` can track the strip. Additionally, the enqueue
        // of a user prompt keeps its v1 analogue: the server echoing the
        // new user message.
        final passthrough = _env(envelope.type, envelope.data);
        final item = event.item;
        if (event.phase != Api2Phase.enqueued ||
            item == null ||
            item.type != 'user') {
          return [passthrough];
        }
        final text = item.promptText ?? '';
        return [
          passthrough,
          _env('message.updated', {
            'info': {
              'id': event.inboxID,
              'sessionID': event.sessionID,
              'role': 'user',
              'time': {'created': item.timeCreated ?? created},
            },
          }),
          if (text.isNotEmpty)
            _env('message.part.updated', {
              'sessionID': event.sessionID,
              'part': {
                'id': 'text-0',
                'type': 'text',
                'text': text,
                'messageID': event.inboxID,
              },
            }),
        ];

      case Api2SessionStepEvent():
        switch (event.phase) {
          case Api2Phase.started:
            _remember(_stepCreated, event.assistantMessageID, created);
            return [
              _env('message.updated', {
                'info': _assistantInfo(event, created: created),
              }),
            ];
          case Api2Phase.ended:
            return [
              _env('message.updated', {
                'info': _assistantInfo(
                  event,
                  created: created,
                  completed: created,
                ),
              }),
            ];
          case Api2Phase.failed:
            return [
              _env('message.updated', {
                'info': _assistantInfo(
                  event,
                  created: created,
                  completed: created,
                  errorMessage:
                      event.error?.message ??
                      event.error?.type ??
                      'The model step failed',
                  errorName: event.error?.type,
                ),
              }),
            ];
          default:
            // `streamed` (provider body finished) has no v1 analogue.
            return const [];
        }

      case Api2SessionTextEvent():
        return _ordinalPart(
          event.phase,
          sessionID: event.sessionID,
          messageID: event.assistantMessageID,
          partID: 'text-${event.ordinal}',
          type: 'text',
          delta: event.delta,
          text: event.text,
        );

      case Api2SessionReasoningEvent():
        return _ordinalPart(
          event.phase,
          sessionID: event.sessionID,
          messageID: event.assistantMessageID,
          partID: 'reasoning-${event.ordinal}',
          type: 'reasoning',
          delta: event.delta,
          text: event.text,
        );

      case Api2SessionToolInputEvent():
        final key = _callKey(event.assistantMessageID, event.callID);
        switch (event.phase) {
          case Api2Phase.started:
            _remember(_toolCalls, key, _ToolCall(name: event.name ?? ''));
            return [
              _toolPart(
                event.sessionID,
                event.assistantMessageID,
                event.callID,
                {'status': 'pending', 'raw': ''},
              ),
            ];
          case Api2Phase.delta:
            return [
              _env('message.part.delta', {
                'sessionID': event.sessionID,
                'messageID': event.assistantMessageID,
                'partID': event.callID,
                'field': 'state.raw',
                'delta': event.delta ?? '',
              }),
            ];
          case Api2Phase.ended:
            return [
              _toolPart(
                event.sessionID,
                event.assistantMessageID,
                event.callID,
                {'status': 'pending', 'raw': event.text ?? ''},
              ),
            ];
          default:
            return const [];
        }

      case Api2SessionToolCalledEvent():
        final key = _callKey(event.assistantMessageID, event.callID);
        final call = _toolCalls[key] ?? const _ToolCall(name: '');
        _remember(
          _toolCalls,
          key,
          _ToolCall(name: call.name, input: event.input),
        );
        return [
          _toolPart(event.sessionID, event.assistantMessageID, event.callID, {
            'status': 'running',
            'input': event.input,
          }),
        ];

      case Api2SessionToolProgressEvent():
        final call =
            _toolCalls[_callKey(event.assistantMessageID, event.callID)];
        return [
          _toolPart(event.sessionID, event.assistantMessageID, event.callID, {
            'status': 'running',
            'input': call?.input ?? const <String, dynamic>{},
            'metadata': event.metadata,
          }),
        ];

      case Api2SessionToolResultEvent():
        final key = _callKey(event.assistantMessageID, event.callID);
        final call = _toolCalls.remove(key);
        final text = event.content
            .whereType<Api2ToolResultText>()
            .map((item) => item.text)
            .where((value) => value.isNotEmpty)
            .join('\n');
        final attachments = [
          for (final item in event.content.whereType<Api2ToolResultFile>())
            {
              'url': item.uri,
              if (item.mime != null) 'mime': item.mime,
              if (item.name != null) 'name': item.name,
            },
        ];
        return [
          _toolPart(event.sessionID, event.assistantMessageID, event.callID, {
            'status': event.succeeded ? 'completed' : 'error',
            'input': call?.input ?? const <String, dynamic>{},
            if (event.succeeded)
              'output': text
            else
              'error':
                  event.error?.message ??
                  (text.isNotEmpty ? text : 'Tool failed'),
            'attachments': attachments,
            if (event.metadata != null) 'metadata': event.metadata,
            if (event.executed != null) 'executed': event.executed,
          }, toolName: call?.name),
        ];

      case Api2SessionMessageContentUpdatedEvent():
        // A server-side message edit: re-emit every content item as a v1
        // part update using the same IDs the message mapper assigns.
        var textCount = 0;
        var reasoningCount = 0;
        final out = <EventEnvelope>[];
        for (final item in event.content) {
          switch (item) {
            case Api2TextContent():
              out.add(
                _env('message.part.updated', {
                  'sessionID': event.sessionID,
                  'part': {
                    'id': 'text-${textCount++}',
                    'type': 'text',
                    'text': item.text,
                    'messageID': event.messageID,
                  },
                }),
              );
            case Api2ReasoningContent():
              out.add(
                _env('message.part.updated', {
                  'sessionID': event.sessionID,
                  'part': {
                    'id': 'reasoning-${reasoningCount++}',
                    'type': 'reasoning',
                    'text': item.text,
                    'messageID': event.messageID,
                  },
                }),
              );
            case Api2ToolCallContent():
              out.add(
                _toolPart(
                  event.sessionID,
                  event.messageID,
                  item.id,
                  _rawToolStateJson(item.state),
                  toolName: item.name,
                ),
              );
            case Api2UnknownContent():
              break;
          }
        }
        return out;

      case Api2PermissionAskedEvent():
        final request = event.request;
        return [
          _env('permission.v2.asked', {
            'id': request.id,
            'sessionID': request.sessionID,
            'action': request.action,
            'resources': request.resources,
            'save': request.save,
            if (request.message != null) 'message': request.message,
            if (request.metadata != null) 'metadata': request.metadata,
            if (request.source != null)
              'source': {
                if (request.source!.type != null) 'type': request.source!.type,
                if (request.source!.messageID != null)
                  'messageID': request.source!.messageID,
                if (request.source!.id != null) 'id': request.source!.id,
                // The v1 PermissionTool parser reads `callID`.
                if (request.source!.id != null) 'callID': request.source!.id,
              },
          }),
        ];

      case Api2PermissionRepliedEvent():
        return [
          _env('permission.v2.replied', {
            'sessionID': event.sessionID,
            'requestID': event.requestID,
            if (event.reply != null) 'reply': event.reply,
          }),
        ];

      // Forms surface under `form.v2.*` envelopes consumed by the pending-
      // form state in `connection.dart`: `form.v2.created` carries the raw
      // `{form: Form.Info}` payload; replied/cancelled carry `{id,
      // sessionID}` (answer omitted — the client only settles the form).
      case Api2FormCreatedEvent():
        return [_env('form.v2.created', envelope.data)];

      case Api2FormRepliedEvent():
        return [
          _env('form.v2.replied', {
            'id': event.id,
            'sessionID': event.sessionID,
          }),
        ];

      case Api2FormCancelledEvent():
        return [
          _env('form.v2.cancelled', {
            'id': event.id,
            'sessionID': event.sessionID,
          }),
        ];

      case Api2RefreshHintEvent():
        switch (event.topic) {
          case 'agent.updated':
            return [_env('agent.updated', const {})];
          case 'config.updated':
            return [_env('config.updated', const {})];
          case 'catalog.updated':
          case 'models-dev.refreshed':
            return [_env('catalog.updated', const {})];
          case 'integration.updated':
          case 'credential.updated':
          case 'credential.switched':
            return [_env('integration.connection.updated', const {})];
          default:
            return const [];
        }

      case UnknownApi2Event():
        // Pass-through for the handful of v1-named types the dispatcher
        // handles that the typed v2 union does not model.
        switch (envelope.type) {
          case 'installation.updated':
          case 'installation.update-available':
          case 'pty.created':
          case 'pty.updated':
          case 'pty.exited':
          case 'pty.deleted':
          case 'worktree.ready':
          case 'worktree.failed':
          case 'workspace.ready':
          case 'workspace.failed':
            return [_env(envelope.type, envelope.data)];
          default:
            return const [];
        }

      default:
        return const [];
    }
  }

  EventEnvelope _env(String type, Map<String, dynamic> properties) =>
      EventEnvelope(type: type, properties: properties);

  EventEnvelope _status(String sessionID, String type) =>
      _env('session.status', {
        'sessionID': sessionID,
        'status': {'type': type},
      });

  Map<String, dynamic> _assistantInfo(
    Api2SessionStepEvent event, {
    required int created,
    int? completed,
    String? errorMessage,
    String? errorName,
  }) => {
    'id': event.assistantMessageID,
    'sessionID': event.sessionID,
    'role': 'assistant',
    if (event.agent != null) 'agent': event.agent,
    if (event.model != null) 'providerID': event.model!.providerID,
    if (event.model != null) 'modelID': event.model!.id,
    if (event.cost != null) 'cost': event.cost,
    if (event.tokens != null) 'tokens': _tokensJson(event.tokens!),
    if (event.finish != null) 'finish': event.finish,
    'time': {
      'created': _stepCreated[event.assistantMessageID] ?? created,
      'completed': ?completed,
    },
    if (errorMessage != null)
      'error': {'message': errorMessage, 'name': ?errorName},
  };

  Map<String, dynamic> _tokensJson(Api2Tokens tokens) => {
    'input': tokens.input,
    'output': tokens.output,
    'reasoning': tokens.reasoning,
    'cache': {'read': tokens.cacheRead, 'write': tokens.cacheWrite},
  };

  List<EventEnvelope> _ordinalPart(
    Api2Phase phase, {
    required String sessionID,
    required String messageID,
    required String partID,
    required String type,
    String? delta,
    String? text,
  }) {
    switch (phase) {
      case Api2Phase.started:
        return [
          _env('message.part.updated', {
            'sessionID': sessionID,
            'part': {
              'id': partID,
              'type': type,
              'text': '',
              'messageID': messageID,
            },
          }),
        ];
      case Api2Phase.delta:
        return [
          _env('message.part.delta', {
            'sessionID': sessionID,
            'messageID': messageID,
            'partID': partID,
            'field': 'text',
            'delta': delta ?? '',
          }),
        ];
      case Api2Phase.ended:
        return [
          _env('message.part.updated', {
            'sessionID': sessionID,
            'part': {
              'id': partID,
              'type': type,
              'text': text ?? '',
              'messageID': messageID,
            },
          }),
        ];
      default:
        return const [];
    }
  }

  EventEnvelope _toolPart(
    String sessionID,
    String messageID,
    String callID,
    Map<String, dynamic> state, {
    String? toolName,
  }) {
    final name =
        toolName ?? _toolCalls[_callKey(messageID, callID)]?.name ?? '';
    return _env('message.part.updated', {
      'sessionID': sessionID,
      'part': {
        'id': callID,
        'callID': callID,
        'type': 'tool',
        'messageID': messageID,
        if (name.isNotEmpty) 'tool': name,
        'state': state,
      },
    });
  }

  Map<String, dynamic> _rawToolStateJson(Api2ToolState state) =>
      switch (state) {
        Api2ToolStreaming() => {'status': 'pending', 'raw': state.rawInput},
        Api2ToolRunning() => {
          'status': 'running',
          'input': state.input,
          if (state.metadata != null) 'metadata': state.metadata,
        },
        Api2ToolCompleted() => {
          'status': 'completed',
          'input': state.input,
          'output': state.textOutput,
          if (state.metadata != null) 'metadata': state.metadata,
        },
        Api2ToolError() => {
          'status': 'error',
          'input': state.input,
          'error': state.error?.message ?? 'Tool failed',
          if (state.metadata != null) 'metadata': state.metadata,
        },
        Api2ToolStateUnknown() => {'status': 'pending'},
      };

  String _callKey(String messageID, String callID) => '$messageID/$callID';

  void _remember<T>(Map<String, T> store, String key, T value) {
    store.remove(key);
    store[key] = value;
    while (store.length > _maxTracked) {
      store.remove(store.keys.first);
    }
  }
}

class _ToolCall {
  final String name;
  final Map<String, dynamic>? input;
  const _ToolCall({required this.name, this.input});
}

/// One live subscription to `GET /api/event`, surfaced through the domain
/// [LiveEventChannel] contract.
///
/// The location-scoped channel filters events client-side to the pinned
/// directory (v2 has a single stream; events carry `location`). The global
/// channel passes everything through and stamps the v1 envelope's
/// `directory`/`workspace` from the v2 event location.
class Api2LiveEventChannel implements LiveEventChannel {
  final Api2EventStream _stream;

  Api2LiveEventChannel({
    required Api2Transport transport,
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
    String? Function()? directoryFilter,
    bool global = false,
  }) : _stream = _buildStream(
         transport: transport,
         onEvent: onEvent,
         onStatus: onStatus,
         onError: onError,
         directoryFilter: directoryFilter,
         global: global,
       );

  static Api2EventStream _buildStream({
    required Api2Transport transport,
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
    String? Function()? directoryFilter,
    required bool global,
  }) {
    final adapter = Api2EventAdapter();
    return Api2EventStream(
      transport: transport,
      onEvent: (envelope, _) {
        final eventDirectory = envelope.location?.directory;
        if (!global) {
          final pinned = directoryFilter?.call();
          if (pinned != null &&
              eventDirectory != null &&
              eventDirectory != pinned) {
            return;
          }
        }
        for (final adapted in adapter.adapt(envelope)) {
          onEvent(
            global
                ? EventEnvelope(
                    type: adapted.type,
                    properties: adapted.properties,
                    directory: eventDirectory,
                    project: envelope.location?.project?.id,
                    workspace: envelope.location?.workspaceID,
                  )
                : adapted,
          );
        }
      },
      onStatus: (status) => onStatus(switch (status) {
        Api2StreamStatus.connecting => StreamStatus.connecting,
        Api2StreamStatus.connected => StreamStatus.connected,
        Api2StreamStatus.reconnecting => StreamStatus.reconnecting,
        Api2StreamStatus.disconnected => StreamStatus.disconnected,
      }),
      onError: onError,
    );
  }

  @override
  void start() => _stream.start();

  @override
  Future<void> dispose() => _stream.dispose();
}

/// Test seam: adapt a raw v2 wire JSON event without a live stream.
List<EventEnvelope> adaptApi2EventJson(
  Api2EventAdapter adapter,
  Map<String, dynamic> json,
) {
  final envelope = Api2EventEnvelope.fromJson(json);
  return adapter.adapt(envelope);
}
