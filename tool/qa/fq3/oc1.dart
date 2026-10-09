import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'common.dart';
import 'evidence.dart' show isPublicModelReference;

const baselineOc1Model = 'zai-coding-plan/glm-5.3';
const _inferenceCapabilities = {
  'modelSwitch',
  'stream',
  'abort',
  'reconnect',
  'permissionAllow',
  'permissionDeny',
  'image',
  'cards',
};

/// Only provider-specific evidence reclassifies a failed inference assertion.
/// Invalid payloads, missing events, generic timeouts and answer mismatches keep
/// their original failed assertion status.
Map<String, Object?> oc1ClassifyResult(
  String capability,
  Map<String, Object?> result,
  Map<String, Object?> observation,
) {
  if (!_inferenceCapabilities.contains(capability) ||
      result['state'] != 'fail') {
    return result;
  }
  final original = result['code'];
  final status = observation['httpStatus'];
  final providerError =
      const {
        'ProviderAuthError',
        'ProviderModelNotFoundError',
      }.contains(observation['errorName']) ||
      (observation['errorName'] == 'APIError' &&
          status is int &&
          (const {401, 403, 429}.contains(status) ||
              status >= 500 && status <= 599));
  if (original != 'oc1_baseline_model_unavailable' &&
      original != 'oc1_requested_model_unavailable' &&
      !(original == 'oc1_no_usable_model' &&
          observation['stage'] == 'provider_admission') &&
      original != 'oc1_second_model_unavailable' &&
      original != 'oc1_image_model_unavailable' &&
      !(providerError &&
          (original == 'oc1_prompt_error' ||
              observation['permissionTimeoutKind'] == 'assistant_error'))) {
    return result;
  }
  return {
    'state': 'blocked',
    'code': 'provider_unavailable',
    'classification': 'provider',
    'originalCode': original,
    'facts': <String, Object?>{},
  };
}

Map<String, dynamic> _map(Object? value) =>
    value is Map<String, dynamic> ? value : <String, dynamic>{};
String _string(Object? value) => value is String ? value : '';
Map<String, dynamic> _event(Map<String, dynamic> event) =>
    event['payload'] is Map<String, dynamic> ? _map(event['payload']) : event;

/// Closed error metadata only. Messages, bodies, headers and unknown names stay
/// private, including when the server nests API metadata in `data`.
Map<String, Object?> oc1FailureFacts(Object? error) {
  const names = {
    'APIError',
    'ProviderAuthError',
    'ProviderModelNotFoundError',
    'TypeValidationError',
    'UnknownError',
    'MessageAbortedError',
    'ContextOverflowError',
    'MessageOutputLengthError',
  };
  final value = _map(error);
  final result = <String, Object?>{};
  if (names.contains(value['name'])) result['errorName'] = value['name'];
  var data = value;
  for (var depth = 0; depth < 4 && data.isNotEmpty; depth++) {
    for (final key in const ['statusCode', 'status']) {
      final status = data[key];
      if (!result.containsKey('httpStatus') &&
          status is num &&
          status.isFinite &&
          status >= 100 &&
          status <= 599 &&
          status == status.truncateToDouble()) {
        result['httpStatus'] = status.toInt();
      }
    }
    final retryable = data['isRetryable'] ?? data['retryable'];
    if (!result.containsKey('retryable') && retryable is bool) {
      result['retryable'] = retryable;
    }
    data = _map(data['data']);
  }
  return result;
}

/// Classify only exact owned assistant history after a permission wait failed.
/// Absence means no tool was observed in this snapshot, not model incapability.
Map<String, Object?> oc1PermissionFailureFacts(
  List<Map<String, dynamic>> history, {
  required String sessionID,
  required String promptID,
}) {
  var toolObserved = false;
  for (final message in history) {
    final info = _map(message['info']);
    if (info['role'] != 'assistant' ||
        info['sessionID'] != sessionID ||
        info['parentID'] != promptID ||
        info['synthetic'] == true) {
      continue;
    }
    if (info['error'] != null) {
      return {
        'permissionTimeoutKind': 'assistant_error',
        ...oc1FailureFacts(info['error']),
      };
    }
    final parts = message['parts'];
    if (parts is! List) continue;
    toolObserved |= parts
        .map(_map)
        .any(
          (part) =>
              part['type'] == 'tool' &&
              part['tool'] == 'bash' &&
              part['sessionID'] == sessionID &&
              part['messageID'] == info['id'] &&
              part['synthetic'] != true &&
              _string(part['callID']).isNotEmpty,
        );
  }
  return {
    'permissionTimeoutKind': toolObserved
        ? 'event_missing'
        : 'tool_not_requested',
    'permissionToolObserved': toolObserved,
  };
}

