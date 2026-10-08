import 'dart:async';
import 'dart:convert';

import 'common.dart';
import 'oc2_observation.dart';

/// The stable server may publish an empty catalog before plugins settle.
/// A catalog entry proves availability only; scenario checks still infer.
Future<List<Map<String, dynamic>>> waitOc2ModelCatalog(
  Fq3Wire wire, {
  required String directory,
  String? model,
  Duration timeout = const Duration(seconds: 20),
  Duration interval = const Duration(milliseconds: 250),
}) async {
  if (timeout.isNegative || interval.isNegative) {
    throw const ProbeFailure('invalid_catalog_poll_budget');
  }
  final elapsed = Stopwatch()..start();
  var sawEnabled = false;
  var attempted = false;
  while (true) {
    if (attempted && elapsed.elapsed >= timeout) break;
    attempted = true;
    final remaining = timeout - elapsed.elapsed;
    Object? response;
    try {
      response = await wire
          .request(
            'GET',
            '/api/model',
            query: {'location[directory]': directory},
          )
          .timeout(remaining.isNegative ? Duration.zero : remaining);
    } on TimeoutException {
      break;
    }
    if (response is! Map || response['data'] is! List) {
      throw const ProbeFailure('invalid_model_catalog');
    }
    final models = _list(response)
        .where(
          (m) =>
              m['enabled'] != false &&
              _ref(m)['providerID'] != '' &&
              _ref(m)['id'] != '',
        )
        .toList();
    sawEnabled = sawEnabled || models.isNotEmpty;
    if (models.isNotEmpty &&
        (model == null || models.any((m) => _modelName(m) == model))) {
      return models;
    }
    final budget = timeout - elapsed.elapsed;
    if (budget <= Duration.zero) break;
    await Future<void>.delayed(interval < budget ? interval : budget);
  }
  throw ProbeFailure(
    sawEnabled && model != null
        ? 'configured_model_unavailable'
        : 'enabled_model_missing',
  );
}

/// OC2 live events are volatile. Every assertion also reads owned state.
Future<ProbeRun> runProtocol2(Fq3Wire wire, ProbeOptions options) async {
  final probe = _Oc2Probe(ProbeRun(wire, options));
  await probe.runChecks();
  return probe.run;
}

class _Oc2Probe {
  final ProbeRun run;
  _Oc2Probe(this.run);
  Fq3Wire get wire => run.wire;
  Map<String, String> get location => {
    'location[directory]': run.options.directory,
  };
  List<Map<String, dynamic>> models = [];
  Map<String, dynamic>? selected;
  String? initialSession;
  String? streamedSession;
  Oc2ProbeObservation? observation;
  String? observationSession;

  void checkpoint(String stage) => observation?.checkpoint(stage);

  Future<void> observe(
    String capability,
    Future<Map<String, Object?>> Function() action,
  ) => run.check(capability, () async {
    final current = Oc2ProbeObservation(
      capability: capability,
      directory: run.options.directory,
    );
    final previous = Set<Map<String, dynamic>>.identity()..addAll(wire.events);
    observation = current;
    observationSession = null;
    try {
      return await action();
    } finally {
      final id = observationSession;
      if (id != null) {
        run.observations[capability] = current.snapshot(
          wire.events.where((event) => !previous.contains(event)),
          sessionID: id,
          eventStart: 0,
        )..['eventWindowMayBeTruncated'] = wire.events.length >= 2000;
      }
      observation = null;
      observationSession = null;
    }
  });

