// Decodes every OpenCode 2 fixture with the Flutter client's own parsers
// (lib/api2/models.dart, lib/api2/events.dart, lib/domain/usage_statistics.dart)
// and writes neutral descriptors to ../parity/dart-variants.json.
//
// The Kotlin port (:protocol:oc2, Oc2ParityTest) builds the same descriptors
// from its own DTOs and must match this file exactly (numbers compare by value).
//
// Run from the repository root with a plain Dart SDK (no Flutter needed):
//   native/fixtures/oc2/tool/dart_variants.sh
//
// Descriptor rules: null fields are omitted; variant tags are the Dart class
// names without the `Api2` prefix and `Event`/`Message`/`Content` suffixes.
import 'dart:convert';
import 'dart:io';

import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/domain/usage_statistics.dart';

const fixtureRoot = 'native/fixtures/oc2/flutter';

void main() {
  final out = <String, dynamic>{};
  dynamic load(String rel) =>
      jsonDecode(File('$fixtureRoot/$rel').readAsStringSync());
  Map<String, dynamic> data(String rel) =>
      Map<String, dynamic>.from((load(rel) as Map)['data'] as Map);
  List<Map<String, dynamic>> dataList(String rel) => [
    for (final e in (load(rel) as Map)['data'] as List)
      Map<String, dynamic>.from(e as Map),
  ];

  // --- sessions
  out['api2/sessions_page.json'] = page(
    Api2Page.fromJson(load('api2/sessions_page.json'), Api2Session.fromJson),
    session,
  );
  out['api2/session_create.json'] = session(
    Api2Session.fromJson(data('api2/session_create.json')),
  );
  // --- messages
  for (final name in [
    'messages.json',
    'messages_2_1_1.json',
    'messages_page1.json',
    'messages_page2.json',
    'messages_variants.json',
  ]) {
    out['api2/$name'] = page(
      Api2Page.fromJson(load('api2/$name'), Api2Message.fromJson),
      message,
    );
  }
  out['api2/message_assistant.json'] = message(
    Api2Message.fromJson(data('api2/message_assistant.json')),
  );
  // --- inbox
  out['api2/prompt_receipt.json'] = inbox(
    Api2InboxItem.fromJson(data('api2/prompt_receipt.json')),
  );
  out['api2/inbox_list.json'] = [
    for (final j in dataList('api2/inbox_list.json'))
      inbox(Api2InboxItem.fromJson(j)),
  ];
  // --- forms
  out['api2/forms_session.json'] = [
    for (final j in dataList('api2/forms_session.json'))
      form(Api2FormInfo.fromJson(j)),
  ];
  out['api2/form_state_pending.json'] = formState(
    Api2FormState.fromJson(
      (load('api2/form_state_pending.json') as Map)['data'],
    ),
  );
  // --- catalog
  out['api2/models_sample.json'] = [
    for (final j in dataList('api2/models_sample.json'))
      modelInfo(Api2ModelInfo.fromJson(j)),
  ];
  out['api2/model_default.json'] = modelInfo(
    Api2ModelInfo.fromJson(data('api2/model_default.json')),
  );
  out['api2/providers.json'] = [
    for (final j in dataList('api2/providers.json'))
      provider(Api2ProviderInfo.fromJson(j)),
  ];
  out['api2/agents.json'] = [
    for (final j in dataList('api2/agents.json'))
      agent(Api2AgentInfo.fromJson(j)),
  ];
  out['api2/commands.json'] = [
    for (final j in dataList('api2/commands.json'))
      command(Api2Command.fromJson(j)),
  ];
  out['api2/skills_sample.json'] = [
    for (final j in dataList('api2/skills_sample.json'))
      skill(Api2Skill.fromJson(j)),
  ];
  // --- location and fs
  out['api2/location.json'] = location(
    Api2Location.fromJson(load('api2/location.json')),
  );
  out['api2/fs_list.json'] = [
    for (final j in dataList('api2/fs_list.json'))
      fsEntry(Api2FsEntry.fromJson(j)),
  ];
  // --- errors: the extraction in Api2Transport.mapError (transport.dart
  // imports Flutter, so the two expressions are copied here verbatim).
  for (final name in [
    'error_not_found.json',
    'error_form_invalid_answer.json',
  ]) {
    final body = Map<String, dynamic>.from(load('api2/$name') as Map);
    out['api2/$name'] = clean({
      'tag': body['_tag']?.toString() ?? body['code']?.toString(),
      'message': body['message']?.toString(),
    });
  }
  // --- usage
  out['api2/session_stats.json'] = usage(
    UsageStatistics.fromJson(data('api2/session_stats.json')),
  );
  // --- events
  final lines = File('$fixtureRoot/api2/events.sse').readAsLinesSync();
  out['api2/events.sse'] = [
    for (final line in lines)
      if (line.startsWith('data:'))
        envelope(
          Api2EventEnvelope.fromJson(
            jsonDecode(line.substring(5).trim()) as Map<String, dynamic>,
          ),
        ),
  ];
  // --- coverage sample sets
  final events = load('coverage/oc2_events_samples.json') as Map;
  out['coverage/oc2_events_samples.json'] = {
    for (final c in events['cases'] as List)
      c['id'] as String: [
        for (final e in c['payload'] as List)
          envelope(
            Api2EventEnvelope.fromJson(Map<String, dynamic>.from(e as Map)),
          ),
      ],
  };
  final messages = load('coverage/oc2_messages_samples.json') as Map;
  out['coverage/oc2_messages_samples.json'] = {
    for (final c in messages['cases'] as List)
      c['id'] as String: [
        for (final m in c['payload'] as List)
          message(Api2Message.fromJson(Map<String, dynamic>.from(m as Map))),
      ],
  };

  final file = File('native/fixtures/oc2/parity/dart-variants.json');
  file.writeAsStringSync(
    '${const JsonEncoder.withIndent('  ').convert(sortKeys(out))}\n',
  );
  stdout.writeln('wrote ${file.path}: ${out.length} fixtures');
}