/// Returns only a real nonempty text delta, scoped to the requested session.
/// Completion and assistant ownership still require authoritative HTTP history.
String? oc1StreamMessage(Map<String, dynamic> event, String sessionID) {
  final value = _event(event);
  final properties = _map(value['properties']);
  if (value['type'] == 'message.part.delta' &&
      properties['sessionID'] == sessionID &&
      properties['field'] == 'text' &&
      _string(properties['delta']).isNotEmpty) {
    final id = _string(properties['messageID']);
    return id.isEmpty ? null : id;
  }
  final part = _map(properties['part']);
  if (value['type'] == 'message.part.updated' &&
      part['sessionID'] == sessionID &&
      part['type'] == 'text' &&
      part['synthetic'] != true &&
      _string(properties['delta']).isNotEmpty) {
    final id = _string(part['messageID']);
    return id.isEmpty ? null : id;
  }
  return null;
}

/// An OC1 completed tool is execution evidence, unless explicitly unexecuted.
/// User, synthetic, foreign-session and merely queued parts cannot qualify.
Map<String, dynamic>? oc1CompletedTool(
  List<Map<String, dynamic>> history,
  String sessionID,
  String promptID,
  String tool,
) {
  for (final message in history) {
    final info = _map(message['info']);
    if (info['role'] != 'assistant' ||
        info['sessionID'] != sessionID ||
        info['parentID'] != promptID ||
        info['error'] != null ||
        _map(info['time'])['completed'] is! num) {
      continue;
    }
    final parts = message['parts'];
    if (parts is! List) continue;
    for (final raw in parts) {
      final part = _map(raw);
      final state = _map(part['state']);
      if (part['type'] == 'tool' &&
          part['tool'] == tool &&
          part['sessionID'] == sessionID &&
          part['messageID'] == info['id'] &&
          part['synthetic'] != true &&
          part['executed'] != false &&
          state['executed'] != false &&
          state['status'] == 'completed' &&
          _string(part['callID']).isNotEmpty) {
        return part;
      }
    }
  }
  return null;
}

/// Confirms the original permission-controlled tool outcome without requiring
/// the unrelated assistant narration to complete. Replied SSE alone is insufficient.
Future<void> oc1VerifyPermissionOutcome(
  Future<List<Map<String, dynamic>>> Function() readHistory,
  Future<dynamic> Function() readPending, {
  required String sessionID,
  required String promptID,
  required String messageID,
  required String callID,
  required String requestID,
  required String command,
  required String marker,
  required bool allow,
  Duration timeout = const Duration(seconds: 50),
}) async {
  final clock = Stopwatch()..start();
  while (true) {
    final history = await readHistory();
    final matches = <Map<String, dynamic>>[];
    for (final message in history) {
      final info = _map(message['info']);
      if (info['role'] != 'assistant' ||
          info['sessionID'] != sessionID ||
          info['parentID'] != promptID ||
          info['id'] != messageID ||
          info['synthetic'] == true) {
        continue;
      }
      final parts = message['parts'];
      if (parts is! List) continue;
      for (final part in parts.map(_map)) {
        if (part['type'] == 'tool' &&
            part['tool'] == 'bash' &&
            part['sessionID'] == sessionID &&
            part['messageID'] == messageID &&
            part['callID'] == callID &&
            part['synthetic'] != true) {
          matches.add(part);
        }
      }
    }
    if (matches.length != 1) {
      throw const ProbeFailure('oc1_permission_tool_not_correlated');
    }
    final part = matches.single;
    final state = _map(part['state']);
    if (_map(state['input'])['command'] != command) {
      throw const ProbeFailure('oc1_permission_command_mismatch');
    }
    final terminal =
        state['status'] == 'completed' || state['status'] == 'error';
    if (terminal) {
      final output = _string(state['output']);
      final verified = allow
          ? state['status'] == 'completed' &&
                part['executed'] != false &&
                state['executed'] != false &&
                output.contains(marker)
          : state['status'] == 'error' && !output.contains(marker);
      if (!verified) {
        throw const ProbeFailure('oc1_permission_outcome_mismatch');
      }
      final pending = await readPending();
      if (pending is! List) {
        throw const ProbeFailure('oc1_invalid_permissions');
      }
      if (!pending.map(_map).any((p) => p['id'] == requestID)) return;
    }
    final remaining = timeout - clock.elapsed;
    if (remaining <= Duration.zero) {
      throw const ProbeFailure('oc1_permission_outcome_timeout');
    }
    await Future<void>.delayed(
      remaining < const Duration(milliseconds: 250)
          ? remaining
          : const Duration(milliseconds: 250),
    );
  }
}

class _Model {
  final String provider;
  final String id;
  final bool image;
  const _Model(this.provider, this.id, this.image);
  Map<String, String> get wire => {'providerID': provider, 'modelID': id};
  String get key => '$provider/$id';
}

