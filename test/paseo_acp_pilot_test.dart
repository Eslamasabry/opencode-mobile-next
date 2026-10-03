import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

const _cwd = '/work/acp-pilot';
const _fakeSecret = 'synthetic-provider-credential-do-not-display';
const _timestamp = '2026-10-02T08:00:00.000Z';

/// Scripted Paseo v0.9.2 wire peer. Fixtures follow the pinned
/// packages/protocol/src/agent-types.ts and messages.ts schemas, including
/// enabled/source, snapshot capabilities and ACP metadata.options. They do
/// not invent provider-snapshot ACP/auth/resume fields.
class FakePaseoSocket implements PaseoSocket {
  final _incoming = StreamController<Object?>.broadcast();
  final sent = <Map<String, dynamic>>[];
  final handlers =
      <
        String,
        (String, Map<String, dynamic>)? Function(Map<String, dynamic>)
      >{};
  bool _closed = false;

  @override
  int? closeCode;
  @override
  Stream<Object?> get messages => _incoming.stream;

  @override
  void send(String message) {
    final envelope = jsonDecode(message) as Map<String, dynamic>;
    if (envelope['type'] == 'hello') {
      push('status', {'status': 'server_info', 'version': '0.9.2'});
      return;
    }
    if (envelope['type'] == 'ping') return;
    final request = envelope['message'] as Map<String, dynamic>;
    sent.add(request);
    final response = handlers[request['type']]?.call(request);
    if (response != null) {
      reply(request, response.$1, response.$2);
    }
  }

  void reply(
    Map<String, dynamic> request,
    String type,
    Map<String, dynamic> payload,
  ) => push(type, {...payload, 'requestId': request['requestId']});

  void push(String type, Map<String, dynamic> payload) {
    scheduleMicrotask(() {
      if (!_closed) {
        _incoming.add(
          jsonEncode({
            'type': 'session',
            'message': {'type': type, 'payload': payload},
          }),
        );
      }
    });
  }

  List<Map<String, dynamic>> of(String type) =>
      sent.where((message) => message['type'] == type).toList();

  @override
  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    closeCode = 1000;
    await _incoming.close();
  }
}

Map<String, dynamic> _provider(
  String id, {
  String status = 'ready',
  bool enabled = true,
  String source = 'builtin',
  String? error,
}) => {
  'provider': id,
  'status': status,
  'enabled': enabled,
  'source': source,
  'label': '$id host label',
  'models': [
    {
      'provider': id,
      'id': '$id-model',
      'label': '$id model',
      'isDefault': true,
    },
  ],
  'modes': [
    {'id': 'default', 'label': 'Default'},
  ],
  'defaultModeId': 'default',
  'fetchedAt': _timestamp,
  'error': ?error,
};

Map<String, dynamic> _snapshot(
  List<Map<String, dynamic>> entries, {
  String cwd = _cwd,
}) => {'cwd': cwd, 'entries': entries, 'generatedAt': _timestamp};

Map<String, dynamic> _agent(
  String provider, {
  String id = 'existing',
  String cwd = _cwd,
  bool persisted = true,
}) => {
  'id': id,
  'provider': provider,
  'cwd': cwd,
  'model': '$provider-model',
  'title': 'Existing conversation',
  'status': 'idle',
  'createdAt': _timestamp,
  'updatedAt': _timestamp,
  'lastUserMessageAt': _timestamp,
  // These daemon flags are real wire fields, but ACPAdapter's defaults are
  // hardcoded true. They are not runtime loadSession/list negotiation proof.
  'capabilities': {
    'supportsStreaming': true,
    'supportsSessionPersistence': true,
    'supportsSessionListing': true,
    'supportsDynamicModes': true,
    'supportsMcpServers': true,
    'supportsReasoningStream': true,
    'supportsToolInvocations': true,
  },
  'currentModeId': 'default',
  'availableModes': [
    {'id': 'default', 'label': 'Default'},
  ],
  'pendingPermissions': <Object>[],
  'persistence': persisted
      ? {'provider': provider, 'sessionId': 'native-$id'}
      : null,
  'labels': <String, String>{},
  'archivedAt': null,
};