// ---------------------------------------------------------------- helpers

dynamic sortKeys(dynamic v) {
  if (v is Map) {
    final keys = v.keys.map((k) => k.toString()).toList()..sort();
    return {for (final k in keys) k: sortKeys(v[k])};
  }
  if (v is List) return [for (final e in v) sortKeys(e)];
  return v;
}

Map<String, dynamic> clean(Map<String, dynamic> m) =>
    Map.fromEntries(m.entries.where((e) => e.value != null));

Map<String, dynamic>? page<T>(Api2Page<T> p, dynamic Function(T) describe) =>
    clean({
      'data': [for (final e in p.data) describe(e)],
      'previous': p.previousCursor,
      'next': p.nextCursor,
    });

String? ref(Api2ModelRef? r) => r?.toString();

Map<String, dynamic> tokens(Api2Tokens t) => {
  'input': t.input,
  'output': t.output,
  'reasoning': t.reasoning,
  'cacheRead': t.cacheRead,
  'cacheWrite': t.cacheWrite,
};

Map<String, dynamic>? error(Api2StructuredError? e) => e == null
    ? null
    : clean({'type': e.type, 'message': e.message, 'status': e.status});

Map<String, dynamic>? location(Api2Location? l) => l == null
    ? null
    : clean({
        'directory': l.directory,
        'workspaceID': l.workspaceID,
        'projectID': l.project?.id,
        'projectDirectory': l.project?.directory,
        'canonical': l.project?.canonical,
      });