class _Turn {
  final String prompt;
  final List<Map<String, dynamic>> history;
  final List<Map<String, dynamic>> assistants;
  final bool streamed;
  const _Turn(this.prompt, this.history, this.assistants, this.streamed);
  String get text => assistants.map(_text).join('\n');
}

String _text(Map<String, dynamic> message) {
  final parts = message['parts'];
  if (parts is! List) return '';
  return parts
      .map(_map)
      .where((part) => part['type'] == 'text' && part['synthetic'] != true)
      .map((part) => _string(part['text']))
      .join('\n');
}

class _Oc1 {
  final ProbeRun run;
  final nonce =
      '${DateTime.now().microsecondsSinceEpoch.toRadixString(36)}'
      '${Random.secure().nextInt(1 << 30).toRadixString(36)}';
  final models = <_Model>[];
  _Model? selected;
  String? streamSession;
  int serial = 0;
  DateTime? deadline;
  Map<String, Object?>? observation;
  String? unavailableModelCode;
  _Oc1(this.run);
  Fq3Wire get wire => run.wire;
  Map<String, String> get query => {'directory': run.options.directory};

  Future<Map<String, Object?>> scenario(
    String capability,
    Future<Map<String, Object?>> Function() action,
  ) async {
    deadline = DateTime.now().add(const Duration(seconds: 170));
    final current = <String, Object?>{'phase': capability, 'stage': 'start'};
    observation = current;
    try {
      if (_inferenceCapabilities.contains(capability) &&
          unavailableModelCode != null) {
        checkpoint('provider_admission', choice: selected);
        throw ProbeFailure(unavailableModelCode!);
      }
      final result = await action();
      current['stage'] = 'complete';
      return result;
    } finally {
      run.observations[capability] = current;
      observation = null;
    }
  }

  void checkpoint(String stage, {_Model? choice}) {
    observation?['stage'] = stage;
    final reference = choice?.key;
    if (isPublicModelReference(reference)) observation?['model'] = reference;
  }

  Duration budget(Duration maximum) {
    final remaining = deadline?.difference(DateTime.now()) ?? maximum;
    run.require(remaining > Duration.zero, 'oc1_scenario_timeout');
    return remaining < maximum ? remaining : maximum;
  }

  Future<dynamic> request(
    String method,
    String path, {
    Object? body,
    Map<String, String>? extraQuery,
  }) => wire
      .request(method, path, body: body, query: {...query, ...?extraQuery})
      .timeout(
        budget(const Duration(seconds: 15)),
        onTimeout: () => throw const ProbeFailure('request_timeout'),
      );

  Future<Map<String, dynamic>> waitFor(
    bool Function(Map<String, dynamic>) predicate,
    Duration maximum,
  ) => wire.waitFor(predicate, timeout: budget(maximum));

  String messageID() {
    // OC1's ascending identifier: 48-bit millisecond/counter prefix + suffix.
    final time =
        ((DateTime.now().millisecondsSinceEpoch << 12) + serial++) &
        0xffffffffffff;
    const alphabet =
        '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';
    final random = Random.secure();
    return 'msg_${time.toRadixString(16).padLeft(12, '0')}'
        '${List.generate(14, (_) => alphabet[random.nextInt(alphabet.length)]).join()}';
  }

  _Model model() {
    run.require(selected != null, 'oc1_no_usable_model');
    return selected!;
  }

  Future<String> create(
    String purpose, {
    List<Map<String, String>>? rules,
  }) async {
    final result = _map(
      await request(
        'POST',
        '/session',
        body: {
          'title': '${run.options.title}-session-${run.sessionIDs.length + 1}',
          'permission': ?rules,
        },
      ),
    );
    final id = _string(result['id']);
    run.require(
      RegExp(r'^ses_[A-Za-z0-9]+$').hasMatch(id),
      'oc1_invalid_session',
    );
    run.require(!run.sessionIDs.contains(id), 'oc1_session_id_reused');
    run.sessionIDs.add(id);
    await run.options.onSessionCreated?.call(id);
    final retained = _map(await request('GET', '/session/$id'));
    run.require(
      retained['id'] == id && retained['title'] == result['title'],
      'oc1_session_not_retained',
    );
    return id;
  }

  Future<List<Map<String, dynamic>>> history(String session) async {
    final result = await request(
      'GET',
      '/session/$session/message',
      extraQuery: {'limit': '50'},
    );
    run.require(result is List && result.length <= 50, 'oc1_invalid_history');
    final messages = (result as List).map(_map).toList();
    run.require(
      messages.every((message) {
        final info = _map(message['info']);
        return info['sessionID'] == session &&
            _string(info['id']).isNotEmpty &&
            const ['user', 'assistant'].contains(info['role']) &&
            message['parts'] is List;
      }),
      'oc1_invalid_history',
    );
    run.historyCounts[session] = messages.length;
    return messages;
  }