Map<String, dynamic> _option(String id, String kind) => {
  'optionId': id,
  'name': id,
  'kind': kind,
};

List<Map<String, dynamic>> _onceAndStanding() => [
  _option('allow-this-call', 'allow_once'),
  _option('allow-future-calls', 'allow_always'),
  _option('reject-this-call', 'reject_once'),
  _option('reject-future-calls', 'reject_always'),
];

Map<String, dynamic> _permission(
  List<Map<String, dynamic>> options, {
  String id = 'approval',
}) => {
  'id': id,
  'provider': 'gemini',
  'name': 'Write',
  'kind': 'tool',
  'title': 'Write project file',
  'detail': {
    'type': 'write',
    'filePath': '$_cwd/result.txt',
    'content': 'hello',
  },
  'actions': [
    for (final option in options)
      {
        'id': option['optionId'],
        'label': option['name'],
        'behavior': (option['kind'] as String).startsWith('allow')
            ? 'allow'
            : 'deny',
      },
  ],
  'metadata': {'toolCallId': 'tool-call', 'options': options},
};

void main() {
  late FakePaseoSocket daemon;
  late FakePaseoSocket replacement;
  late PaseoGateway gateway;
  late List<EventEnvelope> events;
  late List<Map<String, dynamic>> entries;
  late Map<String, dynamic> agent;
  late int savedWarmupAttempts;
  late Duration savedWarmupInterval;

  HostAgentProviderGateway providers() => gateway as HostAgentProviderGateway;
  HostAgentPermissionGateway permissions() =>
      gateway as HostAgentPermissionGateway;

  setUp(() {
    savedWarmupAttempts = providerWarmupAttempts;
    savedWarmupInterval = providerWarmupInterval;
    providerWarmupAttempts = 1;
    providerWarmupInterval = Duration.zero;
    daemon = FakePaseoSocket();
    replacement = FakePaseoSocket();
    entries = [
      _provider('claude'),
      _provider('gemini'),
      _provider('omp'),
      _provider('fx'),
    ];
    agent = _agent('gemini');
    daemon.handlers['get_providers_snapshot_request'] = (request) => (
      'get_providers_snapshot_response',
      _snapshot(entries, cwd: request['cwd'] as String),
    );
    daemon.handlers['refresh_providers_snapshot_request'] = (_) =>
        ('refresh_providers_snapshot_response', {'acknowledged': true});
    daemon.handlers['fetch_agents_request'] = (_) => (
      'fetch_agents_response',
      {
        'entries': [
          {'agent': agent},
        ],
        'pageInfo': {'nextCursor': null, 'prevCursor': null, 'hasMore': false},
      },
    );
    daemon.handlers['fetch_agent_request'] = (_) =>
        ('fetch_agent_response', {'agent': agent});
    daemon.handlers['send_agent_message_request'] = (_) => (
      'send_agent_message_response',
      {'agentId': agent['id'], 'accepted': true, 'error': null},
    );
    var created = 0;
    daemon.handlers['create_agent_request'] = (request) {
      created++;
      final config = request['config'] as Map;
      agent = _agent(config['provider'] as String, id: 'created-$created');
      return ('create_agent_response', {'agent': agent});
    };
    daemon.handlers['cancel_agent_request'] = (_) => (
      'cancel_agent_response',
      {'agentId': agent['id'], 'agent': agent, 'error': null},
    );
    daemon.handlers['fetch_agent_timeline_request'] = (_) => (
      'fetch_agent_timeline_response',
      {'agentId': agent['id'], 'entries': <Object>[]},
    );
    var connections = 0;
    gateway = PaseoGateway(
      directory: _cwd,
      transport: PaseoTransport(
        endpoint: 'ws://100.64.0.20:6767',
        socketFactory: (_, _) async =>
            connections++ == 0 ? daemon : replacement,
      ),
    );
    events = [];
    // Listen without starting background recovery: each test owns its requests.
    gateway.openEventChannel(onEvent: events.add, onStatus: (_) {});
  });

  tearDown(() async {
    providerWarmupAttempts = savedWarmupAttempts;
    providerWarmupInterval = savedWarmupInterval;
    gateway.close();
    await daemon.close();
    await replacement.close();
  });

  Future<void> ask(
    List<Map<String, dynamic>> options, {
    String requestId = 'approval',
  }) async {
    await gateway.sessions();
    daemon.push('agent_permission_request', {
      'agentId': agent['id'],
      'request': _permission(options, id: requestId),
    });
    await pumpEventQueue();
  }

  test(
    'discovery gates are additive and ready agents are selectable',
    () async {
      expect(const ServerCapabilities().hostAgentProviders, isFalse);
      expect(const ServerCapabilities().hostAgentPermissionActions, isFalse);
      expect(gateway.capabilities.hostAgentProviders, isTrue);
      expect(gateway.capabilities.hostAgentPermissionActions, isTrue);
      expect(
        (await providers().loadHostAgentProviders()).selectable,
        hasLength(4),
      );
    },
  );

  test(
    'ready agents can start new chats without inventing restoration proof',
    () async {
      final catalog = await providers().loadHostAgentProviders();
      for (final id in ['gemini', 'omp', 'fx']) {
        final provider = catalog.providers.singleWhere((row) => row.id == id);
        expect(provider.selectable, true);
        expect(provider.hiddenReason, isNull);
        expect(provider.resumeSupport.name, 'unknown');
        expect(provider.resumeLabel, "Can't reopen old chats");
        expect(provider.resumeNote, 'Starts a new chat');
      }
      expect((await gateway.providers()).providers.map((row) => row.id), [
        'claude',
        'gemini',
        'omp',
        'fx',
      ]);
      final draft = await gateway.createSession();
      await gateway.promptAsync(
        draft.id,
        text: 'start',
        model: ModelRef(providerID: 'gemini', modelID: 'gemini-model'),
      );
      expect(
        (daemon.of('create_agent_request').single['config'] as Map)['provider'],
        'gemini',
      );
      expect(
        (await providers().loadHostAgentContinuation(draft.id)).state,
        HostAgentContinuationState.liveSession,
      );
      await gateway.promptAsync(draft.id, text: 'follow up');
      expect(
        daemon.of('send_agent_message_request').single['agentId'],
        'created-1',
      );
    },
  );

  test(
    'newly visible ACP rows use catalog names rather than host labels',
    () async {
      entries = [_provider('qwen'), _provider('goose')];
      final catalog = await providers().loadHostAgentProviders();
      expect(catalog.selectable.map((row) => row.displayName), [
        'Qwen Code',
        'Goose',
      ]);
      expect((await gateway.providers()).providers.map((row) => row.name), [
        'Qwen Code',
        'Goose',
      ]);
      expect(catalog.providers.every((row) => !row.resumeVerified), true);
    },
  );

  test(
    'undeclared capability fields cannot fabricate runtime resume proof',
    () async {
      entries = [
        {
          ..._provider('gemini'),
          'capabilities': {
            'loadSession': true,
            'sessionCapabilities': {'list': {}},
          },
          'loadSession': true,
          'type': 'acp',
        },
        _provider('custom-agent', source: 'custom'),
      ];
      final catalog = await providers().loadHostAgentProviders();
      expect(catalog.selectable, hasLength(2));
      expect(catalog.providers.every((row) => !row.resumeVerified), true);
      expect(
        catalog.providers.every(
          (row) => row.resumeLabel == "Can't reopen old chats",
        ),
        true,
      );
      expect((await gateway.providers()).providers.map((row) => row.id), [
        'gemini',
        'custom-agent',
      ]);
    },
  );

  test(
    'known host auth failure has safe sign-in state without exposing raw error',
    () async {
      entries = [_provider('gemini', status: 'error', error: 'AuthRequired')];
      final catalog = await providers().loadHostAgentProviders();
      final row = catalog.providers.single;
      expect(row.availability.name, 'needsHostSignIn');
      expect(row.hiddenReason, isNull);
      expect(row.loginState.name, 'needsHostSignIn');
      expect(catalog.selectable, isEmpty);
      expect(events, isEmpty);
      entries = [_provider('gemini', status: 'error', error: _fakeSecret)];
      final refreshed = await providers().loadHostAgentProviders(refresh: true);
      expect(refreshed.selectable, isEmpty);
      expect(
        jsonEncode(events.map((event) => event.properties).toList()),
        isNot(contains(_fakeSecret)),
      );
    },
  );

  test('disabled and loading providers cannot enter the picker', () async {
    entries = [
      _provider('gemini', enabled: false),
      _provider('fx', status: 'loading'),
    ];
    final catalog = await providers().loadHostAgentProviders();
    expect(catalog.selectable, isEmpty);
    expect(
      catalog.providers
          .singleWhere((row) => row.id == 'gemini')
          .hiddenReason
          ?.name,
      'disabled',
    );
    expect(
      catalog.providers.singleWhere((row) => row.id == 'fx').availability.name,
      'checking',
    );
    expect((await gateway.providers()).providers, isEmpty);
  });

  test(
    'explicit refresh rechecks host snapshot and invalidates native picker cache',
    () async {
      final original = await gateway.providers();
      expect(original.providers.map((row) => row.id), [
        'claude',
        'gemini',
        'omp',
        'fx',
      ]);
      entries = [_provider('gemini')];
      await providers().loadHostAgentProviders(refresh: true);
      expect(daemon.of('refresh_providers_snapshot_request'), hasLength(1));
      expect((await gateway.providers()).providers.single.id, 'gemini');
      expect(
        daemon.of('get_providers_snapshot_request').length,
        greaterThanOrEqualTo(2),
      );
    },
  );

  test(
    'a catalog response for a retired project never fills the next project cache',
    () async {
      daemon.handlers['get_providers_snapshot_request'] = (_) => null;
      final stale = providers().loadHostAgentProviders();
      await pumpEventQueue();
      final request = daemon.of('get_providers_snapshot_request').single;
      final rejected = expectLater(stale, throwsA(isA<PaseoFailure>()));
      gateway.setLocation(directory: '/work/other');
      daemon.reply(
        request,
        'get_providers_snapshot_response',
        _snapshot(entries),
      );
      await rejected;
      daemon.handlers['get_providers_snapshot_request'] = (_) => (
        'get_providers_snapshot_response',
        _snapshot([_provider('pi')], cwd: '/work/other'),
      );
      final fresh = await providers().loadHostAgentProviders();
      expect(fresh.providers.map((row) => row.id), ['pi']);
    },
  );

  test('permission choices expose only offered one-call actions', () async {
    await ask(_onceAndStanding());
    final pending = (await permissions().pendingHostAgentPermissions()).single;
    expect(pending.requestId, 'approval');
    expect(pending.sessionId, 'existing');
    expect(pending.choices.map((choice) => choice.actionId), [
      'allow-this-call',
      'reject-this-call',
    ]);
    expect(pending.choices.map((choice) => choice.behavior.name), [
      'allowOnce',
      'rejectOnce',
    ]);
  });

  test(
    'allow once echoes its exact offered ID and writes no standing approval',
    () async {
      await ask(_onceAndStanding());
      await permissions().respondHostAgentPermission(
        'approval',
        selectedActionId: 'allow-this-call',
      );
      expect(daemon.of('agent_permission_response').single['response'], {
        'behavior': 'allow',
        'selectedActionId': 'allow-this-call',
      });
      expect(await permissions().pendingHostAgentPermissions(), isEmpty);
      await expectLater(
        permissions().respondHostAgentPermission(
          'approval',
          selectedActionId: 'allow-this-call',
        ),
        throwsA(isA<PaseoFailure>()),
      );
      expect(daemon.of('agent_permission_response'), hasLength(1));
    },
  );

  test('default denial selects the offered reject-once ID', () async {
    await ask(_onceAndStanding());
    await permissions().respondHostAgentPermission('approval');
    expect(daemon.of('agent_permission_response').single['response'], {
      'behavior': 'deny',
      'selectedActionId': 'reject-this-call',
    });
  });

  test(
    'without reject once denial interrupts instead of falling back to always',
    () async {
      await ask([
        _option('allow-future-calls', 'allow_always'),
        _option('reject-future-calls', 'reject_always'),
      ]);
      await permissions().respondHostAgentPermission('approval');
      expect(daemon.of('agent_permission_response'), isEmpty);
      expect(daemon.of('cancel_agent_request').single['agentId'], 'existing');
    },
  );

  test(
    'idle cancel acknowledgement cannot hide an unanswered permission',
    () async {
      final offered = [_option('deny-future', 'reject_always')];
      await ask(offered);
      agent['pendingPermissions'] = [_permission(offered)];
      await expectLater(
        permissions().respondHostAgentPermission('approval'),
        throwsA(isA<PaseoFailure>()),
      );
      expect(
        (await permissions().pendingHostAgentPermissions()).single.requestId,
        'approval',
      );
      expect(daemon.of('agent_permission_response'), isEmpty);
    },
  );

  test('old cancel acknowledgement cannot retire a new project card', () async {
    final offered = [_option('deny-future', 'reject_always')];
    await ask(offered);
    daemon.handlers['cancel_agent_request'] = (_) => null;
    final cancel = permissions().respondHostAgentPermission('approval');
    final rejected = expectLater(cancel, throwsA(isA<PaseoFailure>()));
    await pumpEventQueue();
    final request = daemon.of('cancel_agent_request').single;
    gateway.setLocation(directory: '/work/other');
    gateway.setLocation(directory: _cwd);
    await ask(offered);
    daemon.reply(request, 'cancel_agent_response', {
      'agentId': 'existing',
      'agent': agent,
      'error': null,
    });
    await rejected;
    expect(
      (await permissions().pendingHostAgentPermissions()).single.requestId,
      'approval',
    );
    expect(daemon.of('agent_permission_response'), isEmpty);
  });

  test('existing Copilot route remains in the normal picker', () async {
    entries = [_provider('copilot')];
    expect((await gateway.providers()).providers.single.id, 'copilot');
  });

  test('custom override cannot reuse an existing picker identity', () async {
    entries = [_provider('claude', source: 'custom')];
    expect((await gateway.providers()).providers, isEmpty);
    final draft = await gateway.createSession();
    await expectLater(
      gateway.promptAsync(draft.id, text: 'hello'),
      throwsA(isA<PaseoFailure>()),
    );
    expect(daemon.of('create_agent_request'), isEmpty);
  });

  test(
    'contradictory action behavior cannot grant an ACP permission',
    () async {
      await gateway.sessions();
      final request = _permission(_onceAndStanding());
      (request['actions'] as List).first['behavior'] = 'deny';
      daemon.push('agent_permission_request', {
        'agentId': 'existing',
        'request': request,
      });
      await pumpEventQueue();
      expect(
        (await permissions().pendingHostAgentPermissions()).single.choices,
        isEmpty,
      );
      await expectLater(
        permissions().respondHostAgentPermission(
          'approval',
          selectedActionId: 'allow-this-call',
        ),
        throwsA(isA<PaseoFailure>()),
      );
      expect(daemon.of('agent_permission_response'), isEmpty);
    },
  );

  test(
    'an ACP request cannot claim a native provider to bypass action mapping',
    () async {
      await gateway.sessions();
      final request = _permission(_onceAndStanding());
      request['provider'] = 'claude';
      request['metadata'] = {};
      request['suggestions'] = [
        {'type': 'setMode', 'mode': 'acceptEdits'},
      ];
      daemon.push('agent_permission_request', {
        'agentId': 'existing',
        'request': request,
      });
      await pumpEventQueue();
      expect(
        (await permissions().pendingHostAgentPermissions()).single.choices,
        isEmpty,
      );
      expect((await gateway.pendingPermissions()).single.always, isEmpty);
      await expectLater(
        gateway.respondPermission('approval', 'once'),
        throwsA(isA<PaseoFailure>()),
      );
      await expectLater(
        gateway.respondPermission('approval', 'always'),
        throwsA(isA<PaseoFailure>()),
      );
      expect(daemon.of('agent_permission_response'), isEmpty);
    },
  );

  test(
    'ACP card keeps structured task preview without raw host copy',
    () async {
      await gateway.sessions();
      final request = _permission(_onceAndStanding());
      request['title'] = _fakeSecret;
      request['description'] = _fakeSecret;
      request['input'] = {'credentials': _fakeSecret};
      (request['metadata'] as Map)['rawRequest'] = {'secret': _fakeSecret};
      daemon.push('agent_permission_request', {
        'agentId': 'existing',
        'request': request,
      });
      await pumpEventQueue();
      final card = (await gateway.pendingPermissions()).single;
      expect(card.permission, 'edit');
      expect(card.metadata['filePath'], '$_cwd/result.txt');
      expect(card.metadata['diff'], 'hello');
      expect(card.always, isEmpty);
      expect(
        jsonEncode(events.map((event) => event.properties).toList()),
        isNot(contains(_fakeSecret)),
      );
    },
  );

  test('unoffered or standing action IDs can never grant permission', () async {
    for (final selected in ['not-offered', 'allow-future-calls']) {
      final requestId = 'approval-$selected';
      await ask(_onceAndStanding(), requestId: requestId);
      try {
        await permissions().respondHostAgentPermission(
          requestId,
          selectedActionId: selected,
        );
      } on PaseoFailure {
        // Rejecting stale/unavailable selections before writing is also safe.
      }
      expect(
        daemon
            .of('agent_permission_response')
            .every(
              (message) => (message['response'] as Map)['behavior'] != 'allow',
            ),
        true,
      );
    }
  });

  test('an unknown ACP option kind cannot become an allow action', () async {
    await ask([_option('future-choice', 'allow_future')]);
    expect(
      (await permissions().pendingHostAgentPermissions()).single.choices,
      isEmpty,
    );
    await permissions().respondHostAgentPermission('approval');
    expect(daemon.of('agent_permission_response'), isEmpty);
    expect(daemon.of('cancel_agent_request').single['agentId'], 'existing');
  });

  test(
    'disconnected permissions cannot be replied through a replacement connection',
    () async {
      await ask(_onceAndStanding());
      await daemon.close();
      await pumpEventQueue();
      await expectLater(
        permissions().respondHostAgentPermission(
          'approval',
          selectedActionId: 'allow-this-call',
        ),
        throwsA(isA<PaseoFailure>()),
      );
      expect(replacement.of('agent_permission_response'), isEmpty);
      expect(daemon.of('agent_permission_response'), isEmpty);
    },
  );

  test(
    'restart state is scoped domain data and never promises restoration',
    () async {
      final unverified = await providers().loadHostAgentContinuation(
        'existing',
      );
      expect(unverified.providerId, 'gemini');
      expect(unverified.state, HostAgentContinuationState.resumeUnverified);
      expect(unverified.blocked, isTrue);
      expect(unverified.reason, "Can't reopen old chats");
      expect(unverified.resumeNote, 'Starts a new chat');
      gateway.setLocation(directory: '/work/other');
      await expectLater(
        providers().loadHostAgentContinuation('existing'),
        throwsA(isA<PaseoFailure>()),
      );
    },
  );

  test('missing native handle has honest restart wording', () async {
    agent = _agent('gemini', persisted: false);
    final missing = await providers().loadHostAgentContinuation('existing');
    expect(missing.state, HostAgentContinuationState.missingHandle);
    expect(missing.blocked, isTrue);
    expect(missing.reason, "Can't reopen old chats");
  });

  test(
    'reopened ACP chat requires warning before starting a new chat',
    () async {
      await gateway.sessions();
      await expectLater(
        gateway.promptAsync('existing', text: 'continue'),
        throwsA(isA<PaseoFailure>()),
      );
      expect(daemon.of('send_agent_message_request'), isEmpty);
      expect(daemon.of('create_agent_request'), isEmpty);
    },
  );

  test(
    'missing ACP persistence cannot silently start a replacement conversation',
    () async {
      agent = _agent('gemini', persisted: false);
      await gateway.sessions();
      await expectLater(
        gateway.promptAsync('existing', text: 'continue'),
        throwsA(isA<PaseoFailure>()),
      );
      expect(daemon.of('send_agent_message_request'), isEmpty);
      expect(daemon.of('create_agent_request'), isEmpty);
    },
  );

  test(
    'missing native handle also requires explicit new-chat action',
    () async {
      agent = _agent('claude', persisted: false);
      await gateway.sessions();
      expect(
        (await providers().loadHostAgentContinuation(
          'existing',
        )).requiresNewChat,
        true,
      );
      await expectLater(
        gateway.promptAsync('existing', text: 'continue'),
        throwsA(
          isA<PaseoFailure>().having(
            (e) => e.kind,
            'kind',
            PaseoFailureKind.newChatRequired,
          ),
        ),
      );
      expect(daemon.of('send_agent_message_request'), isEmpty);
      expect(daemon.of('create_agent_request'), isEmpty);
    },
  );

  test(
    'new chat after resume warning requires explicit acknowledgement and a different ID',
    () async {
      final originalAgent = Map<String, dynamic>.from(agent);
      daemon.handlers['fetch_agent_request'] = (request) => (
        'fetch_agent_response',
        {
          'agent': request['agentId'] == originalAgent['id']
              ? originalAgent
              : agent,
        },
      );
      await gateway.sessions();
      await expectLater(
        providers().startNewHostAgentChat(
          'existing',
          newChatAcknowledged: false,
        ),
        throwsA(
          isA<PaseoFailure>().having(
            (e) => e.kind,
            'kind',
            PaseoFailureKind.newChatRequired,
          ),
        ),
      );
      expect(daemon.of('create_agent_request'), isEmpty);
      expect(daemon.of('send_agent_message_request'), isEmpty);
      final next = await providers().startNewHostAgentChat(
        'existing',
        newChatAcknowledged: true,
      );
      expect(next, isNot('existing'));
      expect(gateway.providerIdForSession(next), 'gemini');
      expect(daemon.of('send_agent_message_request'), isEmpty);
      await gateway.promptAsync(next, text: 'explicit new chat');
      expect(daemon.of('create_agent_request'), hasLength(1));
      expect(
        daemon.of('create_agent_request').single['initialPrompt'],
        'explicit new chat',
      );
      expect((await gateway.session('existing')).id, 'existing');
    },
  );

  for (final unavailable in [
    _provider('gemini', enabled: false),
    _provider('gemini', status: 'error', error: 'not installed'),
    _provider('gemini', error: 'not installed'),
    _provider('gemini', error: 'AuthRequired'),
    _provider('gemini', status: 'loading'),
  ]) {
    test(
      'direct admission still refuses disabled, missing, signed-out or loading provider: ${unavailable['status']}/${unavailable['enabled']}/${unavailable['error']}',
      () async {
        entries = [unavailable];
        expect((await gateway.providers()).providers, isEmpty);
        final draft = await gateway.createSession();
        await expectLater(
          gateway.promptAsync(
            draft.id,
            text: 'start',
            model: ModelRef(providerID: 'gemini', modelID: 'gemini-model'),
          ),
          throwsA(isA<PaseoFailure>()),
        );
        expect(daemon.of('create_agent_request'), isEmpty);
        expect(daemon.of('send_agent_message_request'), isEmpty);
        await expectLater(
          providers().startNewHostAgentChat(
            'existing',
            newChatAcknowledged: true,
          ),
          throwsA(isA<PaseoFailure>()),
        );
      },
    );
  }

  test(
    'disconnect retires live ACP admission and requires a visible new-chat action',
    () async {
      final draft = await gateway.createSession();
      await gateway.promptAsync(
        draft.id,
        text: 'start',
        model: ModelRef(providerID: 'gemini', modelID: 'gemini-model'),
      );
      replacement.handlers['get_providers_snapshot_request'] = (request) =>
          ('get_providers_snapshot_response', _snapshot(entries));
      await daemon.close();
      await pumpEventQueue();
      await expectLater(
        gateway.promptAsync(draft.id, text: 'after restart'),
        throwsA(
          isA<PaseoFailure>().having(
            (e) => e.kind,
            'kind',
            PaseoFailureKind.newChatRequired,
          ),
        ),
      );
      expect(replacement.of('send_agent_message_request'), isEmpty);
      expect(replacement.of('create_agent_request'), isEmpty);
      final continuation = await providers().loadHostAgentContinuation(
        draft.id,
      );
      expect(continuation.requiresNewChat, true);
      expect(continuation.resumeNote, 'Starts a new chat');
    },
  );

  test(
    'future custom host agent is selectable and callable with no resume proof',
    () async {
      entries = [_provider('future-agent', source: 'custom')];
      final row = (await providers().loadHostAgentProviders()).providers.single;
      expect(row.selectable, true);
      expect(row.resumeLabel, "Can't reopen old chats");
      final draft = await gateway.createSession();
      gateway.seedDraftProviderForSession(draft.id, 'future-agent');
      await gateway.promptAsync(draft.id, text: 'hello');
      expect(
        (daemon.of('create_agent_request').single['config'] as Map)['provider'],
        'future-agent',
      );
    },
  );

  test(
    'raw agent errors and diagnostic text never reach events or history',
    () async {
      await gateway.sessions();
      daemon.push('agent_stream', {
        'agentId': 'existing',
        'event': {
          'type': 'turn_failed',
          'provider': 'gemini',
          'error': _fakeSecret,
        },
        'timestamp': _timestamp,
      });
      daemon.push('provider_diagnostic_response', {
        'provider': 'gemini',
        'diagnostic': _fakeSecret,
        'requestId': 'not-requested',
      });
      await pumpEventQueue();
      expect(
        jsonEncode(events.map((event) => event.properties).toList()),
        isNot(contains(_fakeSecret)),
      );
      daemon.handlers['fetch_agent_timeline_request'] = (_) => (
        'fetch_agent_timeline_response',
        {
          'agentId': 'existing',
          'entries': [
            {
              'provider': 'gemini',
              'item': {'type': 'error', 'message': _fakeSecret},
              'timestamp': _timestamp,
              'seqStart': 1,
            },
          ],
        },
      );
      final history = await gateway.messages('existing');
      expect(
        history
            .expand((message) => message.parts)
            .map((part) => part.text)
            .join('\n'),
        isNot(contains(_fakeSecret)),
      );
    },
  );

  test(
    'encrypted public endpoints are rejected while private endpoints remain usable',
    () {
      expect(
        () => paseoEndpoint('wss://public.example/ws'),
        throwsA(isA<PaseoFailure>()),
      );
      expect(paseoEndpoint('wss://100.64.0.20:6767').host, '100.64.0.20');
      expect(paseoEndpoint('wss://host.tail1234.ts.net:6767').path, '/ws');
      expect(paseoEndpoint('ws://127.0.0.1:6767').host, '127.0.0.1');
    },
  );
}