Map<String, dynamic>? session(Api2Session? s) => s == null
    ? null
    : clean({
        'id': s.id,
        'parentID': s.parentID,
        'fork': s.fork == null
            ? null
            : clean({
                'sessionID': s.fork!.sessionID,
                'boundary': s.fork!.boundary?.toJson(),
              }),
        'projectID': s.projectID,
        'agent': s.agent,
        'model': ref(s.model),
        'cost': s.cost,
        'tokens': tokens(s.tokens),
        'outcome': s.outcome?.name,
        'time': clean({
          'created': s.time.created,
          'updated': s.time.updated,
          'idle': s.time.idle,
          'viewed': s.time.viewed,
          'archived': s.time.archived,
        }),
        'title': s.title,
        'location': location(s.location),
        'subpath': s.subpath,
        'reverted': s.reverted,
        'revert': s.revert == null
            ? null
            : clean({
                'messageID': s.revert!.messageID,
                'partID': s.revert!.partID,
                'snapshot': s.revert!.snapshot,
                'files': s.revert!.files?.length,
              }),
        'metadata': s.metadata,
        'archived': s.archived,
      });

Map<String, dynamic> resultItem(Api2ToolResultItem i) => switch (i) {
  Api2ToolResultText() => {'v': 'text', 'text': i.text},
  Api2ToolResultFile() => clean({
    'v': 'file',
    'uri': i.uri,
    'mime': i.mime,
    'name': i.name,
  }),
  Api2ToolResultUnknown() => {'v': 'unknown'},
};

Map<String, dynamic> toolState(Api2ToolState s) => switch (s) {
  Api2ToolStreaming() => {'v': 'streaming', 'rawInput': s.rawInput},
  Api2ToolRunning() => clean({
    'v': 'running',
    'input': s.input,
    'metadata': s.metadata,
  }),
  Api2ToolCompleted() => clean({
    'v': 'completed',
    'input': s.input,
    'items': [for (final i in s.content) resultItem(i)],
    'metadata': s.metadata,
  }),
  Api2ToolError() => clean({
    'v': 'error',
    'input': s.input,
    'error': error(s.error),
    'items': [for (final i in s.content) resultItem(i)],
    'metadata': s.metadata,
  }),
  Api2ToolStateUnknown() => {'v': 'unknown'},
};

Map<String, dynamic>? contentTime(Api2ContentTime? t) => t == null
    ? null
    : clean({
        'created': t.created,
        'ran': t.ran,
        'completed': t.completed,
        'pruned': t.pruned,
      });

Map<String, dynamic> content(Api2AssistantContent c) => switch (c) {
  Api2TextContent() => clean({'v': 'text', 'text': c.text, 'state': c.state}),
  Api2ReasoningContent() => clean({
    'v': 'reasoning',
    'text': c.text,
    'state': c.state,
    'time': contentTime(c.time),
  }),
  Api2ToolCallContent() => clean({
    'v': 'tool',
    'id': c.id,
    'name': c.name,
    'executed': c.executed,
    'state': toolState(c.state),
    'time': contentTime(c.time),
  }),
  Api2UnknownContent() => {'v': 'unknown', 'type': c.raw['type']?.toString()},
};