  Future<String> send(
    String session,
    String text, {
    _Model? choice,
    List<Map<String, Object?>>? attachments,
  }) async {
    final id = messageID();
    final selectedModel = choice ?? model();
    if (isPublicModelReference(selectedModel.key)) {
      observation?['model'] = selectedModel.key;
    }
    if (observation?['stage'] == 'start') checkpoint('prompt_reply');
    await request(
      'POST',
      '/session/$session/prompt_async',
      body: {
        'messageID': id,
        'model': selectedModel.wire,
        'parts': [
          <String, Object?>{'type': 'text', 'text': text},
          ...?attachments,
        ],
      },
    );
    return id;
  }

  List<Map<String, dynamic>> assistants(
    List<Map<String, dynamic>> messages,
    String prompt,
  ) => messages.where((message) {
    final info = _map(message['info']);
    return info['role'] == 'assistant' && info['parentID'] == prompt;
  }).toList();

  bool completed(Map<String, dynamic> message) {
    final info = _map(message['info']);
    final time = _map(info['time']);
    return time['completed'] is num &&
        (time['completed'] as num) > 0 &&
        info['error'] == null &&
        _text(message).trim().isNotEmpty &&
        info['finish'] != 'tool-calls';
  }

  Future<_Turn> finish(
    String session,
    String prompt,
    Set<Map<String, dynamic>> prior, {
    bool requireStream = false,
    Duration timeout = const Duration(seconds: 100),
  }) async {
    final clock = Stopwatch()..start();
    while (clock.elapsed < timeout) {
      final messages = await history(session);
      final replies = assistants(messages, prompt);
      if (replies.any(completed)) {
        final user = messages.where((m) => _map(m['info'])['id'] == prompt);
        run.require(
          user.length == 1 && _map(user.single['info'])['role'] == 'user',
          'oc1_prompt_not_retained',
        );
        final ids = replies.map((m) => _string(_map(m['info'])['id'])).toSet();
        final streamed = wire.events.any(
          (event) =>
              !prior.contains(event) &&
              ids.contains(oc1StreamMessage(event, session)),
        );
        run.require(!requireStream || streamed, 'oc1_no_assistant_delta');
        return _Turn(prompt, messages, replies, streamed);
      }
      for (final reply in replies) {
        final error = _map(reply['info'])['error'];
        if (error != null) {
          observation?.addAll(oc1FailureFacts(error));
          throw const ProbeFailure('oc1_prompt_error');
        }
      }
      await Future<void>.delayed(const Duration(milliseconds: 250));
    }
    throw const ProbeFailure('oc1_completion_timeout');
  }

  Future<_Turn> turn(
    String session,
    String text, {
    _Model? choice,
    List<Map<String, Object?>>? attachments,
    bool requireStream = false,
  }) async {
    final prior = Set<Map<String, dynamic>>.identity()..addAll(wire.events);
    final id = await send(
      session,
      text,
      choice: choice,
      attachments: attachments,
    );
    return finish(session, id, prior, requireStream: requireStream);
  }

  Future<void> events() => wire
      .openEvents('/event', query: query)
      .timeout(
        budget(const Duration(seconds: 10)),
        onTimeout: () => throw const ProbeFailure('event_stream_unavailable'),
      );
  String token(String purpose) => 'FQ3_${purpose.toUpperCase()}_$nonce';

  Future<Map<String, Object?>> version() async {
    final result = _map(await request('GET', '/global/health'));
    final value = _string(result['version']);
    run.require(
      result['healthy'] == true &&
          RegExp(r'^1\.[0-9]+\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?$').hasMatch(value),
      'oc1_invalid_version',
    );
    run.observedVersion = value;
    return {'healthy': true};
  }