  Future<void> runChecks() async {
    await run.check('version', () async {
      final health = _map(await wire.request('GET', '/api/health'));
      final version = health['version'];
      run.require(
        health['healthy'] == true &&
            version is String &&
            RegExp(r'^[a-zA-Z0-9.+_-]{1,80}$').hasMatch(version),
        'invalid_health_version',
      );
      run.observedVersion = version as String;
      return {'healthy': true};
    });
    await run.check('create', () async {
      await wire.openEvents('/api/event', query: location);
      initialSession = await create();
      final info = await session(initialSession!);
      run.require(
        info['id'] == initialSession &&
            _map(info['location'])['directory'] == run.options.directory,
        'session_scope_mismatch',
      );
      return {'created': true};
    });
    await run.check('models', () async {
      models = await waitOc2ModelCatalog(
        wire,
        directory: run.options.directory,
        model: run.options.model,
      );
      final configured = run.options.model;
      if (configured != null) {
        selected = models.where((m) => _modelName(m) == configured).firstOrNull;
      } else {
        final defaultModel = _data(
          await wire.request('GET', '/api/model/default', query: location),
        );
        selected = models
            .where((m) => _sameModel(_ref(m), _ref(_map(defaultModel))))
            .firstOrNull;
      }
      run.require(selected != null, 'configured_model_unavailable');
      return {'enabledModels': models.length, 'selectedModelAvailable': true};
    });
    await observe('modelSwitch', () async {
      final model = requireModel();
      final alternatives = models
          .where((m) => !_sameModel(_ref(model), _ref(m)))
          .toList();
      final alternative =
          alternatives
              .where((m) => _ref(m)['providerID'] == _ref(model)['providerID'])
              .firstOrNull ??
          alternatives.firstOrNull;
      run.require(alternative != null, 'alternative_model_missing');
      final id = await create(model: model);
      observation?.selectModel(_modelName(alternative!));
      final fresh = Set<Map<String, dynamic>>.identity()..addAll(wire.events);
      checkpoint('select_model');
      await wire.request(
        'POST',
        '/api/session/$id/model',
        body: {'model': _ref(alternative!)},
      );
      checkpoint('await_selection');
      await wire.waitFor(
        (e) =>
            !fresh.contains(e) &&
            belongs(e, id, 'session.model.selected') &&
            _sameModel(_map(e['data'])['model'], _ref(alternative)),
      );
      checkpoint('verify_selection');
      run.require(
        _sameModel((await session(id))['model'], _ref(alternative)),
        'model_selection_not_retained',
      );
      final messages = await turn(id, 'Reply exactly FQ3_SWITCH_OK.');
      requireAnswer(messages, 'FQ3_SWITCH_OK', model: alternative);
      checkpoint('complete');
      return {'selectionObserved': true, 'inferenceVerified': true};
    });
    await run.check('stream', () async {
      final id = await create(model: requireModel());
      streamedSession = id;
      final fresh = Set<Map<String, dynamic>>.identity()..addAll(wire.events);
      final messages = await turn(id, 'Reply exactly FQ3_STREAM_OK.');
      run.require(
        wire.events.any(
          (e) =>
              !fresh.contains(e) &&
              belongs(e, id, 'session.text.delta') &&
              (_map(e['data'])['delta'] as String? ?? '').isNotEmpty,
        ),
        'stream_delta_missing',
      );
      requireAnswer(messages, 'FQ3_STREAM_OK', model: requireModel());
      return {'streamedDelta': true, 'completedReply': true};
    });
    await run.check('abort', () async {
      final id = await create(model: requireModel());
      final fresh = Set<Map<String, dynamic>>.identity()..addAll(wire.events);
      await prompt(
        id,
        'Write every integer from 1 through 10000, one per line.',
      );
      await wire.waitFor(
        (e) =>
            !fresh.contains(e) && belongs(e, id, 'session.execution.started'),
      );
      await wire.waitFor(
        (e) =>
            !fresh.contains(e) &&
            belongs(e, id, 'session.text.delta') &&
            (_map(e['data'])['delta'] as String? ?? '').isNotEmpty,
      );
      final active = _map(
        _data(await wire.request('GET', '/api/session/active')),
      );
      run.require(active.containsKey(id), 'abort_requires_running_session');
      final response = _map(
        await wire.request('POST', '/api/session/$id/interrupt'),
      );
      run.require(response['interrupted'] == true, 'abort_did_not_interrupt');
      await wire.waitFor(
        (e) =>
            !fresh.contains(e) &&
            belongs(e, id, 'session.execution.interrupted'),
      );
      final messages = await turn(id, 'Reply exactly FQ3_AFTER_ABORT_OK.');
      requireAnswer(messages, 'FQ3_AFTER_ABORT_OK', model: requireModel());
      return {
        'interrupted': true,
        'usableAfterAbort': true,
        'midStreamObserved': true,
      };
    });
    await run.check('reconnect', () async {
      final id = streamedSession ?? await create(model: requireModel());
      if (streamedSession == null) {
        requireAnswer(
          await turn(id, 'Reply exactly FQ3_RECONNECT_OK.'),
          'FQ3_RECONNECT_OK',
          model: requireModel(),
        );
      }
      final before = await history(id);
      run.require(before.any(_completedAssistant), 'reconnect_history_missing');
      final ids = before.map((m) => m['id']).toSet();
      await wire.closeEvents();
      wire.reconnectHttp();
      await wire.openEvents('/api/event', query: location);
      final listed = _list(
        await wire.request(
          'GET',
          '/api/session',
          query: {'directory': run.options.directory, 'limit': '200'},
        ),
      );
      final info = await session(id);
      final after = await history(id);
      final permissions = _list(
        await wire.request('GET', '/api/session/$id/permission'),
      );
      final forms = _list(await wire.request('GET', '/api/session/$id/form'));
      run.require(
        info['id'] == id &&
            listed.any((s) => s['id'] == id) &&
            after.map((m) => m['id']).toSet().containsAll(ids),
        'reconnect_history_changed',
      );
      run.require(
        [...permissions, ...forms].every((p) => p['sessionID'] == id),
        'reconnect_scope_mismatch',
      );
      return {'refetched': true, 'retainedMessages': after.length};
    });
    await observe('permissionAllow', () => permission(allow: true));
    await observe('permissionDeny', () => permission(allow: false));
    await observe('image', image);
    await observe('cards', cards);
  }