Map<String, dynamic>? message(Api2Message? m) {
  if (m == null) return null;
  final base = <String, dynamic>{
    'id': m.id,
    'time': clean({
      'created': m.time.created,
      'streamed': m.time.streamed,
      'completed': m.time.completed,
    }),
    'metadata': m.metadata,
  };
  final variant = switch (m) {
    Api2UserMessage() => {
      'v': 'User',
      'text': m.text,
      'files': [
        for (final f in m.files)
          clean({
            'data': f.data,
            'mime': f.mime,
            'sourceType': f.sourceType,
            'sourceUri': f.sourceUri,
            'name': f.name,
            'description': f.description,
          }),
      ],
    },
    Api2AssistantMessage() => {
      'v': 'Assistant',
      'agent': m.agent,
      'model': ref(m.model),
      'content': [for (final c in m.content) content(c)],
      'finish': m.finish,
      'rawFinish': m.rawFinish,
      'cost': m.cost,
      'tokens': m.tokens == null ? null : tokens(m.tokens!),
      'error': error(m.error),
      'completed': m.completed,
      'text': m.text,
    },
    Api2SyntheticMessage() => {
      'v': 'Synthetic',
      'text': m.text,
      'description': m.description,
    },
    Api2SystemMessage() => {
      'v': 'System',
      'text': m.text,
      'description': m.description,
    },
    Api2SkillMessage() => {
      'v': 'Skill',
      'skill': m.skill,
      'name': m.name,
      'text': m.text,
    },
    Api2ShellMessage() => {
      'v': 'Shell',
      'shellID': m.shellID,
      'command': m.command,
      'status': m.status,
      'exit': m.exit,
      'output': m.output,
      'outputTruncated': m.outputTruncated,
    },
    Api2AgentSwitchedMessage() => {
      'v': 'AgentSwitched',
      'agent': m.agent,
      'previous': m.previous,
    },
    Api2ModelSwitchedMessage() => {
      'v': 'ModelSwitched',
      'model': ref(m.model),
      'previous': ref(m.previous),
    },
    Api2LocationSwitchedMessage() => {
      'v': 'LocationSwitched',
      'location': location(m.location),
      'projectID': m.projectID,
      'subpath': m.subpath,
    },
    Api2CompactionMessage() => {
      'v': 'Compaction',
      'status': m.status,
      'reason': m.reason,
      'summary': m.summary,
      'error': error(m.error),
    },
    Api2UnknownMessage() => {'v': 'Unknown', 'type': m.type},
  };
  return clean({...base, ...variant});
}

Map<String, dynamic>? inbox(Api2InboxItem? i) => i == null
    ? null
    : clean({
        'id': i.id,
        'sessionID': i.sessionID,
        'timeCreated': i.timeCreated,
        'type': i.type,
        'payload': i.payload,
        'delivery': i.delivery?.name,
        'promptText': i.promptText,
      });

Map<String, dynamic>? form(Api2FormInfo? f) => f == null
    ? null
    : clean({
        'id': f.id,
        'sessionID': f.sessionID,
        'title': f.title,
        'metadata': f.metadata,
        'fields': [
          for (final x in f.fields)
            clean({
              'key': x.key,
              'type': x.type.name,
              'title': x.title,
              'description': x.description,
              'required': x.required,
              'when': [
                for (final w in x.when)
                  clean({'key': w.key, 'op': w.op, 'value': w.value}),
              ],
              'format': x.format,
              'minLength': x.minLength,
              'maxLength': x.maxLength,
              'pattern': x.pattern,
              'placeholder': x.placeholder,
              'default': x.defaultValue,
              'options': [
                for (final o in x.options)
                  clean({
                    'value': o.value,
                    'label': o.label,
                    'description': o.description,
                  }),
              ],
              'custom': x.custom,
              'minimum': x.minimum,
              'maximum': x.maximum,
              'minItems': x.minItems,
              'maxItems': x.maxItems,
              'url': x.url,
            }),
        ],
      });

Map<String, dynamic> formState(Api2FormState s) =>
    clean({'status': s.status.name, 'answer': s.answer});

Map<String, dynamic>? permissionRequest(Api2PermissionRequest? r) => r == null
    ? null
    : clean({
        'id': r.id,
        'sessionID': r.sessionID,
        'action': r.action,
        'resources': r.resources,
        'save': r.save,
        'metadata': r.metadata,
        'source': r.source == null
            ? null
            : clean({
                'type': r.source!.type,
                'messageID': r.source!.messageID,
                'id': r.source!.id,
              }),
        'message': r.message,
      });

Map<String, dynamic>? modelInfo(Api2ModelInfo? m) => m == null
    ? null
    : clean({
        'id': m.id,
        'modelID': m.modelID,
        'providerID': m.providerID,
        'family': m.family,
        'name': m.name,
        'status': m.status,
        'enabled': m.enabled,
        'tools': m.capabilities.tools,
        'input': m.capabilities.input,
        'output': m.capabilities.output,
        'variants': m.variants,
        'limit': clean({
          'context': m.limit.context,
          'input': m.limit.input,
          'output': m.limit.output,
        }),
        'released': m.released,
        'cost': [
          for (final c in m.cost)
            clean({
              'input': c.input,
              'output': c.output,
              'cacheRead': c.cacheRead,
              'cacheWrite': c.cacheWrite,
              'tierSize': c.tierSize,
            }),
        ],
        'ref': m.ref().toString(),
      });