  Future<Map<String, Object?>> discoverModels() async {
    final result = _map(await request('GET', '/provider'));
    final all = result['all'];
    final connected = result['connected'];
    run.require(all is List && connected is List, 'oc1_invalid_models');
    for (final provider in (all as List).map(_map)) {
      final id = _string(provider['id']);
      if (!(connected as List).contains(id)) continue;
      final entries = _map(provider['models']);
      for (final entry in entries.entries) {
        final value = _map(entry.value);
        final input = _map(_map(value['capabilities'])['input']);
        final modalities = _map(value['modalities'])['input'];
        models.add(
          _Model(
            id,
            entry.key,
            input['image'] == true ||
                (modalities is List && modalities.contains('image')),
          ),
        );
      }
    }
    if (models.isEmpty) {
      unavailableModelCode = 'oc1_no_usable_model';
      throw const ProbeFailure('oc1_no_connected_model');
    }
    final wanted = run.options.model;
    final baseline = run.options.baselineModel;
    final target = wanted ?? baseline;
    if (target != null) {
      run.require(isPublicModelReference(target), 'phase_model_invalid');
      final matches = models.where((m) => m.key == target).toList();
      run.require(matches.length <= 1, 'oc1_invalid_models');
      selected = matches.isEmpty ? models.first : matches.single;
      if (matches.isEmpty) {
        unavailableModelCode = wanted == null
            ? 'oc1_baseline_model_unavailable'
            : 'oc1_requested_model_unavailable';
      }
      run.modelSelection = {
        'source': wanted != null
            ? 'explicit'
            : matches.isEmpty
            ? 'catalog-fallback'
            : 'baseline',
        'requested': target,
        'selected': isPublicModelReference(selected?.key)
            ? selected!.key
            : null,
        'baselineAvailable': models.any((m) => m.key == baselineOc1Model),
        'inferenceAvailable': unavailableModelCode == null,
      };
    } else {
      // Config can include credentials: inspect only model, never emit its body.
      var savedModel = '';
      try {
        savedModel = _string(_map(await request('GET', '/config'))['model']);
      } catch (_) {
        // Older servers and controlled fixtures may omit this optional route.
      }
      final defaults = _map(result['default']);
      selected = models.firstWhere(
        (m) => m.key == savedModel,
        orElse: () => models.firstWhere(
          (m) => defaults[m.provider] == m.id,
          orElse: () => models.first,
        ),
      );
    }
    run.selectedModel = isPublicModelReference(selected?.key)
        ? selected!.key
        : null;
    checkpoint('model_catalog_selected', choice: selected);
    return {'connectedModels': models.length};
  }

  Future<Map<String, Object?>> stream() async {
    await events();
    final session = await create('stream');
    final answer = token('stream');
    final result = await turn(
      session,
      'Reply with exactly this token: $answer',
      requireStream: true,
    );
    run.require(result.text.contains(answer), 'oc1_reply_mismatch');
    streamSession = session;
    return {'streamedDelta': true, 'completedReply': true};
  }

  Future<Map<String, Object?>> modelSwitch() async {
    final first = model();
    final alternatives = models.where((m) => m.key != first.key);
    run.require(alternatives.isNotEmpty, 'oc1_second_model_unavailable');
    final session = await create('model-switch');
    if (isPublicModelReference(first.key)) {
      observation?['firstModel'] = first.key;
    }
    checkpoint('initial_reply', choice: first);
    final initial = await turn(
      session,
      'Reply with exactly ${token('first')}.',
    );
    run.require(
      initial.assistants.any((m) {
        final info = _map(m['info']);
        return completed(m) &&
            info['providerID'] == first.provider &&
            info['modelID'] == first.id;
      }),
      'oc1_initial_selection_unobserved',
    );
    final alternative = alternatives.firstWhere(
      (candidate) => candidate.provider == first.provider,
      orElse: () => alternatives.first,
    );
    if (isPublicModelReference(alternative.key)) {
      observation?['alternativeModel'] = alternative.key;
    }
    checkpoint('alternative_reply', choice: alternative);
    final changed = await turn(
      session,
      'Reply with exactly ${token('second')}.',
      choice: alternative,
    );
    run.require(
      changed.assistants.any((m) {
        final info = _map(m['info']);
        return completed(m) &&
            info['providerID'] == alternative.provider &&
            info['modelID'] == alternative.id;
      }),
      'oc1_changed_selection_unobserved',
    );
    return {'selectionObserved': true};
  }

  Future<Map<String, Object?>> reconnect() async {
    var session = streamSession;
    if (session == null) {
      session = await create('reconnect');
      await turn(session, 'Reply with exactly ${token('reconnect_seed')}.');
    }
    final before = await history(session);
    run.require(before.any(completed), 'oc1_no_history_before_reconnect');
    final ids = before.map((m) => _string(_map(m['info'])['id'])).toSet();
    await wire.closeEvents();
    wire.reconnectHttp();
    await events();
    final after = await history(session);
    final retained = after.map((m) => _string(_map(m['info'])['id'])).toSet();
    run.require(retained.containsAll(ids), 'oc1_history_lost_on_reconnect');
    return {'refetched': true, 'messages': after.length};
  }

  String abortReplyText(_Turn turn, String session) {
    final replies = turn.assistants.where(completed).toList()
      ..sort(
        (a, b) => (_map(_map(a['info'])['time'])['completed'] as num).compareTo(
          _map(_map(b['info'])['time'])['completed'] as num,
        ),
      );
    run.require(replies.isNotEmpty, 'oc1_after_abort_no_reply');
    final reply = replies.last;
    final info = _map(reply['info']);
    final selectedModel = model();
    run.require(
      info['providerID'] == selectedModel.provider &&
          info['modelID'] == selectedModel.id,
      'oc1_after_abort_model_mismatch',
    );
    final parts = (reply['parts'] as List)
        .map(_map)
        .where((part) => part['type'] == 'text' && part['synthetic'] != true)
        .toList();
    run.require(parts.isNotEmpty, 'oc1_after_abort_no_reply');
    run.require(
      parts.every(
        (part) =>
            part['sessionID'] == session && part['messageID'] == info['id'],
      ),
      'oc1_after_abort_stale_parts',
    );
    return parts.map((part) => _string(part['text'])).join('\n');
  }