  Map<String, dynamic> requireModel() {
    run.require(selected != null, 'model_prerequisite_missing');
    return selected!;
  }

  Future<String> create({
    Map<String, dynamic>? model,
    List<Map<String, String>>? permissions,
  }) async {
    checkpoint('create');
    if (model != null) observation?.selectModel(_modelName(model));
    final info = _map(
      _data(
        await wire.request(
          'POST',
          '/api/session',
          body: {
            'title':
                '${run.options.title}-session-${run.sessionIDs.length + 1}',
            'location': {'directory': run.options.directory},
            if (model != null) 'model': _ref(model),
            'permissions': ?permissions,
          },
        ),
      ),
    );
    final id = info['id'];
    run.require(
      id is String && RegExp(r'^ses_[a-zA-Z0-9]+$').hasMatch(id),
      'invalid_owned_session',
    );
    run.sessionIDs.add(id as String);
    observationSession = id;
    await run.options.onSessionCreated?.call(id);
    return id;
  }

  Future<Map<String, dynamic>> session(String id) async =>
      _map(_data(await wire.request('GET', '/api/session/$id')));

  Future<List<Map<String, dynamic>>> history(String id) async {
    final messages = _list(
      await wire.request(
        'GET',
        '/api/session/$id/message',
        query: {'limit': '200', 'order': 'asc'},
      ),
    );
    run.require(
      messages.every(
        (m) => !m.containsKey('sessionID') || m['sessionID'] == id,
      ),
      'foreign_session_message',
    );
    run.historyCounts[id] = messages.length;
    return messages;
  }

  Future<void> prompt(String id, String text, {List<Object>? files}) async {
    checkpoint('prompt');
    await wire.request(
      'POST',
      '/api/session/$id/prompt',
      body: {
        'text': text,
        'delivery': 'queue',
        'resume': true,
        'files': ?files,
      },
    );
  }