Map<String, dynamic>? provider(Api2ProviderInfo? p) => p == null
    ? null
    : clean({
        'id': p.id,
        'integrationID': p.integrationID,
        'name': p.name,
        'activation': p.activation,
        'package': p.package,
      });

Map<String, dynamic>? agent(Api2AgentInfo? a) => a == null
    ? null
    : clean({
        'id': a.id,
        'name': a.name,
        'description': a.description,
        'mode': a.mode,
        'hidden': a.hidden,
        'color': a.color,
        'model': ref(a.model),
        'selectable': a.selectable,
        'permissions': [
          for (final p in a.permissions)
            clean({
              'action': p.action,
              'resource': p.resource,
              'effect': p.effect?.name,
            }),
        ],
      });

Map<String, dynamic>? command(Api2Command? c) =>
    c == null ? null : clean({'name': c.name, 'description': c.description});

Map<String, dynamic>? skill(Api2Skill? s) => s == null
    ? null
    : clean({
        'id': s.id,
        'name': s.name,
        'description': s.description,
        'location': s.location,
        'contentLength': s.content?.length,
        'slash': s.slash,
      });

Map<String, dynamic>? fsEntry(Api2FsEntry? e) => e == null
    ? null
    : {'path': e.path, 'type': e.type, 'isDirectory': e.isDirectory};

Map<String, dynamic> usageTokens(UsageTokens t) => {
  'input': t.input,
  'output': t.output,
  'reasoning': t.reasoning,
  'cacheRead': t.cacheRead,
  'cacheWrite': t.cacheWrite,
};

Map<String, dynamic> usage(UsageStatistics u) => clean({
  'from': u.from,
  'to': u.to,
  'sessions': u.sessions,
  'subagents': u.subagents,
  'prompts': u.prompts,
  'steps': u.steps,
  'activeDays': u.activeDays,
  'streak': u.streak,
  'cost': u.cost,
  'tokens': usageTokens(u.tokens),
  'tools': u.tools == null
      ? null
      : {
          'calls': u.tools!.calls,
          'succeeded': u.tools!.succeeded,
          'failed': u.tools!.failed,
          'unfinished': u.tools!.unfinished,
        },
  'models': [
    for (final m in u.models)
      clean({
        'providerID': m.providerID,
        'modelID': m.modelID,
        'variant': m.variant,
        'steps': m.steps,
        'tokens': usageTokens(m.tokens),
        'cost': m.cost,
      }),
  ],
  'activity': [
    for (final a in u.activity) {'date': a.date, 'steps': a.steps},
  ],
});

// ---------------------------------------------------------------- events

Map<String, dynamic> envelope(Api2EventEnvelope e) => clean({
  'type': e.type,
  'id': e.id,
  'created': e.created,
  'location': location(e.location),
  'metadata': e.metadata,
  'durable': e.durable == null
      ? null
      : clean({
          'aggregateID': e.durable!.aggregateID,
          'seq': e.durable!.seq,
          'version': e.durable!.version,
        }),
  'event': event(e.event),
});