  Future<Map<String, Object?>> abort() async {
    await events();
    final session = await create('abort');
    final prior = Set<Map<String, dynamic>>.identity()..addAll(wire.events);
    final prompt = await send(
      session,
      'Write the integers from 1 to 20000, one per line. Do not use tools.',
    );
    final delta = await waitFor(
      (e) => !prior.contains(e) && oc1StreamMessage(e, session) != null,
      const Duration(seconds: 35),
    );
    final assistantID = oc1StreamMessage(delta, session);
    final live = assistants(await history(session), prompt);
    final status = _map(await request('GET', '/session/status'));
    run.require(
      live.any(
            (m) =>
                _map(m['info'])['id'] == assistantID &&
                _map(_map(m['info'])['time'])['completed'] == null,
          ) &&
          _map(status[session])['type'] == 'busy',
      'oc1_abort_not_live',
    );
    run.require(
      await request('POST', '/session/$session/abort') == true,
      'oc1_abort_not_accepted',
    );
    final clock = Stopwatch()..start();
    var interrupted = false;
    while (clock.elapsed < const Duration(seconds: 25)) {
      final historyNow = await history(session);
      final replies = assistants(historyNow, prompt);
      final state = _map(await request('GET', '/session/status'));
      final idle =
          !state.containsKey(session) || _map(state[session])['type'] == 'idle';
      final aborted = replies.any((m) {
        final info = _map(m['info']);
        final error = _map(info['error']);
        return error['name'] == 'MessageAbortedError' &&
            _map(info['time'])['completed'] is num;
      });
      if (idle && aborted) {
        interrupted = true;
        break;
      }
      await Future<void>.delayed(const Duration(milliseconds: 250));
    }
    run.require(interrupted, 'oc1_abort_not_observed');
    final answer = token('after_abort');
    final next = await turn(session, 'Reply with exactly this token: $answer');
    run.require(
      abortReplyText(next, session).contains(answer),
      'oc1_after_abort_reply_mismatch',
    );
    return {'interrupted': true, 'usableAfterAbort': true};
  }

  Future<Map<String, Object?>> permission(bool allow) async {
    checkpoint('create_permission_session', choice: model());
    await events();
    final session = await create(
      allow ? 'permission-allow' : 'permission-deny',
      rules: [
        {'permission': '*', 'pattern': '*', 'action': 'deny'},
        {'permission': 'bash', 'pattern': '*', 'action': 'ask'},
      ],
    );
    final answer = token(allow ? 'allowed' : 'denied');
    final command = "printf '%s\\n' '$answer'";
    final prior = Set<Map<String, dynamic>>.identity()..addAll(wire.events);
    final prompt = await send(
      session,
      'Use the bash tool exactly once to execute this harmless command:\n'
      '$command\nDo not substitute another tool. Then report its outcome.',
    );
    try {
      checkpoint('await_permission_asked', choice: model());
      final event = await waitFor((e) {
        final value = _event(e);
        final p = _map(value['properties']);
        return !prior.contains(e) &&
            value['type'] == 'permission.asked' &&
            p['sessionID'] == session &&
            p['permission'] == 'bash';
      }, const Duration(seconds: 50));
      final asked = _map(_event(event)['properties']);
      final requestID = _string(asked['id']);
      final tool = _map(asked['tool']);
      final callID = _string(tool['callID']);
      final pending = await request('GET', '/permission');
      run.require(
        requestID.isNotEmpty &&
            callID.isNotEmpty &&
            pending is List &&
            pending
                .map(_map)
                .any((p) => p['id'] == requestID && p['sessionID'] == session),
        'oc1_permission_not_pending',
      );
      final liveCalls = assistants(await history(session), prompt)
          .expand((m) => (m['parts'] as List).map(_map))
          .where(
            (p) =>
                p['type'] == 'tool' &&
                p['tool'] == 'bash' &&
                p['sessionID'] == session &&
                p['callID'] == callID &&
                p['messageID'] == tool['messageID'] &&
                p['synthetic'] != true,
          );
      run.require(
        liveCalls.length == 1 &&
            _map(_map(liveCalls.single['state'])['input'])['command'] ==
                command,
        'oc1_permission_command_mismatch',
      );
      final reply = allow ? 'once' : 'reject';
      checkpoint('reply_permission', choice: model());
      await request(
        'POST',
        '/permission/$requestID/reply',
        body: {'reply': reply},
      );
      checkpoint('await_permission_replied', choice: model());
      await waitFor((e) {
        final value = _event(e);
        final p = _map(value['properties']);
        return !prior.contains(e) &&
            value['type'] == 'permission.replied' &&
            p['sessionID'] == session &&
            p['requestID'] == requestID &&
            p['reply'] == reply;
      }, const Duration(seconds: 20));
      checkpoint('verify_permission_outcome', choice: model());
      await oc1VerifyPermissionOutcome(
        () => history(session),
        () => request('GET', '/permission'),
        sessionID: session,
        promptID: prompt,
        messageID: _string(tool['messageID']),
        callID: callID,
        requestID: requestID,
        command: command,
        marker: answer,
        allow: allow,
        timeout: budget(const Duration(seconds: 50)),
      );
      return {'requestObserved': true, 'replyObserved': true};
    } on ProbeFailure catch (error) {
      if (error.code == 'timeout' ||
          error.code == 'oc1_permission_outcome_timeout') {
        try {
          // Separate bounded diagnostic read: no prompt retry, event injection,
          // permission change or relaxed qualification follows a timeout.
          final raw = await wire
              .request(
                'GET',
                '/session/$session/message',
                query: {...query, 'limit': '50'},
              )
              .timeout(const Duration(seconds: 5));
          if (raw is List && raw.length <= 50) {
            observation?.addAll(
              oc1PermissionFailureFacts(
                raw.map(_map).toList(),
                sessionID: session,
                promptID: prompt,
              ),
            );
            observation?['diagnosticHistoryAvailable'] = true;
          } else {
            observation?['diagnosticHistoryAvailable'] = false;
          }
        } catch (_) {
          observation?['diagnosticHistoryAvailable'] = false;
        }
      }
      rethrow;
    }
  }