  Future<List<Map<String, dynamic>>> turn(
    String id,
    String text, {
    List<Object>? files,
  }) async {
    final previous = (await history(id)).map((m) => m['id']).toSet();
    final fresh = Set<Map<String, dynamic>>.identity()..addAll(wire.events);
    await prompt(id, text, files: files);
    checkpoint('await_terminal');
    final ended = await wire.waitFor(
      (e) =>
          !fresh.contains(e) &&
          (_terminalTypes.any((type) => belongs(e, id, type))),
      timeout: const Duration(seconds: 100),
    );
    run.require(
      ended['type'] == 'session.execution.succeeded',
      'inference_execution_failed',
    );
    checkpoint('verify_outcome');
    final messages = (await history(
      id,
    )).where((m) => !previous.contains(m['id'])).toList();
    run.require(
      messages.any(_completedAssistant),
      'completed_assistant_missing',
    );
    return messages;
  }

  void requireAnswer(
    List<Map<String, dynamic>> messages,
    String answer, {
    required Map<String, dynamic> model,
  }) {
    run.require(
      messages.any(
        (m) =>
            _completedAssistant(m) &&
            _sameModel(m['model'], _ref(model)) &&
            _text(m).trim() == answer,
      ),
      'assistant_answer_not_verified',
    );
  }