Map<String, dynamic> event(Api2Event e) => clean(switch (e) {
  Api2ServerConnectedEvent() => {'v': 'ServerConnected'},
  Api2ManagedShellEvent() => {
    'v': 'ManagedShell',
    'type': e.type,
    'data': e.data,
  },
  Api2LogSyncedEvent() => {
    'v': 'LogSynced',
    'aggregateID': e.aggregateID,
    'seq': e.seq,
  },
  Api2SessionCreatedEvent() => {
    'v': 'SessionCreated',
    'sessionID': e.sessionID,
    'projectID': e.projectID,
    'parentID': e.parentID,
    'slug': e.slug,
    'title': e.title,
    'agent': e.agent,
    'model': ref(e.model),
    'location': location(e.location),
    'subpath': e.subpath,
    'version': e.version,
  },
  Api2SessionRenamedEvent() => {
    'v': 'SessionRenamed',
    'sessionID': e.sessionID,
    'title': e.title,
  },
  Api2SessionDeletedEvent() => {
    'v': 'SessionDeleted',
    'sessionID': e.sessionID,
  },
  Api2SessionMovedEvent() => {
    'v': 'SessionMoved',
    'sessionID': e.sessionID,
    'location': location(e.location),
    'projectID': e.projectID,
    'subpath': e.subpath,
  },
  Api2SessionForkedEvent() => {
    'v': 'SessionForked',
    'sessionID': e.sessionID,
    'parentID': e.parentID,
    'boundary': e.boundary?.toJson(),
  },
  Api2SessionViewedEvent() => {
    'v': 'SessionViewed',
    'sessionID': e.sessionID,
    'idle': e.idle,
  },
  Api2SessionAgentSelectedEvent() => {
    'v': 'SessionAgentSelected',
    'sessionID': e.sessionID,
    'agent': e.agent,
    'previous': e.previous,
  },
  Api2SessionModelSelectedEvent() => {
    'v': 'SessionModelSelected',
    'sessionID': e.sessionID,
    'model': ref(e.model),
  },
  Api2SessionUsageUpdatedEvent() => {
    'v': 'SessionUsageUpdated',
    'sessionID': e.sessionID,
    'cost': e.cost,
    'tokens': tokens(e.tokens),
  },
  Api2SessionExecutionEvent() => {
    'v': 'SessionExecution',
    'phase': e.phase.name,
    'sessionID': e.sessionID,
    'error': error(e.error),
    'reason': e.reason,
  },
  Api2SessionInboxEvent() => {
    'v': 'SessionInbox',
    'phase': e.phase.name,
    'sessionID': e.sessionID,
    'inboxID': e.inboxID,
    'item': inbox(e.item),
    'delivery': e.delivery?.name,
  },
  Api2SessionInstructionsUpdatedEvent() => {
    'v': 'SessionInstructionsUpdated',
    'sessionID': e.sessionID,
    'delta': e.delta,
    'text': e.text,
  },
  Api2SessionSyntheticEvent() => {
    'v': 'SessionSynthetic',
    'sessionID': e.sessionID,
    'text': e.text,
    'description': e.description,
  },
  Api2SessionSkillActivatedEvent() => {
    'v': 'SessionSkillActivated',
    'sessionID': e.sessionID,
    'id': e.id,
    'name': e.name,
    'text': e.text,
  },
  Api2SessionShellEvent() => {
    'v': 'SessionShell',
    'phase': e.phase.name,
    'sessionID': e.sessionID,
    'shell': e.shell,
    'output': e.output,
  },
  Api2SessionStepEvent() => {
    'v': 'SessionStep',
    'phase': e.phase.name,
    'sessionID': e.sessionID,
    'assistantMessageID': e.assistantMessageID,
    'agent': e.agent,
    'model': ref(e.model),
    'finish': e.finish,
    'error': error(e.error),
    'cost': e.cost,
    'tokens': e.tokens == null ? null : tokens(e.tokens!),
    'snapshot': e.snapshot,
  },
  Api2SessionTextEvent() => {
    'v': 'SessionText',
    'phase': e.phase.name,
    'sessionID': e.sessionID,
    'assistantMessageID': e.assistantMessageID,
    'ordinal': e.ordinal,
    'delta': e.delta,
    'text': e.text,
  },
  Api2SessionReasoningEvent() => {
    'v': 'SessionReasoning',
    'phase': e.phase.name,
    'sessionID': e.sessionID,
    'assistantMessageID': e.assistantMessageID,
    'ordinal': e.ordinal,
    'delta': e.delta,
    'text': e.text,
  },
  Api2SessionToolInputEvent() => {
    'v': 'SessionToolInput',
    'phase': e.phase.name,
    'sessionID': e.sessionID,
    'assistantMessageID': e.assistantMessageID,
    'callID': e.callID,
    'name': e.name,
    'delta': e.delta,
    'text': e.text,
  },
  Api2SessionToolCalledEvent() => {
    'v': 'SessionToolCalled',
    'sessionID': e.sessionID,
    'assistantMessageID': e.assistantMessageID,
    'callID': e.callID,
    'input': e.input,
    'executed': e.executed,
  },
  Api2SessionToolProgressEvent() => {
    'v': 'SessionToolProgress',
    'sessionID': e.sessionID,
    'assistantMessageID': e.assistantMessageID,
    'callID': e.callID,
    'metadata': e.metadata,
  },
  Api2SessionToolResultEvent() => {
    'v': 'SessionToolResult',
    'phase': e.phase.name,
    'sessionID': e.sessionID,
    'assistantMessageID': e.assistantMessageID,
    'callID': e.callID,
    'items': [for (final i in e.content) resultItem(i)],
    'error': error(e.error),
    'metadata': e.metadata,
    'executed': e.executed,
  },
  Api2SessionRetryScheduledEvent() => {
    'v': 'SessionRetryScheduled',
    'sessionID': e.sessionID,
    'assistantMessageID': e.assistantMessageID,
    'attempt': e.attempt,
    'at': e.at,
    'error': error(e.error),
  },
  Api2SessionCompactionEvent() => {
    'v': 'SessionCompaction',
    'phase': e.phase.name,
    'sessionID': e.sessionID,
    'reason': e.reason,
    'text': e.text,
    'error': error(e.error),
  },
  Api2SessionRevertEvent() => {
    'v': 'SessionRevert',
    'phase': e.phase.name,
    'sessionID': e.sessionID,
    'revert': e.revert,
    'toMessageID': e.toMessageID,
  },
  Api2SessionMessageContentUpdatedEvent() => {
    'v': 'SessionMessageContentUpdated',
    'sessionID': e.sessionID,
    'messageID': e.messageID,
    'content': [for (final c in e.content) content(c)],
  },
  Api2SessionStatusEvent() => {
    'v': 'SessionStatus',
    'sessionID': e.sessionID,
    'status': e.status.name,
    'attempt': e.attempt,
    'message': e.message,
    'next': e.next,
  },
  Api2SessionCompactedEvent() => {
    'v': 'SessionCompacted',
    'sessionID': e.sessionID,
  },
  Api2PermissionAskedEvent() => {
    'v': 'PermissionAsked',
    'request': permissionRequest(e.request),
  },
  Api2PermissionRepliedEvent() => {
    'v': 'PermissionReplied',
    'sessionID': e.sessionID,
    'requestID': e.requestID,
    'reply': e.reply,
  },
  Api2FormCreatedEvent() => {'v': 'FormCreated', 'form': form(e.form)},
  Api2FormRepliedEvent() => {
    'v': 'FormReplied',
    'id': e.id,
    'sessionID': e.sessionID,
    'answer': e.answer,
  },
  Api2FormCancelledEvent() => {
    'v': 'FormCancelled',
    'id': e.id,
    'sessionID': e.sessionID,
  },
  Api2McpChangedEvent() => {
    'v': 'McpChanged',
    'kind': e.kind,
    'server': e.server,
  },
  Api2VcsBranchUpdatedEvent() => {'v': 'VcsBranchUpdated', 'branch': e.branch},
  Api2FilesystemChangedEvent() => {
    'v': 'FilesystemChanged',
    'file': e.file,
    'change': e.change,
  },
  Api2TuiToastEvent() => {
    'v': 'TuiToast',
    'title': e.title,
    'message': e.message,
    'variant': e.variant,
    'duration': e.duration,
  },
  Api2RefreshHintEvent() => {'v': 'RefreshHint', 'topic': e.topic},
  UnknownApi2Event() => {'v': 'Unknown', 'type': e.type},
});