  Future<Map<String, Object?>> image() async {
    final candidates = models.where((m) => m.image);
    run.require(candidates.isNotEmpty, 'oc1_image_model_unavailable');
    final preferred = model();
    final choice = preferred.image
        ? preferred
        : candidates.firstWhere(
            (candidate) => candidate.provider == preferred.provider,
            orElse: () => candidates.first,
          );
    final session = await create('image');
    final data = 'data:image/png;base64,${base64Encode(_bluePng())}';
    final result = await turn(
      session,
      'What is the dominant color of the attached image? Reply with one '
      'English color word only.',
      choice: choice,
      attachments: [
        {
          'type': 'file',
          'mime': 'image/png',
          'url': data,
          'filename': 'fq3-image.png',
        },
      ],
    );
    final finalReplies = result.assistants.where(completed).toList()
      ..sort(
        (a, b) => (_map(_map(a['info'])['time'])['completed'] as num).compareTo(
          _map(_map(b['info'])['time'])['completed'] as num,
        ),
      );
    run.require(finalReplies.isNotEmpty, 'oc1_image_content_unverified');
    final finalMessage = finalReplies.last;
    final finalInfo = _map(finalMessage['info']);
    run.require(
      finalInfo['providerID'] == choice.provider &&
          finalInfo['modelID'] == choice.id,
      'oc1_image_selection_unobserved',
    );
    final answer = (finalMessage['parts'] as List)
        .map(_map)
        .where(
          (part) =>
              part['type'] == 'text' &&
              part['synthetic'] != true &&
              part['sessionID'] == session &&
              part['messageID'] == finalInfo['id'],
        )
        .map((part) => _string(part['text']))
        .join('\n')
        .trim();
    run.require(
      RegExp(
        r'^[\s*_`~#>\[\]().,!?:;"-]*blue[\s*_`~#>\[\]().,!?:;"-]*$',
        caseSensitive: false,
      ).hasMatch(answer),
      'oc1_image_content_unverified',
    );
    final user = result.history.firstWhere(
      (m) => _map(m['info'])['id'] == result.prompt,
    );
    run.require(
      (user['parts'] as List)
          .map(_map)
          .any(
            (p) =>
                p['type'] == 'file' &&
                p['mime'] == 'image/png' &&
                p['url'] == data,
          ),
      'oc1_image_not_retained',
    );
    return {'imageAnswerVerified': true};
  }