  Future<Map<String, Object?>> permission({required bool allow}) async {
    final toolName = wire.isStableOc2 ? 'shell' : 'bash';
    final id = await create(
      model: requireModel(),
      permissions: wire.isStableOc2
          ? [
              {'action': toolName, 'resource': '*', 'effect': 'ask'},
            ]
          : null,
    );
    final marker = allow ? 'FQ3_ALLOW' : 'FQ3_DENY';
    final command = 'printf $marker';
    final reply = allow ? 'once' : 'reject';
    final fresh = Set<Map<String, dynamic>>.identity()..addAll(wire.events);
    await prompt(
      id,
      'Use the $toolName tool to run exactly `$command`. '
      'Do not run any other command. Then briefly report the outcome.',
    );
    checkpoint('await_permission');
    final asked = await wire.waitFor(
      (e) => !fresh.contains(e) && belongs(e, id, 'permission.asked'),
    );
    final request = _map(asked['data']);
    final requestId = request['id'];
    final source = _map(request['source']);
    checkpoint('verify_permission');
    run.require(
      requestId is String &&
          RegExp(r'^per_[a-zA-Z0-9]+$').hasMatch(requestId) &&
          request['action'] == toolName &&
          source['type'] == 'tool' &&
          source['id'] is String &&
          source['messageID'] is String,
      'permission_tool_request_missing',
    );
    final pending = _list(
      await wire.request('GET', '/api/session/$id/permission'),
    );
    run.require(
      pending.any((p) => p['id'] == requestId && p['sessionID'] == id),
      'permission_request_not_pending',
    );
    final tools = (await history(id))
        .where(
          (m) =>
              m['type'] == 'assistant' &&
              m['id'] == source['messageID'] &&
              _sameModel(m['model'], _ref(requireModel())),
        )
        .expand(_content)
        .where((t) => t['type'] == 'tool' && t['id'] == source['id']);
    run.require(
      tools.any(
        (t) =>
            t['name'] == toolName &&
            _map(_map(t['state'])['input'])['command'] == command,
      ),
      'permission_command_not_owned_probe',
    );
    checkpoint('reply_permission');
    await wire.request(
      'POST',
      '/api/session/$id/permission/$requestId/reply',
      body: {'reply': reply},
    );
    checkpoint('await_reply');
    await wire.waitFor(
      (e) =>
          !fresh.contains(e) &&
          belongs(e, id, 'permission.replied') &&
          _map(e['data'])['requestID'] == requestId &&
          _map(e['data'])['reply'] == reply,
    );
    checkpoint('await_terminal');
    final ended = await wire.waitFor(
      (e) =>
          !fresh.contains(e) &&
          _terminalTypes.any((type) => belongs(e, id, type)),
    );
    run.require(
      ended['type'] ==
          (wire.isStableOc2 && !allow
              ? 'session.execution.interrupted'
              : 'session.execution.succeeded'),
      'permission_turn_failed',
    );
    checkpoint('verify_outcome');
    final finished = (await history(id))
        .where(
          (m) =>
              m['type'] == 'assistant' &&
              m['id'] == source['messageID'] &&
              _sameModel(m['model'], _ref(requireModel())),
        )
        .expand(_content)
        .where((t) => t['type'] == 'tool' && t['id'] == source['id']);
    run.require(
      finished.any(
        (t) =>
            t['name'] == toolName &&
            _map(_map(t['state'])['input'])['command'] == command &&
            _map(t['state'])['status'] == (allow ? 'completed' : 'error'),
      ),
      'permission_outcome_missing',
    );
    if (wire.isStableOc2) {
      bool ownedToolEvent(Map<String, dynamic> event, String type) =>
          !fresh.contains(event) &&
          belongs(event, id, type) &&
          _map(event['data'])['id'] == source['id'] &&
          _map(event['data'])['assistantMessageID'] == source['messageID'];
      if (allow) {
        run.require(
          wire.events.any((e) => ownedToolEvent(e, 'session.tool.success')) &&
              finished.any((t) => oc2ShellOutputVerified(t['state'], marker)),
          'permission_command_output_missing',
        );
      } else {
        run.require(
          wire.events.any(
                (e) =>
                    ownedToolEvent(e, 'session.tool.failed') &&
                    _map(_map(e['data'])['error'])['type'] == 'aborted',
              ) &&
              finished.any(
                (t) => _map(_map(t['state'])['error'])['type'] == 'aborted',
              ),
          'permission_decline_not_verified',
        );
        run.require(
          !wire.events.any(
            (e) =>
                ownedToolEvent(e, 'session.tool.success') ||
                ownedToolEvent(e, 'session.tool.progress') ||
                (!fresh.contains(e) && belongs(e, id, 'session.shell.started')),
          ),
          'permission_decline_execution_observed',
        );
      }
      checkpoint('verify_idle');
      await permissionSettled(id);
    }
    checkpoint('complete');
    return {
      'requestObserved': true,
      'replyObserved': true,
      'toolOutcomeVerified': true,
    };
  }

  Future<void> permissionSettled(String id) async {
    final elapsed = Stopwatch()..start();
    while (true) {
      final pending = _map(
        await wire.request('GET', '/api/session/$id/permission'),
      )['data'];
      final active = _map(
        await wire.request('GET', '/api/session/active'),
      )['data'];
      run.require(
        pending is List &&
            pending.every((item) => item is Map) &&
            active is Map,
        'permission_settlement_invalid',
      );
      if ((pending as List).isEmpty && !(active as Map).containsKey(id)) return;
      if (elapsed.elapsed >= const Duration(seconds: 2)) {
        throw const ProbeFailure('permission_settlement_pending');
      }
      await Future<void>.delayed(const Duration(milliseconds: 100));
    }
  }

  Future<Map<String, Object?>> image() async {
    final preferred = requireModel();
    final imageModels = models
        .where(
          (m) => (_map(m['capabilities'])['input'] as List? ?? []).contains(
            'image',
          ),
        )
        .toList();
    final model =
        imageModels
            .where((m) => _sameModel(_ref(m), _ref(preferred)))
            .firstOrNull ??
        imageModels
            .where(
              (m) => _ref(m)['providerID'] == _ref(preferred)['providerID'],
            )
            .firstOrNull ??
        imageModels.firstOrNull;
    run.require(model != null, 'image_model_missing');
    final id = await create(model: model);
    final messages = await turn(
      id,
      'Describe the two panels in the attached '
      'image. Reply only JSON with keys left and right and lowercase '
      'English color names.',
      files: [
        {'uri': 'data:image/png;base64,$_image'},
      ],
    );
    var verified = false;
    for (final message in messages.where(_completedAssistant)) {
      if (!_sameModel(message['model'], _ref(model!))) continue;
      try {
        final answer = _map(jsonDecode(_text(message).trim()));
        verified = answer['left'] == 'red' && answer['right'] == 'blue';
      } catch (_) {
        /* Invalid semantic output is a failed assertion. */
      }
      if (verified) break;
    }
    run.require(verified, 'image_content_answer_missing');
    checkpoint('complete');
    return {'imageAnswerVerified': true};
  }

  Future<Map<String, Object?>> cards() async {
    final model = requireModel();
    checkpoint('inventory');
    final inventory = await wire.request('GET', '/api/mcp', query: location);
    final inventoryLocation = _map(inventory)['location'];
    run.require(
      (!wire.isStableOc2 && inventoryLocation == null) ||
          _map(inventoryLocation)['directory'] == run.options.directory,
      'cards_inventory_scope_mismatch',
    );
    final helpers = _list(inventory);
    run.require(
      helpers.any(
        (helper) =>
            helper['name'] == 'oc-ui' &&
            _map(helper['status'])['status'] == 'connected',
      ),
      'cards_helper_unavailable',
    );
    final id = await create(
      model: model,
      permissions: wire.isStableOc2
          ? const [
              {'action': 'oc-ui_show', 'resource': '*', 'effect': 'allow'},
            ]
          : null,
    );
    final fresh = Set<Map<String, dynamic>>.identity()..addAll(wire.events);
    const cardId = 'fq3-confirm';
    final messages = await turn(
      id,
      'Call the oc-ui_show tool exactly once with '
      '{"v":1,"id":"$cardId","title":"FQ3 confirmation",'
      '"body":[{"type":"text","text":"Confirm this disposable probe."}],'
      '"ask":{"kind":"confirm"}}. End the turn after the tool call. '
      'When the following tagged answer confirms it, reply exactly '
      'FQ3_CARD_CONFIRMED.',
    );
    checkpoint('await_tool');
    observation?.recordRetainedCardCalls(messages);
    Map<String, dynamic>? tool;
    for (final message in messages.where(_completedAssistant)) {
      if (!_sameModel(message['model'], _ref(model))) continue;
      for (final candidate in _content(message)) {
        final callID = candidate['id'];
        if (candidate['type'] != 'tool' ||
            candidate['name'] != 'oc-ui_show' ||
            callID is! String ||
            !RegExp(r'^[A-Za-z0-9_-]{1,128}$').hasMatch(callID) ||
            _map(candidate['state'])['status'] != 'completed' ||
            _toolOutput(candidate).trim().isEmpty ||
            _map(_map(candidate['state'])['input'])['v'] != 1 ||
            _map(_map(candidate['state'])['input'])['id'] != cardId ||
            _map(_map(_map(candidate['state'])['input'])['ask'])['kind'] !=
                'confirm') {
          continue;
        }
        final success = wire.events.any(
          (e) =>
              !fresh.contains(e) &&
              belongs(e, id, 'session.tool.success') &&
              _map(e['data'])['id'] == callID &&
              _map(e['data'])['assistantMessageID'] == message['id'],
        );
        if (success) {
          tool = candidate;
          break;
        }
      }
      if (tool != null) break;
    }
    run.require(
      tool != null && tool['id'] is String,
      'cards_tool_call_missing',
    );
    final receipt =
        '[oc-ui answer $cardId] Confirmed\n${jsonEncode({
          'v': 1,
          'cardId': cardId,
          'callId': tool!['id'],
          'value': {'confirm': true},
        })}';
    checkpoint('send_receipt');
    final answers = await turn(id, receipt);
    run.require(
      answers.any((m) => m['type'] == 'user' && m['text'] == receipt),
      'cards_user_receipt_missing',
    );
    requireAnswer(answers, 'FQ3_CARD_CONFIRMED', model: model);
    checkpoint('complete');
    return {'cardsToolCall': true, 'answerReceipt': true};
  }

  bool belongs(Map<String, dynamic> event, String id, String type) =>
      event['type'] == type &&
      _map(event['data'])['sessionID'] == id &&
      (!event.containsKey('location') ||
          _map(event['location'])['directory'] == run.options.directory);
}