  Future<Map<String, Object?>> cards() async {
    checkpoint('cards_inventory', choice: model());
    final inventory = _map(await request('GET', '/mcp'));
    run.require(
      _map(inventory['oc-ui'])['status'] == 'connected',
      'oc1_cards_mcp_unavailable',
    );
    final session = await create(
      'cards',
      rules: [
        {'permission': '*', 'pattern': '*', 'action': 'deny'},
        {'permission': 'oc-ui_show', 'pattern': '*', 'action': 'allow'},
      ],
    );
    final cardID = 'fq3-${nonce.substring(0, min(nonce.length, 30))}';
    final card = <String, Object?>{
      'v': 1,
      'id': cardID,
      'title': 'FQ3 confirmation',
      'body': [
        {'type': 'text', 'text': 'Confirm the protocol probe.'},
      ],
      'ask': {'kind': 'confirm'},
    };
    final ack = token('card_ack');
    checkpoint('cards_tool_reply', choice: model());
    final result = await turn(
      session,
      'Call oc-ui_show exactly once with this exact JSON input: '
      '${jsonEncode(card)}. Do not print a simulated tool call. After showing '
      'the card, wait for my answer. On receiving the matching oc-ui answer '
      'receipt, acknowledge with exactly this token: $ack',
    );
    final call = oc1CompletedTool(
      result.history,
      session,
      result.prompt,
      'oc-ui_show',
    );
    checkpoint('cards_tool_verification', choice: model());
    run.require(call != null, 'oc1_cards_tool_not_executed');
    final input = _map(_map(call!['state'])['input']);
    run.require(_sameJson(input, card), 'oc1_cards_input_mismatch');
    final callID = _string(call['callID']);
    final receipt =
        '[oc-ui answer $cardID] Confirmed\n${jsonEncode({
          'v': 1,
          'cardId': cardID,
          'callId': callID,
          'value': {'confirm': true},
        })}';
    checkpoint('cards_answer_reply', choice: model());
    final answered = await turn(session, receipt);
    final user = answered.history.where(
      (m) => _map(m['info'])['id'] == answered.prompt,
    );
    run.require(
      user.length == 1 &&
          _text(user.single) == receipt &&
          answered.text.contains(ack),
      'oc1_cards_receipt_unverified',
    );
    return {'cardsToolCall': true, 'answerReceipt': true};
  }
}

bool _sameJson(Object? left, Object? right) {
  if (left is Map && right is Map) {
    return left.length == right.length &&
        left.keys.every(
          (key) => right.containsKey(key) && _sameJson(left[key], right[key]),
        );
  }
  if (left is List && right is List) {
    return left.length == right.length &&
        List.generate(
          left.length,
          (i) => _sameJson(left[i], right[i]),
        ).every((equal) => equal);
  }
  return left == right;
}

// Deterministic 96x96 blue image; the prompt never names its color.
List<int> _bluePng() {
  List<int> word(int value) => [
    value >> 24 & 255,
    value >> 16 & 255,
    value >> 8 & 255,
    value & 255,
  ];
  List<int> chunk(String name, List<int> data) {
    final bytes = [...ascii.encode(name), ...data];
    var crc = 0xffffffff;
    for (final byte in bytes) {
      crc ^= byte;
      for (var bit = 0; bit < 8; bit++) {
        crc = (crc >> 1) ^ ((crc & 1) == 1 ? 0xedb88320 : 0);
      }
    }
    return [...word(data.length), ...bytes, ...word(crc ^ 0xffffffff)];
  }

  final pixels = <int>[];
  for (var y = 0; y < 96; y++) {
    pixels.add(0);
    for (var x = 0; x < 96; x++) {
      pixels.addAll([0, 0, 255]);
    }
  }
  return [
    137,
    80,
    78,
    71,
    13,
    10,
    26,
    10,
    ...chunk('IHDR', [...word(96), ...word(96), 8, 2, 0, 0, 0]),
    ...chunk('IDAT', ZLibEncoder().convert(pixels)),
    ...chunk('IEND', []),
  ];
}

Future<ProbeRun> runProtocol1(Fq3Wire wire, ProbeOptions options) async {
  final run = ProbeRun(wire, options);
  final requested = options.model ?? options.baselineModel;
  if (isPublicModelReference(requested)) {
    run.modelSelection = {
      'source': options.model == null ? 'baseline' : 'explicit',
      'requested': requested,
      'selected': null,
      'baselineAvailable': false,
      'inferenceAvailable': false,
    };
  }
  final probe = _Oc1(run);
  Future<void> check(
    String key,
    Future<Map<String, Object?>> Function() action,
  ) async {
    await run.check(key, () => probe.scenario(key, action));
    if (run.results.containsKey(key)) {
      run.results[key] = oc1ClassifyResult(
        key,
        run.results[key]!,
        run.observations[key] ?? {},
      );
    }
  }

  await check('version', probe.version);
  await check('create', () async {
    await probe.create('create');
    return {'retained': true};
  });
  await check('models', probe.discoverModels);
  await check('modelSwitch', probe.modelSwitch);
  await check('stream', probe.stream);
  await check('abort', probe.abort);
  await check('reconnect', probe.reconnect);
  await check('permissionAllow', () => probe.permission(true));
  await check('permissionDeny', () => probe.permission(false));
  await check('image', probe.image);
  await check('cards', probe.cards);
  return run;
}