const _terminalTypes = [
  'session.execution.succeeded',
  'session.execution.failed',
  'session.execution.interrupted',
];
Object? _data(Object? value) => _map(value)['data'];
Map<String, dynamic> _map(Object? value) =>
    value is Map ? Map<String, dynamic>.from(value) : <String, dynamic>{};
List<Map<String, dynamic>> _list(Object? value) => (_data(value) as List? ?? [])
    .whereType<Map>()
    .map((m) => Map<String, dynamic>.from(m))
    .toList();
Iterable<Map<String, dynamic>> _content(Map<String, dynamic> message) =>
    (message['content'] as List? ?? []).whereType<Map>().map(
      (m) => Map<String, dynamic>.from(m),
    );
String _text(Map<String, dynamic> message) => _content(message)
    .where((p) => p['type'] == 'text')
    .map((p) => p['text'] as String? ?? '')
    .join();
String _toolOutput(Map<String, dynamic> tool) =>
    (_map(tool['state'])['content'] as List? ?? [])
        .whereType<Map>()
        .where((item) => item['type'] == 'text' && item['text'] is String)
        .map((item) => item['text'] as String)
        .join('\n');

/// Stable ShellTool returns stdout and a separate exit notice, plus metadata.
/// Do not flatten the notice into stdout or accept a marker in arbitrary text.
bool oc2ShellOutputVerified(Object? value, String marker) {
  final state = _map(value);
  final content = state['content'];
  final metadata = _map(state['metadata']);
  if (state['status'] != 'completed' ||
      content is! List ||
      content.length != 2 ||
      metadata['exit'] != 0 ||
      metadata['truncated'] != false ||
      metadata['timeout'] == true ||
      metadata['status'] == 'running') {
    return false;
  }
  final stdout = _map(content[0]);
  final notice = _map(content[1]);
  return stdout['type'] == 'text' &&
      stdout['text'] is String &&
      (stdout['text'] as String).trim() == marker &&
      notice['type'] == 'text' &&
      notice['text'] == 'Command exited with code 0.';
}

bool _completedAssistant(Map<String, dynamic> message) =>
    message['type'] == 'assistant' &&
    message['error'] == null &&
    _map(message['time'])['completed'] is num &&
    message['finish'] != null;
Map<String, String> _ref(Map<String, dynamic> model) {
  final provider = model['providerID'] as String? ?? '';
  var id = model['modelID'] as String? ?? model['id'] as String? ?? '';
  if (id.startsWith('$provider/')) id = id.substring(provider.length + 1);
  return {'id': id, 'providerID': provider};
}

String _modelName(Map<String, dynamic> model) =>
    '${_ref(model)['providerID']}/${_ref(model)['id']}';
bool _sameModel(Object? actual, Map<String, String> expected) =>
    _ref(_map(actual))['id'] == expected['id'] &&
    _ref(_map(actual))['providerID'] == expected['providerID'];

// Lossless 32x32 RGB fixture: red left half, blue right half; no text labels.
const _image =
    'iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAK0lEQVR4nO3N'
    'sQkAAAzDsPz/dHpDpi4CjwalydS4d90BAAAAAAAAAADAG3A2h/wuHtDxTwAAAABJRU5ErkJggg==';
